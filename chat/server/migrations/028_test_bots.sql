-- Test bots: 500 accounts that keep rooms and random chat busy while the app is tried out before launch.
--
-- They live only in the database. A pg_cron job (every 10 seconds) drives them:
--   - random chat: someone still waiting after 12 seconds gets a bot (people are always matched with each other
--     first); the bot greets, reads (blue ticks), answers in the person's language, sometimes asks for friendship,
--     and leaves after a long chat or 3 quiet minutes. Friend requests to a bot are accepted after a few seconds,
--     video requests are declined, room invites are taken up.
--   - rooms: 24 rooms hosted by bots (talk, music, games; 1 to 3 hours each, then new ones), with members coming
--     and going; a few bots also visit people's public rooms (they raise a hand now and then), chat, welcome
--     newcomers and answer them.
--   - bot-to-bot random chats in the background (load), and presence, so "N people online" counts them.
-- Bots never speak on stage (no audio, no LiveKit cost), never send photos, and have "Test bot" as their bio.
--
-- Off: update public.bot_config set enabled = false. Gone for good: ../owner_run_once_5.sql.
--
-- Also: making rooms was limited to 10 a day, which testing hits at once (and reads as "sending too fast");
-- it is now 5 in 10 minutes and 30 a day, with no limit for admins.

-- 1. Room creation limit
create or replace function public.create_voice_room2(p_title text, p_category text, p_max integer, p_gender text,
  p_min_age integer, p_max_age integer, p_private boolean, p_announcement text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); a text := nullif(btrim(coalesce(p_announcement, '')), '');
 rid text; c text;
begin
 if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
 if p_max is null or p_max not between 2 and 200 then raise exception 'room_size' using errcode = 'P0001'; end if;
 if coalesce(p_category, '') not in ('talk', 'music', 'games') or coalesce(p_gender, 'any') not in ('any', 'female', 'male')
 or coalesce(p_min_age, 18) not between 18 and 100 or coalesce(p_max_age, 100) not between 18 and 100
 or coalesce(p_min_age, 18) > coalesce(p_max_age, 100) or char_length(a) > 200 then
 raise exception 'bad_request' using errcode = 'P0001';
 end if;
 if not public.is_admin() and (
      (select count(*) from public.voice_rooms x where x.owner = me and x.created_at > now() - interval '10 minutes') >= 5
   or (select count(*) from public.voice_rooms x where x.owner = me and x.created_at > now() - interval '1 day') >= 30) then
 raise exception 'slow_down' using errcode = 'P0001';
 end if;
 update public.voice_rooms x set ended_at = now(), rev = rev + 1 where x.host = me and x.ended_at is null;
 rid := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
 c := public.voice_new_code();
 insert into public.voice_rooms (id, owner, host, title, category, max_members, gender_rule, min_age, max_age, is_private, announcement)
 values (rid, me, me, t, p_category, p_max, coalesce(p_gender, 'any'), coalesce(p_min_age, 18), coalesce(p_max_age, 100),
 coalesce(p_private, false), a);
 insert into public.voice_room_codes (room_id, code) values (rid, c);
 update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
 role = case when x.role = 'speaker' then 'listener' else x.role end
 where x.user_id = me and x.left_at is null and x.role <> 'host';
 insert into public.voice_members (room_id, user_id, role, seat) values (rid, me, 'host', null);
 return jsonb_build_object('id', rid, 'code', c);
end $$;

-- 2. Tables (no client access: RLS on, no policies, no grants)
create table if not exists public.bots (
  id uuid primary key,
  lang text not null check (lang in ('en', 'hi', 'bn', 'pt-br', 'vi'))
);
create table if not exists public.bot_lines (lang text not null, kind text not null, body text not null);
create index if not exists bot_lines_kind on public.bot_lines (lang, kind);
create table if not exists public.bot_titles (lang text not null, category text not null, title text not null);
create table if not exists public.bot_config (
  id int primary key check (id = 1), enabled boolean not null default true, tick bigint not null default 0
);
-- What a bot already reacted to (a room line, a newcomer, a chat it offered friendship in).
create table if not exists public.bot_done (key text primary key, at timestamptz not null default now());
alter table public.bots enable row level security;
alter table public.bot_lines enable row level security;
alter table public.bot_titles enable row level security;
alter table public.bot_config enable row level security;
alter table public.bot_done enable row level security;
revoke all on public.bots, public.bot_lines, public.bot_titles, public.bot_config, public.bot_done from public, anon, authenticated;
insert into public.bot_config (id) values (1) on conflict do nothing;

-- Found by the bots every few seconds (and useful to the app too: open chats, recent room lines).
create index if not exists messages_created on public.messages (created_at);
create index if not exists voice_messages_created on public.voice_messages (created_at);
create index if not exists matches_open_a on public.matches (user_a) where ended_at is null;
create index if not exists matches_open_b on public.matches (user_b) where ended_at is null;

-- 3. What they say ({name}, {city}, {age}: the bot's; {to}: the person addressed)
insert into public.bot_lines (lang, kind, body)
select v.lang, v.kind, v.body from (values
 ('en','greet','Hi! How''s your day going?'), ('en','greet','Hey there :)'), ('en','greet','Hello! Where are you chatting from?'),
 ('en','greet','Hii'), ('en','greet','Hey, nice to meet you'), ('en','greet','Hi, what''s up?'),
 ('en','react','Haha true'), ('en','react','Oh nice!'), ('en','react','Same here honestly'), ('en','react','That''s interesting, tell me more'),
 ('en','react','Hmm I get it'), ('en','react','Wow really?'), ('en','react','lol'), ('en','react','Makes sense'),
 ('en','react','Nice yaar'), ('en','react','Ahh okay okay'), ('en','react','I was thinking the same'), ('en','react','That sounds fun'),
 ('en','ask','What do you do?'), ('en','ask','Student or working?'), ('en','ask','Which movie did you watch last?'),
 ('en','ask','Do you like cricket?'), ('en','ask','What kind of music do you listen to?'), ('en','ask','Any weekend plans?'),
 ('en','ask','Tea or coffee?'), ('en','ask','Have you tried the rooms here?'), ('en','ask','What''s your favourite food?'),
 ('en','ask','Where would you travel if you could?'),
 ('en','where','I''m from {city}. You?'), ('en','where','{city} here, what about you?'),
 ('en','name','I''m {name} :)'), ('en','name','{name}. And you?'),
 ('en','age','{age}. You?'), ('en','age','I''m {age}'),
 ('en','bye','Gotta go, nice talking!'), ('en','bye','Okay I''ll leave now, take care'), ('en','bye','Bye bye, good chat'),
 ('en','room_talk','Good evening everyone'), ('en','room_talk','Anyone from {city}?'), ('en','room_talk','What''s everyone up to today?'),
 ('en','room_talk','This room is so chill'), ('en','room_talk','Who''s watching the match tonight?'), ('en','room_talk','Chai or coffee, vote now'),
 ('en','room_talk','Hello all'), ('en','room_talk','Monday blues anyone?'), ('en','room_talk','Any book suggestions?'),
 ('en','room_talk','Haha this chat is fun'), ('en','room_talk','Weekend plans everyone?'), ('en','room_talk','Just finished work, finally'),
 ('en','room_music','Play some Arijit please'), ('en','room_music','Next song should be AP Dhillon'), ('en','room_music','This one is a vibe'),
 ('en','room_music','Anyone into lo-fi?'), ('en','room_music','Add a Diljit song!'), ('en','room_music','Old Bollywood hits are the best'),
 ('en','room_music','Volume up, this is my song'),
 ('en','room_games','Ludo anyone?'), ('en','room_games','I''ll join the next game'), ('en','room_games','Who won the last round?'),
 ('en','room_games','Lucky dice today haha'), ('en','room_games','One more game?'), ('en','room_games','That was close!'),
 ('en','room_reply','Hi {to}!'), ('en','room_reply','{to} welcome yaar'), ('en','room_reply','Haha {to} true'),
 ('en','room_reply','Agree with {to}'), ('en','room_reply','{to} where are you from?'), ('en','room_reply','{to} nice one'),
 ('en','welcome','Welcome {to}!'), ('en','welcome','Hey {to}, join the chat'), ('en','welcome','Hi {to} :)'),

 ('hi','greet','Hi! Kaise ho?'), ('hi','greet','Hello ji'), ('hi','greet','Namaste! Kya chal raha hai?'),
 ('hi','greet','Hey, kaisa din tha?'), ('hi','greet','Hii, baat karte hain?'),
 ('hi','react','Haha sahi hai'), ('hi','react','Accha accha'), ('hi','react','Sach me?'), ('hi','react','Mast yaar'),
 ('hi','react','Same yaar'), ('hi','react','Hmm samjha'), ('hi','react','Wah'), ('hi','react','Badhiya'), ('hi','react','lol'),
 ('hi','react','Ekdum sahi'),
 ('hi','ask','Aap kya karte ho?'), ('hi','ask','Student ho ya job?'), ('hi','ask','Kaunsi movie dekhi recently?'),
 ('hi','ask','Cricket pasand hai?'), ('hi','ask','Kaunse gaane sunte ho?'), ('hi','ask','Weekend ka kya plan hai?'),
 ('hi','ask','Chai ya coffee?'), ('hi','ask','Favourite khana kya hai?'),
 ('hi','where','Main {city} se hoon. Aap?'), ('hi','where','{city} se, aur aap?'),
 ('hi','name','Mera naam {name} hai'), ('hi','name','{name}. Aapka?'),
 ('hi','age','{age} saal. Aap?'), ('hi','age','{age}'),
 ('hi','bye','Chalo bye, accha laga baat karke'), ('hi','bye','Mujhe jaana hai, take care'), ('hi','bye','Bye bye'),
 ('hi','room_talk','Sabko namaste'), ('hi','room_talk','Koi {city} se hai?'), ('hi','room_talk','Aaj sab kya kar rahe ho?'),
 ('hi','room_talk','Room mast hai'), ('hi','room_talk','Aaj match kaun dekh raha hai?'), ('hi','room_talk','Chai lovers yahan hain?'),
 ('hi','room_talk','Sab chup kyun ho?'),
 ('hi','room_music','Arijit ka gaana lagao'), ('hi','room_music','Next song Kishore Kumar ka'), ('hi','room_music','Ye gaana mast hai'),
 ('hi','room_music','Koi Punjabi song lagao'), ('hi','room_music','Purane gaane best hain'),
 ('hi','room_games','Ludo khelega koi?'), ('hi','room_games','Agla game main bhi'), ('hi','room_games','Last round kaun jeeta?'),
 ('hi','room_games','Ek aur game?'), ('hi','room_games','Kya game tha!'),
 ('hi','room_reply','Hi {to}!'), ('hi','room_reply','{to} welcome yaar'), ('hi','room_reply','Haha {to} sahi bola'),
 ('hi','room_reply','{to} kahan se ho?'),
 ('hi','welcome','Welcome {to}!'), ('hi','welcome','Aao {to}, baat karo'), ('hi','welcome','Hi {to}'),

 ('bn','greet','হ্যালো! কেমন আছো?'), ('bn','greet','হাই, কী খবর?'),
 ('bn','react','হাহা ঠিক'), ('bn','react','দারুণ!'), ('bn','react','সত্যি?'), ('bn','react','আচ্ছা'), ('bn','react','আমিও তাই ভাবি'),
 ('bn','ask','তুমি কী করো?'), ('bn','ask','প্রিয় খাবার কী?'), ('bn','ask','কোন গান শোনো?'),
 ('bn','where','আমি {city} থেকে। তুমি?'), ('bn','name','আমার নাম {name}'), ('bn','age','{age}। তুমি?'),
 ('bn','bye','যাই, ভালো লাগলো কথা বলে'),
 ('bn','room_talk','সবাইকে নমস্কার'), ('bn','room_talk','আজ সবাই কী করছো?'), ('bn','room_talk','কেউ {city} থেকে?'),
 ('bn','room_music','রবীন্দ্রসংগীত চালাও'), ('bn','room_music','এই গানটা দারুণ'),
 ('bn','room_games','লুডো খেলবে কেউ?'), ('bn','room_games','আরেকটা গেম?'),
 ('bn','room_reply','হাই {to}!'), ('bn','room_reply','{to} স্বাগতম'), ('bn','welcome','স্বাগতম {to}!'),

 ('pt-br','greet','Oi! Tudo bem?'), ('pt-br','greet','Olá, como vai?'),
 ('pt-br','react','Haha verdade'), ('pt-br','react','Que legal!'), ('pt-br','react','Sério?'), ('pt-br','react','Entendi'),
 ('pt-br','react','Também acho'),
 ('pt-br','ask','O que você faz?'), ('pt-br','ask','Que música você curte?'), ('pt-br','ask','Comida favorita?'),
 ('pt-br','where','Sou de {city}. E você?'), ('pt-br','name','Meu nome é {name}'), ('pt-br','age','{age}. E você?'),
 ('pt-br','bye','Tenho que ir, foi bom conversar'),
 ('pt-br','room_talk','Boa noite pessoal'), ('pt-br','room_talk','O que vocês estão fazendo?'), ('pt-br','room_talk','Alguém de {city}?'),
 ('pt-br','room_music','Coloca um funk aí'), ('pt-br','room_music','Essa música é boa demais'),
 ('pt-br','room_games','Bora jogar ludo?'), ('pt-br','room_games','Mais uma partida?'),
 ('pt-br','room_reply','Oi {to}!'), ('pt-br','room_reply','{to} bem-vindo'), ('pt-br','welcome','Bem-vindo {to}!'),

 ('vi','greet','Chào bạn! Khỏe không?'), ('vi','greet','Xin chào'),
 ('vi','react','Haha đúng rồi'), ('vi','react','Hay quá!'), ('vi','react','Thật hả?'), ('vi','react','Ừ hiểu rồi'),
 ('vi','ask','Bạn làm nghề gì?'), ('vi','ask','Bạn thích nghe nhạc gì?'), ('vi','ask','Món ăn yêu thích?'),
 ('vi','where','Mình ở {city}. Còn bạn?'), ('vi','name','Mình tên {name}'), ('vi','age','{age}. Còn bạn?'),
 ('vi','bye','Mình đi đây, vui khi nói chuyện'),
 ('vi','room_talk','Chào mọi người'), ('vi','room_talk','Mọi người đang làm gì?'), ('vi','room_talk','Có ai ở {city} không?'),
 ('vi','room_music','Mở bài Sơn Tùng đi'), ('vi','room_music','Bài này hay quá'),
 ('vi','room_games','Chơi cờ cá ngựa không?'), ('vi','room_games','Thêm một ván nữa?'),
 ('vi','room_reply','Chào {to}!'), ('vi','room_reply','{to} chào mừng'), ('vi','welcome','Chào mừng {to}!')
) v(lang, kind, body)
where not exists (select 1 from public.bot_lines);

insert into public.bot_titles (lang, category, title)
select v.lang, v.category, v.title from (values
 ('en','talk','Late night chill'), ('en','talk','Chai & chatter'), ('en','talk','Make new friends'), ('en','talk','College gossip'),
 ('en','talk','Mumbai meetup'), ('en','talk','Cricket talk'), ('en','talk','Bored? Come chat'), ('en','talk','Startups & jobs'),
 ('en','music','Bollywood hits'), ('en','music','Lo-fi & study'), ('en','music','Punjabi beats'), ('en','music','Retro 90s songs'),
 ('en','games','Ludo night'), ('en','games','Ludo masters'), ('en','games','Quick ludo'),
 ('hi','talk','Dil ki baatein'), ('hi','talk','Desi gossip'), ('hi','talk','Dosti karein?'), ('hi','talk','Raat ki baatein'),
 ('hi','music','Purane gaane'), ('hi','music','Arijit fans'), ('hi','games','Ludo khelo'),
 ('bn','talk','আড্ডা ঘর'), ('bn','talk','কলকাতার আড্ডা'), ('bn','music','বাংলা গান'), ('bn','games','লুডো খেলা'),
 ('pt-br','talk','Papo da noite'), ('pt-br','talk','Fazer amigos'), ('pt-br','music','Só as melhores'), ('pt-br','games','Ludo rápido'),
 ('vi','talk','Tâm sự đêm khuya'), ('vi','talk','Kết bạn bốn phương'), ('vi','music','Nhạc chill'), ('vi','games','Cờ cá ngựa')
) v(lang, category, title)
where not exists (select 1 from public.bot_titles);

-- 4. The 500 accounts: 440 in India (en, hi, bn), 20 each in Bangladesh, Brazil and Vietnam. Their phone numbers
-- are ones no real phone can have (India 9110..., Bangladesh 88010..., Brazil area code 00, Vietnam 8410...).
do $$
declare
  i int; uid uuid; ph text; lang text; g text; nm text; city text; cc text;
  in_city text[] := array['Mumbai','Delhi','Bengaluru','Hyderabad','Pune','Chennai','Jaipur','Lucknow','Ahmedabad','Indore',
                          'Chandigarh','Kochi','Patna','Bhopal','Nagpur','Surat','Guwahati','Noida'];
  m_in text[] := array['Aarav','Rohan','Arjun','Vikram','Rahul','Karan','Aditya','Siddharth','Ishaan','Kabir','Manish','Nikhil',
                       'Pranav','Rajat','Sahil','Tanmay','Varun','Yash','Dev','Harsh','Amit','Ankit','Deepak','Gaurav','Imran',
                       'Faizan','Sameer','Abhishek','Kunal','Mohit'];
  f_in text[] := array['Priya','Ananya','Diya','Isha','Kavya','Meera','Neha','Pooja','Riya','Sneha','Tanvi','Aisha','Simran',
                       'Shreya','Nandini','Aditi','Khushi','Muskan','Sana','Zoya','Divya','Anjali','Kritika','Payal','Radhika',
                       'Sakshi','Tanya','Vaishnavi','Pallavi','Nisha'];
  m_bn text[] := array['Arnab','Sourav','Rupam','Tanmoy','Ayan','Rafi','Tanvir','Sabbir'];
  f_bn text[] := array['Moumita','Debolina','Sreya','Puja','Nusrat','Farzana','Tania','Rupa'];
  m_br text[] := array['Lucas','Gabriel','Mateus','Pedro','Rafael','Thiago'];
  f_br text[] := array['Ana','Beatriz','Larissa','Camila','Julia','Mariana'];
  m_vn text[] := array['Minh','Huy','Khoa','Tuan','Long','Duc'];
  f_vn text[] := array['Linh','Trang','Mai','Ngoc','Thao','Vy'];
  tags text[] := array['music','movies','cricket','football','gaming','travel','food','fitness','books','tech','art','dance',
                       'fashion','photography','anime','pets','study','business','spirituality','comedy'];
begin
  if exists (select 1 from public.bots) then return; end if;
  for i in 1 .. 500 loop
    g := case when i % 37 = 0 then 'other' when i % 2 = 0 then 'female' else 'male' end;
    if i <= 440 then
      cc := 'IN'; ph := '9110' || lpad(i::text, 8, '0');
      lang := case when i % 20 < 11 then 'en' when i % 20 < 18 then 'hi' else 'bn' end;
      city := case when lang = 'bn' then 'Kolkata' else in_city[1 + i % 18] end;
      nm := case when lang = 'bn' then (case when g = 'female' then f_bn else m_bn end)[1 + (i * 7) % 8]
                 else (case when g = 'female' then f_in else m_in end)[1 + (i * 7) % 30] end;
    elsif i <= 460 then
      ph := '88010' || lpad(i::text, 8, '0'); lang := 'bn';
      city := (array['Dhaka','Chattogram','Khulna','Sylhet'])[1 + i % 4];
      nm := (case when g = 'female' then f_bn else m_bn end)[1 + (i * 7) % 8];
    elsif i <= 480 then
      ph := '5500' || lpad(i::text, 9, '0'); lang := 'pt-br';
      city := (array['São Paulo','Rio','Belo Horizonte','Salvador'])[1 + i % 4];
      nm := (case when g = 'female' then f_br else m_br end)[1 + (i * 7) % 6];
    else
      ph := '8410' || lpad(i::text, 8, '0'); lang := 'vi';
      city := (array['Hà Nội','TP.HCM','Đà Nẵng','Hải Phòng'])[1 + i % 4];
      nm := (case when g = 'female' then f_vn else m_vn end)[1 + (i * 7) % 6];
    end if;
    continue when exists (select 1 from auth.users u where u.phone = ph);
    uid := gen_random_uuid();
    insert into auth.users (instance_id, id, aud, role, phone, phone_confirmed_at, created_at, updated_at,
                            raw_app_meta_data, raw_user_meta_data, is_sso_user, is_anonymous,
                            confirmation_token, recovery_token, email_change_token_new, email_change,
                            phone_change, phone_change_token, email_change_token_current, reauthentication_token)
    values ('00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated', ph, now(), now(), now(),
            '{"provider":"phone","providers":["phone"],"test_bot":true}', '{}', false, false,
            '', '', '', '', '', '', '', '');
    insert into public.profiles (id, name, gender, birth_date, city, bio, interests)
    values (uid, nm, g, (current_date - ((19 + (i * 7) % 15) * 365 + (i * 13) % 300))::date, city, 'Test bot',
            array[tags[1 + i % 20], tags[1 + (i + 7) % 20], tags[1 + (i + 13) % 20]]);
    insert into public.bots (id, lang) values (uid, lang);
  end loop;
end $$;

-- 5. Helpers
create or replace function public.is_bot(p uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.bots y where y.id = p)
$$;

-- Not chatting and not in a room.
create or replace function public.bot_free(p uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select not exists (select 1 from public.matches x where x.user_a = p and x.ended_at is null)
     and not exists (select 1 from public.matches x where x.user_b = p and x.ended_at is null)
     and not exists (select 1 from public.voice_members m where m.user_id = p and m.left_at is null and not m.kicked)
$$;

create or replace function public.bot_say(p_bot uuid, p_lang text, p_kind text, p_to text default null) returns text
language sql volatile security definer set search_path = '' as $$
  select replace(replace(replace(replace(l.body, '{name}', p.name), '{city}', coalesce(p.city, 'India')),
                 '{age}', extract(year from age(p.birth_date))::int::text), '{to}', coalesce(p_to, ''))
    from public.profiles p,
         lateral (select coalesce(
           (select x.body from public.bot_lines x where x.lang = p_lang and x.kind = p_kind order by random() limit 1),
           (select x.body from public.bot_lines x where x.lang = 'en' and x.kind = p_kind order by random() limit 1)) as body) l
   where p.id = p_bot
$$;

create or replace function public.bot_chat_say(p_match uuid, p_bot uuid, p_body text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if p_body is null then return; end if;
  insert into public.messages (match_id, sender, body) values (p_match, p_bot, p_body);
exception when others then null; -- too fast, or banned by a test: skip this line
end $$;

create or replace function public.bot_room_say(p_room text, p_bot uuid, p_body text) returns void
language sql security definer set search_path = '' as $$
  insert into public.voice_messages (room_id, sender, kind, body, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  select p_room, p.id, 'text', p_body, p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country
    from public.profiles p where p.id = p_bot and p_body is not null
     and p_body is distinct from (select v.body from public.voice_messages v where v.room_id = p_room and v.kind = 'text'
                                   order by v.id desc limit 1)
$$;

-- A bot member of the room (fresh), at random.
create or replace function public.bot_in_room(p_room text) returns uuid
language sql volatile security definer set search_path = '' as $$
  select m.user_id from public.voice_members m join public.bots y on y.id = m.user_id
   where m.room_id = p_room and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds'
   order by random() limit 1
$$;

create or replace function public.bot_join(p_room text, p_bot uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms; m public.voice_members;
begin
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null;
  if not found then return false; end if;
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = p_bot;
  if found and (m.kicked or m.left_at is null) then return false; end if;
  if public.voice_refusal(r, p_bot, false) is not null then return false; end if;
  insert into public.voice_members (room_id, user_id, role, seat) values (p_room, p_bot, 'listener', null)
  on conflict (room_id, user_id) do update set seen_at = now(), left_at = null, role = 'listener', seat = null, hand_at = null;
  if random() < 0.3 and (m.joined_line_at is null or m.joined_line_at < now() - interval '10 minutes') then
    update public.voice_members set joined_line_at = now() where room_id = p_room and user_id = p_bot;
    perform public.voice_line(p_room, p_bot, 'joined', '{}'::jsonb);
  end if;
  perform public.voice_touch(p_room);
  return true;
end $$;

-- 6. Every 10 seconds: random chat with people, friend requests, room chat.
create or replace function public.bot_match_people() returns void
language plpgsql security definer set search_path = '' as $$
declare q public.queue; b uuid; last_partner uuid;
begin
  for q in select x.* from public.queue x
            where x.seen_at > now() - interval '15 seconds' and x.joined_at < now() - interval '12 seconds'
              and not exists (select 1 from public.bots y where y.id = x.user_id)
            order by x.joined_at limit 20
  loop
    -- The same lock find_match2 takes, so a person is never matched twice.
    continue when not pg_try_advisory_xact_lock(hashtext('match:' || q.user_id::text));
    perform 1 from public.queue x where x.user_id = q.user_id and x.seen_at > now() - interval '15 seconds' for update skip locked;
    continue when not found;
    continue when exists (select 1 from public.matches x where x.user_a = q.user_id and x.ended_at is null)
               or exists (select 1 from public.matches x where x.user_b = q.user_id and x.ended_at is null);
    select case when x.user_a = q.user_id then x.user_b else x.user_a end into last_partner
      from public.matches x where q.user_id in (x.user_a, x.user_b) order by x.created_at desc limit 1;
    select y.id into b from public.bots y join public.profiles p on p.id = y.id
     where (q.target = 'any' or p.gender = q.target)
       and y.id is distinct from last_partner
       and not public.is_blocked(y.id, q.user_id)
       and public.bot_free(y.id)
     order by (y.lang = q.lang) desc, random() limit 1;
    continue when b is null;
    update public.queue set seen_at = '-infinity' where user_id = q.user_id;
    -- The person is user_a: find_match2 looks for that when they poll.
    insert into public.matches (user_a, user_b) values (q.user_id, b);
  end loop;
end $$;

create or replace function public.bot_chat() returns void
language plpgsql security definer set search_path = '' as $$
declare r record; mt public.matches; b uuid; h uuid; lg text; k text; body text; n int; last_at timestamptz;
begin
  -- Answer the last line of each chat a person wrote to a bot in the last 3 minutes.
  for r in select distinct on (m.match_id) m.match_id, m.id, m.sender, m.body, m.created_at
             from public.messages m where m.created_at > now() - interval '3 minutes'
            order by m.match_id, m.id desc
  loop
    continue when r.sender is null or public.is_bot(r.sender);
    select * into mt from public.matches x where x.id = r.match_id;
    b := case when public.is_bot(mt.user_a) then mt.user_a when public.is_bot(mt.user_b) then mt.user_b end;
    continue when b is null;
    -- Read at once (blue ticks), answer 2 to 5 seconds later.
    update public.matches x set read_a = case when x.user_a = b then r.id else x.read_a end,
                                read_b = case when x.user_b = b then r.id else x.read_b end
     where x.id = mt.id and coalesce(case when x.user_a = b then x.read_a else x.read_b end, 0) < r.id;
    continue when r.created_at > now() - make_interval(secs => 2 + r.id % 4);
    continue when (mt.ended_at is not null and not public.are_friends(mt.user_a, mt.user_b)) or public.is_blocked(mt.user_a, mt.user_b);
    select y.lang into lg from public.bots y where y.id = b;
    k := case
      when coalesce(r.body, '') ~* '(where|kahan|kaha |kidhar|onde|đâu|কোথায়)' then 'where'
      when coalesce(r.body, '') ~* '(name|naam|nome|tên|নাম)' then 'name'
      when coalesce(r.body, '') ~* '(\mage\M|how old|umar|umra|idade|tuổi|বয়স)' then 'age'
      when coalesce(r.body, '') ~* '^\s*(hi+|hello|hey+|namaste|hola|oi|olá|chào|xin chào|হাই|হ্যালো)\W*$' then 'greet'
      else 'react' end;
    body := public.bot_say(b, lg, k);
    if k in ('react', 'greet') and random() < 0.45 then body := body || ' ' || public.bot_say(b, lg, 'ask'); end if;
    perform public.bot_chat_say(mt.id, b, body);
  end loop;

  -- Open chats with people: greet, decline video, offer friendship, and leave in the end.
  for mt in select x.* from public.matches x
             where x.ended_at is null and public.is_bot(x.user_b) and not public.is_bot(x.user_a)
  loop
    b := mt.user_b; h := mt.user_a;
    select y.lang into lg from public.bots y where y.id = b;
    select max(m.created_at), count(*) filter (where m.sender = b) into last_at, n from public.messages m where m.match_id = mt.id;
    if last_at is null then
      if mt.created_at < now() - interval '3 seconds' then perform public.bot_chat_say(mt.id, b, public.bot_say(b, lg, 'greet')); end if;
      if mt.created_at < now() - interval '3 minutes' then
        update public.matches set ended_at = now(), ended_by = b, video_status = 'none' where id = mt.id and ended_at is null;
      end if;
      continue;
    end if;
    if mt.video_status = 'requested' and mt.video_requested_by = h then
      update public.matches set video_status = 'declined' where id = mt.id and video_status = 'requested';
    end if;
    if not public.are_friends(h, b)
       and (select count(*) from public.messages m where m.match_id = mt.id and m.sender = h) >= 3
       and not exists (select 1 from public.friendships f where (f.requester = h and f.addressee = b) or (f.requester = b and f.addressee = h)) then
      insert into public.bot_done (key) values ('f' || mt.id) on conflict do nothing;
      if found and random() < 0.5 then
        insert into public.friendships (requester, addressee) values (b, h) on conflict do nothing;
      end if;
    end if;
    if last_at < now() - interval '3 minutes'
       or (n >= 14 and not public.are_friends(h, b) and random() < 0.15) then
      if last_at >= now() - interval '3 minutes' then perform public.bot_chat_say(mt.id, b, public.bot_say(b, lg, 'bye')); end if;
      update public.matches set ended_at = now(), ended_by = b, video_status = 'none' where id = mt.id and ended_at is null;
    end if;
  end loop;
end $$;

create or replace function public.bot_social() returns void
language plpgsql security definer set search_path = '' as $$
declare r record;
begin
  -- Friend requests to a bot: yes, after a few seconds.
  update public.friendships f set status = 'accepted'
   where f.status = 'pending' and f.created_at < now() - interval '5 seconds' and public.is_bot(f.addressee);
  -- Room invites: go and see.
  for r in select n.id, n.user_id, n.room_id from public.notifications n join public.bots y on y.id = n.user_id
            where n.read_at is null and n.kind = 'room_invite' and n.room_id is not null
              and n.created_at < now() - interval '4 seconds'
  loop
    if public.bot_free(r.user_id) then perform public.bot_join(r.room_id, r.user_id); end if;
  end loop;
  update public.notifications n set read_at = now()
    from public.bots y where y.id = n.user_id and n.read_at is null and n.created_at < now() - interval '4 seconds';
end $$;

create or replace function public.bot_rooms_talk() returns void
language plpgsql security definer set search_path = '' as $$
declare r record; b uuid;
begin
  -- Answer people in rooms (most of the time).
  for r in select vm.id, vm.room_id, vm.sender_name, x.lang from public.voice_messages vm join public.voice_rooms x on x.id = vm.room_id
            where vm.created_at > now() - interval '30 seconds' and vm.created_at < now() - interval '3 seconds'
              and vm.kind = 'text' and x.ended_at is null and not public.is_bot(vm.sender)
  loop
    insert into public.bot_done (key) values ('r' || r.id) on conflict do nothing;
    continue when not found or random() > 0.7;
    b := public.bot_in_room(r.room_id);
    continue when b is null;
    perform public.bot_room_say(r.room_id, b, public.bot_say(b, coalesce(r.lang, 'en'), 'room_reply', r.sender_name));
  end loop;
  -- Welcome people who just came in.
  for r in select m.room_id, m.user_id, m.joined_line_at, p.name, x.lang from public.voice_members m
             join public.voice_rooms x on x.id = m.room_id join public.profiles p on p.id = m.user_id
            where m.joined_line_at > now() - interval '30 seconds' and m.joined_line_at < now() - interval '2 seconds'
              and m.left_at is null and x.ended_at is null and not public.is_bot(m.user_id)
  loop
    insert into public.bot_done (key) values ('w' || r.room_id || r.user_id || extract(epoch from r.joined_line_at)::bigint)
    on conflict do nothing;
    continue when not found or random() > 0.8;
    b := public.bot_in_room(r.room_id);
    continue when b is null;
    perform public.bot_room_say(r.room_id, b, public.bot_say(b, coalesce(r.lang, 'en'), 'welcome', r.name));
  end loop;
  -- Small talk: 8 rooms with bots in them, each says something about two times in three.
  for r in select x.id, coalesce(x.lang, 'en') as lang, x.category from public.voice_rooms x
            where x.ended_at is null and exists (select 1 from public.voice_members m join public.bots y on y.id = m.user_id
                                                  where m.room_id = x.id and m.left_at is null and not m.kicked)
            order by random() limit 8
  loop
    continue when random() < 0.35;
    b := public.bot_in_room(r.id);
    continue when b is null;
    perform public.bot_room_say(r.id, b, public.bot_say(b, r.lang,
      case when random() < 0.25 then 'react' else 'room_' || r.category end));
  end loop;
end $$;

-- 7. Every 30 seconds: presence, rooms, bot-to-bot chats.
create or replace function public.bot_slow() returns void
language plpgsql security definer set search_path = '' as $$
declare
  free uuid[]; fi int := 1; r record; b uuid; b2 uuid; n int; k int; target int; i int; lg text; cat text; rid text; t text;
begin
  -- Presence: bots in a room are counted there; the rest as online.
  insert into public.user_seen (user_id, seen_at)
  select y.id, now() from public.bots y
   where not exists (select 1 from public.voice_members m where m.user_id = y.id and m.left_at is null and not m.kicked)
  on conflict (user_id) do update set seen_at = excluded.seen_at;
  update public.voice_members m set seen_at = now()
    from public.bots y where y.id = m.user_id and m.left_at is null and not m.kicked;

  free := array(select y.id from public.bots y where public.bot_free(y.id) order by random());

  -- Bot rooms close after 1 to 3 hours (later while a person is in one).
  for r in select x.id from public.voice_rooms x join public.bots y on y.id = x.host
            where x.ended_at is null and x.created_at < now() - make_interval(mins => 60 + (hashtext(x.id) & 2147483647) % 120)
              and not exists (select 1 from public.voice_members m where m.room_id = x.id and m.left_at is null and not m.kicked
                                and m.seen_at > now() - interval '90 seconds' and not public.is_bot(m.user_id))
  loop
    update public.voice_rooms set ended_at = now(), rev = rev + 1 where id = r.id;
    update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true
     where m.room_id = r.id and m.left_at is null and public.is_bot(m.user_id);
  end loop;

  -- Keep 24 bot rooms open (8 new at most per run).
  select count(*) into n from public.voice_rooms x join public.bots y on y.id = x.host where x.ended_at is null;
  for i in 1 .. least(greatest(24 - n, 0), 8) loop
    exit when fi > coalesce(array_length(free, 1), 0);
    b := free[fi]; fi := fi + 1;
    select y.lang into lg from public.bots y where y.id = b;
    cat := (array['talk', 'talk', 'talk', 'music', 'music', 'games'])[1 + floor(random() * 6)::int];
    select coalesce((select x.title from public.bot_titles x where x.lang = lg and x.category = cat order by random() limit 1),
                    (select x.title from public.bot_titles x where x.lang = 'en' and x.category = cat order by random() limit 1))
      into t;
    rid := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
    insert into public.voice_rooms (id, owner, host, title, category, max_members, gender_rule, min_age, max_age, is_private, lang)
    values (rid, b, b, t, cat, (array[30, 50, 100])[1 + floor(random() * 3)::int], 'any', 18, 100, false, lg);
    insert into public.voice_room_codes (room_id, code) values (rid, public.voice_new_code());
    insert into public.voice_members (room_id, user_id, role, seat) values (rid, b, 'host', null);
  end loop;

  -- Members: bot rooms fill to 3-20, people's public rooms get 2-5 visitors; 4% of them wander off each run.
  for r in select x.id, x.host, x.speak_mode, public.is_bot(x.host) as bot_host from public.voice_rooms x
            where x.ended_at is null and not x.is_private
              and exists (select 1 from public.voice_members m where m.room_id = x.id and m.user_id = x.host
                            and m.left_at is null and m.seen_at > now() - interval '2 minutes')
  loop
    update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
           role = case when m.role = 'speaker' then 'listener' else m.role end
     where m.room_id = r.id and m.user_id <> r.host and m.role <> 'host' and m.left_at is null and not m.kicked
       and public.is_bot(m.user_id) and random() < 0.04;
    get diagnostics k = row_count;
    if k > 0 then perform public.voice_touch(r.id); end if;
    select count(*) into n from public.voice_members m join public.bots y on y.id = m.user_id
     where m.room_id = r.id and m.user_id <> r.host and m.left_at is null and not m.kicked;
    target := case when r.bot_host then 3 + (hashtext(r.id) & 2147483647) % 18 else 2 + (hashtext(r.id) & 2147483647) % 4 end;
    for i in 1 .. least(greatest(target - n, 0), 3) loop
      exit when fi > coalesce(array_length(free, 1), 0);
      b := free[fi]; fi := fi + 1;
      perform public.bot_join(r.id, b);
    end loop;
    -- In people's rooms a bot asks to speak now and then (the host can try approving).
    if not r.bot_host and r.speak_mode = 'approval' and random() < 0.1 then
      update public.voice_members m set hand_at = now()
       where (m.room_id, m.user_id) = (select z.room_id, z.user_id from public.voice_members z join public.bots y on y.id = z.user_id
                                        where z.room_id = r.id and z.left_at is null and not z.kicked and z.seat is null
                                          and z.hand_at is null limit 1);
    end if;
  end loop;

  -- Bot-to-bot random chats: about 15 at a time, a line every 30 seconds or so, 6-13 lines each.
  for r in select x.id, x.user_a, x.user_b, x.created_at from public.matches x
            where x.ended_at is null and public.is_bot(x.user_a) and public.is_bot(x.user_b)
  loop
    select count(*), (array_agg(m.sender order by m.id desc))[1] into n, b from public.messages m where m.match_id = r.id;
    if n >= 6 + (hashtext(r.id::text) & 2147483647) % 8 or r.created_at < now() - interval '8 minutes' then
      update public.matches set ended_at = now(), ended_by = r.user_a, video_status = 'none' where id = r.id;
      continue;
    end if;
    continue when random() < 0.3;
    b2 := case when b = r.user_a then r.user_b else r.user_a end;
    select y.lang into lg from public.bots y where y.id = b2;
    perform public.bot_chat_say(r.id, b2, public.bot_say(b2, lg,
      case when n = 0 then 'greet' else (array['react', 'ask', 'react', 'where', 'name'])[1 + floor(random() * 5)::int] end));
  end loop;
  select count(*) into n from public.matches x where x.ended_at is null and public.is_bot(x.user_a) and public.is_bot(x.user_b);
  for i in 1 .. least(greatest(15 - n, 0), 5) loop
    exit when fi + 1 > coalesce(array_length(free, 1), 0);
    b := free[fi]; b2 := free[fi + 1]; fi := fi + 2;
    insert into public.matches (user_a, user_b) values (b, b2);
  end loop;
end $$;

create or replace function public.bot_tick() returns void
language plpgsql security definer set search_path = '' as $$
declare t bigint;
begin
  -- One tick at a time; none while switched off.
  update public.bot_config set tick = tick + 1
   where id = 1 and enabled and id = (select c.id from public.bot_config c where c.id = 1 for update skip locked)
  returning tick into t;
  if t is null then return; end if;
  perform public.bot_match_people();
  perform public.bot_chat();
  perform public.bot_social();
  perform public.bot_rooms_talk();
  if t % 3 = 1 then perform public.bot_slow(); end if;
end $$;

revoke execute on function public.is_bot(uuid), public.bot_free(uuid), public.bot_say(uuid, text, text, text),
  public.bot_chat_say(uuid, uuid, text), public.bot_room_say(text, uuid, text), public.bot_in_room(text),
  public.bot_join(text, uuid), public.bot_match_people(), public.bot_chat(), public.bot_social(), public.bot_rooms_talk(),
  public.bot_slow(), public.bot_tick() from public, anon, authenticated;

select cron.schedule('yaari-bots', '10 seconds', $cron$set statement_timeout = '8s'; select public.bot_tick()$cron$);
