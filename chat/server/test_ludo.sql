-- Ludo in voice rooms (migration 018). One transaction, rolled back. The dice are queued with ludo.test_dice
-- (honoured only in a direct database session like this one, never for requests from the apps).
begin;
set local statement_timeout = '30s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create function pg_temp.fails(q text, code text) returns void language plpgsql as $$
declare ok boolean := false;
begin
  begin
    execute q;
  exception when others then
    if sqlerrm like '%' || code || '%' then ok := true; else raise; end if;
  end;
  if not ok then raise exception 'failed: expected % from %', code, q; end if;
end $$;
create function pg_temp.dice(q text) returns void language sql as $$ select set_config('ludo.test_dice', q, true) $$;
create temp table v (k text primary key, id text) on commit drop;
grant all on v to authenticated;
create function pg_temp.room() returns text language sql as $$ select id from v where k = 'r' $$;
create function pg_temp.g() returns uuid language sql as $$ select id::uuid from v where k = 'g' $$;
-- Read as the acting user (who must be in the room: the game row is behind RLS).
create function pg_temp.game() returns public.ludo_games language sql as $$ select * from public.ludo_games where id = pg_temp.g() $$;
create function pg_temp.ver() returns int language sql as $$ select (pg_temp.game()).version $$;
create function pg_temp.tok(seat int, k int) returns int language sql as $$ select (pg_temp.game()).tokens[seat * 4 + k + 1] $$;
create function pg_temp.happened(kind text) returns boolean language sql as $$
  select exists (select 1 from jsonb_array_elements((pg_temp.game()).events) e where e->>'k' = kind) $$;
-- Puts the game in a position (as the database owner): tokens, whose turn, misses; a fresh turn with no roll yet.
create function pg_temp.put(p_tokens int[], p_turn int, p_misses int[] default '{0,0,0,0}') returns void language sql as $$
  update public.ludo_games set tokens = p_tokens::smallint[], turn = p_turn, dice = null, sixes = 0, misses = p_misses::smallint[],
         deadline = now() + interval '15 seconds' where id = pg_temp.g() $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000201', 'authenticated', 'authenticated', '919855590201'),
  ('eeeeeeee-0000-0000-0000-000000000202', 'authenticated', 'authenticated', '919855590202'),
  ('eeeeeeee-0000-0000-0000-000000000203', 'authenticated', 'authenticated', '919855590203'),
  ('eeeeeeee-0000-0000-0000-000000000204', 'authenticated', 'authenticated', '919855590204'),
  ('eeeeeeee-0000-0000-0000-000000000205', 'authenticated', 'authenticated', '919855590205');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000201', 'LRed', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000202', 'LGreen', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000203', 'LYellow', 'female', '2000-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000204', 'LWatcher', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000205', 'LOutsider', 'male', '1997-01-01');

-- ---- the die: uniform 1-6 (as the owner, nothing queued) ----
select pg_temp.dice('');
select pg_temp.check((select min(d) = 1 and max(d) = 6 and bool_and(n between 850 and 1150)
                        from (select d, count(*) over (partition by d) n from (select public.ludo_die() d from generate_series(1, 6000)) x) y),
                     'die: 6000 rolls, every face 850-1150 times');

set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
insert into v values ('r', public.create_voice_room('Ludo night', 'gaming', 'open'));
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select public.join_voice(pg_temp.room());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select public.join_voice(pg_temp.room());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000204');
select public.join_voice(pg_temp.room());

-- ---- privileges: helpers and the table are out of reach ----
select pg_temp.fails($$select public.ludo_die()$$, 'permission denied');
select pg_temp.fails($$select public.ludo_settle(null::public.ludo_games)$$, 'permission denied');

-- ---- open: host or co-host, one game per room ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.fails($$select public.ludo_open(pg_temp.room())$$, 'not_allowed');
select pg_temp.check(public.ludo_state(pg_temp.room()) is null, 'no game yet');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
insert into v values ('g', public.ludo_open(pg_temp.room())->>'id');
select pg_temp.check((pg_temp.game()).status = 'lobby', 'opened: lobby');
select pg_temp.check((pg_temp.game()).players[1] = 'eeeeeeee-0000-0000-0000-000000000201', 'opener takes red');
select pg_temp.check((public.ludo_open(pg_temp.room())->>'id')::uuid = pg_temp.g(), 'one game per room');
select pg_temp.fails($$update public.ludo_games set status = 'finished'$$, 'permission denied');

-- ---- outsiders see and do nothing ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000205');
select pg_temp.fails($$select public.ludo_state(pg_temp.room())$$, 'not_allowed');
select pg_temp.fails($$select public.ludo_join(pg_temp.g(), null)$$, 'not_allowed');
select pg_temp.check((select count(*) from public.ludo_games) = 0, 'outsider: game row hidden (RLS)');

-- ---- lobby: colours ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.check(public.ludo_state(pg_temp.room())->>'status' = 'lobby', 'member sees the lobby');
select public.ludo_join(pg_temp.g(), 1);
select pg_temp.fails($$select public.ludo_join(pg_temp.g(), 7)$$, 'bad_request');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select pg_temp.fails($$select public.ludo_join(pg_temp.g(), 1)$$, 'seat_taken');
select public.ludo_join(pg_temp.g(), null);
select pg_temp.check((pg_temp.game()).players[3] = 'eeeeeeee-0000-0000-0000-000000000203', 'first free colour: yellow');
select public.ludo_join(pg_temp.g(), 3);
select pg_temp.check((pg_temp.game()).players[3] is null and (pg_temp.game()).players[4] = 'eeeeeeee-0000-0000-0000-000000000203', 'switch to blue');
select public.ludo_join(pg_temp.g(), 2);
select pg_temp.check((pg_temp.game()).names[3] = 'LYellow', 'back to yellow, name kept');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.fails($$select public.ludo_start(pg_temp.g())$$, 'not_allowed');
select public.ludo_leave(pg_temp.g());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select public.ludo_leave(pg_temp.g());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select pg_temp.fails($$select public.ludo_start(pg_temp.g())$$, 'need_players');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select public.ludo_join(pg_temp.g(), 1);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select public.ludo_join(pg_temp.g(), 2);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000204');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_allowed');

-- ---- start: red first ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select public.ludo_start(pg_temp.g());
select pg_temp.check((pg_temp.game()).status = 'playing' and (pg_temp.game()).turn = 0, 'started, red to roll');
select pg_temp.check((pg_temp.game()).deadline = now() + interval '15 seconds', '15 s turn');
select pg_temp.fails($$select public.ludo_join(pg_temp.g(), 3)$$, 'not_allowed');

-- ---- who may act ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_your_turn');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000204');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_allowed');
select pg_temp.fails($$select public.ludo_move(pg_temp.g(), 0, pg_temp.ver())$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver() - 1)$$, 'stale');
select pg_temp.fails($$select public.ludo_move(pg_temp.g(), 0, pg_temp.ver())$$, 'not_allowed');

-- ---- a 6 to leave the base ----
select pg_temp.dice('3');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).turn = 1 and pg_temp.tok(0, 0) = -1 and pg_temp.happened('pass'), '3 with all in base: no move, turn passes');
select pg_temp.check((pg_temp.game()).roll = 3 and (pg_temp.game()).dice is null, 'last roll kept for the die');

-- ---- a 6: out of the base (all base tokens are the same move: played at once) and another roll ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.dice('6');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(1, 0) = 0 and (pg_temp.game()).turn = 1 and (pg_temp.game()).dice is null, '6: token out, roll again');
select pg_temp.dice('4');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(1, 0) = 4 and (pg_temp.game()).turn = 2, 'single legal move played at once, turn passes');
select pg_temp.check((pg_temp.game()).misses[2] = 0, 'acting keeps misses at 0');

-- ---- three 6s in a row end the turn (moves from the first two stand) ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select pg_temp.dice('6');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.dice('6');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).dice = 6 and (pg_temp.game()).sixes = 2, 'second 6: a choice is waiting');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'stale');
select pg_temp.fails($$select public.ludo_move(pg_temp.g(), 0, pg_temp.ver() - 1)$$, 'stale');
select public.ludo_move(pg_temp.g(), 0, pg_temp.ver());
select pg_temp.check(pg_temp.tok(2, 0) = 6 and (pg_temp.game()).turn = 2, 'moved 6, rolls again');
select pg_temp.dice('6');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).turn = 0 and (pg_temp.game()).sixes = 0 and pg_temp.happened('triple') and pg_temp.tok(2, 0) = 6,
                     'third 6: turn over, earlier moves stand');

-- ---- capture: back to base and another roll ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
reset role;
-- Red token 0 on track square 10; green token 0 three squares behind it (green position 46 = square (13 + 46) % 52 = 7).
select pg_temp.put(array[10,-1,-1,-1, 46,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 1);
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.dice('3');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(1, 0) = 49 and pg_temp.tok(0, 0) = -1, 'capture: red token sent home');
select pg_temp.check((pg_temp.game()).turn = 1, 'capture: another roll');
select pg_temp.check((select e->'cap' from jsonb_array_elements((pg_temp.game()).events) e where e->>'k' = 'move') = '[[0, 0, 10]]', 'capture event');

-- ---- safe squares: a star and a start square ----
reset role;
-- Red on the star (square 8); green lands on it from position 44 (square 5) with a 3.
select pg_temp.put(array[8,-1,-1,-1, 44,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 1);
set local role authenticated;
select pg_temp.dice('3');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(1, 0) = 47 and pg_temp.tok(0, 0) = 8, 'star: both tokens stay');
select pg_temp.check((pg_temp.game()).turn = 2, 'no capture, no extra roll');
reset role;
-- Red on its start square (0); green lands on it (green position 39 = square 0).
select pg_temp.put(array[0,-1,-1,-1, 36,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 1);
set local role authenticated;
select pg_temp.dice('3');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(1, 0) = 39 and pg_temp.tok(0, 0) = 0, 'start square: both tokens stay');

-- ---- home column: exact roll ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
reset role;
select pg_temp.put(array[54,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0);
set local role authenticated;
select pg_temp.dice('3');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(0, 0) = 54 and (pg_temp.game()).turn = 1, 'overshooting home: no move, turn passes');
reset role;
select pg_temp.put(array[54,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0);
set local role authenticated;
select pg_temp.dice('2');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(0, 0) = 56 and (pg_temp.game()).turn = 0, 'exact roll: home, another roll');

-- ---- a choice: only legal tokens may move ----
reset role;
select pg_temp.put(array[5,20,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0);
set local role authenticated;
select pg_temp.dice('2');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).dice = 2, 'two different moves: the player picks');
select pg_temp.fails($$select public.ludo_move(pg_temp.g(), 2, pg_temp.ver())$$, 'not_allowed');
select pg_temp.fails($$select public.ludo_move(pg_temp.g(), 9, pg_temp.ver())$$, 'not_allowed');
select public.ludo_move(pg_temp.g(), 1, pg_temp.ver());
select pg_temp.check(pg_temp.tok(0, 1) = 22 and pg_temp.tok(0, 0) = 5 and (pg_temp.game()).turn = 1, 'picked token moved');

-- ---- win: the first player home ends the game ----
reset role;
select pg_temp.put(array[56,56,56,55, 30,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0);
set local role authenticated;
select pg_temp.dice('1');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).status = 'finished' and (pg_temp.game()).winner = 0 and pg_temp.happened('win'), 'red wins');
select pg_temp.check((pg_temp.game()).deadline is null and (pg_temp.game()).ended_at is not null, 'finished: no clock');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_allowed');
select pg_temp.check(public.ludo_state(pg_temp.room())->>'status' = 'finished', 'the result stays visible');

-- ---- play again: the same colours ----
insert into v values ('g1', pg_temp.g()::text);
update v set id = public.ludo_open(pg_temp.room())->>'id' where k = 'g';
select pg_temp.check(pg_temp.g() <> (select id::uuid from v where k = 'g1'), 'a new game');
select pg_temp.check((pg_temp.game()).players = array['eeeeeeee-0000-0000-0000-000000000201', 'eeeeeeee-0000-0000-0000-000000000202',
                       'eeeeeeee-0000-0000-0000-000000000203', null]::uuid[], 'rematch: players keep their colours');
select public.ludo_start(pg_temp.g());

-- ---- timeouts: anyone in the room plays the turn once the 15 s are over ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000204');
select pg_temp.dice('4');
select public.ludo_timeout(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).turn = 0 and (pg_temp.game()).version = 1, 'too early: nothing happens');
reset role;
update public.ludo_games set deadline = now() - interval '1 second' where id = pg_temp.g();
set local role authenticated;
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_allowed');
insert into v values ('v', pg_temp.ver()::text);
select public.ludo_timeout(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).turn = 1 and (pg_temp.game()).misses[1] = 1 and pg_temp.happened('timeout') and pg_temp.happened('pass'),
                     'timeout: rolled for red (4, no move), turn passes, a miss counted');
select public.ludo_timeout(pg_temp.g(), (select id::int from v where k = 'v'));
select pg_temp.check((pg_temp.game()).turn = 1, 'a second call for the same turn does nothing');
reset role;
select pg_temp.put(array[3,10,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0, '{1,0,0,0}');
update public.ludo_games set deadline = now() - interval '1 second' where id = pg_temp.g();
set local role authenticated;
select pg_temp.dice('2');
select public.ludo_timeout(pg_temp.g(), pg_temp.ver());
select pg_temp.check(pg_temp.tok(0, 0) = 5 and pg_temp.tok(0, 1) = 10 and (pg_temp.game()).misses[1] = 2, 'timeout: first legal move played');
reset role;
select pg_temp.put(array[3,10,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1], 0, '{2,0,0,0}');
update public.ludo_games set deadline = now() - interval '1 second' where id = pg_temp.g();
set local role authenticated;
select public.ludo_timeout(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).out[1] and pg_temp.tok(0, 0) = -1 and pg_temp.tok(0, 1) = -1 and (pg_temp.game()).turn = 1,
                     'third timeout in a row: red is out, tokens off the board');
select pg_temp.check((pg_temp.game()).status = 'playing', 'two players left: the game goes on');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select pg_temp.fails($$select public.ludo_roll(pg_temp.g(), pg_temp.ver())$$, 'not_allowed');

-- ---- leaving the room is a forfeit; one player left wins ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000203');
select public.leave_voice(pg_temp.room());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select public.ludo_roll(pg_temp.g(), pg_temp.ver());
select pg_temp.check((pg_temp.game()).out[3] and (pg_temp.game()).status = 'finished' and (pg_temp.game()).winner = 1,
                     'yellow left the room: out, green wins');
select pg_temp.check(exists (select 1 from jsonb_array_elements((pg_temp.game()).events) e where e->>'k' = 'out' and e->>'why' = 'left'), 'forfeit event');

-- ---- quitting a game; closing ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
update v set id = public.ludo_open(pg_temp.room())->>'id' where k = 'g';
select pg_temp.check((pg_temp.game()).players[3] is null, 'rematch: whoever left the room is not seated');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000204');
select public.ludo_join(pg_temp.g(), 3);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select public.ludo_start(pg_temp.g());
select public.ludo_leave(pg_temp.g());
select pg_temp.check((pg_temp.game()).out[1] and (pg_temp.game()).turn = 1 and (pg_temp.game()).status = 'playing', 'red quits: next player rolls');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000202');
select pg_temp.fails($$select public.ludo_close(pg_temp.g())$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000201');
select public.ludo_close(pg_temp.g());
select pg_temp.check((pg_temp.game()).status = 'cancelled', 'host closed the game');
select pg_temp.check((select count(*) from public.ludo_games where room_id = pg_temp.room() and status in ('lobby', 'playing')) = 0, 'no open game');

-- ---- a room that ends cancels its game ----
update v set id = public.ludo_open(pg_temp.room())->>'id' where k = 'g';
select public.end_voice_room(pg_temp.room());
select pg_temp.fails($$select public.ludo_join(pg_temp.g(), 1)$$, 'room_closed');

select 'ludo: all checks passed' as result;
rollback;
