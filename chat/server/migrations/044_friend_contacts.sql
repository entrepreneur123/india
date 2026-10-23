-- Friends can exchange messenger contacts (WhatsApp, phone, Telegram, Instagram, LINE, KakaoTalk, WeChat, Snapchat,
-- Discord). I keep mine once (set_contacts) and choose each friend I share them with (share_contacts); a friend sees
-- them only while we are friends and I share (contacts_of). Unfriending or blocking ends the sharing both ways.

create table if not exists public.contact_cards (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  contacts jsonb not null default '{}',
  updated_at timestamptz not null default now()
);
create table if not exists public.contact_shares (
  owner uuid not null references public.profiles(id) on delete cascade,
  viewer uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (owner, viewer)
);
alter table public.contact_cards enable row level security;
alter table public.contact_shares enable row level security;
revoke all on public.contact_cards, public.contact_shares from public, anon, authenticated;

-- Kept: known apps only, trimmed, no line breaks; phone-like ones as digits with an optional +, the rest as handles.
create or replace function public.contacts_clean(p jsonb) returns jsonb language plpgsql immutable set search_path = '' as $$
declare k text; v text; out jsonb := '{}';
begin
  for k, v in select key, btrim(value) from jsonb_each_text(coalesce(p, '{}')) loop
    if v = '' then continue; end if;
    if k in ('phone', 'whatsapp') then
      v := regexp_replace(v, '[\s().-]', '', 'g');
      if v !~ '^\+?[0-9]{6,16}$' then raise exception 'contact_invalid' using errcode = 'P0001', detail = k; end if;
    elsif k in ('telegram', 'instagram', 'line', 'kakaotalk', 'wechat', 'snapchat', 'discord') then
      v := ltrim(v, '@');
      if v !~ '^[A-Za-z0-9._-]{1,40}$' then raise exception 'contact_invalid' using errcode = 'P0001', detail = k; end if;
    else
      raise exception 'bad_request' using errcode = 'P0001';
    end if;
    out := out || jsonb_build_object(k, v);
  end loop;
  return out;
end $$;

create or replace function public.set_contacts(p_contacts jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); c jsonb := public.contacts_clean(p_contacts);
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  insert into public.contact_cards (user_id, contacts) values (me, c)
  on conflict (user_id) do update set contacts = excluded.contacts, updated_at = now();
  return c;
end $$;

-- Mine: what I keep and who I share with.
create or replace function public.my_contacts() returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'contacts', coalesce((select c.contacts from public.contact_cards c where c.user_id = auth.uid()), '{}'),
    'shared_with', coalesce((select jsonb_agg(s.viewer) from public.contact_shares s where s.owner = auth.uid()), '[]'))
$$;

create or replace function public.share_contacts(p_user uuid, p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if p_on then
    if not public.are_friends(me, p_user) then raise exception 'not_friends' using errcode = 'P0001'; end if;
    insert into public.contact_shares (owner, viewer) values (me, p_user) on conflict do nothing;
  else
    delete from public.contact_shares where owner = me and viewer = p_user;
  end if;
end $$;

-- A friend's side: their contacts if they share them with me, and whether I share mine with them.
create or replace function public.contacts_of(p_user uuid) returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'contacts', (select c.contacts from public.contact_cards c join public.contact_shares s on s.owner = c.user_id
                  where c.user_id = p_user and s.viewer = auth.uid() and c.contacts <> '{}'),
    'mine_shared', exists (select 1 from public.contact_shares s where s.owner = auth.uid() and s.viewer = p_user))
  where public.are_friends(auth.uid(), p_user)
$$;

-- No longer friends (unfriended, or blocked: unfriend_on_block): sharing ends both ways.
create or replace function public.unshare_on_unfriend() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.contact_shares where (owner = old.requester and viewer = old.addressee) or (owner = old.addressee and viewer = old.requester);
  return old;
end $$;
create or replace trigger friendships_unshare after delete on public.friendships for each row execute function public.unshare_on_unfriend();

revoke execute on function public.contacts_clean(jsonb), public.unshare_on_unfriend() from public, anon, authenticated;
revoke execute on function public.set_contacts(jsonb), public.my_contacts(), public.share_contacts(uuid, boolean), public.contacts_of(uuid) from public, anon;
grant execute on function public.set_contacts(jsonb), public.my_contacts(), public.share_contacts(uuid, boolean), public.contacts_of(uuid) to authenticated;
