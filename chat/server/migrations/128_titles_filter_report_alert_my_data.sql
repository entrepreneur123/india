-- 128 (worker 6, 2026-11-01): launch trust & safety — the rest of the text filter, operators hear of new reports,
-- and a person can download their own data. Re-runnable; no existing table, column or signature changes.
--
-- 1. The text filter (125's text_mask) where it didn't reach yet. Same rule as profile_name_guard: a name-like text
--    that text_mask would change is refused with 'name_rude' (a starred title would still say it); a body is masked.
--    Done in BEFORE triggers on the tables, so every writer is covered (create_voice_room..4, voice_settings/2,
--    voice_notice2, voice_set_extras, group_create, group_rename, save_highlight/2 — the Android app's old names too):
--      voice_rooms.title, voice_rooms.tags  -> refused     voice_rooms.announcement -> masked
--      group_chats.title (not 'party': the server joins two titles already checked) -> refused
--      highlights.title -> refused
--      profiles.handle (@ID) -> refused as 'handle_reserved' on change, and handle_check answers 'reserved' first, so
--        the field says so while typing (the web and the app already show that answer). New accounts' generated IDs
--        come from a name that passed the same check, so INSERT is not checked (a false hit must not block sign-up).
--    Poll question and options are masked in voice_poll_create, because it also copies them into the room line.
--    Measured before (2026-11-01): 0 of 122 room titles, 7 group names, 2 highlight titles, 507 @IDs and 2 polls hit;
--    1 room announcement did (left as it is: rows are only checked when written).
-- 2. A new report (report_user, report_post, report_comment, the automatic nudity flag: all insert into reports) is
--    broadcast to every admin's private topic user:<admin> as event 'report' {id, reason}. /admin listens there and
--    refreshes its open count, title and list, with a browser notification if allowed. The app's own user topic
--    ignores events it doesn't know (supabaseBackend USER_EVENTS), and rt_topic_ok already lets only that person read
--    it. notifications.kind isn't touched: changing its check constraint would need a DROP.
-- 3. my_data(): the signed-in person's own data as one JSON document (Settings -> Download my data). Only rows that
--    are theirs or that they wrote; other people appear as ids and names only where the row is a relationship of
--    theirs (friends, blocks, chats). Not included: what others wrote to them (that is the other person's data),
--    who reported them, internal hashes and purchase tokens. 3 downloads an hour (rate_ok, 121).

-- 1 ---------------------------------------------------------------------------------------------------------------

/** True when the filter would change this text (null and '' are clean). */
create or replace function public.text_rude(p text) returns boolean
language sql stable set search_path = '' as $$
  select coalesce(p, '') <> '' and public.text_mask(p) is distinct from p
$$;

create or replace function public.room_text_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (tg_op = 'INSERT' or new.title is distinct from old.title) and public.text_rude(new.title) then
    raise exception 'name_rude' using errcode = 'P0001';
  end if;
  if (tg_op = 'INSERT' or new.tags is distinct from old.tags)
     and exists (select 1 from unnest(coalesce(new.tags, '{}')) t where public.text_rude(t)) then
    raise exception 'name_rude' using errcode = 'P0001';
  end if;
  if tg_op = 'INSERT' or new.announcement is distinct from old.announcement then
    new.announcement := public.text_mask(new.announcement);
  end if;
  return new;
end $$;
create or replace trigger voice_rooms_text before insert or update of title, tags, announcement on public.voice_rooms
  for each row execute function public.room_text_guard();

/** Room-less titles: group names (not the server-made 'party' ones) and highlight titles. */
create or replace function public.title_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (tg_op = 'INSERT' or new.title is distinct from old.title)
     and not (tg_table_name = 'group_chats' and to_jsonb(new)->>'kind' = 'party')
     and public.text_rude(new.title) then
    raise exception 'name_rude' using errcode = 'P0001';
  end if;
  return new;
end $$;
create or replace trigger group_chats_title before insert or update of title on public.group_chats
  for each row execute function public.title_guard();
create or replace trigger highlights_title before insert or update of title on public.highlights
  for each row execute function public.title_guard();

create or replace function public.handle_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.handle is distinct from old.handle and public.text_rude(new.handle) then
    raise exception 'handle_reserved' using errcode = 'P0001';
  end if;
  return new;
end $$;
create or replace trigger profiles_handle_plain before update of handle on public.profiles
  for each row execute function public.handle_guard();

revoke execute on function public.text_rude(text) from public, anon;
revoke execute on function public.room_text_guard() from public, anon, authenticated;
revoke execute on function public.title_guard() from public, anon, authenticated;
revoke execute on function public.handle_guard() from public, anon, authenticated;

-- handle_check (040) with the filter answered as 'reserved' before 'taken'.
create or replace function public.handle_check(p_handle text) returns text
language plpgsql stable security definer set search_path = '' as $$
declare h text := lower(btrim(coalesce(p_handle, ''))); me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then return 'invalid'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.handle = h) then return 'mine'; end if;
  if public.handle_reserved(h) or public.text_rude(h) then return 'reserved'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = h and p.id <> me) then return 'taken'; end if;
  return 'ok';
end $$;

-- voice_poll_create with the question and options masked (the rest as it runs on production).
create or replace function public.voice_poll_create(p_room text, p_question text, p_options text[]) returns public.voice_messages
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members; q text := public.text_mask(btrim(coalesce(p_question, ''))); o text[]; p public.voice_polls; old public.voice_polls;
  pr public.profiles; msg public.voice_messages;
begin
  o := array(select public.text_mask(btrim(x)) from unnest(coalesce(p_options, '{}')) with ordinality t(x, n) where btrim(x) <> '' order by n);
  if char_length(q) not between 1 and 120 or cardinality(o) not between 2 and 4
     or exists (select 1 from unnest(o) x where char_length(x) > 40)
     or (select count(distinct lower(x)) from unnest(o) x) <> cardinality(o) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  m := public.voice_actor(p_room);
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if (select count(*) from public.voice_polls x where x.room_id = p_room and x.created_at > now() - interval '10 minutes') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  for old in update public.voice_polls x set closed_at = now() where x.room_id = p_room and x.closed_at is null returning * loop
    perform public.voice_emit(p_room, jsonb_build_object('t', 'p', 'poll', public.voice_poll_json(old, null)));
  end loop;
  insert into public.voice_polls (room_id, created_by, question, options) values (p_room, m.user_id, q, o) returning * into p;
  select * into pr from public.profiles_public x where x.id = m.user_id;
  insert into public.voice_messages (room_id, sender, kind, payload, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_room, pr.id, 'poll', jsonb_build_object('poll_id', p.id, 'question', q, 'options', to_jsonb(o)),
          pr.name, pr.gender, extract(year from age(pr.birth_date))::int, pr.photo_path, pr.country)
  returning * into msg;
  return msg;
end $$;

-- 2 ---------------------------------------------------------------------------------------------------------------

create or replace function public.report_tell_admins() returns trigger
language plpgsql security definer set search_path = '' as $$
declare a uuid;
begin
  for a in select x.user_id from public.admins x loop
    begin
      perform realtime.send(jsonb_build_object('id', new.id, 'reason', new.reason), 'report', 'user:' || a::text, true);
    exception when others then
      raise warning 'report_tell_admins: %', sqlerrm; -- a broadcast hiccup must never lose the report itself
    end;
  end loop;
  return null;
end $$;
revoke execute on function public.report_tell_admins() from public, anon, authenticated;
create or replace trigger reports_tell_admins after insert on public.reports
  for each row execute function public.report_tell_admins();

-- 3 ---------------------------------------------------------------------------------------------------------------

create or replace function public.my_data() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not public.rate_ok('my_data:' || me::text, 3, interval '1 hour') then raise exception 'slow_down' using errcode = 'P0001'; end if;
  return jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'phone', u.phone, 'email', nullif(u.email, ''), 'guest', u.is_anonymous,
                                          'sign_in', u.raw_app_meta_data->>'provider', 'created_at', u.created_at, 'last_sign_in_at', u.last_sign_in_at)
                  from auth.users u where u.id = me),
    'profile', (select to_jsonb(p) from public.profiles p where p.id = me),
    'identity_check', (select jsonb_build_object('gender', i.gender, 'birth_date', i.birth_date, 'nationality', i.nationality,
                                                 'issuing_country', i.issuing_state, 'verified_at', i.verified_at)
                         from public.identities i where i.user_id = me),
    'posts', coalesce((select jsonb_agg(jsonb_build_object('id', x.id, 'text', x.body, 'photo', x.image_path, 'visibility', x.visibility,
                                                           'tags', x.tags, 'likes', x.like_count, 'comments', x.comment_count, 'at', x.created_at) order by x.created_at)
                         from public.posts x where x.user_id = me), '[]'),
    'comments', coalesce((select jsonb_agg(jsonb_build_object('post', x.post_id, 'text', x.body, 'at', x.created_at, 'removed_at', x.removed_at) order by x.id)
                            from public.post_comments x where x.user_id = me), '[]'),
    'highlights', coalesce((select jsonb_agg(jsonb_build_object('title', x.title, 'photos', x.photos, 'visibility', x.visibility, 'at', x.created_at) order by x.id)
                              from public.highlights x where x.user_id = me), '[]'),
    'friends', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'status', f.status,
                                                             'requested_by_me', f.requester = me, 'at', f.created_at) order by f.created_at)
                           from public.friendships f join public.profiles o on o.id = case when f.requester = me then f.addressee else f.requester end
                          where me in (f.requester, f.addressee)), '[]'),
    'following', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'at', l.created_at) order by l.created_at)
                             from public.profile_likes l join public.profiles o on o.id = l.liked where l.liker = me), '[]'),
    'blocked', coalesce((select jsonb_agg(jsonb_build_object('id', b.blocked, 'at', b.created_at) order by b.created_at)
                           from public.blocks b where b.blocker = me), '[]'),
    'chats', coalesce((select jsonb_agg(jsonb_build_object('id', x.id, 'kind', x.kind, 'with', case when x.user_a = me then x.user_b else x.user_a end,
                                                           'started_at', x.created_at, 'ended_at', x.ended_at) order by x.created_at)
                         from public.matches x where me in (x.user_a, x.user_b)), '[]'),
    'messages_sent', coalesce((select jsonb_agg(jsonb_build_object('chat', x.match_id, 'text', x.body, 'media', x.media_path,
                                                                   'media_kind', x.media_kind, 'at', x.created_at) order by x.id)
                                 from public.messages x where x.sender = me), '[]'),
    'group_messages_sent', coalesce((select jsonb_agg(jsonb_build_object('group', x.group_id, 'kind', x.kind, 'text', x.body, 'at', x.created_at) order by x.id)
                                       from public.group_messages x where x.sender = me), '[]'),
    'room_lines_sent', coalesce((select jsonb_agg(jsonb_build_object('room', x.room_id, 'kind', x.kind, 'text', x.body, 'media', x.media_path, 'at', x.created_at) order by x.id)
                                   from public.voice_messages x where x.sender = me and x.kind in ('text', 'photo', 'audio')), '[]'),
    'rooms_created', coalesce((select jsonb_agg(jsonb_build_object('id', x.id, 'title', x.title, 'announcement', x.announcement,
                                                                   'created_at', x.created_at, 'ended_at', x.ended_at) order by x.created_at)
                                 from public.voice_rooms x where x.owner = me), '[]'),
    'megaphones', coalesce((select jsonb_agg(jsonb_build_object('text', x.body, 'country', x.country, 'at', x.created_at) order by x.id)
                              from public.megaphones x where x.user_id = me), '[]'),
    'chat_ratings_given', coalesce((select jsonb_agg(jsonb_build_object('chat', x.match_id, 'good', x.good, 'tags', x.tags, 'at', x.created_at) order by x.created_at)
                                      from public.chat_ratings x where x.rater = me), '[]'),
    'reports_made', coalesce((select jsonb_agg(jsonb_build_object('reported', x.reported, 'reason', x.reason, 'at', x.created_at,
                                                                  'reviewed', x.reviewed, 'outcome', x.outcome) order by x.id)
                                from public.reports x where x.reporter = me), '[]'),
    'purchases', coalesce((select jsonb_agg(jsonb_build_object('product', x.product_id, 'at', x.created_at, 'revoked_at', x.revoked_at) order by x.created_at)
                             from public.purchases x where x.user_id = me), '[]'),
    'subscription', (select jsonb_build_object('premium_until', s.premium_until, 'gender_pass_until', s.gender_until)
                       from public.subscriptions s where s.user_id = me),
    'items', coalesce((select jsonb_agg(jsonb_build_object('item', x.item_id, 'at', x.acquired_at) order by x.acquired_at)
                         from public.user_cosmetics x where x.user_id = me), '[]'),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('platform', d.platform, 'language', d.locale, 'time_zone', d.tz,
                                                             'active', d.active, 'updated_at', d.updated_at))
                           from public.device_tokens d where d.user_id = me), '[]'),
    'notifications', coalesce((select jsonb_agg(jsonb_build_object('kind', n.kind, 'from', n.actor, 'at', n.created_at, 'read_at', n.read_at) order by n.id)
                                 from public.notifications n where n.user_id = me), '[]')
  );
end $$;

revoke execute on function public.my_data() from public, anon;
grant execute on function public.my_data() to authenticated;
