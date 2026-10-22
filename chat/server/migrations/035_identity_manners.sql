-- Real identities and a manners score: a random chat where people are who they say they are.
--
-- 1. One account per phone number (already: phone sign-in) and now one account per person: an identity document
--    can verify one account only. Verification is a hosted flow (Didit: ID document + liveness selfie + face
--    match, 220+ countries) started by the Edge Function kyc; its result is read back from Didit by that function
--    (never trusted from a webhook body) and applied here by kyc_apply:
--      - gender and date of birth are taken from the document and can't be changed after; the country becomes the
--        document's nationality (else issuing country); under 18 is banned; a document already used by another
--        account is refused; a document of someone banned carries the ban to the new account.
--      - stored: a salted hash of the document number (vault secret id_pepper), gender, date of birth, nationality,
--        issuing country. No names, no images (data minimisation).
-- 2. app_flags.require_verification (off until the owner has a Didit account): when on, unverified people can look
--    around but not match, join or make rooms, post, comment or ask for friendship ('verify_required').
-- 3. Manners: after a random chat (both wrote at least one line) each can rate the other once: good or bad, with
--    tags. manner (0-100, starts at 50) is a Bayesian score over the last 90 days where a bad rating weighs double.
--    Below 20, random chat is closed ('manner_low'). Only good tags are shown to others.
-- 4. trust(ids) and my_trust() give badges to the apps; admin_verify marks test accounts.

-- Countries (ISO 3166-1, from pycountry)
create table if not exists public.iso_countries (a3 text primary key, a2 text not null);
insert into public.iso_countries (a3, a2)
select left(x, 3), right(x, 2) from regexp_split_to_table('ABWAW AFGAF AGOAO AIAAI ALAAX ALBAL ANDAD AREAE ARGAR ARMAM ASMAS ATAAQ ATFTF ATGAG AUSAU AUTAT AZEAZ BDIBI BELBE BENBJ BESBQ BFABF BGDBD BGRBG BHRBH BHSBS BIHBA BLMBL BLRBY BLZBZ BMUBM BOLBO BRABR BRBBB BRNBN BTNBT BVTBV BWABW CAFCF CANCA CCKCC CHECH CHLCL CHNCN CIVCI CMRCM CODCD COGCG COKCK COLCO COMKM CPVCV CRICR CUBCU CUWCW CXRCX CYMKY CYPCY CZECZ DEUDE DJIDJ DMADM DNKDK DOMDO DZADZ ECUEC EGYEG ERIER ESHEH ESPES ESTEE ETHET FINFI FJIFJ FLKFK FRAFR FROFO FSMFM GABGA GBRGB GEOGE GGYGG GHAGH GIBGI GINGN GLPGP GMBGM GNBGW GNQGQ GRCGR GRDGD GRLGL GTMGT GUFGF GUMGU GUYGY HKGHK HMDHM HNDHN HRVHR HTIHT HUNHU IDNID IMNIM INDIN IOTIO IRLIE IRNIR IRQIQ ISLIS ISRIL ITAIT JAMJM JEYJE JORJO JPNJP KAZKZ KENKE KGZKG KHMKH KIRKI KNAKN KORKR KWTKW LAOLA LBNLB LBRLR LBYLY LCALC LIELI LKALK LSOLS LTULT LUXLU LVALV MACMO MAFMF MARMA MCOMC MDAMD MDGMG MDVMV MEXMX MHLMH MKDMK MLIML MLTMT MMRMM MNEME MNGMN MNPMP MOZMZ MRTMR MSRMS MTQMQ MUSMU MWIMW MYSMY MYTYT NAMNA NCLNC NERNE NFKNF NGANG NICNI NIUNU NLDNL NORNO NPLNP NRUNR NZLNZ OMNOM PAKPK PANPA PCNPN PERPE PHLPH PLWPW PNGPG POLPL PRIPR PRKKP PRTPT PRYPY PSEPS PYFPF QATQA REURE ROURO RUSRU RWARW SAUSA SDNSD SENSN SGPSG SGSGS SHNSH SJMSJ SLBSB SLESL SLVSV SMRSM SOMSO SPMPM SRBRS SSDSS STPST SURSR SVKSK SVNSI SWESE SWZSZ SXMSX SYCSC SYRSY TCATC TCDTD TGOTG THATH TJKTJ TKLTK TKMTM TLSTL TONTO TTOTT TUNTN TURTR TUVTV TWNTW TZATZ UGAUG UKRUA UMIUM URYUY USAUS UZBUZ VATVA VCTVC VENVE VGBVG VIRVI VNMVN VUTVU WLFWF WSMWS YEMYE ZAFZA ZMBZM ZWEZW', ' ') x
on conflict do nothing;
alter table public.iso_countries enable row level security;
revoke all on public.iso_countries from public, anon, authenticated;

alter table public.profiles add column if not exists id_verified_at timestamptz,
  add column if not exists manner smallint not null default 50 check (manner between 0 and 100);

create table if not exists public.identities (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  doc_hash text not null unique,
  provider text not null,
  session_id text not null,
  gender text not null,
  birth_date date not null,
  nationality text,
  issuing_state text,
  verified_at timestamptz not null default now()
);
create table if not exists public.banned_identities (doc_hash text primary key, banned_until timestamptz not null);
create table if not exists public.kyc_sessions (
  session_id text primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  status text not null default 'Not Started',
  result text,
  decided_at timestamptz
);
create index if not exists kyc_sessions_user on public.kyc_sessions (user_id, created_at desc);
create table if not exists public.app_flags (id int primary key check (id = 1), require_verification boolean not null default false);
insert into public.app_flags (id) values (1) on conflict do nothing;
alter table public.identities enable row level security;
alter table public.banned_identities enable row level security;
alter table public.kyc_sessions enable row level security;
alter table public.app_flags enable row level security;
revoke all on public.identities, public.banned_identities, public.kyc_sessions, public.app_flags from public, anon, authenticated;

select vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'id_pepper')
 where not exists (select 1 from vault.secrets where name = 'id_pepper');

-- A banned person who deletes the account keeps the ban on the document.
create or replace function public.keep_identity_ban() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if old.banned_until > now() then
    insert into public.banned_identities (doc_hash, banned_until)
    select i.doc_hash, old.banned_until from public.identities i where i.user_id = old.id
    on conflict (doc_hash) do update set banned_until = greatest(public.banned_identities.banned_until, excluded.banned_until);
  end if;
  return old;
end $$;
create trigger profiles_keep_identity_ban before delete on public.profiles for each row execute function public.keep_identity_ban();

-- 1. Verification
create or replace function public.kyc_session_new(p_user uuid, p_session text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.profiles p where p.id = p_user and p.id_verified_at is not null) then
    raise exception 'already_verified' using errcode = 'P0001';
  end if;
  if (select count(*) from public.kyc_sessions s where s.user_id = p_user and s.created_at > now() - interval '1 day') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.kyc_sessions (session_id, user_id) values (p_session, p_user);
end $$;

create or replace function public.kyc_latest(p_user uuid) returns text
language sql stable security definer set search_path = '' as $$
  select s.session_id from public.kyc_sessions s where s.user_id = p_user order by s.created_at desc limit 1
$$;

create or replace function public.kyc_owner(p_session text) returns uuid
language sql stable security definer set search_path = '' as $$
  select s.user_id from public.kyc_sessions s where s.session_id = p_session
$$;

-- p_doc: the approved id_verifications[] item. Returns verified | pending | declined | unreadable | minor | duplicate.
create or replace function public.kyc_apply(p_user uuid, p_session text, p_status text, p_doc jsonb) returns text
language plpgsql security definer set search_path = '' as $$
declare res text; dob date; g text; num text; st text; nat text; h text; c2 text; ban timestamptz;
begin
  if not exists (select 1 from public.kyc_sessions s where s.session_id = p_session and s.user_id = p_user) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.profiles p where p.id = p_user and p.id_verified_at is not null) then
    return 'verified';
  end if;
  if p_status <> 'Approved' then
    res := case when p_status in ('Declined', 'Abandoned', 'Expired') then 'declined' else 'pending' end;
  else
    dob := nullif(p_doc->>'date_of_birth', '')::date;
    num := upper(regexp_replace(coalesce(p_doc->>'document_number', p_doc->>'personal_number', ''), '[^A-Za-z0-9]', '', 'g'));
    st := upper(coalesce(nullif(p_doc->>'issuing_state', ''), nullif(p_doc->>'nationality', ''), ''));
    nat := upper(coalesce(nullif(p_doc->>'nationality', ''), nullif(p_doc->>'issuing_state', ''), ''));
    g := case upper(left(coalesce(p_doc->>'gender', ''), 1)) when 'M' then 'male' when 'F' then 'female' else 'other' end;
    if dob is null or num = '' then
      res := 'unreadable';
    else
      h := encode(extensions.digest((select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'id_pepper')
                                    || st || ':' || num, 'sha256'), 'hex');
      if exists (select 1 from public.identities i where i.doc_hash = h and i.user_id <> p_user) then
        res := 'duplicate';
      else
        insert into public.identities (user_id, doc_hash, provider, session_id, gender, birth_date, nationality, issuing_state)
        values (p_user, h, 'didit', p_session, g, dob, nullif(nat, ''), nullif(st, ''))
        on conflict (user_id) do update set doc_hash = excluded.doc_hash, session_id = excluded.session_id, gender = excluded.gender,
          birth_date = excluded.birth_date, nationality = excluded.nationality, issuing_state = excluded.issuing_state, verified_at = now();
        if dob > (current_date - interval '18 years')::date then
          update public.profiles set banned_until = 'infinity' where id = p_user;
          res := 'minor';
        else
          select c.a2 into c2 from public.iso_countries c where c.a3 = nat;
          if c2 is null then select c.a2 into c2 from public.iso_countries c where c.a3 = st; end if;
          select b.banned_until into ban from public.banned_identities b where b.doc_hash = h and b.banned_until > now();
          update public.profiles set gender = g, birth_date = dob, country = coalesce(c2, country), id_verified_at = now(),
                 banned_until = case when ban is not null then greatest(coalesce(banned_until, ban), ban) else banned_until end
           where id = p_user;
          res := 'verified';
        end if;
      end if;
    end if;
  end if;
  update public.kyc_sessions set status = p_status, result = res, decided_at = case when res <> 'pending' then now() end
   where session_id = p_session;
  return res;
end $$;

create or replace function public.admin_verify(p_user uuid, p_gender text, p_birth date, p_country text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if p_gender not in ('male', 'female', 'other') or p_birth > (current_date - interval '18 years')::date then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  insert into public.identities (user_id, doc_hash, provider, session_id, gender, birth_date, nationality)
  values (p_user, 'admin:' || p_user, 'admin', 'admin', p_gender, p_birth, p_country)
  on conflict (user_id) do update set gender = excluded.gender, birth_date = excluded.birth_date, provider = 'admin', verified_at = now();
  update public.profiles set gender = p_gender, birth_date = p_birth, country = coalesce(upper(p_country), country), id_verified_at = now()
   where id = p_user;
end $$;

-- 2. Verified-only use
create or replace function public.require_verified(p uuid) returns void
language plpgsql stable security definer set search_path = '' as $$
begin
  if (select f.require_verification from public.app_flags f where f.id = 1)
     and not exists (select 1 from public.profiles x where x.id = p and x.id_verified_at is not null) then
    raise exception 'verify_required' using errcode = 'P0001';
  end if;
end $$;

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.find_match2(text, text)'::regprocedure);
  a := 'if banned > now() then raise exception ''banned'' using errcode = ''P0001''; end if;';
  if position(a in d) = 0 then raise exception 'find_match2: anchor not found'; end if;
  execute replace(d, a, a || '
  perform public.require_verified(me);
  if (select p.manner from public.profiles p where p.id = me) < 20 then raise exception ''manner_low'' using errcode = ''P0001''; end if;');

  d := pg_get_functiondef('public.voice_admit(text, uuid, boolean)'::regprocedure);
  a := 'perform public.random_chat_end_all(me);';
  if position(a in d) = 0 then raise exception 'voice_admit: anchor not found'; end if;
  execute replace(d, a, 'perform public.require_verified(me);
 ' || a);

  d := pg_get_functiondef('public.create_voice_room2(text, text, integer, text, integer, integer, boolean, text)'::regprocedure);
  a := 'perform public.random_chat_end_all(me);';
  if position(a in d) = 0 then raise exception 'create_voice_room2: anchor not found'; end if;
  execute replace(d, a, 'perform public.require_verified(me);
 ' || a);

  d := pg_get_functiondef('public.post_limit()'::regprocedure);
  a := 'if (select count(*) from public.posts p where p.user_id = new.user_id';
  if position(a in d) = 0 then raise exception 'post_limit: anchor not found'; end if;
  execute replace(d, a, 'perform public.require_verified(new.user_id);
  ' || a);

  d := pg_get_functiondef('public.comment_add(uuid, text, bigint)'::regprocedure);
  a := 'if char_length(t) not between 1 and 300 then';
  if position(a in d) = 0 then raise exception 'comment_add: anchor not found'; end if;
  execute replace(d, a, 'perform public.require_verified(me);
  ' || a);

  d := pg_get_functiondef('public.request_friend(uuid)'::regprocedure);
  a := 'perform pg_advisory_xact_lock(hashtext(''friend:'' || me::text));';
  if position(a in d) = 0 then raise exception 'request_friend: anchor not found'; end if;
  execute replace(d, a, a || '
  perform public.require_verified(me);');
end $$;

-- 3. Manners
create table if not exists public.chat_ratings (
  match_id uuid not null, -- no foreign key: ratings outlive the 30-day purge of chats (the score looks back 90 days)
  rater uuid not null references public.profiles(id) on delete cascade,
  rated uuid not null references public.profiles(id) on delete cascade,
  good boolean not null,
  tags text[] not null default '{}',
  created_at timestamptz not null default now(),
  primary key (match_id, rater)
);
create index if not exists chat_ratings_rated on public.chat_ratings (rated, created_at desc);
alter table public.chat_ratings enable row level security;
revoke all on public.chat_ratings from public, anon, authenticated;

create or replace function public.manner_refresh(p uuid) returns void
language sql security definer set search_path = '' as $$
  update public.profiles x set manner = (
    select round(100.0 * (count(*) filter (where r.good) + 5) / (count(*) filter (where r.good) + 2 * count(*) filter (where not r.good) + 10))::smallint
      from public.chat_ratings r where r.rated = p and r.created_at > now() - interval '90 days')
   where x.id = p
$$;

create or replace function public.rate_chat(p_match uuid, p_good boolean, p_tags text[] default '{}') returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); m public.matches; other uuid; t text[] := array(select distinct x from unnest(coalesce(p_tags, '{}')) x);
begin
  select * into m from public.matches x where x.id = p_match and me in (x.user_a, x.user_b) and x.kind = 'random';
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  other := case when m.user_a = me then m.user_b else m.user_a end;
  if coalesce(m.ended_at, now()) < now() - interval '1 day' then raise exception 'too_late' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.messages x where x.match_id = m.id and x.sender = me)
     or not exists (select 1 from public.messages x where x.match_id = m.id and x.sender = other) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  if not t <@ (case when p_good then array['kind', 'funny', 'respectful', 'good_listener', 'interesting']
                    else array['rude', 'sexual', 'spam', 'fake', 'creepy'] end) or cardinality(t) > 3 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  insert into public.chat_ratings (match_id, rater, rated, good, tags) values (m.id, me, other, p_good, t)
  on conflict (match_id, rater) do nothing;
  if not found then raise exception 'already_rated' using errcode = 'P0001'; end if;
  perform public.manner_refresh(other);
end $$;

-- 4. Badges
create or replace function public.trust_json(p uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', x.id, 'verified', x.id_verified_at is not null, 'manner', x.manner,
    'ratings', (select count(*) from public.chat_ratings r where r.rated = x.id and r.created_at > now() - interval '90 days'),
    'tags', coalesce((select jsonb_agg(jsonb_build_object('tag', z.tag, 'n', z.n) order by z.n desc, z.tag) from (
       select t.tag, count(*) n from public.chat_ratings r, unnest(r.tags) t(tag)
        where r.rated = x.id and r.good and r.created_at > now() - interval '90 days'
        group by t.tag order by count(*) desc limit 3) z), '[]'))
    from public.profiles x where x.id = p
$$;

create or replace function public.trust(p_users uuid[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.trust_json(u)), '[]') from unnest(p_users[1:100]) u
   where u = auth.uid() or public.can_view(u) or exists (select 1 from public.posts pp where pp.user_id = u and pp.visibility = 'public')
$$;

create or replace function public.my_trust() returns jsonb
language sql stable security definer set search_path = '' as $$
  select public.trust_json(auth.uid()) || jsonb_build_object(
    'required', (select f.require_verification from public.app_flags f where f.id = 1),
    'kyc', (select jsonb_build_object('status', s.status, 'result', s.result, 'at', s.created_at)
              from public.kyc_sessions s where s.user_id = auth.uid() order by s.created_at desc limit 1),
    'rated', coalesce((select jsonb_agg(r.match_id) from public.chat_ratings r where r.rater = auth.uid()
                        and r.created_at > now() - interval '1 day'), '[]'))
$$;

-- Verified badges on posts and comments
do $$
declare d text;
begin
  d := pg_get_functiondef('public.post_json(public.posts)'::regprocedure);
  if position('''accent'', a.accent)' in d) = 0 then raise exception 'post_json: anchor not found'; end if;
  execute replace(d, '''accent'', a.accent)', '''accent'', a.accent, ''verified'', a.id_verified_at is not null)');
  d := pg_get_functiondef('public.comment_json(public.post_comments)'::regprocedure);
  if position('''accent'', a.accent)' in d) = 0 then raise exception 'comment_json: anchor not found'; end if;
  execute replace(d, '''accent'', a.accent)', '''accent'', a.accent, ''verified'', a.id_verified_at is not null)');
end $$;

revoke execute on function public.keep_identity_ban(), public.kyc_session_new(uuid, text), public.kyc_latest(uuid), public.kyc_owner(text),
  public.kyc_apply(uuid, text, text, jsonb), public.admin_verify(uuid, text, date, text), public.require_verified(uuid),
  public.manner_refresh(uuid), public.rate_chat(uuid, boolean, text[]), public.trust_json(uuid), public.trust(uuid[]),
  public.my_trust() from public, anon, authenticated;
grant execute on function public.kyc_session_new(uuid, text), public.kyc_latest(uuid), public.kyc_owner(text),
  public.kyc_apply(uuid, text, text, jsonb) to service_role;
grant execute on function public.admin_verify(uuid, text, date, text), public.rate_chat(uuid, boolean, text[]),
  public.trust(uuid[]), public.my_trust() to authenticated;
