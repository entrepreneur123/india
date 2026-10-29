# LottoDost — working rules

## Priority 1 (owner's top goal)
**Make lottodost.com rank at the very top of Google (and Indian browsers' search) for "lottery result"**,
then "lottery sambad result", "kerala lottery result", "maharashtra lottery result" and the same in Bengali/Hindi/Marathi/Malayalam.
Keep this in mind in every task: before finishing any change, ask whether it helps or hurts this ranking.
(소유자 1순위: "lottery result" 검색에서 우리 사이트가 가장 상단에 뜨는 것. 모든 작업에서 이를 염두에 둘 것.)

How we pursue it:
- Be the fastest correct source: results go live minutes after each draw (relay workflow, IndexNow, sitemap lastmod).
- On-page: home title/H1 lead with "Lottery Result Today {date}"; every page keeps a precise title, description, canonical, hreflang, schema.org (BreadcrumbList, WebPage with dateModified and primaryImageOfPage), result image in the image sitemap.
- Coverage: every draw and every state we can read correctly gets its own page (draw, day, game hub, state hub, language pages).
- Speed and UX: small static pages, no layout shift, works on cheap Android phones on slow networks.
- Never use black-hat methods (spam posts, fake reviews, link buying, cloaking, doorway pages, parasite pages): they risk a Google penalty and the AdSense account.
- Numbers must be exactly right: publish only verified numbers; otherwise link the official sheet.

## Search Console loop
- `.github/workflows/gsc.yml` reads Search Console daily (secret `GSC_SERVICE_ACCOUNT_JSON`) into branch `seo`: read `gsc/latest.md` first, `gsc/history/` for trends.
- Twice a week a scheduled session tunes the site from it: striking-distance queries (position 4–20) get their page's title/description/content matched to the exact wording people search; target queries are tracked over time. Never write pages for games or claims we cannot verify.

## Other standing rules
- Report to the owner in Korean. Verify thoroughly, no hallucination; state what was not verified.
- Develop and push only on branch `claude/gallant-lovelace-ynoa63`.
- Secrets only in GitHub Secrets.
- Tests: `cd web && python3 -m pytest -q test_site.py`; `cd pipeline && python3 -m pytest -q`.
