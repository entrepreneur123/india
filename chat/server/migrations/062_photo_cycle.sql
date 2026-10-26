-- Profile photo gradient (the owner, 2026-10-26): a look bought once ('cycle_photos'); its owner picks 2-4 of their
-- profile photos and wherever their photo shows, those photos fade one into the next every 2 seconds. The photos are
-- the person's own avatar files (the read rule of profile photos, hidden by incognito) and are kept from the clean-up.
-- Photo effects (16): 'effect_aurora' and 'effect_prism', light moving over the photo wherever it shows; one worn at a
-- time (profiles.look_effect, set_worn). New profiles columns -> appended to profiles_seen and profiles_public too
-- (061's rule). Prices are placeholders until the owner sets them.
-- Chat bubbles (21): 'bubble_*', the look of my chat bubbles that everyone sees (random chats, messages, rooms); one
-- worn at a time (profiles.look_bubble). set_worn(kind, item) wears an effect or a bubble (null takes it off).
-- No more Yaari Plus or stickers (23): every paid thing is a single item. Choosing women/men is free for everyone
-- (can_choose_gender), the exit lock is 5 s for everyone, sticker packs leave the shop (rows stay for old purchases).
-- A refund also takes off an effect, a bubble or the photo gradient. Room lines go up to 1000 characters (22).

alter table public.cosmetic_items drop constraint if exists cosmetic_items_id_check;
alter table public.cosmetic_items drop constraint if exists cosmetic_items_kind_check;
alter table public.cosmetic_items add constraint cosmetic_items_id_check check (id ~ '^(badge|color|font|frame|wall|sticker|vip|cycle|effect|bubble)_[a-z0-9]{2,20}$');
alter table public.cosmetic_items add constraint cosmetic_items_kind_check check (kind in ('badge', 'color', 'font', 'frame', 'wall', 'sticker', 'vip', 'cycle', 'effect', 'bubble'));
insert into public.cosmetic_items (id, kind, price_inr, sort, active) values
  ('cycle_photos', 'cycle', 199, 1, true), ('effect_aurora', 'effect', 249, 1, true), ('effect_prism', 'effect', 249, 2, true),
  ('bubble_peach', 'bubble', 99, 1, true), ('bubble_ocean', 'bubble', 99, 2, true), ('bubble_mint', 'bubble', 99, 3, true),
  ('bubble_galaxy', 'bubble', 149, 4, true), ('bubble_gold', 'bubble', 149, 5, true), ('bubble_neon', 'bubble', 149, 6, true)
on conflict (id) do nothing;
update public.cosmetic_items set active = false where kind = 'sticker';

alter table public.profiles add column if not exists cycle_photos text[] check (cycle_photos is null or cardinality(cycle_photos) between 2 and 4);
alter table public.profiles add column if not exists look_effect text check (look_effect is null or look_effect like 'effect\_%');
alter table public.profiles add column if not exists look_bubble text check (look_bubble is null or look_bubble like 'bubble\_%');

create or replace view public.profiles_seen with (security_barrier = true) as
 select p.id, case when x.m then 'VIP'::text else p.name end as name, p.gender,
        case when x.m then current_date else p.birth_date end as birth_date,
        case when x.m then ''::text else p.city end as city, case when x.m then ''::text else p.bio end as bio,
        case when x.m then null::text else p.photo_path end as photo_path, p.banned_until, p.created_at,
        case when x.m then null::text else p.country end as country, case when x.m then '{}'::text[] else p.interests end as interests,
        case when x.m then '{}'::text[] else p.photos end as photos, case when x.m then null::text else p.cover_path end as cover_path,
        case when x.m then null::text else p.accent end as accent, case when x.m then null::text else p.status end as status,
        case when x.m then '{}'::text[] else p.languages end as languages, case when x.m then null::jsonb else p.prompts end as prompts,
        case when x.m then null::timestamptz else p.id_verified_at end as id_verified_at, p.manner,
        case when x.m then ''::text else p.handle end as handle, p.handle_set_at,
        case when x.m then null::text else p.look_badge end as look_badge, case when x.m then null::text else p.look_color end as look_color,
        case when x.m then null::text else p.look_font end as look_font, case when x.m then null::text else p.look_frame end as look_frame,
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from,
        case when x.m then null::text[] else p.cycle_photos end as cycle_photos,
        case when x.m then null::text else p.look_effect end as look_effect,
        case when x.m then null::text else p.look_bubble end as look_bubble
   from public.profiles p cross join lateral (select public.mask_seen(p.*, false) as m) x;

create or replace view public.profiles_public with (security_barrier = true) as
 select p.id, case when x.m then 'VIP'::text else p.name end as name, p.gender,
        case when x.m then current_date else p.birth_date end as birth_date,
        case when x.m then ''::text else p.city end as city, case when x.m then ''::text else p.bio end as bio,
        case when x.m then null::text else p.photo_path end as photo_path, p.banned_until, p.created_at,
        case when x.m then null::text else p.country end as country, case when x.m then '{}'::text[] else p.interests end as interests,
        case when x.m then '{}'::text[] else p.photos end as photos, case when x.m then null::text else p.cover_path end as cover_path,
        case when x.m then null::text else p.accent end as accent, case when x.m then null::text else p.status end as status,
        case when x.m then '{}'::text[] else p.languages end as languages, case when x.m then null::jsonb else p.prompts end as prompts,
        case when x.m then null::timestamptz else p.id_verified_at end as id_verified_at, p.manner,
        case when x.m then ''::text else p.handle end as handle, p.handle_set_at,
        case when x.m then null::text else p.look_badge end as look_badge, case when x.m then null::text else p.look_color end as look_color,
        case when x.m then null::text else p.look_font end as look_font, case when x.m then null::text else p.look_frame end as look_frame,
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from,
        case when x.m then null::text[] else p.cycle_photos end as cycle_photos,
        case when x.m then null::text else p.look_effect end as look_effect,
        case when x.m then null::text else p.look_bubble end as look_bubble
   from public.profiles p cross join lateral (select public.mask_seen(p.*, true) as m) x;

create or replace function public.look_json(p public.profiles) returns jsonb
language sql stable set search_path = '' as $$
  select jsonb_build_object('id', p.id, 'handle', p.handle, 'badge', p.look_badge, 'color', p.look_color,
                            'font', p.look_font, 'frame', p.look_frame, 'cycle', p.cycle_photos,
                            'effect', p.look_effect, 'bubble', p.look_bubble)
$$;

-- null or '{}': off. Otherwise 2-4 different photos of mine (files in my avatar folder).
create or replace function public.set_cycle(p_paths text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p text[] := nullif(coalesce(p_paths, '{}'), '{}');
begin
  if p is not null then
    if not public.owns(me, 'cycle_photos') then raise exception 'not_owned' using errcode = 'P0001'; end if;
    if cardinality(p) not between 2 and 4 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
       or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                    or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
      raise exception 'bad_request' using errcode = 'P0001';
    end if;
  end if;
  update public.profiles set cycle_photos = p where id = me;
end $$;

create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.profiles p where p.cover_path = o.name)
     and not exists (select 1 from public.profiles p where p.id::text = (storage.foldername(o.name))[1]
                       and (o.name = any (p.photos) or o.name = any (p.cycle_photos)))
     and not exists (select 1 from public.highlights h where h.user_id::text = (storage.foldername(o.name))[1] and o.name = any (h.photos))
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.voice_messages m where m.media_path = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;

-- The photo effect or chat bubble I wear: one I own, or none.
create or replace function public.set_worn(p_kind text, p_item text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); it text := nullif(p_item, '');
begin
  if p_kind is null or p_kind not in ('effect', 'bubble') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if it is not null and (split_part(it, '_', 1) <> p_kind or not public.owns(me, it)) then raise exception 'not_owned' using errcode = 'P0001'; end if;
  if p_kind = 'effect' then update public.profiles set look_effect = it where id = me;
  else update public.profiles set look_bubble = it where id = me; end if;
end $$;
revoke execute on function public.set_worn(text, text), public.set_cycle(text[]) from public, anon;
grant execute on function public.set_worn(text, text), public.set_cycle(text[]) to authenticated;

do $$ begin
  execute replace(pg_get_functiondef('public.play_revoke'::regproc),
    'look_wall = nullif(look_wall, item), anon = anon and item <> ''vip_anon''',
    'look_wall = nullif(look_wall, item), anon = anon and item <> ''vip_anon'', look_effect = nullif(look_effect, item), look_bubble = nullif(look_bubble, item), cycle_photos = case when item = ''cycle_photos'' then null else cycle_photos end');
end $$;

-- No Plus (23): everyone may choose women or men, everyone waits the same 5 s before leaving a random chat.
create or replace function public.can_choose_gender(uid uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select true
$$;
create or replace function public.exit_lock() returns interval language sql stable security definer set search_path = '' as $$
  select interval '5 seconds'
$$;

-- Long lines (22): room lines may be 1000 characters like messages; apps fold anything over 500 behind "See all".
alter table public.voice_messages drop constraint if exists voice_messages_body_check;
alter table public.voice_messages add constraint voice_messages_body_check check (char_length(body) between 1 and 1000);
do $$ begin
  execute replace(pg_get_functiondef('public.voice_send2(text, text, bigint)'::regprocedure), 'not between 1 and 500', 'not between 1 and 1000');
  execute replace(pg_get_functiondef('public.voice_send(text, text)'::regprocedure), 'not between 1 and 500', 'not between 1 and 1000');
end $$;
