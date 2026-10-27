-- The owner, 2026-10-27: a room can have a cover photo and hashtags, the room list sorts by people, relevance or
-- popularity, and posts have hashtags too.
-- 1. hashtags(text): the #words of a text (letters of any script, digits, "_"; 1-30 long), lower-cased, at most 10.
--    Rooms keep up to 5 (voice_rooms.tags), posts keep theirs in posts.tags (generated from the text, GIN-indexed).
-- 2. voice_set_extras(room, cover, tags): the host or a co-host sets the cover (a photo they uploaded to their own
--    "cover" folder) and the hashtags. Anyone may see a live room's cover (can_read_media).
-- 3. voice_list5 = voice_list4 + cover_path, tags and p_sort: 'members' (as before), 'popular' (people plus what was
--    written in the last 15 minutes) or 'relevant' (my interests and language, my country, the search). A search that
--    starts with "#" looks for that tag. voice_list4 stays for installed apps as a wrapper (a drop needs the owner).
-- 4. posts_by_tag(tag, before): posts I may see with that hashtag, newest first, 30 at a time.

-- 1 ------------------------------------------------------------------------------------------------------------------
create or replace function public.hashtags(p text) returns text[]
language sql immutable parallel safe set search_path = '' as $$
  select coalesce(array(select distinct lower(m[1])
                          from regexp_matches(coalesce(p, ''), '#((?:[^[:space:][:punct:]]|_){1,30})', 'g') m limit 10), '{}')
$$;

alter table public.voice_rooms
  add column if not exists cover_path text,
  add column if not exists tags text[] not null default '{}';
create index if not exists voice_rooms_cover on public.voice_rooms (cover_path) where cover_path is not null;

alter table public.posts add column if not exists tags text[] generated always as (public.hashtags(body)) stored;
create index if not exists posts_tags on public.posts using gin (tags);

-- 2 ------------------------------------------------------------------------------------------------------------------
create or replace function public.voice_set_extras(p_room text, p_cover text, p_tags text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_mod(p_room);
begin
  if p_cover is not null and p_cover !~ ('^' || m.user_id::text || '/cover/[^/]+$') then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  update public.voice_rooms
     set cover_path = p_cover, tags = (public.hashtags('#' || array_to_string(coalesce(p_tags, '{}'), ' #')))[1:5]
   where id = p_room;
end $$;

create or replace function public.can_read_media(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] = 'cover'
         and exists (select 1 from public.voice_rooms r where r.cover_path = object_name and r.ended_at is null) then true
    when (storage.foldername(object_name))[2] in ('avatar', 'cover') then public.can_view(((storage.foldername(object_name))[1])::uuid)
          and not exists (select 1 from public.profiles x where x.id = ((storage.foldername(object_name))[1])::uuid and x.anon
                            and not public.are_friends(auth.uid(), x.id))
    when (storage.foldername(object_name))[2] = 'posts' then exists (
      select 1 from public.posts p where p.image_path = object_name and public.post_visible(p.user_id, p.visibility))
    when (storage.foldername(object_name))[2] = 'chat' then exists (
      select 1 from public.messages m join public.matches x on x.id = m.match_id
       where m.media_path = object_name and auth.uid() in (x.user_a, x.user_b)
         and not public.is_blocked(x.user_a, x.user_b))
    when (storage.foldername(object_name))[2] = 'rooms' then exists (
      select 1 from public.voice_messages v join public.voice_members vm on vm.room_id = v.room_id and vm.user_id = auth.uid()
       where v.media_path = object_name and not vm.kicked and (vm.left_at is null or vm.seen_at > now() - interval '1 day')
         and not public.blocked_with(v.sender))
    else false
  end or public.is_admin()
$$;

-- The open room as the app reads it: + cover, tags.
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
    'private', r.is_private, 'lang', r.lang, 'voice', r.voice, 'theme', r.theme, 'cover', r.cover_path, 'tags', to_jsonb(r.tags),
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

-- 3 ------------------------------------------------------------------------------------------------------------------
create or replace function public.voice_list_refresh() returns void
language sql security definer set search_path = '' as $$
  update public.voice_list_cache set at = now(), rooms = coalesce((select jsonb_agg(to_jsonb(x)) from (
    select r.id, r.title, r.category, r.host, r.created_at, r.max_members, r.gender_rule, r.min_age, r.max_age, r.is_private,
           r.announcement, r.lang, case when r.music is not null then coalesce(r.music->>'title', '') end as now_playing,
           (select g.status from public.ludo_games g where g.room_id = r.id and g.status in ('lobby', 'playing') limit 1) as game,
           (select jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                     'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)
              from public.profiles_public p where p.id = r.host) as host_card,
           coalesce((select jsonb_agg(s.card order by s.seat) from (
              select h.seat, jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                       'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country) as card
                from public.voice_members h join public.profiles_public p on p.id = h.user_id
               where h.room_id = r.id and h.seat is not null and h.left_at is null and not h.kicked
                 and (h.seen_at > now() - interval '90 seconds' or h.user_id = r.host)
               order by h.seat limit 4) s), '[]') as speakers,
           c.members, c.listeners, c.fresh, r.password is not null as locked, r.cover_path, r.tags,
           (select count(*)::int from public.voice_messages v where v.room_id = r.id and v.created_at > now() - interval '15 minutes') as talk
      from public.voice_rooms r
      join lateral (
        select count(*)::int as members, (count(*) filter (where m.seat is null))::int as listeners,
               (count(*) filter (where m.seen_at > now() - interval '90 seconds'))::int as fresh
          from public.voice_members m
         where m.room_id = r.id and m.left_at is null and not m.kicked
           and (m.seen_at > now() - interval '90 seconds' or m.user_id = r.host)) c on true
     where r.ended_at is null
       and exists (select 1 from public.voice_members m where m.room_id = r.id and m.left_at is null and not m.kicked
                     and m.seen_at > now() - interval '2 minutes' and (m.user_id = r.host or m.role = 'cohost'))
     order by c.members desc, r.created_at desc
     limit 1000) x), '[]')
   where id = 1
$$;

create or replace function public.voice_list5(p_query text default null, p_lang text default null, p_countries text[] default null,
  p_locked boolean default true, p_sort text default 'members')
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
  now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
  announcement text, code text, refusal text, lang text, cover_path text, tags text[])
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare me uuid := auth.uid(); q text := nullif(btrim(coalesce(p_query, '')), ''); l text := public.voice_lang(p_lang);
  cs text[] := nullif(p_countries, '{}'); tag text := (public.hashtags(nullif(btrim(coalesce(p_query, '')), '')))[1];
  sort_by text := coalesce(p_sort, 'members');
begin
  if char_length(q) > 40 or cardinality(cs) > 300 or sort_by not in ('members', 'popular', 'relevant') then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if left(q, 1) <> '#' then tag := null; end if;
  if (select c.at from public.voice_list_cache c where c.id = 1) < now() - interval '5 seconds'
     and pg_try_advisory_xact_lock(hashtext('voice_list_refresh')) then
    perform public.voice_list_refresh();
  end if;
  return query
  with p as (
    select x.gender, x.country, extract(year from age(x.birth_date))::int as age, coalesce(x.interests, '{}') as interests
      from public.profiles x where x.id = me
  ), k as (
    select m.room_id, m.kicked, m.seat,
           m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds' as fresh,
           m.left_at is null and not m.kicked as present
      from public.voice_members m where m.user_id = me
  ), c as (
    select * from jsonb_to_recordset((select y.rooms from public.voice_list_cache y where y.id = 1)) as x(
      id text, title text, category text, host uuid, created_at timestamptz, max_members int, gender_rule text, min_age int,
      max_age int, is_private boolean, announcement text, lang text, now_playing text, game text, host_card jsonb, speakers jsonb,
      members int, listeners int, fresh int, locked boolean, cover_path text, tags text[], talk int)
  ), v as (
    select c.*, coalesce(k.present and (k.fresh or c.host = me), false) as here, k.seat as my_seat, coalesce(k.fresh, false) as my_fresh,
           coalesce(k.present, false) as inside, k.room_id is not null as known
      from c join public.voice_rooms r on r.id = c.id and r.ended_at is null
      left join k on k.room_id = c.id
     where not coalesce(k.kicked, false)
       and (q is null or (tag is not null and tag = any (coalesce(c.tags, '{}')))
            or (tag is null and (strpos(lower(c.title), lower(q)) > 0 or lower(q) = any (coalesce(c.tags, '{}')))))
       and (l is null or c.lang = l)
       and (cs is null or c.host_card->>'country' = any (cs))
       and (coalesce(p_locked, true) or c.host = me or k.present or not (coalesce(c.locked, false) or (c.is_private and k.room_id is null)))
       and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = c.host)
       and not exists (select 1 from public.blocks b where b.blocker = c.host and b.blocked = me)
  )
  select v.id, v.title, v.category, v.host = me, v.host_card, v.speakers,
         v.listeners - (v.here and v.my_seat is null)::int, v.members, v.now_playing, v.game,
         v.max_members, v.gender_rule, v.min_age, v.max_age, v.is_private, v.announcement,
         case when v.host = me then (select x.code from public.voice_room_codes x where x.room_id = v.id) end,
         case
           when v.host = me then null
           when v.gender_rule <> 'any' and (select p.gender from p) is distinct from v.gender_rule then 'room_gender'
           when (select p.age from p) < v.min_age or (select p.age from p) > v.max_age then 'room_age'
           when not v.here and v.fresh - v.my_fresh::int >= v.max_members then 'room_full'
           when v.is_private and not v.known then 'room_private'
           when coalesce(v.locked, false) and not v.inside then 'room_password'
         end,
         v.lang, v.cover_path, coalesce(v.tags, '{}')
    from v
   order by v.host = me desc,
            case sort_by
              when 'popular' then v.members + 2 * coalesce(v.talk, 0)
              when 'relevant' then 3 * cardinality(array(select unnest(coalesce(v.tags, '{}')) intersect select unnest((select p.interests from p))))
                                   + 2 * (v.lang is not distinct from l)::int
                                   + 2 * (v.host_card->>'country' is not distinct from (select p.country from p))::int
                                   + 3 * (q is not null and strpos(lower(v.title), lower(q)) > 0)::int
              else 0
            end desc,
            v.host_card->>'country' is not distinct from (select p.country from p) desc, v.members desc, v.created_at desc
   limit 100;
end $$;

create or replace function public.voice_list4(p_query text default null, p_lang text default null, p_countries text[] default null,
  p_locked boolean default true)
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
  now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
  announcement text, code text, refusal text, lang text)
language sql security definer set search_path = '' as $$
  select v.id, v.title, v.category, v.mine, v.host, v.speakers, v.listeners, v.members, v.now_playing, v.game, v.max_members,
         v.gender_rule, v.min_age, v.max_age, v.is_private, v.announcement, v.code, v.refusal, v.lang
    from public.voice_list5(p_query, p_lang, p_countries, p_locked, 'members') v
$$;

-- 4 ------------------------------------------------------------------------------------------------------------------
create or replace function public.posts_by_tag(p_tag text, p_before timestamptz default null) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.post_json(p) order by p.created_at desc), '[]') from (
    select x.* from public.posts x
     where x.tags @> array[(public.hashtags('#' || ltrim(coalesce(p_tag, ''), '#')))[1]]
       and public.post_visible(x.user_id, x.visibility)
       and not exists (select 1 from public.profiles a where a.id = x.user_id and a.banned_until > now())
       and (p_before is null or x.created_at < p_before)
     order by x.created_at desc limit 30) p
$$;

revoke execute on function public.voice_set_extras(text, text, text[]), public.voice_list5(text, text, text[], boolean, text),
  public.posts_by_tag(text, timestamptz) from public, anon;
grant execute on function public.voice_set_extras(text, text, text[]), public.voice_list5(text, text, text[], boolean, text),
  public.posts_by_tag(text, timestamptz) to authenticated;
