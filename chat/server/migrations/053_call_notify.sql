-- A friend's call rings wherever I am in the app: asking for a call (request_call, 051/052) between friends leaves a
-- 'call' notification (the bell, push, and the apps' incoming-call sheet). Answered, declined or ended: it is read.
alter table public.notifications drop constraint notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('message', 'friend_request', 'friend_accept', 'room_invite', 'post_like', 'post_comment', 'call'));

create or replace function public.notify_call() returns trigger language plpgsql security definer set search_path = '' as $$
declare them uuid := case when new.video_requested_by = new.user_a then new.user_b else new.user_a end;
begin
  update public.notifications set read_at = now() where match_id = new.id and kind = 'call' and read_at is null;
  if new.video_status = 'requested' and public.are_friends(new.user_a, new.user_b) then
    insert into public.notifications (user_id, kind, actor, match_id) values (them, 'call', new.video_requested_by, new.id);
  end if;
  return null;
end $$;
revoke execute on function public.notify_call() from public, anon, authenticated;
create or replace trigger matches_call_notify after update of video_status on public.matches for each row
  when (old.video_status is distinct from new.video_status) execute function public.notify_call();
