-- Private rooms in the list (the owner, 2026-10-26: "show them too, nobody gets in without the key"): voice_list4
-- lists private rooms like any other; someone outside sees 'room_private' and enters with the room's code (voice_admit
-- still refuses a private room without it; someone who came in once may come back, as before). p_locked false leaves
-- out both kinds of locked room: private ones and ones with a password. voice_list3 (installed apps) follows.

create or replace function public.voice_list4(p_query text default null, p_lang text default null, p_countries text[] default null,
  p_locked boolean default true)
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
  now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
  announcement text, code text, refusal text, lang text)
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare me uuid := auth.uid(); q text := nullif(btrim(coalesce(p_query, '')), ''); l text := public.voice_lang(p_lang);
  cs text[] := nullif(p_countries, '{}');
begin
  if char_length(q) > 40 or cardinality(cs) > 300 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select c.at from public.voice_list_cache c where c.id = 1) < now() - interval '5 seconds'
     and pg_try_advisory_xact_lock(hashtext('voice_list_refresh')) then
    perform public.voice_list_refresh();
  end if;
  return query
  with p as (
    select x.gender, x.country, extract(year from age(x.birth_date))::int as age from public.profiles x where x.id = me
  ), k as (
    select m.room_id, m.kicked, m.seat,
           m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds' as fresh,
           m.left_at is null and not m.kicked as present
      from public.voice_members m where m.user_id = me
  ), c as (
    select * from jsonb_to_recordset((select y.rooms from public.voice_list_cache y where y.id = 1)) as x(
      id text, title text, category text, host uuid, created_at timestamptz, max_members int, gender_rule text, min_age int,
      max_age int, is_private boolean, announcement text, lang text, now_playing text, game text, host_card jsonb, speakers jsonb,
      members int, listeners int, fresh int, locked boolean)
  ), v as (
    select c.*, coalesce(k.present and (k.fresh or c.host = me), false) as here, k.seat as my_seat, coalesce(k.fresh, false) as my_fresh,
           coalesce(k.present, false) as inside, k.room_id is not null as known
      from c join public.voice_rooms r on r.id = c.id and r.ended_at is null
      left join k on k.room_id = c.id
     where not coalesce(k.kicked, false)
       and (q is null or strpos(lower(c.title), lower(q)) > 0)
       and (l is null or c.lang = l)
       and (cs is null or c.host_card->>'country' = any (cs))
       and (coalesce(p_locked, true) or c.host = me or k.present or not (coalesce(c.locked, false) or (c.is_private and k.room_id is null)))
       and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = c.host)
       and not exists (select 1 from public.blocks b where b.blocker = c.host and b.blocked = me)
  )
  select v.id, v.title, v.category, v.host = me, v.host_card, v.speakers,
         v.listeners - (v.here and v.my_seat is null)::int, v.members, v.now_playing, v.game,
         v.max_members, v.gender_rule, v.min_age, v.max_age, v.is_private, v.announcement,
         case when v.host = me then (select x.code from public.voice_room_codes x where x.room_id = v.id) end,
         case
           when v.host = me then null
           when v.gender_rule <> 'any' and (select p.gender from p) is distinct from v.gender_rule then 'room_gender'
           when (select p.age from p) < v.min_age or (select p.age from p) > v.max_age then 'room_age'
           when not v.here and v.fresh - v.my_fresh::int >= v.max_members then 'room_full'
           when v.is_private and not v.known then 'room_private'
           when coalesce(v.locked, false) and not v.inside then 'room_password'
         end,
         v.lang
    from v
   order by v.host = me desc, v.host_card->>'country' is not distinct from (select p.country from p) desc, v.members desc, v.created_at desc
   limit 100;
end $$;
