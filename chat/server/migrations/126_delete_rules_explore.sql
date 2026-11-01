-- 126 (owner, 2026-11-01): account deletion rules, and Explore's online filter and feed status line.
--
-- 1. No deletion while suspended (banned_until > now(), permanent bans included): `leave_account` refuses with
--    banned_no_delete. It replaces delete-account's direct call to remember_ban and runs before anything is removed.
-- 2. For 24 hours after a deletion, the same phone number (phone_identities, 042) and the same ID document
--    (banned_identities, 035) can't start a new account: check_adult refuses a new profile with recently_deleted, and
--    kyc_apply answers 'declined' (the app's retryable outcome) for a document left within 24 hours. Guests and
--    social sign-ins have no number, so only a verified document covers them.
-- 3. explore_people: 'recommend' / 'online' / 'offline' are For you's filters (all, online, offline) and `n` is 1 for
--    an online person (hide_online reads offline, as in friend_activity2); the Online tab and the Following list
--    ('liked') are gone from Explore (the profile's people sheet lists whom I follow, 117).
-- 4. explore_feed2: also the status lines of friends and people I follow ('status' rows, newest change first), so
--    the feed's notes row is the same people as the feed; blocked people are left out of the feed.

alter table public.phone_identities add column if not exists left_at timestamptz;
alter table public.banned_identities add column if not exists left_at timestamptz;

-- 1, 2: called by delete-account (service role) before the person's files and auth user are removed.
create or replace function public.leave_account(uid uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.profiles p where p.id = uid and p.banned_until > now()) then
    raise exception 'banned_no_delete' using errcode = 'P0001';
  end if;
  perform public.remember_ban(uid);
  update public.phone_identities k set left_at = now()
    from auth.users u where u.id = uid and u.phone is not null and k.phone_hash = public.phone_hash(u.phone);
  insert into public.banned_identities (doc_hash, banned_until, left_at)
  select i.doc_hash, now(), now() from public.identities i where i.user_id = uid
  on conflict (doc_hash) do update set left_at = now();
end $$;
revoke execute on function public.leave_account(uuid) from public, anon, authenticated;

-- 123's check_adult, with the 24-hour wait after a deletion on the same number.
create or replace function public.check_adult() returns trigger
language plpgsql security definer set search_path = '' as $$
declare carried timestamptz; kept public.phone_identities; dialed text;
begin
  if public.is_guest() and not (select f.guests from public.app_flags f where f.id = 1) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select d.country into dialed from auth.users u join public.dial_codes d on u.phone like d.prefix || '%'
   where u.id = new.id order by length(d.prefix) desc limit 1;
  if dialed is not null then new.country := dialed;
  elsif new.country is not null and not exists (select 1 from public.dial_codes d where d.country = new.country) then new.country := null;
  end if;
  select k.* into kept from public.phone_identities k join auth.users u on k.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and k.saved_at > now() - interval '1 year';
  if kept.left_at > now() - interval '24 hours' then
    raise exception 'recently_deleted' using errcode = 'P0001';
  end if;
  if kept.phone_hash is not null then new.gender := kept.gender; new.birth_date := kept.birth_date; end if;
  if new.birth_date > (current_date - make_interval(years => public.adult_age(new.country)))::date then
    raise exception 'under_18' using errcode = 'P0001';
  end if;
  if new.birth_date < (current_date - interval '100 years')::date then
    raise exception 'invalid_birth_date' using errcode = 'P0001';
  end if;
  select b.banned_until into carried from public.banned_phones b join auth.users u on b.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and b.banned_until > now();
  if carried is not null then new.banned_until := carried; end if;
  return new;
end $$;

-- 035's kyc_apply, with the 24-hour wait after a deletion on the same document.
create or replace function public.kyc_apply(p_user uuid, p_session text, p_status text, p_doc jsonb) returns text
language plpgsql security definer set search_path = '' as $$
declare res text; dob date; g text; num text; st text; nat text; h text; c2 text; ban timestamptz;
begin
  if not exists (select 1 from public.kyc_sessions s where s.session_id = p_session and s.user_id = p_user) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.profiles p where p.id = p_user and p.id_verified_at is not null) then
    return 'verified';
  end if;
  if p_status <> 'Approved' then
    res := case when p_status in ('Declined', 'Abandoned', 'Expired') then 'declined' else 'pending' end;
  else
    dob := nullif(p_doc->>'date_of_birth', '')::date;
    num := upper(regexp_replace(coalesce(p_doc->>'document_number', p_doc->>'personal_number', ''), '[^A-Za-z0-9]', '', 'g'));
    st := upper(coalesce(nullif(p_doc->>'issuing_state', ''), nullif(p_doc->>'nationality', ''), ''));
    nat := upper(coalesce(nullif(p_doc->>'nationality', ''), nullif(p_doc->>'issuing_state', ''), ''));
    g := case upper(left(coalesce(p_doc->>'gender', ''), 1)) when 'M' then 'male' when 'F' then 'female' else 'other' end;
    if dob is null or num = '' then
      res := 'unreadable';
    else
      h := encode(extensions.digest((select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'id_pepper')
                                    || st || ':' || num, 'sha256'), 'hex');
      if exists (select 1 from public.banned_identities b where b.doc_hash = h and b.left_at > now() - interval '24 hours') then
        res := 'declined';
      elsif exists (select 1 from public.identities i where i.doc_hash = h and i.user_id <> p_user) then
        res := 'duplicate';
      else
        insert into public.identities (user_id, doc_hash, provider, session_id, gender, birth_date, nationality, issuing_state)
        values (p_user, h, 'didit', p_session, g, dob, nullif(nat, ''), nullif(st, ''))
        on conflict (user_id) do update set doc_hash = excluded.doc_hash, session_id = excluded.session_id, gender = excluded.gender,
          birth_date = excluded.birth_date, nationality = excluded.nationality, issuing_state = excluded.issuing_state, verified_at = now();
        if dob > (current_date - interval '18 years')::date then
          update public.profiles set banned_until = 'infinity' where id = p_user;
          res := 'minor';
        else
          select c.a2 into c2 from public.iso_countries c where c.a3 = nat;
          if c2 is null then select c.a2 into c2 from public.iso_countries c where c.a3 = st; end if;
          select b.banned_until into ban from public.banned_identities b where b.doc_hash = h and b.banned_until > now();
          update public.profiles set gender = g, birth_date = dob, country = coalesce(c2, country), id_verified_at = now(),
                 banned_until = case when ban is not null then greatest(coalesce(banned_until, ban), ban) else banned_until end
           where id = p_user;
          res := 'verified';
        end if;
      end if;
    end if;
  end if;
  update public.kyc_sessions set status = p_status, result = res, decided_at = case when res <> 'pending' then now() end
   where session_id = p_session;
  return res;
end $$;

-- 3. For you: 50 at random of a country, all / online / offline, with who is online; rankings as before (078).
create or replace function public.explore_people(p_kind text, p_country text default null)
returns table(id uuid, handle text, name text, photo_path text, age integer, gender text, country text, look jsonb, n integer)
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
  c text := coalesce(upper(nullif(p_country, '')), (select x.country from public.profiles x where x.id = me));
begin
  if p_kind not in ('recommend', 'online', 'offline', 'likes', 'friends') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if p_kind in ('recommend', 'online', 'offline') then
    return query
    select x.id, x.handle, x.name, x.photo_path, extract(year from age(x.birth_date))::int, x.gender, x.country, public.look_json(x),
           (not x.hide_online and public.user_online(x.id))::int
      from public.profiles x
     where x.country = c and x.id <> me and not x.anon and (x.banned_until is null or x.banned_until < now())
       and not public.is_blocked(me, x.id)
       and not exists (select 1 from public.friendships f
                        where (f.requester = me and f.addressee = x.id) or (f.requester = x.id and f.addressee = me))
       and (p_kind = 'recommend' or (p_kind = 'online') = (not x.hide_online and public.user_online(x.id)))
     order by random()
     limit 50;
    return;
  end if;
  return query
  with s as (
    select l.liked as uid, count(*)::int as cnt from public.profile_likes l where p_kind = 'likes' group by l.liked
    union all
    select f.uid, count(*)::int from (
        select requester as uid from public.friendships where status = 'accepted'
        union all select addressee from public.friendships where status = 'accepted') f
     where p_kind = 'friends' group by f.uid
  ), top as (
    select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int as age, p.gender, p.country,
           public.look_json(p) as look, s.cnt, false as mine
      from s join public.profiles p on p.id = s.uid
     where p.country = c and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now())
       and not public.is_blocked(me, p.id)
     order by s.cnt desc
     limit 50
  )
  select x.id, x.handle, x.name, x.photo_path, x.age, x.gender, x.country, x.look, x.cnt from (
    select * from top
    union all
    select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int, p.gender, p.country, public.look_json(p),
           case when p_kind = 'likes' then (select count(*)::int from public.profile_likes l where l.liked = me)
                else (select count(*)::int from public.friendships f where f.status = 'accepted' and me in (f.requester, f.addressee)) end,
           true
      from public.profiles p
     where p.id = me and p.country is not distinct from c and not p.anon
  ) x
  order by x.cnt desc nulls last, x.mine;
end $$;

-- 4. Friends' and followed people's posts and highlights (60, newest first), then their status lines (100, newest first).
create or replace function public.explore_feed2()
returns table(kind text, item text, user_id uuid, handle text, name text, photo_path text, look jsonb, body text, photos text[], at timestamptz, post jsonb)
language sql stable security definer set search_path = '' as $$
  with who as (
    select y.id, bool_or(y.friend) as friend from (
      select case when f.requester = auth.uid() then f.addressee else f.requester end as id, true as friend
        from public.friendships f where f.status = 'accepted' and auth.uid() in (f.requester, f.addressee)
      union all
      select l.liked, false from public.profile_likes l where l.liker = auth.uid()
    ) y group by y.id
  ), shown as (
    select w.id, w.friend, p.handle, p.name, p.photo_path, public.look_json(p) as look, p.status, p.status_at
      from who w join public.profiles p on p.id = w.id
     where (p.banned_until is null or p.banned_until < now()) and (w.friend or not p.anon) and not public.is_blocked(auth.uid(), p.id)
  )
  (select x.kind, x.item, s.id, s.handle, s.name, s.photo_path, s.look, x.body, x.photos, x.at,
          case when x.kind = 'post' then (select public.post_json(po) from public.posts po where po.id = x.item::uuid) end
     from (
       select 'post' as kind, po.id::text as item, po.user_id as uid, po.body, array_remove(array[po.image_path], null) as photos, po.created_at as at
         from shown w join public.posts po on po.user_id = w.id
        where w.friend or public.post_visible(po.user_id, po.visibility)
       union all
       select 'highlight', h.id::text, h.user_id, h.title, h.photos, h.created_at
         from shown w join public.highlights h on h.user_id = w.id
        where w.friend or public.post_visible(h.user_id, h.visibility)
     ) x join shown s on s.id = x.uid
    order by x.at desc
    limit 60)
  union all
  (select 'status', s.id::text, s.id, s.handle, s.name, s.photo_path, s.look, btrim(s.status), '{}'::text[], s.status_at, null::jsonb
     from shown s where nullif(btrim(coalesce(s.status, '')), '') is not null
    order by s.status_at desc nulls last
    limit 100)
$$;
