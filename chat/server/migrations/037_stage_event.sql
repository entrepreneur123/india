-- A shared page (036's voice_rooms.stage) reaches everyone in the room at once: the room-change broadcast ('r')
-- now carries `stage` and fires when it changes.
do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.voice_room_event()'::regprocedure);
  a := '''locked'', to_jsonb(new.locked_seats), ''music'', new.music,';
  if position(a in d) = 0 then raise exception 'voice_room_event: anchor not found'; end if;
  execute replace(d, a, a || ' ''stage'', new.stage,');
end $$;

create or replace trigger voice_rooms_event after update on public.voice_rooms for each row
  when ((old.title, old.category, old.speak_mode, old.host, old.locked_seats, old.music, old.queue, old.announcement,
         old.max_members, old.gender_rule, old.min_age, old.max_age, old.is_private, old.ended_at, old.stage)
        is distinct from
        (new.title, new.category, new.speak_mode, new.host, new.locked_seats, new.music, new.queue, new.announcement,
         new.max_members, new.gender_rule, new.min_age, new.max_age, new.is_private, new.ended_at, new.stage))
  execute function public.voice_room_event();
