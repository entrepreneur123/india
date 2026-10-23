-- Paid chat walls, sticker packs and the VIP incognito mode; no emoji in chats.
-- 1. New Play products in the catalogue (same one-time, non-consumable flow as looks, 040): wall_* (my chat
--    background, only I see it), sticker_* (a pack of 8 stickers I can send), vip_anon (incognito, below).
-- 2. A sticker is a whole chat message ":st:<pack>/<1-8>:" and needs its pack (ordinary emoji stay free everywhere;
--    stickers are the paid, richer kind). Bots (no signed-in user) are not checked.
-- 3. Incognito: someone with vip_anon who turns it on is shown to everyone but friends as "VIP": no photo, no ID, no
--    age (age 0, which the apps hide), city, bio, country, looks or badges; they can't be found by ID. Friend requests
--    still work, and friends see everything. Room chat lines keep the name they were sent with, so there it stays "VIP"
--    for everyone. Done with two views of profiles that the reading functions use instead of the table: per viewer
--    (friends see through) for cards, lists and profiles; "VIP" for everyone in rooms, whose changes are broadcast to all.

alter table public.cosmetic_items drop constraint cosmetic_items_id_check, drop constraint cosmetic_items_kind_check;
alter table public.cosmetic_items add constraint cosmetic_items_id_check check (id ~ '^(badge|color|font|frame|wall|sticker|vip)_[a-z0-9]{2,20}$'),
  add constraint cosmetic_items_kind_check check (kind in ('badge', 'color', 'font', 'frame', 'wall', 'sticker', 'vip'));
insert into public.cosmetic_items (id, kind, price_inr, sort) values
  ('wall_saffron', 'wall', 79, 1), ('wall_midnight', 'wall', 79, 2), ('wall_paisley', 'wall', 79, 3),
  ('wall_mehndi', 'wall', 79, 4), ('wall_stars', 'wall', 79, 5), ('wall_waves', 'wall', 79, 6),
  ('sticker_namaste', 'sticker', 49, 1), ('sticker_chai', 'sticker', 49, 2), ('sticker_cricket', 'sticker', 49, 3),
  ('sticker_love', 'sticker', 49, 4),
  ('vip_anon', 'vip', 299, 1)
on conflict (id) do nothing;

alter table public.profiles add column if not exists look_wall text, add column if not exists anon boolean not null default false;

create or replace function public.owns(p_user uuid, p_item text) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.user_cosmetics u where u.user_id = p_user and u.item_id = p_item)
$$;

create or replace function public.set_wall(p_item text) returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_item is not null and (p_item !~ '^wall_' or not public.owns(auth.uid(), p_item)) then raise exception 'not_owned' using errcode = 'P0001'; end if;
  update public.profiles set look_wall = p_item where id = auth.uid();
end $$;

create or replace function public.set_anon(p_on boolean) returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_on and not public.owns(auth.uid(), 'vip_anon') then raise exception 'not_owned' using errcode = 'P0001'; end if;
  update public.profiles set anon = coalesce(p_on, false) where id = auth.uid();
end $$;
revoke execute on function public.set_wall(text), public.set_anon(boolean) from public, anon;
grant execute on function public.set_wall(text), public.set_anon(boolean) to authenticated;
revoke execute on function public.owns(uuid, text) from public, anon, authenticated;

-- My shop now also says my wall and whether incognito is on.
do $$ begin
  execute replace(pg_get_functiondef('public.shop'::regproc),
    '''handle_passes'', coalesce(p.handle_passes, 0))', '''handle_passes'', coalesce(p.handle_passes, 0), ''wall'', p.look_wall, ''anon'', coalesce(p.anon, false))');
end $$;

-- A refund also takes off a wall and ends incognito (the rest of play_revoke as in 042).
do $$ begin
  execute replace(pg_get_functiondef('public.play_revoke'::regproc),
    'look_font = nullif(look_font, item), look_frame = nullif(look_frame, item)',
    'look_font = nullif(look_font, item), look_frame = nullif(look_frame, item), look_wall = nullif(look_wall, item), anon = anon and item <> ''vip_anon''');
end $$;

-- ---- 2: stickers in chats ----------------------------------------------------------------------------------------
create or replace function public.has_emoji(p text) returns boolean language sql immutable set search_path = '' as $$
  select coalesce(p ~ '[\U0001F000-\U0001FAFF☀-➿⌀-⏿⬀-⯿️‍⃣]', false)
$$;

create or replace function public.chat_body_check(p_sender uuid, p_body text) returns void language plpgsql stable security definer set search_path = '' as $$
declare st text[] := regexp_match(coalesce(p_body, ''), '^:st:([a-z0-9]{2,20})/([1-8]):$');
begin
  if auth.uid() is null or st is null then return; end if;
  if not public.owns(p_sender, 'sticker_' || st[1]) then raise exception 'sticker_not_owned' using errcode = 'P0001'; end if;
end $$;
revoke execute on function public.chat_body_check(uuid, text) from public, anon, authenticated;

create or replace function public.message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtext('msg:' || new.sender::text));
  if exists (select 1 from public.profiles p where p.id = new.sender and p.banned_until > now()) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if (select count(*) from public.messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  perform public.chat_body_check(new.sender, new.body);
  return new;
end $$;

create or replace function public.voice_line_guard() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.kind = 'text' then perform public.chat_body_check(new.sender, new.body); end if;
  return new;
end $$;
create or replace trigger voice_messages_guard before insert on public.voice_messages for each row execute function public.voice_line_guard();

-- ---- 3: incognito ---------------------------------------------------------------------------------------------------
-- Same columns in the same order as public.profiles (row(p.*)::public.profiles works), masked when incognito.
-- When a column is added to profiles, add it here too.
create or replace function public.mask_seen(p public.profiles, everyone boolean) returns boolean language sql stable security definer set search_path = '' as $$
  select p.anon and (everyone or (p.id is distinct from auth.uid() and not public.are_friends(auth.uid(), p.id)))
$$;

create or replace view public.profiles_seen with (security_barrier) as
select p.id, case when m then 'VIP' else p.name end as name, p.gender,
  case when m then current_date else p.birth_date end as birth_date, case when m then '' else p.city end as city,
  case when m then '' else p.bio end as bio, case when m then null else p.photo_path end as photo_path, p.banned_until,
  p.created_at, case when m then null else p.country end as country, case when m then '{}' else p.interests end as interests,
  case when m then '{}' else p.photos end as photos, case when m then null else p.cover_path end as cover_path,
  case when m then null else p.accent end as accent, case when m then null else p.status end as status,
  case when m then '{}' else p.languages end as languages, case when m then null else p.prompts end as prompts,
  case when m then null else p.id_verified_at end as id_verified_at, p.manner, case when m then '' else p.handle end as handle,
  p.handle_set_at, case when m then null else p.look_badge end as look_badge, case when m then null else p.look_color end as look_color,
  case when m then null else p.look_font end as look_font, case when m then null else p.look_frame end as look_frame,
  p.handle_passes, case when m then null else p.look_wall end as look_wall, p.anon
from public.profiles p cross join lateral (select public.mask_seen(p, false) as m) x;

create or replace view public.profiles_public with (security_barrier) as
select p.id, case when m then 'VIP' else p.name end as name, p.gender,
  case when m then current_date else p.birth_date end as birth_date, case when m then '' else p.city end as city,
  case when m then '' else p.bio end as bio, case when m then null else p.photo_path end as photo_path, p.banned_until,
  p.created_at, case when m then null else p.country end as country, case when m then '{}' else p.interests end as interests,
  case when m then '{}' else p.photos end as photos, case when m then null else p.cover_path end as cover_path,
  case when m then null else p.accent end as accent, case when m then null else p.status end as status,
  case when m then '{}' else p.languages end as languages, case when m then null else p.prompts end as prompts,
  case when m then null else p.id_verified_at end as id_verified_at, p.manner, case when m then '' else p.handle end as handle,
  p.handle_set_at, case when m then null else p.look_badge end as look_badge, case when m then null else p.look_color end as look_color,
  case when m then null else p.look_font end as look_font, case when m then null else p.look_frame end as look_frame,
  p.handle_passes, case when m then null else p.look_wall end as look_wall, p.anon
from public.profiles p cross join lateral (select public.mask_seen(p, true) as m) x;
revoke all on public.profiles_seen, public.profiles_public from public, anon, authenticated;

-- The functions that show other people read the views. Their bodies are only re-pointed: "public.profiles <alias>"
-- becomes the view, and a row passed to look_json is cast back to the table type.
do $$
declare f text; d text;
begin
  foreach f in array array['card', 'comment_json', 'friend_cards', 'friend_list', 'inbox', 'inbox2', 'post_json', 'profile_card',
                           'profile_extras', 'profile_style', 'recent_cards', 'recent_list', 'looks', 'trust_json',
                           'voice_leaders', 'voice_list_refresh', 'voice_member_json', 'voice_state_of',
                           'voice_poll_create', 'voice_post', 'voice_send', 'voice_send2'] loop
    for d in select pg_get_functiondef(p.oid) from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname = f loop
      d := regexp_replace(d, 'public\.profiles(\s+)([a-z]\w*)', case when f like 'voice\_%'
                                                                   then 'public.profiles_public\1\2' else 'public.profiles_seen\1\2' end, 'g');
      d := regexp_replace(d, 'look_json\(([a-z]\w*)\)', 'look_json(row(\1.*)::public.profiles)', 'g');
      execute d;
    end loop;
  end loop;
end $$;

-- Found by ID: never someone incognito (kept on the table, so the ID index still serves the search).
do $$ begin
  execute replace(pg_get_functiondef('public.search_handles'::regproc),
    'and p.id <> me and', 'and p.id <> me and not p.anon and');
end $$;
