-- 124: a guest's own country choice (123) must be insertable by the app. 001 granted insert on a fixed column list
-- (id, name, gender, birth_date, city, bio, photo_path), so 123's insert with `country` was refused for every new
-- profile (42501 → 403; caught by the production guest test on 2026-10-31). check_adult still overrides the value
-- from a phone account's number and blanks a code that isn't in dial_codes, so nothing is trusted from the client.
grant insert (country) on public.profiles to authenticated;
