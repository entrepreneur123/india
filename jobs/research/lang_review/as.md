# অসমীয়া (as) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/as.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/as.js)

| 키 | English | অসমীয়া | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | চৰকাৰী জব এলাৰ্ট | |
| `tabHome` | Home | হোম | |
| `tabMore` | More | অধিক | |
| `loading` | Loading… | লোড হৈ আছে… | |
| `homeComing` | Exam dates in the next 30 days | অহা 30 দিনৰ পৰীক্ষাৰ তাৰিখ | |
| `homeLatest` | Latest updates | শেহতীয়া আপডেট | |
| `inDays` | in {n} days | {n} দিনৰ পিছত | |
| `onNow` | on now | চলি আছে | |
| `today` | today | আজি | |
| `theme` | Theme | থিম | |
| `themeAuto` | Auto | অটো | |
| `themeLight` | Light | লাইট | |
| `themeDark` | Dark | ডাৰ্ক | |
| `language` | Language | ভাষা | |
| `howToApply` | How to apply | কেনেকৈ আবেদন কৰিব | |
| `documents` | Keep these documents ready | এই ডকুমেণ্টবোৰ সাজু কৰি ৰাখক | |
| `helplineTitle` | SSC helplines | SSC হেল্পলাইন | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ফৰ্ম পূৰণ কৰোঁতে বা কম্পিউটাৰ-ভিত্তিক পৰীক্ষাত কিবা সমস্যা হ'লে। | |
| `typingTitle` | Typing / data entry speed practice | টাইপিং / ডেটা এণ্ট্ৰি স্পীড অনুশীলন | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | তলৰ অনুচ্ছেদটো টাইপ কৰক। প্ৰথম কী টিপাৰ লগে লগে টাইমাৰ আৰম্ভ হ'ব। স্পীড দেখুওৱা হয় প্ৰতি ঘণ্টাত কী ডিপ্ৰেছনত, SSC-এ যিদৰে কয়। | |
| `typingTest` | Standard | ষ্টেণ্ডাৰ্ড | |
| `typingMinutes` | Time | সময় | |
| `typingFull` | Full ({n} min) | সম্পূৰ্ণ ({n} মিনিট) | |
| `typingStart` | Start again | পুনৰ আৰম্ভ কৰক | |
| `typingDone` | Time is up. | সময় শেষ। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | ঘণ্টাত {kdph} কী ডিপ্ৰেছন (≈ {wpm} w.p.m.), {accuracy}% শুদ্ধ, {typed}টাৰ ভিতৰত {correct}টা শুদ্ধ | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name}ৰ স্পীড ({kdph}/ঘণ্টা) পূৰণ হৈছে। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name}ৰ স্পীডতকৈ ({kdph}/ঘণ্টা) কম: অনুশীলন চলাই থাকক। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | কেৱল অনুশীলনৰ বাবে: SSC-এ নিজৰ মূল্যায়ন নিৰ্দেশনা অনুসৰি ভুল গণনা কৰে। হিন্দী টাইপিং অনুশীলন ইয়াত নাই। | |
| `faqTitle` | Frequently asked | সঘনাই সোধা প্ৰশ্ন | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}ৰ আবেদনৰ শেষ তাৰিখ কেতিয়া? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | আবেদনৰ শেষ তাৰিখ {date}; ফী {fee} পৰ্যন্ত জমা দিব পাৰিব। | |
| `faqAge` | What is the age limit for {exam}? | {exam}ৰ বয়সৰ সীমা কিমান? | |
| `faqFee` | What is the application fee? | আবেদন ফী কিমান? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | জেনেৰেল, OBC আৰু EWS প্ৰাৰ্থীৰ বাবে ₹{amount}। {exempt}: কোনো ফী নালাগে। | |
| `faqSalary` | What is the salary? | দৰমহা কিমান? | |
| `faqExam` | When is the {exam} exam? | {exam} পৰীক্ষা কেতিয়া? | |
| `faqVacancy` | How many vacancies are there? | কিমানটা খালী পদ আছে? | |
| `faqQual` | What qualification is needed? | কি যোগ্যতা লাগে? | |
| `aboutTitle` | About this app | এই এপৰ বিষয়ে | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ভাৰতত চৰকাৰী চাকৰিৰ বাবে প্ৰস্তুতি চলোৱাসকলৰ বাবে এটা বিনামূলীয়া, আনঅফিচিয়েল সহায়ক। জাননী, তাৰিখ আৰু নিয়মসমূহ নিযুক্তিকৰ্তা সংস্থাৰ অফিচিয়েল ৱেবছাইট আৰু জাননীৰ পৰা লোৱা হৈছে, ৰেফাৰেন্সসহ। ছাইন-আপ নালাগে, কোনো ব্যক্তিগত তথ্য সংগ্ৰহ কৰা নহয়: আপোনাৰ তথ্য আৰু ৰোল নম্বৰ এই ফোনতে থাকে। | |
| `sourceRef` | Source: {ref} | উৎস: {ref} | |
| `toastSaved` | Saved | ছেভ কৰা হ'ল | |
| `toastRemoved` | Removed | আঁতৰোৱা হ'ল | |
| `toastFollow` | You follow this exam | আপুনি এই পৰীক্ষাটো ফল' কৰি আছে | |
| `toastUnfollow` | Unfollowed | আনফল' কৰা হ'ল | |
| `settings` | Settings | ছেটিংছ | |
| `toolsTitle` | Tools | টুলছ | |
| `tabResult` | Result | ৰিজাল্ট | |
| `tabForMe` | For me | মোৰ বাবে | |
| `resultTitle` | Check your result by roll number | ৰোল নম্বৰেৰে ৰিজাল্ট চাওক | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | যোৱা 120 দিনত SSC-এ প্ৰকাশ কৰা ৰিজাল্টৰ তালিকাত বিচাৰে, ডাঙৰ PDF ডাউনলোড নকৰাকৈ। এটা সৰু ফাইল আনিবলৈ আপোনাৰ ৰোল নম্বৰৰ কেৱল শেষৰ দুটা সংখ্যা ব্যৱহাৰ কৰা হয়। | |
| `rollNo` | Roll number | ৰোল নম্বৰ | |
| `resultExam` | Result | ৰিজাল্ট | |
| `find` | Find | বিচাৰক | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | এডমিট কাৰ্ডত যিদৰে ছপা হৈছে, ঠিক সেইদৰে ৰোল নম্বৰ লিখক (8-14 টা সংখ্যা)। | |
| `foundIn` | Found in: {list} | পোৱা গ'ল: {list} | |
| `notIn` | Not in: {list} | নাই: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | এই তালিকাবোৰত আপোনাৰ ৰোল নম্বৰ নাই। কেৱল ইয়াৰ অৰ্থ এইটো নহয় যে আপুনি কোৱালিফাই কৰা নাই: তলৰ অফিচিয়েল PDF চাই লওক। | |
| `officialFiles` | Official files of this notice | এই জাননীৰ অফিচিয়েল ফাইল | |
| `listCount` | {n} roll numbers | {n}টা ৰোল নম্বৰ | |
| `noLists` | No result lists to search right now. | এই মুহূৰ্তত বিচাৰিবলৈ কোনো ৰিজাল্টৰ তালিকা নাই। | |
| `forMeTitle` | Exams that suit you | আপোনাৰ বাবে উপযুক্ত পৰীক্ষা | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | এবাৰ আপোনাৰ তথ্য দিয়ক। আপুনি আবেদন কৰিব পৰা সকলো SSC পৰীক্ষা, সেইবোৰৰ স্থিতি আৰু তাৰ পিছত কি আছে, সকলো দেখিব। | |
| `forMeEdit` | Edit my details | মোৰ তথ্য সলনি কৰক | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | আপোনাৰ যোগ্যতাৰ বাবে (নিয়ম এতিয়াও যোগ কৰা হোৱা নাই: জাননী পঢ়ক) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | আপোনাৰ যোগ্যতাৰ বাবে অহা পৰীক্ষা (সম্ভাৱ্য) | |
| `saveDetails` | Show my exams | মোৰ পৰীক্ষা দেখুৱাওক | |
| `whenTitle` | What has happened and what is next | কি হৈ গ'ল আৰু এতিয়া কি | |
| `st_done` | Done | হৈ গ'ল | |
| `st_now` | Now | এতিয়া | |
| `st_next` | Next | পৰৱৰ্তী | |
| `st_later` | Later | পিছত | |
| `stage_notice` | Notice published | জাননী প্ৰকাশিত | |
| `stage_apply` | Online application | অনলাইন আবেদন | |
| `stage_admit_card` | Admit card / exam city | এডমিট কাৰ্ড / পৰীক্ষাৰ চহৰ | |
| `stage_answer_key` | Answer key | আনছাৰ কী | |
| `stage_result` | Result | ৰিজাল্ট | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC-এ এতিয়াও তাৰিখ ঘোষণা কৰা নাই | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | মূল দৰমহা মাহে ₹{from}–{to} (লেভেল {l}; ভাট্টা বেলেগে) | |
| `savedLink` | ★ Saved | ★ ছেভ কৰা | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | এইটো কোনো চৰকাৰী ৱেবছাইট নহয় আৰু কোনো নিযুক্তিকৰ্তা সংস্থা বা চৰকাৰৰ সৈতে জড়িত নহয়। ইয়াৰ সকলো তথ্য নিযুক্তিকৰ্তা সংস্থাৰ অফিচিয়েল ৱেবছাইটৰ পৰা লোৱা, আৰু প্ৰতিটো আপডেটত অফিচিয়েল ফাইল বা পেজৰ লিংক আছে। আবেদন কৰাৰ আগতে সদায় অফিচিয়েল জাননী চাই লওক। | |
| `tabExams` | Exams | পৰীক্ষা | |
| `filters` | Filters | ফিল্টাৰ | |
| `kindAll` | All | সকলো | |
| `kindRecruitment` | New jobs | নতুন চাকৰি | |
| `kindResult` | Results | ৰিজাল্ট | |
| `kindAdmit` | Admit cards | এডমিট কাৰ্ড | |
| `kindAnswer` | Answer keys | আনছাৰ কী | |
| `kindAmendment` | Changes | সংশোধন | |
| `kindVacancy` | Vacancies | খালী পদ | |
| `kind_recruitment` | New job | নতুন চাকৰি | |
| `kind_amendment` | Change | সংশোধন | |
| `kind_result` | Result | ৰিজাল্ট | |
| `kind_answer_key` | Answer key | আনছাৰ কী | |
| `kind_admit_card` | Admit card | এডমিট কাৰ্ড | |
| `kind_vacancy` | Vacancy | খালী পদ | |
| `kind_schedule` | Exam dates | পৰীক্ষাৰ তাৰিখ | |
| `kind_notice` | Notice | জাননী | |
| `search` | Search jobs, exams, results | চাকৰি, পৰীক্ষা, ৰিজাল্ট বিচাৰক | |
| `allExams` | All exams | সকলো পৰীক্ষা | |
| `myExams` | My exams only | কেৱল মোৰ পৰীক্ষা | |
| `anyQualification` | Any qualification | যিকোনো যোগ্যতা | |
| `qual_10` | Class 10 | দশম শ্ৰেণী | |
| `qual_12` | Class 12 | দ্বাদশ শ্ৰেণী | |
| `qual_diploma` | Diploma | ডিপ্লোমা | |
| `qual_graduate` | Bachelor's degree | স্নাতক ডিগ্ৰী | |
| `qual_postgraduate` | Master's degree | স্নাতকোত্তৰ ডিগ্ৰী | |
| `departmental` | Departmental exams | বিভাগীয় পৰীক্ষা | |
| `updated` | Updated {time} IST | আপডেট: {time} IST | |
| `newSince` | {n} new since your last visit | আপোনাৰ শেষবাৰৰ ভিজিটৰ পিছত {n}টা নতুন | |
| `cutoffTitle` | Cut-off marks | কাট-অফ নম্বৰ | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC-এ কাট-অফ কেৱল ৰিজাল্টৰ বিৱৰণৰ ভিতৰতহে দিয়ে। সংখ্যাবোৰ ইয়াত কপি কৰা হোৱা নাই (এটা ভুল সংখ্যাইও বিভ্ৰান্ত কৰিব পাৰে): প্ৰতিটো লিংকে SSC-ৰ PDF ঠিক সেই পেজত খোলে য'ৰ পৰা কাট-অফৰ বিৱৰণ আৰম্ভ হয়। | |
| `cutoffFrom` | Open at page {p} | পেজ {p}ত খোলক | |
| `cutoffPages` | mentioned on p. {pages} | পৃ. {pages}ত উল্লেখ আছে | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | যোৱা 120 দিনৰ ৰিজাল্টৰ বিৱৰণত কোনো কাট-অফ পোৱা নগ'ল। | |
| `alertsTitle` | Alerts on this phone | এই ফোনত এলাৰ্ট | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | আপুনি ফল' কৰা পৰীক্ষাৰ কোনো খবৰ আহিলে এটা নোটিফিকেচন, আৰু আবেদন বন্ধ হোৱাৰ দুদিন আগতে আৰু এটা। | |
| `alertsOn` | Turn on alerts | এলাৰ্ট অন কৰক | |
| `alertsOff` | Turn off alerts | এলাৰ্ট অফ কৰক | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | প্ৰথমে এটা পৰীক্ষা ফল' কৰক (পৰীক্ষা → ফল' কৰক)। | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n}টা পৰীক্ষাৰ বাবে অন আছে। কেতিয়া চেক কৰিব সেয়া Chrome-এ ঠিক কৰে, এপটো সঘনাই খুলিলে বেছি সঘনাই চেক কৰে, গতিকে খবৰ পোষ্ট হোৱাৰ কেইঘণ্টামান পিছতহে আপোনাৰ ওচৰ পাব পাৰে। | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | এই ছাইটৰ নোটিফিকেচন ব্লক কৰা আছে। ব্ৰাউজাৰৰ ছাইট ছেটিংছত গৈ অনুমতি দিয়ক। | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | এই ব্ৰাউজাৰে বেকগ্ৰাউণ্ডত চেক কৰিব নোৱাৰে। Android-ত প্ৰথমে Chrome-ৰ পৰা এপটো ইনষ্টল কৰক। নহ'লে প্ৰতিটো পৰীক্ষাৰ পেজত থকা কেলেণ্ডাৰ ৰিমাইণ্ডাৰ ব্যৱহাৰ কৰক। | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | আপোনাৰ পৰীক্ষাবোৰৰ আৰু {n}টা আপডেট | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: {n} দিনৰ পিছত আবেদন বন্ধ হ'ব | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: আজি আবেদনৰ শেষ দিন | |
| `installTitle` | Install the app | এপটো ইনষ্টল কৰক | |
| `installBtn` | Install | ইনষ্টল | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | এপৰ দৰে খোলে, অফলাইনতো চলে, আৰু Android-ত এলাৰ্ট পাব পাৰি। | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone-ত: Share টিপক, তাৰ পিছত "Add to Home Screen"। | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ফল' কৰি আছে। ফোনত এলাৰ্টৰ বাবে: অধিক → এলাৰ্ট অন কৰক | |
| `rollPlaceholder` | Roll number on your admit card | এডমিট কাৰ্ডত থকা ৰোল নম্বৰ | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n}টা পৰীক্ষাৰ বাবে অন আছে। SSC-ৰ জাননী সাধাৰণতে পোষ্ট হোৱাৰ এঘণ্টাৰ ভিতৰত আপোনাৰ ওচৰ পায়; ৰিজাল্ট আৰু আন বোৰ্ডৰ জাননী আহিবলৈ 3 ঘণ্টালৈকে লাগিব পাৰে। | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | এই ব্ৰাউজাৰে এলাৰ্ট দেখুৱাব নোৱাৰে। iPhone-ত প্ৰথমে এপটো হোম স্ক্ৰীণত যোগ কৰক (Share → Add to Home Screen), তাৰ পিছত তাৰ পৰাই খোলক। | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | এলাৰ্ট অন কৰিব পৰা নগ'ল। ইণ্টাৰনেট সংযোগ চাই পুনৰ চেষ্টা কৰক। | |
| `allBoards` | All recruiters | সকলো নিযুক্তিকৰ্তা সংস্থা | |
| `newBadge` | NEW | নতুন | |
| `nothing` | Nothing here in the last 120 days. | যোৱা 120 দিনত ইয়াত একো নাই। | |
| `loadError` | Couldn't load. Check your internet connection and try again. | লোড কৰিব পৰা নগ'ল। ইণ্টাৰনেট সংযোগ চাই পুনৰ চেষ্টা কৰক। | |
| `offline` | Offline: showing the copy saved on this phone. | অফলাইন: এই ফোনত ছেভ কৰা কপিটো দেখুওৱা হৈছে। | |
| `save` | Save | ছেভ | |
| `saved` | Saved | ছেভ কৰা হ'ল | |
| `share` | Share | শ্বেয়াৰ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | অফিচিয়েল পেজ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ অফিচিয়েল লিংক: {url} ⏎ {app}ৰ জৰিয়তে (চৰকাৰী ছাইট নহয়) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | প্ৰতিটো পৰীক্ষাৰ শেহতীয়া জাননী, তাৰিখ, ফী, বয়সৰ সীমা আৰু তাৰ পিছৰ সকলো জাননী। | |
| `follow` | Follow | ফল' কৰক | |
| `following` | Following | ফল' কৰি আছে | |
| `minQual` | Qualification: {q} | যোগ্যতা: {q} | |
| `qualUnknown` | Qualification: see the Notice | যোগ্যতা: জাননী চাওক | |
| `calendarTitle` | SSC's tentative calendar | SSC-ৰ সম্ভাৱ্য কেলেণ্ডাৰ | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | এইবোৰ পৰিকল্পনা, জাননী নহয়: প্ৰকৃত তাৰিখ প্ৰতিটো জাননীত থাকে আৰু বেলেগ হ'ব পাৰে। | |
| `resourcesTitle` | Official resources | অফিচিয়েল ৰিছ'ৰ্চ | |
| `res_syllabus` | Syllabus | চিলেবাছ | |
| `res_vacancy` | Vacancies (tentative / final) | খালী পদ (সম্ভাৱ্য / চূড়ান্ত) | |
| `res_previous_papers` | Previous year papers | যোৱা বছৰৰ প্ৰশ্নকাকত | |
| `res_certificate_formats` | Certificate formats | চাৰ্টিফিকেটৰ ফৰ্মেট | |
| `res_instructions` | Instructions | নিৰ্দেশনা | |
| `back` | ← All exams | ← সকলো পৰীক্ষা | |
| `latestNotice` | Latest Notice | শেহতীয়া জাননী | |
| `importantDates` | Important dates | গুৰুত্বপূৰ্ণ তাৰিখ | |
| `applyWindow` | Apply online | অনলাইন আবেদন | |
| `feeLastDate` | Fee payment last date | ফী জমা দিয়াৰ শেষ তাৰিখ | |
| `correctionWindow` | Form correction window | ফৰ্ম সংশোধনৰ সময় | |
| `tentative` | tentative | সম্ভাৱ্য | |
| `applicationFee` | Application fee | আবেদন ফী | |
| `feeGeneral` | General / OBC / EWS | জেনেৰেল / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ৰেহাই (₹0) | |
| `exempt_female` | Women | মহিলা | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | প্ৰাক্তন সৈনিক | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | সংশোধন: প্ৰথমবাৰ ₹{a}, দ্বিতীয়বাৰ ₹{b} (সকলো প্ৰাৰ্থী) | |
| `ageLimit` | Age limit (as on {date}) | বয়সৰ সীমা ({date}ৰ হিচাপত) | |
| `ageBand` | {band} years: born {from} to {to} | {band} বছৰ: জন্ম {from}ৰ পৰা {to}ৰ ভিতৰত | |
| `relaxation` | Upper age relaxation | ঊৰ্ধ্ব বয়সসীমাত ৰেহাই | |
| `relaxYears` | {n} years | {n} বছৰ | |
| `notComputed` | Not calculated here: {list} | ইয়াত হিচাপ কৰা হোৱা নাই: {list} | |
| `vacancies` | Vacancies | খালী পদ | |
| `vacancyCount` | About {n} ({tentative}) | প্ৰায় {n} ({tentative}) | |
| `posts` | Posts | পদ | |
| `importantLinks` | Important links | গুৰুত্বপূৰ্ণ লিংক | |
| `applyOnline` | Apply online (ssc.gov.in) | অনলাইনত আবেদন (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | অফিচিয়েল জাননী (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} তাৰিখৰ সংশোধন | |
| `allNotices` | All notices for this exam | এই পৰীক্ষাৰ সকলো জাননী | |
| `addCalendar` | Add dates to my calendar | তাৰিখবোৰ মোৰ কেলেণ্ডাৰত যোগ কৰক | |
| `checkThis` | Check my eligibility | মোৰ যোগ্যতা চেক কৰক | |
| `status_open` | Applications open till {date} | {date} পৰ্যন্ত আবেদন চলি আছে | |
| `status_upcoming` | Applications open on {date} | {date}ৰ পৰা আবেদন আৰম্ভ হ'ব | |
| `status_closed` | Applications closed on {date} | {date}ত আবেদন বন্ধ হৈছে | |
| `daysLeft` | {n} days left | আৰু {n} দিন বাকী | |
| `lastDay` | Last day today | আজি শেষ দিন | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | এই পৰীক্ষাৰ যোগ্যতাৰ নিয়ম এতিয়াও যোগ কৰা হোৱা নাই: জাননী পঢ়ক। | |
| `examLabel` | Exam | পৰীক্ষা | |
| `dob` | Date of birth (as in Class 10 certificate) | জন্ম তাৰিখ (দশম শ্ৰেণীৰ চাৰ্টিফিকেট অনুসৰি) | |
| `category` | Category | কেটেগৰী | |
| `catUR` | General (UR) | জেনেৰেল (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (নন-ক্ৰিমী লেয়াৰ) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | সৰ্বোচ্চ যোগ্যতা | |
| `edu10` | Class 10 | দশম শ্ৰেণী | |
| `edu12` | Class 12 | দ্বাদশ শ্ৰেণী | |
| `eduGraduate` | Bachelor's degree or higher | স্নাতক বা তাতকৈ ওপৰ | |
| `gender` | Gender | লিংগ | |
| `male` | Male | পুৰুষ | |
| `female` | Female | মহিলা | |
| `other` | Other | অন্যান্য | |
| `appearing` | Final exam of that level taken, result not out yet | সেই স্তৰৰ চূড়ান্ত পৰীক্ষা দিছোঁ, ৰিজাল্ট এতিয়াও ওলোৱা নাই | |
| `pwbd` | Person with benchmark disability (PwBD) | বেঞ্চমাৰ্ক দিব্যাংগতা থকা ব্যক্তি (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | প্ৰাক্তন সৈনিক, কেন্দ্ৰীয় চৰকাৰী কৰ্মচাৰী, বা বিধৱা/বিবাহবিচ্ছিন্না মহিলা | |
| `check` | Check | চেক কৰক | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | আপোনাৰ উত্তৰ এই ডিভাইচতে থাকে। কৰবালৈ একো পঠিওৱা নহয়। | |
| `enterDob` | Enter your date of birth. | আপোনাৰ জন্ম তাৰিখ দিয়ক। | |
| `closedNote` | Shown so you can prepare for the next cycle. | পৰৱৰ্তী চক্ৰৰ বাবে প্ৰস্তুতি ল'ব পৰাকৈ দেখুওৱা হৈছে। | |
| `canApply` | {n} can apply | {n}টাত আবেদন কৰিব পাৰিব | |
| `toCheck` | {n} to check | {n}টা চেক কৰিবলগীয়া | |
| `notEligible` | {n} not eligible | {n}টাত যোগ্য নহয় | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ফী: {fee}। বয়সত ৰেহাই ধৰা হৈছে: {n} বছৰ। বয়স {date}ৰ হিচাপত গণনা কৰা হয়। | |
| `feeNone` | none (exempt) | নাই (ৰেহাই) | |
| `st_eligible` | You can apply | আবেদন কৰিব পাৰিব | |
| `st_check` | Check one condition | এটা চৰ্ত চেক কৰক | |
| `st_ineligible` | Not eligible | যোগ্য নহয় | |
| `payLevel` | Pay Level {l} · Group {g} | পে লেভেল {l} · গ্ৰুপ {g} | |
| `groupOnly` | Group {g} | গ্ৰুপ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | জন্ম {from}ৰ পৰা {to}ৰ ভিতৰত (আপোনাৰ বাবে) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date} তাৰিখৰ জাননীৰ পৰা লোৱা নিয়ম ({checked}ত পৰীক্ষা কৰা)। | |
| `sigTitle` | Signature for SSC forms | SSC ফৰ্মৰ বাবে চহী | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC-এ {min}-{max} KB-ৰ JPEG বিচাৰে, প্ৰায় {w} cm × {h} cm। বগা কাগজত ক'লা চিয়াঁহীৰে চহী কৰি স্পষ্ট ফটো তোলক। | |
| `sigPick` | Choose signature photo | চহীৰ ফটো বাছক | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | সাজু: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ফাইলটো {min}ৰ পৰা {max} KB-ৰ ভিতৰত আনিব পৰা নগ'ল। আৰু স্পষ্ট ফটো এখন দি চেষ্টা কৰক। | |
| `sigDownload` | Download JPEG | JPEG ডাউনলোড কৰক | |
| `photoTitle` | Your photograph | আপোনাৰ ফটো | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC-এ এতিয়া আপলোড কৰা ফটো নলয়: আবেদন ফৰ্মে আপোনাৰ কেমেৰাৰে লাইভ ফটো তোলে, আৰু পুৰণি ফটোৰ ফটো তুলিলে ফৰ্ম বাতিল হয়। ভাল পোহৰ, সাধাৰণ বেকগ্ৰাউণ্ড, চকুৰ সমান উচ্চতাত কেমেৰা ৰাখক; টুপী, মাস্ক বা চশমা নিপিন্ধিব। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | উৎস: জাননীৰ অনুচ্ছেদ 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | বয়স কেলকুলেটৰ | |
| `ageOn` | Age on | যি তাৰিখত বয়স | |
| `ageResult` | {y} years, {m} months, {d} days | {y} বছৰ, {m} মাহ, {d} দিন | |
| `ageInvalid` | Date of birth must be before that date. | জন্ম তাৰিখ সেই তাৰিখৰ আগৰ হ'ব লাগিব। | |
| `docsTitle` | Certificate formats | চাৰ্টিফিকেটৰ ফৰ্মেট | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | কেটেগৰী, দিব্যাংগতা আৰু প্ৰাক্তন সৈনিকৰ চাৰ্টিফিকেটৰ বাবে SSC-ৰ ফৰ্মেট। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | এতিয়াও একো ছেভ কৰা নাই। যিকোনো জাননীত ছেভ টিপক। | |
| `followedExams` | Exams you follow | আপুনি ফল' কৰা পৰীক্ষাসমূহ | |
| `chooseLanguage` | Choose your language | আপোনাৰ ভাষা বাছক | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | জাননীৰ শিৰোনাম নিযুক্তিকৰ্তা সংস্থাই যি ভাষাত প্ৰকাশ কৰে সেই ভাষাতে থাকে (বেছিভাগ ইংৰাজী বা হিন্দী)। | |
| `qaLatest` | Latest jobs | শেহতীয়া চাকৰি | |
| `qaResults` | Results | ৰিজাল্ট | |
| `qaAdmit` | Admit cards | এডমিট কাৰ্ড | |
| `qaAnswer` | Answer keys | আনছাৰ কী | |
| `qaEligibility` | Check eligibility | যোগ্যতা চেক | |
| `qaRoll` | Result by roll no. | ৰোল নং-ৰে ৰিজাল্ট | |
| `homeSectors` | Jobs by sector | ছেক্টৰ অনুসৰি চাকৰি | |
| `homeStates` | Jobs by state | ৰাজ্য অনুসৰি চাকৰি | |
| `homeClosing` | Closing soon | সোনকালে বন্ধ হ'ব | |
| `homeChannels` | Get every update on WhatsApp or Telegram | সকলো আপডেট WhatsApp বা Telegram-ত পাওক | |
| `homeChannelsNote` | Free. One message per update, with the official link. | বিনামূলীয়া। প্ৰতিটো আপডেটত এটা মেছেজ, অফিচিয়েল লিংকসহ। | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | আনঅফিচিয়েল · প্ৰতিটো আপডেটত অফিচিয়েল ৱেবছাইটৰ লিংক | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | আৰক্ষী | |
| `sector_defence` | Defence & CAPF | প্ৰতিৰক্ষা আৰু CAPF | |
| `sector_railway` | Railway | ৰে'লৱে | |
| `sector_bank` | Bank | বেংক | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | বিজ্ঞান আৰু মহাকাশ | |
| `sector_state` | State govt | ৰাজ্যিক চৰকাৰ | |
| `sector_teaching` | Teaching | শিক্ষকতা | |
| `state_all` | All India | সৰ্বভাৰতীয় | |
| `state_UP` | Uttar Pradesh | উত্তৰ প্ৰদেশ | |
| `state_BR` | Bihar | বিহাৰ | |
| `state_HR` | Haryana | হাৰিয়ানা | |
| `anySector` | All sectors | সকলো ছেক্টৰ | |
| `anyState` | All states | সকলো ৰাজ্য | |
| `jobsSector` | {name} jobs | {name}ৰ চাকৰি | |
| `jobsState` | Government jobs in {name} | {name}ত চৰকাৰী চাকৰি | |
| `jobsKind` | {name}: latest | {name}: শেহতীয়া | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | এই ছেক্টৰৰ এতিয়াও একো নাই। আমি কোনো নিযুক্তিকৰ্তা সংস্থাৰ অফিচিয়েল ৱেবছাইট পৰীক্ষা কৰাৰ পিছতহে তাক যোগ কৰোঁ, গতিকে এই তালিকা বাঢ়ি যাব। | |
| `centralJobs` | Central govt (all India) | কেন্দ্ৰীয় চৰকাৰ (সৰ্বভাৰতীয়) | |
| `recruitersTitle` | Recruiters we follow | আমি ফল' কৰা নিযুক্তিকৰ্তা সংস্থাসমূহ | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | এই অফিচিয়েল ৱেবছাইটবোৰৰ পৰা আপডেট লোৱা হয়। খুলিবলৈ যিকোনো এটাত টিপক। | |
| `moreUpdates` | See all updates | সকলো আপডেট চাওক | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | বয়স কম: {date}ত আপোনাৰ বয়স অন্ততঃ {n} হ'ব লাগিব। | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | বয়সৰ সীমা পাৰ হৈছে: {date}ত আপোনাৰ বয়স {n}তকৈ কম হ'ব লাগিব (ৰেহাইসহ)। | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | সাধাৰণ বয়সৰ সীমা পাৰ হৈছে। প্ৰাক্তন সৈনিক / চৰকাৰী কৰ্মচাৰী / বিধৱাৰ ৰেহাইৰ ভিতৰত পৰিব পাৰে: জাননী {ref} চাওক। | |
| `r_education_graduate` | Needs a bachelor's degree. | স্নাতক ডিগ্ৰী লাগিব। | |
| `r_education_12` | Needs Class 12 pass. | দ্বাদশ শ্ৰেণী উত্তীৰ্ণ হ'ব লাগিব। | |
| `r_education_10` | Needs Class 10 pass. | দশম শ্ৰেণী উত্তীৰ্ণ হ'ব লাগিব। | |
| `r_result_needed_by` | Your result must be out by {date}. | {date}ৰ ভিতৰত আপোনাৰ ৰিজাল্ট ওলাব লাগিব। | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ৰিজাল্টৰ অপেক্ষাত থকা প্ৰাৰ্থীয়ে আবেদন কৰিব পাৰিব বুলি জাননীত কোৱা নাই; {date}ৰ ভিতৰত আপোনাৰ যোগ্যতা থাকিব লাগিব। | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | এই জাননীত PwBD-ৰ বাবে বয়সত কোনো ৰেহাই দিয়া নাই: জাননী চাই লওক। | |
| `r_gender_male` | Only for male candidates. | কেৱল পুৰুষ প্ৰাৰ্থীৰ বাবে। | |
| `r_gender_female` | Only for female candidates. | কেৱল মহিলা প্ৰাৰ্থীৰ বাবে। | |
| `r_gender_other` | Only for {g} candidates. | কেৱল {g} প্ৰাৰ্থীৰ বাবে। | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | মাধ্যমিক স্তৰত ৰাজ্যৰ চৰকাৰী/আঞ্চলিক ভাষা পঢ়া থাকিব লাগিব। | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | দ্বাদশ শ্ৰেণীত গণিতত 60% বা ডিগ্ৰীত এটা বিষয় হিচাপে পৰিসংখ্যা থাকিব লাগিব। | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ডিগ্ৰী পৰিসংখ্যা, গণিত, অৰ্থনীতি, ডেমোগ্ৰাফী, পপুলেচন ষ্টাডিজ, অপাৰেচনছ ৰিছাৰ্চ, IT, কম্পিউটাৰ ছায়েন্স/ইঞ্জিনিয়াৰিং/এপ্লিকেচনছ, ডেটা ছায়েন্স বা AI-ত হ'ব লাগিব। | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | শাৰীৰিক পৰীক্ষাৰ দিনা বৈধ LMV (মটৰচাইকেল আৰু গাড়ী) ড্ৰাইভিং লাইচেন্স থাকিব লাগিব। | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | পদাৰ্থ বিজ্ঞান, ৰসায়ন বিজ্ঞান আৰু গণিতৰ সৈতে B.Sc., বা দশম শ্ৰেণী উত্তীৰ্ণ আৰু মেকানিকেল, চিভিল, ইলেক্ট্ৰিকেল বা তালিকাভুক্ত কোনো ইঞ্জিনিয়াৰিং শাখাত 3 বছৰীয়া ডিপ্লোমা লাগিব। | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | শাৰীৰিক আৰু মেডিকেল মাপকাঠী প্ৰযোজ্য (জাননী চাওক)। | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | বৰ্ডাৰ ৰোডছ অৰ্গেনাইজেচনৰ খালী পদ: কেৱল পুৰুষ, শাৰীৰিক মাপকাঠী প্ৰযোজ্য। | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | কিছুমান কাৰ্যালয়ত (উপভোক্তা পৰিক্ৰমা আৰু খাদ্য, SSC, সংস্কৃতি) এই পদৰ বাবে গণিতসহ দ্বাদশ শ্ৰেণী বিজ্ঞান লাগিব। | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | নিৰ্বাচনৰ অংশ হিচাপে ষ্টেনোগ্ৰাফী স্কিল টেষ্ট হ'ব। | |
| `listWord` | Lists: {n} | তালিকা: {n} | |
