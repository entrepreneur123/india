-- 130: Korean swear words, which are written stuck to the next word ('니애미보짓살…' on the megaphone, 2026-11-01:
-- the 125 list had 3 Korean words and no roots). `anywhere` roots mask the whole word they sit in, so only roots that
-- are never part of an ordinary word are listed that way; words with an everyday meaning inside ('보지 마' = don't
-- look, '자지 마' = don't sleep, '시발점' = starting point, '새끼' = a young animal, even as a whole word) are matched only
-- as whole words or left out. Re-runnable.
insert into public.text_filter_words (word, anywhere)
select public.text_key(w), a from (values
  ('씨발', true), ('씨바', true), ('씨빨', true), ('씨팔', true), ('쓰발', true), ('ㅅㅂ', true), ('ㅆㅂ', true),
  ('개새끼', true), ('개새기', true), ('개색기', true), ('개색끼', true), ('개세끼', true),
  ('병신', true), ('븅신', true), ('ㅂㅅ', false),
  ('좆', true), ('존나', true), ('ㅈㄴ', false), ('지랄', true),
  ('니애미', true), ('느금마', true), ('니미럴', true), ('엠창', true), ('애미뒤', true),
  ('썅년', true), ('쌍년', true), ('걸레년', true), ('창녀', true), ('미친년', true), ('미친놈', true),
  ('보짓', true), ('보지털', true), ('자지털', true),
  ('시발', false), ('시발놈', true), ('시발년', true)
) v(w, a)
-- Stored as text_key() leaves them: consonant shorthand (ㅅㅂ) is folded to conjoining jamo by its NFKD step.
on conflict (word) do update set anywhere = excluded.anywhere;
