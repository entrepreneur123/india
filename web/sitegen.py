"""Static, search-friendly results site generated from the v2 feeds.

Every draw gets its own pre-rendered page (readable without JavaScript, fast on 2G), every day
has a page, and the home page shows today. English at the root, Bengali under /bn/ and Hindi
under /hi/, linked with hreflang. Wording comes from the Android app's strings.xml plus the
page-level texts below, so app and site say the same thing.

    python web/sitegen.py --data public --out public --base-url https://example.in
Optional environment: ADSENSE_CLIENT (ca-pub-…) adds the AdSense script (Auto ads) and ads.txt, and
ADSENSE_SLOT (a display ad unit id) adds in-page units; PLAY_URL links the app once it is on Google Play;
CF_BEACON_TOKEN adds Cloudflare Web Analytics (cookie-free); GOOGLE_SITE_VERIFICATION and
BING_SITE_VERIFICATION add the ownership meta tags for Search Console / Bing Webmaster Tools.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import pathlib
import re
import urllib.parse
import xml.etree.ElementTree as ET

import guides  # web/guides.py: tax, claim and scam guides

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"
STATIC = ROOT / "web/static"
LANGS = {"en": ("values", ""), "bn": ("values-bn", "/bn"), "hi": ("values-hi", "/hi")}
# Every page is for readers in India: language + region in hreflang, lang and og:locale.
REGION = {"en": "en-IN", "bn": "bn-IN", "hi": "hi-IN"}
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
# Until 12:30 IST the home page shows yesterday's results; then today's date and the draws to come
# (the 1 PM result run publishes it then: FLIP in pipeline/lottery_pipeline/schedule.py).
DAY_FLIP = dt.time(12, 30)
WEEKLY = [("NL1PM", "13:00", "1pm"), ("SK6PM", "18:00", "6pm"), ("NL8PM", "20:00", "8pm")]
SLUG = {"NL1PM": "1pm", "SK6PM": "6pm", "NL8PM": "8pm", "NLLAXMI": "laxmi-8pm", "KL": "kerala"}
TAG = {"NL1PM": "tag_morning", "SK6PM": "tag_day", "NL8PM": "tag_night"}
# One fixed page per daily draw: people search "lottery sambad morning/night" before the result exists, so the
# page that ranks must already be there and update in place (Google Trends, India, 20-10-2026).
HUB = {"NL1PM": "/morning-1pm/", "SK6PM": "/day-6pm/", "NL8PM": "/night-8pm/"}
CONTACT = "yechan750745@gmail.com"

# Page-level texts. {d} = 19.10.2026, {t} = 1 PM, {g} = game name, {s} = state, {n} = 1st prize.
# Kerala in Malayalam (one page: /ml/kerala/). Draw names as printed on Malayalam tickets.
ML = {
    "h1": "കേരള ലോട്ടറി ഫലം ഇന്ന്",
    "title": "കേരള ലോട്ടറി ഫലം ഇന്ന് {d} – {g} | Kerala Lottery Result",
    "desc": "കേരള ലോട്ടറി ഫലം {d}: {g}, ഒന്നാം സമ്മാനം {n}. എല്ലാ സമ്മാനങ്ങളും കഴിഞ്ഞ നറുക്കെടുപ്പുകളുടെ ഫലങ്ങളും.",
    "schedule": "എല്ലാ ദിവസവും ഉച്ചയ്ക്ക് 3 മണിക്ക് നറുക്കെടുപ്പ്: തിങ്കൾ ഭാഗ്യതാര, ചൊവ്വ സ്ത്രീ ശക്തി, ബുധൻ ധനലക്ഷ്മി, വ്യാഴം കാരുണ്യ പ്ലസ്, വെള്ളി സുവർണ്ണ കേരളം, ശനി കാരുണ്യ, ഞായർ സമൃദ്ധി. ഫലം സാധാരണയായി നറുക്കെടുപ്പിന് ഏകദേശം ഒരു മണിക്കൂറിന് ശേഷം ലഭ്യമാകും.",
    "waiting": "ഇന്നത്തെ ഫലം നറുക്കെടുപ്പിന് ശേഷം ഇവിടെ വരും (ഏകദേശം വൈകുന്നേരം 4 മണിയോടെ). ഈ പേജ് പുതുക്കി നോക്കുക.",
    "recent": "കേരള ലോട്ടറി ഫലങ്ങൾ – സമീപകാല നറുക്കെടുപ്പുകൾ", "date": "തീയതി", "game": "ലോട്ടറി",
    "first": "ഒന്നാം സമ്മാനം", "last4": "അവസാനത്തെ 4 അക്കങ്ങൾ ഒത്തുവന്നാൽ",
    "unconfirmed": "ഈ നമ്പറുകൾ ഇനിയും സ്ഥിരീകരിച്ചിട്ടില്ല.",
    "note": "ഫലങ്ങൾ മൂന്ന് പൊതു സ്രോതസ്സുകളിൽ നിന്ന് ഒത്തുനോക്കി പ്രസിദ്ധീകരിക്കുന്നവയാണ്. സമ്മാനം അവകാശപ്പെടുന്നതിന് മുമ്പ് ഔദ്യോഗിക ഗസറ്റുമായി ഒത്തുനോക്കുക.",
    "official": "ഔദ്യോഗിക വെബ്സൈറ്റ്", "check": "ടിക്കറ്റ് നമ്പർ പരിശോധിക്കുക (English) →",
    "wd": ["തിങ്കൾ", "ചൊവ്വ", "ബുധൻ", "വ്യാഴം", "വെള്ളി", "ശനി", "ഞായർ"],
    "tiers": {"1": "ഒന്നാം സമ്മാനം", "CONS": "സമാശ്വാസ സമ്മാനം", "2": "രണ്ടാം സമ്മാനം", "3": "മൂന്നാം സമ്മാനം",
              "4": "നാലാം സമ്മാനം", "5": "അഞ്ചാം സമ്മാനം", "6": "ആറാം സമ്മാനം", "7": "ഏഴാം സമ്മാനം",
              "8": "എട്ടാം സമ്മാനം", "9": "ഒമ്പതാം സമ്മാനം", "10": "പത്താം സമ്മാനം"},
    "games": {"BT": "ഭാഗ്യതാര", "SS": "സ്ത്രീ ശക്തി", "DL": "ധനലക്ഷ്മി", "KN": "കാരുണ്യ പ്ലസ്",
              "SK": "സുവർണ്ണ കേരളം", "KR": "കാരുണ്യ", "SM": "സമൃദ്ധി"},
}

# Maharashtra State Lottery (other state lotteries, kept apart from Lottery Sambad and Kerala).
MH = {
    "en": {"tab": "Maharashtra", "h1": "Maharashtra Lottery Result Today", "other": "Other state lotteries",
           "game_h1": "Maharashtra {g} Lottery Result", "game_title": "Maharashtra {g} Lottery Result {d} – {k} Draw, Official Sheet",
           "game_desc": "Maharashtra {g} lottery result {d}{f}. Every {g} draw with its official result sheet (PDF) from the Government of Maharashtra.",
           "game_when": "{k} draw every {wd}{t}.", "game_when_any": "{k} draw.", "game_past": "Earlier {g} draws", "games": "All Maharashtra lotteries",
           "first_is": ": 1st prize {n}", "sheet_only": "Official sheet", "marathi": "मराठीत वाचा →",
           "title": "Maharashtra Lottery Result Today {d} – {g} and All Draws",
           "desc": "Maharashtra State Lottery result {d}: {list}. Full prize list of every draw, ticket checker and the official result sheet.",
           "schedule": "The Government of Maharashtra holds weekly draws every afternoon (around 4–5 PM), plus monthly draws and festival bumpers. Each draw has its own name and series, e.g. GL-00 to GL-09.",
           "ocr_note": "These numbers are read by computer from the official result sheet and published only when several independent readings agree. Always check your ticket against the official sheet before claiming.",
           "recent": "Maharashtra lottery result chart – recent draws",
           "draw_title": "Maharashtra {g} Lottery Result {d}{n}",
           "draw_desc": "Maharashtra {g} lottery result {d}: 1st prize {f}. All prizes, ticket checker and the official result sheet.",
           "draw_no": " (Draw {n})", "check_hint": "Type your full ticket number (e.g. GL-00 2142) or just the 4 digits.",
           "open_pdf": "Official result sheet (PDF)", "open_site": "Maharashtra State Lottery website",
           "weekly": "Weekly", "monthly": "Monthly", "bumper": "Bumper", "all": "All Maharashtra results",
           "title_pdf": "Maharashtra Lottery Result Today {d} – {g}, Official Result Sheets",
           "desc_pdf": "Maharashtra State Lottery result {d}: {list} – the official result sheet (PDF) of every draw, straight from the Government of Maharashtra.",
           "pdf_note": "Each draw links its official result sheet (PDF) on the Maharashtra State Lottery website. Check your ticket against that sheet."},
    "bn": {"tab": "মহারাষ্ট্র", "h1": "মহারাষ্ট্র লটারি আজকের রেজাল্ট", "other": "অন্য রাজ্যের লটারি",
           "game_h1": "মহারাষ্ট্র {g} লটারি রেজাল্ট", "game_title": "মহারাষ্ট্র {g} লটারি রেজাল্ট {d} – {k} ড্র, সরকারি শিট",
           "game_desc": "মহারাষ্ট্র {g} লটারি রেজাল্ট {d}{f}। মহারাষ্ট্র সরকারের সরকারি রেজাল্ট শিট (PDF) সহ {g}-এর প্রতিটি ড্র।",
           "game_when": "প্রতি {wd} {k} ড্র{t}।", "game_when_any": "{k} ড্র।", "game_past": "{g}-এর আগের ড্র", "games": "মহারাষ্ট্রের সব লটারি",
           "first_is": ": প্রথম পুরস্কার {n}", "sheet_only": "সরকারি শিট", "marathi": "মারাঠিতে পড়ুন →",
           "title": "মহারাষ্ট্র লটারি আজকের রেজাল্ট {d} – {g} ও সব ড্র",
           "desc": "মহারাষ্ট্র রাজ্য লটারি রেজাল্ট {d}: {list}। প্রতিটি ড্রয়ের পুরো পুরস্কারের তালিকা, টিকিট চেক আর সরকারি রেজাল্ট শিট।",
           "schedule": "মহারাষ্ট্র সরকার রোজ বিকেলে (প্রায় 4–5টা) সাপ্তাহিক ড্র করে, এছাড়া মাসিক ড্র আর উৎসবের বাম্পার। প্রতিটি ড্রয়ের নিজস্ব নাম আর সিরিজ থাকে, যেমন GL-00 থেকে GL-09।",
           "ocr_note": "এই নম্বরগুলো সরকারি রেজাল্ট শিট থেকে কম্পিউটারে পড়া, আর কয়েকটি আলাদা পড়া মিললে তবেই প্রকাশ করা হয়। পুরস্কার দাবির আগে সবসময় সরকারি শিটের সঙ্গে টিকিট মিলিয়ে নিন।",
           "recent": "মহারাষ্ট্র লটারির সাম্প্রতিক রেজাল্ট",
           "draw_title": "মহারাষ্ট্র {g} লটারি রেজাল্ট {d}{n}",
           "draw_desc": "মহারাষ্ট্র {g} লটারি রেজাল্ট {d}: প্রথম পুরস্কার {f}। সব পুরস্কার, টিকিট চেক আর সরকারি রেজাল্ট শিট।",
           "draw_no": " (ড্র {n})", "check_hint": "পুরো টিকিট নম্বর লিখুন (যেমন GL-00 2142) বা শুধু 4টি সংখ্যা।",
           "open_pdf": "সরকারি রেজাল্ট শিট (PDF)", "open_site": "মহারাষ্ট্র রাজ্য লটারির ওয়েবসাইট",
           "weekly": "সাপ্তাহিক", "monthly": "মাসিক", "bumper": "বাম্পার", "all": "মহারাষ্ট্রের সব রেজাল্ট",
           "title_pdf": "মহারাষ্ট্র লটারি আজকের রেজাল্ট {d} – {g}, সরকারি রেজাল্ট শিট",
           "desc_pdf": "মহারাষ্ট্র রাজ্য লটারি রেজাল্ট {d}: {list} – প্রতিটি ড্রয়ের সরকারি রেজাল্ট শিট (PDF), সরাসরি মহারাষ্ট্র সরকারের কাছ থেকে।",
           "pdf_note": "প্রতিটি ড্রয়ের সঙ্গে মহারাষ্ট্র রাজ্য লটারির ওয়েবসাইটের সরকারি রেজাল্ট শিটের (PDF) লিংক আছে। ওই শিটের সঙ্গে টিকিট মিলিয়ে নিন।"},
    "hi": {"tab": "महाराष्ट्र", "h1": "महाराष्ट्र लॉटरी रिज़ल्ट आज", "other": "दूसरे राज्यों की लॉटरी",
           "game_h1": "महाराष्ट्र {g} लॉटरी रिज़ल्ट", "game_title": "महाराष्ट्र {g} लॉटरी रिज़ल्ट {d} – {k} ड्रॉ, सरकारी शीट",
           "game_desc": "महाराष्ट्र {g} लॉटरी रिज़ल्ट {d}{f}। महाराष्ट्र सरकार की सरकारी रिज़ल्ट शीट (PDF) के साथ {g} का हर ड्रॉ।",
           "game_when": "हर {wd} {k} ड्रॉ{t}।", "game_when_any": "{k} ड्रॉ।", "game_past": "{g} के पिछले ड्रॉ", "games": "महाराष्ट्र की सभी लॉटरी",
           "first_is": ": पहला इनाम {n}", "sheet_only": "सरकारी शीट", "marathi": "मराठी में पढ़ें →",
           "title": "महाराष्ट्र लॉटरी रिज़ल्ट आज {d} – {g} और सभी ड्रॉ",
           "desc": "महाराष्ट्र राज्य लॉटरी रिज़ल्ट {d}: {list}। हर ड्रॉ के सारे इनाम, टिकट चेक और सरकारी रिज़ल्ट शीट।",
           "schedule": "महाराष्ट्र सरकार हर दोपहर (लगभग 4–5 बजे) साप्ताहिक ड्रॉ करती है, साथ में मासिक ड्रॉ और त्योहारों के बंपर। हर ड्रॉ का अपना नाम और सीरीज़ होती है, जैसे GL-00 से GL-09।",
           "ocr_note": "ये नंबर सरकारी रिज़ल्ट शीट से कंप्यूटर द्वारा पढ़े गए हैं और कई अलग-अलग रीडिंग मिलने पर ही प्रकाशित होते हैं। इनाम का दावा करने से पहले हमेशा सरकारी शीट से टिकट मिलाएँ।",
           "recent": "महाराष्ट्र लॉटरी रिज़ल्ट चार्ट – हाल के ड्रॉ",
           "draw_title": "महाराष्ट्र {g} लॉटरी रिज़ल्ट {d}{n}",
           "draw_desc": "महाराष्ट्र {g} लॉटरी रिज़ल्ट {d}: पहला इनाम {f}। सारे इनाम, टिकट चेक और सरकारी रिज़ल्ट शीट।",
           "draw_no": " (ड्रॉ {n})", "check_hint": "पूरा टिकट नंबर लिखें (जैसे GL-00 2142) या सिर्फ़ 4 अंक।",
           "open_pdf": "सरकारी रिज़ल्ट शीट (PDF)", "open_site": "महाराष्ट्र राज्य लॉटरी की वेबसाइट",
           "weekly": "साप्ताहिक", "monthly": "मासिक", "bumper": "बंपर", "all": "महाराष्ट्र के सभी रिज़ल्ट",
           "title_pdf": "महाराष्ट्र लॉटरी रिज़ल्ट आज {d} – {g}, सरकारी रिज़ल्ट शीट",
           "desc_pdf": "महाराष्ट्र राज्य लॉटरी रिज़ल्ट {d}: {list} – हर ड्रॉ की सरकारी रिज़ल्ट शीट (PDF), सीधे महाराष्ट्र सरकार से।",
           "pdf_note": "हर ड्रॉ के साथ महाराष्ट्र राज्य लॉटरी की वेबसाइट पर उसकी सरकारी रिज़ल्ट शीट (PDF) का लिंक है। टिकट उसी शीट से मिलाएँ।"},
}
WD_FULL = {"en": ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
           "bn": ["সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার"],
           "hi": ["सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार"]}

# Maharashtra in Marathi (one page: /mr/maharashtra/). Prize names as printed on the result sheets.
MR = {
    "h1": "महाराष्ट्र राज्य लॉटरी निकाल आज",
    "title": "महाराष्ट्र राज्य लॉटरी निकाल आज {d} – {g} | Maharashtra Lottery Result",
    "desc": "महाराष्ट्र राज्य लॉटरी निकाल {d}: {list}. प्रत्येक सोडतीचे अधिकृत निकाल पत्रक (PDF).",
    "schedule": "महाराष्ट्र शासन दररोज दुपारी (सुमारे ४ ते ५ वाजता) साप्ताहिक सोडती काढते, तसेच मासिक सोडती आणि सणांच्या बंपर सोडती.",
    "pdf": "अधिकृत निकाल पत्रक (PDF)", "site": "महाराष्ट्र राज्य लॉटरीची वेबसाइट",
    "note_pdf": "प्रत्येक सोडतीसोबत महाराष्ट्र राज्य लॉटरीच्या वेबसाइटवरील अधिकृत निकाल पत्रकाची (PDF) लिंक दिली आहे. आपले तिकीट त्या पत्रकाशी जुळवून पहा.",
    "note_ocr": "हे क्रमांक अधिकृत निकाल पत्रकातून संगणकाद्वारे वाचलेले आहेत आणि अनेक स्वतंत्र वाचने जुळल्यावरच प्रसिद्ध केले जातात. बक्षिसाचा दावा करण्यापूर्वी अधिकृत पत्रकाशी तिकीट जुळवून पहा.",
    "recent": "महाराष्ट्र लॉटरी निकाल – अलीकडील सोडती", "date": "दिनांक", "game": "सोडत", "first": "पहिले बक्षिस",
    "check": "तिकीट क्रमांक तपासा (English) →",
    "wd": ["सोमवार", "मंगळवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार"],
    "tiers": {"1": "पहिले बक्षिस", "CONS": "प्रोत्साहनपर बक्षिस", "2": "दुसरे बक्षिस", "3": "तिसरे बक्षिस", "4": "चौथे बक्षिस",
              "5": "पाचवे बक्षिस", "6": "सहावे बक्षिस", "7": "सातवे बक्षिस", "8": "आठवे बक्षिस", "9": "नववे बक्षिस",
              "10": "दहावे बक्षिस"},
}

WEB = {
    "en": {
        "home_title": "Lottery Sambad Result Today {d} – Dear Nagaland State Lottery 1 PM, 6 PM, 8 PM",
        "home_h1": "Lottery Sambad Result Today",
        "home_desc": "Lottery Sambad (লটারি সংবাদ) today result {d} ({s}): {list}. Nagaland State Lottery Dear morning 1 PM, day 6 PM, night 8 PM – all prizes, ticket checker and live draw.",
        "day_desc": "Lottery Sambad result {d} ({s}): {list}. All prizes of the Dear 1 PM, 6 PM and 8 PM Nagaland State Lottery draws, with a ticket checker.",
        "yday": "Yesterday", "yday_h1": "Lottery Sambad Yesterday Result",
        "yday_title": "Lottery Sambad Yesterday Result {d} – Dear 1 PM, 6 PM, 8 PM and Kerala",
        "yday_desc": "Yesterday's lottery result {d} ({s}): {list}. Dear Nagaland State Lottery 1 PM, 6 PM, 8 PM and Kerala lottery – all prizes and a ticket checker.",
        "yday_week": "1st prizes – last 7 days",
        "day_title": "Lottery Sambad Result {d} – Dear Lottery 1 PM, 6 PM, 8 PM",
        "day_h1": "Lottery Sambad Result {d}",
        "draw_title": "Lottery Sambad {w} {t} Result {d} – {g}",
        "draw_title_special": "Dear Lottery Result {d} {t} – {g} | {s} State Lottery",
        "slot_title": "Lottery Sambad {w} Result Today {d} – Dear {t}", "slot_h1": "Lottery Sambad {w} {t} Result",
        "slot_desc": "Nagaland State Lottery Dear {t} ({w}) result {d}: 1st prize {n}. Full prize list, ticket checker and every {w} result of the last 30 days.",
        "slot_desc_wait": "Dear {t} ({w}) result {d} comes about 10 minutes after the draw and this page updates by itself. {w} results of the last 30 days.",
        "slot_recent": "{t} result chart – last 30 days", "slot_all": "All {w} {t} results",
        "draw_desc": "{g} result {d} {t}: 1st prize {n}. All prize numbers and a ticket checker.",
        "awaited": "awaited", "result_of": "Result of {d}", "all_of_day": "All results of {d}",
        "old_title": "Lottery Sambad Old Result – Dear Lottery 1 PM, 6 PM, 8 PM, All Dates", "old_h1": "Old results",
        "month_title": "Lottery Sambad Old Result {m} – Dear Lottery 1 PM, 6 PM, 8 PM", "month_h1": "Old results – {m}",
        "months": "By month", "recent_days": "Last 31 days",
        "mon": "January February March April May June July August September October November December".split(),
        "prev": "Previous day", "next": "Next day", "older": "Older results",
        "app": "Get the result the moment it is out: free LottoDost app (alerts, ticket scan, number history).",
        "faq": "Common questions", "q_time": "When are the results declared?",
        "a_time": "Dear lottery draws are at 1 PM (Nagaland), 6 PM (Sikkim) and 8 PM (Nagaland) IST every day. The result sheet usually appears 7–10 minutes after each draw, and this page updates by itself.",
        "check_hint": "Type the full number (e.g. 39C 06366, bumper A 801021) or just the last 4–6 digits.",
        "q_check": "How do I check my ticket?", "q_claim": "How do I claim a prize?",
        "updated": "Updated {t} IST", "home": "Today", "lang": "Language",
        "about": "About", "contact": "Contact", "disclaimer": "Disclaimer", "privacy": "Privacy policy",
        "about_body": "LottoDost publishes Dear lottery results within minutes of each draw. Numbers are read from the published result sheet, checked for completeness and compared with the official Nagaland gazette when it is released. We are not a government website, we are not affiliated with Lottery Sambad or any lottery department, and we do not sell tickets.",
        "contact_body": "Questions, corrections or removal requests: {e}. We usually reply within two working days.",
        "notfound": "Page not found", "search_as": "Also searched as Lottery Sambad, Dear Lottery Result, Nagaland State Lottery, Dhankesari and লটারি সংবাদ.",
        "wd": "Mon Tue Wed Thu Fri Sat Sun".split(), "copy": "Copy link", "copied": "Link copied", "date": "Date",
        "open_page": "Open the full result page", "pick_date": "Find a date", "go": "Show",
        "check_hint_kl": "Type the full number (e.g. MM 289707) or just the last 4 digits.", "kl_tab": "Kerala", "kl_h1": "Kerala Lottery Result Today",
        "kl_title": "Kerala Lottery Result Today {d} – {g} 3 PM, Result Chart",
        "kl_desc": "Kerala lottery result {d}: {g}, 1st prize {n}. All prizes, ticket checker and the official result link.",
        "kl_draw_title": "Kerala Lottery Result {d} – {g}",
        "kl_schedule": "Draw every day at 3 PM: Monday Bhagyathara, Tuesday Sthree Sakthi, Wednesday Dhanalekshmi, Thursday Karunya Plus, Friday Suvarna Keralam, Saturday Karunya, Sunday Samrudhi. The result usually appears about an hour after the draw.",
        "kl_recent": "Kerala lottery result chart – recent draws", "game": "Lottery",
    },
    "bn": {
        "home_title": "লটারি সংবাদ আজকের রেজাল্ট {d} – নাগাল্যান্ড স্টেট ডিয়ার লটারি দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা",
        "home_h1": "লটারি সংবাদ আজকের রেজাল্ট",
        "home_desc": "লটারি সংবাদ আজকের রেজাল্ট {d} ({s}): {list}। নাগাল্যান্ড স্টেট লটারি ডিয়ার দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা – সব পুরস্কার, টিকিট চেক আর লাইভ ড্র।",
        "day_desc": "লটারি সংবাদ রেজাল্ট {d} ({s}): {list}। নাগাল্যান্ড স্টেট লটারি ডিয়ার 1টা, 6টা, 8টার সব পুরস্কার আর টিকিট চেক।",
        "yday": "গতকাল", "yday_h1": "লটারি সংবাদ গতকালের রেজাল্ট",
        "yday_title": "লটারি সংবাদ গতকালের রেজাল্ট {d} – ডিয়ার 1টা, 6টা, 8টা আর কেরালা",
        "yday_desc": "গতকালের লটারি রেজাল্ট {d} ({s}): {list}। নাগাল্যান্ড স্টেট লটারি ডিয়ার 1টা, 6টা, 8টা আর কেরালা লটারি – সব পুরস্কার আর টিকিট চেক।",
        "yday_week": "প্রথম পুরস্কার – গত 7 দিন",
        "day_title": "লটারি সংবাদ রেজাল্ট {d} – ডিয়ার লটারি দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা",
        "day_h1": "লটারি সংবাদ রেজাল্ট {d}",
        "draw_title": "লটারি সংবাদ {w} {t} রেজাল্ট {d} – {g}",
        "draw_title_special": "ডিয়ার লটারি রেজাল্ট {d} {t} – {g} | {s} স্টেট লটারি",
        "slot_title": "লটারি সংবাদ আজকের {w} রেজাল্ট {d} – ডিয়ার {t}", "slot_h1": "লটারি সংবাদ {w} {t} রেজাল্ট",
        "slot_desc": "ডিয়ার {t} ({w}) রেজাল্ট {d}: প্রথম পুরস্কার {n}। পুরো পুরস্কারের তালিকা, টিকিট চেক আর গত 30 দিনের সব {w} রেজাল্ট।",
        "slot_desc_wait": "ডিয়ার {t} ({w}) রেজাল্ট {d} ড্রয়ের প্রায় 10 মিনিট পরে আসে, পেজটি নিজে থেকেই আপডেট হয়। গত 30 দিনের {w} রেজাল্ট।",
        "slot_recent": "{t} রেজাল্ট চার্ট – গত 30 দিন", "slot_all": "সব {w} {t} রেজাল্ট",
        "draw_desc": "{g} রেজাল্ট {d} {t}: প্রথম পুরস্কার {n}। সব পুরস্কারের নম্বর আর টিকিট চেক।",
        "awaited": "রেজাল্টের অপেক্ষায়", "result_of": "{d}-এর রেজাল্ট", "all_of_day": "{d}-এর সব রেজাল্ট",
        "old_title": "লটারি সংবাদ পুরোনো রেজাল্ট – ডিয়ার লটারি সব তারিখ", "old_h1": "পুরোনো রেজাল্ট",
        "month_title": "লটারি সংবাদ পুরোনো রেজাল্ট {m} – ডিয়ার লটারি দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা", "month_h1": "পুরোনো রেজাল্ট – {m}",
        "months": "মাস অনুযায়ী", "recent_days": "গত 31 দিন",
        "mon": "জানুয়ারি ফেব্রুয়ারি মার্চ এপ্রিল মে জুন জুলাই আগস্ট সেপ্টেম্বর অক্টোবর নভেম্বর ডিসেম্বর".split(),
        "prev": "আগের দিন", "next": "পরের দিন", "older": "পুরোনো রেজাল্ট",
        "app": "রেজাল্ট বেরোনোর সঙ্গে সঙ্গে জানতে ফ্রি LottoDost অ্যাপ নিন (অ্যালার্ট, টিকিট স্ক্যান, নম্বরের ইতিহাস)।",
        "faq": "সাধারণ প্রশ্ন", "q_time": "রেজাল্ট কখন বেরোয়?",
        "a_time": "ডিয়ার লটারির ড্র রোজ দুপুর 1টায় (নাগাল্যান্ড), সন্ধ্যা 6টায় (সিকিম) আর রাত 8টায় (নাগাল্যান্ড) হয়। প্রতিটি ড্রয়ের সাধারণত 7–10 মিনিট পরে রেজাল্ট শিট বেরোয়, আর এই পেজ নিজে থেকেই আপডেট হয়।",
        "check_hint": "পুরো নম্বর লিখুন (যেমন 39C 06366, বাম্পার A 801021) বা শুধু শেষ 4–6টি সংখ্যা।",
        "q_check": "টিকিট কীভাবে মেলাব?", "q_claim": "পুরস্কার কীভাবে তুলব?",
        "updated": "আপডেট {t} IST", "home": "আজ", "lang": "ভাষা",
        "about": "আমাদের কথা", "contact": "যোগাযোগ", "disclaimer": "দায়মুক্তি", "privacy": "প্রাইভেসি পলিসি",
        "about_body": "LottoDost প্রতিটি ড্রয়ের কয়েক মিনিটের মধ্যে ডিয়ার লটারির রেজাল্ট দেয়। নম্বরগুলো প্রকাশিত রেজাল্ট শিট থেকে পড়া হয়, সব নম্বর আছে কি না দেখা হয়, আর নাগাল্যান্ডের সরকারি গেজেট বেরোলে তার সঙ্গে মেলানো হয়। এটি সরকারি ওয়েবসাইট নয়, লটারি সংবাদ বা কোনো লটারি দপ্তরের সঙ্গে আমাদের সম্পর্ক নেই, আর আমরা টিকিট বিক্রি করি না।",
        "contact_body": "প্রশ্ন, সংশোধন বা কিছু সরানোর অনুরোধ: {e}। সাধারণত দুই কর্মদিবসের মধ্যে উত্তর দিই।",
        "notfound": "পেজটি পাওয়া যায়নি", "search_as": "লটারি সংবাদ, ডিয়ার লটারি রেজাল্ট, নাগাল্যান্ড স্টেট লটারি, ধনকেশরী নামেও খোঁজা হয়।",
        "wd": "সোম মঙ্গল বুধ বৃহস্পতি শুক্র শনি রবি".split(), "copy": "লিংক কপি করুন", "copied": "লিংক কপি হয়েছে", "date": "তারিখ",
        "open_page": "পুরো রেজাল্টের পেজ খুলুন", "pick_date": "তারিখ বেছে নিন", "go": "দেখুন",
        "check_hint_kl": "পুরো নম্বর লিখুন (যেমন MM 289707) বা শুধু শেষ 4টি সংখ্যা।", "kl_tab": "কেরালা", "kl_h1": "কেরালা লটারি আজকের রেজাল্ট",
        "kl_title": "কেরালা লটারি আজকের রেজাল্ট {d} – {g} দুপুর 3টা",
        "kl_desc": "কেরালা লটারি রেজাল্ট {d}: {g}, প্রথম পুরস্কার {n}। সব পুরস্কার, টিকিট চেক আর সরকারি রেজাল্টের লিংক।",
        "kl_draw_title": "কেরালা লটারি রেজাল্ট {d} – {g}",
        "kl_schedule": "রোজ দুপুর 3টায় ড্র: সোমবার ভাগ্যতারা, মঙ্গলবার স্ত্রী শক্তি, বুধবার ধনলক্ষ্মী, বৃহস্পতিবার কারুণ্য প্লাস, শুক্রবার সুবর্ণ কেরালাম, শনিবার কারুণ্য, রবিবার সমৃদ্ধি। রেজাল্ট সাধারণত ড্রয়ের প্রায় এক ঘণ্টা পরে আসে।",
        "kl_recent": "কেরালার সাম্প্রতিক রেজাল্ট", "game": "লটারি",
    },
    "hi": {
        "home_title": "लॉटरी संबाद आज का रिज़ल्ट {d} – नागालैंड स्टेट डियर लॉटरी 1 PM, 6 PM, 8 PM",
        "home_h1": "लॉटरी संबाद आज का रिज़ल्ट",
        "home_desc": "लॉटरी संबाद आज का रिज़ल्ट {d} ({s}): {list}। नागालैंड स्टेट लॉटरी डियर 1 PM, 6 PM, 8 PM – सारे इनाम, टिकट चेक और लाइव ड्रॉ।",
        "day_desc": "लॉटरी संबाद रिज़ल्ट {d} ({s}): {list}। नागालैंड स्टेट लॉटरी डियर 1 PM, 6 PM, 8 PM के सारे इनाम और टिकट चेक।",
        "yday": "कल", "yday_h1": "लॉटरी संबाद कल का रिज़ल्ट",
        "yday_title": "लॉटरी संबाद कल का रिज़ल्ट {d} – डियर 1 PM, 6 PM, 8 PM और केरल",
        "yday_desc": "कल का लॉटरी रिज़ल्ट {d} ({s}): {list}। नागालैंड स्टेट लॉटरी डियर 1 PM, 6 PM, 8 PM और केरल लॉटरी – सारे इनाम और टिकट चेक।",
        "yday_week": "पहला इनाम – पिछले 7 दिन",
        "day_title": "लॉटरी संबाद रिज़ल्ट {d} – डियर लॉटरी 1 PM, 6 PM, 8 PM",
        "day_h1": "लॉटरी संबाद रिज़ल्ट {d}",
        "draw_title": "लॉटरी संबाद {w} {t} रिज़ल्ट {d} – {g}",
        "draw_title_special": "डियर लॉटरी रिज़ल्ट {d} {t} – {g} | {s} स्टेट लॉटरी",
        "slot_title": "लॉटरी संबाद {w} रिज़ल्ट आज {d} – डियर {t}", "slot_h1": "लॉटरी संबाद {w} {t} रिज़ल्ट",
        "slot_desc": "डियर {t} ({w}) रिज़ल्ट {d}: पहला इनाम {n}। पूरी इनाम लिस्ट, टिकट चेक और पिछले 30 दिनों के सभी {w} रिज़ल्ट।",
        "slot_desc_wait": "डियर {t} ({w}) रिज़ल्ट {d} ड्रॉ के लगभग 10 मिनट बाद आता है और यह पेज अपने-आप अपडेट होता है। पिछले 30 दिनों के {w} रिज़ल्ट।",
        "slot_recent": "{t} रिज़ल्ट चार्ट – पिछले 30 दिन", "slot_all": "सभी {w} {t} रिज़ल्ट",
        "draw_desc": "{g} रिज़ल्ट {d} {t}: पहला इनाम {n}। सारे इनामों के नंबर और टिकट चेक।",
        "awaited": "रिज़ल्ट का इंतज़ार", "result_of": "{d} का रिज़ल्ट", "all_of_day": "{d} के सारे रिज़ल्ट",
        "old_title": "लॉटरी संबाद पुराने रिज़ल्ट – डियर लॉटरी सभी तारीख़ें", "old_h1": "पुराने रिज़ल्ट",
        "month_title": "लॉटरी संबाद पुराने रिज़ल्ट {m} – डियर लॉटरी 1 PM, 6 PM, 8 PM", "month_h1": "पुराने रिज़ल्ट – {m}",
        "months": "महीने के हिसाब से", "recent_days": "पिछले 31 दिन",
        "mon": "जनवरी फ़रवरी मार्च अप्रैल मई जून जुलाई अगस्त सितंबर अक्टूबर नवंबर दिसंबर".split(),
        "prev": "पिछला दिन", "next": "अगला दिन", "older": "पुराने रिज़ल्ट",
        "app": "रिज़ल्ट आते ही जानने के लिए फ़्री LottoDost ऐप लें (अलर्ट, टिकट स्कैन, नंबर हिस्ट्री)।",
        "faq": "आम सवाल", "q_time": "रिज़ल्ट कब आता है?",
        "a_time": "डियर लॉटरी का ड्रॉ रोज़ 1 PM (नागालैंड), 6 PM (सिक्किम) और 8 PM (नागालैंड) IST पर होता है। हर ड्रॉ के आमतौर पर 7–10 मिनट बाद रिज़ल्ट शीट आती है, और यह पेज अपने-आप अपडेट होता है।",
        "check_hint": "पूरा नंबर लिखें (जैसे 39C 06366, बंपर A 801021) या सिर्फ़ आख़िरी 4–6 अंक।",
        "q_check": "अपना टिकट कैसे चेक करें?", "q_claim": "इनाम कैसे लें?",
        "updated": "अपडेट {t} IST", "home": "आज", "lang": "भाषा",
        "about": "हमारे बारे में", "contact": "संपर्क", "disclaimer": "डिस्क्लेमर", "privacy": "प्राइवेसी पॉलिसी",
        "about_body": "LottoDost हर ड्रॉ के कुछ ही मिनट में डियर लॉटरी का रिज़ल्ट देता है। नंबर प्रकाशित रिज़ल्ट शीट से पढ़े जाते हैं, पूरे हैं या नहीं यह जाँचा जाता है, और नागालैंड का सरकारी गज़ट आने पर उससे मिलाया जाता है। यह सरकारी वेबसाइट नहीं है, लॉटरी संबाद या किसी लॉटरी विभाग से हमारा कोई संबंध नहीं है, और हम टिकट नहीं बेचते।",
        "contact_body": "सवाल, सुधार या कुछ हटवाने के लिए: {e}। हम आमतौर पर दो कामकाजी दिनों में जवाब दे देते हैं।",
        "notfound": "पेज नहीं मिला", "search_as": "लॉटरी संबाद, डियर लॉटरी रिज़ल्ट, नागालैंड स्टेट लॉटरी, धनकेसरी नाम से भी खोजा जाता है।",
        "wd": "सोम मंगल बुध गुरु शुक्र शनि रवि".split(), "copy": "लिंक कॉपी करें", "copied": "लिंक कॉपी हो गया", "date": "तारीख़",
        "open_page": "पूरे रिज़ल्ट का पेज खोलें", "pick_date": "तारीख़ चुनें", "go": "देखें",
        "check_hint_kl": "पूरा नंबर लिखें (जैसे MM 289707) या सिर्फ़ आख़िरी 4 अंक।", "kl_tab": "केरल", "kl_h1": "केरल लॉटरी आज का रिज़ल्ट",
        "kl_title": "केरल लॉटरी रिज़ल्ट आज {d} – {g} 3 PM",
        "kl_desc": "केरल लॉटरी रिज़ल्ट {d}: {g}, पहला इनाम {n}। सारे इनाम, टिकट चेक और सरकारी रिज़ल्ट का लिंक।",
        "kl_draw_title": "केरल लॉटरी रिज़ल्ट {d} – {g}",
        "kl_schedule": "रोज़ दोपहर 3 बजे ड्रॉ: सोमवार भाग्यतारा, मंगलवार स्त्री शक्ति, बुधवार धनलक्ष्मी, गुरुवार करुण्या प्लस, शुक्रवार सुवर्ण केरलम, शनिवार करुण्या, रविवार समृद्धि। रिज़ल्ट आमतौर पर ड्रॉ के लगभग एक घंटे बाद आता है।",
        "kl_recent": "केरल लॉटरी रिज़ल्ट चार्ट – हाल के ड्रॉ", "game": "लॉटरी",
    },
}

APP_KEYS = """first_prize tier_1 tier_cons tier_2 tier_3 tier_4 tier_5 tier_n match_last4 match_last5 match_cons
state_lottery state_nagaland state_sikkim state_punjab tag_morning tag_day tag_night verified verified_long
pdf_only open_official_pdf open_sheet special_tag check_title check_hint ticket_number ticket_invalid check_button
no_win_title no_win_body need_full_number you_won maybe_first also_matches disclaimer_body safety_title safety_body
claim_info claim_other_state source_note info_schedule_note no_draw_day upcoming_special amount_crore amount_lakh
result_coming today more_dates draw_in live result_delayed tap_full_result draw_meta draw_meta_special find_in_result
share_title share_more share_short tab_results tab_check results_subtitle watch_live no_result history_title history_hint
history_search history_none history_count history_unavailable state_kerala tier_unconfirmed kerala_source_note
open_kerala_official""".split()


def _unescape(s: str) -> str:
    return s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").strip()


def app_strings(folder: str) -> dict[str, str]:
    out = {}
    for e in ET.parse(RES / folder / "strings.xml").getroot():
        name = e.get("name")
        if e.tag == "string":
            out[name] = _unescape("".join(e.itertext()))
        elif e.tag == "plurals":
            for item in e:  # "other" is the plain key; "one" is kept as key_one for counts of 1
                q = item.get("quantity")
                if q in ("one", "other"):
                    out[name if q == "other" else name + "_one"] = _unescape("".join(item.itertext()))
    return {k: v for k, v in out.items() if k in APP_KEYS or k.removesuffix("_one") in APP_KEYS}


def fmt(s: str, *args) -> str:
    """Android-style %1$s / %1$d placeholders."""
    for i, a in enumerate(args, 1):
        s = s.replace(f"%{i}$s", str(a)).replace(f"%{i}$d", str(a))
    return s


def esc(s) -> str:
    return html.escape(str(s), quote=True)


def rupees(n: int) -> str:
    s = str(n)
    if len(s) <= 3:
        return "₹" + s
    head, tail = s[:-3], s[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    if head:
        groups.insert(0, head)
    return "₹" + ",".join(groups) + "," + tail


def time12(hhmm: str) -> str:
    h, m = (int(x) for x in hhmm.split(":"))
    return f"{h % 12 or 12}:{m:02d} {'PM' if h >= 12 else 'AM'}"


def ddmmyy(iso: str) -> str:  # "25/10/26", as people type dates into search
    y, m, d = iso.split("-")
    return f"{d}/{m}/{y[2:]}"


def short_time(hhmm: str) -> str:  # "1 PM" as people search it
    h, m = (int(x) for x in hhmm.split(":"))
    return f"{h % 12 or 12}{'' if m == 0 else f':{m:02d}'} {'PM' if h >= 12 else 'AM'}"


def ddmmyyyy(iso: str, sep: str = ".") -> str:
    y, m, d = iso.split("-")
    return f"{d}{sep}{m}{sep}{y}"


def game(name: str) -> str:
    """'DEAR RISE MONDAY WEEKLY LOTTERY' -> 'Dear Rise Monday', 'SAMRUDHI SM-77' -> 'Samrudhi SM-77'."""
    t = name.removesuffix(" WEEKLY LOTTERY").strip().title()
    return re.sub(r"\b([A-Z][a-z])-(\d+)\b", lambda m: m.group(1).upper() + "-" + m.group(2), t)


def verification(value: str) -> str:
    """Accepts the bare code or the whole <meta ... content="code"> tag people copy from the console."""
    m = re.search(r'content\s*=\s*["\']([^"\']+)', value)
    return (m.group(1) if m else value).strip()


def beacon_token(value: str) -> str:
    """Accepts the bare token or the whole Cloudflare snippet (… data-cf-beacon='{"token": "…"}' …)."""
    m = re.search(r'"token"\s*:\s*"([^"]+)"', value)
    return (m.group(1) if m else value).strip()


def draw_slug(d: dict) -> str:
    if d.get("lottery") == "maharashtra":  # several draws a day: one page each, by name
        return "maharashtra-" + re.sub(r"[^a-z0-9]+", "-", d["name"].lower().removeprefix("maharashtra ")).strip("-")
    return SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))


class Site:
    def __init__(self, data: pathlib.Path, base_url: str, now: dt.datetime | None = None,
                 link_prefix: str | None = None):
        self.data = data
        self.base = base_url.rstrip("/")
        if link_prefix is None:  # served where base_url points
            link_prefix = "/" + self.base.split("/", 3)[3] if self.base.count("/") >= 3 else ""
        # Links follow where this copy is served (a mirror at a host root uses ""); canonical URLs stay on base_url.
        self.path_prefix = link_prefix.rstrip("/")
        self.now = now or dt.datetime.now(IST)
        self.today = self.now.date().isoformat()
        self.s = {code: app_strings(folder) for code, (folder, _) in LANGS.items()}
        self.days: dict[str, list[dict]] = {}
        for f in sorted((data / "v2/days").glob("*.json")):
            day = json.loads(f.read_text())
            self.days[day["date"]] = day["draws"]
        today_feed = data / "v2/today.json"
        if today_feed.exists():  # today.json is the freshest copy of the last two days
            for d in json.loads(today_feed.read_text())["draws"]:
                lst = self.days.setdefault(d["date"], [])
                lst[:] = [x for x in lst if x["id"] != d["id"]] + [d]
        self.updated = self.now
        if today_feed.exists():
            gen = json.loads(today_feed.read_text()).get("generatedAt")
            if gen:
                self.updated = dt.datetime.fromisoformat(gen).astimezone(IST)
        self.stamp = self.updated.isoformat(timespec="minutes")   # sitemap lastmod of pages that change today
        self.index = json.loads((data / "v2/index.json").read_text()) if (data / "v2/index.json").exists() else {}
        self.dates = sorted(self.days, reverse=True)
        self.mh: dict[str, list[dict]] = {}   # Maharashtra (v2/mh/, a feed of its own)
        for f in sorted((data / "v2/mh/days").glob("*.json")):
            day = json.loads(f.read_text())
            self.mh[day["date"]] = sorted(day["draws"], key=lambda d: (d.get("time") or "99", d["name"]))
        self.mh_dates = sorted((x for x in self.mh if self.mh[x]), reverse=True)
        self.adsense = os.environ.get("ADSENSE_CLIENT", "").strip()
        self.play_url = os.environ.get("PLAY_URL", "").strip()
        # A manual unit needs its slot id (AdSense → Ads → By ad unit); without one, Auto ads alone place ads.
        self.ad_slot = os.environ.get("ADSENSE_SLOT", "").strip()
        cfg = json.loads((data / "v2/config.json").read_text()) if (data / "v2/config.json").exists() else {}
        self.live_url = cfg.get("liveDrawUrl") or "https://www.nagalandlotteries.com/livedraw/index.php"
        self.beacon = beacon_token(os.environ.get("CF_BEACON_TOKEN", ""))
        self.verify = {"google-site-verification": verification(os.environ.get("GOOGLE_SITE_VERIFICATION", "")),
                       "msvalidate.01": verification(os.environ.get("BING_SITE_VERIFICATION", ""))}
        self.pages: list[tuple[str, dict[str, str], str]] = []  # (key, {lang: path}, lastmod) for the sitemap
        self._ads = 0
        self._keys: set[str] = set()
        self.og_images: dict[str, str] = {}

    # ---- URLs ----
    def url(self, lang: str, path: str) -> str:
        """Site-absolute path ("/india/bn/result/…/") for links."""
        return f"{self.path_prefix}{LANGS[lang][1]}{path}"

    def abs(self, lang: str, path: str) -> str:
        return f"{self.base}{LANGS[lang][1]}{path}"

    @staticmethod
    def day_path(date: str) -> str:
        return f"/result/{ddmmyyyy(date, '-')}/"

    def draw_path(self, d: dict) -> str:
        return f"{self.day_path(d['date'])}{draw_slug(d)}/"

    # ---- text helpers ----
    def state(self, lang: str, st: str) -> str:
        s = self.s[lang]
        return {"Nagaland": s["state_nagaland"], "Sikkim": s["state_sikkim"], "Punjab": s["state_punjab"],
                "Kerala": s["state_kerala"]}.get(st, st)

    def tier_title(self, lang: str, rank: str) -> str:
        s = self.s[lang]
        key = {"1": "tier_1", "CONS": "tier_cons", "2": "tier_2", "3": "tier_3", "4": "tier_4", "5": "tier_5"}.get(rank)
        return s[key] if key else fmt(s["tier_n"], rank)

    def match_hint(self, lang: str, m: str) -> str:
        return {"LAST4": self.s[lang]["match_last4"], "LAST5": self.s[lang]["match_last5"],
                "CONS": self.s[lang]["match_cons"]}.get(m, "")

    def prize_short(self, lang: str, n: int) -> str:
        s = self.s[lang]
        if n >= 10_000_000 and n % 10_000_000 == 0:
            return fmt(s["amount_crore"], n // 10_000_000)
        if n >= 100_000 and n % 100_000 == 0:
            return fmt(s["amount_lakh"], n // 100_000)
        return rupees(n)

    # ---- page shell ----
    def page(self, lang: str, path: str, title: str, desc: str, body: str, alternates: bool = True,
             data: list[dict] | None = None, live: bool | str = False, crumbs: list[tuple[str, str]] | None = None,
             tab: str = "", image: str = "", modified: dt.datetime | None = None, wait: str = "") -> str:
        w, s = WEB[lang], self.s[lang]
        alt = ""
        if alternates:
            alt = "".join(f'<link rel="alternate" hreflang="{REGION[l]}" href="{esc(self.abs(l, path))}">' for l in LANGS)
            alt += f'<link rel="alternate" hreflang="x-default" href="{esc(self.abs("en", path))}">'
            if path == "/kerala/" and self.kerala_ml_ok:
                alt += f'<link rel="alternate" hreflang="ml-IN" href="{esc(self.abs("en", "/ml/kerala/"))}">'
            if path == "/maharashtra/" and self.mh_dates:
                alt += f'<link rel="alternate" hreflang="mr-IN" href="{esc(self.abs("en", "/mr/maharashtra/"))}">'
        ads_head = (f'<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client={esc(self.adsense)}" '
                    f'crossorigin="anonymous"></script>') if self.adsense else ""
        beacon = (f'<script defer src="https://static.cloudflareinsights.com/beacon.min.js" '
                  f'data-cf-beacon=\'{{"token": "{esc(self.beacon)}"}}\'></script>') if self.beacon else ""
        ld = []
        if crumbs:
            ld.append({"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
                {"@type": "ListItem", "position": i + 1, "name": n, "item": self.abs(lang, p)} for i, (n, p) in enumerate(crumbs)]})
        if path == "/":  # the name Google shows for the site, and who publishes it
            ld.append({"@context": "https://schema.org", "@type": "WebSite", "name": "LottoDost",
                       "alternateName": ["Lotto Dost", "LottoDost Lottery Result"], "url": self.abs(lang, "/"),
                       "inLanguage": REGION[lang], "publisher": {"@type": "Organization", "name": "LottoDost",
                                                                 "url": self.abs("en", "/"), "email": CONTACT,
                                                                 "logo": self.abs("en", "/icon-512.png")}})
        if modified:  # a real freshness signal: when these numbers last changed
            ld.append({"@context": "https://schema.org", "@type": "WebPage", "url": self.abs(lang, path),
                       "dateModified": modified.isoformat(timespec="minutes")})
        ld_html = "".join(f'<script type="application/ld+json">{json.dumps(x, ensure_ascii=False).replace("</", "<\\/")}</script>'
                          for x in ld)
        og_img = (f'<meta property="og:image" content="{esc(image)}"><meta property="og:image:width" content="1200">'
                  f'<meta property="og:image:height" content="630"><meta name="twitter:card" content="summary_large_image">'
                  if image else "")
        nav_lang = lang if alternates else "en"
        langs_nav = "".join(
            f'<a href="{esc(self.url(l, path))}" hreflang="{REGION[l]}" lang="{REGION[l]}"{" aria-current=\"true\"" if l == lang else ""}>'
            f'{ {"en": "EN", "bn": "বাংলা", "hi": "हिन्दी"}[l] }</a>' for l in LANGS) if alternates else ""
        check_href = "#check" if 'id="check"' in body else self.url(nav_lang, "/") + "#check"
        tabs = [("results", s["tab_results"], self.url(nav_lang, "/")), ("kerala", w["kl_tab"], self.url(nav_lang, "/kerala/")),
                ("check", s["tab_check"], check_href), ("old", w["old_h1"], self.url(nav_lang, "/old-results/"))]
        tabs_html = "".join(f'<a href="{esc(h)}"{" aria-current=\"page\"" if k == tab else ""}>{esc(n)}</a>' for k, n, h in tabs)
        data_json = json.dumps([{k: d[k] for k in ("id", "date", "time", "name", "format", "tiers") if k in d} for d in data or []],
                               ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
        live_attr = f' data-live="{esc(data[0]["date"])}"' if live and data else (f' data-live="{self.today}"' if live else "")
        live_attr = f' data-live="{esc(live)}"' if isinstance(live, str) and live else live_attr   # this day's draws
        live_attr += f' data-wait="{esc(wait)}"' if wait else ""
        self._ads = 0  # the body is built: the next page starts its own ad count
        i18n = dict(self.s[lang], copied=w["copied"], need_full_mh=MH[lang]["check_hint"])
        gl = guides.G[nav_lang]
        foot = (f'<p class="links">' + "".join(f'<a href="{self.url(nav_lang, p)}">{esc(gl["names"][k])}</a>' for k, p in guides.PATHS.items())
                + '</p>'
                f'<p class="links"><a href="{self.url("en", "/about/")}">{esc(w["about"])}</a><a href="{self.url("en", "/contact/")}">{esc(w["contact"])}</a>'
                f'<a href="{self.url("en", "/disclaimer/")}">{esc(w["disclaimer"])}</a><a href="{self.path_prefix}/privacy.html">{esc(w["privacy"])}</a></p>'
                f'<p>{esc(s["disclaimer_body"])}</p><p>{esc(s["source_note"])}</p>')
        return f"""<!doctype html>
<html lang="{REGION[lang]}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)}</title>
<meta name="description" content="{esc(desc)}">
<link rel="canonical" href="{esc(self.abs(lang, path))}">
{alt}
<meta property="og:title" content="{esc(title)}"><meta property="og:description" content="{esc(desc)}">
<meta property="og:type" content="website"><meta property="og:locale" content="{REGION[lang].replace('-', '_')}"><meta property="og:url" content="{esc(self.abs(lang, path))}">{og_img}
<meta name="theme-color" content="#b3261e">
<link rel="icon" href="{self.path_prefix}/favicon.ico" sizes="48x48"><link rel="icon" href="{self.path_prefix}/favicon.svg" type="image/svg+xml"><link rel="icon" href="{self.path_prefix}/icon-192.png" type="image/png" sizes="192x192">
<link rel="apple-touch-icon" href="{self.path_prefix}/apple-touch-icon.png"><link rel="manifest" href="{self.path_prefix}/manifest.webmanifest">
<meta name="robots" content="max-image-preview:large">
{"".join(f'<meta name="{k}" content="{esc(v)}">' for k, v in self.verify.items() if v)}
<style>{CSS}</style>
{ads_head}{ld_html}
</head>
<body{live_attr}>
<header class="top"><div class="wrap bar"><a class="brand" href="{self.url(nav_lang, '/')}">LottoDost</a>
<nav class="langs" aria-label="{esc(w['lang'])}">{langs_nav}</nav></div>
<nav class="wrap tabs">{tabs_html}</nav></header>
<main class="wrap">
{body}
</main>
<footer class="foot"><div class="wrap">{foot}</div></footer>
<script type="application/json" id="draws">{data_json}</script>
<script type="application/json" id="i18n">{json.dumps(i18n, ensure_ascii=False).replace("</", "<\\/")}</script>
<script src="{self.path_prefix}/assets/site.js" defer></script>
{beacon}
</body>
</html>
"""

    def ad(self) -> str:
        # At most 3 units per page and never inside the numbers: people come for the numbers.
        if not self.adsense or not self.ad_slot or self._ads >= 3:
            return ""
        self._ads += 1
        return (f'<div class="ad"><ins class="adsbygoogle" style="display:block" data-ad-client="{esc(self.adsense)}" '
                f'data-ad-slot="{esc(self.ad_slot)}" data-ad-format="auto" data-full-width-responsive="true"></ins>'
                f'<script>(adsbygoogle=window.adsbygoogle||[]).push({{}});</script></div>')

    # ---- blocks ----
    def stub(self, lang: str, d: dict) -> str:
        s, tag = self.s[lang], TAG.get(d["slot"])
        sub = s[tag] if tag else (WEB[lang]["kl_tab"] if d["slot"] == "KL" else s["special_tag"] if d.get("special") else "")
        return f'<div class="stub"><b>{esc(short_time(d["time"]))}</b>' + (f'<span>{esc(sub)}</span>' if sub else "") + "</div>"

    def sources(self, d: dict) -> dict[str, str]:
        src = {x["kind"]: x["url"] for x in d.get("sources", [])}
        if "lotterysambad" in src and d["date"] == self.today:
            # Lottery Sambad serves the current day's sheet only from fetchtoday.php (checked 18-10-2026).
            src["lotterysambad"] = src["lotterysambad"].replace("lotteryoldresultsdown.php", "fetchtoday.php")
        return src

    def source_buttons(self, lang: str, d: dict) -> str:
        s, src = self.s[lang], self.sources(d)
        b = [f'<a class="btn" rel="nofollow" href="{esc(src[k])}">{esc(s[t])}</a>'
             for k, t in (("lotterysambad", "open_sheet"), ("official", "open_official_pdf"),
                          ("officialSite", "open_kerala_official")) if k in src]
        return f'<p class="btns">{"".join(b)}</p>' if b else ""

    def verified(self, lang: str, d: dict) -> str:
        s = self.s[lang]
        return f' <em class="ok" title="{esc(s["verified_long"])}">✓ {esc(s["verified"])}</em>' if d.get("verified") else ""

    def tiers(self, lang: str, d: dict, ads: bool = False) -> str:
        out = []
        for i, t in enumerate(d["tiers"][1:]):
            hint = self.match_hint(lang, t["match"])
            width = max((len(n) for n in t["numbers"]), default=4)
            cls = "n4" if width <= 4 else "n5" if width <= 5 else "n6"
            nums = "".join(f"<li>{esc(n)}</li>" for n in t["numbers"])
            out.append(f'<div class="tier"><div class="th"><h3>{esc(self.tier_title(lang, t["rank"]))}</h3>'
                       f'<b>{esc(rupees(t["prize"]))}</b></div>'
                       + (f'<p class="hint">{esc(hint)}</p>' if hint else "")
                       + ("" if t.get("confirmed", True) else f'<p class="warn">{esc(self.s[lang]["tier_unconfirmed"])}</p>')
                       + f'<ul class="nums {cls}">{nums}</ul></div>')
            if ads and i == 1:
                out.append(self.ad())
        return "".join(out)

    def slip(self, lang: str, d: dict) -> str:
        """A result as a ticket: time stub, game, the 1st prize, and every other prize one tap away."""
        s = self.s[lang]
        page = self.url(lang, self.draw_path(d))
        name = (f'<a href="{page}">{esc(game(d["name"]))}</a>' if d.get("tiers")  # PDF-only draws get no page of their own
                else esc(game(d["name"])))
        head = (f'{self.stub(lang, d)}<div class="sb"><h2>{name}</h2>'
                f'<p class="meta">{esc(fmt(s["state_lottery"], self.state(lang, d.get("state", ""))))}{self.verified(lang, d)}</p>')
        if not d.get("tiers"):
            return f'<section class="slip">{head}<p>{esc(s["pdf_only"])}</p>{self.source_buttons(lang, d)}</div></section>'
        first = d["tiers"][0]
        return (f'<section class="slip" id="{esc(draw_slug(d))}">{head}'
                f'<p class="eyebrow">{esc(s["first_prize"])} · {esc(self.prize_short(lang, first["prize"]))}</p>'
                f'<p class="first">{esc(first["numbers"][0])}</p></div>'
                f'<details class="all"><summary>{esc(s["tap_full_result"])}</summary><div class="in">{self.tiers(lang, d)}'
                f'{self.source_buttons(lang, d)}<p><a class="go" href="{page}">{esc(WEB[lang]["open_page"])} →</a></p></div></details>'
                f'</section>')

    def waiting(self, lang: str, slot: str, time: str, date: str) -> str:
        s = self.s[lang]
        kerala = slot == "KL"
        st = "Kerala" if kerala else "Sikkim" if slot == "SK6PM" else "Nagaland"
        at = f"{date}T{time}:00+05:30"
        title = f'{WEB[lang]["kl_tab"]} {short_time(time)}' if kerala else f"Dear {short_time(time)}"
        live = "" if kerala else (f'<p class="btns"><a class="btn" rel="nofollow" href="{esc(self.live_url)}">'
                                  f'{esc(s["watch_live"])}</a></p>')   # Dear's official live stream only
        return (f'<section class="slip wait">{self.stub(lang, {"slot": slot, "time": time})}<div class="sb">'
                f'<h2>{esc(title)}</h2><p class="meta">{esc(fmt(s["state_lottery"], self.state(lang, st)))}</p>'
                f'<p class="when" data-at="{at}">{esc(s["result_coming"])}</p>{live}</div></section>')

    def sheet(self, lang: str, d: dict) -> str:
        """The draw page's header, shaped like the printed result sheet."""
        s = self.s[lang]
        first = d["tiers"][0]
        st = self.state(lang, d.get("state", ""))
        if d.get("price") and d.get("drawNo") and not d.get("special"):
            meta = fmt(s["draw_meta"], st, d["drawNo"], d["price"])
        elif d.get("price"):
            meta = fmt(s["draw_meta_special"], st, d["price"])
        else:
            meta = fmt(s["state_lottery"], st)
        y, m, dd = d["date"].split("-")
        when = f'{WEB[lang]["wd"][dt.date(int(y), int(m), int(dd)).weekday()]}, {ddmmyyyy(d["date"])} · {time12(d["time"])}'
        return (f'<article class="sheet"><div class="band"><h2>{esc(d["name"])}</h2><p>{esc(meta)}</p></div>'
                f'<div class="hero"><p class="eyebrow">{esc(s["first_prize"])} · {esc(rupees(first["prize"]))}</p>'
                f'<p class="first">{esc(first["numbers"][0])}</p><p class="meta">{esc(when)}{self.verified(lang, d)}</p></div></article>')

    def share(self, lang: str, d: dict) -> str:
        s = self.s[lang]
        url = self.abs(lang, self.draw_path(d))
        text = fmt(s["share_short"], game(d["name"]), f'{ddmmyyyy(d["date"])} {short_time(d["time"])}', d["tiers"][0]["numbers"][0], url)
        q = urllib.parse.quote
        links = [("wa", "WhatsApp", f"https://wa.me/?text={q(text)}"),
                 ("tg", "Telegram", f"https://t.me/share/url?url={q(url)}&text={q(text.rsplit(chr(10), 1)[0])}"),
                 ("fb", "Facebook", f"https://www.facebook.com/sharer/sharer.php?u={q(url)}"),
                 ("x", "X", f"https://x.com/intent/tweet?text={q(text.rsplit(chr(10), 1)[0])}&url={q(url)}")]
        a = "".join(f'<a class="{c}" rel="nofollow noopener" target="_blank" href="{esc(h)}">{n}</a>' for c, n, h in links)
        return (f'<section class="share" aria-label="{esc(s["share_title"])}"><p class="eyebrow">{esc(s["share_title"])}</p>'
                f'<div class="row">{a}<button type="button" class="cp" data-url="{esc(url)}">{esc(WEB[lang]["copy"])}</button>'
                f'<button type="button" class="more" hidden data-url="{esc(url)}" data-text="{esc(text)}">{esc(s["share_more"])}</button></div></section>')

    def checker(self, lang: str, title: str | None = None, kerala: bool = False, hint: str = "", example: str = "") -> str:
        s, w = self.s[lang], WEB[lang]
        if not hint:
            hint, example = (w["check_hint_kl"], "MM 289707") if kerala else (w["check_hint"], "39C 06366")
        return (f'<section class="check" id="check"><h2>{esc(title or s["check_title"])}</h2><p class="hint">{esc(hint)}</p>'
                f'<form id="checkForm" class="row"><input id="ticket" aria-label="{esc(s["ticket_number"])}" placeholder="{example}" '
                f'autocomplete="off" autocapitalize="characters" spellcheck="false" inputmode="text"><button>{esc(s["check_button"])}</button></form>'
                f'<div id="checkRes" aria-live="polite"></div></section>')

    def history(self, lang: str) -> str:
        """The app's number history on the web: every win of a number in the last 90 days (loaded on demand)."""
        s = self.s[lang]
        return (f'<section class="check" id="history"><h2>{esc(s["history_title"])}</h2>'
                f'<p class="hint">{esc(fmt(s["history_hint"], 90))}</p>'
                f'<form id="histForm" class="row"><input id="histNum" aria-label="{esc(s["ticket_number"])}" placeholder="0889" '
                f'autocomplete="off" autocapitalize="characters" spellcheck="false"><button>{esc(s["history_search"])}</button></form>'
                f'<div id="histRes" aria-live="polite"></div></section>')

    def og_image(self, out: pathlib.Path, d: dict) -> str:
        """A 1200x630 card with the 1st prize for WhatsApp/Telegram/Facebook previews. Our own rendering of
        the published numbers (facts), not a copy of anyone's sheet. Skipped when Pillow is missing."""
        if os.environ.get("SITE_OG", "1") == "0":
            return ""
        try:
            from PIL import Image, ImageDraw, ImageFont
        except ImportError:
            return ""
        fonts = [pathlib.Path(p) for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                                            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]
        if not all(f.exists() for f in fonts):
            return ""
        rel = f"og/{d['id']}.png"
        f = out / rel
        if d["date"] < (self.now.date() - dt.timedelta(days=30)).isoformat():
            return f"{self.base}/{rel}" if f.exists() else ""   # old draws (archive backfill) get no new image
        if f.exists() and d["date"] < (self.now.date() - dt.timedelta(days=2)).isoformat():
            return f"{self.base}/{rel}"  # numbers of older draws no longer change
        cache: dict = self.__dict__.setdefault("_fonts", {})

        def font(i: int, n: int):
            if (i, n) not in cache:
                cache[(i, n)] = ImageFont.truetype(str(fonts[i]), n)
            return cache[(i, n)]
        bold = lambda n: font(0, n)
        reg = lambda n: font(1, n)
        img = Image.new("RGB", (1200, 630), "#f7f2f0")
        g = ImageDraw.Draw(img)
        g.rectangle((0, 0, 1200, 150), fill="#b3261e")
        g.text((60, 38), game(d["name"]).upper(), font=bold(50), fill="#ffffff")
        g.text((60, 100), f'{fmt("%1$s State Lottery", d.get("state", ""))} · {ddmmyyyy(d["date"])} · {time12(d["time"])}',
               font=reg(30), fill="#ffdad5")
        first = d["tiers"][0]
        g.text((60, 200), f'1st Prize · {rupees(first["prize"])}', font=reg(40), fill="#6b5552")
        num, size = first["numbers"][0], 170
        while g.textlength(num, font=bold(size)) > 1080 and size > 60:
            size -= 10
        g.text((60, 260), num, font=bold(size), fill="#b3261e")
        g.text((60, 545), "LottoDost · All prizes and ticket check", font=reg(32), fill="#6b5552")
        f.parent.mkdir(parents=True, exist_ok=True)
        import io
        buf = io.BytesIO()
        img.quantize(16).save(buf, "PNG")  # flat colours: ~17 KB
        if not f.exists() or f.read_bytes() != buf.getvalue():  # unchanged numbers: leave the file (and git) alone
            f.write_bytes(buf.getvalue())
        return f"{self.base}/{rel}"

    def faq(self, lang: str) -> str:
        s, w = self.s[lang], WEB[lang]
        qa = [(w["q_time"], [w["a_time"]]), (w["q_check"], [w["check_hint"]]),
              (w["q_claim"], [s["claim_info"], s["claim_other_state"]]), (s["safety_title"], [s["safety_body"]])]
        items = "".join(f'<details><summary>{esc(q)}</summary>' + "".join(f"<p>{esc(a)}</p>" for a in ans) + "</details>"
                        for q, ans in qa)
        return f'<section class="faq"><h2>{esc(w["faq"])}</h2>{items}<p class="hint">{esc(w["search_as"])}</p></section>'

    def chips(self, lang: str, current: str) -> str:
        """Recent dates as one row of chips, like the app's date picker."""
        s, w = self.s[lang], WEB[lang]
        out = []
        if current == self.today and current not in self.dates:   # today, before its first result
            out.append(f'<a class="chip" aria-current="page" href="{self.url(lang, "/")}"><b>{esc(s["today"])}</b>{current[8:]}.{current[5:7]}</a>')
        # The dates around this one (a page of an old date stays unchanged as new days come in).
        i = self.dates.index(current) if current in self.dates else 0
        lo = max(0, min(i - 3, len(self.dates) - 7))
        for x in self.dates[lo:lo + 7 - len(out)]:
            y, m, d = x.split("-")
            label = s["today"] if x == self.today else w["wd"][dt.date(int(y), int(m), int(d)).weekday()]
            cur = ' aria-current="page"' if x == current else ""
            out.append(f'<a class="chip"{cur} href="{self.url(lang, self.day_path(x))}"><b>{esc(label)}</b>{d}.{m}</a>')
        out.append(f'<a class="chip" href="{self.url(lang, "/old-results/")}"><b>→</b>{esc(s["more_dates"])}</a>')
        return f'<nav class="chips" aria-label="{esc(w["older"])}">{"".join(out)}</nav>'

    def ordered(self, draws: list[dict]) -> list[dict]:
        order = {"NL1PM": 0, "SK6PM": 1, "NL8PM": 2, "NLLAXMI": 3}
        return sorted(draws, key=lambda d: (d["time"], order.get(d["slot"], 9)))

    def day_cards(self, lang: str, date: str) -> str:
        draws = self.ordered(self.days.get(date, []))
        no_dear = date in self.index.get("noDraw", [])   # the Nagaland calendar; Kerala draws regardless
        parts = [(d["time"], self.slip(lang, d)) for d in draws]
        have = {d["slot"] for d in draws}
        for slot, time, _ in WEEKLY + [("KL", "15:00", "kerala")]:  # today's empty slots: a result is coming
            if date == self.today and slot not in have and not (no_dear and slot != "KL"):
                parts.append((time, self.waiting(lang, slot, time, date)))
        banner = f'<p class="banner">{esc(self.s[lang]["no_draw_day"])}</p>' if no_dear else ""
        return banner + "".join(h for _, h in sorted(parts, key=lambda p: p[0]))

    def write(self, out: pathlib.Path, lang: str, path: str, content: str, key: str | None = None, lastmod: str = ""):
        f = out / (LANGS[lang][1].lstrip("/")) / path.strip("/") / "index.html" if path != "/404" else out / "404.html"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(content, encoding="utf-8")
        if key and key not in self._keys:  # one sitemap entry per page, listing all its languages
            self._keys.add(key)
            self.pages.append((key, {l: self.abs(l, path) for l in LANGS}, lastmod))

    # ---- pages ----
    def build(self, out: pathlib.Path) -> int:
        flipped = self.today in self.days or self.now.time() >= DAY_FLIP or not self.dates
        latest = self.today if flipped else self.dates[0]
        for lang in LANGS:
            w, s = WEB[lang], self.s[lang]
            # home: today's results, refreshed by the page itself while a draw is due
            d0 = ddmmyyyy(latest)
            got = {d["slot"]: d for d in self.days.get(latest, []) if d.get("tiers")}
            summary = ", ".join(f'{short_time(t)} {got[sl]["tiers"][0]["numbers"][0] if sl in got else w["awaited"]}'
                                for sl, t, _ in WEEKLY)
            up = next((u for u in self.index.get("upcoming", []) if u["date"] >= self.today), None)
            last = ([d for d in self.ordered(self.days.get(latest, [])) if d.get("tiers")] or [None])[-1]
            yd = self.yesterday()
            # Turned to today before its first result: yesterday's results stay below the draws to come.
            before = ("" if got or not yd else f'<h2 class="sub">{esc(w["result_of"].format(d=ddmmyyyy(yd)))}</h2>'
                      + "".join(self.slip(lang, d) for d in self.ordered(self.days[yd]) if d.get("tiers")))
            if not last and yd:   # the share picture stays yesterday's newest result until today's first one
                last = ([d for d in self.ordered(self.days[yd]) if d.get("tiers")] or [None])[-1]
            if last and last["id"] not in self.og_images:  # the home page's share picture: the newest result
                self.og_images[last["id"]] = self.og_image(out, last)
            body = (f'<div class="intro"><h1>{esc(w["home_h1"])} <span class="d">{esc(d0)}</span></h1>'
                    f'<p class="hint">{esc(s["results_subtitle"])} · {esc(w["updated"].format(t=self.updated.strftime("%d.%m %H:%M")))}</p></div>'
                    + self.checker(lang) + self.chips(lang, latest) + self.hub_links(lang)
                    + (f'<p class="banner">{esc(fmt(s["upcoming_special"], up["name"], ddmmyyyy(up["date"])))}</p>' if up else "")
                    + self.day_cards(lang, latest) + before + self.ad() + self.other_states(lang) + self.history(lang) + self.app_promo(lang)
                    + self.guide_links(lang) + self.faq(lang))
            self.write(out, lang, "/", self.page(lang, "/", w["home_title"].format(d=d0), w["home_desc"].format(d=d0, s=ddmmyy(latest), list=summary),
                                                 body, data=list(got.values()) or ([d for d in self.days[yd] if d.get("tiers")] if before else []),
                                                 live=self.today,   # the checker reads yesterday's until today's 1st result, then reloads
                                                 crumbs=[(w["home"], "/")], tab="results", modified=self.updated,
                                                 image=self.og_images.get(last["id"], "") if last else ""), key="home", lastmod=self.stamp)
            # one page per day and per draw
            for i, date in enumerate(self.dates):
                dd = ddmmyyyy(date)
                nav = []
                if i + 1 < len(self.dates):
                    nav.append(f'<a class="btn" href="{self.url(lang, self.day_path(self.dates[i + 1]))}">← {esc(w["prev"])}</a>')
                if i > 0:
                    nav.append(f'<a class="btn" href="{self.url(lang, self.day_path(self.dates[i - 1]))}">{esc(w["next"])} →</a>')
                draws = [d for d in self.ordered(self.days[date]) if d.get("tiers")]
                body = (f'<div class="intro"><h1>{esc(w["day_h1"].format(d=dd))}</h1></div>' + self.checker(lang) + self.chips(lang, date)
                        + self.day_cards(lang, date) + self.ad() + f'<p class="pn">{"".join(nav)}</p>')
                self.write(out, lang, self.day_path(date),
                           self.page(lang, self.day_path(date), w["day_title"].format(d=dd),
                                     w["day_desc"].format(d=dd, s=ddmmyy(date), list=", ".join(f'{short_time(d["time"])} {d["tiers"][0]["numbers"][0]}'
                                                                                  for d in draws)) if draws else w["day_title"].format(d=dd),
                                     body, data=draws, live=date == self.today,
                                     crumbs=[(w["home"], "/"), (dd, self.day_path(date))], tab="results",
                                     modified=self.updated if date == self.today else None), key=f"day:{date}",
                       lastmod=self.stamp if date == self.today else date)
                for d in draws:  # PDF-only draws have nothing to read: the day page links the PDF
                    if d["id"] not in self.og_images:  # one image per draw, shared by the three languages
                        self.og_images[d["id"]] = self.og_image(out, d)
                    image = self.og_images[d["id"]]
                    t, g, first = short_time(d["time"]), game(d["name"]), d["tiers"][0]["numbers"][0]
                    kl = d["slot"] == "KL"
                    title = self.draw_title(lang, d)
                    desc = w["kl_desc"].format(d=dd, g=g, n=first) if kl else w["draw_desc"].format(g=g, d=dd, t=t, n=first)
                    sibs = "".join(f'<a class="chip"{" aria-current=\"page\"" if x is d else ""} href="{self.url(lang, self.draw_path(x))}">'
                                   f'<b>{esc(short_time(x["time"]))}</b>{esc(game(x["name"]).replace("Dear ", ""))}</a>' for x in draws)
                    body = (f'<div class="intro"><h1 class="sm">{esc(title)}</h1></div>' + self.sheet(lang, d)
                            + self.checker(lang, s["find_in_result"], kerala=kl) + self.share(lang, d)
                            + (f'<p class="hint">{esc(s["kerala_source_note"])}</p>' if kl else "")
                            + f'<section class="card">{self.tiers(lang, d, ads=True)}</section>' + self.source_buttons(lang, d)
                            + f'<nav class="chips" aria-label="{esc(w["all_of_day"].format(d=dd))}">{sibs}</nav>'
                            + f'<p class="pn"><a class="btn" href="{self.url(lang, self.day_path(date))}">{esc(w["all_of_day"].format(d=dd))}</a>'
                            + (f'<a class="btn" href="{self.url(lang, HUB[d["slot"]])}">{esc(w["slot_all"].format(w=s[TAG[d["slot"]]], t=t))}</a>'
                               if d["slot"] in HUB else "") + '</p>'
                            + self.app_promo(lang) + self.guide_links(lang))
                    self.write(out, lang, self.draw_path(d),
                               self.page(lang, self.draw_path(d), title, desc, body,
                                         data=[d], crumbs=[(w["home"], "/"), (dd, self.day_path(date)), (t, self.draw_path(d))],
                                         tab="kerala" if kl else "results", image=image),
                               key=f"draw:{d['id']}", lastmod=self.stamp if date == self.today else date)
            self.kerala_hub(out, lang)
            self.mh_pages(out, lang)
            self.mh_game_hubs(out, lang)
            self.guide_pages(out, lang)
            for slot, time, _ in WEEKLY:
                self.slot_hub(out, lang, slot, time)
            self.yesterday_hub(out, lang)
            # archive: every date with its three 1st prizes, so the right day is easy to spot
            months = sorted({x[:7] for x in self.dates}, reverse=True)
            self.write(out, lang, "/old-results/", self.page(lang, "/old-results/", w["old_title"], w["old_title"],
                                                             f'<div class="intro"><h1>{esc(w["old_h1"])}</h1></div>{self.date_form(lang)}'
                                                             f'{self.month_links(lang, months)}<h2 class="sub">{esc(w["recent_days"])}</h2>'
                                                             f'{self.archive(lang, self.dates[:31])}{self.history(lang)}',
                                                             crumbs=[(w["home"], "/"), (w["old_h1"], "/old-results/")], tab="old"),
                       key="old", lastmod=self.stamp)
            # one page per month, so every old date is two clicks from anywhere and searchable by month
            for i, ym in enumerate(months):
                path, m = f"/old-results/{ym}/", self.month_name(lang, ym)
                nav = []
                if i + 1 < len(months):
                    nav.append(f'<a class="btn" href="{self.url(lang, f"/old-results/{months[i + 1]}/")}">← {esc(self.month_name(lang, months[i + 1]))}</a>')
                if i > 0:
                    nav.append(f'<a class="btn" href="{self.url(lang, f"/old-results/{months[i - 1]}/")}">{esc(self.month_name(lang, months[i - 1]))} →</a>')
                body = (f'<div class="intro"><h1>{esc(w["month_h1"].format(m=m))}</h1></div>'
                        + self.archive(lang, [x for x in self.dates if x.startswith(ym)]) + f'<p class="pn">{"".join(nav)}</p>'
                        + self.month_links(lang, months, ym))
                last = max(x for x in self.dates if x.startswith(ym))
                self.write(out, lang, path, self.page(lang, path, w["month_title"].format(m=m), w["month_title"].format(m=m), body,
                                                      crumbs=[(w["home"], "/"), (w["old_h1"], "/old-results/"), (m, path)], tab="old"),
                           key=f"month:{ym}", lastmod=self.stamp if last == self.dates[0] else last)
        # English-only trust pages (required by ad networks; also what visitors look for)
        w = WEB["en"]
        for path, title, text in (("/about/", w["about"], w["about_body"]),
                                  ("/contact/", w["contact"], w["contact_body"].format(e=CONTACT)),
                                  ("/disclaimer/", w["disclaimer"], self.s["en"]["disclaimer_body"] + " " + self.s["en"]["safety_body"])):
            self.write(out, "en", path, self.page("en", path, f"{title} – LottoDost", text,
                                                  f'<div class="intro"><h1>{esc(title)}</h1></div><section class="card prose"><p>{esc(text)}</p></section>',
                                                  alternates=False), key=f"page:{path}")
        self.write(out, "en", "/404", self.page("en", "/404/", w["notfound"], w["notfound"],
                                               f'<div class="intro"><h1>{esc(w["notfound"])}</h1></div>'
                                               f'<p class="pn"><a class="btn" href="{self.url("en", "/")}">{esc(self.s["en"]["tab_results"])} →</a></p>',
                                               alternates=False))
        self.kerala_ml(out)
        self.maharashtra_mr(out)
        self.share_kit(out)
        self.assets(out)
        return len(self.pages)

    def draw_title(self, lang: str, d: dict) -> str:
        w, s = WEB[lang], self.s[lang]
        dd, t, g = ddmmyyyy(d["date"]), short_time(d["time"]), game(d["name"])
        if d["slot"] == "KL":
            return w["kl_draw_title"].format(d=dd, g=g)
        if d["slot"] in TAG:
            return w["draw_title"].format(w=s[TAG[d["slot"]]], t=t, d=dd, g=g)
        return w["draw_title_special"].format(s=self.state(lang, d.get("state", "")), d=dd, t=t, g=g)

    def share_kit(self, out: pathlib.Path) -> None:
        """/share/: the newest results as ready-made posts for a WhatsApp channel, Facebook page or
        YouTube community tab: picture + caption + link, shared from the phone in one tap.
        Not for search: noindex, not in the sitemap, linked from nowhere."""
        recent = [x for x in self.dates[:2]]
        draws = [d for x in recent for d in reversed(self.ordered(self.days[x])) if d.get("tiers")]
        cards = []
        for i, d in enumerate(draws):
            first = d["tiers"][0]
            link = self.abs("en", self.draw_path(d))
            tags = "#KeralaLottery #KeralaLotteryResult" if d["slot"] == "KL" else \
                "#LotterySambad #DearLottery #NagalandStateLottery"
            text = (f"🎟️ {self.draw_title('en', d)}\n"
                    f"🏆 1st Prize {self.prize_short('en', first['prize'])}: {first['numbers'][0]}\n"
                    f"✅ Full result + ticket checker / पूरा रिजल्ट और टिकट चेक 👇\n{link}\n\n{tags}")
            img = self.og_images.get(d["id"], "")
            pic = (f'<img src="{esc(img)}" alt="" width="600" height="315">'
                   f'<a class="b" href="{esc(img)}" download>⬇ Image</a>') if img else ""
            cards.append(f'<section><h2>{esc(self.draw_title("en", d))}</h2>{pic}'
                         f'<textarea id="t{i}" rows="7" readonly>{esc(text)}</textarea>'
                         f'<p><button data-i="{i}" data-img="{esc(img)}" class="sh">📤 Share (image + text)</button>'
                         f'<button data-i="{i}" class="cp">📋 Copy text</button>'
                         f'<a class="b" href="https://wa.me/?text={urllib.parse.quote(text)}">WhatsApp</a></p></section>')
        js = ("function T(i){return document.getElementById('t'+i).value}"
              "function C(i,b){navigator.clipboard.writeText(T(i)).then(function(){b.textContent='✔ Copied'})}"
              "document.querySelectorAll('.cp').forEach(function(b){b.onclick=function(){C(b.dataset.i,b)}});"
              "document.querySelectorAll('.sh').forEach(function(b){b.onclick=function(){var t=T(b.dataset.i);"
              "if(!navigator.share){C(b.dataset.i,b);return}"
              "(b.dataset.img?fetch(b.dataset.img).then(function(r){return r.blob()}).then(function(x){"
              "var f=new File([x],'result.png',{type:'image/png'});"
              "return navigator.canShare&&navigator.canShare({files:[f]})?navigator.share({files:[f],text:t}):navigator.share({text:t})})"
              ":navigator.share({text:t})).catch(function(){})}});")
        page = ('<!doctype html><html lang="en"><head><meta charset="utf-8">'
                '<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow">'
                '<title>LottoDost – share kit</title><style>body{font:16px/1.4 system-ui,sans-serif;margin:0 auto;max-width:640px;'
                'padding:12px 16px;background:#f7f2f0;color:#1d1b1a}section{background:#fff;border-radius:12px;padding:12px;margin:12px 0}'
                'h2{font-size:17px;margin:0 0 8px}img{width:100%;height:auto;border-radius:8px}textarea{width:100%;box-sizing:border-box;'
                'font:14px/1.4 system-ui,sans-serif;margin:8px 0}button,.b{display:inline-block;margin:4px 6px 0 0;padding:10px 14px;'
                'border-radius:8px;border:0;background:#b3261e;color:#fff;font-size:15px;text-decoration:none}.cp,.b{background:#5c4d4a}</style>'
                '</head><body><h1>Share kit</h1><p>Newest results first. On a phone, <b>Share</b> sends the picture with '
                'the text to WhatsApp (channel or group), Facebook or YouTube. Updated after every draw.</p>'
                + "".join(cards) + f'<script>{js}</script></body></html>')
        f = out / "share" / "index.html"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(page, encoding="utf-8")

    # ---- Maharashtra (other state lotteries) ----
    def mh_game(self, lang: str, d: dict) -> str:
        """'Maharashtra Gajlaxmi Ravi' + weekly -> 'Gajlaxmi Ravi Weekly' (English names, as printed)."""
        name = d["name"].removeprefix("Maharashtra ")
        return f'{name} {MH[lang].get(d.get("kind", ""), "")}'.strip()

    def mh_title(self, lang: str, d: dict) -> str:
        m = MH[lang]
        return m["draw_title"].format(g=self.mh_game(lang, d), d=ddmmyyyy(d["date"]),
                                      n=m["draw_no"].format(n=d["drawNo"]) if d.get("drawNo") else "")

    def mh_buttons(self, lang: str, d: dict) -> str:
        src = {x["kind"]: x["url"] for x in d.get("sources", [])}
        b = [f'<a class="btn" rel="nofollow" href="{esc(src[k])}">{esc(MH[lang][t])}</a>'
             for k, t in (("officialPdf", "open_pdf"), ("officialSite", "open_site")) if k in src]
        return f'<p class="btns">{"".join(b)}</p>'

    def mh_card(self, lang: str, d: dict) -> str:
        s, m = self.s[lang], MH[lang]
        stub = f'<div class="stub"><b>{esc(short_time(d["time"]) if d.get("time") else "MH")}</b><span>{esc(m["tab"])}</span></div>'
        ok = d.get("status") == "OK" and d.get("tiers")
        page = self.url(lang, self.draw_path(d))
        name = f'<a href="{page}">{esc(self.mh_game(lang, d))}</a>' if ok else esc(self.mh_game(lang, d))
        head = (f'{stub}<div class="sb"><h2>{name}</h2><p class="meta">{esc(fmt(s["state_lottery"], m["tab"]))}'
                + (f' · <span lang="mr">{esc(d["nameLocal"])}</span>' if d.get("nameLocal") else "") + '</p>')
        if not ok:
            return f'<section class="slip">{head}<p>{esc(s["pdf_only"])}</p>{self.mh_buttons(lang, d)}</div></section>'
        first = d["tiers"][0]
        return (f'<section class="slip" id="{esc(draw_slug(d))}">{head}'
                f'<p class="eyebrow">{esc(s["first_prize"])} · {esc(self.prize_short(lang, first["prize"]))}</p>'
                f'<p class="first">{esc(first["numbers"][0])}</p></div>'
                f'<details class="all"><summary>{esc(s["tap_full_result"])}</summary><div class="in">{self.tiers(lang, d)}'
                f'{self.mh_buttons(lang, d)}<p><a class="go" href="{page}">{esc(WEB[lang]["open_page"])} →</a></p></div></details>'
                f'</section>')

    def other_states(self, lang: str) -> str:
        """Lotteries of other states, in a row of their own below the Sambad/Kerala results."""
        if not self.mh_dates:
            return ""
        m = MH[lang]
        return (f'<h2 class="sub">{esc(m["other"])}</h2><nav class="chips wrapchips" aria-label="{esc(m["other"])}">'
                f'<a class="chip" href="{self.url(lang, "/maharashtra/")}"><b>4 PM</b>{esc(m["tab"])}</a></nav>')

    def mh_pages(self, out: pathlib.Path, lang: str) -> None:
        """/maharashtra/ (the newest day's draws and a 30-draw chart) and one page per draw."""
        if not self.mh_dates:
            return
        s, w, m = self.s[lang], WEB[lang], MH[lang]
        latest = self.mh_dates[0]
        draws = self.mh[latest]
        done = [d for x in self.mh_dates for d in self.mh[x] if d.get("status") == "OK" and d.get("tiers")]
        d0 = ddmmyyyy(latest)
        listing = ", ".join(f'{self.mh_game("en" if lang == "en" else lang, d)} {d["tiers"][0]["numbers"][0]}'
                            for d in draws if d.get("status") == "OK" and d.get("tiers"))
        rows = "".join(
            f'<tr><th scope="row"><a href="{self.url(lang, self.draw_path(d))}">{d["date"][8:]}.{d["date"][5:7]}</a>'
            f'<small>{esc(w["wd"][dt.date.fromisoformat(d["date"]).weekday()])} {d["date"][:4]}</small></th>'
            f'<td>{esc(self.mh_game(lang, d))}</td><td><a href="{self.url(lang, self.draw_path(d))}">{esc(d["tiers"][0]["numbers"][0])}</a></td></tr>'
            for d in done[:30])
        table = (f'<h2 class="sub">{esc(m["recent"])}</h2><div class="tbl"><table><thead><tr><th>{esc(w["date"])}</th>'
                 f'<th>{esc(w["game"])}</th><th>{esc(s["first_prize"])}</th></tr></thead><tbody>{rows}</tbody></table></div>') if rows else ""
        first_ok = next((d for d in draws if d.get("status") == "OK" and d.get("tiers")), None)
        # Without numbers the page says only what it has: the official sheets (no checker, no prize list).
        earlier = "".join(f'<h2 class="sub">{esc(ddmmyyyy(x))}</h2>' + "".join(self.mh_card(lang, d) for d in self.mh[x])
                          for x in self.mh_dates[1:3])
        body = (f'<div class="intro"><h1>{esc(m["h1"])} <span class="d">{esc(d0)}</span></h1>'
                f'<p class="hint">{esc(m["schedule"])}</p></div>'
                + (self.checker(lang, hint=m["check_hint"], example="GL-00 2142") if done else "")
                + "".join(self.mh_card(lang, d) for d in draws) + self.ad() + table + earlier
                + f'<p class="hint">{esc(m["ocr_note"] if done else m["pdf_note"])}</p>'
                + f'<p><a class="go" href="{self.url("en", "/mr/maharashtra/")}" lang="mr" hreflang="mr-IN">{esc(m["marathi"])}</a></p>'
                + self.mh_game_links(lang) + self.hub_links(lang))
        names = ", ".join(self.mh_game(lang, d) for d in draws)
        if first_ok:
            title = m["title"].format(d=d0, g=self.mh_game(lang, first_ok))
            desc = m["desc"].format(d=d0, list=listing)
        else:
            title = m["title_pdf"].format(d=d0, g=self.mh_game(lang, draws[0]))
            desc = m["desc_pdf"].format(d=d0, list=names)
        self.write(out, lang, "/maharashtra/", self.page(
            lang, "/maharashtra/", title, desc, body, data=[d for d in draws if d.get("tiers")],
            image=self.og_images.get(first_ok["id"], "") if first_ok else "",
            crumbs=[(w["home"], "/"), (m["tab"], "/maharashtra/")], modified=self.updated if latest == self.today else None),
            key="maharashtra", lastmod=self.stamp)
        for d in done:
            if d["id"] not in self.og_images and d.get("time"):
                self.og_images[d["id"]] = self.og_image(out, d)
            dd = ddmmyyyy(d["date"])
            sibs = "".join(f'<a class="chip"{" aria-current=\"page\"" if x is d else ""} href="{self.url(lang, self.draw_path(x))}">'
                           f'<b>{esc(short_time(x["time"]) if x.get("time") else "MH")}</b>{esc(self.mh_game(lang, x))}</a>'
                           for x in self.mh[d["date"]] if x.get("status") == "OK" and x.get("tiers"))
            when = f'{w["wd"][dt.date.fromisoformat(d["date"]).weekday()]}, {dd}' + (f' · {time12(d["time"])}' if d.get("time") else "")
            first = d["tiers"][0]
            sheet = (f'<article class="sheet"><div class="band"><h2>{esc(d["name"])} {esc(m.get(d.get("kind", ""), ""))}</h2>'
                     f'<p>{esc(fmt(s["state_lottery"], m["tab"]))}' + (f' · <span lang="mr">{esc(d["nameLocal"])}</span>' if d.get("nameLocal") else "")
                     + f'</p></div><div class="hero"><p class="eyebrow">{esc(s["first_prize"])} · {esc(rupees(first["prize"]))}</p>'
                     f'<p class="first">{esc(first["numbers"][0])}</p><p class="meta">{esc(when)}</p></div></article>')
            title = self.mh_title(lang, d)
            body = (f'<div class="intro"><h1 class="sm">{esc(title)}</h1></div>' + sheet
                    + self.checker(lang, hint=m["check_hint"], example=first["numbers"][0]) + self.tiers(lang, d, ads=True)
                    + self.mh_buttons(lang, d) + f'<p class="hint">{esc(m["ocr_note"])}</p>'
                    + f'<nav class="chips wrapchips">{sibs}</nav>'
                    + f'<p class="pn"><a class="btn" href="{self.url(lang, self.mh_game_path(d))}">{esc(m["game_past"].format(g=d["name"].removeprefix("Maharashtra ")))}</a>'
                    + f'<a class="btn" href="{self.url(lang, "/maharashtra/")}">{esc(m["all"])}</a></p>')
            self.write(out, lang, self.draw_path(d), self.page(
                lang, self.draw_path(d), title, m["draw_desc"].format(g=self.mh_game(lang, d), d=dd, f=first["numbers"][0]), body,
                data=[d], image=self.og_images.get(d["id"], ""),
                crumbs=[(w["home"], "/"), (m["tab"], "/maharashtra/"), (dd, self.draw_path(d))]),
                key=f"draw:{d['id']}", lastmod=self.stamp if d["date"] == self.today else d["date"])

    @staticmethod
    def mh_game_path(d: dict) -> str:
        return "/maharashtra/" + draw_slug(d).removeprefix("maharashtra-") + "/"

    def mh_games(self) -> dict[str, list[dict]]:
        """Every Maharashtra draw by its game page, newest first (a weekly game draws once a week)."""
        out: dict[str, list[dict]] = {}
        for x in self.mh_dates:
            for d in self.mh[x]:
                out.setdefault(self.mh_game_path(d), []).append(d)
        return out

    def mh_game_links(self, lang: str, current: str = "") -> str:
        games = self.mh_games()
        if not games:
            return ""
        m = MH[lang]
        a = "".join(f'<a class="chip"{" aria-current=\"page\"" if p == current else ""} href="{self.url(lang, p)}">'
                    f'<b>{esc(MH[lang].get(ds[0].get("kind", ""), "MH"))}</b>{esc(ds[0]["name"].removeprefix("Maharashtra "))}</a>'
                    for p, ds in sorted(games.items(), key=lambda kv: kv[1][0]["name"]))
        return f'<h2 class="sub">{esc(m["games"])}</h2><nav class="chips wrapchips" aria-label="{esc(m["games"])}">{a}</nav>'

    def mh_game_hubs(self, out: pathlib.Path, lang: str) -> None:
        """/maharashtra/<game>/: one page per draw name ("gajlaxmi ravi lottery result" is searched by
        name), with its newest draw and every earlier one; it updates in place each week."""
        s, w, m = self.s[lang], WEB[lang], MH[lang]
        for path, draws in self.mh_games().items():
            latest = draws[0]
            name = latest["name"].removeprefix("Maharashtra ")
            kind = m.get(latest.get("kind", ""), "")
            d0 = ddmmyyyy(latest["date"])
            days = {dt.date.fromisoformat(d["date"]).weekday() for d in draws}
            at = {"en": " at {t}", "bn": " · {t}", "hi": " · {t}"}[lang].format(t=time12(latest["time"])) if latest.get("time") else ""
            when = (m["game_when"].format(k=kind, wd=WD_FULL[lang][days.pop()], t=at)
                    if latest.get("kind") == "weekly" and len(days) == 1 else m["game_when_any"].format(k=kind or m["tab"]))
            ok = [d for d in draws if d.get("status") == "OK" and d.get("tiers")]
            rows = "".join(
                f'<tr><th scope="row">{d["date"][8:]}.{d["date"][5:7]}<small>{esc(w["wd"][dt.date.fromisoformat(d["date"]).weekday()])} '
                f'{d["date"][:4]}</small></th><td>{esc(str(d.get("drawNo") or ""))}</td><td>'
                + (f'<a href="{self.url(lang, self.draw_path(d))}">{esc(d["tiers"][0]["numbers"][0])}</a>' if d in ok else
                   f'<a rel="nofollow" href="{esc(next((x["url"] for x in d.get("sources", []) if x["kind"] == "officialPdf"), ""))}">'
                   f'{esc(m["sheet_only"])} (PDF)</a>') + '</td></tr>'
                for d in draws[1:])
            past = (f'<h2 class="sub">{esc(m["game_past"].format(g=name))}</h2><div class="tbl"><table><thead><tr>'
                    f'<th>{esc(w["date"])}</th><th>#</th><th>{esc(s["first_prize"])}</th></tr></thead><tbody>{rows}</tbody></table></div>'
                    if rows else "")
            body = (f'<div class="intro"><h1>{esc(m["game_h1"].format(g=name))} <span class="d">{esc(d0)}</span></h1>'
                    f'<p class="hint">{esc(when)}</p></div>'
                    + (self.checker(lang, hint=m["check_hint"], example=ok[0]["tiers"][0]["numbers"][0]) if ok else "")
                    + self.mh_card(lang, latest) + self.ad() + past
                    + f'<p class="hint">{esc(m["ocr_note"] if ok else m["pdf_note"])}</p>'
                    + self.mh_game_links(lang, path)
                    + f'<p class="pn"><a class="btn" href="{self.url(lang, "/maharashtra/")}">{esc(m["all"])}</a></p>')
            first = m["first_is"].format(n=latest["tiers"][0]["numbers"][0]) if latest in ok else ""
            self.write(out, lang, path, self.page(
                lang, path, m["game_title"].format(g=name, d=d0, k=kind or m["tab"]),
                m["game_desc"].format(g=name, d=d0, f=first),
                body, data=ok[:5], image=self.og_images.get(latest["id"], ""),
                crumbs=[(w["home"], "/"), (m["tab"], "/maharashtra/"), (name, path)],
                modified=self.updated if latest["date"] == self.today else None),
                key=f"mh-game:{path}", lastmod=self.stamp if latest["date"] == self.today else latest["date"])

    def maharashtra_mr(self, out: pathlib.Path) -> None:
        """/mr/maharashtra/: the Maharashtra results in Marathi, as Maharashtra searches for them
        (महाराष्ट्र राज्य लॉटरी निकाल), with the draw names as the lottery prints them."""
        if not self.mh_dates:
            return
        latest = self.mh_dates[0]
        d0 = ddmmyyyy(latest)
        def pdf(d):
            return next((x["url"] for x in d.get("sources", []) if x["kind"] == "officialPdf"), "")
        def mr_name(d):
            return d.get("nameLocal") or d["name"]
        cards = []
        for d in self.mh[latest]:
            ok = d.get("status") == "OK" and d.get("tiers")
            tiers = ""
            if ok:
                for t in d["tiers"]:
                    nums = "".join(f"<li>{esc(n)}</li>" for n in t["numbers"])
                    width = max((len(n) for n in t["numbers"]), default=4)
                    tiers += (f'<div class="tier"><div class="th"><h3>{esc(MR["tiers"].get(t["rank"], t["rank"]))}</h3>'
                              f'<b>{esc(rupees(t["prize"]))}</b></div><ul class="nums {"n4" if width <= 4 else "n5" if width <= 5 else "n6"}">{nums}</ul></div>')
            head = (f'<h2>{esc(mr_name(d))}</h2><p class="meta">{esc(MR["wd"][dt.date.fromisoformat(d["date"]).weekday()])}, {esc(ddmmyyyy(d["date"]))}'
                    + (f' · {esc(time12(d["time"]))}' if d.get("time") else "") + (f' · सोडत क्रमांक {d["drawNo"]}' if d.get("drawNo") else "") + '</p>')
            first = (f'<p class="eyebrow">{esc(MR["first"])} · {esc(rupees(d["tiers"][0]["prize"]))}</p><p class="first">{esc(d["tiers"][0]["numbers"][0])}</p>'
                     if ok else "")
            btn = f'<p class="btns"><a class="btn" rel="nofollow" href="{esc(pdf(d))}">{esc(MR["pdf"])}</a></p>' if pdf(d) else ""
            cards.append(f'<section class="slip"><div class="stub"><b>MH</b><span>महाराष्ट्र</span></div><div class="sb">{head}{first}{btn}'
                         + (f'<details class="all"><summary>सर्व बक्षिसे</summary><div class="in">{tiers}</div></details>' if tiers else "")
                         + '</div></section>')
        rows = "".join(
            f'<tr><th scope="row">{d["date"][8:]}.{d["date"][5:7]}<small>{esc(MR["wd"][dt.date.fromisoformat(d["date"]).weekday()])} {d["date"][:4]}</small></th>'
            f'<td>{esc(mr_name(d).removeprefix("महाराष्ट्र "))}</td><td>'
            + (f'<a href="{self.url("en", self.draw_path(d))}">{esc(d["tiers"][0]["numbers"][0])}</a>' if d.get("status") == "OK" and d.get("tiers")
               else f'<a rel="nofollow" href="{esc(pdf(d))}">PDF</a>') + '</td></tr>'
            for x in self.mh_dates[1:8] for d in self.mh[x])
        table = (f'<h2 class="sub">{esc(MR["recent"])}</h2><div class="tbl"><table><thead><tr><th>{esc(MR["date"])}</th>'
                 f'<th>{esc(MR["game"])}</th><th>{esc(MR["first"])}</th></tr></thead><tbody>{rows}</tbody></table></div>') if rows else ""
        done = any(d.get("status") == "OK" and d.get("tiers") for x in self.mh_dates for d in self.mh[x])
        body = (f'<div class="intro" lang="mr"><h1>{esc(MR["h1"])} <span class="d">{esc(d0)}</span></h1>'
                f'<p class="hint">{esc(MR["schedule"])}</p></div><div lang="mr">'
                + (f'<p class="pn"><a class="btn" href="{self.url("en", "/maharashtra/")}#check">{esc(MR["check"])}</a></p>' if done else "")
                + "".join(cards) + self.ad() + table + f'<p class="hint">{esc(MR["note_ocr"] if done else MR["note_pdf"])}</p></div>'
                + self.mh_game_links("en"))
        draws = self.mh[latest]
        listing = ", ".join(mr_name(d).removeprefix("महाराष्ट्र ") + (f' {d["tiers"][0]["numbers"][0]}' if d.get("status") == "OK" and d.get("tiers") else "")
                            for d in draws)
        g = mr_name(draws[0]).removeprefix("महाराष्ट्र ")
        html_ = self.page("en", "/mr/maharashtra/", MR["title"].format(d=d0, g=g), MR["desc"].format(d=d0, list=listing), body,
                          alternates=False, data=[d for d in draws if d.get("tiers")],
                          crumbs=[("LottoDost", "/"), ("Maharashtra", "/maharashtra/"), ("मराठी", "/mr/maharashtra/")],
                          modified=self.updated if latest == self.today else None)
        alts = (f'<link rel="alternate" hreflang="mr-IN" href="{esc(self.abs("en", "/mr/maharashtra/"))}">'
                + "".join(f'<link rel="alternate" hreflang="{REGION[l]}" href="{esc(self.abs(l, "/maharashtra/"))}">' for l in LANGS))
        html_ = (html_.replace('<html lang="en-IN">', '<html lang="mr-IN">', 1)
                 .replace('<meta property="og:locale" content="en_IN">', '<meta property="og:locale" content="mr_IN">', 1)
                 .replace('<link rel="canonical"', alts + '<link rel="canonical"', 1))
        self.write(out, "en", "/mr/maharashtra/", html_, key="page:maharashtra-mr", lastmod=self.stamp)

    @property
    def kerala_ml_ok(self) -> bool:
        return any(d["slot"] == "KL" and d.get("tiers") for x in self.dates for d in self.days[x])

    def ml_game(self, d: dict) -> str:
        """'STHREE SAKTHI SS-543' -> 'സ്ത്രീ ശക്തി SS-543' (bumpers keep their English name)."""
        m = re.search(r"\b([A-Z]{2})-(\d+)\b", d["name"])
        return f'{ML["games"][m.group(1)]} {m.group(0)}' if m and m.group(1) in ML["games"] else game(d["name"])

    def kerala_ml(self, out: pathlib.Path) -> None:
        """/ml/kerala/: the Kerala result in Malayalam, as Kerala searches for it (കേരള ലോട്ടറി ഫലം)."""
        if not self.kerala_ml_ok:
            return
        kl = [d for x in self.dates for d in self.days[x] if d["slot"] == "KL" and d.get("tiers")]
        latest = kl[0]
        d0 = ddmmyyyy(latest["date"])
        wait = "" if latest["date"] == self.today else f'<p class="banner">{esc(ML["waiting"])}</p>'
        tiers = []
        for t in latest["tiers"]:
            nums = "".join(f"<li>{esc(n)}</li>" for n in t["numbers"])
            width = max((len(n) for n in t["numbers"]), default=4)
            hint = f'<p class="hint">{esc(ML["last4"])}</p>' if t.get("match") == "LAST4" else ""
            warn = "" if t.get("confirmed", True) else f'<p class="warn">{esc(ML["unconfirmed"])}</p>'
            tiers.append(f'<div class="tier"><div class="th"><h3>{esc(ML["tiers"].get(t["rank"], t["rank"]))}</h3>'
                         f'<b>{esc(rupees(t["prize"]))}</b></div>{hint}{warn}'
                         f'<ul class="nums {"n4" if width <= 4 else "n5" if width <= 5 else "n6"}">{nums}</ul></div>')
        wd = ML["wd"][dt.date.fromisoformat(latest["date"]).weekday()]
        first = latest["tiers"][0]
        sheet = (f'<article class="sheet"><div class="band"><h2>{esc(self.ml_game(latest))}</h2><p>കേരള സംസ്ഥാന ലോട്ടറി</p></div>'
                 f'<div class="hero"><p class="eyebrow">{esc(ML["first"])} · {esc(rupees(first["prize"]))}</p>'
                 f'<p class="first">{esc(first["numbers"][0])}</p><p class="meta">{esc(wd)}, {esc(d0)} · 3:00 PM</p></div></article>')
        rows = "".join(
            f'<tr><th scope="row"><a href="{self.url("en", self.draw_path(d))}">{d["date"][8:]}.{d["date"][5:7]}</a>'
            f'<small>{esc(ML["wd"][dt.date.fromisoformat(d["date"]).weekday()])} {d["date"][:4]}</small></th>'
            f'<td>{esc(self.ml_game(d))}</td><td><a href="{self.url("en", self.draw_path(d))}">{esc(d["tiers"][0]["numbers"][0])}</a></td></tr>'
            for d in kl[:30])
        table = (f'<h2 class="sub">{esc(ML["recent"])}</h2><div class="tbl"><table><thead><tr><th>{esc(ML["date"])}</th>'
                 f'<th>{esc(ML["game"])}</th><th>{esc(ML["first"])}</th></tr></thead><tbody>{rows}</tbody></table></div>')
        src = {x["kind"]: x["url"] for x in latest.get("sources", [])}
        body = (f'<div class="intro" lang="ml"><h1>{esc(ML["h1"])} <span class="d">{esc(d0)}</span></h1>'
                f'<p class="hint">{esc(ML["schedule"])}</p></div>{wait}<div lang="ml">{sheet}'
                f'<p class="pn"><a class="btn" href="{self.url("en", "/kerala/")}#check">{esc(ML["check"])}</a></p>'
                + "".join(tiers) + self.ad() + table + f'<p class="hint">{esc(ML["note"])}</p>'
                + (f'<p class="btns"><a class="btn" rel="nofollow" href="{esc(src["officialSite"])}">{esc(ML["official"])}</a></p>'
                   if "officialSite" in src else "") + '</div>')
        g = self.ml_game(latest)
        html_ = self.page("en", "/ml/kerala/", ML["title"].format(d=d0, g=g), ML["desc"].format(d=d0, g=g, n=first["numbers"][0]),
                          body, alternates=False, data=kl[:7], image=self.og_images.get(latest["id"], ""),
                          crumbs=[("LottoDost", "/"), ("Kerala", "/kerala/"), ("മലയാളം", "/ml/kerala/")],
                          modified=self.updated if latest["date"] == self.today else None)
        alts = (f'<link rel="alternate" hreflang="ml-IN" href="{esc(self.abs("en", "/ml/kerala/"))}">'
                + "".join(f'<link rel="alternate" hreflang="{REGION[l]}" href="{esc(self.abs(l, "/kerala/"))}">' for l in LANGS))
        html_ = (html_.replace('<html lang="en-IN">', '<html lang="ml-IN">', 1)
                 .replace('<meta property="og:locale" content="en_IN">', '<meta property="og:locale" content="ml_IN">', 1)
                 .replace('<link rel="canonical"', alts + '<link rel="canonical"', 1))
        self.write(out, "en", "/ml/kerala/", html_, key="page:kerala-ml", lastmod=self.stamp)

    def kerala_hub(self, out: pathlib.Path, lang: str) -> None:
        """/kerala/: today's Kerala result (or the countdown to it) and the recent draws."""
        w, s = WEB[lang], self.s[lang]
        kl = [d for x in self.dates for d in self.days[x] if d["slot"] == "KL" and d.get("tiers")]
        today_done = any(d["date"] == self.today for d in kl)
        latest = kl[0] if kl else None
        cards = ("" if today_done else self.waiting(lang, "KL", "15:00", self.today)) + (self.slip(lang, latest) if latest else "")
        table = self.recent_table(lang, kl[:30], w["kl_recent"])
        d0 = ddmmyyyy(latest["date"]) if latest else ddmmyyyy(self.today)
        body = (f'<div class="intro"><h1>{esc(w["kl_h1"])} <span class="d">{esc(d0)}</span></h1>'
                f'<p class="hint">{esc(w["kl_schedule"])}</p></div>' + self.checker(lang, kerala=True) + cards + self.ad() + table
                + f'<p class="hint">{esc(s["kerala_source_note"])}</p>'
                + (f'<p class="pn"><a class="btn" lang="ml" href="{self.url("en", "/ml/kerala/")}">മലയാളത്തിൽ വായിക്കുക →</a></p>'
                   if self.kerala_ml_ok else "") + self.history(lang))
        title = w["kl_title"].format(d=d0, g=game(latest["name"])) if latest else w["kl_h1"]
        desc = w["kl_desc"].format(d=d0, g=game(latest["name"]), n=latest["tiers"][0]["numbers"][0]) if latest else w["kl_schedule"]
        self.write(out, lang, "/kerala/", self.page(lang, "/kerala/", title, desc, body, data=kl[:7],
                                                    image=self.og_images.get(latest["id"], "") if latest else "",
                                                    crumbs=[(w["home"], "/"), (w["kl_tab"], "/kerala/")], tab="kerala",
                                                    wait=f"{self.today}_KL" if not today_done else "",
                                                    modified=self.updated if today_done else None), key="kerala", lastmod=self.stamp)

    def guide_links(self, lang: str, current: str = "") -> str:
        gl = guides.G[lang]
        a = "".join(f'<a class="chip"{" aria-current=\"page\"" if k == current else ""} href="{self.url(lang, p)}"><b>→</b>{esc(gl["names"][k])}</a>'
                    for k, p in guides.PATHS.items())
        return f'<h2 class="sub">{esc(gl["nav"])}</h2><nav class="chips wrapchips" aria-label="{esc(gl["nav"])}">{a}</nav>'

    def tax_calc(self, lang: str) -> str:
        c = guides.G[lang]["tax"]["calc"]
        return (f'<section class="check" id="tax"><form id="taxForm" class="row" data-l="{esc(json.dumps(c, ensure_ascii=False))}">'
                f'<input id="taxAmt" aria-label="{esc(c["amount"])}" placeholder="{esc(c["amount"])}" inputmode="numeric" autocomplete="off">'
                f'<button>{esc(c["go"])}</button></form><div id="taxRes" aria-live="polite"></div>'
                f'<p class="hint">{esc(c["note"])}</p></section>')

    def guide_pages(self, out: pathlib.Path, lang: str) -> None:
        """Our own explainers, written from the official texts (sources listed on each page)."""
        w, gl = WEB[lang], guides.G[lang]
        for key, path in guides.PATHS.items():
            g = gl[key]
            secs = "".join(f'<h2>{esc(h)}</h2>' + "".join(f"<p>{esc(x)}</p>" for x in ps) for h, ps in g["sections"])
            src = "".join(f'<li><a rel="noopener" href="{esc(u)}">{esc(n)}</a></li>' for n, u in g["src"])
            body = (f'<div class="intro"><h1>{esc(g["h1"])}</h1><p class="hint">{esc(g["intro"])}</p></div>'
                    + (self.tax_calc(lang) if key == "tax" else "") + self.ad()
                    + f'<section class="card prose guide">{secs}</section>'
                    + f'<section class="card prose"><h2>{esc(gl["sources"])}</h2><ul class="src">{src}</ul><p class="hint">{esc(gl["checked"])}</p></section>'
                    + self.guide_links(lang, key))
            self.write(out, lang, path, self.page(lang, path, g["title"], g["desc"], body,
                                                  crumbs=[(w["home"], "/"), (gl["names"][key], path)]),
                       key=f"guide:{key}", lastmod="2026-10-20")

    def recent_table(self, lang: str, draws: list[dict], heading: str) -> str:
        w, s = WEB[lang], self.s[lang]
        rows = "".join(
            f'<tr><th scope="row"><a href="{self.url(lang, self.day_path(d["date"]))}">{d["date"][8:]}.{d["date"][5:7]}</a>'
            f'<small>{esc(w["wd"][dt.date.fromisoformat(d["date"]).weekday()])} {d["date"][:4]}</small></th>'
            f'<td>{esc(game(d["name"]))}</td><td><a href="{self.url(lang, self.draw_path(d))}">{esc(d["tiers"][0]["numbers"][0])}</a></td></tr>'
            for d in draws)
        return (f'<h2 class="sub">{esc(heading)}</h2><div class="tbl"><table><thead><tr><th>{esc(w["date"])}</th>'
                f'<th>{esc(w["game"])}</th><th>{esc(s["first_prize"])}</th></tr></thead><tbody>{rows}</tbody></table></div>') if rows else ""

    def hub_links(self, lang: str, current: str = "") -> str:
        """The four daily results by the names people search them by (Morning, Day, Night, Kerala)."""
        s, w = self.s[lang], WEB[lang]
        items = [(HUB[sl], s[TAG[sl]], short_time(t)) for sl, t, _ in WEEKLY] + [("/kerala/", w["kl_tab"], "3 PM")]
        yd = self.yesterday()
        if yd:
            items.append(("/yesterday/", w["yday"], f"{yd[8:]}.{yd[5:7]}"))
        a = "".join(f'<a class="chip"{" aria-current=\"page\"" if p == current else ""} href="{self.url(lang, p)}"><b>{esc(t)}</b>{esc(n)}</a>'
                    for p, n, t in items)
        return f'<nav class="chips hubs" aria-label="{esc(s["tab_results"])}">{a}</nav>'

    def yesterday(self) -> str:
        """The last day before today with results ("lottery sambad yesterday" is searched every day)."""
        return next((x for x in self.dates if x < self.today), "")

    def yesterday_hub(self, out: pathlib.Path, lang: str) -> None:
        """/yesterday/: a fixed address for yesterday's results, with the 1st prizes of the week before."""
        yd = self.yesterday()
        if not yd:
            return
        w, path, dd = WEB[lang], "/yesterday/", ddmmyyyy(yd)
        draws = [d for d in self.ordered(self.days[yd]) if d.get("tiers")]
        week = [x for x in self.dates if x < self.today][:7]
        body = (f'<div class="intro"><h1>{esc(w["yday_h1"])} <span class="d">{esc(dd)}</span></h1></div>'
                + self.checker(lang) + self.hub_links(lang, path) + self.day_cards(lang, yd)
                + f'<p class="pn"><a class="btn" href="{self.url(lang, self.day_path(yd))}">{esc(w["all_of_day"].format(d=dd))}</a></p>'
                + self.ad() + f'<h2 class="sub">{esc(w["yday_week"])}</h2>' + self.archive(lang, week) + self.faq(lang))
        summary = ", ".join(f'{short_time(d["time"])} {d["tiers"][0]["numbers"][0]}' for d in draws)
        self.write(out, lang, path, self.page(lang, path, w["yday_title"].format(d=dd), w["yday_desc"].format(d=dd, s=ddmmyy(yd), list=summary),
                                              body, data=draws, tab="results", crumbs=[(w["home"], "/"), (w["yday"], path)]),
                   key="yesterday", lastmod=self.stamp)

    def slot_hub(self, out: pathlib.Path, lang: str, slot: str, time: str) -> None:
        """/morning-1pm/ etc.: today's result of one daily draw (or the countdown to it) and its last 30 days."""
        w, s = WEB[lang], self.s[lang]
        word, t, path = s[TAG[slot]], short_time(time), HUB[slot]
        mine = [d for x in self.dates for d in self.days[x] if d["slot"] == slot and d.get("tiers")]
        today_d = next((d for d in mine if d["date"] == self.today), None)
        no_dear = self.today in self.index.get("noDraw", [])
        if today_d:
            cards = self.slip(lang, today_d)
        else:
            cards = (f'<p class="banner">{esc(s["no_draw_day"])}</p>' if no_dear else self.waiting(lang, slot, time, self.today))
            cards += self.slip(lang, mine[0]) if mine else ""   # the last result, for those checking yesterday's ticket
        d0 = ddmmyyyy(self.today)
        body = (f'<div class="intro"><h1>{esc(w["slot_h1"].format(w=word, t=t))} <span class="d">{esc(d0)}</span></h1>'
                f'<p class="hint">{esc(w["a_time"])}</p></div>' + self.checker(lang) + self.hub_links(lang, path) + cards
                + self.ad() + self.recent_table(lang, mine[:30], w["slot_recent"].format(w=word, t=t))
                + self.history(lang) + self.app_promo(lang) + self.faq(lang))
        desc = (w["slot_desc"].format(t=t, w=word, d=d0, n=today_d["tiers"][0]["numbers"][0]) if today_d
                else w["slot_desc_wait"].format(t=t, w=word, d=d0))
        latest = today_d or (mine[0] if mine else None)
        self.write(out, lang, path, self.page(lang, path, w["slot_title"].format(w=word, d=d0, t=t), desc, body,
                                              data=[latest] if latest else [], tab="results",
                                              image=self.og_images.get(latest["id"], "") if latest else "",
                                              crumbs=[(w["home"], "/"), (f"{word} {t}", path)],
                                              wait=f"{self.today}_{slot}" if not today_d and not no_dear else "",
                                              modified=self.updated if today_d else None), key=f"slot:{slot}",
                   lastmod=self.stamp)

    def app_promo(self, lang: str) -> str:
        # Only once the app is really on Google Play: never point people to something they cannot get.
        if not self.play_url:
            return ""
        return f'<p class="app"><a href="{esc(self.play_url)}">{esc(WEB[lang]["app"])}</a></p>'

    def date_form(self, lang: str) -> str:
        w, s = WEB[lang], self.s[lang]
        lo, hi = (self.dates[-1], self.dates[0]) if self.dates else ("", "")
        return (f'<form id="dateForm" class="check row" data-none="{esc(s["no_result"])}" data-base="{self.url(lang, "/result/")}" '
                f'data-days="{" ".join(x.replace("-", "") for x in self.dates)}"><input type="date" id="dateIn" '
                f'aria-label="{esc(w["pick_date"])}" min="{lo}" max="{hi}" value="{hi}"><button>{esc(w["go"])}</button></form>'
                f'<p id="dateRes" class="hint" aria-live="polite"></p>')

    def month_name(self, lang: str, ym: str) -> str:
        return f'{WEB[lang]["mon"][int(ym[5:]) - 1]} {ym[:4]}'

    def month_links(self, lang: str, months: list[str], current: str = "") -> str:
        a = "".join(f'<a class="chip"{" aria-current=\"page\"" if ym == current else ""} href="{self.url(lang, f"/old-results/{ym}/")}">'
                    f'<b>{ym[:4]}</b>{esc(WEB[lang]["mon"][int(ym[5:]) - 1])}</a>' for ym in months)
        return f'<h2 class="sub">{esc(WEB[lang]["months"])}</h2><nav class="chips wrapchips" aria-label="{esc(WEB[lang]["months"])}">{a}</nav>'

    def archive(self, lang: str, dates: list[str]) -> str:
        s, w = self.s[lang], WEB[lang]
        head = "".join(f"<th>{esc(short_time(t))}</th>" for _, t, _ in WEEKLY)
        rows = []
        for x in dates:
            y, m, d = x.split("-")
            by_slot = {dr["slot"]: dr for dr in self.days[x] if dr.get("tiers")}
            cells = "".join(f'<td><a href="{self.url(lang, self.draw_path(by_slot[sl]))}">{esc(by_slot[sl]["tiers"][0]["numbers"][0])}</a></td>'
                            if sl in by_slot else "<td>–</td>" for sl, _, _ in WEEKLY)
            rows.append(f'<tr><th scope="row"><a href="{self.url(lang, self.day_path(x))}">{d}.{m}</a>'
                        f'<small>{esc(w["wd"][dt.date(int(y), int(m), int(d)).weekday()])} {y}</small></th>{cells}</tr>')
        return (f'<div class="tbl"><table><thead><tr><th>{esc(w["date"])}</th>{head}</tr></thead>'
                f'<tbody>{"".join(rows)}</tbody></table></div><p class="hint">{esc(s["first_prize"])}</p>')

    def assets(self, out: pathlib.Path):
        (out / "assets").mkdir(parents=True, exist_ok=True)
        (out / "assets/site.js").write_text(JS, encoding="utf-8")
        for f in STATIC.iterdir():  # site icons (web/static: favicon.svg is the source of the PNGs)
            if not (out / f.name).exists() or (out / f.name).read_bytes() != f.read_bytes():
                (out / f.name).write_bytes(f.read_bytes())
        urls = []
        for key, alts, lastmod in self.pages:
            for lang, loc in alts.items():
                if key.startswith("page:") and lang != "en":
                    continue
                links = "" if key.startswith("page:") else "".join(
                    f'<xhtml:link rel="alternate" hreflang="{REGION[l]}" href="{esc(u)}"/>' for l, u in alts.items())
                mod = f"<lastmod>{lastmod}</lastmod>" if lastmod else ""   # when the numbers last changed
                urls.append(f"<url><loc>{esc(loc)}</loc>{mod}{links}</url>")
        (out / "sitemap.xml").write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
            'xmlns:xhtml="http://www.w3.org/1999/xhtml">' + "".join(urls) + "</urlset>\n", encoding="utf-8")
        (out / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {self.abs('en', '/sitemap.xml').replace(LANGS['en'][1], '')}\n")
        ads = out / "ads.txt"
        if self.adsense:
            ads.write_text(f"google.com, {self.adsense.replace('ca-pub-', 'pub-')}, DIRECT, f08c47fec0942fa0\n")
        elif ads.exists():
            ads.unlink()


# Same palette as the app (Material 3 from the brand red), light and dark.
CSS = """:root{--bg:#f7f2f0;--fg:#221a19;--mut:#6b5552;--card:#fff;--line:#eaddd9;--soft:#f5ece9;--brand:#b3261e;--red:#b3261e;--gold:#ffe08a;--ongold:#3d2e00;--pink:#ffdad5;--onpink:#410001;--ok:#1b7f3b}
@media (prefers-color-scheme:dark){:root{--bg:#151110;--fg:#efe0dd;--mut:#c2aca8;--card:#201b1a;--line:#3b3230;--soft:#2b2422;--brand:#8c1d18;--red:#ffb4a9;--gold:#5c4300;--ongold:#ffe08a;--pink:#5c1a14;--onpink:#ffdad5;--ok:#7fd18f}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans","Noto Sans Bengali","Noto Sans Devanagari",sans-serif}
.wrap{max-width:42rem;margin:0 auto;padding:0 16px}a{color:var(--red)}h1,h2,h3{margin:0;line-height:1.25}p{margin:0}
.top{background:var(--brand);color:#fff}.bar{display:flex;align-items:center;justify-content:space-between;gap:12px;height:56px}
.brand{color:#fff;text-decoration:none;font-weight:800;font-size:1.25rem;letter-spacing:-.01em}
.langs{display:flex;background:rgba(255,255,255,.16);border-radius:999px;padding:3px}.langs a{color:#fff;text-decoration:none;font-size:.85rem;padding:4px 11px;border-radius:999px}
.langs a[aria-current]{background:#fff;color:#8c1d18;font-weight:700}
.tabs{display:flex}.tabs a{flex:1;text-align:center;color:rgba(255,255,255,.82);text-decoration:none;font-weight:600;font-size:.95rem;padding:10px 4px 9px;border-bottom:3px solid transparent}
.tabs a[aria-current]{color:#fff;border-color:var(--gold)}
.intro{margin:20px 0 4px}h1{font-size:1.45rem;font-weight:800;letter-spacing:-.01em}h1 .d{white-space:nowrap;color:var(--red)}h1.sm{font-size:1.15rem}
.hint{color:var(--mut);font-size:.88rem;margin-top:4px}.eyebrow{color:var(--mut);font-size:.85rem;font-weight:600}
.chips{display:flex;gap:8px;overflow-x:auto;margin:14px -16px 4px;padding:2px 16px 6px;scrollbar-width:none}.chips::-webkit-scrollbar{display:none}.wrapchips{flex-wrap:wrap;overflow:visible;margin:10px 0}.guide h2{font-size:1.05rem;margin:18px 0 6px}.guide h2:first-child{margin-top:4px}.guide p{margin:0 0 10px}.src{padding-left:18px}.src li{margin:6px 0}.tax{width:100%;border-collapse:collapse;margin:10px 0}.tax td{padding:8px 0;border-bottom:1px solid var(--line)}.tax td:last-child{text-align:right;font-weight:700;font-variant-numeric:tabular-nums}.tax tr.keep td{font-size:1.1rem;color:var(--brand);border-bottom:0}
.chip{flex:none;min-width:64px;text-align:center;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:6px 12px;color:var(--fg);text-decoration:none;font-weight:600;font-size:.9rem;line-height:1.25}
.chip b{display:block;font-size:.72rem;color:var(--mut);font-weight:600}.chip[aria-current]{background:var(--gold);border-color:var(--gold);color:var(--ongold)}.chip[aria-current] b{color:var(--ongold)}
.banner{background:var(--pink);color:var(--onpink);border-radius:14px;padding:12px 16px;margin:12px 0;font-weight:600}
.slip{display:grid;grid-template-columns:86px 1fr;background:var(--card);border:1px solid var(--line);border-radius:18px;margin:12px 0;overflow:hidden}
.stub{display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:2px;padding:14px 6px;color:#fff;
background:radial-gradient(circle at 100% 50%,var(--card) 2.5px,transparent 3px) right/8px 11px repeat-y,var(--brand)}
.stub b{font-size:1.05rem;white-space:nowrap}.stub span{font-size:.78rem;opacity:.92}
.sb{padding:14px 16px;min-width:0}.sb h2{font-size:1rem;font-weight:700;text-transform:uppercase;letter-spacing:.01em}.sb h2 a{color:var(--fg);text-decoration:none}
.meta{color:var(--mut);font-size:.85rem;margin:2px 0 10px}.sb>p:not(.meta):not(.eyebrow):not(.first){margin-top:6px}
.ok{color:var(--ok);font-style:normal;font-weight:700;white-space:nowrap}.warn{background:var(--pink);color:var(--onpink);border-radius:8px;padding:6px 10px;font-size:.85rem;margin-top:6px}
.first{font-weight:800;font-size:clamp(1.7rem,9vw,2.7rem);line-height:1.1;color:var(--red);word-spacing:.28em;white-space:nowrap;font-variant-numeric:tabular-nums;margin-top:2px}
.all{grid-column:1/-1;border-top:1px dashed var(--line)}
.all>summary,.faq summary{list-style:none;cursor:pointer;display:flex;justify-content:space-between;align-items:center;gap:8px}
.all>summary{padding:12px 16px;color:var(--red);font-weight:700;font-size:.95rem}
.all>summary::-webkit-details-marker,.faq summary::-webkit-details-marker{display:none}
.all>summary::after,.faq summary::after{content:"";width:8px;height:8px;border:solid currentColor;border-width:0 2px 2px 0;transform:rotate(45deg);transition:transform .15s;flex:none;margin-right:4px}
.all[open]>summary::after,.faq details[open] summary::after{transform:rotate(-135deg)}
.all .in{padding:0 16px 14px}.go{font-weight:700;text-decoration:none}
.wait .when{color:var(--mut);font-weight:600}.live{display:inline-block;background:var(--red);color:var(--card);border-radius:6px;padding:0 6px;font-size:.72rem;font-weight:800;margin-right:6px;vertical-align:1px}
.tier{padding:14px 0;border-top:1px solid var(--line)}.tier:first-child{border-top:0}.th{display:flex;justify-content:space-between;align-items:baseline;gap:8px}
.th h3{font-size:1rem}.th b{color:var(--red);white-space:nowrap}
.nums{list-style:none;padding:0;margin:10px 0 0;display:grid;gap:5px;grid-template-columns:repeat(auto-fill,minmax(3.4rem,1fr))}
.nums.n5{grid-template-columns:repeat(auto-fill,minmax(4.1rem,1fr))}.nums.n6{grid-template-columns:repeat(auto-fill,minmax(6.2rem,1fr))}
.nums li{background:var(--soft);border-radius:7px;text-align:center;padding:5px 0;font-weight:600;font-variant-numeric:tabular-nums;letter-spacing:.02em}
.nums li.hit{background:var(--gold);color:var(--ongold);box-shadow:inset 0 0 0 2px #d9a400}
.sheet{background:var(--card);border:1px solid var(--line);border-radius:18px;overflow:hidden;margin:14px 0}.band{background:var(--brand);color:#fff;padding:14px 16px}
.band h2{font-size:1.02rem;text-transform:uppercase;letter-spacing:.01em}.band p{opacity:.9;font-size:.88rem;margin-top:2px}.hero{padding:14px 16px 16px}.hero .meta{margin:4px 0 0}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:2px 16px;margin:12px 0}.prose{padding:16px}
.share{margin:14px 0}.share .row{margin-top:8px;display:grid;grid-template-columns:repeat(auto-fit,minmax(6.5rem,1fr))}.share a,.share button{display:inline-flex;align-items:center;justify-content:center;min-height:42px;padding:8px 14px;border-radius:999px;border:0;font:inherit;font-weight:700;font-size:.9rem;color:#fff;text-decoration:none;cursor:pointer}
.wa{background:#1b9e4f}.tg{background:#2481cc}.fb{background:#1877f2}.x{background:#14171a}.share .cp,.share .more{background:var(--card);color:var(--fg);box-shadow:inset 0 0 0 1px var(--line)}
.row{display:flex;gap:8px;flex-wrap:wrap}
.check{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px;margin:16px 0}.check h2{font-size:1.1rem}
.check .row{margin-top:12px}.check input{flex:1 1 11rem;min-width:0;font-family:inherit;font-weight:700;font-size:1.25rem;line-height:1.2;letter-spacing:.04em;padding:12px 14px;border:1.5px solid var(--line);border-radius:12px;background:var(--bg);color:var(--fg);text-transform:uppercase;font-variant-numeric:tabular-nums}
.check input:focus{outline:none;border-color:var(--red)}
.check button{font:inherit;font-weight:700;padding:12px 26px;border:0;border-radius:999px;background:var(--brand);color:#fff;cursor:pointer}
.res{margin-top:12px;border:1px solid var(--line);border-radius:14px;overflow:hidden}.res .rh{background:var(--ok);color:#fff;padding:10px 14px;font-weight:700}
@media (prefers-color-scheme:dark){.res .rh{color:#0b2912}}
.res .rb{padding:12px 14px}.res .rb strong{display:block;font-size:1.3rem}.res.no .rh{background:var(--soft);color:var(--fg)}
.btns{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.btn{display:inline-flex;align-items:center;min-height:42px;padding:8px 16px;border:1px solid var(--line);border-radius:999px;background:var(--card);color:var(--fg);text-decoration:none;font-weight:600;font-size:.92rem}
.pn{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;margin:14px 0}
.app{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 16px;margin:12px 0;font-weight:600}
h2.sub{font-size:1.1rem;margin:22px 0 4px}.faq{margin:24px 0 8px}.faq h2{font-size:1.1rem;margin-bottom:8px}.faq details{background:var(--card);border:1px solid var(--line);border-radius:14px;margin:8px 0}
.faq summary{padding:12px 16px;font-weight:600}.faq details p{padding:0 16px 12px}
.tbl{overflow-x:auto;background:var(--card);border:1px solid var(--line);border-radius:18px;margin:14px 0}table{width:100%;border-collapse:collapse;font-size:.92rem}
th,td{padding:10px 6px;border-top:1px solid var(--line);text-align:left;white-space:nowrap}th:first-child{padding-left:14px}td:last-child{padding-right:12px}thead th{border-top:0;background:var(--soft);color:var(--mut);font-size:.8rem}
tbody th a{color:var(--fg);font-weight:700;text-decoration:none}tbody th small{display:block;color:var(--mut);font-weight:400}
td a{color:var(--red);font-weight:700;text-decoration:none;font-variant-numeric:tabular-nums;font-size:.86rem}tbody th small{font-size:.75rem}
.hl{margin:6px 0 0;padding-left:18px}.hl li{margin:2px 0;font-variant-numeric:tabular-nums}
#dateForm{display:flex;align-items:center;gap:8px;padding:12px}#dateForm input{flex:1 1 10rem;font-size:1.05rem;letter-spacing:0;text-transform:none}
.foot{color:var(--mut);font-size:.8rem;padding:28px 0 36px;margin-top:24px;border-top:1px solid var(--line)}.foot p{margin:0 0 8px}
.foot .links{display:flex;flex-wrap:wrap;gap:4px 16px;font-size:.88rem}.ad{margin:16px 0;min-height:100px}
@media (min-width:720px){.intro{margin-top:28px}h1{font-size:1.7rem}.slip{grid-template-columns:104px 1fr}}
@media (max-width:380px){th,td{padding-left:4px;padding-right:4px}td a{font-size:.78rem}.tbl{margin-inline:-8px}}
@media (prefers-reduced-motion:reduce){*{transition:none!important}}
"""

JS = r"""(function(){"use strict";
var S=JSON.parse(document.getElementById("i18n").textContent),D=JSON.parse(document.getElementById("draws").textContent);
function t(k){var s=S[k]||k,a=[].slice.call(arguments,1);return s.replace(/%(\d)\$[sd]/g,function(_,n){return a[n-1]})}
function rup(n){var s=String(n);if(s.length<=3)return"₹"+s;return"₹"+s.slice(0,-3).replace(/\B(?=(\d{2})+(?!\d))/g,",")+","+s.slice(-3)}
function tier(r){var k={"1":"tier_1",CONS:"tier_cons","2":"tier_2","3":"tier_3","4":"tier_4","5":"tier_5"}[r];return k?t(k):t("tier_n",r)}
function parse(raw){var s=raw.replace(/[०-९]/g,function(d){return d.charCodeAt(0)-0x966}).replace(/[০-৯]/g,function(d){return d.charCodeAt(0)-0x9e6});
s=s.trim().toUpperCase().replace(/[-_./,]/g," ").replace(/\s+/g," ");
function p(x){var m=/^([A-Z]{2})\s*-\s*(\d{2})\s*(\d{4})$/.exec(x)||/^([A-Z]{2})\s?(\d{2})\s+(\d{4})$/.exec(x);if(m)return{pre:m[1]+"-"+m[2],dg:m[3],f:"MH4"};
m=/^(\d{2})\s*([A-Z])\s*(\d{5})$/.exec(x);if(m)return(+m[1]>=34&&"ABCDEGHJKL".indexOf(m[2])>=0)?{pre:m[1]+m[2],dg:m[3],f:"D5"}:false;
if((m=/^(\d{2})\s+(\d{4})$/.exec(x)))return{pre:m[1],dg:m[2],f:"D4"};if((m=/^([A-Z]{1,2})\s*(\d{6})$/.exec(x)))return{pre:m[1],dg:m[2],f:"B6"};return null}
var r=p(s);if(r)return r;if(r===false)return null;var c=s.replace(/ /g,"");if(c!==s){r=p(c);if(r)return r;if(r===false)return null}
return/^\d{4,6}$/.test(c)?{pre:null,dg:c,f:"DIGITS"}:null}
function show(k){return k.pre?k.pre+" "+k.dg:k.dg}
function compat(k,d){if(d.format==="D5")return k.f==="D5"||(k.f==="DIGITS"&&k.dg.length<=5);if(d.format==="D4")return k.f==="D4"||(k.f==="DIGITS"&&k.dg.length===4);if(d.format==="B6")return k.f==="B6"||k.f==="DIGITS";if(d.format==="MH4")return k.f==="MH4"||(k.f==="DIGITS"&&k.dg.length===4);return false}
function check(k,d){if(!d.tiers||!d.tiers.length||!compat(k,d))return null;var w=[],n=k.dg.length,full=k.f!=="DIGITS",sh=show(k);
d.tiers.forEach(function(T){var N=T.numbers||[];if(T.match==="FULL"){var f=N[0];if(!f)return;if(full){if(sh===f)w.push({T:T})}else if(n===5&&k.dg===f.slice(-5))w.push({T:T,nf:1})}
else if(T.match==="CONS"){if(n<5)return;var first=full&&(d.tiers[0].numbers||[])[0]===sh;if(!first&&N.indexOf(k.dg)>=0)w.push({T:T})}
else if(T.match==="EXACT"){if(full){if(N.indexOf(sh)>=0)w.push({T:T})}else if(n===(d.format==="MH4"?4:6)&&N.some(function(x){return x.slice(-n)===k.dg}))w.push({T:T,nf:1})}
else if(T.match==="LAST5"){if(n>=5&&N.indexOf(k.dg.slice(-5))>=0)w.push({T:T})}else if(T.match==="LAST4"){if(N.indexOf(k.dg.slice(-4))>=0)w.push({T:T})}});
w.sort(function(a,b){return b.T.prize-a.T.prize});return{d:d,w:w}}
function el(tag,cls,txt){var e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e}
function hm(s){var p=s.split(":"),h=+p[0];return(h%12||12)+(p[1]!=="00"?":"+p[1]:"")+(h>=12?" PM":" AM")}
function box(head,cls){var b=el("div","res"+(cls?" "+cls:""));b.appendChild(el("div","rh",head));var c=el("div","rb");b.appendChild(c);return[b,c]}
var f=document.getElementById("checkForm");
if(f)f.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("checkRes");out.textContent="";
var v=document.getElementById("ticket").value;if(!v.trim())return;var k=parse(v);if(!k){var e=box(t("ticket_invalid"),"no");out.appendChild(e[0]);return}
var cs=D.map(function(d){return check(k,d)}).filter(Boolean),won=cs.filter(function(c){return c.w.length});
won.forEach(function(c){var x=c.w[0],e=box(x.nf?t("maybe_first"):t("you_won"));e[1].appendChild(el("strong",null,tier(x.T.rank)+" · "+rup(x.T.prize)));
e[1].appendChild(el("div","hint",c.d.name.replace(/ WEEKLY LOTTERY$/,"")+" · "+c.d.date.split("-").reverse().join(".")+" "+hm(c.d.time)));
if(x.nf)e[1].appendChild(el("div","hint",t(c.d.format==="MH4"?"need_full_mh":"need_full_number")));if(x.T.confirmed===false)e[1].appendChild(el("div","warn",t("tier_unconfirmed")));out.appendChild(e[0])});
if(!won.length){var e=box(t("no_win_title",show(k)),"no");e[1].appendChild(el("div","hint",t(cs.length===1&&S.no_win_body_one?"no_win_body_one":"no_win_body",cs.length)));out.appendChild(e[0])}
var dg=k.dg;document.querySelectorAll(".nums li").forEach(function(s){var x=s.textContent,hit=dg.length>=x.length&&dg.slice(-x.length)===x;
s.classList.toggle("hit",hit);if(hit){var dt=s.closest("details");if(dt)dt.open=true}});
out.scrollIntoView({block:"nearest",behavior:"smooth"})});
// Share: copy the link, or the phone's own share sheet where the browser has one.
document.querySelectorAll(".share .cp").forEach(function(b){b.addEventListener("click",function(){var u=b.dataset.url,l=b.textContent;
function done(){b.textContent=t("copied");setTimeout(function(){b.textContent=l},1800)}
if(navigator.clipboard)navigator.clipboard.writeText(u).then(done,function(){prompt("",u)});else prompt("",u)})});
if(navigator.share)document.querySelectorAll(".share .more").forEach(function(b){b.hidden=false;b.addEventListener("click",function(){
navigator.share({text:b.dataset.text}).catch(function(){})})});
// Draws still to come: countdown, then LIVE, then the "taking longer" note, as in the app.
var W=document.querySelectorAll(".when[data-at]");function tick(){var now=Date.now();W.forEach(function(e){var at=Date.parse(e.dataset.at),m=Math.floor((at-now)/60000);
if(m>=0){e.textContent=t("draw_in",Math.floor(m/60),m%60)}else if(m>-15){e.textContent="";e.appendChild(el("span","live",t("live")));e.appendChild(document.createTextNode(t("result_coming")))}
else e.textContent=t("result_delayed")})}if(W.length){tick();setInterval(tick,30000)}
// Number history: the 90-day feed is fetched only when someone searches.
var feed=function(name){return document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/"+name)};
var hf=document.getElementById("histForm"),H=null;
if(hf)hf.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("histRes"),v=document.getElementById("histNum").value;
out.textContent="";if(!v.trim())return;var k=parse(v);if(!k){out.appendChild(box(t("ticket_invalid"),"no")[0]);return}
(H?Promise.resolve(H):fetch(feed("history.json")).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(j){H=j;return j})).then(function(j){
var days=j.days||90,hits=[];(j.draws||[]).forEach(function(d){var c=check(k,d);if(c&&c.w.length)hits.push(c)});
if(!hits.length){var e=box(t("history_none",show(k),days),"no");out.appendChild(e[0]);return}
var e=box(t(hits.length===1&&S.history_count_one?"history_count_one":"history_count",hits.length,days)),ul=el("ul","hl");
hits.forEach(function(c){var x=c.w[0];ul.appendChild(el("li",null,c.d.date.split("-").reverse().join(".")+" "+hm(c.d.time)+" · "+
c.d.name.replace(/ WEEKLY LOTTERY$/,"")+" · "+tier(x.T.rank)+" "+rup(x.T.prize)+(x.nf?" *":"")))});e[1].appendChild(ul);out.appendChild(e[0])
}).catch(function(){out.appendChild(box(t("history_unavailable"),"no")[0])})});
// Lottery tax: 30% cut above Rs 10,000 (s.393(3)); at filing 30% + surcharge (new regime, marginal relief) + 4% cess.
var tf=document.getElementById("taxForm");
function lotteryTax(p){var tds=p>10000?Math.round(p*0.3):0,t=p*0.3,r=0,T=0,rp=0;
if(p>20000000){r=0.25;T=20000000;rp=0.15}else if(p>10000000){r=0.15;T=10000000;rp=0.10}else if(p>5000000){r=0.10;T=5000000;rp=0}
var ts=t*(1+r);if(r){var cap=T*0.3*(1+rp)+(p-T);if(ts>cap)ts=cap}var total=Math.round(ts*1.04);
return{tds:tds,paid:p-tds,more:total-tds,total:total,keep:p-total}}
window.lotteryTax=lotteryTax;
if(tf)tf.addEventListener("submit",function(ev){ev.preventDefault();var L=JSON.parse(tf.dataset.l),out=document.getElementById("taxRes");
var p=parseInt(document.getElementById("taxAmt").value.replace(/[^0-9]/g,""),10);out.textContent="";if(!(p>0))return;
var x=lotteryTax(p),inr=function(n){return"₹"+n.toLocaleString("en-IN")},tb=el("table","tax");
[["prize",p],["tds",x.tds],["paid",x.paid],["more",x.more],["total",x.total],["keep",x.keep]].forEach(function(r){var tr=el("tr",r[0]==="keep"?"keep":"");
tr.appendChild(el("td","",L[r[0]]));tr.appendChild(el("td","",inr(r[1])));tb.appendChild(tr)});out.appendChild(tb);
if(p<=10000)out.appendChild(el("p","hint",L.small))});
// Old results: jump to a date we have a page for.
var df=document.getElementById("dateForm");
if(df)df.addEventListener("submit",function(ev){ev.preventDefault();var v=document.getElementById("dateIn").value,p=v.split("-");if(p.length!==3)return;
if((" "+df.dataset.days+" ").indexOf(" "+p.join("")+" ")>=0)location.href=df.dataset.base+p[2]+"-"+p[1]+"-"+p[0]+"/";
else document.getElementById("dateRes").textContent=df.dataset.none});
// Today's pages refresh themselves while a result is due (static pages, so a reload is cheap).
var day=document.body.dataset.live;
if(day){var shown=D.filter(function(d){return d.date===day}).length,url=document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/today.json");
setInterval(function(){if(document.hidden)return;fetch(url,{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
var n=j.draws.filter(function(d){return d.date===day&&d.tiers&&d.tiers.length}).length;if(n>shown)location.reload()}).catch(function(){})},30000)}
// A page waiting for one draw (Kerala before 4 PM, Morning/Day/Night before their result) reloads once it is in the feed.
var wid=document.body.dataset.wait;
if(wid)setInterval(function(){if(document.hidden)return;fetch(feed("today.json"),{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
if(j.draws.some(function(d){return d.id===wid&&d.tiers&&d.tiers.length}))location.reload()}).catch(function(){})},30000);
})();
"""


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=pathlib.Path, required=True, help="folder containing v2/")
    ap.add_argument("--out", type=pathlib.Path, required=True)
    ap.add_argument("--base-url", default=os.environ.get("SITE_URL", "https://lottodost.com"))
    ap.add_argument("--link-prefix", default=None,
                    help='path the copy is served under, if not base-url\'s ("" for a mirror at a host root)')
    a = ap.parse_args(argv)
    n = Site(a.data, a.base_url, link_prefix=a.link_prefix).build(a.out)
    print(f"site: {n} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
