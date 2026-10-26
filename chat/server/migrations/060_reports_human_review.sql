-- Reports go to a person (the owner, 2026-10-26): no automatic ban any more (it was 7 days after 3 reporters in 24 h);
-- every report keeps the whole conversation (up to its last 1000 messages, before: 100) and the reported person's room
-- lines of the last 24 h, for review at /admin. The reporter still blocks the person and the chat still ends.
create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null) returns void
language plpgsql security definer set search_path = '' as $$
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
               from (select * from public.messages m where m.match_id = thread order by m.id desc limit 1000) s),
    'rooms', '[]'::jsonb,
    'voice', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.voice_messages m join public.voice_members vm on vm.room_id = m.room_id and vm.user_id = me
                      where m.sender = p_reported and m.kind in ('text', 'photo') and m.created_at > now() - interval '24 hours'
                      order by m.id desc limit 200) s))
   where id = rid;

  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
end $$;
