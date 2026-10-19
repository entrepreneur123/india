-- Opening a new random chat took six requests in a row (the match, the partner's card, their posts, a link for
-- every post image, their photo). match_card returns the partner's card for a match I am in, in one request;
-- posts load only when the profile is opened. Same visibility rules as card().
create function public.match_card(p_match uuid)
returns table(id uuid, name text, gender text, age integer, city text, bio text, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select c.* from public.matches m
  cross join lateral public.card(case when m.user_a = auth.uid() then m.user_b else m.user_a end) c
  where m.id = p_match and auth.uid() in (m.user_a, m.user_b)
$$;
revoke execute on function public.match_card(uuid) from public, anon;
grant execute on function public.match_card(uuid) to authenticated;
