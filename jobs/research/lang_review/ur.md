# اردو (ur) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/ur.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/ur.js)

| 키 | English | اردو | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | سرکاری جاب الرٹ | |
| `tabHome` | Home | ہوم | |
| `tabMore` | More | مزید | |
| `loading` | Loading… | لوڈ ہو رہا ہے… | |
| `homeComing` | Exam dates in the next 30 days | اگلے 30 دنوں کی امتحان کی تاریخیں | |
| `homeLatest` | Latest updates | تازہ اپڈیٹس | |
| `inDays` | in {n} days | {n} دن میں | |
| `onNow` | on now | ابھی جاری ہے | |
| `today` | today | آج | |
| `theme` | Theme | تھیم | |
| `themeAuto` | Auto | آٹو | |
| `themeLight` | Light | لائٹ | |
| `themeDark` | Dark | ڈارک | |
| `language` | Language | زبان | |
| `howToApply` | How to apply | اپلائی کیسے کریں | |
| `documents` | Keep these documents ready | یہ دستاویزات تیار رکھیں | |
| `helplineTitle` | SSC helplines | SSC ہیلپ لائن | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | فارم بھرنے میں یا کمپیوٹر بیسڈ امتحان میں کوئی مسئلہ ہو تو۔ | |
| `typingTitle` | Typing / data entry speed practice | ٹائپنگ / ڈیٹا انٹری اسپیڈ کی پریکٹس | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | نیچے دی گئی عبارت ٹائپ کریں۔ پہلا بٹن دباتے ہی ٹائمر شروع ہو جائے گا۔ اسپیڈ فی گھنٹہ کی اسٹروکس (key depressions per hour) میں دکھائی جاتی ہے، جیسے SSC بتاتا ہے۔ | |
| `typingTest` | Standard | اسٹینڈرڈ | |
| `typingMinutes` | Time | وقت | |
| `typingFull` | Full ({n} min) | پورا ({n} منٹ) | |
| `typingStart` | Start again | دوبارہ شروع کریں | |
| `typingDone` | Time is up. | وقت ختم۔ | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | فی گھنٹہ {kdph} کی اسٹروکس (≈ {wpm} w.p.m.)، {accuracy}% درستگی، {typed} میں سے {correct} صحیح | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} کی اسپیڈ ({kdph}/گھنٹہ) پوری ہو گئی۔ | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} کی اسپیڈ ({kdph}/گھنٹہ) سے کم: پریکٹس جاری رکھیں۔ | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | صرف پریکٹس کے لیے: SSC غلطیوں کو اپنی ایویلیوایشن گائیڈ لائنز کے مطابق جانچتا ہے۔ ہندی ٹائپنگ کی پریکٹس یہاں شامل نہیں ہے۔ | |
| `faqTitle` | Frequently asked | اکثر پوچھے جانے والے سوال | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} کے لیے اپلائی کرنے کی آخری تاریخ کیا ہے؟ | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | اپلائی کرنے کی آخری تاریخ {date} ہے؛ فیس {fee} تک جمع کی جا سکتی ہے۔ | |
| `faqAge` | What is the age limit for {exam}? | {exam} کے لیے عمر کی حد کیا ہے؟ | |
| `faqFee` | What is the application fee? | اپلیکیشن فیس کتنی ہے؟ | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | جنرل، OBC اور EWS امیدواروں کے لیے ₹{amount}۔ {exempt} کو کوئی فیس نہیں دینی۔ | |
| `faqSalary` | What is the salary? | تنخواہ کتنی ہے؟ | |
| `faqExam` | When is the {exam} exam? | {exam} کا امتحان کب ہے؟ | |
| `faqVacancy` | How many vacancies are there? | کتنی ویکینسی ہیں؟ | |
| `faqQual` | What qualification is needed? | کون سی تعلیمی قابلیت چاہیے؟ | |
| `aboutTitle` | About this app | اس ایپ کے بارے میں | |
| `privacyPolicy` | Privacy policy | رازداری کی پالیسی | |
| `termsOfUse` | Terms of use | شرائط و ضوابط | |
| `contactUs` | Contact us | رابطہ کریں | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | بھارت میں سرکاری نوکری کی تیاری کرنے والوں کے لیے ایک مفت، غیر سرکاری مددگار۔ نوٹس، تاریخیں اور اصول بھرتی کرنے والے اداروں کی آفیشل ویب سائٹس اور نوٹسز سے لیے گئے ہیں، حوالوں کے ساتھ۔ کوئی سائن اپ نہیں، کوئی ذاتی ڈیٹا جمع نہیں کیا جاتا: آپ کی تفصیلات اور رول نمبر اسی فون میں رہتے ہیں۔ | |
| `sourceRef` | Source: {ref} | ماخذ: {ref} | |
| `toastSaved` | Saved | محفوظ ہو گیا | |
| `toastRemoved` | Removed | ہٹا دیا گیا | |
| `toastFollow` | You follow this exam | آپ اس امتحان کو فالو کر رہے ہیں | |
| `toastUnfollow` | Unfollowed | ان فالو کر دیا | |
| `settings` | Settings | سیٹنگز | |
| `toolsTitle` | Tools | ٹولز | |
| `tabResult` | Result | رزلٹ | |
| `tabForMe` | For me | میرے لیے | |
| `resultTitle` | Check your result by roll number | رول نمبر سے اپنا رزلٹ دیکھیں | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | پچھلے 120 دنوں میں SSC کی جاری کردہ رزلٹ لسٹوں میں تلاش کرتا ہے، بڑی PDF ڈاؤن لوڈ کیے بغیر۔ ایک چھوٹی فائل لانے کے لیے آپ کے رول نمبر کے صرف آخری دو ہندسے استعمال ہوتے ہیں۔ | |
| `rollNo` | Roll number | رول نمبر | |
| `resultExam` | Result | رزلٹ | |
| `find` | Find | تلاش کریں | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | رول نمبر ویسے ہی لکھیں جیسے آپ کے ایڈمٹ کارڈ پر چھپا ہے (8-14 ہندسے)۔ | |
| `foundIn` | Found in: {list} | اس میں ملا: {list} | |
| `notIn` | Not in: {list} | اس میں نہیں: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | آپ کا رول نمبر ان لسٹوں میں نہیں ہے۔ صرف اس سے یہ مطلب نہیں کہ آپ کوالیفائی نہیں ہوئے: نیچے دی گئی آفیشل PDF ضرور دیکھیں۔ | |
| `officialFiles` | Official files of this notice | اس نوٹس کی آفیشل فائلیں | |
| `listCount` | {n} roll numbers | {n} رول نمبر | |
| `noLists` | No result lists to search right now. | ابھی تلاش کے لیے کوئی رزلٹ لسٹ نہیں ہے۔ | |
| `forMeTitle` | Exams that suit you | آپ کے لیے موزوں امتحانات | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | اپنی تفصیلات ایک بار بھر دیں۔ آپ ہر وہ SSC امتحان دیکھیں گے جس کے لیے آپ اپلائی کر سکتے ہیں، اس کا اسٹیٹس، اور آگے کیا ہونا ہے۔ | |
| `forMeEdit` | Edit my details | میری تفصیلات بدلیں | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | آپ کی قابلیت کے مطابق (اصول ابھی شامل نہیں کیے گئے: نوٹس پڑھیں) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | آپ کی قابلیت کے لیے آنے والے امتحانات (عارضی) | |
| `saveDetails` | Show my exams | میرے امتحانات دکھائیں | |
| `whenTitle` | What has happened and what is next | کیا ہو چکا ہے اور آگے کیا ہے | |
| `st_done` | Done | ہو گیا | |
| `st_now` | Now | ابھی | |
| `st_next` | Next | اگلا | |
| `st_later` | Later | بعد میں | |
| `stage_notice` | Notice published | نوٹس جاری | |
| `stage_apply` | Online application | آن لائن اپلیکیشن | |
| `stage_admit_card` | Admit card / exam city | ایڈمٹ کارڈ / امتحان کا شہر | |
| `stage_answer_key` | Answer key | آنسر کی | |
| `stage_result` | Result | رزلٹ | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC نے ابھی تاریخ کا اعلان نہیں کیا | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | بیسک پے ₹{from}–{to} ماہانہ (لیول {l}؛ الاؤنس الگ سے) | |
| `savedLink` | ★ Saved | ★ محفوظ | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | یہ سرکاری ویب سائٹ نہیں ہے اور کسی بھرتی ادارے یا حکومت سے منسلک نہیں ہے۔ یہاں کی ہر چیز بھرتی کرنے والوں کی آفیشل ویب سائٹس سے لی گئی ہے، اور ہر اپڈیٹ کے ساتھ آفیشل فائل یا پیج کا لنک ہے۔ اپلائی کرنے سے پہلے ہمیشہ آفیشل نوٹس ضرور دیکھیں۔ | |
| `tabExams` | Exams | امتحانات | |
| `filters` | Filters | فلٹر | |
| `kindAll` | All | سب | |
| `kindRecruitment` | New jobs | نئی نوکریاں | |
| `kindResult` | Results | رزلٹ | |
| `kindAdmit` | Admit cards | ایڈمٹ کارڈ | |
| `kindAnswer` | Answer keys | آنسر کی | |
| `kindAmendment` | Changes | تبدیلیاں | |
| `kindVacancy` | Vacancies | ویکینسی | |
| `kind_recruitment` | New job | نئی نوکری | |
| `kind_amendment` | Change | تبدیلی | |
| `kind_result` | Result | رزلٹ | |
| `kind_answer_key` | Answer key | آنسر کی | |
| `kind_admit_card` | Admit card | ایڈمٹ کارڈ | |
| `kind_vacancy` | Vacancy | ویکینسی | |
| `kind_schedule` | Exam dates | امتحان کی تاریخیں | |
| `kind_notice` | Notice | نوٹس | |
| `search` | Search jobs, exams, results | نوکری، امتحان، رزلٹ تلاش کریں | |
| `allExams` | All exams | تمام امتحانات | |
| `myExams` | My exams only | صرف میرے امتحانات | |
| `anyQualification` | Any qualification | کوئی بھی قابلیت | |
| `qual_10` | Class 10 | دسویں | |
| `qual_12` | Class 12 | بارہویں | |
| `qual_diploma` | Diploma | ڈپلوما | |
| `qual_graduate` | Bachelor's degree | گریجویشن | |
| `qual_postgraduate` | Master's degree | پوسٹ گریجویشن | |
| `departmental` | Departmental exams | محکمہ جاتی امتحانات | |
| `updated` | Updated {time} IST | اپڈیٹ: {time} IST | |
| `newSince` | {n} new since your last visit | آپ کی پچھلی وزٹ کے بعد {n} نئے | |
| `cutoffTitle` | Cut-off marks | کٹ آف مارکس | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC کٹ آف صرف رزلٹ کی تفصیل کے اندر دیتا ہے۔ نمبر یہاں کاپی نہیں کیے گئے (ایک غلط نمبر بھی گمراہ کر سکتا ہے): ہر لنک SSC کی PDF اسی پیج پر کھولتا ہے جہاں سے کٹ آف کی تفصیل شروع ہوتی ہے۔ | |
| `cutoffFrom` | Open at page {p} | پیج {p} پر کھولیں | |
| `cutoffPages` | mentioned on p. {pages} | صفحہ {pages} پر درج | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | پچھلے 120 دنوں کی رزلٹ تفصیلات میں کوئی کٹ آف نہیں ملا۔ | |
| `alertsTitle` | Alerts on this phone | اس فون پر الرٹ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | جس امتحان کو آپ فالو کرتے ہیں اس کی کوئی خبر آنے پر، اور اپلیکیشن بند ہونے سے دو دن پہلے نوٹیفکیشن۔ | |
| `alertsOn` | Turn on alerts | الرٹ آن کریں | |
| `alertsOff` | Turn off alerts | الرٹ بند کریں | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | پہلے کسی امتحان کو فالو کریں (امتحانات → فالو)۔ | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} امتحانات کے لیے آن ہے۔ کب چیک کرنا ہے یہ Chrome طے کرتا ہے، اگر آپ ایپ اکثر کھولتے ہیں تو زیادہ بار، اس لیے خبر پوسٹ ہونے کے کئی گھنٹے بعد بھی پہنچ سکتی ہے۔ | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | اس سائٹ کے نوٹیفکیشن بلاک ہیں۔ براؤزر کی سائٹ سیٹنگز میں جا کر اجازت دیں۔ | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | یہ براؤزر بیک گراؤنڈ میں چیک نہیں کر سکتا۔ Android پر پہلے اس ایپ کو Chrome سے انسٹال کریں۔ ورنہ ہر امتحان کے پیج پر کیلنڈر ریمائنڈر استعمال کریں۔ | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | آپ کے امتحانات کی {n} مزید اپڈیٹس | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: اپلیکیشن {n} دن میں بند ہو جائے گی | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: آج اپلائی کرنے کا آخری دن ہے | |
| `installTitle` | Install the app | ایپ انسٹال کریں | |
| `installBtn` | Install | انسٹال کریں | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ایپ کی طرح کھلتی ہے، بغیر انٹرنیٹ کے چلتی ہے، اور Android پر الرٹ بھیج سکتی ہے۔ | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone پر: Share دبائیں، پھر "Add to Home Screen"۔ | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | فالو کر لیا۔ فون پر الرٹ کے لیے: مزید → الرٹ آن کریں | |
| `rollPlaceholder` | Roll number on your admit card | ایڈمٹ کارڈ پر لکھا رول نمبر | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} امتحانات کے لیے آن ہے۔ SSC کے نوٹس عام طور پر پوسٹ ہونے کے ایک گھنٹے کے اندر آپ تک پہنچ جاتے ہیں؛ رزلٹ اور دوسرے بورڈز کے نوٹس میں 3 گھنٹے تک لگ سکتے ہیں۔ | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | یہ براؤزر الرٹ نہیں دکھا سکتا۔ iPhone پر پہلے اس ایپ کو ہوم اسکرین پر شامل کریں (Share → Add to Home Screen)، پھر وہیں سے کھولیں۔ | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | الرٹ آن نہیں ہو سکے۔ اپنا کنکشن چیک کریں اور دوبارہ کوشش کریں۔ | |
| `allBoards` | All recruiters | تمام بھرتی ادارے | |
| `newBadge` | NEW | نیا | |
| `nothing` | Nothing here in the last 120 days. | پچھلے 120 دنوں میں یہاں کچھ نہیں۔ | |
| `loadError` | Couldn't load. Check your internet connection and try again. | لوڈ نہیں ہو سکا۔ اپنا انٹرنیٹ کنکشن چیک کریں اور دوبارہ کوشش کریں۔ | |
| `offline` | Offline: showing the copy saved on this phone. | آف لائن: اس فون میں محفوظ کاپی دکھائی جا رہی ہے۔ | |
| `save` | Save | محفوظ کریں | |
| `saved` | Saved | محفوظ | |
| `share` | Share | شیئر کریں | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | آفیشل پیج | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ آفیشل لنک: {url} ⏎ {app} کے ذریعے (سرکاری سائٹ نہیں) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ہر امتحان، اس کا تازہ نوٹس، تاریخیں، فیس، عمر کی حد اور اس کے بعد کے تمام نوٹس۔ | |
| `follow` | Follow | فالو کریں | |
| `following` | Following | فالو کر رہے ہیں | |
| `minQual` | Qualification: {q} | قابلیت: {q} | |
| `qualUnknown` | Qualification: see the Notice | قابلیت: نوٹس دیکھیں | |
| `calendarTitle` | SSC's tentative calendar | SSC کا عارضی کیلنڈر | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | یہ منصوبہ ہے، نوٹس نہیں: اصل تاریخیں ہر نوٹس میں ہوتی ہیں اور مختلف ہو سکتی ہیں۔ | |
| `resourcesTitle` | Official resources | آفیشل وسائل | |
| `res_syllabus` | Syllabus | سلیبس | |
| `res_vacancy` | Vacancies (tentative / final) | ویکینسی (عارضی / فائنل) | |
| `res_previous_papers` | Previous year papers | پچھلے سال کے پیپر | |
| `res_certificate_formats` | Certificate formats | سرٹیفکیٹ کے فارمیٹ | |
| `res_instructions` | Instructions | ہدایات | |
| `back` | ← All exams | ← تمام امتحانات | |
| `latestNotice` | Latest Notice | تازہ نوٹس | |
| `importantDates` | Important dates | اہم تاریخیں | |
| `applyWindow` | Apply online | آن لائن اپلائی | |
| `feeLastDate` | Fee payment last date | فیس جمع کرنے کی آخری تاریخ | |
| `correctionWindow` | Form correction window | فارم میں ترمیم کی مدت | |
| `tentative` | tentative | متوقع | |
| `applicationFee` | Application fee | اپلیکیشن فیس | |
| `feeGeneral` | General / OBC / EWS | جنرل / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | فیس معاف (₹0) | |
| `exempt_female` | Women | خواتین | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | سابق فوجی | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | ترمیم: پہلی بار ₹{a}، دوسری بار ₹{b} (تمام امیدوار) | |
| `ageLimit` | Age limit (as on {date}) | عمر کی حد ({date} تک) | |
| `ageBand` | {band} years: born {from} to {to} | {band} سال: {from} سے {to} کے درمیان پیدائش | |
| `relaxation` | Upper age relaxation | زیادہ سے زیادہ عمر میں چھوٹ | |
| `relaxYears` | {n} years | {n} سال | |
| `notComputed` | Not calculated here: {list} | یہاں حساب نہیں کیا گیا: {list} | |
| `vacancies` | Vacancies | ویکینسی | |
| `vacancyCount` | About {n} ({tentative}) | تقریباً {n} ({tentative}) | |
| `posts` | Posts | پوسٹیں | |
| `importantLinks` | Important links | اہم لنکس | |
| `applyOnline` | Apply online (ssc.gov.in) | آن لائن اپلائی کریں (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | آفیشل نوٹس (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} کی تبدیلی | |
| `allNotices` | All notices for this exam | اس امتحان کے تمام نوٹس | |
| `addCalendar` | Add dates to my calendar | تاریخیں میرے کیلنڈر میں شامل کریں | |
| `checkThis` | Check my eligibility | میری اہلیت چیک کریں | |
| `status_open` | Applications open till {date} | اپلیکیشن {date} تک کھلی ہے | |
| `status_upcoming` | Applications open on {date} | اپلیکیشن {date} کو کھلے گی | |
| `status_closed` | Applications closed on {date} | اپلیکیشن {date} کو بند ہو گئی | |
| `daysLeft` | {n} days left | {n} دن باقی | |
| `lastDay` | Last day today | آج آخری دن | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | اس امتحان کے اہلیت کے اصول ابھی شامل نہیں: نوٹس پڑھیں۔ | |
| `examLabel` | Exam | امتحان | |
| `dob` | Date of birth (as in Class 10 certificate) | تاریخ پیدائش (دسویں کے سرٹیفکیٹ کے مطابق) | |
| `category` | Category | کیٹیگری | |
| `catUR` | General (UR) | جنرل (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (نان کریمی لیئر) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | سب سے اعلیٰ قابلیت | |
| `edu10` | Class 10 | دسویں | |
| `edu12` | Class 12 | بارہویں | |
| `eduGraduate` | Bachelor's degree or higher | گریجویشن یا اس سے زیادہ | |
| `gender` | Gender | جنس | |
| `male` | Male | مرد | |
| `female` | Female | عورت | |
| `other` | Other | دیگر | |
| `appearing` | Final exam of that level taken, result not out yet | اس لیول کا فائنل امتحان دے دیا، رزلٹ ابھی نہیں آیا | |
| `pwbd` | Person with benchmark disability (PwBD) | بینچ مارک معذوری والے فرد (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | سابق فوجی، مرکزی حکومت کے ملازم، یا بیوہ/طلاق یافتہ خاتون | |
| `check` | Check | چیک کریں | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | آپ کے جواب اسی ڈیوائس پر رہتے ہیں۔ کہیں کچھ نہیں بھیجا جاتا۔ | |
| `enterDob` | Enter your date of birth. | اپنی تاریخ پیدائش درج کریں۔ | |
| `closedNote` | Shown so you can prepare for the next cycle. | اس لیے دکھایا گیا ہے تاکہ آپ اگلے سائیکل کی تیاری کر سکیں۔ | |
| `canApply` | {n} can apply | {n} میں اپلائی کر سکتے ہیں | |
| `toCheck` | {n} to check | {n} چیک کرنے ہیں | |
| `notEligible` | {n} not eligible | {n} میں اہل نہیں | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | فیس: {fee}۔ عمر میں دی گئی چھوٹ: {n} سال۔ عمر {date} کے حساب سے گنی جاتی ہے۔ | |
| `feeNone` | none (exempt) | کوئی نہیں (معاف) | |
| `st_eligible` | You can apply | آپ اپلائی کر سکتے ہیں | |
| `st_check` | Check one condition | ایک شرط چیک کریں | |
| `st_ineligible` | Not eligible | اہل نہیں | |
| `payLevel` | Pay Level {l} · Group {g} | پے لیول {l} · گروپ {g} | |
| `groupOnly` | Group {g} | گروپ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | {from} سے {to} کے درمیان پیدائش (آپ کے لیے) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | اصول {date} کے نوٹس سے لیے گئے ہیں ({checked} کو چیک کیے گئے)۔ | |
| `sigTitle` | Signature for SSC forms | SSC فارم کے لیے دستخط | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC کو {min}-{max} KB کی JPEG چاہیے، تقریباً {w} cm × {h} cm۔ سفید کاغذ پر کالی سیاہی سے دستخط کریں اور صاف فوٹو لیں۔ | |
| `sigPick` | Choose signature photo | دستخط کی فوٹو چنیں | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | تیار: {kb} KB، {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | فائل {min} اور {max} KB کے درمیان نہیں بن سکی۔ زیادہ صاف فوٹو آزمائیں۔ | |
| `sigDownload` | Download JPEG | JPEG ڈاؤن لوڈ کریں | |
| `photoTitle` | Your photograph | آپ کی تصویر | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC اب اپلوڈ کی گئی فوٹو نہیں لیتا: اپلیکیشن فارم آپ کے کیمرے سے لائیو فوٹو لیتا ہے، اور پرانی فوٹو کی فوٹو کھینچنے پر فارم ریجیکٹ ہو جاتا ہے۔ اچھی روشنی، سادہ بیک گراؤنڈ، کیمرا آنکھوں کی سطح پر رکھیں، ٹوپی، ماسک یا چشمہ نہ ہو۔ | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ماخذ: نوٹس پیرا 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | عمر کیلکولیٹر | |
| `ageOn` | Age on | اس تاریخ کو عمر | |
| `ageResult` | {y} years, {m} months, {d} days | {y} سال، {m} مہینے، {d} دن | |
| `ageInvalid` | Date of birth must be before that date. | تاریخ پیدائش اس تاریخ سے پہلے ہونی چاہیے۔ | |
| `docsTitle` | Certificate formats | سرٹیفکیٹ کے فارمیٹ | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | کیٹیگری، معذوری اور سابق فوجی سرٹیفکیٹ کے لیے SSC کے فارمیٹ۔ | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ابھی کچھ محفوظ نہیں کیا۔ کسی بھی نوٹس پر محفوظ کریں دبائیں۔ | |
| `followedExams` | Exams you follow | آپ کے فالو کیے ہوئے امتحانات | |
| `chooseLanguage` | Choose your language | اپنی زبان چنیں | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | نوٹس کے عنوان اسی زبان میں رہتے ہیں جس میں بھرتی ادارہ انہیں جاری کرتا ہے (زیادہ تر انگریزی یا ہندی)۔ | |
| `qaLatest` | Latest jobs | نئی نوکریاں | |
| `qaResults` | Results | رزلٹ | |
| `qaAdmit` | Admit cards | ایڈمٹ کارڈ | |
| `qaAnswer` | Answer keys | آنسر کی | |
| `qaEligibility` | Check eligibility | اہلیت چیک | |
| `qaRoll` | Result by roll no. | رول نمبر سے رزلٹ | |
| `homeSectors` | Jobs by sector | سیکٹر کے حساب سے نوکریاں | |
| `homeStates` | Jobs by state | ریاست کے حساب سے نوکریاں | |
| `homeClosing` | Closing soon | جلد بند ہونے والی | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ہر اپڈیٹ WhatsApp یا Telegram پر پائیں | |
| `homeChannelsNote` | Free. One message per update, with the official link. | مفت۔ ہر اپڈیٹ پر ایک میسج، آفیشل لنک کے ساتھ۔ | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | غیر سرکاری · ہر اپڈیٹ کے ساتھ آفیشل ویب سائٹ کا لنک | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | پولیس | |
| `sector_defence` | Defence & CAPF | ڈیفنس اور CAPF | |
| `sector_railway` | Railway | ریلوے | |
| `sector_bank` | Bank | بینک | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | سائنس اور اسپیس | |
| `sector_state` | State govt | ریاستی حکومت | |
| `sector_teaching` | Teaching | ٹیچنگ | |
| `state_all` | All India | آل انڈیا | |
| `state_UP` | Uttar Pradesh | اتر پردیش | |
| `state_BR` | Bihar | بہار | |
| `state_HR` | Haryana | ہریانہ | |
| `anySector` | All sectors | تمام سیکٹر | |
| `anyState` | All states | تمام ریاستیں | |
| `jobsSector` | {name} jobs | {name} کی نوکریاں | |
| `jobsState` | Government jobs in {name} | {name} میں سرکاری نوکریاں | |
| `jobsKind` | {name}: latest | {name}: تازہ | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | اس سیکٹر سے ابھی کچھ نہیں۔ ہم کسی بھرتی ادارے کو اس کی آفیشل ویب سائٹ چیک کرنے کے بعد ہی شامل کرتے ہیں، اس لیے یہ لسٹ بڑھتی جائے گی۔ | |
| `centralJobs` | Central govt (all India) | مرکزی حکومت (آل انڈیا) | |
| `recruitersTitle` | Recruiters we follow | جن بھرتی اداروں کو ہم فالو کرتے ہیں | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | اپڈیٹس انہی آفیشل ویب سائٹس سے لی جاتی ہیں۔ کھولنے کے لیے کسی پر ٹیپ کریں۔ | |
| `moreUpdates` | See all updates | تمام اپڈیٹس دیکھیں | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | عمر کم ہے: {date} کو آپ کی عمر کم از کم {n} ہونی چاہیے۔ | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | عمر کی حد سے زیادہ: {date} کو آپ کی عمر {n} سے کم ہونی چاہیے (چھوٹ شامل کر کے)۔ | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | عام عمر کی حد سے زیادہ۔ سابق فوجی / سرکاری ملازم / بیوہ کی چھوٹ سے کام بن سکتا ہے: نوٹس {ref} دیکھیں۔ | |
| `r_education_graduate` | Needs a bachelor's degree. | گریجویشن کی ڈگری چاہیے۔ | |
| `r_education_12` | Needs Class 12 pass. | بارہویں پاس ہونا چاہیے۔ | |
| `r_education_10` | Needs Class 10 pass. | دسویں پاس ہونا چاہیے۔ | |
| `r_result_needed_by` | Your result must be out by {date}. | آپ کا رزلٹ {date} تک آ جانا چاہیے۔ | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | نوٹس میں یہ نہیں لکھا کہ رزلٹ کا انتظار کرنے والے امیدوار اپلائی کر سکتے ہیں؛ {date} تک آپ کے پاس قابلیت ہونی چاہیے۔ | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | اس نوٹس میں PwBD کے لیے عمر میں کوئی چھوٹ نہیں دی گئی: نوٹس چیک کریں۔ | |
| `r_gender_male` | Only for male candidates. | صرف مرد امیدواروں کے لیے۔ | |
| `r_gender_female` | Only for female candidates. | صرف خاتون امیدواروں کے لیے۔ | |
| `r_gender_other` | Only for {g} candidates. | صرف {g} امیدواروں کے لیے۔ | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | سیکنڈری اسکول لیول پر ریاست کی سرکاری/علاقائی زبان ہونی چاہیے۔ | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | بارہویں میں ریاضی میں 60% یا ڈگری میں شماریات (Statistics) ایک مضمون کے طور پر ہونا چاہیے۔ | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ڈگری شماریات، ریاضی، اکنامکس، ڈیموگرافی، پاپولیشن اسٹڈیز، آپریشنز ریسرچ، IT، کمپیوٹر سائنس/انجینئرنگ/ایپلیکیشنز، ڈیٹا سائنس یا AI میں ہونی چاہیے۔ | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | فزیکل ٹیسٹ کے دن ایک درست LMV (موٹر سائیکل اور کار) ڈرائیونگ لائسنس ہونا چاہیے۔ | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | فزکس، کیمسٹری اور ریاضی کے ساتھ B.Sc. چاہیے، یا دسویں کے ساتھ مکینیکل، سول، الیکٹریکل یا کسی درج انجینئرنگ برانچ میں 3 سالہ ڈپلوما۔ | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | فزیکل اور میڈیکل معیار لاگو ہیں (نوٹس دیکھیں)۔ | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | بارڈر روڈز آرگنائزیشن کی ویکینسی: صرف مرد، فزیکل معیار لاگو۔ | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | کچھ دفاتر (کنزیومر افیئرز اور فوڈ، SSC، کلچر) میں اس پوسٹ کے لیے بارہویں سائنس ریاضی کے ساتھ چاہیے۔ | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | سلیکشن میں اسٹینوگرافی اسکل ٹیسٹ بھی شامل ہے۔ | |
| `listWord` | Lists: {n} | لسٹیں: {n} | |
