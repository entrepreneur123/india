-- Video calls check the partner's video on the phone (nsfwjs / on-device model) and hide it when it looks like
-- nudity. The phone also files a report with reason 'auto_nudity' so the safety team can review it.
-- Automatic reports never block anyone and don't count toward the 3-reports suspension (a model can be wrong);
-- the person who saw it can still report by hand, which does.

alter table public.reports drop constraint if exists reports_reason_check;
alter table public.reports add constraint reports_reason_check
  check (reason in ('nudity', 'minor', 'harassment', 'fake', 'other', 'auto_nudity'));

create or replace function public.flag_nudity(p_match uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); other uuid;
begin
  select case when x.user_a = me then x.user_b else x.user_a end into other
    from public.matches x where x.id = p_match and me in (x.user_a, x.user_b);
  if other is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  -- One per call per side is enough for review.
  if exists (select 1 from public.reports r where r.reporter = me and r.match_id = p_match and r.reason = 'auto_nudity') then return; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.created_at > now() - interval '1 day') >= 20 then return; end if;
  insert into public.reports (reporter, reported, match_id, reason, evidence)
  values (me, other, p_match, 'auto_nudity', jsonb_build_object('name', (select p.name from public.profiles p where p.id = other), 'source', 'video_model'));
end $$;
revoke execute on function public.flag_nudity(uuid) from public, anon;
grant execute on function public.flag_nudity(uuid) to authenticated;

-- Manual reports: automatic ones don't use up the daily limit or count toward the suspension.
create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); rid bigint; thread uuid;
begin
  if me is null or p_reported is null or p_reported = me or p_reason = 'auto_nudity' then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.reason <> 'auto_nudity' and r.created_at > now() - interval '1 day') >= 20 then
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
       where r.reported = p_reported and r.reason <> 'auto_nudity' and r.created_at > now() - interval '24 hours'
         and rp.created_at < now() - interval '3 days' and (rp.banned_until is null or rp.banned_until < now())) >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;
