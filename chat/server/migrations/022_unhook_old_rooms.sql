-- The live functions stop reading the text rooms of 008 (rooms, room_members, room_messages, room_codes), so
-- owner_run_once_3.sql can drop those tables. Nothing has written to them since 019.
--  * can_view: the "in the same old room" clause goes (voice rooms have their own clause).
--  * report_user: no old-room checks; the evidence keeps its 'rooms' key (empty) so the console reads every
--    report the same way.
--  * stale_media: no longer looks at old room messages.

create or replace function public.can_view(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid())
                     or (f.requester = auth.uid() and f.addressee = other and f.status = 'accepted'))
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')))
$$;

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
     and not exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                      where a.user_id = me and b.user_id = p_reported
                        and a.seen_at > now() - interval '24 hours' and b.seen_at > now() - interval '24 hours') then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason) returning id into rid;

  thread := coalesce(p_match, (select x.id from public.matches x
    where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported)
    order by x.created_at desc limit 1));
  update public.reports set evidence = jsonb_build_object(
    'name', (select p.name from public.profiles p where p.id = p_reported),
    'chat', (select coalesce(jsonb_agg(jsonb_build_object('by', case when s.sender = p_reported then 'reported' else 'reporter' end,
                     'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select * from public.messages m where m.match_id = thread order by m.id desc limit 100) s),
    'rooms', '[]'::jsonb,
    'voice', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.voice_messages m join public.voice_members vm on vm.room_id = m.room_id and vm.user_id = me
                      where m.sender = p_reported and m.kind in ('text', 'photo') and m.created_at > now() - interval '24 hours'
                      order by m.id desc limit 50) s))
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

create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.voice_messages m where m.media_path = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;
