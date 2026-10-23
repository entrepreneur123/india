-- Reports that outlive accounts, and old data retired on a schedule.
--
-- 1. A report stays when either person deletes their account (the person becomes NULL), and it carries a copy
--    of what was said (the chat, or the reported person's recent room messages), because the chat itself is
--    deleted with the account. Reports are kept for one year.
-- 2. Every night (pg_cron, 03:17 UTC):
--      - random chats: 30 days after they ended, unless the two are friends (a friend chat lives in that thread)
--      - room messages: after 30 days; rooms people made: 7 days after they closed or went quiet
--      - queue rows not used for a day, phone hashes of bans that are over, reports older than a year
--      - files nobody uses any more (old profile photos, photos of deleted chats and posts): Edge Function
--        purge-media, which the database calls with a key that never leaves the database (vault).
-- 3. Test helpers from 001b are removed.
-- Applied as 013a (evidence, stale_media, vault key, extensions), 013b (report FKs) and 013d (media job);
-- the parts with DELETE/DROP are in ../owner_run_once.sql because the MCP tool cannot run them.

-- 1. Reports
alter table public.reports alter column reporter drop not null, alter column reported drop not null;
alter table public.reports drop constraint reports_reporter_fkey,
  add constraint reports_reporter_fkey foreign key (reporter) references public.profiles(id) on delete set null;
alter table public.reports drop constraint reports_reported_fkey,
  add constraint reports_reported_fkey foreign key (reported) references public.profiles(id) on delete set null;
alter table public.reports add column if not exists evidence jsonb;

create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); rid bigint; thread uuid;
begin
  if me is null or p_reported is null or p_reported = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  if p_match is not null and not exists (select 1 from public.matches x where x.id = p_match
       and ((x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))) then
    p_match := null;
  end if;
  if not exists (select 1 from public.matches x where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))
     and not public.can_view(p_reported)
     and not exists (select 1 from public.room_messages m join public.room_members rm on rm.room_id = m.room_id and rm.user_id = me
                      where m.sender = p_reported and m.created_at > now() - interval '24 hours') then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason) returning id into rid;

  -- What was said, kept with the report: the chat it came from (else the latest one between the two),
  -- and what the reported person wrote in my rooms in the last day.
  thread := coalesce(p_match, (select x.id from public.matches x
    where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported)
    order by x.created_at desc limit 1));
  update public.reports set evidence = jsonb_build_object(
    'name', (select p.name from public.profiles p where p.id = p_reported),
    'chat', (select coalesce(jsonb_agg(jsonb_build_object('by', case when s.sender = p_reported then 'reported' else 'reporter' end,
                     'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select * from public.messages m where m.match_id = thread order by m.id desc limit 100) s),
    'rooms', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.room_messages m join public.room_members rm on rm.room_id = m.room_id and rm.user_id = me
                      where m.sender = p_reported and m.created_at > now() - interval '24 hours' order by m.id desc limit 50) s))
   where id = rid;

  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
  if (select count(distinct r.reporter) from public.reports r join public.profiles rp on rp.id = r.reporter
       where r.reported = p_reported and r.created_at > now() - interval '24 hours'
         and rp.created_at < now() - interval '3 days' and (rp.banned_until is null or rp.banned_until < now())) >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;

-- 2. Retention
create or replace function public.purge_old_data() returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.matches x
   where x.ended_at < now() - interval '30 days' and not public.are_friends(x.user_a, x.user_b);
  delete from public.room_messages m where m.created_at < now() - interval '30 days';
  delete from public.rooms r
   where r.owner is not null and r.created_at < now() - interval '7 days' and not public.room_alive(r);
  delete from public.queue q where q.seen_at < now() - interval '1 day';
  delete from public.banned_phones b where b.banned_until < now();
  delete from public.reports r where r.created_at < now() - interval '1 year';
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;

-- Files older than a day that no profile, post, chat message or room message points at.
create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.room_messages m where m.sender_photo = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;
revoke execute on function public.stale_media(integer) from public, anon, authenticated;
grant execute on function public.stale_media(integer) to service_role;

-- The key purge-media must be called with. It lives only in the vault.
select vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'purge_media_key')
 where not exists (select 1 from vault.secrets where name = 'purge_media_key');
create or replace function public.purge_key_ok(p_key text) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(p_key = (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'purge_media_key'), false)
$$;
revoke execute on function public.purge_key_ok(text) from public, anon, authenticated;
grant execute on function public.purge_key_ok(text) to service_role;

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;
select cron.schedule('yaari-purge-data', '17 3 * * *', 'select public.purge_old_data()');
select cron.schedule('yaari-purge-media', '27 3 * * *', $cron$
  select net.http_post(
    url := 'https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/purge-media',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-purge-key', (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'purge_media_key')),
    body := '{}'::jsonb, timeout_milliseconds := 60000)
$cron$);

-- 3. Test helpers
drop function if exists public._act(uuid);
drop function if exists public._fails(text, text);
drop table if exists public._t;
