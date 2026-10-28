-- More looks (the owner, 2026-10-28: "add fonts and other paid items"): two badges, two ID colours, six ID fonts.
-- Drawn by the apps (web: lib/looks.ts, components/looks/badges.tsx, public/fonts/looks); sold in the Android app as
-- Google Play products of the same ids (created in the Play Console before they can be bought there).

insert into public.cosmetic_items (id, kind, price_inr, sort, active) values
  ('badge_moon', 'badge', 99, 7, true), ('badge_lotus', 'badge', 149, 8, true),
  ('color_platinum', 'color', 99, 9, true), ('color_cyber', 'color', 99, 10, true),
  ('font_elegant', 'font', 69, 7, true), ('font_classic', 'font', 69, 8, true), ('font_hand', 'font', 69, 9, true),
  ('font_mono', 'font', 69, 10, true), ('font_neon', 'font', 69, 11, true), ('font_sport', 'font', 69, 12, true)
on conflict (id) do nothing;
