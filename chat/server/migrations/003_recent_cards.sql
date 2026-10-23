-- Recent tab in one request (instead of one profile_card call per person).
create function public.recent_cards()
returns table (match_id uuid, met_at timestamptz, last_body text,
               id uuid, name text, gender text, age int, city text, bio text, photo_path text)
language sql stable security definer set search_path = '' as $$
  select r.match_id, r.met_at, r.last_body,
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path
    from public.my_recents() r join public.profiles p on p.id = r.partner
   order by r.met_at desc
   limit 50
$$;
revoke execute on function public.recent_cards() from public, anon;
grant execute on function public.recent_cards() to authenticated;
