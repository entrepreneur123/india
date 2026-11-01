-- Owner 2026-11-01: if I take messages from friends only but write first to someone who takes them from anyone, they
-- may write back to me, friend or not (else I could insult someone who can't answer). One rule, dm_ok, for the
-- profile's message button (profile_social, profile_social2) and for opening the thread (friend_thread); a thread's
-- composer (chat_open) already allows a reply once the other one wrote in it.
create or replace function public.dm_ok(p_me uuid, p_other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_me is not null and p_other <> p_me and not public.is_blocked(p_me, p_other)
     and (public.are_friends(p_me, p_other)
          or exists (select 1 from public.profiles x where x.id = p_other and x.dm_from = 'everyone' and not x.anon)
          or exists (select 1 from public.matches m join public.messages g on g.match_id = m.id and g.sender = p_other
                      where m.kind = 'dm' and ((m.user_a = p_me and m.user_b = p_other) or (m.user_a = p_other and m.user_b = p_me))))
$$;
-- Only the functions below use it: called directly it would tell who wrote to whom.
revoke execute on function public.dm_ok(uuid, uuid) from public, anon, authenticated;

create or replace function public.friend_thread(p_user uuid) returns uuid language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid;
begin
  if not public.dm_ok(me, p_user) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)) and x.kind = 'dm'
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

create or replace function public.profile_social(p_user uuid)
returns table(likes integer, friends integer, liked boolean, can_message boolean)
language sql stable security definer set search_path = '' as $$
  select (select count(*) from public.profile_likes l where l.liked = p_user)::int,
         (select count(*) from public.friendships f where f.status = 'accepted' and p_user in (f.requester, f.addressee))::int,
         exists (select 1 from public.profile_likes l where l.liker = auth.uid() and l.liked = p_user),
         public.dm_ok(auth.uid(), p_user)
   where public.can_view(p_user)
$$;

create or replace function public.profile_social2(p_user uuid)
returns table(likes integer, friends integer, liked boolean, can_message boolean, following integer)
language sql stable security definer set search_path = '' as $$
  select (select count(*) from public.profile_likes l where l.liked = p_user)::int,
         (select count(*) from public.friendships f where f.status = 'accepted' and p_user in (f.requester, f.addressee))::int,
         exists (select 1 from public.profile_likes l where l.liker = auth.uid() and l.liked = p_user),
         public.dm_ok(auth.uid(), p_user),
         (select count(*) from public.profile_likes l where l.liker = p_user)::int
   where public.can_view(p_user)
$$;
