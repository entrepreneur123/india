-- The owner, 2026-10-26 (31-33, 35).
-- 31 Friends on the computer's side panel like League of Legends: what each friend is doing now. friend_activity():
--    'offline' (the app isn't open), 'chat' (in a random chat), 'matching' (searching), 'room' (in a room) or 'online'.
-- 32 Friends' status messages on Explore, newest change first (Instagram's notes over the DMs): profiles.status_at is
--    stamped when the status changes (trigger), appended to profiles_seen and profiles_public (061's rule).
-- 33 Gifts: a look bought for a friend. gift_check(to, item) before paying ('ok', 'not_friends', 'owned', 'bad_item');
--    redeem_gift (verify-purchase, service role) grants it to the friend, keeps who gave it (gifts) and tells them
--    (notification 'gift' with the item). A refund takes it back from the friend (play_revoke follows purchases).
-- 35 VIP: people who aren't friends can't message someone in VIP (dm_from 'everyone' doesn't count while anon); friend
--    requests still work. Turning VIP off or on reaches the rooms I'm in at once (my card is sent again).

-- 31 + 32 ----------------------------------------------------------------------------------------------------------
alter table public.profiles add column if not exists status_at timestamptz;

create or replace function public.profile_status_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.status is distinct from old.status then
    new.status_at := case when nullif(btrim(coalesce(new.status, '')), '') is null then null else now() end;
  end if;
  return new;
end $$;
create or replace trigger profiles_status_at before update of status on public.profiles
  for each row execute function public.profile_status_at();

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
        case when x.m then null::timestamptz else p.status_at end as status_at
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
        case when x.m then null::timestamptz else p.status_at end as status_at
   from public.profiles p cross join lateral (select public.mask_seen(p.*, true) as m) x;

create or replace function public.friend_activity()
returns table (id uuid, presence text, status text, status_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select f.id,
         case
           when not public.user_online(f.id) then 'offline'
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

-- 33 -----------------------------------------------------------------------------------------------------------------
create table if not exists public.gifts (
  token text primary key,
  from_user uuid not null references public.profiles (id) on delete cascade,
  to_user uuid not null references public.profiles (id) on delete cascade,
  item_id text not null references public.cosmetic_items (id),
  created_at timestamptz not null default now()
);
create index if not exists gifts_to on public.gifts (to_user, created_at desc);
alter table public.gifts enable row level security;
revoke all on public.gifts from public, anon, authenticated;

alter table public.notifications add column if not exists item_id text;
alter table public.notifications drop constraint if exists notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('message', 'friend_request', 'friend_accept', 'room_invite', 'post_like', 'post_comment', 'call', 'gift'));

create or replace function public.gift_ok(p_from uuid, p_to uuid, p_item text) returns text
language sql stable security definer set search_path = '' as $$
  select case
    when not exists (select 1 from public.cosmetic_items i where i.id = p_item and i.active) then 'bad_item'
    when p_from is null or p_to is null or p_from = p_to or not public.are_friends(p_from, p_to) or public.is_blocked(p_from, p_to) then 'not_friends'
    when public.owns(p_to, p_item) then 'owned'
    else 'ok'
  end
$$;
revoke execute on function public.gift_ok(uuid, uuid, text) from public, anon, authenticated;

create or replace function public.gift_check(p_to uuid, p_item text) returns text
language sql stable security definer set search_path = '' as $$
  select public.gift_ok(auth.uid(), p_to, p_item)
$$;

create or replace function public.redeem_gift(p_from uuid, p_to uuid, p_item text, p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare why text; r text;
begin
  if exists (select 1 from public.gifts g where g.token = p_token) then
    return case when (select g.from_user from public.gifts g where g.token = p_token) = p_from then 'already' else 'other_account' end;
  end if;
  why := public.gift_ok(p_from, p_to, p_item);
  if why <> 'ok' then return why; end if;
  r := public.redeem_cosmetic(p_to, p_item, p_token);
  if r = 'granted' then
    insert into public.gifts (token, from_user, to_user, item_id) values (p_token, p_from, p_to, p_item);
    insert into public.notifications (user_id, kind, actor, item_id) values (p_to, 'gift', p_from, p_item);
  end if;
  return r;
end $$;
revoke execute on function public.redeem_gift(uuid, uuid, text, text) from public, anon, authenticated;
grant execute on function public.redeem_gift(uuid, uuid, text, text) to service_role;

create or replace function public.inbox2(p_before bigint default null, p_limit integer default 30) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', o.id, 'kind', o.kind, 'n', o.n, 'created_at', o.created_at, 'read_at', o.read_at,
           'match_id', o.match_id, 'room_id', o.room_id, 'room_title', r.title,
           'room_open', r.id is not null and r.ended_at is null and public.voice_alive(r),
           'pending', o.kind = 'friend_request' and exists (select 1 from public.friendships f
             where f.requester = o.actor and f.addressee = o.user_id and f.status = 'pending'),
           'post', case when o.post_id is not null then (select jsonb_build_object('id', pp.id, 'body', left(pp.body, 80), 'image_path', pp.image_path)
                                                          from public.posts pp where pp.id = o.post_id) end,
           'item', o.item_id,
           'actor', jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
             'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)) order by o.id desc), '[]')
    from (select x.* from public.notifications x
           where x.user_id = auth.uid() and (p_before is null or x.id < p_before) and not public.is_blocked(x.user_id, x.actor)
           order by x.id desc limit least(greatest(coalesce(p_limit, 30), 1), 100)) o
    join public.profiles_seen p on p.id = o.actor
    left join public.voice_rooms r on r.id = o.room_id
$$;

-- 35 -----------------------------------------------------------------------------------------------------------------
create or replace function public.set_anon(p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members;
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_on and not public.owns(auth.uid(), 'vip_anon') then raise exception 'not_owned' using errcode = 'P0001'; end if;
  update public.profiles set anon = coalesce(p_on, false) where id = auth.uid();
  for m in select * from public.voice_members x where x.user_id = auth.uid() and x.left_at is null and not x.kicked loop
    perform public.voice_emit(m.room_id, public.voice_member_json(m));
  end loop;
  update public.voice_list_cache set at = '-infinity' where id = 1;
end $$;

create or replace function public.profile_social(p_user uuid)
returns table (likes int, friends int, liked boolean, can_message boolean)
language sql stable security definer set search_path = '' as $$
  select (select count(*) from public.profile_likes l where l.liked = p_user)::int,
         (select count(*) from public.friendships f where f.status = 'accepted' and p_user in (f.requester, f.addressee))::int,
         exists (select 1 from public.profile_likes l where l.liker = auth.uid() and l.liked = p_user),
         p_user <> auth.uid() and not public.is_blocked(auth.uid(), p_user)
           and (public.are_friends(auth.uid(), p_user)
                or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone' and not x.anon))
   where public.can_view(p_user)
$$;

create or replace function public.friend_thread(p_user uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid; friends boolean := public.are_friends(me, p_user);
begin
  if public.is_blocked(me, p_user)
     or not (friends or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone' and not x.anon)) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)) and (friends or x.kind = 'dm')
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and not public.is_blocked(m.user_a, m.user_b)
                 and (m.ended_at is null or public.are_friends(m.user_a, m.user_b)
                      or (m.kind = 'dm' and exists (select 1 from public.profiles x where x.id in (m.user_a, m.user_b)
                                                      and x.dm_from = 'everyone' and not x.anon))))
$$;

revoke execute on function public.friend_activity(), public.gift_check(uuid, text) from public, anon;
grant execute on function public.friend_activity(), public.gift_check(uuid, text) to authenticated;
