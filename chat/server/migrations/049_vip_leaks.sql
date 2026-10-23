-- VIP incognito (046) leaks found in review: room lines, reactions, queue/links, ludo names and the room bot still read
-- the real name; the block list, push notifications and avatar/cover files gave the real person away; "VIP" could be
-- imitated with invisible or look-alike characters.

-- Room-wide names: the everyone-masked view, like the other room functions in 046. The block list: per viewer.
do $$
declare f text; d text;
begin
  foreach f in array array['voice_line', 'voice_react', 'voice_queue_add', 'voice_share_link', 'ludo_join', 'ludo_open',
                           'bot_context', 'my_blocks'] loop
    for d in select pg_get_functiondef(p.oid) from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname = f loop
      execute regexp_replace(d, 'public\.profiles(\s+)([a-z]\w*)',
                             case when f = 'my_blocks' then 'public.profiles_seen\1\2' else 'public.profiles_public\1\2' end, 'g');
    end loop;
  end loop;
end $$;

-- Push: shown to the notification's owner, who sees "VIP" unless they are friends (no signed-in user here).
do $$ begin
  execute replace(pg_get_functiondef('public.push_payload'::regproc), '''actor_name'', p.name',
    '''actor_name'', case when p.anon and not public.are_friends(o.user_id, p.id) then ''VIP'' else p.name end');
end $$;

-- Avatar and cover files of someone incognito: only they and their friends.
do $$ begin
  execute replace(pg_get_functiondef('public.can_read_media'::regproc),
    'then public.can_view(((storage.foldername(object_name))[1])::uuid)',
    'then public.can_view(((storage.foldername(object_name))[1])::uuid)
          and not exists (select 1 from public.profiles x where x.id = ((storage.foldername(object_name))[1])::uuid and x.anon
                            and not public.are_friends(auth.uid(), x.id))');
end $$;

-- "VIP" can't be imitated: look-alike letters folded, everything but a-z / 0-9 dropped before comparing.
create or replace function public.looks_like_vip(p text) returns boolean language sql immutable set search_path = '' as $$
  select regexp_replace(translate(lower(coalesce(p, '')), 'ірѵνιρ', 'ipvvip'), '[^a-z0-9]', '', 'g') = 'vip'
$$;
do $$ begin
  execute replace(pg_get_functiondef('public.profile_name_guard'::regproc),
    'lower(btrim(new.name)) = ''vip''', 'public.looks_like_vip(new.name)');
end $$;

-- Internal helpers (has_emoji: unused since 046b).
revoke execute on function public.mask_seen(public.profiles, boolean), public.looks_like_vip(text), public.has_emoji(text) from public, anon, authenticated;
