-- 072's hashtags() lost the order (distinct before the limit, so a room's first tag could fall off its 5) and cut a
-- word longer than 30 into a tag. Now: first-appearance order, and a #word longer than 30 is no tag at all.
-- Posts written before keep the tags 072 gave them until edited (the same tags, at most a long word's first 30).
create or replace function public.hashtags(p text) returns text[]
language sql immutable parallel safe set search_path = '' as $$
  select coalesce(array(
    select z.t from (
      select lower(x.m[1]) as t, min(x.n) as n
        from regexp_matches(coalesce(p, ''), '#((?:[^[:space:][:punct:]]|_){1,30})(?![^[:space:][:punct:]]|_)', 'g') with ordinality x(m, n)
       group by 1) z
     order by z.n limit 10), '{}')
$$;
