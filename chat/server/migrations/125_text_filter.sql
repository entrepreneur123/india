-- 125: chat text filter, stage 1 — swear words and slurs are masked (***) on the server, and repeat / link spam to
-- strangers is held back. Free, code only (no outside service).
--
-- Mask, not refuse, for chat lines: the line still arrives with the word starred (Roblox's ### chat, Twitch viewers'
-- chat filters hide the word, not the conversation), so a false hit costs one starred word rather than a lost message
-- (Discord AutoMod and Twitch blocked terms refuse the whole message instead). Names are refused, not masked
-- ('name_rude'): Kakao keeps a word list for nicknames and room titles, and a starred name would still say it.
--
-- One function does it for every way text is written: text_mask(), called by the existing guards — message_guard
-- (random chats and friend DMs), voice_line_guard (room chat lines), group_send, megaphone_send, comment_add,
-- post_limit (post captions) and profile_name_guard (bio and status masked, a rude name refused).
--
-- Matching is per word (Scunthorpe: 'class', 'assistant', 'Scunthorpe' are whole words that only contain one), after
-- folding what people use to get round a list: fancy fonts and full-width letters (NFKD), accents and Arabic
-- harakat/tatweel, invisible characters (also removed from the stored text), l33t digits and @ $ inside a word,
-- stretched letters ('fuuuuck', 'shitt' — only words of 4+ letters, so the Hindi 'aas' never reads as 'ass') and
-- letters spelled out one by one ('f u c k', 'f.u.c.k'). A few roots never found inside a normal word (checked
-- against a 275,000-word English list) match anywhere in a word: 'fuck' in 'motherfucker', 'chutiy' in 'chutiyapa'.
-- The words live in text_filter_words, so the list can grow without a new function.
--
-- Spam (measured before this: ten copies of a line with a link to a stranger all went through; only the 11th in
-- 10 seconds was stopped): a third copy of the same line in one chat within a minute, the same line (12+ letters) in
-- a fifth chat within 10 minutes, or a fourth line with a link or phone number to people who aren't friends within
-- 10 minutes is refused with 'spam'. Twitch refuses a copy of your last line within 30 seconds; ours is looser. Friends
-- share links freely. Lines written by the server (bots) are not counted. Re-runnable.

create table if not exists public.text_filter_words (
  word text primary key,           -- as text_key() leaves it
  squeezed text generated always as (regexp_replace(word, '(.)\1+', '\1', 'g')) stored,
  anywhere boolean not null default false
);
create index if not exists text_filter_words_squeezed on public.text_filter_words (squeezed);
alter table public.text_filter_words enable row level security;
revoke all on public.text_filter_words from public, anon, authenticated;

/** A word as it is compared: lower case, no accents or harakat, fancy letters folded, l33t read as letters. */
create or replace function public.text_key(p text) returns text
language sql immutable parallel safe set search_path = '' as $$
  select case when k ~ '[a-z]' then translate(k, '013457@$', 'oieastas') else k end
  from (select normalize(regexp_replace(lower(normalize(p, NFKD)), '[\x0300-\x036f\x0640\x064b-\x065f\x0670\x200c\x200d]', '', 'g'), NFC) k) x
$$;

/** The text with each bad word starred, same length; invisible characters removed. Spaces and signs are kept. */
create or replace function public.text_mask(p text) returns text
language plpgsql stable set search_path = '' as $$
declare
  -- What separates words: spaces, ASCII signs but @ and $, Latin-1 signs, general punctuation (not ZWNJ/ZWJ),
  -- symbols, CJK signs, variation selectors, full-width signs, emoji, danda, Arabic punctuation.
  cls constant text := '\s!-#%-/:-?\[-`{-~\x00a0-\x00bf\x00d7\x00f7\x2000-\x200b\x200e-\x206f\x2100-\x2bff\x3000-\x303f\xfe00-\xfe0f\xfe30-\xfe4f\xff01-\xff0f\U0001f000-\U0001faff\x0964\x0965\x060c\x061b\x061f\x066a-\x066d\x06d4';
  r text[]; k text[]; runs int[] := '{}'; n int; i int := 1; j int; x int; y int; w text;
begin
  if p is null or p = '' then return p; end if;
  p := regexp_replace(p, '[\x00ad\x200b\x2060\xfeff]', '', 'g');
  -- Plain ASCII letters are their own key in lower case (what text_key gives them), without the function call.
  select array_agg(m[1] order by o),
         array_agg(case when m[1] ~ '^[A-Za-z]+$' then lower(m[1]) when m[1] ~ ('^[^' || cls || ']') then public.text_key(m[1]) end order by o)
    into r, k from regexp_matches(p, '[' || cls || ']+|[^' || cls || ']+', 'g') with ordinality t(m, o);
  n := coalesce(array_length(r, 1), 0);
  -- Letters spelled out one by one with a space or . _ * - between them ('f u c k'): also looked up as one word,
  -- added after the words; runs[] keeps the first and last token of each.
  while i <= n loop
    j := i;
    if char_length(k[i]) = 1 then
      w := r[i];
      while j + 2 <= n and char_length(k[j + 2]) = 1 and r[j + 1] ~ '^[ ._*-]{1,3}$' loop j := j + 2; w := w || r[j]; end loop;
      if j - i >= 4 then k := k || public.text_key(w); runs := runs || i || j; end if;
    end if;
    i := j + 1;
  end loop;
  -- One lookup for all: a listed word; a stretched one ('fuuuck', 4+ letters, so the Hindi 'aas' stays); a listed root inside.
  for x in
    with u as materialized (select o, t, regexp_replace(t, '(.)\1+', '\1', 'g') s from unnest(k) with ordinality u(t, o) where t is not null)
    select o from u join public.text_filter_words f on f.word = u.t
    union select o from u join public.text_filter_words f on f.squeezed = u.s where char_length(u.t) >= 4 and u.s <> u.t
    union select o from u join public.text_filter_words f on f.anywhere and (strpos(u.t, f.word) > 0 or strpos(u.s, f.word) > 0)
  loop
    if x <= n then r[x] := repeat('*', char_length(r[x]));
    else for y in runs[2 * (x - n) - 1]..runs[2 * (x - n)] by 2 loop r[y] := '*'; end loop;
    end if;
  end loop;
  return array_to_string(r, '');
end $$;

/** A line as compared for repeats: lower case without spaces or invisible characters. */
create or replace function public.text_squash(p text) returns text
language sql immutable parallel safe set search_path = '' as $$
  select regexp_replace(lower(p), '[\s\x00ad\x200b-\x200f\x2060\xfeff\U000e0000-\U000e007f]+', '', 'g')
$$;

/** A link or a phone number. */
create or replace function public.text_has_link(p text) returns boolean
language sql immutable parallel safe set search_path = '' as $$
  select p ~* '(https?://|www\.|t\.me/|wa\.me/|\m[a-z0-9-]+\.(com|net|org|in|io|me|ly|gg|xyz|app|link|site|online|info|co|to|ru|top|club|live|shop|store|biz|vip|cc)\M|(\d[ .-]?){9}\d)'
$$;

revoke execute on function public.text_key(text), public.text_mask(text), public.text_squash(text),
  public.text_has_link(text) from public, anon, authenticated;

insert into public.text_filter_words (word, anywhere)
select distinct on (public.text_key(w)) public.text_key(w), a from (values
  -- English
  ('fuck', true), ('bitch', true), ('shit', false), ('shits', false), ('shitty', false), ('shitting', false), ('shithead', false),
  ('bullshit', false), ('horseshit', false), ('dipshit', false), ('ass', false), ('asshole', false), ('assholes', false),
  ('arsehole', false), ('bastard', false), ('bastards', false), ('cunt', false), ('cunts', false), ('dickhead', false),
  ('cocksucker', false), ('whore', false), ('whores', false), ('slut', false), ('sluts', false), ('slutty', false),
  ('wanker', false), ('twat', false), ('retard', false), ('retarded', false), ('nigger', false), ('niggers', false),
  ('nigga', false), ('niggas', false), ('faggot', false), ('faggots', false), ('kike', false), ('tranny', false),
  -- Hindi / Hinglish, Latin letters
  ('madarchod', true), ('maderchod', true), ('behenchod', true), ('bhenchod', true), ('behnchod', true), ('benchod', true),
  ('bhosdi', true), ('bhosadi', true), ('chutiy', true), ('chootiy', true), ('gandu', false), ('gaandu', false),
  ('lauda', false), ('lavda', false), ('lawda', false), ('lund', false), ('randi', false), ('haramzada', false),
  ('haramzade', false), ('bhadwa', false), ('bhadwe', false), ('bsdk', false),
  -- Hindi, Devanagari
  ('मादरचोद', true), ('बहनचोद', true), ('बेहनचोद', true), ('भेनचोद', true), ('भोसड़ी', true), ('भोसडी', true),
  ('चूतिय', true), ('चुतिय', true), ('गांडू', false), ('गाँडू', false), ('गान्डू', false), ('लौड़ा', false), ('लौडा', false),
  ('लौड़े', false), ('लौडे', false), ('लंड', false), ('रंडी', false), ('हरामज़ादा', false), ('हरामजादा', false),
  -- Arabic
  ('شرموطة', false), ('شرموطه', false), ('شرموط', false), ('منيوك', false), ('قحبة', false), ('قحبه', false), ('كسمك', false), ('عرص', false),
  -- Spanish / Portuguese
  ('puta', false), ('putas', false), ('puto', false), ('hijueputa', false), ('hijoputa', false), ('pendejo', false),
  ('pendeja', false), ('mierda', false), ('coño', false), ('gilipollas', false), ('maricón', false), ('caralho', false),
  ('buceta', false), ('viado', false), ('arrombado', false),
  -- Indonesian / Malay / Filipino
  ('kontol', false), ('memek', false), ('ngentot', false), ('bangsat', false), ('pukimak', false), ('jancok', false),
  ('jancuk', false), ('putangina', false), ('tangina', false), ('pakyu', false),
  -- Turkish
  ('orospu', true), ('siktir', true), ('amına', false), ('yarrak', false),
  -- Russian / Ukrainian
  ('блять', false), ('блядь', false), ('бля', false), ('сука', false), ('суки', false), ('хуй', false), ('нахуй', false),
  ('хуйня', false), ('пизда', false), ('пиздец', false), ('ебать', false), ('ебаный', false),
  -- German / French / Italian / Polish
  ('hurensohn', false), ('wichser', false), ('fotze', false), ('connard', false), ('connasse', false), ('salope', false),
  ('enculé', false), ('putain', false), ('merde', false), ('cazzo', false), ('vaffanculo', false), ('stronzo', false),
  ('stronza', false), ('puttana', false), ('minchia', false), ('kurwa', false), ('kurwy', false), ('chuj', false),
  ('pierdolę', false), ('jebać', false),
  -- Korean / Chinese (no spaces between words: roots match anywhere)
  ('씨발', true), ('개새끼', true), ('좆', true), ('傻逼', true), ('操你妈', true), ('肏', true)
) v(w, a)
on conflict (word) do nothing;

-- Random chats and friend DMs: the client inserts the row; the guard masks it before it is stored and broadcast.
create or replace function public.message_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
declare s text; here int; elsewhere int; other uuid;
begin
  perform pg_advisory_xact_lock(hashtext('msg:' || new.sender::text));
  if exists (select 1 from public.profiles p where p.id = new.sender and p.banned_until > now()) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if (select count(*) from public.messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  perform public.chat_body_check(new.sender, new.body);
  new.body := public.text_mask(new.body);
  if auth.uid() is null or new.body is null then return new; end if;
  s := public.text_squash(new.body);
  select count(*) filter (where m.match_id = new.match_id and m.created_at > now() - interval '1 minute'),
         count(distinct m.match_id) filter (where m.match_id <> new.match_id)
    into here, elsewhere
    from public.messages m
   where m.sender = new.sender and m.created_at > now() - interval '10 minutes' and public.text_squash(m.body) = s;
  if here >= 2 or (char_length(s) >= 12 and elsewhere >= 4) then raise exception 'spam' using errcode = 'P0001'; end if;
  if public.text_has_link(new.body) then
    select case when x.user_a = new.sender then x.user_b else x.user_a end into other from public.matches x where x.id = new.match_id;
    if not public.are_friends(new.sender, other) and (
      select count(*) from public.messages m join public.matches x on x.id = m.match_id
       where m.sender = new.sender and m.created_at > now() - interval '10 minutes' and public.text_has_link(m.body)
         and not public.are_friends(new.sender, case when x.user_a = new.sender then x.user_b else x.user_a end)) >= 3 then
      raise exception 'spam' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

-- Room chat lines (every text line goes through this trigger, whichever function writes it).
create or replace function public.voice_line_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.kind = 'text' then
    perform public.chat_body_check(new.sender, new.body);
    new.body := public.text_mask(new.body);
    if auth.uid() = new.sender and (
      select count(*) from public.voice_messages x
       where x.sender = new.sender and x.room_id = new.room_id and x.kind = 'text'
         and x.created_at > now() - interval '1 minute' and public.text_squash(x.body) = public.text_squash(new.body)) >= 2 then
      raise exception 'spam' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

create or replace function public.group_send(p_group uuid, p_body text, p_client uuid)
returns bigint language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); b text := btrim(coalesce(p_body, '')); n bigint;
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(b) not between 1 and 2000 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('gmsg:' || me::text));
  -- Sent before (its reply was lost): that line is the answer. After the lock, so two tries can't both insert.
  if p_client is not null then
    select m.id into n from public.group_messages m where m.sender = me and m.client_id = p_client;
    if n is not null then return n; end if;
  end if;
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  if (select count(*) from public.group_messages m where m.sender = me and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return public.group_line(p_group, me, 'text', public.text_mask(b), p_client);
end $$;

create or replace function public.megaphone_send(p_body text)
returns bigint language plpgsql security definer set search_path = '' as $$
declare
  me uuid := public.voice_me();
  b text := btrim(coalesce(p_body, ''));
  p public.profiles;
  mid bigint;
begin
  if b = '' or char_length(b) > 80 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform public.chat_body_check(me, b);
  b := public.text_mask(b);
  perform pg_advisory_xact_lock(hashtext('mega:' || me::text));
  if exists (select 1 from public.megaphones m where m.user_id = me and m.created_at > now() - interval '30 seconds') then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  update public.megaphone_wallet set n = n - (not unlimited)::int where user_id = me and (unlimited or n > 0);
  if not found then raise exception 'no_megaphone' using errcode = 'P0001'; end if;
  select * into p from public.profiles x where x.id = me;
  insert into public.megaphones (user_id, country, body) values (me, p.country, b) returning id into mid;
  perform realtime.send(
    jsonb_build_object('id', mid, 'user', me, 'name', p.name, 'handle', p.handle, 'photo', p.photo_path, 'body', b,
                       'at', (extract(epoch from now()) * 1000)::bigint),
    'm', public.megaphone_topic(p.country), true);
  return mid;
end $$;

create or replace function public.comment_add(p_post uuid, p_body text, p_reply_to bigint default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.posts; c public.post_comments; t text := btrim(coalesce(p_body, ''));
begin
  perform public.require_verified(me);
  if char_length(t) not between 1 and 300 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  t := public.text_mask(t);
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

create or replace function public.post_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform public.require_verified(new.user_id);
  if (select count(*) from public.posts p where p.user_id = new.user_id and p.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  new.body := public.text_mask(new.body);
  return new;
end $$;

create or replace function public.profile_name_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.name := regexp_replace(btrim(normalize(new.name, NFC)), '\s+', ' ', 'g');
  new.bio := public.text_mask(normalize(new.bio, NFC));
  new.status := public.text_mask(normalize(new.status, NFC));
  if tg_op = 'INSERT' or new.name is distinct from old.name then
    if not public.name_clean(new.name) or public.looks_like_vip(new.name) then
      raise exception 'name_invalid' using errcode = 'P0001';
    end if;
    if public.text_mask(new.name) is distinct from new.name then raise exception 'name_rude' using errcode = 'P0001'; end if;
  end if;
  if not (public.name_plain(new.bio) and public.name_plain(new.status) and public.name_plain(normalize(new.prompts::text, NFC))) then
    raise exception 'text_invalid' using errcode = 'P0001';
  end if;
  return new;
end $$;
