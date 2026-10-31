-- Names without emoji or symbols (the owner, 2026-10-31, item 4: "no fancy fonts or special characters; . , _ are fine,
-- as Instagram"). Until now a name refused only fancy-font letters (name_plain, 040/055); emoji and symbols passed.
-- name_clean() also refuses ASCII signs but . , _ - ' and space, Latin-1 signs, invisible spaces, arrows, maths,
-- dingbats and other symbols (★ ♥), braille blanks, CJK signs (not 々 〆 〇), Hangul fillers (blank names), emoji
-- and their variation selectors, private use, and the Tibetan, Javanese and Balinese signs of "stylish" names
-- (༺ ༻ ꧁ ꧂). Letters of every language stay, with their marks (Postgres's [[:alpha:]] refuses Hindi, Bengali or
-- Arabic letters with marks here, checked on this database, so the check lists what it refuses). The web app's
-- nameClean (lib/handle.ts) is the stricter side: only letters, marks, digits and those signs.
-- A name is checked when it is set or changed: someone whose name has an emoji can still edit the rest of the
-- profile. Spaces are tidied (trimmed, one between words) rather than refused. Re-runnable.

create or replace function public.name_clean(p text) returns boolean
language plpgsql immutable set search_path = '' as $$
declare c int;
begin
  if p is null then return true; end if;
  if not public.name_plain(p) or p ~ '[!"#$%&()*+/:;<=>?@\[\\\]^`{|}~]' then return false; end if;
  for i in 1..char_length(p) loop
    c := ascii(substr(p, i, 1));
    if c < 32 or c = 127
       or (c between 161 and 191) or c in (215, 247)                      -- Latin-1 signs: ¡ © ® ° ± ¶ « » × ÷
       or (c between 8192 and 8303 and c not in (8204, 8205, 8217))       -- punctuation, invisible spaces (not ZWNJ, ZWJ, ’)
       or (c between 8304 and 11263)                                       -- super/subscripts to symbols: ₹ → ∞ ★ ♥ ✓, braille
       or (c between 11776 and 11903)                                      -- supplemental punctuation
       or (c between 12288 and 12351 and c not between 12293 and 12295)   -- CJK signs and spaces (not 々 〆 〇)
       or c in (4447, 4448, 12644, 65440, 65279)                           -- Hangul fillers, zero-width no-break space
       or (c between 65024 and 65135)                                      -- variation selectors, vertical and small forms
       or (c between 65504 and 65519)                                      -- fullwidth signs ￠ ￡ ￥
       or (c between 57344 and 63743) or c >= 983040                       -- private use
       or (c between 126976 and 130047)                                    -- emoji, pictographs, flags' letters, legacy symbols
       or (c between 917504 and 917631)                                    -- emoji tags
       or (c between 3841 and 3863) or (c between 3866 and 3871) or c in (3892, 3894, 3896) or (c between 3898 and 3901)
       or (c between 4030 and 4058 and c <> 4038)                          -- Tibetan signs (༒ ༺ ༻ ࿐)
       or (c between 43457 and 43469) or c in (43486, 43487)               -- Javanese punctuation (꧁ ꧂)
       or (c between 7002 and 7039)                                        -- Balinese punctuation and musical signs
    then return false; end if;
  end loop;
  return true;
end $$;

create or replace function public.profile_name_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.name := regexp_replace(btrim(normalize(new.name, NFC)), '\s+', ' ', 'g');
  new.bio := normalize(new.bio, NFC);
  new.status := normalize(new.status, NFC);
  if (tg_op = 'INSERT' or new.name is distinct from old.name)
     and (not public.name_clean(new.name) or public.looks_like_vip(new.name)) then
    raise exception 'name_invalid' using errcode = 'P0001';
  end if;
  if not (public.name_plain(new.bio) and public.name_plain(new.status) and public.name_plain(normalize(new.prompts::text, NFC))) then
    raise exception 'text_invalid' using errcode = 'P0001';
  end if;
  return new;
end $$;
