-- Yaari core schema. Every rule the client shows is enforced here too:
-- 18+, immutable gender/birth date, Premium-only gender filter and video, exit lock, blocks, auto-ban.

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 30),
  gender text not null check (gender in ('male', 'female', 'other')),
  birth_date date not null,
  city text not null default 'India' check (char_length(city) <= 30),
  bio text not null default '' check (char_length(bio) <= 150),
  photo_path text,
  banned_until timestamptz,
  created_at timestamptz not null default now()
);

create function public.check_adult() returns trigger language plpgsql set search_path = '' as $$
begin
  if new.birth_date > (current_date - interval '18 years')::date then
    raise exception 'under_18' using errcode = 'P0001';
  end if;
  if new.birth_date < (current_date - interval '100 years')::date then
    raise exception 'invalid_birth_date' using errcode = 'P0001';
  end if;
  return new;
end $$;
create trigger profiles_adult before insert on public.profiles for each row execute function public.check_adult();

create table public.subscriptions (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  premium_until timestamptz not null,
  purchase_token text,
  updated_at timestamptz not null default now()
);

create table public.blocks (
  blocker uuid not null references public.profiles (id) on delete cascade,
  blocked uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked)
);
create index blocks_blocked on public.blocks (blocked);

create table public.matches (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references public.profiles (id) on delete cascade, -- the one who was waiting
  user_b uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  ended_at timestamptz,
  ended_by uuid,
  video_status text not null default 'none' check (video_status in ('none', 'requested', 'accepted', 'declined')),
  video_requested_by uuid,
  check (user_a <> user_b)
);
create index matches_a on public.matches (user_a, created_at desc);
create index matches_b on public.matches (user_b, created_at desc);

create table public.queue (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  gender text not null,
  target text not null,
  joined_at timestamptz not null default now(),
  seen_at timestamptz not null default now()
);

create table public.messages (
  id bigint generated always as identity primary key,
  match_id uuid not null references public.matches (id) on delete cascade,
  sender uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  body text check (char_length(body) <= 1000),
  media_path text,
  media_kind text check (media_kind in ('image', 'video')),
  created_at timestamptz not null default now(),
  check (coalesce(btrim(body), '') <> '' or media_path is not null),
  check ((media_path is null) = (media_kind is null))
);
create index messages_match on public.messages (match_id, id);

create table public.posts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  body text not null default '' check (char_length(body) <= 500),
  image_path text,
  created_at timestamptz not null default now(),
  check (btrim(body) <> '' or image_path is not null)
);
create index posts_user on public.posts (user_id, created_at desc);

create table public.reports (
  id bigint generated always as identity primary key,
  reporter uuid not null references public.profiles (id) on delete cascade,
  reported uuid not null references public.profiles (id) on delete cascade,
  match_id uuid references public.matches (id) on delete set null,
  reason text not null check (reason in ('nudity', 'minor', 'harassment', 'fake', 'other')),
  created_at timestamptz not null default now(),
  reviewed boolean not null default false
);
create index reports_reported on public.reports (reported, created_at desc);

-- ---------- helpers ----------

create function public.is_premium(uid uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.subscriptions s where s.user_id = uid and s.premium_until > now())
$$;

create function public.is_blocked(x uuid, y uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.blocks b where (b.blocker = x and b.blocked = y) or (b.blocker = y and b.blocked = x))
$$;

-- You may see someone's profile and posts only after being matched with them, and never across a block.
create function public.can_view(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and exists (
      select 1 from public.matches m
      where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other)))
$$;

create function public.is_participant(mid uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b))
$$;

create function public.exit_lock() returns interval language sql stable security definer set search_path = '' as $$
  select case when public.is_premium(auth.uid()) then interval '200 milliseconds' else interval '5 seconds' end
$$;

-- ---------- RLS ----------

alter table public.profiles enable row level security;
alter table public.subscriptions enable row level security;
alter table public.blocks enable row level security;
alter table public.matches enable row level security;
alter table public.queue enable row level security;
alter table public.messages enable row level security;
alter table public.posts enable row level security;
alter table public.reports enable row level security;

create policy "own profile" on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy "create own profile" on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy "edit own profile" on public.profiles for update to authenticated using (id = (select auth.uid()));
create policy "own subscription" on public.subscriptions for select to authenticated using (user_id = (select auth.uid()));
create policy "own blocks" on public.blocks for select to authenticated using (blocker = (select auth.uid()));
create policy "my matches" on public.matches for select to authenticated using ((select auth.uid()) in (user_a, user_b));
create policy "read chat" on public.messages for select to authenticated using (public.is_participant(match_id));
create policy "write chat" on public.messages for insert to authenticated with check (
  sender = (select auth.uid()) and public.is_participant(match_id)
  and not exists (
    select 1 from public.matches m where m.id = match_id
    and public.is_blocked(m.user_a, m.user_b)));
create policy "read posts" on public.posts for select to authenticated using (public.can_view(user_id));
create policy "write posts" on public.posts for insert to authenticated with check (user_id = (select auth.uid()));
create policy "delete posts" on public.posts for delete to authenticated using (user_id = (select auth.uid()));

-- Clients get exactly the columns they may touch. Gender and birth date can't be changed after sign-up.
revoke all on all tables in schema public from anon, authenticated;
grant select on public.profiles, public.subscriptions, public.blocks, public.matches, public.messages, public.posts to authenticated;
grant insert (id, name, gender, birth_date, city, bio, photo_path) on public.profiles to authenticated;
grant update (name, city, bio, photo_path) on public.profiles to authenticated;
grant insert (match_id, body, media_path, media_kind) on public.messages to authenticated;
grant insert (body, image_path) on public.posts to authenticated;
grant delete on public.posts to authenticated;

-- Anti-spam: at most 10 messages per 10 seconds; banned users cannot send.
create function public.message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.profiles p where p.id = new.sender and p.banned_until > now()) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if (select count(*) from public.messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return new;
end $$;
create trigger messages_guard before insert on public.messages for each row execute function public.message_guard();

-- ---------- RPCs ----------

-- Public part of a profile, with age instead of birth date. Null if you may not see it.
create function public.profile_card(uid uuid)
returns table (id uuid, name text, gender text, age int, city text, bio text, photo_path text)
language sql stable security definer set search_path = '' as $$
  select p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path
  from public.profiles p where p.id = uid and public.can_view(uid)
$$;

-- Returns a match id, or null when queued (call again every few seconds; that also keeps your queue spot alive).
create function public.find_match(p_target text) returns uuid language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  banned timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.banned_until into my_gender, banned from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if p_target <> 'any' and not public.is_premium(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(7340001); -- one matcher at a time: no double matches, no lost wakeups

  -- Someone picked me while I was waiting.
  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;

  -- Searching again ends any chat I am still in, but never before the exit lock (no skipping the 5 s via the API).
  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;

  select case when x.user_a = me then x.user_b else x.user_a end into last_partner
    from public.matches x where me in (x.user_a, x.user_b) order by x.created_at desc limit 1;

  select q.user_id into partner
    from public.queue q join public.profiles p on p.id = q.user_id
   where q.user_id <> me
     and q.seen_at > now() - interval '20 seconds'
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and (p.banned_until is null or p.banned_until < now())
     and not public.is_blocked(me, q.user_id)
     and q.user_id is distinct from last_partner
   order by q.joined_at
   limit 1;

  if partner is not null then
    delete from public.queue where user_id in (me, partner);
    insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
    return mid;
  end if;

  insert into public.queue (user_id, gender, target) values (me, my_gender, p_target)
  on conflict (user_id) do update set gender = excluded.gender, target = excluded.target, seen_at = now();
  return null;
end $$;

create function public.cancel_search() returns void language sql security definer set search_path = '' as $$
  delete from public.queue where user_id = auth.uid()
$$;

create function public.leave_match(p_match uuid) returns void language plpgsql security definer set search_path = '' as $$
declare m public.matches;
begin
  select * into m from public.matches where id = p_match and auth.uid() in (user_a, user_b) for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if m.ended_at is not null then return; end if; -- partner already left: no lock
  if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
  update public.matches set ended_at = now(), ended_by = auth.uid(), video_status = 'none' where id = p_match;
end $$;

-- Always allowed, never locked. Blocks the other person and ends the chat if it is still open.
create function public.report_user(p_reported uuid, p_reason text, p_match uuid default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_reported = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.matches x where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported)) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason);
  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
  -- Automatic first response (well inside the 2-hour window): 3 different reporters in 24 h = 7-day ban pending review.
  if (select count(distinct r.reporter) from public.reports r where r.reported = p_reported and r.created_at > now() - interval '24 hours') >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;

create function public.request_video(p_match uuid) returns void language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_premium(auth.uid()) then raise exception 'premium_required' using errcode = 'P0001'; end if;
  update public.matches set video_status = 'requested', video_requested_by = auth.uid()
   where id = p_match and auth.uid() in (user_a, user_b) and ended_at is null and video_status in ('none', 'declined');
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

create function public.respond_video(p_match uuid, p_accept boolean) returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.matches set video_status = case when p_accept then 'accepted' else 'declined' end
   where id = p_match and auth.uid() in (user_a, user_b) and auth.uid() <> video_requested_by
     and ended_at is null and video_status = 'requested';
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

create function public.end_video(p_match uuid) returns void language sql security definer set search_path = '' as $$
  update public.matches set video_status = 'none' where id = p_match and auth.uid() in (user_a, user_b)
$$;

-- Latest match per person, newest first, without blocked people.
create function public.my_recents()
returns table (match_id uuid, partner uuid, met_at timestamptz, last_body text)
language sql stable security definer set search_path = '' as $$
  select distinct on (o.partner) o.id, o.partner, o.created_at,
         (select coalesce(msg.body, '[media]') from public.messages msg where msg.match_id = o.id order by msg.id desc limit 1)
    from (select x.id, x.created_at, case when x.user_a = auth.uid() then x.user_b else x.user_a end as partner
            from public.matches x where auth.uid() in (x.user_a, x.user_b)) o
   where not public.is_blocked(auth.uid(), o.partner)
   order by o.partner, o.created_at desc
$$;

revoke execute on all functions in schema public from public, anon;
grant execute on function public.profile_card, public.find_match, public.cancel_search, public.leave_match,
  public.report_user, public.request_video, public.respond_video, public.end_video, public.my_recents,
  public.is_premium, public.can_view, public.is_participant, public.is_blocked, public.exit_lock to authenticated;

alter publication supabase_realtime add table public.messages, public.matches;

-- ---------- storage: private bucket, files under <user id>/ ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('media', 'media', false, 26214400,
        array['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/3gpp', 'video/quicktime', 'video/webm']);

create policy "upload own media" on storage.objects for insert to authenticated
  with check (bucket_id = 'media' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "read visible media" on storage.objects for select to authenticated
  using (bucket_id = 'media' and (
    (storage.foldername(name))[1] = (select auth.uid())::text
    or ((storage.foldername(name))[1] ~ '^[0-9a-f-]{36}$' and public.can_view(((storage.foldername(name))[1])::uuid))));
