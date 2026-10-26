-- Fix of 059: profiles got dm_from, but looks() turns a profiles_seen row into a profiles row by position
-- (row(p.*)::public.profiles), so every looks() call failed ("Input has too few columns") and nobody's badge, colour,
-- font or frame showed. Both masking views get the new column at the same place as in profiles (last).
-- Rule: a column added to public.profiles must be appended to profiles_seen and profiles_public in the same migration.
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
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from
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
        p.handle_passes, case when x.m then null::text else p.look_wall end as look_wall, p.anon, p.dm_from
   from public.profiles p cross join lateral (select public.mask_seen(p.*, true) as m) x;
