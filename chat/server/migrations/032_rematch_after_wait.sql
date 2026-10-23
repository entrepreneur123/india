-- The last partner is skipped only for the first 10 seconds of both searches: with nobody else around (few users,
-- or one person testing with two phones) the two can meet again instead of waiting forever.
do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.find_match2(text, text)'::regprocedure);
  a := 'and q.user_id is distinct from last_partner';
  if position(a in d) = 0 then raise exception 'find_match2: anchor not found'; end if;
  execute replace(d, a, 'and (q.user_id is distinct from last_partner
          or (my_since < now() - interval ''10 seconds'' and q.joined_at < now() - interval ''10 seconds''))');
end $$;
