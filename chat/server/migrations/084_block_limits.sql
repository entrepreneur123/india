-- 8 (the owner, 2026-10-27): blocking someone who isn't a friend. A block also keeps the two apart in random chat,
-- so blocking everyone of one gender would work as a free gender filter (a paid item, 026). Against that:
--  * at most 10 blocks a day of people who aren't friends ('block_limit'; a report still goes through, reviewed);
--  * the admin sees who blocks mostly one gender of the people met in random chat (admin_block_skew: 10 or more such
--    blocks and reports in 30 days, 85% or more of one gender), to look at with the reports — someone blocking
--    harassers is told apart there, not by a rule that would take their protection away.

create or replace function public.block_user(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = p_user) then raise exception 'not_found' using errcode = 'P0001'; end if;
  if not public.are_friends(me, p_user)
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = p_user)
     and (select count(*) from public.blocks b where b.blocker = me and b.created_at > now() - interval '1 day') >= 10 then
    raise exception 'block_limit' using errcode = 'P0001';
  end if;
  insert into public.blocks (blocker, blocked) values (me, p_user) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_user) or (user_b = me and user_a = p_user));
end $$;

create or replace function public.admin_block_skew()
returns table (user_id uuid, name text, blocks int, gender text, share numeric)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  return query
  with b as (
    select x.blocker, t.gender from public.blocks x join public.profiles t on t.id = x.blocked
     where x.created_at > now() - interval '30 days'
       and exists (select 1 from public.matches m where m.kind = 'random'
                    and ((m.user_a = x.blocker and m.user_b = x.blocked) or (m.user_b = x.blocker and m.user_a = x.blocked)))
  ), g as (
    select b.blocker, b.gender, count(*) as n from b group by 1, 2
  ), t as (
    select g.blocker, sum(g.n) as total, max(g.n) as top from g group by 1
  )
  select t.blocker, p.name, t.total::int,
         (select g.gender from g where g.blocker = t.blocker order by g.n desc limit 1),
         round(t.top::numeric / t.total, 2)
    from t join public.profiles p on p.id = t.blocker
   where t.total >= 10 and t.top::numeric / t.total >= 0.85
   order by t.total desc
   limit 50;
end $$;

revoke execute on function public.admin_block_skew() from public, anon;
grant execute on function public.admin_block_skew() to authenticated;
