# Incidents

Every miss: what users saw, the root cause, the fix, and what now stops it from happening silently again
(a test, a check in `pipeline/lottery_pipeline/watch.py`, or both). Newest first. Ids match `ops/incidents.json`
on the `ops` branch where the watcher found them.

| Date | What happened | Root cause | Fix | Guard against a repeat |
|---|---|---|---|---|
| 30-10 | Two 29.10 Maharashtra sheets the improved reader reads stayed PDF links | A sheet gets 2 OCR tries for good; a better reader never re-tried old failures | `OCR_VERSION`: a new reader version gets fresh tries | `test_a_better_reader_tries_failed_sheets_again`; watcher `mh:*` unread incidents |
| 30-10 | 1 PM result 68C 85552 on the site at 16:05 IST, ~3 h late | `jobs-publish.yml` pushes the `data` branch every ~15 min; the results run's push was rejected and the run stopped | rebase-and-retry push (`push_data`), a failed publish no longer ends the run | reproduced locally (two clones, concurrent pushes); watcher `late:*` and `run:*` incidents |
| 30-10 | `/maharashtra/` hub titled "Maharashtra महाराष्ट्र आकर्षक पुष्कराज साप्ताहिक सोडत …" since 29.10 | Sheet files ending at the game name ("…_Mah. Akarshak Pushkraj_0001.pdf") gave no English name; the empty slug made the game page `/maharashtra/` itself; three draws shared one id | file-name reader fixed; a nameless draw gets a page by its upload number and no game hub | `test_english_name_when_the_file_name_stops_at_the_game`, `test_draws_with_marathi_only_names_keep_distinct_ids`, `test_a_maharashtra_draw_without_an_english_name_does_not_replace_the_hub`; watcher `mh:hub-title` |
| 30-10 | Kerala Karunya Plus (every Thursday) never published: 15, 22, 29.10 missing | One copy wrote "1.00,00,000" (and "5 ,00,000"), the other had a sentence "first prize winner got 1 Crore" before the prize block; two copies never agreed | amounts with those typos read; a prize heading with no ticket numbers is skipped | `test_amount_typos_and_a_sentence_before_the_prize_block`; watcher `late:<yesterday>:KL` |
| 30-10 | Kerala archive only 11 days (Dear: 300) | Blog copies were looked up in the latest 8 posts only | blog feed asked by date; 25 older dates a night, same two-copies rule | `test_blog_feed_is_asked_for_the_days_around_the_draw` |
| 30-10 | Four 25-26.10 Maharashtra sheets never read (they read correctly) | Unread sheets were tried for 3 days only; downloads take 7-12 min from the runners; one night run skipped Maharashtra on a DNS failure | 7 days of tries, ~2 min of retries for the sheet list, 45 min in night runs | watcher `mh:*` unread incidents |
| 30-10 | Maharashtra layouts not read: "10 Numbers" misread as 40, a rule inside the 350-number grid, the consolation heading run into its row, two grid rows joined | OCR misreads and grid rules the reader did not expect | see commit 13c90fde: each case handled, only when the sheet's own structure confirms it | regression on all 18 sheets of 25-29.10: 15 read, every number identical to the sheets; `test_a_position_between_two_rows_does_not_join_them` |

## How an incident is handled

1. Reproduce it from the record (ops branch, run logs, the live page).
2. Find the root cause, not the symptom (why did nothing catch it earlier?).
3. Fix it; add a test that fails without the fix; add or adjust a watcher check if the miss could recur another way.
4. Validate (tests, local build, a re-read of real sheets where the reader changed) and push.
5. Add a row above. A sheet the reader correctly refuses (its readings disagree) is not a bug: note it once, link stays.
