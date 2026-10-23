-- The room's language (024) in voice_state, for the host's settings sheet. One more key in the JSON: Android 0.7.0
-- ignores keys it doesn't know.
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'seq', coalesce((select s.seq from public.voice_seq s where s.room_id = r.id), 0),
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.voted),
               'voted', exists (select 1 from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.user_id = me and v.voted))
             order by n)
        from jsonb_array_elements(r.queue) with ordinality as x(q, n)), '[]'),
    'announcement', r.announcement, 'max', r.max_members, 'gender', r.gender_rule, 'minAge', r.min_age, 'maxAge', r.max_age,
    'private', r.is_private, 'lang', r.lang,
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
               'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country,
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'hand', m.hand_at is not null,
               'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint, 'j', floor(extract(epoch from m.joined_at) * 1000)::bigint)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;
