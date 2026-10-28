-- 086 keeps one of ID colour / name effect at a time. A purchase wears the first item of a kind at once (040); for a
-- colour, only while no name effect is worn (else the new colour would sit unseen under the effect). Ownership is
-- unchanged: this is redeem_cosmetic as it is live, with that one condition.

create or replace function public.redeem_cosmetic(p_user uuid, p_item text, p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare k text; pu public.purchases;
begin
  select i.kind into k from public.cosmetic_items i where i.id = p_item;
  if k is null or p_token is null or p_user is null then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.purchases (token, user_id, product_id) values (p_token, p_user, p_item) on conflict (token) do nothing;
  select * into pu from public.purchases x where x.token = p_token;
  if pu.user_id <> p_user then return 'other_account'; end if;
  if pu.revoked_at is not null then return 'revoked'; end if;
  insert into public.user_cosmetics (user_id, item_id, token) values (p_user, p_item, p_token)
    on conflict (user_id, item_id) do nothing;
  if not found then return 'already'; end if;
  update public.profiles set
    look_badge = case when k = 'badge' then coalesce(look_badge, p_item) else look_badge end,
    look_color = case when k = 'color' and look_effect is null then coalesce(look_color, p_item) else look_color end,
    look_font = case when k = 'font' then coalesce(look_font, p_item) else look_font end,
    look_frame = case when k = 'frame' then coalesce(look_frame, p_item) else look_frame end
   where id = p_user;
  return 'granted';
end $$;
