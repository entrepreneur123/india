# Collects Google autocomplete suggestions for India (gl=in) for govt-job seed terms.
# Usage: python collect_suggest.py  (writes suggest.json; ~400 requests, ~3 minutes)
import json, time, urllib.parse, urllib.request, string, sys
SEEDS = ["sarkari result", "ssc", "ssc cgl", "ssc chsl", "ssc gd", "ssc mts", "ssc cpo", "ssc je", "ssc stenographer",
         "ssc result", "ssc admit card", "sarkari naukri", "govt job", "सरकारी रिजल्ट", "सरकारी नौकरी", "एसएससी"]
def suggest(q, hl):
    u = "https://suggestqueries.google.com/complete/search?" + urllib.parse.urlencode({"client": "firefox", "hl": hl, "gl": "in", "q": q})
    for i in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"}), timeout=15) as r:
                return json.loads(r.read().decode("utf-8", "replace"))[1]
        except Exception as e:
            time.sleep(2)
    return []
out = {}
for seed in SEEDS:
    hl = "hi" if any("ऀ" <= c <= "ॿ" for c in seed) else "en"
    res = {seed: suggest(seed, hl)}
    suffixes = [""] if hl == "hi" else list(string.ascii_lowercase) + ["2026"]
    for s in suffixes:
        if s:
            res[seed + " " + s] = suggest(seed + " " + s, hl)
            time.sleep(0.25)
    out[seed] = res
    print(seed, sum(len(v) for v in res.values()), file=sys.stderr)
json.dump(out, open("suggest.json", "w"), ensure_ascii=False, indent=1)
