-- Friends' presence at once (the owner, 2026-10-28: "a friend shows online only after I refresh"). Until now the
-- friends panel asked friend_activity() every 20 s and a friend counted as online up to 60 s after their last stamp.
-- Now a change is pushed: when I open the app, start or stop matching, chatting or a room, or close the app, my online
-- friends get a tiny private broadcast ('p' on 'friends:<their id>') and read friend_activity() again at once.
--  * The broadcast carries nothing: what they see is still decided by friend_activity() (hidden online stays hidden).
--  * Only friends online now get it, at most one push per person every 2 s (pinged_at), never for a hidden person.
--  * Closing the app (presence_ping(false)) also clears my stamp, so I show offline at once instead of after 60 s.

alter table public.user_seen add column if not exists pinged_at timestamptz;

create or replace function public.presence_ping(p_online boolean default true) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); was timestamptz;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select s.pinged_at into was from public.user_seen s where s.user_id = me;
  insert into public.user_seen as s (user_id, seen_at, pinged_at)
  values (me, case when p_online then now() else '-infinity'::timestamptz end, now())
  on conflict (user_id) do update set seen_at = excluded.seen_at, pinged_at = now();
  -- A burst of changes (or a loop) tells friends once; closing the app always does.
  if p_online and was > now() - interval '2 seconds' then return; end if;
  if (select p.hide_online from public.profiles p where p.id = me) then return; end if;
  perform realtime.send('{}'::jsonb, 'p', 'friends:' || f.id, true)
     from (select case when fr.requester = me then fr.addressee else fr.requester end as id
             from public.friendships fr
            where me in (fr.requester, fr.addressee) and fr.status = 'accepted' and not public.is_blocked(fr.requester, fr.addressee)
            limit 500) f
    where public.user_online(f.id);
end $$;
revoke execute on function public.presence_ping(boolean) from public, anon;
grant execute on function public.presence_ping(boolean) to authenticated;

-- Only I may listen on my own topic.
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'friend presence') then
    create policy "friend presence" on realtime.messages for select to authenticated
      using (realtime.messages.extension = 'broadcast' and realtime.topic() = 'friends:' || auth.uid()::text);
  end if;
end $$;
