-- The name's colour comes from one item at a time (the owner, 2026-10-28: "ID colour change doesn't apply"): a name
-- effect (062) drew over the ID colour, so a new colour never showed. Now wearing a new colour takes the name effect
-- off, and wearing a name effect takes the colour off; the last one chosen is what everyone sees.
-- Both functions are 040's and 062's with that one change.

create or replace function public.equip_look(p_badge text, p_color text, p_font text, p_frame text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); want text[] := array[nullif(p_badge, ''), nullif(p_color, ''), nullif(p_font, ''), nullif(p_frame, '')];
  kinds text[] := array['badge', 'color', 'font', 'frame']; p public.profiles;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  for i in 1..4 loop
    if want[i] is not null then
      if split_part(want[i], '_', 1) <> kinds[i] then raise exception 'bad_request' using errcode = 'P0001'; end if;
      if not exists (select 1 from public.user_cosmetics u where u.user_id = me and u.item_id = want[i]) then
        raise exception 'not_owned' using errcode = 'P0001';
      end if;
    end if;
  end loop;
  -- The right-hand sides read the row as it was: only a colour that changes takes the effect off.
  update public.profiles set look_badge = want[1], look_color = want[2], look_font = want[3], look_frame = want[4],
         look_effect = case when want[2] is not null and want[2] is distinct from look_color then null else look_effect end
   where id = me returning * into p;
  return public.look_json(p);
end $$;

create or replace function public.set_worn(p_kind text, p_item text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); it text := nullif(p_item, '');
begin
  if p_kind is null or p_kind not in ('effect', 'bubble') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if it is not null and (split_part(it, '_', 1) <> p_kind or not public.owns(me, it)) then raise exception 'not_owned' using errcode = 'P0001'; end if;
  if p_kind = 'effect' then update public.profiles set look_effect = it, look_color = case when it is null then look_color else null end where id = me;
  else update public.profiles set look_bubble = it where id = me; end if;
end $$;
