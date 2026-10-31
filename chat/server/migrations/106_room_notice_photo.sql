-- Room notice with a photo (the owner, 2026-10-31: "more in the notice, photos and so on"). As KakaoTalk's notice:
-- text and a photo, set from the notice sheet or by pinning a line of the chat ("Set as notice").
-- The photo is a photo line of the room's chat (notice_line): it is read under the chat's media rules (can_read_media,
-- 'rooms'), and stays shown when the line is hidden or the chat cleared. voice_notice2 replaces the whole notice
-- (text up to 200 characters and/or the photo line; both empty removes it). voice_notice (text only, older pages)
-- keeps the photo. The room's state and its change event carry noticeLine and noticePhoto (the file's path).
-- Re-runnable.

alter table public.voice_rooms add column if not exists notice_line bigint;

create or replace function public.voice_notice2(p_room text, p_text text, p_line bigint) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); a text := nullif(btrim(coalesce(p_text, '')), '');
begin
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(a) > 200 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if p_line is not null and not exists (select 1 from public.voice_messages x
                                         where x.id = p_line and x.room_id = p_room and x.kind = 'photo') then
    raise exception 'bad_media' using errcode = 'P0001';
  end if;
  update public.voice_rooms set announcement = a, notice_line = p_line, rev = rev + 1
   where id = p_room and (announcement is distinct from a or notice_line is distinct from p_line);
  if found and (a is not null or p_line is not null) then perform public.voice_line(p_room, m.user_id, 'notice'); end if;
  update public.voice_list_cache set at = '-infinity' where id = 1;
end $$;

create or replace function public.voice_notice(p_room text, p_text text) returns void
language sql security definer set search_path = '' as $$
  select public.voice_notice2(p_room, p_text, (select r.notice_line from public.voice_rooms r where r.id = p_room))
$$;

revoke execute on function public.voice_notice2(text, text, bigint) from public, anon;
grant execute on function public.voice_notice2(text, text, bigint) to authenticated;

create or replace function public.voice_notice_photo(p_line bigint) returns text
language sql stable security definer set search_path = '' as $$
  select x.media_path from public.voice_messages x where x.id = p_line
$$;
revoke execute on function public.voice_notice_photo(bigint) from public, anon, authenticated;

-- The change event: as 037/045, with the notice's photo.
create or replace function public.voice_room_event() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_emit(new.id, jsonb_build_object(
    't', 'r', 'title', new.title, 'category', new.category, 'mode', new.speak_mode, 'host', new.host,
    'locked', to_jsonb(new.locked_seats), 'music', new.music, 'stage', new.stage,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = new.id and v.item_id = q->>'id' and v.voted))
             order by n)
        from jsonb_array_elements(new.queue) with ordinality as x(q, n)), '[]'),
    'announcement', new.announcement, 'max', new.max_members, 'gender', new.gender_rule, 'minAge', new.min_age,
    'maxAge', new.max_age, 'private', new.is_private, 'ended', new.ended_at is not null, 'theme', new.theme,
    'noticeLine', new.notice_line, 'noticePhoto', public.voice_notice_photo(new.notice_line)));
  return null;
end $$;

drop trigger if exists voice_rooms_event on public.voice_rooms;
create trigger voice_rooms_event after update on public.voice_rooms for each row
  when (old.title is distinct from new.title or old.category is distinct from new.category
        or old.speak_mode is distinct from new.speak_mode or old.host is distinct from new.host
        or old.locked_seats is distinct from new.locked_seats or old.music is distinct from new.music
        or old.queue is distinct from new.queue or old.announcement is distinct from new.announcement
        or old.max_members is distinct from new.max_members or old.gender_rule is distinct from new.gender_rule
        or old.min_age is distinct from new.min_age or old.max_age is distinct from new.max_age
        or old.is_private is distinct from new.is_private or old.ended_at is distinct from new.ended_at
        or old.stage is distinct from new.stage or old.theme is distinct from new.theme
        or old.notice_line is distinct from new.notice_line)
  execute function public.voice_room_event();

-- The room's state: as before, with the notice's photo.
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'seq', coalesce((select s.seq from public.voice_seq s where s.room_id = r.id), 0),
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music, 'stage', r.stage,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.voted),
               'voted', exists (select 1 from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.user_id = me and v.voted))
             order by n)
        from jsonb_array_elements(r.queue) with ordinality as x(q, n)), '[]'),
    'announcement', r.announcement, 'max', r.max_members, 'gender', r.gender_rule, 'minAge', r.min_age, 'maxAge', r.max_age,
    'private', r.is_private, 'lang', r.lang, 'voice', r.voice, 'theme', r.theme, 'cover', r.cover_path, 'tags', to_jsonb(r.tags),
    'noticeLine', r.notice_line, 'noticePhoto', public.voice_notice_photo(r.notice_line),
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'password', case when r.host = me then r.password end,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
               'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country,
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'lost', m.lost_at is not null, 'hand', m.hand_at is not null,
               'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint, 'j', floor(extract(epoch from m.joined_at) * 1000)::bigint)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles_public p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;
