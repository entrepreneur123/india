-- Ludo inside voice rooms (Ludo King "Classic" rules). The host or a co-host opens a game; up to 4 people in the
-- room take a colour (red, green, yellow, blue: seats 0-3, played in that order, clockwise); everyone else watches.
-- Voice and chat carry on. Everything is decided here: the dice are rolled on the server and every move is checked.
--
-- Rules:
--  * 4 tokens each. A 6 is needed to leave the base; a 6 gives another roll; a third 6 in a row ends the turn.
--  * Landing on an opponent sends it home, except on the 8 safe squares (the 4 start squares and the 4 stars);
--    a capture and a token reaching home each give another roll. Home needs the exact number.
--  * No legal move: the turn passes. One possible move (or several that are the same, like any token out of the
--    base): it is played at once, to keep the pace.
--  * The game ends when the first player brings all 4 tokens home (short sessions in a voice room; no 2nd/3rd
--    place), or when fewer than 2 players are left.
--  * 15 s per turn. Once it has passed, anyone in the room may call ludo_timeout(): the player's die is rolled and
--    the first legal move played for them. Three timeouts in a row and the player is out.
--  * Players who leave the room (or drop out of it) are out: their tokens leave the board.
-- One game at a time per room. Every change bumps ludo_games.version; clients follow the row over realtime and
-- send the version they saw with each action, so a double tap or a stale screen never acts twice.
--
-- Token positions (tokens[seat * 4 + k + 1]): -1 in the base, 0-50 on the track (counted from the colour's start
-- square), 51-55 the home column, 56 home. Track square (shared by all colours): (seat * 13 + position) % 52.

create table public.ludo_games (
  id uuid primary key default gen_random_uuid(),
  room_id text not null references public.voice_rooms (id) on delete cascade,
  opened_by uuid not null references public.profiles (id) on delete cascade,
  status text not null default 'lobby' check (status in ('lobby', 'playing', 'finished', 'cancelled')),
  -- One person per colour (null = free), their name when they sat down, and whether they are out.
  players uuid[] not null default array[null, null, null, null]::uuid[] check (cardinality(players) = 4),
  names text[] not null default array[null, null, null, null]::text[],
  out boolean[] not null default '{f,f,f,f}',
  -- Turns each player missed in a row.
  misses smallint[] not null default '{0,0,0,0}',
  tokens smallint[] not null default array_fill(-1::smallint, array[16]) check (cardinality(tokens) = 16),
  turn smallint check (turn between 0 and 3),
  -- The rolled value waiting for a move (null: the player rolls next), and the last value shown on the die.
  dice smallint check (dice between 1 and 6),
  roll smallint check (roll between 1 and 6),
  sixes smallint not null default 0,
  deadline timestamptz,
  winner smallint check (winner between 0 and 3),
  -- What the last action did, in order, for the apps to animate and announce:
  -- {k: start | roll | move | pass | triple | timeout | out | win, s: seat, d: die, t: token, f: from, to, cap: [[seat, token, from]], why}
  events jsonb not null default '[]',
  version int not null default 0,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  ended_at timestamptz
);
create unique index ludo_games_active on public.ludo_games (room_id) where status in ('lobby', 'playing');
create index ludo_games_room on public.ludo_games (room_id, created_at desc);

alter table public.ludo_games enable row level security;
-- Nothing private in a game: people in the room read it (and follow it over realtime). Writes go through the functions.
grant select on public.ludo_games to authenticated;
create policy "read ludo game" on public.ludo_games for select to authenticated using (public.voice_can_read(room_id));
alter publication supabase_realtime add table public.ludo_games;

-- ---------- helpers (not callable by users) ----------

/**
 * A fair die: one random byte, values 252-255 thrown away so each face has exactly 42 of the 252 kept values.
 * Tests running in a direct database session (never a request from the app: those come in as "authenticator")
 * may queue the next values: set_config('ludo.test_dice', '6,6,3', true).
 */
create function public.ludo_die() returns smallint language plpgsql volatile security definer set search_path = '' as $$
declare q text; b int;
begin
  if session_user in ('postgres', 'supabase_admin') then
    q := nullif(current_setting('ludo.test_dice', true), '');
    if q is not null then
      perform set_config('ludo.test_dice', case when position(',' in q) > 0 then substr(q, position(',' in q) + 1) else '' end, true);
      if split_part(q, ',', 1) !~ '^[1-6]$' then raise exception 'bad test die %', q; end if;
      return split_part(q, ',', 1)::smallint;
    end if;
  end if;
  loop
    b := get_byte(extensions.gen_random_bytes(1), 0);
    exit when b < 252;
  end loop;
  return (b % 6 + 1)::smallint;
end $$;

/** The shared track square of a token, or null in the base, the home column or home. */
create function public.ludo_square(p_seat int, p_pos int) returns int language sql immutable set search_path = '' as $$
  select case when p_pos between 0 and 50 then (p_seat * 13 + p_pos) % 52 end
$$;

/** The 4 start squares (0, 13, 26, 39) and the 4 stars 8 squares on (8, 21, 34, 47). */
create function public.ludo_safe(p_square int) returns boolean language sql immutable set search_path = '' as $$
  select p_square % 13 in (0, 8)
$$;

/** Tokens (0-3) the player to move may move with this die. */
create function public.ludo_legal(g public.ludo_games, d int) returns int[] language plpgsql immutable set search_path = '' as $$
declare r int[] := '{}'; p int;
begin
  for k in 0..3 loop
    p := g.tokens[g.turn * 4 + k + 1];
    if (p = -1 and d = 6) or (p >= 0 and p + d <= 56) then r := r || k; end if;
  end loop;
  return r;
end $$;

create function public.ludo_active(g public.ludo_games) returns int language sql immutable set search_path = '' as $$
  select count(*)::int from generate_series(1, 4) s where (g).players[s] is not null and not (g).out[s]
$$;

create function public.ludo_event(g public.ludo_games, e jsonb) returns public.ludo_games language plpgsql immutable set search_path = '' as $$
begin
  g.events := g.events || jsonb_build_array(e);
  return g;
end $$;

/** The next player still in the game rolls, with a fresh 15 s. */
create function public.ludo_next(g public.ludo_games) returns public.ludo_games language plpgsql volatile set search_path = '' as $$
declare s int;
begin
  for i in 1..4 loop
    s := (g.turn + i) % 4;
    if g.players[s + 1] is not null and not g.out[s + 1] then
      g.turn := s;
      exit;
    end if;
  end loop;
  g.dice := null;
  g.sixes := 0;
  g.deadline := now() + interval '15 seconds';
  return g;
end $$;

create function public.ludo_finish(g public.ludo_games, p_winner int) returns public.ludo_games language plpgsql volatile set search_path = '' as $$
begin
  g.status := 'finished';
  g.winner := p_winner;
  g.dice := null;
  g.deadline := null;
  g.ended_at := now();
  if p_winner is not null then g := public.ludo_event(g, jsonb_build_object('k', 'win', 's', p_winner)); end if;
  return g;
end $$;

/** A player is out (left the room, missed 3 turns, quit): their tokens leave the board. In the lobby the colour frees up. */
create function public.ludo_forfeit(g public.ludo_games, p_seat int, p_why text) returns public.ludo_games
language plpgsql volatile set search_path = '' as $$
declare left_one int;
begin
  if g.status = 'lobby' then
    g.players[p_seat + 1] := null;
    g.names[p_seat + 1] := null;
    return g;
  end if;
  if g.status <> 'playing' or g.players[p_seat + 1] is null or g.out[p_seat + 1] then return g; end if;
  g.out[p_seat + 1] := true;
  for k in 0..3 loop g.tokens[p_seat * 4 + k + 1] := -1; end loop;
  g := public.ludo_event(g, jsonb_build_object('k', 'out', 's', p_seat, 'why', p_why));
  if public.ludo_active(g) < 2 then
    select s - 1 into left_one from generate_series(1, 4) s where g.players[s] is not null and not g.out[s] limit 1;
    return public.ludo_finish(g, left_one);
  end if;
  if g.turn = p_seat then g := public.ludo_next(g); end if;
  return g;
end $$;

/** Moves token `k` of the player to move by the rolled die: captures, home, another roll or the next player. */
create function public.ludo_move_token(g public.ludo_games, k int) returns public.ludo_games language plpgsql volatile set search_path = '' as $$
declare seat int := g.turn; d int := g.dice; i int := g.turn * 4 + k + 1; f int; t int; sq int; caps jsonb := '[]';
begin
  f := g.tokens[i];
  t := case when f = -1 then 0 else f + d end;
  g.tokens[i] := t;
  sq := public.ludo_square(seat, t);
  if sq is not null and not public.ludo_safe(sq) then
    for s in 0..3 loop
      continue when s = seat or g.players[s + 1] is null or g.out[s + 1];
      for j in 0..3 loop
        if public.ludo_square(s, g.tokens[s * 4 + j + 1]) = sq then
          caps := caps || jsonb_build_array(jsonb_build_array(s, j, g.tokens[s * 4 + j + 1]));
          g.tokens[s * 4 + j + 1] := -1;
        end if;
      end loop;
    end loop;
  end if;
  g := public.ludo_event(g, jsonb_build_object('k', 'move', 's', seat, 't', k, 'f', f, 'to', t, 'cap', caps));
  g.dice := null;
  if (select bool_and(g.tokens[seat * 4 + j] = 56) from generate_series(1, 4) j) then
    return public.ludo_finish(g, seat);
  end if;
  if d = 6 or jsonb_array_length(caps) > 0 or t = 56 then
    if d <> 6 then g.sixes := 0; end if; -- a bonus roll for a capture or home starts a new run of sixes
    g.deadline := now() + interval '15 seconds';
    return g;
  end if;
  return public.ludo_next(g);
end $$;

/**
 * A die was rolled for the player to move. A third 6 ends the turn; no legal move passes it; a move that is the
 * only one possible (or the timeout's `p_auto`) is played at once; otherwise the player picks a token.
 */
create function public.ludo_after_roll(g public.ludo_games, d int, p_auto boolean) returns public.ludo_games
language plpgsql volatile set search_path = '' as $$
declare legal int[];
begin
  g.roll := d;
  g := public.ludo_event(g, jsonb_build_object('k', 'roll', 's', g.turn, 'd', d));
  g.sixes := case when d = 6 then g.sixes + 1 else 0 end;
  if g.sixes >= 3 then
    g := public.ludo_event(g, jsonb_build_object('k', 'triple', 's', g.turn));
    return public.ludo_next(g);
  end if;
  legal := public.ludo_legal(g, d);
  if cardinality(legal) = 0 then
    g := public.ludo_event(g, jsonb_build_object('k', 'pass', 's', g.turn));
    return public.ludo_next(g);
  end if;
  g.dice := d;
  if p_auto or (select count(distinct g.tokens[g.turn * 4 + k + 1]) from unnest(legal) k) = 1 then
    return public.ludo_move_token(g, legal[1]);
  end if;
  g.deadline := now() + interval '15 seconds';
  return g;
end $$;

/** Players no longer in the room are out; a game in a room that ended is cancelled. Starts a new list of events. */
create function public.ludo_settle(g public.ludo_games) returns public.ludo_games language plpgsql volatile security definer set search_path = '' as $$
begin
  g.events := '[]';
  if g.status not in ('lobby', 'playing') then return g; end if;
  if (select r.ended_at from public.voice_rooms r where r.id = g.room_id) is not null then
    g.status := 'cancelled';
    g.deadline := null;
    g.ended_at := now();
    return g;
  end if;
  for s in 0..3 loop
    continue when g.players[s + 1] is null or g.out[s + 1];
    if not exists (select 1 from public.voice_members m where m.room_id = g.room_id and m.user_id = g.players[s + 1]
                     and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds') then
      g := public.ludo_forfeit(g, s, 'left');
    end if;
  end loop;
  return g;
end $$;

create function public.ludo_json(g public.ludo_games) returns jsonb language sql volatile set search_path = '' as $$
  select jsonb_build_object(
    'id', (g).id, 'room', (g).room_id, 'status', (g).status, 'openedBy', (g).opened_by,
    'players', to_jsonb((g).players), 'names', to_jsonb((g).names), 'out', to_jsonb((g).out), 'tokens', to_jsonb((g).tokens),
    'turn', (g).turn, 'dice', (g).dice, 'roll', (g).roll, 'sixes', (g).sixes, 'winner', (g).winner, 'events', (g).events,
    'version', (g).version, 'deadline', floor(extract(epoch from (g).deadline) * 1000)::bigint,
    'endedAt', floor(extract(epoch from (g).ended_at) * 1000)::bigint, 'now', public.voice_ms())
$$;

/** Me, present in the game's room (the room row is locked: actions in a room are serialised), and the game, locked. */
create function public.ludo_lock(p_game uuid) returns public.ludo_games language plpgsql volatile security definer set search_path = '' as $$
declare rid text; g public.ludo_games;
begin
  select x.room_id into rid from public.ludo_games x where x.id = p_game;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  perform public.voice_actor(rid);
  select * into g from public.ludo_games x where x.id = p_game for update;
  return g;
end $$;

/** Writes the game if anything but the event list changed (with a new version); returns what clients show. */
create function public.ludo_save(g public.ludo_games, orig public.ludo_games) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare same public.ludo_games := g;
begin
  same.events := orig.events;
  if same is not distinct from orig then return public.ludo_json(orig); end if;
  update public.ludo_games set status = g.status, players = g.players, names = g.names, out = g.out, misses = g.misses,
         tokens = g.tokens, turn = g.turn, dice = g.dice, roll = g.roll, sixes = g.sixes, deadline = g.deadline,
         winner = g.winner, events = g.events, started_at = g.started_at, ended_at = g.ended_at, version = version + 1
   where id = g.id returning * into g;
  return public.ludo_json(g);
end $$;

/**
 * An action that can't be done. When settling already changed the game (someone left), that is saved and the new
 * state returned instead, so the change isn't rolled back with the error; the app shows the new state.
 */
create function public.ludo_refuse(g public.ludo_games, orig public.ludo_games, p_code text) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare same public.ludo_games := g;
begin
  same.events := orig.events;
  if same is distinct from orig then return public.ludo_save(g, orig); end if;
  raise exception '%', p_code using errcode = 'P0001';
end $$;

create function public.ludo_is_mod(p_room text, p_user uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = p_user
                   and m.role in ('host', 'cohost') and m.left_at is null and not m.kicked)
$$;

-- ---------- for the apps ----------

/** The room's game: the one open, else one that ended in the last 5 minutes (its result), else null. */
create function public.ludo_state(p_room text) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.voice_me(); g public.ludo_games;
begin
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me and m.left_at is null and not m.kicked) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select * into g from public.ludo_games x where x.room_id = p_room
     and (x.status in ('lobby', 'playing') or x.ended_at > now() - interval '5 minutes')
   order by x.created_at desc limit 1;
  return case when found then public.ludo_json(g) end;
end $$;

/**
 * Host or co-host: opens a game (or returns the one already open). After a game that just ended ("Play again"),
 * its players still in the room keep their colours; the opener takes a free colour if they have none.
 */
create function public.ludo_open(p_room text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_mod(p_room); g public.ludo_games; prev public.ludo_games;
        players uuid[] := array[null, null, null, null]::uuid[]; s int;
begin
  select * into g from public.ludo_games x where x.room_id = p_room and x.status in ('lobby', 'playing');
  if found then return public.ludo_json(g); end if;
  if (select count(*) from public.ludo_games x where x.room_id = p_room and x.created_at > now() - interval '1 hour') >= 30 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  select * into prev from public.ludo_games x where x.room_id = p_room and x.status = 'finished'
     and x.ended_at > now() - interval '10 minutes' order by x.ended_at desc limit 1;
  if found then
    for s in 0..3 loop
      if exists (select 1 from public.voice_members v where v.room_id = p_room and v.user_id = prev.players[s + 1]
                   and v.left_at is null and not v.kicked) then
        players[s + 1] := prev.players[s + 1];
      end if;
    end loop;
  end if;
  if array_position(players, m.user_id) is null then
    s := array_position(players, null);
    if s is not null then players[s] := m.user_id; end if;
  end if;
  insert into public.ludo_games (room_id, opened_by, players, names)
  values (p_room, m.user_id, players,
          array(select (select p.name from public.profiles p where p.id = players[i]) from generate_series(1, 4) i))
  returning * into g;
  return public.ludo_json(g);
end $$;

/** Takes a colour in the lobby (`p_seat` 0-3, or null for the first free one); someone seated may switch to a free one. */
create function public.ludo_join(p_game uuid, p_seat int default null) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig);
        me uuid := auth.uid(); mine int; s int;
begin
  if p_seat is not null and p_seat not between 0 and 3 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if g.status <> 'lobby' then return public.ludo_refuse(g, orig, 'not_allowed'); end if;
  mine := array_position(g.players, me) - 1;
  if mine is not null and (p_seat is null or p_seat = mine) then return public.ludo_save(g, orig); end if;
  s := coalesce(p_seat, array_position(g.players, null) - 1);
  if s is null then return public.ludo_refuse(g, orig, 'game_full'); end if;
  if g.players[s + 1] is not null then return public.ludo_refuse(g, orig, 'seat_taken'); end if;
  if mine is not null then
    g.players[mine + 1] := null;
    g.names[mine + 1] := null;
  end if;
  g.players[s + 1] := me;
  g.names[s + 1] := (select p.name from public.profiles p where p.id = me);
  return public.ludo_save(g, orig);
end $$;

/** Leaves my colour in the lobby; during a game, I'm out (my tokens leave the board). */
create function public.ludo_leave(p_game uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig); s int;
begin
  s := array_position(g.players, auth.uid()) - 1;
  if s is not null then g := public.ludo_forfeit(g, s, 'quit'); end if;
  return public.ludo_save(g, orig);
end $$;

/** Host or co-host, with 2-4 players seated. Red (or the first colour taken) starts. */
create function public.ludo_start(p_game uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig);
begin
  if not public.ludo_is_mod(g.room_id, auth.uid()) or g.status <> 'lobby' then return public.ludo_refuse(g, orig, 'not_allowed'); end if;
  if public.ludo_active(g) < 2 then return public.ludo_refuse(g, orig, 'need_players'); end if;
  g.status := 'playing';
  g.started_at := now();
  g.turn := 3;
  g := public.ludo_next(g);
  g := public.ludo_event(g, jsonb_build_object('k', 'start', 's', g.turn));
  return public.ludo_save(g, orig);
end $$;

/** Host or co-host: closes the lobby or stops the game (the stage comes back). */
create function public.ludo_close(p_game uuid) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig);
begin
  if not public.ludo_is_mod(g.room_id, auth.uid()) then return public.ludo_refuse(g, orig, 'not_allowed'); end if;
  if g.status in ('lobby', 'playing') then
    g.status := 'cancelled';
    g.dice := null;
    g.deadline := null;
    g.ended_at := now();
  end if;
  return public.ludo_save(g, orig);
end $$;

/** Why I can't roll or move now (null: I can). `p_version` is the version my screen showed. */
create function public.ludo_check(g public.ludo_games, orig public.ludo_games, p_version int) returns text
language plpgsql stable security definer set search_path = '' as $$
declare s int := array_position(g.players, auth.uid()) - 1;
begin
  if orig.version <> p_version then return 'stale'; end if;
  if g.status <> 'playing' or s is null or g.out[s + 1] then return 'not_allowed'; end if;
  if g.turn <> s then return 'not_your_turn'; end if;
  return null;
end $$;

create function public.ludo_roll(p_game uuid, p_version int) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig);
        why text := public.ludo_check(g, orig, p_version); s int := array_position(g.players, auth.uid()) - 1;
begin
  if why is null and g.dice is not null then why := 'stale'; end if; -- already rolled
  if why is not null then return public.ludo_refuse(g, orig, why); end if;
  g.misses[s + 1] := 0;
  g := public.ludo_after_roll(g, public.ludo_die(), false);
  return public.ludo_save(g, orig);
end $$;

create function public.ludo_move(p_game uuid, p_token int, p_version int) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig);
        why text := public.ludo_check(g, orig, p_version); s int := array_position(g.players, auth.uid()) - 1;
begin
  if why is null and (g.dice is null or p_token is null or not (p_token = any (public.ludo_legal(g, g.dice)))) then why := 'not_allowed'; end if;
  if why is not null then return public.ludo_refuse(g, orig, why); end if;
  g.misses[s + 1] := 0;
  g := public.ludo_move_token(g, p_token);
  return public.ludo_save(g, orig);
end $$;

/**
 * Anyone in the room, once the turn's 15 s have passed: plays the turn for the absent player (rolls, then the first
 * legal move). The third timeout in a row puts the player out. Early or repeated calls change nothing.
 */
create function public.ludo_timeout(p_game uuid, p_version int) returns jsonb language plpgsql security definer set search_path = '' as $$
declare orig public.ludo_games := public.ludo_lock(p_game); g public.ludo_games := public.ludo_settle(orig); s int;
begin
  if g.status = 'playing' and orig.version = p_version and g.deadline <= now() then
    s := g.turn;
    g := public.ludo_event(g, jsonb_build_object('k', 'timeout', 's', s));
    g.misses[s + 1] := g.misses[s + 1] + 1;
    if g.misses[s + 1] >= 3 then
      g := public.ludo_forfeit(g, s, 'timeouts');
    elsif g.dice is null then
      g := public.ludo_after_roll(g, public.ludo_die(), true);
    else
      g := public.ludo_move_token(g, (public.ludo_legal(g, g.dice))[1]);
    end if;
  end if;
  return public.ludo_save(g, orig);
end $$;

-- ---------- privileges ----------

revoke execute on function public.ludo_die(), public.ludo_square(int, int), public.ludo_safe(int),
  public.ludo_legal(public.ludo_games, int), public.ludo_active(public.ludo_games), public.ludo_event(public.ludo_games, jsonb),
  public.ludo_next(public.ludo_games), public.ludo_finish(public.ludo_games, int), public.ludo_forfeit(public.ludo_games, int, text),
  public.ludo_move_token(public.ludo_games, int), public.ludo_after_roll(public.ludo_games, int, boolean),
  public.ludo_settle(public.ludo_games), public.ludo_json(public.ludo_games), public.ludo_lock(uuid),
  public.ludo_save(public.ludo_games, public.ludo_games), public.ludo_refuse(public.ludo_games, public.ludo_games, text),
  public.ludo_is_mod(text, uuid), public.ludo_check(public.ludo_games, public.ludo_games, int)
  from public, anon, authenticated;
revoke execute on function public.ludo_state(text), public.ludo_open(text), public.ludo_join(uuid, int), public.ludo_leave(uuid),
  public.ludo_start(uuid), public.ludo_close(uuid), public.ludo_roll(uuid, int), public.ludo_move(uuid, int, int),
  public.ludo_timeout(uuid, int)
  from public, anon;
grant execute on function public.ludo_state(text), public.ludo_open(text), public.ludo_join(uuid, int), public.ludo_leave(uuid),
  public.ludo_start(uuid), public.ludo_close(uuid), public.ludo_roll(uuid, int), public.ludo_move(uuid, int, int),
  public.ludo_timeout(uuid, int)
  to authenticated;
