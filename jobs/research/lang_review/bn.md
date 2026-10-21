# বাংলা (bn) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/bn.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/bn.js)

| 키 | English | বাংলা | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | সরকারি জব অ্যালার্ট | |
| `tabHome` | Home | হোম | |
| `tabMore` | More | আরও | |
| `loading` | Loading… | লোড হচ্ছে… | |
| `homeComing` | Exam dates in the next 30 days | আগামী 30 দিনের পরীক্ষার তারিখ | |
| `homeLatest` | Latest updates | লেটেস্ট আপডেট | |
| `inDays` | in {n} days | {n} দিন পরে | |
| `onNow` | on now | চলছে | |
| `today` | today | আজ | |
| `theme` | Theme | থিম | |
| `themeAuto` | Auto | অটো | |
| `themeLight` | Light | লাইট | |
| `themeDark` | Dark | ডার্ক | |
| `language` | Language | ভাষা | |
| `howToApply` | How to apply | কীভাবে আবেদন করবেন | |
| `documents` | Keep these documents ready | এই ডকুমেন্টগুলো তৈরি রাখুন | |
| `helplineTitle` | SSC helplines | SSC হেল্পলাইন | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ফর্ম পূরণে বা কম্পিউটার-ভিত্তিক পরীক্ষায় কোনো সমস্যা হলে। | |
| `typingTitle` | Typing / data entry speed practice | টাইপিং / ডেটা এন্ট্রি স্পিড প্র্যাকটিস | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | নিচের অনুচ্ছেদটি টাইপ করুন। প্রথম কী চাপলেই টাইমার চালু হবে। স্পিড দেখানো হয় প্রতি ঘণ্টায় কী ডিপ্রেশনে, ঠিক যেভাবে SSC বলে। | |
| `typingTest` | Standard | স্ট্যান্ডার্ড | |
| `typingMinutes` | Time | সময় | |
| `typingFull` | Full ({n} min) | পুরো ({n} মিনিট) | |
| `typingStart` | Start again | আবার শুরু করুন | |
| `typingDone` | Time is up. | সময় শেষ। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | ঘণ্টায় {kdph} কী ডিপ্রেশন (≈ {wpm} w.p.m.), {accuracy}% নির্ভুল, {typed}টির মধ্যে {correct}টি ঠিক | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name}-এর স্পিড ({kdph}/ঘণ্টা) পূরণ হয়েছে। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name}-এর স্পিডের ({kdph}/ঘণ্টা) চেয়ে কম: প্র্যাকটিস চালিয়ে যান। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | শুধু প্র্যাকটিসের জন্য: SSC নিজের মূল্যায়ন নির্দেশিকা অনুযায়ী ভুল গণনা করে। হিন্দি টাইপিং প্র্যাকটিস এখানে নেই। | |
| `faqTitle` | Frequently asked | প্রায়ই জিজ্ঞাসিত প্রশ্ন | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}-এর আবেদনের শেষ তারিখ কবে? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | আবেদনের শেষ তারিখ {date}; ফি জমা দেওয়া যাবে {fee} পর্যন্ত। | |
| `faqAge` | What is the age limit for {exam}? | {exam}-এর বয়সসীমা কত? | |
| `faqFee` | What is the application fee? | আবেদন ফি কত? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | জেনারেল, OBC ও EWS প্রার্থীদের জন্য ₹{amount}। {exempt}: কোনো ফি লাগবে না। | |
| `faqSalary` | What is the salary? | বেতন কত? | |
| `faqExam` | When is the {exam} exam? | {exam} পরীক্ষা কবে? | |
| `faqVacancy` | How many vacancies are there? | কতগুলো শূন্যপদ আছে? | |
| `faqQual` | What qualification is needed? | কী যোগ্যতা লাগবে? | |
| `aboutTitle` | About this app | এই অ্যাপ সম্পর্কে | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ভারতে যাঁরা সরকারি চাকরির প্রস্তুতি নিচ্ছেন, তাঁদের জন্য একটি ফ্রি, আনঅফিশিয়াল সহায়ক। নোটিস, তারিখ ও নিয়ম নিয়োগকারী সংস্থার অফিশিয়াল ওয়েবসাইট ও নোটিস থেকে নেওয়া, রেফারেন্স সহ। সাইন-আপ লাগে না, কোনো ব্যক্তিগত তথ্য নেওয়া হয় না: আপনার তথ্য ও রোল নম্বর এই ফোনেই থাকে। | |
| `sourceRef` | Source: {ref} | সূত্র: {ref} | |
| `toastSaved` | Saved | সেভ হয়েছে | |
| `toastRemoved` | Removed | সরানো হয়েছে | |
| `toastFollow` | You follow this exam | আপনি এই পরীক্ষাটি ফলো করছেন | |
| `toastUnfollow` | Unfollowed | আনফলো করা হয়েছে | |
| `settings` | Settings | সেটিংস | |
| `toolsTitle` | Tools | টুলস | |
| `tabResult` | Result | রেজাল্ট | |
| `tabForMe` | For me | আমার জন্য | |
| `resultTitle` | Check your result by roll number | রোল নম্বর দিয়ে রেজাল্ট দেখুন | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | গত 120 দিনে SSC যে রেজাল্ট লিস্ট প্রকাশ করেছে, সেগুলোতে খোঁজে, বড় PDF ডাউনলোড না করেই। একটি ছোট ফাইল আনতে শুধু আপনার রোল নম্বরের শেষ দুটি সংখ্যা ব্যবহার করা হয়। | |
| `rollNo` | Roll number | রোল নম্বর | |
| `resultExam` | Result | রেজাল্ট | |
| `find` | Find | খুঁজুন | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | অ্যাডমিট কার্ডে যেমন ছাপা আছে, ঠিক সেভাবে রোল নম্বর লিখুন (8-14 সংখ্যা)। | |
| `foundIn` | Found in: {list} | পাওয়া গেছে: {list} | |
| `notIn` | Not in: {list} | নেই: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | এই লিস্টগুলোতে আপনার রোল নম্বর নেই। শুধু এর মানে এই নয় যে আপনি কোয়ালিফাই করেননি: নিচের অফিশিয়াল PDF দেখে নিন। | |
| `officialFiles` | Official files of this notice | এই নোটিসের অফিশিয়াল ফাইল | |
| `listCount` | {n} roll numbers | {n}টি রোল নম্বর | |
| `noLists` | No result lists to search right now. | এই মুহূর্তে খোঁজার মতো কোনো রেজাল্ট লিস্ট নেই। | |
| `forMeTitle` | Exams that suit you | আপনার উপযোগী পরীক্ষা | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | একবার আপনার তথ্য দিন। যেসব SSC পরীক্ষায় আবেদন করতে পারবেন, সেগুলো, তাদের স্ট্যাটাস আর এরপর কী আছে, সব দেখতে পাবেন। | |
| `forMeEdit` | Edit my details | আমার তথ্য বদলান | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | আপনার যোগ্যতা অনুযায়ী (নিয়ম এখনও যোগ হয়নি: নোটিস পড়ুন) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | আপনার যোগ্যতা অনুযায়ী আসন্ন পরীক্ষা (সম্ভাব্য) | |
| `saveDetails` | Show my exams | আমার পরীক্ষা দেখান | |
| `whenTitle` | What has happened and what is next | কী হয়ে গেছে আর এরপর কী | |
| `st_done` | Done | হয়ে গেছে | |
| `st_now` | Now | এখন | |
| `st_next` | Next | এরপর | |
| `st_later` | Later | পরে | |
| `stage_notice` | Notice published | নোটিস প্রকাশিত | |
| `stage_apply` | Online application | অনলাইন আবেদন | |
| `stage_admit_card` | Admit card / exam city | অ্যাডমিট কার্ড / পরীক্ষার শহর | |
| `stage_answer_key` | Answer key | আনসার কী | |
| `stage_result` | Result | রেজাল্ট | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC এখনও তারিখ ঘোষণা করেনি | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | বেসিক পে মাসে ₹{from}–{to} (লেভেল {l}; ভাতা আলাদা) | |
| `savedLink` | ★ Saved | ★ সেভ করা | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | এটি কোনো সরকারি ওয়েবসাইট নয় এবং কোনো নিয়োগকারী সংস্থা বা সরকারের সঙ্গে যুক্ত নয়। এখানকার সব তথ্য নিয়োগকারী সংস্থার অফিশিয়াল ওয়েবসাইট থেকে নেওয়া, আর প্রতিটি আপডেটে অফিশিয়াল ফাইল বা পেজের লিংক আছে। আবেদন করার আগে সবসময় অফিশিয়াল নোটিস দেখে নিন। | |
| `tabExams` | Exams | পরীক্ষা | |
| `filters` | Filters | ফিল্টার | |
| `kindAll` | All | সব | |
| `kindRecruitment` | New jobs | নতুন চাকরি | |
| `kindResult` | Results | রেজাল্ট | |
| `kindAdmit` | Admit cards | অ্যাডমিট কার্ড | |
| `kindAnswer` | Answer keys | আনসার কী | |
| `kindAmendment` | Changes | পরিবর্তন | |
| `kindVacancy` | Vacancies | শূন্যপদ | |
| `kind_recruitment` | New job | নতুন চাকরি | |
| `kind_amendment` | Change | পরিবর্তন | |
| `kind_result` | Result | রেজাল্ট | |
| `kind_answer_key` | Answer key | আনসার কী | |
| `kind_admit_card` | Admit card | অ্যাডমিট কার্ড | |
| `kind_vacancy` | Vacancy | শূন্যপদ | |
| `kind_schedule` | Exam dates | পরীক্ষার তারিখ | |
| `kind_notice` | Notice | নোটিস | |
| `search` | Search jobs, exams, results | চাকরি, পরীক্ষা, রেজাল্ট খুঁজুন | |
| `allExams` | All exams | সব পরীক্ষা | |
| `myExams` | My exams only | শুধু আমার পরীক্ষা | |
| `anyQualification` | Any qualification | যেকোনো যোগ্যতা | |
| `qual_10` | Class 10 | দশম শ্রেণি | |
| `qual_12` | Class 12 | দ্বাদশ শ্রেণি | |
| `qual_diploma` | Diploma | ডিপ্লোমা | |
| `qual_graduate` | Bachelor's degree | স্নাতক ডিগ্রি | |
| `qual_postgraduate` | Master's degree | স্নাতকোত্তর ডিগ্রি | |
| `departmental` | Departmental exams | বিভাগীয় পরীক্ষা | |
| `updated` | Updated {time} IST | আপডেট: {time} IST | |
| `newSince` | {n} new since your last visit | আপনার শেষ ভিজিটের পর {n}টি নতুন | |
| `cutoffTitle` | Cut-off marks | কাট-অফ মার্কস | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC কাট-অফ দেয় শুধু রেজাল্টের বিবরণের ভেতরে। সংখ্যাগুলো এখানে কপি করা হয়নি (একটা ভুল সংখ্যাও বিভ্রান্ত করতে পারে): প্রতিটি লিংক SSC-র PDF ঠিক সেই পেজে খোলে, যেখান থেকে কাট-অফের বিবরণ শুরু। | |
| `cutoffFrom` | Open at page {p} | পেজ {p}-এ খুলুন | |
| `cutoffPages` | mentioned on p. {pages} | পৃ. {pages}-এ উল্লেখ আছে | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | গত 120 দিনের রেজাল্টের বিবরণে কোনো কাট-অফ পাওয়া যায়নি। | |
| `alertsTitle` | Alerts on this phone | এই ফোনে অ্যালার্ট | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | আপনি যে পরীক্ষা ফলো করেন, তার কোনো খবর এলে একটি নোটিফিকেশন, আর আবেদন বন্ধ হওয়ার দুই দিন আগে আরেকটি। | |
| `alertsOn` | Turn on alerts | অ্যালার্ট চালু করুন | |
| `alertsOff` | Turn off alerts | অ্যালার্ট বন্ধ করুন | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | আগে একটি পরীক্ষা ফলো করুন (পরীক্ষা → ফলো)। | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n}টি পরীক্ষার জন্য চালু। কখন চেক করবে তা Chrome ঠিক করে, অ্যাপটি বেশি খুললে বেশি বার চেক করে, তাই খবর পোস্ট হওয়ার কয়েক ঘণ্টা পরেও আপনার কাছে পৌঁছাতে পারে। | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | এই সাইটের নোটিফিকেশন ব্লক করা আছে। ব্রাউজারের সাইট সেটিংসে গিয়ে অনুমতি দিন। | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | এই ব্রাউজার ব্যাকগ্রাউন্ডে চেক করতে পারে না। Android-এ আগে Chrome থেকে অ্যাপটি ইনস্টল করুন। নাহলে প্রতিটি পরীক্ষার পেজে ক্যালেন্ডার রিমাইন্ডার ব্যবহার করুন। | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | আপনার পরীক্ষাগুলোর আরও {n}টি আপডেট | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: আবেদন বন্ধ হবে {n} দিন পরে | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: আজ আবেদনের শেষ দিন | |
| `installTitle` | Install the app | অ্যাপটি ইনস্টল করুন | |
| `installBtn` | Install | ইনস্টল | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | অ্যাপের মতো খোলে, অফলাইনে চলে, আর Android-এ অ্যালার্ট পাওয়া যায়। | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone-এ: Share-এ ট্যাপ করুন, তারপর "Add to Home Screen"। | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ফলো করছেন। ফোনে অ্যালার্ট পেতে: আরও → অ্যালার্ট চালু করুন | |
| `rollPlaceholder` | Roll number on your admit card | অ্যাডমিট কার্ডে থাকা রোল নম্বর | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n}টি পরীক্ষার জন্য চালু। SSC-র নোটিস সাধারণত পোস্ট হওয়ার এক ঘণ্টার মধ্যে পৌঁছে যায়; রেজাল্ট ও অন্য বোর্ডের নোটিস আসতে 3 ঘণ্টা পর্যন্ত লাগতে পারে। | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | এই ব্রাউজার অ্যালার্ট দেখাতে পারে না। iPhone-এ আগে অ্যাপটি হোম স্ক্রিনে যোগ করুন (Share → Add to Home Screen), তারপর সেখান থেকে খুলুন। | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | অ্যালার্ট চালু করা গেল না। ইন্টারনেট কানেকশন দেখে আবার চেষ্টা করুন। | |
| `allBoards` | All recruiters | সব নিয়োগকারী সংস্থা | |
| `newBadge` | NEW | নতুন | |
| `nothing` | Nothing here in the last 120 days. | গত 120 দিনে এখানে কিছু নেই। | |
| `loadError` | Couldn't load. Check your internet connection and try again. | লোড হয়নি। ইন্টারনেট কানেকশন দেখে আবার চেষ্টা করুন। | |
| `offline` | Offline: showing the copy saved on this phone. | অফলাইন: এই ফোনে সেভ করা কপি দেখানো হচ্ছে। | |
| `save` | Save | সেভ | |
| `saved` | Saved | সেভ হয়েছে | |
| `share` | Share | শেয়ার | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | অফিশিয়াল পেজ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ অফিশিয়াল লিংক: {url} ⏎ {app} থেকে (সরকারি সাইট নয়) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | প্রতিটি পরীক্ষার লেটেস্ট নোটিস, তারিখ, ফি, বয়সসীমা আর তারপরের সব নোটিস। | |
| `follow` | Follow | ফলো | |
| `following` | Following | ফলো করছেন | |
| `minQual` | Qualification: {q} | যোগ্যতা: {q} | |
| `qualUnknown` | Qualification: see the Notice | যোগ্যতা: নোটিস দেখুন | |
| `calendarTitle` | SSC's tentative calendar | SSC-র সম্ভাব্য ক্যালেন্ডার | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | এগুলো পরিকল্পনা, নোটিস নয়: আসল তারিখ প্রতিটি নোটিসে থাকে এবং আলাদা হতে পারে। | |
| `resourcesTitle` | Official resources | অফিশিয়াল রিসোর্স | |
| `res_syllabus` | Syllabus | সিলেবাস | |
| `res_vacancy` | Vacancies (tentative / final) | শূন্যপদ (সম্ভাব্য / চূড়ান্ত) | |
| `res_previous_papers` | Previous year papers | আগের বছরের প্রশ্নপত্র | |
| `res_certificate_formats` | Certificate formats | সার্টিফিকেটের ফরম্যাট | |
| `res_instructions` | Instructions | নির্দেশাবলি | |
| `back` | ← All exams | ← সব পরীক্ষা | |
| `latestNotice` | Latest Notice | লেটেস্ট নোটিস | |
| `importantDates` | Important dates | গুরুত্বপূর্ণ তারিখ | |
| `applyWindow` | Apply online | অনলাইন আবেদন | |
| `feeLastDate` | Fee payment last date | ফি জমার শেষ তারিখ | |
| `correctionWindow` | Form correction window | ফর্ম সংশোধনের সময় | |
| `tentative` | tentative | সম্ভাব্য | |
| `applicationFee` | Application fee | আবেদন ফি | |
| `feeGeneral` | General / OBC / EWS | জেনারেল / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ছাড় (₹0) | |
| `exempt_female` | Women | মহিলা | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | প্রাক্তন সৈনিক | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | সংশোধন: প্রথমবার ₹{a}, দ্বিতীয়বার ₹{b} (সব প্রার্থী) | |
| `ageLimit` | Age limit (as on {date}) | বয়সসীমা ({date} অনুযায়ী) | |
| `ageBand` | {band} years: born {from} to {to} | {band} বছর: জন্ম {from} থেকে {to}-এর মধ্যে | |
| `relaxation` | Upper age relaxation | ঊর্ধ্ব বয়সসীমায় ছাড় | |
| `relaxYears` | {n} years | {n} বছর | |
| `notComputed` | Not calculated here: {list} | এখানে হিসাব করা হয়নি: {list} | |
| `vacancies` | Vacancies | শূন্যপদ | |
| `vacancyCount` | About {n} ({tentative}) | প্রায় {n} ({tentative}) | |
| `posts` | Posts | পদ | |
| `importantLinks` | Important links | গুরুত্বপূর্ণ লিংক | |
| `applyOnline` | Apply online (ssc.gov.in) | অনলাইনে আবেদন (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | অফিশিয়াল নোটিস (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} তারিখের পরিবর্তন | |
| `allNotices` | All notices for this exam | এই পরীক্ষার সব নোটিস | |
| `addCalendar` | Add dates to my calendar | তারিখগুলো আমার ক্যালেন্ডারে যোগ করুন | |
| `checkThis` | Check my eligibility | আমার যোগ্যতা চেক করুন | |
| `status_open` | Applications open till {date} | আবেদন চলছে {date} পর্যন্ত | |
| `status_upcoming` | Applications open on {date} | আবেদন শুরু {date} থেকে | |
| `status_closed` | Applications closed on {date} | আবেদন বন্ধ হয়েছে {date}-এ | |
| `daysLeft` | {n} days left | আর {n} দিন বাকি | |
| `lastDay` | Last day today | আজ শেষ দিন | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | এই পরীক্ষার যোগ্যতার নিয়ম এখনও যোগ হয়নি: নোটিস পড়ুন। | |
| `examLabel` | Exam | পরীক্ষা | |
| `dob` | Date of birth (as in Class 10 certificate) | জন্ম তারিখ (দশম শ্রেণির সার্টিফিকেট অনুযায়ী) | |
| `category` | Category | ক্যাটাগরি | |
| `catUR` | General (UR) | জেনারেল (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (নন-ক্রিমি লেয়ার) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | সর্বোচ্চ যোগ্যতা | |
| `edu10` | Class 10 | দশম শ্রেণি | |
| `edu12` | Class 12 | দ্বাদশ শ্রেণি | |
| `eduGraduate` | Bachelor's degree or higher | স্নাতক বা তার বেশি | |
| `gender` | Gender | লিঙ্গ | |
| `male` | Male | পুরুষ | |
| `female` | Female | মহিলা | |
| `other` | Other | অন্যান্য | |
| `appearing` | Final exam of that level taken, result not out yet | ওই স্তরের ফাইনাল পরীক্ষা দিয়েছি, রেজাল্ট এখনও বেরোয়নি | |
| `pwbd` | Person with benchmark disability (PwBD) | বেঞ্চমার্ক প্রতিবন্ধী ব্যক্তি (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | প্রাক্তন সৈনিক, কেন্দ্রীয় সরকারি কর্মচারী, অথবা বিধবা/বিবাহবিচ্ছিন্ন মহিলা | |
| `check` | Check | চেক করুন | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | আপনার উত্তর এই ডিভাইসেই থাকে। কোথাও কিছু পাঠানো হয় না। | |
| `enterDob` | Enter your date of birth. | আপনার জন্ম তারিখ দিন। | |
| `closedNote` | Shown so you can prepare for the next cycle. | পরের সাইকেলের প্রস্তুতির জন্য দেখানো হচ্ছে। | |
| `canApply` | {n} can apply | {n}টিতে আবেদন করা যাবে | |
| `toCheck` | {n} to check | {n}টি চেক করতে হবে | |
| `notEligible` | {n} not eligible | {n}টিতে যোগ্য নন | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ফি: {fee}। বয়সে ছাড় ধরা হয়েছে: {n} বছর। বয়স গোনা হয় {date} তারিখে। | |
| `feeNone` | none (exempt) | নেই (ছাড়) | |
| `st_eligible` | You can apply | আবেদন করতে পারবেন | |
| `st_check` | Check one condition | একটি শর্ত চেক করুন | |
| `st_ineligible` | Not eligible | যোগ্য নন | |
| `payLevel` | Pay Level {l} · Group {g} | পে লেভেল {l} · গ্রুপ {g} | |
| `groupOnly` | Group {g} | গ্রুপ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | জন্ম {from} থেকে {to}-এর মধ্যে (আপনার জন্য) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date} তারিখের নোটিস থেকে নেওয়া নিয়ম ({checked}-এ যাচাই করা)। | |
| `sigTitle` | Signature for SSC forms | SSC ফর্মের জন্য সিগনেচার | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC চায় {min}-{max} KB-এর JPEG, মাপ প্রায় {w} cm × {h} cm। সাদা কাগজে কালো কালিতে সই করে পরিষ্কার ছবি তুলুন। | |
| `sigPick` | Choose signature photo | সিগনেচারের ছবি বাছুন | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | তৈরি: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ফাইলটি {min} থেকে {max} KB-এর মধ্যে আনা গেল না। আরও স্পষ্ট ছবি দিয়ে চেষ্টা করুন। | |
| `sigDownload` | Download JPEG | JPEG ডাউনলোড করুন | |
| `photoTitle` | Your photograph | আপনার ছবি | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC আর আপলোড করা ছবি নেয় না: আবেদন ফর্ম আপনার ক্যামেরা দিয়ে সরাসরি ছবি তোলে, আর পুরনো ছবির ছবি তুললে ফর্ম বাতিল হয়ে যায়। ভালো আলো, সাদামাটা ব্যাকগ্রাউন্ড, চোখের সমান উচ্চতায় ক্যামেরা রাখুন; টুপি, মাস্ক বা চশমা পরবেন না। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | সূত্র: নোটিস অনুচ্ছেদ 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | বয়স ক্যালকুলেটর | |
| `ageOn` | Age on | যে তারিখে বয়স | |
| `ageResult` | {y} years, {m} months, {d} days | {y} বছর, {m} মাস, {d} দিন | |
| `ageInvalid` | Date of birth must be before that date. | জন্ম তারিখ ওই তারিখের আগে হতে হবে। | |
| `docsTitle` | Certificate formats | সার্টিফিকেটের ফরম্যাট | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | ক্যাটাগরি, প্রতিবন্ধকতা ও প্রাক্তন সৈনিক সার্টিফিকেটের জন্য SSC-র ফরম্যাট। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | এখনও কিছু সেভ করা হয়নি। যেকোনো নোটিসে সেভ-এ ট্যাপ করুন। | |
| `followedExams` | Exams you follow | আপনি যে পরীক্ষাগুলো ফলো করেন | |
| `chooseLanguage` | Choose your language | আপনার ভাষা বাছুন | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | নোটিসের শিরোনাম নিয়োগকারী সংস্থা যে ভাষায় প্রকাশ করে, সেই ভাষাতেই থাকে (বেশিরভাগ ইংরেজি বা হিন্দি)। | |
| `qaLatest` | Latest jobs | লেটেস্ট চাকরি | |
| `qaResults` | Results | রেজাল্ট | |
| `qaAdmit` | Admit cards | অ্যাডমিট কার্ড | |
| `qaAnswer` | Answer keys | আনসার কী | |
| `qaEligibility` | Check eligibility | যোগ্যতা চেক | |
| `qaRoll` | Result by roll no. | রোল নং দিয়ে রেজাল্ট | |
| `homeSectors` | Jobs by sector | সেক্টর অনুযায়ী চাকরি | |
| `homeStates` | Jobs by state | রাজ্য অনুযায়ী চাকরি | |
| `homeClosing` | Closing soon | শীঘ্রই বন্ধ হচ্ছে | |
| `homeChannels` | Get every update on WhatsApp or Telegram | সব আপডেট পান WhatsApp বা Telegram-এ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | ফ্রি। প্রতিটি আপডেটে একটি মেসেজ, অফিশিয়াল লিংক সহ। | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | আনঅফিশিয়াল · প্রতিটি আপডেটে অফিশিয়াল ওয়েবসাইটের লিংক | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | পুলিশ | |
| `sector_defence` | Defence & CAPF | ডিফেন্স ও CAPF | |
| `sector_railway` | Railway | রেলওয়ে | |
| `sector_bank` | Bank | ব্যাংক | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | বিজ্ঞান ও মহাকাশ | |
| `sector_state` | State govt | রাজ্য সরকার | |
| `sector_teaching` | Teaching | শিক্ষকতা | |
| `state_all` | All India | সর্বভারতীয় | |
| `state_UP` | Uttar Pradesh | উত্তরপ্রদেশ | |
| `state_BR` | Bihar | বিহার | |
| `state_HR` | Haryana | হরিয়ানা | |
| `anySector` | All sectors | সব সেক্টর | |
| `anyState` | All states | সব রাজ্য | |
| `jobsSector` | {name} jobs | {name} চাকরি | |
| `jobsState` | Government jobs in {name} | {name}-এ সরকারি চাকরি | |
| `jobsKind` | {name}: latest | {name}: লেটেস্ট | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | এই সেক্টরের এখনও কিছু নেই। কোনো নিয়োগকারী সংস্থার অফিশিয়াল ওয়েবসাইট যাচাই করার পরেই আমরা তাকে যোগ করি, তাই এই লিস্ট বাড়বে। | |
| `centralJobs` | Central govt (all India) | কেন্দ্রীয় সরকার (সর্বভারতীয়) | |
| `recruitersTitle` | Recruiters we follow | যেসব নিয়োগকারী সংস্থা আমরা ফলো করি | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | এই অফিশিয়াল ওয়েবসাইটগুলো থেকে আপডেট নেওয়া হয়। খুলতে যেকোনোটিতে ট্যাপ করুন। | |
| `moreUpdates` | See all updates | সব আপডেট দেখুন | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | বয়স কম: {date} তারিখে আপনার বয়স অন্তত {n} হতে হবে। | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | বয়সসীমা পেরিয়ে গেছে: {date} তারিখে আপনার বয়স {n}-এর কম হতে হবে (ছাড় ধরে)। | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | সাধারণ বয়সসীমা পেরিয়ে গেছে। প্রাক্তন সৈনিক / সরকারি কর্মচারী / বিধবাদের ছাড়ে এটা কভার হতে পারে: নোটিস {ref} দেখুন। | |
| `r_education_graduate` | Needs a bachelor's degree. | স্নাতক ডিগ্রি লাগবে। | |
| `r_education_12` | Needs Class 12 pass. | দ্বাদশ শ্রেণি পাস লাগবে। | |
| `r_education_10` | Needs Class 10 pass. | দশম শ্রেণি পাস লাগবে। | |
| `r_result_needed_by` | Your result must be out by {date}. | {date}-এর মধ্যে আপনার রেজাল্ট বেরোতে হবে। | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | রেজাল্টের অপেক্ষায় থাকা প্রার্থীরা আবেদন করতে পারবেন, নোটিসে এমন বলা নেই; {date}-এর মধ্যে আপনার যোগ্যতা থাকতে হবে। | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | এই নোটিসে PwBD-দের জন্য বয়সে কোনো ছাড় দেওয়া নেই: নোটিস দেখে নিন। | |
| `r_gender_male` | Only for male candidates. | শুধু পুরুষ প্রার্থীদের জন্য। | |
| `r_gender_female` | Only for female candidates. | শুধু মহিলা প্রার্থীদের জন্য। | |
| `r_gender_other` | Only for {g} candidates. | শুধু {g} প্রার্থীদের জন্য। | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | মাধ্যমিক স্তরে রাজ্যের সরকারি/আঞ্চলিক ভাষা পড়া থাকতে হবে। | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | দ্বাদশ শ্রেণিতে গণিতে 60% অথবা ডিগ্রিতে একটি বিষয় হিসেবে পরিসংখ্যান থাকতে হবে। | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ডিগ্রি হতে হবে পরিসংখ্যান, গণিত, অর্থনীতি, ডেমোগ্রাফি, পপুলেশন স্টাডিজ, অপারেশনস রিসার্চ, IT, কম্পিউটার সায়েন্স/ইঞ্জিনিয়ারিং/অ্যাপ্লিকেশনস, ডেটা সায়েন্স বা AI-তে। | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ফিজিক্যাল টেস্টের দিন বৈধ LMV (মোটরসাইকেল ও গাড়ি) ড্রাইভিং লাইসেন্স থাকতে হবে। | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | পদার্থবিদ্যা, রসায়ন ও গণিত সহ B.Sc., অথবা দশম শ্রেণি পাস এবং মেকানিক্যাল, সিভিল, ইলেকট্রিক্যাল বা তালিকাভুক্ত কোনো ইঞ্জিনিয়ারিং শাখায় 3 বছরের ডিপ্লোমা লাগবে। | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | শারীরিক ও মেডিক্যাল মানদণ্ড প্রযোজ্য (নোটিস দেখুন)। | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | বর্ডার রোডস অর্গানাইজেশনের শূন্যপদ: শুধু পুরুষ, শারীরিক মানদণ্ড প্রযোজ্য। | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | কিছু অফিসে (উপভোক্তা বিষয়ক ও খাদ্য, SSC, সংস্কৃতি) এই পদের জন্য গণিত সহ দ্বাদশ শ্রেণি বিজ্ঞান লাগবে। | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | নির্বাচনের অংশ হিসেবে স্টেনোগ্রাফি স্কিল টেস্ট হবে। | |
| `listWord` | Lists: {n} | লিস্ট: {n} | |
