-- Instagram-style IDs and looks you buy (the owner, 2026-10-22):
--  * Every profile has an ID ("handle", e.g. L._.1sA) apart from its name. Letters of the world's scripts, digits and
--    ASCII special characters only: no emoji, no spaces, and none of the "fancy font" look-alikes (𝓛𝓲𝓼𝓪, Ｌｉｓａ,
--    ʟɪꜱᴀ, ⓛⓘⓢⓐ, upside-down letters...) people paste to get another font for free. One ID per person, case-blind.
--    Names keep emoji but lose the fancy-font letters too, so a look can only be had by buying it.
--  * Looks (cosmetic_items): a badge beside the ID (crown, diamond...), the ID's colour, the ID's font, and a frame
--    for the profile (frost, fire, jewels, sandstorm...). Bought in the Android app (Google Play, verify-purchase),
--    owned for good, worn by choice, seen by everyone who sees the person (looks()).
--  * The free cover photo and colour of 033 are gone (frames replace them); status, languages and prompts stay.
--  * A random chat that ended goes on only between friends: the history stays readable, a new line needs friendship.

-- ---- IDs ---------------------------------------------------------------------------------------------------------

-- Letters of real scripts (allow-list by code point), ASCII digits and specials, 2–24 characters, not only
-- specials, one of Latin / Greek / Cyrillic at most (no "аdmin" with a Cyrillic а). The input must already be NFC.
create or replace function public.handle_valid(p text) returns boolean
language plpgsql immutable set search_path = '' as $$
declare c int; n int := 0; word boolean := false; latin boolean := false; greek boolean := false; cyr boolean := false;
begin
  if p is null or p <> normalize(p, NFC) then return false; end if;
  n := char_length(p);
  if n < 2 or n > 24 then return false; end if;
  for i in 1..n loop
    c := ascii(substr(p, i, 1));
    if (c between 48 and 57) then word := true;                                         -- 0-9
    elsif (c between 65 and 90) or (c between 97 and 122) then word := true; latin := true;  -- A-Z a-z
    -- ASCII specials, without space " ' ` < > \ @ (the @ is how IDs are shown)
    elsif c in (33, 35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 58, 59, 61, 63, 91, 93, 94, 95, 123, 124, 125, 126) then null;
    -- Latin with accents (Latin-1 letters, Extended-A/B, Vietnamese & co.) and the few IPA letters real alphabets use
    elsif (c between 192 and 591 and c not in (215, 247)) or (c between 7680 and 7935)
       or c in (595, 596, 599, 601, 603) then word := true; latin := true;
    elsif (c between 902 and 974 and c not in (903)) then word := true; greek := true;  -- Greek letters
    elsif (c between 1024 and 1279) then word := true; cyr := true;                      -- Cyrillic
    elsif (c between 1329 and 1366) or (c between 1377 and 1415)                         -- Armenian
       or (c between 1488 and 1514)                                                      -- Hebrew
       or (c between 1568 and 1610) or (c between 1632 and 1641) or (c between 1649 and 1747) or (c between 1776 and 1785) -- Arabic, Urdu, Persian
       or (c between 2304 and 3583)    -- Devanagari .. Sinhala (Hindi, Bengali/Assamese, Punjabi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam, Sinhala)
       or (c between 3584 and 3839)    -- Thai, Lao
       or (c between 4096 and 4255)    -- Myanmar
       or (c between 4304 and 4351)    -- Georgian
       or (c between 4608 and 4954) or (c between 4969 and 4988)                         -- Ethiopic
       or (c between 6016 and 6109) or (c between 6112 and 6121)                         -- Khmer
       or (c between 12353 and 12438) or (c between 12445 and 12447)                     -- Hiragana
       or (c between 12449 and 12538) or (c between 12540 and 12543)                     -- Katakana
       or (c between 12593 and 12686)                                                    -- Hangul jamo (ㅋㅋ)
       or (c between 13312 and 19903) or (c between 19968 and 40959)                     -- CJK
       or (c between 44032 and 55203)                                                    -- Hangul
    then word := true;
    else return false;
    end if;
  end loop;
  return word and (latin::int + greek::int + cyr::int) <= 1;
end $$;

-- IDs nobody may take (staff look-alikes).
create or replace function public.handle_reserved(p text) returns boolean
language sql immutable set search_path = '' as $$
  select lower(p) ~ '^(yaari|admin|official|moderator)'
      or lower(p) in ('support', 'staff', 'help', 'team', 'system', 'security', 'root', 'null', 'undefined')
$$;

-- Names: emoji are fine, the fancy-font letters are not (they'd be a free font), nor stacks of accents (zalgo).
create or replace function public.name_plain(p text) returns boolean
language plpgsql immutable set search_path = '' as $$
declare c int; marks int := 0;
begin
  if p is null then return true; end if;
  if p <> normalize(p, NFC) then return false; end if;
  for i in 1..char_length(p) loop
    c := ascii(substr(p, i, 1));
    if (c between 119808 and 120831)   -- Mathematical Alphanumeric Symbols (𝓛𝓲𝓼𝓪, 𝐋𝐢𝐬𝐚, 𝕃𝕚𝕤𝕒)
       or (c between 65281 and 65376)  -- fullwidth (Ｌｉｓａ)
       or (c between 7424 and 7615)    -- small capitals, modifier letters (ʟɪsᴀ, ᴸⁱˢᵃ)
       or (c between 592 and 767 and c not in (595, 596, 599, 601, 603)) -- IPA letters (upside-down text), modifier letters
       or (c between 8304 and 8351)    -- superscripts, subscripts
       or (c between 8448 and 8527)    -- letterlike (ℒ ℓ ℐ)
       or (c between 9312 and 9471)    -- circled / parenthesised (ⓛⓘⓢⓐ)
       or (c between 127232 and 127461) -- enclosed alphanumerics (🄻🄸🅂🄰), not the flag letters after them
       or (c between 42784 and 43007)  -- Latin Extended-D (ꜱ small capitals)
       or (c between 5024 and 5119) or (c between 43888 and 43967) -- Cherokee (Ꮮ Ꭵ Ꮥ Ꭺ)
       or (c between 5120 and 5759)    -- Canadian syllabics (ᒪ ᔕ ᗩ)
    then return false; end if;
    if (c between 768 and 879) or (c between 6832 and 6911) or (c between 7616 and 7679) or (c between 8400 and 8447) or (c between 65056 and 65071) then
      marks := marks + 1;
      if marks > 1 then return false; end if;
    else
      marks := 0;
    end if;
  end loop;
  return true;
end $$;

alter table public.profiles add column if not exists handle text;
alter table public.profiles add column if not exists handle_set_at timestamptz;  -- null: given by the app, change it freely

-- A free ID like "lisa_4821" from a name (only the plain letters and digits of it), or "yaari" + digits.
create or replace function public.handle_make(p_name text) returns text
language plpgsql volatile set search_path = '' as $$
declare base text; h text;
begin
  base := left(regexp_replace(lower(coalesce(p_name, '')), '[^a-z0-9._]', '', 'g'), 14);
  base := btrim(base, '._');
  if char_length(base) < 3 or public.handle_reserved(base) then base := 'user'; end if;
  for i in 1..50 loop
    h := base || '_' || lpad((floor(random() * case when i < 20 then 10000 else 100000000 end))::int::text, case when i < 20 then 4 else 8 end, '0');
    if not exists (select 1 from public.profiles p where lower(p.handle) = lower(h)) then return h; end if;
  end loop;
  return 'user_' || replace(gen_random_uuid()::text, '-', '');
end $$;

-- Everyone gets one now (the 500 test bots too); new profiles get one when they are made.
update public.profiles set handle = public.handle_make(name) where handle is null;
alter table public.profiles alter column handle set not null;
create unique index if not exists profiles_handle_key on public.profiles (lower(handle));
-- Free ones from handle_make can be longer than a chosen one (the uuid fallback); everything else is checked.
alter table public.profiles add constraint profiles_handle_check check (public.handle_valid(handle) or handle ~ '^[a-z0-9._]{3,14}_[0-9]{4,8}$' or handle ~ '^user_[0-9a-f]{32}$');

create or replace function public.profile_defaults() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.handle := public.handle_make(new.name);
  new.handle_set_at := null;
  return new;
end $$;
create or replace trigger profiles_handle before insert on public.profiles for each row execute function public.profile_defaults();

-- Names: plain letters only (existing names already are: checked 2026-10-22, 0 of 505 differ under NFKC).
create or replace function public.profile_name_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.name := normalize(new.name, NFC);
  if not public.name_plain(new.name) then raise exception 'name_invalid' using errcode = 'P0001'; end if;
  return new;
end $$;
create or replace trigger profiles_name_plain before insert or update of name on public.profiles for each row execute function public.profile_name_guard();

-- 'ok', 'invalid', 'reserved', 'taken' (or 'mine' when it is already my ID). Spelling: NFC first, like set_handle.
create or replace function public.handle_check(p_handle text) returns text
language plpgsql stable security definer set search_path = '' as $$
declare h text := normalize(btrim(coalesce(p_handle, '')), NFC); me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then return 'invalid'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.handle = h) then return 'mine'; end if;
  if public.handle_reserved(h) then return 'reserved'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = lower(h) and p.id <> me) then return 'taken'; end if;
  return 'ok';
end $$;

-- My ID. The app's free one changes at once; after that once every 7 days (a new spelling of the same ID, e.g.
-- lisa → Lisa, is always fine). Errors: handle_invalid, handle_reserved, handle_taken, handle_cooldown.
create or replace function public.set_handle(p_handle text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); h text := normalize(btrim(coalesce(p_handle, '')), NFC); cur public.profiles;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('handle:' || lower(h)));
  select * into cur from public.profiles p where p.id = me for update;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then raise exception 'handle_invalid' using errcode = 'P0001'; end if;
  if cur.handle = h then return h; end if;
  if public.handle_reserved(h) and lower(h) <> lower(cur.handle) then raise exception 'handle_reserved' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = lower(h) and p.id <> me) then
    raise exception 'handle_taken' using errcode = 'P0001';
  end if;
  if lower(cur.handle) <> lower(h) and cur.handle_set_at > now() - interval '7 days' then
    raise exception 'handle_cooldown' using errcode = 'P0001';
  end if;
  update public.profiles set handle = h,
         handle_set_at = case when lower(cur.handle) = lower(h) then cur.handle_set_at else now() end
   where id = me;
  return h;
exception when unique_violation then raise exception 'handle_taken' using errcode = 'P0001';
end $$;

-- ---- looks -------------------------------------------------------------------------------------------------------

-- The catalogue; the id is also the Google Play product id (a one-time, non-consumable product).
create table if not exists public.cosmetic_items (
  id text primary key check (id ~ '^(badge|color|font|frame)_[a-z0-9]{2,20}$'),
  kind text not null check (kind in ('badge', 'color', 'font', 'frame')),
  price_inr integer not null check (price_inr > 0),
  sort smallint not null default 0,
  active boolean not null default true,
  check (split_part(id, '_', 1) = kind)
);
alter table public.cosmetic_items enable row level security;
create policy "the shop" on public.cosmetic_items for select to anon, authenticated using (true);
grant select on public.cosmetic_items to anon, authenticated;

insert into public.cosmetic_items (id, kind, price_inr, sort) values
  ('badge_crown', 'badge', 99, 1), ('badge_diamond', 'badge', 149, 2), ('badge_flame', 'badge', 79, 3),
  ('badge_star', 'badge', 79, 4), ('badge_heart', 'badge', 79, 5), ('badge_bolt', 'badge', 79, 6),
  ('color_gold', 'color', 49, 1), ('color_rose', 'color', 49, 2), ('color_ocean', 'color', 49, 3),
  ('color_emerald', 'color', 49, 4), ('color_violet', 'color', 49, 5),
  ('color_sunset', 'color', 99, 6), ('color_aurora', 'color', 99, 7), ('color_rainbow', 'color', 129, 8),
  ('font_script', 'font', 69, 1), ('font_retro', 'font', 69, 2), ('font_bold', 'font', 69, 3),
  ('font_pixel', 'font', 69, 4), ('font_round', 'font', 69, 5), ('font_future', 'font', 69, 6),
  ('frame_frost', 'frame', 199, 1), ('frame_fire', 'frame', 199, 2), ('frame_jewel', 'frame', 249, 3),
  ('frame_sand', 'frame', 199, 4), ('frame_neon', 'frame', 149, 5), ('frame_sakura', 'frame', 149, 6)
on conflict (id) do update set kind = excluded.kind, price_inr = excluded.price_inr, sort = excluded.sort, active = true;

-- What each person owns. Written only by redeem_cosmetic (Play) and admin_grant_cosmetic.
create table if not exists public.user_cosmetics (
  user_id uuid not null references public.profiles (id) on delete cascade,
  item_id text not null references public.cosmetic_items (id),
  token text,
  acquired_at timestamptz not null default now(),
  primary key (user_id, item_id)
);
create index if not exists user_cosmetics_token on public.user_cosmetics (token) where token is not null;
alter table public.user_cosmetics enable row level security;
create policy "my looks" on public.user_cosmetics for select to authenticated using (user_id = (select auth.uid()));
grant select on public.user_cosmetics to authenticated;

-- What each person wears (one of each kind at most); only equip_look writes these (no column grant).
alter table public.profiles add column if not exists look_badge text references public.cosmetic_items (id);
alter table public.profiles add column if not exists look_color text references public.cosmetic_items (id);
alter table public.profiles add column if not exists look_font text references public.cosmetic_items (id);
alter table public.profiles add column if not exists look_frame text references public.cosmetic_items (id);
alter table public.profiles add constraint profiles_look_kinds check (
  (look_badge is null or look_badge like 'badge\_%') and (look_color is null or look_color like 'color\_%')
  and (look_font is null or look_font like 'font\_%') and (look_frame is null or look_frame like 'frame\_%'));

create or replace function public.look_json(p public.profiles) returns jsonb
language sql stable set search_path = '' as $$
  select jsonb_build_object('id', p.id, 'handle', p.handle, 'badge', p.look_badge, 'color', p.look_color,
                            'font', p.look_font, 'frame', p.look_frame)
$$;

-- IDs and looks of the people on screen (at most 100): whoever I may see (as trust()).
create or replace function public.looks(p_users uuid[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.look_json(p)), '[]') from public.profiles p
   where p.id = any (p_users[1:100])
     and (p.id = auth.uid() or public.can_view(p.id)
          or exists (select 1 from public.posts pp where pp.user_id = p.id and pp.visibility = 'public'))
$$;

-- The shop: every item for sale, what I own and what I wear.
create or replace function public.shop() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := auth.uid(); p public.profiles;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select * into p from public.profiles x where x.id = me;
  return jsonb_build_object(
    'items', (select coalesce(jsonb_agg(jsonb_build_object('id', i.id, 'kind', i.kind, 'price_inr', i.price_inr) order by i.kind, i.sort), '[]')
                from public.cosmetic_items i where i.active),
    'owned', (select coalesce(jsonb_agg(u.item_id order by u.acquired_at), '[]') from public.user_cosmetics u where u.user_id = me),
    'look', case when p.id is null then null else public.look_json(p) end,
    'handle_next', case when p.handle_set_at is null then null else p.handle_set_at + interval '7 days' end);
end $$;

-- Wear these (null: none of that kind). Every one must be mine and of its kind (bad_request / not_owned).
create or replace function public.equip_look(p_badge text, p_color text, p_font text, p_frame text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); want text[] := array[nullif(p_badge, ''), nullif(p_color, ''), nullif(p_font, ''), nullif(p_frame, '')];
  kinds text[] := array['badge', 'color', 'font', 'frame']; p public.profiles;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  for i in 1..4 loop
    if want[i] is not null then
      if split_part(want[i], '_', 1) <> kinds[i] then raise exception 'bad_request' using errcode = 'P0001'; end if;
      if not exists (select 1 from public.user_cosmetics u where u.user_id = me and u.item_id = want[i]) then
        raise exception 'not_owned' using errcode = 'P0001';
      end if;
    end if;
  end loop;
  update public.profiles set look_badge = want[1], look_color = want[2], look_font = want[3], look_frame = want[4]
   where id = me returning * into p;
  return public.look_json(p);
end $$;

-- Google Play redeemed a product token (verify-purchase, service role): the item becomes this person's. A token is
-- the first account's; 'granted', 'already' (same person again) or 'other_account'. The first item of a kind is worn
-- at once (what people expect after paying).
create or replace function public.redeem_cosmetic(p_user uuid, p_item text, p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare k text; owner uuid;
begin
  select i.kind into k from public.cosmetic_items i where i.id = p_item;
  if k is null or p_token is null or p_user is null then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.purchases (token, user_id, product_id) values (p_token, p_user, p_item) on conflict (token) do nothing;
  select x.user_id into owner from public.purchases x where x.token = p_token;
  if owner <> p_user then return 'other_account'; end if;
  insert into public.user_cosmetics (user_id, item_id, token) values (p_user, p_item, p_token)
    on conflict (user_id, item_id) do nothing;
  if not found then return 'already'; end if;
  update public.profiles set
    look_badge = case when k = 'badge' then coalesce(look_badge, p_item) else look_badge end,
    look_color = case when k = 'color' then coalesce(look_color, p_item) else look_color end,
    look_font = case when k = 'font' then coalesce(look_font, p_item) else look_font end,
    look_frame = case when k = 'frame' then coalesce(look_frame, p_item) else look_frame end
   where id = p_user;
  return 'granted';
end $$;
revoke all on function public.redeem_cosmetic(uuid, text, text) from public, anon, authenticated;
grant execute on function public.redeem_cosmetic(uuid, text, text) to service_role;

-- The owner gives an item (a gift, a tester, a refund made good). Logged like every admin action.
create or replace function public.admin_grant_cosmetic(p_user uuid, p_item text) returns void
language plpgsql security definer set search_path = '' as $$
declare a uuid := public.admin_me();
begin
  if not exists (select 1 from public.cosmetic_items i where i.id = p_item) or not exists (select 1 from public.profiles p where p.id = p_user) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  insert into public.user_cosmetics (user_id, item_id) values (p_user, p_item) on conflict do nothing;
  insert into public.admin_actions (admin, action, target, detail) values (a, 'grant_cosmetic', p_user, jsonb_build_object('item', p_item));
end $$;

-- Refunds and chargebacks (025) take a bought look back off.
create or replace function public.play_revoke(p_token text, p_reason text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare pu public.purchases; owner uuid; premium boolean; item text;
begin
  select * into pu from public.purchases x where x.token = p_token;
  owner := coalesce(pu.user_id, (select s.user_id from public.subscriptions s where s.purchase_token = p_token limit 1));
  if owner is null then return jsonb_build_object('found', false); end if;
  update public.purchases set revoked_at = coalesce(revoked_at, now()), revoke_reason = coalesce(revoke_reason, left(p_reason, 40))
   where token = p_token;
  item := (select i.id from public.cosmetic_items i where i.id = pu.product_id);
  if item is not null then
    delete from public.user_cosmetics u where u.user_id = owner and u.item_id = item;
    update public.profiles set
      look_badge = nullif(look_badge, item), look_color = nullif(look_color, item),
      look_font = nullif(look_font, item), look_frame = nullif(look_frame, item)
     where id = owner;
    return jsonb_build_object('found', true, 'user', owner, 'product', item);
  end if;
  premium := coalesce(pu.product_id, 'yaari_premium_monthly') = 'yaari_premium_monthly';
  if premium then
    update public.subscriptions set premium_until = least(premium_until, now()), updated_at = now() where user_id = owner;
  else
    update public.subscriptions set gender_until = greatest(now(), gender_until - interval '24 hours'), updated_at = now()
     where user_id = owner and gender_until > now() and pu.revoked_at is null;
  end if;
  return jsonb_build_object('found', true, 'user', owner, 'product', coalesce(pu.product_id, 'yaari_premium_monthly'));
end $$;

-- ---- the free cover and colour are gone (frames are the decoration now) ---------------------------------------------

update public.profiles set cover_path = null, accent = null where cover_path is not null or accent is not null;

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
  -- p_cover_path and p_accent are ignored (apps before 0.15 still send them).
  update public.profiles set cover_path = null, accent = null, status = s, languages = langs,
         prompts = (select coalesce(jsonb_agg(jsonb_build_object('q', e->>'q', 'a', btrim(e->>'a'))), '[]') from jsonb_array_elements(pr) e)
   where id = me;
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

-- ---- an ended random chat goes on only between friends ------------------------------------------------------------

create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and (m.ended_at is null or public.are_friends(m.user_a, m.user_b)))
$$;

alter policy "write chat" on public.messages to authenticated with check (
  sender = (select auth.uid()) and public.is_participant(match_id) and not public.chat_blocked(match_id)
  and public.chat_open(match_id)
  and (media_path is null or exists (select 1 from public.matches x where x.id = messages.match_id and x.ended_at is null)));

-- ---- grants ------------------------------------------------------------------------------------------------------

revoke all on function public.handle_check(text), public.set_handle(text), public.looks(uuid[]), public.shop(),
  public.equip_look(text, text, text, text), public.admin_grant_cosmetic(uuid, text), public.chat_open(uuid) from public, anon;
grant execute on function public.handle_check(text), public.set_handle(text), public.looks(uuid[]), public.shop(),
  public.equip_look(text, text, text, text), public.admin_grant_cosmetic(uuid, text), public.chat_open(uuid) to authenticated;
revoke all on function public.handle_make(text), public.profile_defaults(), public.profile_name_guard() from public, anon, authenticated;
