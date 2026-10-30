-- The owner, 2026-10-30: anyone may message anyone by default (was friends only, 059). Everyone still on the old
-- default moves too (504 of 505 profiles, all on the default; the app isn't out yet). The setting stays: Settings >
-- "Anyone can message me" turns it back to friends only. VIP (066) and blocks still keep strangers out.
-- Runs once: the move happens only while the old default is still set, so a later re-run never undoes someone's choice.
do $$ begin
  if (select column_default from information_schema.columns
       where table_schema = 'public' and table_name = 'profiles' and column_name = 'dm_from') like '''friends''%' then
    update public.profiles set dm_from = 'everyone' where dm_from = 'friends';
    alter table public.profiles alter column dm_from set default 'everyone';
  end if;
end $$;
