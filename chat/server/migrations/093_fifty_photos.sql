-- Up to 50 profile photos besides the main one (the owner, 2026-10-28: "account photos go up to 50"; 024 allowed 4).
-- Others see them by tapping the profile photo; the photo gradient (062/085) picks 2–5 of them.
-- set_photos is 024's with 50 in place of 4.

alter table public.profiles drop constraint if exists profiles_photos_check;
alter table public.profiles add constraint profiles_photos_check check (cardinality(photos) <= 50);

create or replace function public.set_photos(p_paths text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p text[] := coalesce(p_paths, '{}');
begin
  if cardinality(p) > 50 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
     or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                  or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  update public.profiles set photos = p where id = me;
end $$;
