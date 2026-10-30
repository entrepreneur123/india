-- 104's voice_read could not read its own row's "by" column inside PL/pgSQL (record "r" has no field "by"; found
-- testing it on the live server before any app used it). The column is renamed "reader" and the function made again;
-- the app still receives it as "by". Re-runnable.

do $$ begin
  if exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'voice_reads' and column_name = 'by') then
    alter table public.voice_reads rename column by to reader;
  end if;
end $$;

create or replace function public.voice_read(p_room text, p_upto bigint) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); v bigint; r public.voice_reads;
begin
  if not exists (select 1 from public.voice_members m
                  where m.room_id = p_room and m.user_id = me and not m.kicked and m.left_at is null) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select max(x.id) into v from public.voice_messages x where x.room_id = p_room and x.id <= coalesce(p_upto, 0);
  if v is not null then
    insert into public.voice_reads (room_id) select p_room
     where not exists (select 1 from public.voice_reads y where y.room_id = p_room) on conflict do nothing;
    update public.voice_reads x set
      hi2 = case when x.reader is distinct from me and v > x.hi then x.hi
                 when x.reader is distinct from me then greatest(x.hi2, v) else x.hi2 end,
      hi = greatest(x.hi, v),
      reader = case when v > x.hi then me else x.reader end
     where x.room_id = p_room and (v > x.hi or (x.reader is distinct from me and v > x.hi2))
    returning * into r;
    if found then
      perform public.voice_emit(p_room, jsonb_build_object('t', 'k', 'hi', r.hi, 'by', r.reader, 'hi2', r.hi2));
    end if;
  end if;
  if r.room_id is null then select * into r from public.voice_reads y where y.room_id = p_room; end if;
  return jsonb_build_object('hi', coalesce(r.hi, 0), 'by', r.reader, 'hi2', coalesce(r.hi2, 0));
end $$;
revoke execute on function public.voice_read(text, bigint) from public, anon;
grant execute on function public.voice_read(text, bigint) to authenticated;
