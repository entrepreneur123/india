-- 1:1 calls go straight between the two friends' browsers (P2P; LiveKit only when that fails). The two trade
-- connection offers on the private broadcast topic call:<match id>. Only the two people of an accepted call may
-- listen or send there: call_open (friends, their DM, nobody blocked) and video_status 'accepted', as video-token.
-- The topic is checked before the cast, so other topics never reach it.

create or replace function public.call_topic_ok(p_topic text) returns boolean
language plpgsql stable security definer set search_path = '' as $$
declare v_match uuid;
begin
  if p_topic !~ '^call:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  v_match := substr(p_topic, 6)::uuid;
  return public.call_open(v_match)
    and exists (select 1 from public.matches m where m.id = v_match and m.video_status = 'accepted');
end $$;
revoke execute on function public.call_topic_ok(text) from public, anon;
grant execute on function public.call_topic_ok(text) to authenticated;

do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'call signal listen') then
    create policy "call signal listen" on realtime.messages for select to authenticated
      using (realtime.messages.extension = 'broadcast' and public.call_topic_ok(realtime.topic()));
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'call signal send') then
    create policy "call signal send" on realtime.messages for insert to authenticated
      with check (realtime.messages.extension = 'broadcast' and public.call_topic_ok(realtime.topic()));
  end if;
end $$;
