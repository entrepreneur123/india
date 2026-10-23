-- Recents: I can clear people from my list (Settings). They come back only if we meet again later. The list stays the
-- latest 50 (recent_list / recent_cards limit).
create table if not exists public.recent_hidden (
  user_id uuid not null references public.profiles(id) on delete cascade,
  partner uuid not null references public.profiles(id) on delete cascade,
  hidden_at timestamptz not null default now(),
  primary key (user_id, partner)
);
alter table public.recent_hidden enable row level security;
revoke all on public.recent_hidden from public, anon, authenticated;

create or replace function public.hide_recents(p_users uuid[]) returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if cardinality(p_users) > 100 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.recent_hidden (user_id, partner) select auth.uid(), u from unnest(p_users) u where u is not null
  on conflict (user_id, partner) do update set hidden_at = now();
end $$;
revoke execute on function public.hide_recents(uuid[]) from public, anon;
grant execute on function public.hide_recents(uuid[]) to authenticated;

do $$ begin
  execute replace(pg_get_functiondef('public.my_recents'::regproc),
    'where not public.is_blocked(auth.uid(), o.partner)',
    'where not public.is_blocked(auth.uid(), o.partner)
     and not exists (select 1 from public.recent_hidden h where h.user_id = auth.uid() and h.partner = o.partner and h.hidden_at > o.created_at)');
end $$;
