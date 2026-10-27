-- Photo gradient (the owner, 2026-10-27): 2 to 5 photos (was 2-4); a profile has up to 5 (its photo and 4 more).
-- The check is replaced in place (it only ever allowed fewer); set_cycle is 062's with the new upper bound.

alter table public.profiles drop constraint if exists profiles_cycle_photos_check;
alter table public.profiles add constraint profiles_cycle_photos_check
  check (cycle_photos is null or cardinality(cycle_photos) between 2 and 5);

create or replace function public.set_cycle(p_paths text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p text[] := nullif(coalesce(p_paths, '{}'), '{}');
begin
  if p is not null then
    if not public.owns(me, 'cycle_photos') then raise exception 'not_owned' using errcode = 'P0001'; end if;
    if cardinality(p) not between 2 and 5 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
       or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                    or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
      raise exception 'bad_request' using errcode = 'P0001';
    end if;
  end if;
  update public.profiles set cycle_photos = p where id = me;
end $$;
