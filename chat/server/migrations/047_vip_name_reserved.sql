-- "VIP" is what incognito people (046) are shown as; nobody may take it as a real name and pass for one.
alter table public.profiles add constraint profiles_name_not_vip check (lower(btrim(name)) <> 'vip');
-- The name check trigger says so first, as name_invalid (which the apps already explain).
do $$ begin
  execute replace(pg_get_functiondef('public.profile_name_guard'::regproc),
    'if not public.name_plain(new.name) then', 'if not public.name_plain(new.name) or lower(btrim(new.name)) = ''vip'' then');
end $$;
