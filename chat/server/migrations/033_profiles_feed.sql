-- Profiles people can make their own, and posts with likes, comments and a feed.
--
-- 1. Profile style (set_profile_style; read with profile_style):
--      cover_path  a cover photo above the profile (own "<id>/cover/" folder, checked on the phone like any photo)
--      accent      the profile's colour: rose, violet, ocean, mint, sunset, gold, berry, slate
--      status      a short line under the name (1-60), like WhatsApp's "About"
--      languages   up to 6 spoken languages (en hi bn ta te mr gu kn ml pa ur or as pt vi)
--      prompts     up to 3 answered questions [{q, a}] (q from a fixed list, a 1-120), like Hinge's
-- 2. Posts: visibility 'friends' (default; friends and people I chatted with, as before) or 'public' (anyone, in
--    Discover). Likes (post_like), comments with replies (comment_add / comment_remove / comments_list), counts kept
--    on the post. Someone who posts publicly can be looked at and asked for friendship by anyone (can_view), and
--    their friends-only posts still stay with friends (post_visible does not use can_view).
-- 3. Feed: feed('friends' | 'discover', before, limit) and posts_of(user, before, limit), newest first; every post
--    comes with its author, counts and whether I liked it.
-- 4. Inbox: likes and comments on my posts, one line per post until read (n = how many), in inbox2 (with the post).
-- 5. Reports: report_post / report_comment keep the post or comment with the report.
-- At most 20 posts a day and 10 comments a minute per person.

-- 1. Profile style
alter table public.profiles add column if not exists cover_path text,
  add column if not exists accent text,
  add column if not exists status text,
  add column if not exists languages text[] not null default '{}',
  add column if not exists prompts jsonb not null default '[]';
alter table public.profiles
  add constraint profiles_cover_own check (cover_path is null or cover_path like id::text || '/cover/%'),
  add constraint profiles_accent_check check (accent is null or accent in ('rose', 'violet', 'ocean', 'mint', 'sunset', 'gold', 'berry', 'slate')),
  add constraint profiles_status_check check (status is null or char_length(status) between 1 and 60),
  add constraint profiles_languages_check check (cardinality(languages) <= 6 and languages <@ array['en', 'hi', 'bn', 'ta', 'te',
    'mr', 'gu', 'kn', 'ml', 'pa', 'ur', 'or', 'as', 'pt', 'vi']),
  add constraint profiles_prompts_check check (jsonb_typeof(prompts) = 'array' and jsonb_array_length(prompts) <= 3);

create or replace function public.set_profile_style(p_cover_path text, p_accent text, p_status text, p_languages text[], p_prompts jsonb)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); s text := nullif(btrim(coalesce(p_status, '')), ''); pr jsonb := coalesce(p_prompts, '[]');
  langs text[] := array(select distinct x from unnest(coalesce(p_languages, '{}')) x);
begin
  if jsonb_typeof(pr) <> 'array' or jsonb_array_length(pr) > 3
     or exists (select 1 from jsonb_array_elements(pr) e
                 where jsonb_typeof(e) <> 'object'
                    or coalesce(e->>'q', '') not in ('weekend', 'food', 'song', 'travel', 'talk', 'laugh', 'goal', 'secret')
                    or char_length(btrim(coalesce(e->>'a', ''))) not between 1 and 120)
     or (select count(distinct e->>'q') from jsonb_array_elements(pr) e) <> jsonb_array_length(pr) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  update public.profiles set cover_path = nullif(p_cover_path, ''), accent = nullif(p_accent, ''), status = s, languages = langs,
         prompts = (select coalesce(jsonb_agg(jsonb_build_object('q', e->>'q', 'a', btrim(e->>'a'))), '[]') from jsonb_array_elements(pr) e)
   where id = me;
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

create or replace function public.profile_style(p_users uuid[])
returns table (id uuid, cover_path text, accent text, status text, languages text[], prompts jsonb)
language sql stable security definer set search_path = '' as $$
  select p.id, p.cover_path, p.accent, p.status, p.languages, p.prompts from public.profiles p
   where p.id = any (p_users[1:50]) and public.can_view(p.id)
$$;

-- A cover photo is kept like the profile photos.
do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.stale_media(integer)'::regprocedure);
  a := 'and not exists (select 1 from public.profiles p where p.photo_path = o.name)';
  if position(a in d) = 0 then raise exception 'stale_media: anchor not found'; end if;
  execute replace(d, a, a || '
     and not exists (select 1 from public.profiles p where p.cover_path = o.name)');
end $$;

-- 2. Posts, likes, comments
alter table public.posts add column if not exists visibility text not null default 'friends' check (visibility in ('public', 'friends')),
  add column if not exists like_count integer not null default 0,
  add column if not exists comment_count integer not null default 0;
grant insert (visibility) on public.posts to authenticated;
create index if not exists posts_public_time on public.posts (created_at desc) where visibility = 'public';
create index if not exists posts_user_time on public.posts (user_id, created_at desc);

create table if not exists public.post_likes (
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  liked boolean not null default true,
  at timestamptz not null default now(),
  primary key (post_id, user_id)
);
create index if not exists post_likes_user on public.post_likes (user_id);
create table if not exists public.post_comments (
  id bigint generated always as identity primary key,
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 300),
  reply_to bigint,
  created_at timestamptz not null default now(),
  removed_at timestamptz
);
create index if not exists post_comments_post on public.post_comments (post_id, id);
create index if not exists post_comments_user on public.post_comments (user_id, created_at desc);
alter table public.post_likes enable row level security;
alter table public.post_comments enable row level security;
revoke all on public.post_likes, public.post_comments from public, anon, authenticated;

-- Who sees a post: its author; anyone for a public post; friends and people I chatted with for the rest.
create or replace function public.post_visible(p_author uuid, p_visibility text) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_author = auth.uid() or (
    not public.is_blocked(auth.uid(), p_author) and (
      p_visibility = 'public' or public.are_friends(auth.uid(), p_author)
      or exists (select 1 from public.matches m where (m.user_a = auth.uid() and m.user_b = p_author)
                                                    or (m.user_b = auth.uid() and m.user_a = p_author))))
$$;
alter policy "read posts" on public.posts using (public.post_visible(user_id, visibility));

-- Anyone who posts publicly can be looked at (their card, style, public posts) and asked for friendship.
do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.can_view(uuid)'::regprocedure);
  a := 'or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id';
  if position(a in d) = 0 then raise exception 'can_view: anchor not found'; end if;
  execute replace(d, a, 'or exists (select 1 from public.posts pp where pp.user_id = other and pp.visibility = ''public'')
      ' || a);
end $$;

create or replace function public.post_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.posts p where p.user_id = new.user_id and p.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return new;
end $$;
create trigger posts_limit before insert on public.posts for each row execute function public.post_limit();

create or replace function public.post_json(p public.posts) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', p.id, 'body', p.body, 'image_path', p.image_path, 'created_at', p.created_at,
    'visibility', p.visibility, 'likes', p.like_count, 'comments', p.comment_count,
    'liked', exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = auth.uid() and l.liked),
    'author', (select jsonb_build_object('id', a.id, 'name', a.name, 'gender', a.gender, 'age', extract(year from age(a.birth_date))::int,
                 'city', a.city, 'country', a.country, 'photo_path', a.photo_path, 'accent', a.accent)
                 from public.profiles a where a.id = p.user_id))
$$;

-- 3. Feed
create or replace function public.feed(p_tab text, p_before timestamptz default null, p_limit integer default 20) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.voice_me(); n int := least(greatest(coalesce(p_limit, 20), 1), 50);
begin
  if p_tab = 'friends' then
    return coalesce((select jsonb_agg(public.post_json(p) order by p.created_at desc) from (
      select x.* from public.posts x
       where (x.user_id = me
              or x.user_id in (select case when f.requester = me then f.addressee else f.requester end
                                 from public.friendships f where f.status = 'accepted' and (f.requester = me or f.addressee = me)))
         and (p_before is null or x.created_at < p_before)
         and not public.is_blocked(me, x.user_id)
       order by x.created_at desc limit n) p), '[]');
  elsif p_tab = 'discover' then
    return coalesce((select jsonb_agg(public.post_json(p) order by p.created_at desc) from (
      select x.* from public.posts x
       where x.visibility = 'public' and (p_before is null or x.created_at < p_before)
         and not public.is_blocked(me, x.user_id)
         and not exists (select 1 from public.profiles a where a.id = x.user_id and a.banned_until > now())
       order by x.created_at desc limit n) p), '[]');
  end if;
  raise exception 'bad_request' using errcode = 'P0001';
end $$;

create or replace function public.posts_of(p_user uuid, p_before timestamptz default null, p_limit integer default 20) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.post_json(p) order by p.created_at desc), '[]') from (
    select x.* from public.posts x
     where x.user_id = p_user and public.post_visible(x.user_id, x.visibility)
       and (p_before is null or x.created_at < p_before)
     order by x.created_at desc limit least(greatest(coalesce(p_limit, 20), 1), 50)) p
$$;

create or replace function public.post_get(p_post uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select public.post_json(p) from public.posts p where p.id = p_post and public.post_visible(p.user_id, p.visibility)
$$;

-- 4. Likes and comments (and their inbox lines)
alter table public.notifications add column if not exists post_id uuid references public.posts(id) on delete cascade;
alter table public.notifications drop constraint notifications_kind_check,
  add constraint notifications_kind_check check (kind in ('message', 'friend_request', 'friend_accept', 'room_invite', 'post_like', 'post_comment'));
create unique index if not exists notifications_post on public.notifications (user_id, kind, post_id)
  where read_at is null and kind in ('post_like', 'post_comment');

create or replace function public.post_notify(p_owner uuid, p_kind text, p_post uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if p_owner = me then return; end if;
  insert into public.notifications as o (user_id, kind, actor, post_id) values (p_owner, p_kind, me, p_post)
  on conflict (user_id, kind, post_id) where read_at is null and kind in ('post_like', 'post_comment')
  do update set n = o.n + 1, actor = excluded.actor, created_at = now();
end $$;

create or replace function public.post_like(p_post uuid, p_on boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.posts; was boolean; fresh boolean := false;
begin
  select * into p from public.posts x where x.id = p_post for update;
  if not found or not public.post_visible(p.user_id, p.visibility) then raise exception 'not_found' using errcode = 'P0001'; end if;
  select l.liked into was from public.post_likes l where l.post_id = p.id and l.user_id = me;
  if not found then
    if p_on then
      insert into public.post_likes (post_id, user_id) values (p.id, me);
      fresh := true;
    end if;
    was := false;
  elsif was is distinct from p_on then
    update public.post_likes set liked = p_on, at = now() where post_id = p.id and user_id = me;
  end if;
  if coalesce(was, false) <> p_on then
    update public.posts set like_count = greatest(like_count + case when p_on then 1 else -1 end, 0) where id = p.id
    returning * into p;
  end if;
  if fresh then perform public.post_notify(p.user_id, 'post_like', p.id); end if; -- the first like only, not every toggle
  return jsonb_build_object('likes', p.like_count, 'liked', p_on);
end $$;

create or replace function public.comment_json(c public.post_comments) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', c.id, 'post_id', c.post_id, 'body', c.body, 'created_at', c.created_at, 'reply_to', c.reply_to,
    'reply_name', (select a.name from public.post_comments r join public.profiles a on a.id = r.user_id where r.id = c.reply_to),
    'mine', c.user_id = auth.uid(),
    'can_remove', c.user_id = auth.uid() or exists (select 1 from public.posts p where p.id = c.post_id and p.user_id = auth.uid()),
    'author', (select jsonb_build_object('id', a.id, 'name', a.name, 'gender', a.gender, 'age', extract(year from age(a.birth_date))::int,
                 'country', a.country, 'photo_path', a.photo_path, 'accent', a.accent)
                 from public.profiles a where a.id = c.user_id))
$$;

create or replace function public.comment_add(p_post uuid, p_body text, p_reply_to bigint default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.posts; c public.post_comments; t text := btrim(coalesce(p_body, ''));
begin
  if char_length(t) not between 1 and 300 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  select * into p from public.posts x where x.id = p_post for update;
  if not found or not public.post_visible(p.user_id, p.visibility) then raise exception 'not_found' using errcode = 'P0001'; end if;
  if (select count(*) from public.post_comments x where x.user_id = me and x.created_at > now() - interval '1 minute') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.post_comments (post_id, user_id, body, reply_to)
  values (p.id, me, t, (select r.id from public.post_comments r where r.id = p_reply_to and r.post_id = p.id and r.removed_at is null))
  returning * into c;
  update public.posts set comment_count = comment_count + 1 where id = p.id;
  perform public.post_notify(p.user_id, 'post_comment', p.id);
  return public.comment_json(c);
end $$;

create or replace function public.comment_remove(p_id bigint) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); c public.post_comments;
begin
  select * into c from public.post_comments x where x.id = p_id and x.removed_at is null for update;
  if not found or not (c.user_id = me or exists (select 1 from public.posts p where p.id = c.post_id and p.user_id = me)) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  update public.post_comments set removed_at = now() where id = c.id;
  update public.posts set comment_count = greatest(comment_count - 1, 0) where id = c.post_id;
end $$;

create or replace function public.comments_list(p_post uuid, p_after bigint default null, p_limit integer default 50) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.comment_json(c) order by c.id), '[]') from (
    select x.* from public.post_comments x join public.posts p on p.id = x.post_id
     where x.post_id = p_post and x.removed_at is null and public.post_visible(p.user_id, p.visibility)
       and not public.is_blocked(auth.uid(), x.user_id)
       and (p_after is null or x.id > p_after)
     order by x.id limit least(greatest(coalesce(p_limit, 50), 1), 100)) c
$$;

-- The inbox with posts: inbox's lines plus the post a like or comment is about.
create or replace function public.inbox2(p_before bigint default null, p_limit integer default 30) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', o.id, 'kind', o.kind, 'n', o.n, 'created_at', o.created_at, 'read_at', o.read_at,
           'match_id', o.match_id, 'room_id', o.room_id, 'room_title', r.title,
           'room_open', r.id is not null and r.ended_at is null and public.voice_alive(r),
           'pending', o.kind = 'friend_request' and exists (select 1 from public.friendships f
             where f.requester = o.actor and f.addressee = o.user_id and f.status = 'pending'),
           'post', case when o.post_id is not null then (select jsonb_build_object('id', pp.id, 'body', left(pp.body, 80), 'image_path', pp.image_path)
                                                          from public.posts pp where pp.id = o.post_id) end,
           'actor', jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
             'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)) order by o.id desc), '[]')
    from (select x.* from public.notifications x
           where x.user_id = auth.uid() and (p_before is null or x.id < p_before) and not public.is_blocked(x.user_id, x.actor)
           order by x.id desc limit least(greatest(coalesce(p_limit, 30), 1), 100)) o
    join public.profiles p on p.id = o.actor
    left join public.voice_rooms r on r.id = o.room_id
$$;

-- 5. Reports
create or replace function public.report_post(p_post uuid, p_reason text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.posts;
begin
  select * into p from public.posts x where x.id = p_post;
  if not found or p.user_id = me or not public.post_visible(p.user_id, p.visibility) then raise exception 'not_found' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, reason, evidence)
  values (me, p.user_id, p_reason, jsonb_build_object('name', (select a.name from public.profiles a where a.id = p.user_id),
          'post', jsonb_build_object('id', p.id, 'body', p.body, 'image', p.image_path, 'visibility', p.visibility, 'at', p.created_at)));
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

create or replace function public.report_comment(p_id bigint, p_reason text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); c public.post_comments;
begin
  select x.* into c from public.post_comments x join public.posts p on p.id = x.post_id
   where x.id = p_id and x.removed_at is null and public.post_visible(p.user_id, p.visibility);
  if not found or c.user_id = me then raise exception 'not_found' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, reason, evidence)
  values (me, c.user_id, p_reason, jsonb_build_object('name', (select a.name from public.profiles a where a.id = c.user_id),
          'comment', jsonb_build_object('id', c.id, 'post', c.post_id, 'body', c.body, 'at', c.created_at)));
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

-- Grants
revoke execute on function public.set_profile_style(text, text, text, text[], jsonb), public.profile_style(uuid[]),
  public.post_visible(uuid, text), public.post_limit(), public.post_json(public.posts), public.feed(text, timestamptz, integer),
  public.posts_of(uuid, timestamptz, integer), public.post_get(uuid), public.post_notify(uuid, text, uuid),
  public.post_like(uuid, boolean), public.comment_json(public.post_comments), public.comment_add(uuid, text, bigint),
  public.comment_remove(bigint), public.comments_list(uuid, bigint, integer), public.inbox2(bigint, integer),
  public.report_post(uuid, text), public.report_comment(bigint, text) from public, anon;
revoke execute on function public.post_limit(), public.post_json(public.posts), public.post_notify(uuid, text, uuid),
  public.comment_json(public.post_comments) from authenticated;
grant execute on function public.set_profile_style(text, text, text, text[], jsonb), public.profile_style(uuid[]),
  public.post_visible(uuid, text), public.feed(text, timestamptz, integer), public.posts_of(uuid, timestamptz, integer),
  public.post_get(uuid), public.post_like(uuid, boolean), public.comment_add(uuid, text, bigint), public.comment_remove(bigint),
  public.comments_list(uuid, bigint, integer), public.inbox2(bigint, integer), public.report_post(uuid, text),
  public.report_comment(bigint, text) to authenticated;
