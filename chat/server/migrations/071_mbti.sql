-- 071 MBTI on the profile (the owner, 2026-10-27): one of the 16 types or none, shown on the profile card under the
-- status, set with the other profile details. set_profile_style2 = set_profile_style without the cover and colour
-- (gone since 040) + p_mbti; profile_style2 = profile_style + mbti. The old two stay as wrappers for installed apps
-- (a drop needs the owner's confirmation).

-- profiles + views (061's rule: a profiles column is appended to both views in the same migration) --------------------
alter table public.profiles add column if not exists mbti text
  check (mbti in ('ISTJ', 'ISFJ', 'INFJ', 'INTJ', 'ISTP', 'ISFP', 'INFP', 'INTP', 'ESTP', 'ESFP', 'ENFP', 'ENTP', 'ESTJ', 'ESFJ', 'ENFJ', 'ENTJ'));
grant select (mbti) on public.profiles to authenticated;

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
        case when x.m then null::text else p.look_bubble end as look_bubble,
        case when x.m then null::timestamptz else p.status_at end as status_at,
        p.hide_online, case when x.m then null::text else p.mbti end as mbti
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
        case when x.m then null::text else p.look_bubble end as look_bubble,
        case when x.m then null::timestamptz else p.status_at end as status_at,
        p.hide_online, case when x.m then null::text else p.mbti end as mbti
   from public.profiles p cross join lateral (select public.mask_seen(p.*, true) as m) x;

create or replace function public.set_profile_style2(p_status text, p_languages text[], p_prompts jsonb, p_mbti text) returns void
language plpgsql security definer set search_path = '' as $$
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
  update public.profiles set cover_path = null, accent = null, status = s, languages = langs, mbti = nullif(upper(btrim(p_mbti)), ''),
         prompts = (select coalesce(jsonb_agg(jsonb_build_object('q', e->>'q', 'a', btrim(e->>'a'))), '[]') from jsonb_array_elements(pr) e)
   where id = me;
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

create or replace function public.set_profile_style(p_cover_path text, p_accent text, p_status text, p_languages text[], p_prompts jsonb)
returns void language sql security definer set search_path = '' as $$
  select public.set_profile_style2(p_status, p_languages, p_prompts, (select p.mbti from public.profiles p where p.id = public.voice_me()))
$$;

create or replace function public.profile_style2(p_users uuid[])
returns table (id uuid, cover_path text, accent text, status text, languages text[], prompts jsonb, mbti text)
language sql stable security definer set search_path = '' as $$
  select p.id, p.cover_path, p.accent, p.status, p.languages, p.prompts, p.mbti from public.profiles_seen p
   where p.id = any (p_users[1:50]) and public.can_view(p.id)
$$;

create or replace function public.profile_style(p_users uuid[])
returns table (id uuid, cover_path text, accent text, status text, languages text[], prompts jsonb)
language sql stable security definer set search_path = '' as $$
  select s.id, s.cover_path, s.accent, s.status, s.languages, s.prompts from public.profile_style2(p_users) s
$$;

revoke execute on function public.set_profile_style2(text, text[], jsonb, text), public.profile_style2(uuid[]) from public, anon;
grant execute on function public.set_profile_style2(text, text[], jsonb, text), public.profile_style2(uuid[]) to authenticated;
