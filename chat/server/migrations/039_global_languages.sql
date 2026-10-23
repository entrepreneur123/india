-- Global (the owner: Yaari is a worldwide app). Rooms, matching and profiles accept every app language, not just
-- the first five: room language and "prefer my language" in matching take the 36 app languages (lower case, as
-- before: 'pt-br', 'zh-hans'); a profile's spoken languages take any of 62 common languages (plain codes).

create or replace function public.app_lang(p text) returns text
language sql immutable set search_path = '' as $$
  select case when lower(p) = any (array['en','hi','bn','ur','pa','mr','gu','ta','te','kn','ml','ne','pt-br','es','fr',
    'de','it','nl','pl','ro','tr','ru','uk','ar','fa','he','sw','vi','th','id','ms','fil','ja','ko','zh-hans','zh-hant'])
  then lower(p) end
$$;

create or replace function public.voice_lang(p text) returns text
language sql immutable set search_path = '' as $$ select public.app_lang(p) $$;

alter table public.voice_rooms drop constraint if exists voice_rooms_lang_check;
alter table public.voice_rooms add constraint voice_rooms_lang_check check (lang is null or public.app_lang(lang) = lang);

alter table public.profiles drop constraint if exists profiles_languages_check;
alter table public.profiles add constraint profiles_languages_check check (cardinality(languages) <= 6 and languages <@ array[
  'en','hi','bn','ur','pa','mr','gu','ta','te','kn','ml','or','as','ne','si','pt','es','fr','de','it','nl','pl','ro',
  'cs','sk','hu','el','sv','da','no','fi','uk','ru','bg','sr','hr','tr','ar','fa','he','sw','am','ha','yo','zu','vi',
  'th','id','ms','fil','my','km','lo','ja','ko','zh','kk','uz','az','ka','hy']::text[]);

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.find_match2(text, text)'::regprocedure);
  a := 'my_lang text := case when lower(p_lang) in (''en'', ''hi'', ''bn'', ''pt-br'', ''vi'') then lower(p_lang) end;';
  if position(a in d) = 0 then raise exception 'find_match2: anchor not found'; end if;
  execute replace(d, a, 'my_lang text := public.app_lang(p_lang);');
end $$;
