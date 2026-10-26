-- Hide online (the owner, 2026-10-26): profiles.hide_online, set by set_hide_online. Friends see me 'offline' on the
--    side panel (friend_activity), I'm left out of Explore's online list, and (40) friends can't call me — like
--    "appear offline". The online count stays a plain count.
-- 36 A partner's chat wall: look_json carries the wall, so in a random chat I can use the wall my partner bought
--    (only while I look at that chat; nothing is stored). Walls redrawn as moving backgrounds: the old six (nobody
--    owned one) give way to eight new ones, same price.
-- 40 Calls only when the other person is online: request_call refuses 'offline' in a friends' thread (the chat has
--    ended) when they aren't online or hide it. An open random chat calls as before.
-- 37 (check for 100k users) Security definer functions that anon could run (057, 059 left out the revoke): granted to
--    signed-in people only. None leaked anything (each checks the caller), this closes the door.

-- profiles + views (061's rule: a profiles column is appended to both views in the same migration) --------------------
alter table public.profiles add column if not exists hide_online boolean not null default false;

create or replace view public.profiles_seen with (security_barrier = true) as
 select p.id, case when x.m then 'VIP'::text else p.name end as name, p.gender,
        case when x.m then current_date else p.birth_date end as birth_date,
        case when x.m then ''::text else p.city end as city, case when x.m then ''::text else p.bio end as bio,
        case when x.m then null::text else p.photo_path end as photo_path, p.banned_until, p.created_at,
        case when x.m then null::text else p.country end as country, case when x.m then '{}'::text[] else p.interests end as interests,
        case when x.m then '{}'::text[] else p.photos end as photos, case when x.m then null::text else p.cover_path end as cover_path,
        case when x.m then null::text else p.accent end as accent, case when x.m then null::text else p.status end as status,
        case when x.m then '{}'::text[] else p.languages end as languages, case when x.m then null::jsonb else p.prompts end as prompts,
        case when x.m then null::timestamptz else p.id_verified_at end as id_verified_at, p.manner,
        case when x.m then ''::text else p.handle end as handle, p.handle_set_at,
        case when x.m then null::text else p.look_badge end as look_badge, case when x.m then null::text else p.look_color end as look_color,
        case when x.m then null::text else p.look_font end as look_font, case when x.m then null::text else p.look_frame end as look_frame,
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from,
        case when x.m then null::text[] else p.cycle_photos end as cycle_photos,
        case when x.m then null::text else p.look_effect end as look_effect,
        case when x.m then null::text else p.look_bubble end as look_bubble,
        case when x.m then null::timestamptz else p.status_at end as status_at,
        p.hide_online
   from public.profiles p cross join lateral (select public.mask_seen(p.*, false) as m) x;

create or replace view public.profiles_public with (security_barrier = true) as
 select p.id, case when x.m then 'VIP'::text else p.name end as name, p.gender,
        case when x.m then current_date else p.birth_date end as birth_date,
        case when x.m then ''::text else p.city end as city, case when x.m then ''::text else p.bio end as bio,
        case when x.m then null::text else p.photo_path end as photo_path, p.banned_until, p.created_at,
        case when x.m then null::text else p.country end as country, case when x.m then '{}'::text[] else p.interests end as interests,
        case when x.m then '{}'::text[] else p.photos end as photos, case when x.m then null::text else p.cover_path end as cover_path,
        case when x.m then null::text else p.accent end as accent, case when x.m then null::text else p.status end as status,
        case when x.m then '{}'::text[] else p.languages end as languages, case when x.m then null::jsonb else p.prompts end as prompts,
        case when x.m then null::timestamptz else p.id_verified_at end as id_verified_at, p.manner,
        case when x.m then ''::text else p.handle end as handle, p.handle_set_at,
        case when x.m then null::text else p.look_badge end as look_badge, case when x.m then null::text else p.look_color end as look_color,
        case when x.m then null::text else p.look_font end as look_font, case when x.m then null::text else p.look_frame end as look_frame,
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from,
        case when x.m then null::text[] else p.cycle_photos end as cycle_photos,
        case when x.m then null::text else p.look_effect end as look_effect,
        case when x.m then null::text else p.look_bubble end as look_bubble,
        case when x.m then null::timestamptz else p.status_at end as status_at,
        p.hide_online
   from public.profiles p cross join lateral (select public.mask_seen(p.*, true) as m) x;

-- Hide online ---------------------------------------------------------------------------------------------------------
create or replace function public.set_hide_online(p_on boolean) returns void
language sql security definer set search_path = '' as $$
  update public.profiles set hide_online = coalesce(p_on, false) where id = public.voice_me();
$$;

-- Online to others: the app is open and they don't hide it.
create or replace function public.seen_online(u uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.user_online(u) and not coalesce((select p.hide_online from public.profiles p where p.id = u), false)
$$;

create or replace function public.friend_activity()
returns table (id uuid, presence text, status text, status_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select f.id,
         case
           when p.hide_online or not public.user_online(f.id) then 'offline'
           when exists (select 1 from public.matches x where x.user_a = f.id and x.ended_at is null and x.kind = 'random'
                          and x.created_at > now() - interval '6 hours')
             or exists (select 1 from public.matches x where x.user_b = f.id and x.ended_at is null and x.kind = 'random'
                          and x.created_at > now() - interval '6 hours') then 'chat'
           when exists (select 1 from public.queue q where q.user_id = f.id and q.seen_at > now() - interval '20 seconds') then 'matching'
           when exists (select 1 from public.voice_members m where m.user_id = f.id and m.left_at is null and not m.kicked
                          and m.seen_at > now() - interval '90 seconds') then 'room'
           else 'online'
         end,
         nullif(btrim(coalesce(p.status, '')), ''), p.status_at
    from (select case when fr.requester = auth.uid() then fr.addressee else fr.requester end as id
            from public.friendships fr
           where auth.uid() in (fr.requester, fr.addressee) and fr.status = 'accepted' and not public.is_blocked(fr.requester, fr.addressee)
           limit 500) f
    join public.profiles p on p.id = f.id
$$;

create or replace function public.explore_people(p_kind text, p_country text default null)
returns table (id uuid, handle text, name text, photo_path text, age int, gender text, country text, look jsonb, n int)
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
  c text := coalesce(upper(nullif(p_country, '')), (select x.country from public.profiles x where x.id = me));
begin
  if p_kind not in ('recommend', 'online', 'likes', 'friends', 'liked') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  return query
  with s as (
    select p.id as uid, null::int as cnt, null::timestamptz as at from public.profiles p where p_kind = 'recommend' and p.country = c
    union all
    select u.user_id, null, u.seen_at from public.user_seen u where p_kind = 'online' and u.seen_at > now() - interval '60 seconds'
    union all
    select l.liked, count(*)::int, null from public.profile_likes l where p_kind = 'likes' group by l.liked
    union all
    select f.uid, count(*)::int, null from (
        select requester as uid from public.friendships where status = 'accepted'
        union all select addressee from public.friendships where status = 'accepted') f
     where p_kind = 'friends' group by f.uid
    union all
    select l.liked, null, l.created_at from public.profile_likes l where p_kind = 'liked' and l.liker = me
  )
  select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int, p.gender, p.country, public.look_json(p), s.cnt
    from s join public.profiles p on p.id = s.uid
   where (p_kind = 'liked' or p.country = c)
     and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
     and not (p_kind = 'online' and p.hide_online)
   order by case when p_kind = 'recommend' then random() end, s.cnt desc nulls last, s.at desc nulls last
   limit case when p_kind = 'online' then 100 else 50 end;
end $$;

-- 36 ------------------------------------------------------------------------------------------------------------------
create or replace function public.look_json(p public.profiles) returns jsonb
language sql stable set search_path = '' as $$
  select jsonb_build_object('id', p.id, 'handle', p.handle, 'badge', p.look_badge, 'color', p.look_color,
                            'font', p.look_font, 'frame', p.look_frame, 'cycle', p.cycle_photos,
                            'effect', p.look_effect, 'bubble', p.look_bubble, 'wall', p.look_wall)
$$;

delete from public.cosmetic_items c
 where c.kind = 'wall' and c.id in ('wall_saffron', 'wall_midnight', 'wall_paisley', 'wall_mehndi', 'wall_stars', 'wall_waves')
   and not exists (select 1 from public.user_cosmetics u where u.item_id = c.id)
   and not exists (select 1 from public.profiles p where p.look_wall = c.id);
insert into public.cosmetic_items (id, kind, price_inr, sort, active) values
  ('wall_aurora', 'wall', 79, 1, true), ('wall_neon', 'wall', 79, 2, true), ('wall_sakura', 'wall', 79, 3, true),
  ('wall_galaxy', 'wall', 79, 4, true), ('wall_ocean', 'wall', 79, 5, true), ('wall_lava', 'wall', 79, 6, true),
  ('wall_rain', 'wall', 79, 7, true), ('wall_diya', 'wall', 79, 8, true)
on conflict (id) do nothing;

-- 40 ------------------------------------------------------------------------------------------------------------------
create or replace function public.request_call(p_match uuid, p_video boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.matches x where x.id = p_match and auth.uid() in (x.user_a, x.user_b) and x.ended_at is not null
               and not public.seen_online(case when x.user_a = auth.uid() then x.user_b else x.user_a end)) then
    raise exception 'offline' using errcode = 'P0001';
  end if;
  update public.matches set video_status = 'requested', video_requested_by = auth.uid(), call_video = coalesce(p_video, true)
   where id = p_match and public.call_open(p_match) and video_status in ('none', 'declined');
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

-- grants --------------------------------------------------------------------------------------------------------------
revoke execute on function public.seen_online(uuid) from public, anon, authenticated;
revoke execute on function public.set_hide_online(boolean), public.delete_highlight(bigint), public.explore_feed(),
  public.explore_people(text, text), public.highlights_of(uuid), public.like_profile(uuid, boolean), public.profile_social(uuid),
  public.save_highlight(bigint, text, text[]), public.set_dm_from(text), public.posts_cap(), public.voice_line_guard()
  from public, anon;
grant execute on function public.set_hide_online(boolean), public.delete_highlight(bigint), public.explore_feed(),
  public.explore_people(text, text), public.highlights_of(uuid), public.like_profile(uuid, boolean), public.profile_social(uuid),
  public.save_highlight(bigint, text, text[]), public.set_dm_from(text)
  to authenticated;
