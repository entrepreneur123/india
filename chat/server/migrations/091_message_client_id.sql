-- A line is stored once however often it is sent (the clean-room overhaul, 2026-10-28; Matrix's transaction ids:
-- the client names each line, a retry with the same name gets the first result). The web app now shows a line the
-- moment it is sent and sends it again after a dropped connection; without this a retry after a lost reply stored
-- it twice. The name also lets the app match its "sending…" line with the copy that comes back live.
--  * Nullable: older apps (and the bots) keep inserting without one. NULLs never collide in a unique index.

alter table public.messages add column if not exists client_id uuid;
create unique index if not exists messages_sender_client on public.messages (sender, client_id);
grant insert (client_id) on public.messages to authenticated;
