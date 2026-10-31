-- The owner bans by hand (item 5, 2026-10-31: nudity and gore, until moderation is automatic). The console could act
-- only on a report; now it finds a person by @ID, name, phone number or account id and bans them directly (a profile
-- photo seen in passing). A ban's steps move into admin_ban_user, which a report's ban (admin_resolve) uses too:
-- banned_until, their live chats end, the phone number keeps the ban (remember_ban). Both are written to
-- admin_actions as before. The search shows only the phone number's last 4 digits. Re-runnable.

create or replace function public.admin_ban_user(p_admin uuid, p_user uuid, p_days int) returns timestamptz
language plpgsql security definer set search_path = '' as $$
declare until timestamptz := case when p_days is null then 'infinity'::timestamptz else now() + make_interval(days => p_days) end;
begin
  update public.profiles set banned_until = greatest(coalesce(banned_until, now()), until) where id = p_user;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  update public.matches set ended_at = now(), ended_by = p_admin, video_status = 'none'
   where ended_at is null and p_user in (user_a, user_b);
  perform public.remember_ban(p_user);
  return until;
end $$;
revoke execute on function public.admin_ban_user(uuid, uuid, int) from public, anon, authenticated;

create or replace function public.admin_resolve(p_id bigint, p_action text, p_days integer default null, p_note text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.admin_me(); r public.reports; until timestamptz;
begin
  if p_action is null or p_action not in ('reviewed', 'dismiss', 'ban') or (p_days is not null and p_days not between 1 and 3650)
     or char_length(p_note) > 500 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  select * into r from public.reports x where x.id = p_id for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if p_action = 'ban' then
    if r.reported is null then raise exception 'not_found' using errcode = 'P0001'; end if;
    until := public.admin_ban_user(me, r.reported, p_days);
  end if;
  update public.reports set reviewed = true, reviewed_at = now(), reviewed_by = me,
         outcome = case p_action when 'dismiss' then 'dismissed' when 'ban' then 'banned' else 'reviewed' end
   where id = p_id;
  insert into public.admin_actions (admin, action, report_id, target, detail)
  values (me, p_action, p_id, r.reported, jsonb_strip_nulls(jsonb_build_object('days', p_days, 'note', nullif(btrim(coalesce(p_note, '')), ''),
          'until', until)));
end $$;

create or replace function public.admin_ban(p_user uuid, p_days int default null, p_note text default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.admin_me(); until timestamptz;
begin
  if (p_days is not null and p_days not between 1 and 3650) or char_length(p_note) > 500 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  until := public.admin_ban_user(me, p_user, p_days);
  insert into public.admin_actions (admin, action, target, detail)
  values (me, 'ban', p_user, jsonb_strip_nulls(jsonb_build_object('days', p_days, 'note', nullif(btrim(coalesce(p_note, '')), ''),
          'until', until)));
end $$;
revoke execute on function public.admin_ban(uuid, int, text) from public, anon;
grant execute on function public.admin_ban(uuid, int, text) to authenticated;

create or replace function public.admin_find(p_query text) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare q text := btrim(coalesce(p_query, '')); h text := lower(ltrim(btrim(coalesce(p_query, '')), '@'));
  digits text := regexp_replace(coalesce(p_query, ''), '\D', '', 'g');
begin
  perform public.admin_me();
  if char_length(q) < 2 then return '[]'::jsonb; end if;
  return coalesce((
    select jsonb_agg(public.admin_card(x.id) || jsonb_build_object('handle', x.handle, 'phone_end', right(x.phone, 4)) order by x.exact, x.created_at desc)
      from (select p.id, p.handle, p.created_at, u.phone,
                   case when p.id::text = q or lower(p.handle) = h or (char_length(digits) >= 8 and u.phone = digits) then 0 else 1 end as exact
              from public.profiles p join auth.users u on u.id = p.id
             where p.id::text = q or lower(p.handle) like h || '%' or p.name ilike '%' || q || '%'
                or (char_length(digits) >= 8 and u.phone like '%' || digits)
             order by 5, p.created_at desc limit 20) x), '[]'::jsonb);
end $$;
revoke execute on function public.admin_find(text) from public, anon;
grant execute on function public.admin_find(text) to authenticated;
