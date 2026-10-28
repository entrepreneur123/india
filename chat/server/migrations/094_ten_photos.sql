-- Up to 10 profile photos besides the main one (the owner, 2026-10-28: "50 → 10"; replaces 093's 50).
-- No profile had more than 10 when this was written. set_photos is 093's with 10 in place of 50.

alter table public.profiles drop constraint if exists profiles_photos_check;
alter table public.profiles add constraint profiles_photos_check check (cardinality(photos) <= 10);

create or replace function public.set_photos(p_paths text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p text[] := coalesce(p_paths, '{}');
begin
  if cardinality(p) > 10 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
     or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                  or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  update public.profiles set photos = p where id = me;
end $$;
