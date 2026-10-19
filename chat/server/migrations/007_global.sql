-- Global launch: sign-up from many countries, a country flag on every profile, international room topics.

-- Country comes from the verified phone number (not from the app), so nobody can fake their flag.
create table public.dial_codes (prefix text primary key, country text not null check (country ~ '^[A-Z]{2}$'));
alter table public.dial_codes enable row level security; -- internal
insert into public.dial_codes (prefix, country) values
  ('1', 'US'), ('20', 'EG'), ('27', 'ZA'), ('30', 'GR'), ('31', 'NL'), ('32', 'BE'), ('33', 'FR'), ('34', 'ES'),
  ('39', 'IT'), ('40', 'RO'), ('41', 'CH'), ('44', 'GB'), ('46', 'SE'), ('47', 'NO'), ('48', 'PL'), ('49', 'DE'),
  ('51', 'PE'), ('52', 'MX'), ('54', 'AR'), ('55', 'BR'), ('56', 'CL'), ('57', 'CO'), ('60', 'MY'), ('61', 'AU'),
  ('62', 'ID'), ('63', 'PH'), ('64', 'NZ'), ('65', 'SG'), ('66', 'TH'), ('81', 'JP'), ('82', 'KR'), ('84', 'VN'),
  ('90', 'TR'), ('91', 'IN'), ('92', 'PK'), ('94', 'LK'), ('212', 'MA'), ('233', 'GH'), ('234', 'NG'), ('254', 'KE'),
  ('351', 'PT'), ('353', 'IE'), ('380', 'UA'), ('852', 'HK'), ('880', 'BD'), ('886', 'TW'), ('965', 'KW'),
  ('966', 'SA'), ('971', 'AE'), ('974', 'QA'), ('977', 'NP');

alter table public.profiles add column country text check (country ~ '^[A-Z]{2}$');

create function public.set_country() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  select d.country into new.country from auth.users u join public.dial_codes d on u.phone like d.prefix || '%'
   where u.id = new.id order by length(d.prefix) desc limit 1;
  return new;
end $$;
create trigger profiles_country before insert on public.profiles for each row execute function public.set_country();
revoke execute on function public.set_country() from public, anon, authenticated;
update public.profiles p set country = (select d.country from auth.users u join public.dial_codes d on u.phone like d.prefix || '%'
                                         where u.id = p.id order by length(d.prefix) desc limit 1);

-- Cards with the country. New names: the old functions stay for app versions already installed.

create function public.card(uid uuid)
returns table (id uuid, name text, gender text, age int, city text, bio text, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path, p.country
  from public.profiles p where p.id = uid and public.can_view(uid)
$$;

create function public.recent_list()
returns table (match_id uuid, met_at timestamptz, last_body text,
               id uuid, name text, gender text, age int, city text, bio text, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select r.match_id, r.met_at, r.last_body,
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path, p.country
    from public.my_recents() r join public.profiles p on p.id = r.partner
   order by r.met_at desc
   limit 50
$$;

create function public.friend_list()
returns table (state text, match_id uuid, id uuid, name text, gender text, age int, city text, bio text, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select case when f.status = 'accepted' then 'friends' when f.requester = auth.uid() then 'requested' else 'incoming' end,
         (select x.id from public.matches x
           where (x.user_a = auth.uid() and x.user_b = p.id) or (x.user_b = auth.uid() and x.user_a = p.id)
           order by x.created_at desc limit 1),
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path, p.country
    from public.friendships f
    join public.profiles p on p.id = case when f.requester = auth.uid() then f.addressee else f.requester end
   where auth.uid() in (f.requester, f.addressee) and not public.is_blocked(f.requester, f.addressee)
   order by f.created_at desc
   limit 500
$$;

revoke execute on function public.card(uuid), public.recent_list(), public.friend_list() from public, anon;
grant execute on function public.card(uuid), public.recent_list(), public.friend_list() to authenticated;

-- Room messages carry the sender's country too.
alter table public.room_messages add column sender_country text;
create or replace function public.room_message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
declare p public.profiles;
begin
  select * into p from public.profiles where id = new.sender;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if p.banned_until > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if (select count(*) from public.room_messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  new.sender_name := p.name;
  new.sender_gender := p.gender;
  new.sender_age := extract(year from age(p.birth_date))::int;
  new.sender_photo := p.photo_path;
  new.sender_country := p.country;
  new.created_at := now();
  return new;
end $$;

-- International room topics (the rooms were still empty when this ran).
update public.rooms set id = 'lounge', title = 'Global Lounge', emoji = '🌍', sort = 1 where id = 'hindi';
update public.rooms set id = 'language', title = 'Language Exchange', emoji = '🗣️', sort = 2 where id = 'english';
update public.rooms set id = 'music', title = 'Music', emoji = '🎧', sort = 3 where id = 'cricket';
update public.rooms set id = 'movies', title = 'Movies & Series', emoji = '🎬', sort = 4 where id = 'bollywood';
update public.rooms set sort = 7 where id = 'night';
update public.rooms set id = 'study', title = 'Study & Campus', emoji = '🎓', sort = 9 where id = 'campus';
update public.rooms set sort = 5 where id = 'gaming';
update public.rooms set title = 'Memes', sort = 8 where id = 'memes';
insert into public.rooms (id, title, emoji, sort) values ('sports', 'Sports', '⚽', 6), ('travel', 'Travel', '✈️', 10);
