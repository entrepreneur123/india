-- 45 Room decoration (the owner, 2026-10-26): the host or a co-host dresses the room in one of the walls they bought
--    (067's moving scenes); everyone in the room sees it. voice_set_theme(room, wall or null).
-- 46 Chat-only rooms: chosen when the room is made (create_voice_room3's p_voice false). Nobody takes a seat or raises
--    a hand there: a trigger keeps seat and hand empty, so every path (a seat taken, an invite answered, the host
--    handed over) leaves people in the chat; with nobody on a seat the app never opens the room's audio.

alter table public.voice_rooms
  add column if not exists voice boolean not null default true,
  add column if not exists theme text references public.cosmetic_items (id) on delete set null;

-- 46 ------------------------------------------------------------------------------------------------------------------
create or replace function public.voice_chat_only_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if not coalesce((select r.voice from public.voice_rooms r where r.id = new.room_id), true) then
    new.seat := null;
    new.hand_at := null;
  end if;
  return new;
end $$;
create or replace trigger voice_members_chat_only before insert or update of seat, hand_at on public.voice_members
  for each row when (new.seat is not null or new.hand_at is not null) execute function public.voice_chat_only_guard();

drop function if exists public.create_voice_room3(text, text, integer, text, integer, integer, boolean, text, text);
create function public.create_voice_room3(p_title text, p_category text, p_max integer, p_gender text, p_min_age integer,
  p_max_age integer, p_private boolean, p_announcement text, p_lang text, p_voice boolean default true)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare j jsonb;
begin
  j := public.create_voice_room2(p_title, p_category, p_max, p_gender, p_min_age, p_max_age, p_private, p_announcement);
  update public.voice_rooms set lang = coalesce(public.voice_lang(p_lang), lang), voice = coalesce(p_voice, true) where id = j->>'id';
  return j;
end $$;

-- 45 ------------------------------------------------------------------------------------------------------------------
create or replace function public.voice_set_theme(p_room text, p_item text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_mod(p_room);
begin
  if p_item is not null and (p_item !~ '^wall_' or not public.owns(m.user_id, p_item)) then
    raise exception 'not_owned' using errcode = 'P0001';
  end if;
  update public.voice_rooms set theme = p_item, rev = rev + 1 where id = p_room and theme is distinct from p_item;
end $$;

-- The room as the app reads it, and its change events: + voice, theme.
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'seq', coalesce((select s.seq from public.voice_seq s where s.room_id = r.id), 0),
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music, 'stage', r.stage,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.voted),
               'voted', exists (select 1 from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.user_id = me and v.voted))
             order by n)
        from jsonb_array_elements(r.queue) with ordinality as x(q, n)), '[]'),
    'announcement', r.announcement, 'max', r.max_members, 'gender', r.gender_rule, 'minAge', r.min_age, 'maxAge', r.max_age,
    'private', r.is_private, 'lang', r.lang, 'voice', r.voice, 'theme', r.theme,
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'password', case when r.host = me then r.password end,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
               'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country,
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'lost', m.lost_at is not null, 'hand', m.hand_at is not null,
               'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint, 'j', floor(extract(epoch from m.joined_at) * 1000)::bigint)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles_public p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;

create or replace function public.voice_room_event() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_emit(new.id, jsonb_build_object(
    't', 'r', 'title', new.title, 'category', new.category, 'mode', new.speak_mode, 'host', new.host,
    'locked', to_jsonb(new.locked_seats), 'music', new.music, 'stage', new.stage,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = new.id and v.item_id = q->>'id' and v.voted))
             order by n)
        from jsonb_array_elements(new.queue) with ordinality as x(q, n)), '[]'),
    'announcement', new.announcement, 'max', new.max_members, 'gender', new.gender_rule, 'minAge', new.min_age,
    'maxAge', new.max_age, 'private', new.is_private, 'ended', new.ended_at is not null, 'theme', new.theme));
  return null;
end $$;

drop trigger if exists voice_rooms_event on public.voice_rooms;
create trigger voice_rooms_event after update on public.voice_rooms for each row
  when (old.title is distinct from new.title or old.category is distinct from new.category
        or old.speak_mode is distinct from new.speak_mode or old.host is distinct from new.host
        or old.locked_seats is distinct from new.locked_seats or old.music is distinct from new.music
        or old.queue is distinct from new.queue or old.announcement is distinct from new.announcement
        or old.max_members is distinct from new.max_members or old.gender_rule is distinct from new.gender_rule
        or old.min_age is distinct from new.min_age or old.max_age is distinct from new.max_age
        or old.is_private is distinct from new.is_private or old.ended_at is distinct from new.ended_at
        or old.stage is distinct from new.stage or old.theme is distinct from new.theme)
  execute function public.voice_room_event();

revoke execute on function public.voice_chat_only_guard() from public, anon, authenticated;
revoke execute on function public.create_voice_room3(text, text, integer, text, integer, integer, boolean, text, text, boolean),
  public.voice_set_theme(text, text) from public, anon;
grant execute on function public.create_voice_room3(text, text, integer, text, integer, integer, boolean, text, text, boolean),
  public.voice_set_theme(text, text) to authenticated;
