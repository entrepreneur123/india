-- Profile highlights (the owner, 2026-10-26): up to 10 titled albums of 1-10 photos each, shown on the profile in a
-- turning carousel; nothing moves on its own. Photos are uploaded to <me>/avatar/ (the same read rule as profile photos:
-- can_view, hidden by VIP incognito) and kept from the nightly clean-up (stale_media). Titles are plain text like names.

create table if not exists public.highlights (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 20),
  photos text[] not null check (cardinality(photos) between 1 and 10),
  created_at timestamptz not null default now()
);
create index if not exists highlights_user on public.highlights (user_id, id);
alter table public.highlights enable row level security;
revoke all on public.highlights from public, anon, authenticated;

-- p_id null: a new one. Returns its id.
create or replace function public.save_highlight(p_id bigint, p_title text, p_photos text[]) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := normalize(btrim(coalesce(p_title, '')), NFC); p text[] := coalesce(p_photos, '{}'); hid bigint;
begin
  if char_length(t) not between 1 and 20 or not public.name_plain(t)
     or cardinality(p) not between 1 and 10 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
     or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                  or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if p_id is null then
    perform pg_advisory_xact_lock(hashtext('highlights:' || me::text));
    if (select count(*) from public.highlights h where h.user_id = me) >= 10 then raise exception 'not_allowed' using errcode = 'P0001'; end if;
    insert into public.highlights (user_id, title, photos) values (me, t, p) returning id into hid;
  else
    update public.highlights set title = t, photos = p where id = p_id and user_id = me returning id into hid;
    if hid is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  end if;
  return hid;
end $$;

create or replace function public.delete_highlight(p_id bigint) returns void
language sql security definer set search_path = '' as $$
  delete from public.highlights where id = p_id and user_id = public.voice_me()
$$;

-- Someone's highlights, oldest first, when I may see their photos (can_read_media's rule for avatar files).
create or replace function public.highlights_of(p_user uuid) returns table (id bigint, title text, photos text[])
language sql stable security definer set search_path = '' as $$
  select h.id, h.title, h.photos from public.highlights h
   where h.user_id = p_user and public.can_read_media(p_user::text || '/avatar/x')
   order by h.id
$$;

create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.profiles p where p.cover_path = o.name)
     and not exists (select 1 from public.profiles p where p.id::text = (storage.foldername(o.name))[1] and o.name = any (p.photos))
     and not exists (select 1 from public.highlights h where h.user_id::text = (storage.foldername(o.name))[1] and o.name = any (h.photos))
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.voice_messages m where m.media_path = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;
