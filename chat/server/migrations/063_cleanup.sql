-- Leftovers of 055 that only a drop removes (the database tool asks the owner to confirm drops, so they wait here):
-- find_match / find_match2 (wrappers no app calls; both apps use find_match3), queue.want (043's country choice),
-- app_stats.online (055 counts by country), set_country and its trigger (check_adult finds the country since 055).
drop function if exists public.find_match2(text, text);
drop function if exists public.find_match(text);
alter table public.queue drop column if exists want;
alter table public.app_stats drop column if exists online;
drop trigger if exists profiles_country on public.profiles;
drop function if exists public.set_country();
