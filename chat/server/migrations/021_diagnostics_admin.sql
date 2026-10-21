-- Crash reports and funnel events without a third-party service, and a moderation console for the team.
--  * client_events: errors (message <= 2k, stack <= 8k) and named events from the apps, kept 90 days (the purge
--    is in owner_run_once_3.sql). No message content, no phone numbers: the apps send fixed event names only.
--    log_events(): signed in, up to 20 a call and 200 a day per person. log_events_anon(): before sign-in, the
--    same rules under one shared cap of 5,000 a day.
--  * admins, admin_actions (every action of the console is written down), and admin_* functions: the reports
--    queue, one report with its evidence, mark reviewed / dismiss / ban N days or for good (the phone is
--    remembered, as for account deletion) / unban, and a dashboard. Anyone else gets not_allowed.
--    Admins can read report photos (can_read_media).

-- ---------- diagnostics ----------

create table if not exists public.client_events (
  id bigint generated always as identity primary key,
  kind text not null check (kind in ('error', 'event')),
  name text not null check (char_length(name) between 1 and 100),
  message text check (char_length(message) <= 2000),
  stack text check (char_length(stack) <= 8000),
  app_version text check (char_length(app_version) <= 40),
  platform text check (platform in ('web', 'android')),
  route text check (char_length(route) <= 200),
  user_id uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);
create index if not exists client_events_time on public.client_events (created_at);
create index if not exists client_events_kind on public.client_events (kind, created_at desc);
alter table public.client_events enable row level security;

-- Events accepted per day and person (the zero id: everyone not signed in, together).
create table if not exists public.client_event_quota (
  day date not null,
  who uuid not null,
  n int not null,
  primary key (day, who)
);
alter table public.client_event_quota enable row level security;

create or replace function public.log_events_as(p_user uuid, p_events jsonb, p_cap int) returns int
language plpgsql security definer set search_path = '' as $$
declare k uuid := coalesce(p_user, '00000000-0000-0000-0000-000000000000'); want int; used int; room int; n int;
begin
  if jsonb_typeof(p_events) is distinct from 'array' then raise exception 'bad_request' using errcode = 'P0001'; end if;
  want := least(jsonb_array_length(p_events), 20);
  if want = 0 then return 0; end if;
  insert into public.client_event_quota as q (day, who, n) values (current_date, k, want)
  on conflict (day, who) do update set n = q.n + excluded.n
  returning q.n into used;
  room := want - greatest(0, used - p_cap);
  if room <= 0 then return 0; end if;
  insert into public.client_events (kind, name, message, stack, app_version, platform, route, user_id)
  select e->>'kind', left(btrim(e->>'name'), 100), left(e->>'message', 2000), left(e->>'stack', 8000), left(e->>'version', 40),
         case when e->>'platform' in ('web', 'android') then e->>'platform' end, left(e->>'route', 200), p_user
    from jsonb_array_elements(p_events) with ordinality as x(e, i)
   where x.i <= room and jsonb_typeof(e) = 'object' and e->>'kind' in ('error', 'event') and coalesce(btrim(e->>'name'), '') <> '';
  get diagnostics n = row_count;
  return n;
end $$;

/** Up to 20 events a call, 200 a day per person. Returns how many were kept. */
create or replace function public.log_events(p_events jsonb) returns int language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  return public.log_events_as(auth.uid(), p_events, 200);
end $$;

/** Before sign-in: the same, under one cap of 5,000 a day for everyone not signed in. */
create or replace function public.log_events_anon(p_events jsonb) returns int language sql security definer set search_path = '' as $$
  select public.log_events_as(null, p_events, 5000)
$$;

revoke execute on function public.log_events_as(uuid, jsonb, int) from public, anon, authenticated;
revoke execute on function public.log_events(jsonb) from public, anon;
grant execute on function public.log_events(jsonb) to authenticated;
revoke execute on function public.log_events_anon(jsonb) from public, authenticated;
grant execute on function public.log_events_anon(jsonb) to anon;

-- ---------- moderation ----------

create table if not exists public.admins (
  user_id uuid primary key references auth.users (id) on delete cascade,
  added_at timestamptz not null default now()
);
alter table public.admins enable row level security;
-- The test account (owner's phone for QA). The owner adds himself the same way (chat/docs/NEXT_SESSION.md).
insert into public.admins (user_id) select u.id from auth.users u where u.phone = '919000000001' on conflict do nothing;

create table if not exists public.admin_actions (
  id bigint generated always as identity primary key,
  admin uuid references auth.users (id) on delete set null,
  action text not null,
  report_id bigint,
  target uuid,
  detail jsonb,
  created_at timestamptz not null default now()
);
create index if not exists admin_actions_time on public.admin_actions (created_at desc);
create index if not exists admin_actions_target on public.admin_actions (target, created_at desc);
alter table public.admin_actions enable row level security;

alter table public.reports
  add column if not exists reviewed_at timestamptz,
  add column if not exists reviewed_by uuid references auth.users (id) on delete set null,
  add column if not exists outcome text check (outcome in ('reviewed', 'dismissed', 'banned'));
create index if not exists reports_open on public.reports (id desc) where not reviewed;
create index if not exists reports_reason on public.reports (reason, id desc);

/** Whether I am on the moderation team (the console asks first). */
create or replace function public.is_admin() returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.admins a where a.user_id = auth.uid())
$$;

/** Me, if I am an admin; not_allowed otherwise. */
create or replace function public.admin_me() returns uuid language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  return auth.uid();
end $$;

create or replace function public.admin_card(p uuid) returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', x.id, 'name', x.name, 'gender', x.gender, 'age', extract(year from age(x.birth_date))::int,
           'city', x.city, 'bio', x.bio, 'photo_path', x.photo_path, 'country', x.country, 'created_at', x.created_at,
           'banned_until', x.banned_until)
    from public.profiles x where x.id = p
$$;

/**
 * The queue, newest first, 50 at a time (`p_before`: the last id of the page before). `p_filter`: 'unreviewed'
 * (default), 'all', 'auto_nudity', 'manual' (everything people sent), or one reason.
 */
create or replace function public.admin_reports(p_filter text default 'unreviewed', p_before bigint default null, p_limit int default 50)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  perform public.admin_me();
  return coalesce((select jsonb_agg(jsonb_build_object(
      'id', r.id, 'created_at', r.created_at, 'reason', r.reason, 'reviewed', r.reviewed, 'outcome', r.outcome,
      'reviewed_at', r.reviewed_at, 'match_id', r.match_id,
      'reporter', (select jsonb_build_object('id', p.id, 'name', p.name) from public.profiles p where p.id = r.reporter),
      'reported', (select jsonb_build_object('id', p.id, 'name', p.name, 'photo_path', p.photo_path, 'banned_until', p.banned_until)
                     from public.profiles p where p.id = r.reported),
      'open_against', (select count(*) from public.reports o where o.reported = r.reported and not o.reviewed),
      'has_evidence', coalesce(jsonb_array_length(r.evidence->'chat'), 0) + coalesce(jsonb_array_length(r.evidence->'voice'), 0)
                      + coalesce(jsonb_array_length(r.evidence->'rooms'), 0) > 0
    ) order by r.id desc)
    from (select * from public.reports x
           where (p_before is null or x.id < p_before)
             and case coalesce(p_filter, 'unreviewed')
                   when 'unreviewed' then not x.reviewed
                   when 'all' then true
                   when 'manual' then x.reason <> 'auto_nudity'
                   else x.reason = p_filter end
           order by x.id desc limit least(greatest(coalesce(p_limit, 50), 1), 200)) r), '[]');
end $$;

/** One report: what was written down when it was made, both people, the reported person's other reports, actions taken. */
create or replace function public.admin_report(p_id bigint) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare r public.reports;
begin
  perform public.admin_me();
  select * into r from public.reports x where x.id = p_id;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  return jsonb_build_object(
    'id', r.id, 'created_at', r.created_at, 'reason', r.reason, 'reviewed', r.reviewed, 'outcome', r.outcome,
    'reviewed_at', r.reviewed_at, 'reviewed_by', (select p.name from public.profiles p where p.id = r.reviewed_by),
    'match_id', r.match_id, 'evidence', coalesce(r.evidence, '{}'),
    'reporter', public.admin_card(r.reporter), 'reported', public.admin_card(r.reported),
    'reports_24h', (select count(*) from public.reports o where o.reported = r.reported and o.created_at > now() - interval '24 hours'),
    'reports_total', (select count(*) from public.reports o where o.reported = r.reported),
    'history', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'reason', o.reason, 'created_at', o.created_at,
                                   'reviewed', o.reviewed, 'outcome', o.outcome) order by o.id desc)
                           from (select * from public.reports o where o.reported = r.reported and o.id <> r.id order by o.id desc limit 20) o), '[]'),
    'actions', coalesce((select jsonb_agg(jsonb_build_object('action', a.action, 'at', a.created_at, 'detail', a.detail,
                                   'by', (select p.name from public.profiles p where p.id = a.admin)) order by a.id desc)
                           from (select * from public.admin_actions a where a.report_id = r.id or (r.reported is not null and a.target = r.reported)
                                  order by a.id desc limit 20) a), '[]'));
end $$;

/**
 * Closes a report: 'reviewed' (looked at, nothing more), 'dismiss' (not a problem), or 'ban' (the reported person,
 * `p_days` 1-3650 or null for good; their phone is remembered so a new account can't get round it).
 */
create or replace function public.admin_resolve(p_id bigint, p_action text, p_days int default null, p_note text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.admin_me(); r public.reports; until timestamptz;
begin
  if p_action is null or p_action not in ('reviewed', 'dismiss', 'ban') or (p_days is not null and p_days not between 1 and 3650)
     or char_length(p_note) > 500 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  select * into r from public.reports x where x.id = p_id for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if p_action = 'ban' then
    if r.reported is null then raise exception 'not_found' using errcode = 'P0001'; end if;
    until := case when p_days is null then 'infinity'::timestamptz else now() + make_interval(days => p_days) end;
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), until) where id = r.reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and r.reported in (user_a, user_b);
    perform public.remember_ban(r.reported);
  end if;
  update public.reports set reviewed = true, reviewed_at = now(), reviewed_by = me,
         outcome = case p_action when 'dismiss' then 'dismissed' when 'ban' then 'banned' else 'reviewed' end
   where id = p_id;
  insert into public.admin_actions (admin, action, report_id, target, detail)
  values (me, p_action, p_id, r.reported, jsonb_strip_nulls(jsonb_build_object('days', p_days, 'note', nullif(btrim(coalesce(p_note, '')), ''),
          'until', until)));
end $$;

/** Lifts a ban (and the remembered phone). */
create or replace function public.admin_unban(p_user uuid, p_note text default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.admin_me();
begin
  if char_length(p_note) > 500 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.profiles set banned_until = null where id = p_user;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  update public.banned_phones b set banned_until = now()
   where b.banned_until > now() and b.phone_hash = (select public.phone_hash(u.phone) from auth.users u where u.id = p_user);
  insert into public.admin_actions (admin, action, target, detail)
  values (me, 'unban', p_user, jsonb_strip_nulls(jsonb_build_object('note', nullif(btrim(coalesce(p_note, '')), ''))));
end $$;

/** Counts for the console's first page. */
create or replace function public.admin_dashboard() returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  perform public.admin_me();
  return jsonb_build_object(
    'reports_24h', coalesce((select jsonb_object_agg(x.reason, x.n) from (select r.reason, count(*) n from public.reports r
                      where r.created_at > now() - interval '24 hours' group by 1) x), '{}'),
    'reports_7d', coalesce((select jsonb_object_agg(x.reason, x.n) from (select r.reason, count(*) n from public.reports r
                      where r.created_at > now() - interval '7 days' group by 1) x), '{}'),
    'unreviewed', (select count(*) from public.reports r where not r.reviewed),
    'active_rooms', (select count(*) from public.voice_rooms r where r.ended_at is null and public.voice_alive(r)),
    'in_rooms', (select count(distinct m.user_id) from public.voice_members m
                  where m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds'),
    'searching', (select count(*) from public.queue q where q.seen_at > now() - interval '20 seconds'),
    'in_chats', (select count(*) * 2 from public.matches x where x.ended_at is null and x.created_at > now() - interval '2 hours'),
    'banned', (select count(*) from public.profiles p where p.banned_until > now()),
    'errors_24h', (select count(*) from public.client_events e where e.kind = 'error' and e.created_at > now() - interval '24 hours'),
    'top_errors', coalesce((select jsonb_agg(x order by x.n desc) from (
                     select e.name, left(e.message, 200) as message, count(*) n, count(distinct e.user_id) users, max(e.created_at) last,
                            max(e.platform) platform, max(e.app_version) version
                       from public.client_events e where e.kind = 'error' and e.created_at > now() - interval '24 hours'
                      group by 1, 2 order by 3 desc limit 10) x), '[]'),
    'events_24h', coalesce((select jsonb_object_agg(x.name, x.n) from (select e.name, count(*) n from public.client_events e
                      where e.kind = 'event' and e.created_at > now() - interval '24 hours' group by 1) x), '{}'),
    'recent_actions', coalesce((select jsonb_agg(jsonb_build_object('action', a.action, 'at', a.created_at, 'report', a.report_id,
                                  'target', (select p.name from public.profiles p where p.id = a.target),
                                  'by', (select p.name from public.profiles p where p.id = a.admin)) order by a.id desc)
                       from (select * from public.admin_actions a order by a.id desc limit 10) a), '[]'));
end $$;

revoke execute on function public.admin_me(), public.admin_card(uuid) from public, anon, authenticated;
revoke execute on function public.is_admin(), public.admin_reports(text, bigint, int), public.admin_report(bigint),
  public.admin_resolve(bigint, text, int, text), public.admin_unban(uuid, text), public.admin_dashboard()
  from public, anon;
grant execute on function public.is_admin(), public.admin_reports(text, bigint, int), public.admin_report(bigint),
  public.admin_resolve(bigint, text, int, text), public.admin_unban(uuid, text), public.admin_dashboard()
  to authenticated;

-- Admins read the photos a report points at (and the reported person's own).
create or replace function public.can_read_media(object_name text) returns boolean language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] in ('avatar', 'posts') then public.can_view(((storage.foldername(object_name))[1])::uuid)
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
