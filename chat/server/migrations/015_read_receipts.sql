-- Read ticks (WhatsApp style): each side's last read message id lives on the chat row, so the other side sees
-- it change through the realtime UPDATE on matches it already listens to (end of chat, video).
alter table public.matches add column if not exists read_a bigint, add column if not exists read_b bigint;
grant select (read_a, read_b) on public.matches to authenticated; -- matches has column-level grants (009)

-- Marks the partner's messages up to p_upto as read. Never moves backwards, never past the newest message.
create or replace function public.mark_read(p_match uuid, p_upto bigint) returns void
language sql security definer set search_path = '' as $$
  update public.matches x set
    read_a = case when x.user_a = auth.uid() then greatest(coalesce(x.read_a, 0), least(p_upto, n.top)) else x.read_a end,
    read_b = case when x.user_b = auth.uid() then greatest(coalesce(x.read_b, 0), least(p_upto, n.top)) else x.read_b end
  from (select coalesce(max(m.id), 0) top from public.messages m where m.match_id = p_match) n
  where x.id = p_match and auth.uid() in (x.user_a, x.user_b)
    and p_upto > coalesce(case when x.user_a = auth.uid() then x.read_a else x.read_b end, 0)
$$;
revoke execute on function public.mark_read(uuid, bigint) from public, anon;
grant execute on function public.mark_read(uuid, bigint) to authenticated;
