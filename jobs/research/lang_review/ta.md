# தமிழ் (ta) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/ta.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/ta.js)

| 키 | English | தமிழ் | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | சர்காரி ஜாப் அலர்ட் | |
| `tabHome` | Home | முகப்பு | |
| `tabMore` | More | மேலும் | |
| `loading` | Loading… | லோடு ஆகிறது… | |
| `homeComing` | Exam dates in the next 30 days | அடுத்த 30 நாட்களில் தேர்வு தேதிகள் | |
| `homeLatest` | Latest updates | புதிய அப்டேட்கள் | |
| `inDays` | in {n} days | {n} நாட்களில் | |
| `onNow` | on now | இப்போது நடக்கிறது | |
| `today` | today | இன்று | |
| `theme` | Theme | தீம் | |
| `themeAuto` | Auto | ஆட்டோ | |
| `themeLight` | Light | லைட் | |
| `themeDark` | Dark | டார்க் | |
| `language` | Language | மொழி | |
| `howToApply` | How to apply | விண்ணப்பிப்பது எப்படி | |
| `documents` | Keep these documents ready | இந்த ஆவணங்களைத் தயாராக வைத்திருங்கள் | |
| `helplineTitle` | SSC helplines | SSC உதவி எண்கள் | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | படிவம் நிரப்புவதில் அல்லது கணினி வழித் தேர்வில் பிரச்சனை இருந்தால். | |
| `typingTitle` | Typing / data entry speed practice | டைப்பிங் / டேட்டா என்ட்ரி வேகப் பயிற்சி | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | கீழே உள்ள பத்தியை டைப் செய்யுங்கள். முதல் கீயை அழுத்தியதும் டைமர் தொடங்கும். SSC குறிப்பிடுவது போலவே, வேகம் ஒரு மணி நேரத்துக்கான கீ அழுத்தங்களில் (key depressions per hour) காட்டப்படும். | |
| `typingTest` | Standard | ஸ்டாண்டர்டு | |
| `typingMinutes` | Time | நேரம் | |
| `typingFull` | Full ({n} min) | முழு நேரம் ({n} நிமி) | |
| `typingStart` | Start again | மீண்டும் தொடங்கு | |
| `typingDone` | Time is up. | நேரம் முடிந்தது. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | மணிக்கு {kdph} கீ அழுத்தங்கள் (≈ {wpm} w.p.m.), {accuracy}% துல்லியம், டைப் செய்த {typed}-இல் {correct} சரி | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} வேகத்தை எட்டிவிட்டீர்கள் ({kdph}/மணி). | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} வேகத்தை விடக் குறைவு ({kdph}/மணி): தொடர்ந்து பயிற்சி செய்யுங்கள். | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | இது பயிற்சிக்கு மட்டுமே: SSC தன் சொந்த மதிப்பீட்டு விதிகளின்படி பிழைகளைக் கணக்கிடும். இந்தி டைப்பிங் பயிற்சி இங்கு இல்லை. | |
| `faqTitle` | Frequently asked | அடிக்கடி கேட்கப்படும் கேள்விகள் | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}-க்கு விண்ணப்பிக்கக் கடைசி தேதி என்ன? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | விண்ணப்பிக்கக் கடைசி தேதி {date}; கட்டணம் செலுத்தக் கடைசி தேதி {fee}. | |
| `faqAge` | What is the age limit for {exam}? | {exam}-க்கான வயது வரம்பு என்ன? | |
| `faqFee` | What is the application fee? | விண்ணப்பக் கட்டணம் எவ்வளவு? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | பொது, OBC, EWS விண்ணப்பதாரர்களுக்கு ₹{amount}. {exempt} கட்டணம் செலுத்த வேண்டியதில்லை. | |
| `faqSalary` | What is the salary? | சம்பளம் எவ்வளவு? | |
| `faqExam` | When is the {exam} exam? | {exam} தேர்வு எப்போது? | |
| `faqVacancy` | How many vacancies are there? | எத்தனை காலியிடங்கள் உள்ளன? | |
| `faqQual` | What qualification is needed? | என்ன கல்வித் தகுதி தேவை? | |
| `aboutTitle` | About this app | இந்த ஆப் பற்றி | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | இந்தியாவில் அரசு வேலைக்குத் தயாராகுபவர்களுக்கான இலவச, அதிகாரப்பூர்வமற்ற உதவி ஆப். அறிவிப்புகள், தேதிகள், விதிகள் அனைத்தும் ஆட்சேர்ப்பு அமைப்புகளின் அதிகாரப்பூர்வ இணையதளங்கள் மற்றும் நோட்டிஃபிகேஷன்களிலிருந்து, ஆதாரங்களுடன் எடுக்கப்படுகின்றன. பதிவு தேவையில்லை, தனிப்பட்ட தகவல் எதுவும் சேகரிக்கப்படுவதில்லை: உங்கள் விவரங்களும் ரோல் நம்பரும் இந்த போனிலேயே இருக்கும். | |
| `sourceRef` | Source: {ref} | ஆதாரம்: {ref} | |
| `toastSaved` | Saved | சேமிக்கப்பட்டது | |
| `toastRemoved` | Removed | நீக்கப்பட்டது | |
| `toastFollow` | You follow this exam | இந்தத் தேர்வை ஃபாலோ செய்கிறீர்கள் | |
| `toastUnfollow` | Unfollowed | ஃபாலோ நீக்கப்பட்டது | |
| `settings` | Settings | செட்டிங்ஸ் | |
| `toolsTitle` | Tools | டூல்ஸ் | |
| `tabResult` | Result | ரிசல்ட் | |
| `tabForMe` | For me | எனக்கு | |
| `resultTitle` | Check your result by roll number | ரோல் நம்பர் மூலம் ரிசல்ட் பாருங்கள் | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | கடந்த 120 நாட்களில் SSC வெளியிட்ட ரிசல்ட் பட்டியல்களில் தேடுகிறது; பெரிய PDF-களை டவுன்லோட் செய்யத் தேவையில்லை. ஒரு சிறிய ஃபைலைப் பெற உங்கள் ரோல் நம்பரின் கடைசி இரண்டு இலக்கங்கள் மட்டுமே பயன்படுத்தப்படும். | |
| `rollNo` | Roll number | ரோல் நம்பர் | |
| `resultExam` | Result | ரிசல்ட் | |
| `find` | Find | தேடு | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | அட்மிட் கார்டில் உள்ளபடி ரோல் நம்பரை உள்ளிடுங்கள் (8-14 இலக்கங்கள்). | |
| `foundIn` | Found in: {list} | இதில் உள்ளது: {list} | |
| `notIn` | Not in: {list} | இதில் இல்லை: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | இந்தப் பட்டியல்களில் உங்கள் ரோல் நம்பர் இல்லை. அதனால் மட்டுமே நீங்கள் தேர்ச்சி பெறவில்லை என்று அர்த்தமில்லை: கீழே உள்ள அதிகாரப்பூர்வ PDF-களைப் பாருங்கள். | |
| `officialFiles` | Official files of this notice | இந்த அறிவிப்பின் அதிகாரப்பூர்வ ஃபைல்கள் | |
| `listCount` | {n} roll numbers | {n} ரோல் நம்பர்கள் | |
| `noLists` | No result lists to search right now. | இப்போது தேட ரிசல்ட் பட்டியல்கள் எதுவும் இல்லை. | |
| `forMeTitle` | Exams that suit you | உங்களுக்குப் பொருத்தமான தேர்வுகள் | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | உங்கள் விவரங்களை ஒருமுறை நிரப்புங்கள். நீங்கள் விண்ணப்பிக்கக்கூடிய ஒவ்வொரு SSC தேர்வும், அதன் நிலையும், அடுத்து என்ன என்பதும் தெரியும். | |
| `forMeEdit` | Edit my details | என் விவரங்களை மாற்று | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | உங்கள் கல்வித் தகுதிக்கு (விதிகள் இன்னும் சேர்க்கப்படவில்லை: நோட்டிஃபிகேஷனைப் படியுங்கள்) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | உங்கள் தகுதிக்கு வரவிருக்கும் தேர்வுகள் (தற்காலிகம்) | |
| `saveDetails` | Show my exams | என் தேர்வுகளைக் காட்டு | |
| `whenTitle` | What has happened and what is next | என்ன நடந்தது, அடுத்து என்ன | |
| `st_done` | Done | முடிந்தது | |
| `st_now` | Now | இப்போது | |
| `st_next` | Next | அடுத்து | |
| `st_later` | Later | பின்னர் | |
| `stage_notice` | Notice published | நோட்டிஃபிகேஷன் வெளியீடு | |
| `stage_apply` | Online application | ஆன்லைன் விண்ணப்பம் | |
| `stage_admit_card` | Admit card / exam city | அட்மிட் கார்டு / தேர்வு நகரம் | |
| `stage_answer_key` | Answer key | ஆன்சர் கீ | |
| `stage_result` | Result | ரிசல்ட் | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC இன்னும் தேதியை அறிவிக்கவில்லை | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | அடிப்படைச் சம்பளம் மாதம் ₹{from}–{to} (லெவல் {l}; படிகள் தனி) | |
| `savedLink` | ★ Saved | ★ சேமித்தவை | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | இது அரசு இணையதளம் அல்ல; எந்த ஆட்சேர்ப்பு அமைப்புடனோ அரசுடனோ தொடர்புடையதும் அல்ல. இங்குள்ள அனைத்தும் ஆட்சேர்ப்பு அமைப்புகளின் அதிகாரப்பூர்வ இணையதளங்களிலிருந்து எடுக்கப்பட்டவை; ஒவ்வொரு அப்டேட்டிலும் அதிகாரப்பூர்வ ஃபைல் அல்லது பக்கத்தின் லிங்க் உள்ளது. விண்ணப்பிக்கும் முன் எப்போதும் அதிகாரப்பூர்வ நோட்டிஃபிகேஷனைச் சரிபாருங்கள். | |
| `tabExams` | Exams | தேர்வுகள் | |
| `filters` | Filters | ஃபில்டர் | |
| `kindAll` | All | அனைத்தும் | |
| `kindRecruitment` | New jobs | புதிய வேலைகள் | |
| `kindResult` | Results | ரிசல்ட்கள் | |
| `kindAdmit` | Admit cards | அட்மிட் கார்டுகள் | |
| `kindAnswer` | Answer keys | ஆன்சர் கீ | |
| `kindAmendment` | Changes | மாற்றங்கள் | |
| `kindVacancy` | Vacancies | காலியிடங்கள் | |
| `kind_recruitment` | New job | புதிய வேலை | |
| `kind_amendment` | Change | மாற்றம் | |
| `kind_result` | Result | ரிசல்ட் | |
| `kind_answer_key` | Answer key | ஆன்சர் கீ | |
| `kind_admit_card` | Admit card | அட்மிட் கார்டு | |
| `kind_vacancy` | Vacancy | காலியிடம் | |
| `kind_schedule` | Exam dates | தேர்வு தேதிகள் | |
| `kind_notice` | Notice | அறிவிப்பு | |
| `search` | Search jobs, exams, results | வேலை, தேர்வு, ரிசல்ட் தேடுங்கள் | |
| `allExams` | All exams | அனைத்து தேர்வுகள் | |
| `myExams` | My exams only | என் தேர்வுகள் மட்டும் | |
| `anyQualification` | Any qualification | எந்தத் தகுதியும் | |
| `qual_10` | Class 10 | 10ஆம் வகுப்பு | |
| `qual_12` | Class 12 | 12ஆம் வகுப்பு | |
| `qual_diploma` | Diploma | டிப்ளமோ | |
| `qual_graduate` | Bachelor's degree | இளநிலைப் பட்டம் | |
| `qual_postgraduate` | Master's degree | முதுநிலைப் பட்டம் | |
| `departmental` | Departmental exams | துறைத் தேர்வுகள் | |
| `updated` | Updated {time} IST | {time} IST-க்கு அப்டேட் செய்யப்பட்டது | |
| `newSince` | {n} new since your last visit | நீங்கள் கடைசியாக வந்த பிறகு {n} புதியவை | |
| `cutoffTitle` | Cut-off marks | கட்-ஆஃப் மதிப்பெண்கள் | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC கட்-ஆஃப்களை ரிசல்ட் விளக்கக் குறிப்பில் மட்டுமே தருகிறது. எண்கள் இங்கு நகலெடுக்கப்படவில்லை (ஒரு தவறான எண் கூட தவறாக வழிநடத்தும்): ஒவ்வொரு லிங்கும் கட்-ஆஃப் விவரங்கள் தொடங்கும் பக்கத்தில் SSC-யின் PDF-ஐத் திறக்கும். | |
| `cutoffFrom` | Open at page {p} | பக்கம் {p}-இல் திற | |
| `cutoffPages` | mentioned on p. {pages} | பக். {pages}-இல் உள்ளது | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | கடந்த 120 நாட்களின் ரிசல்ட் குறிப்புகளில் கட்-ஆஃப் எதுவும் இல்லை. | |
| `alertsTitle` | Alerts on this phone | இந்த போனில் அலர்ட்கள் | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | நீங்கள் ஃபாலோ செய்யும் தேர்வுக்குப் புதிய செய்தி வந்தாலும், விண்ணப்பம் முடிவதற்கு இரண்டு நாள் முன்பும் ஒரு அலர்ட் வரும். | |
| `alertsOn` | Turn on alerts | அலர்ட்களை ஆன் செய் | |
| `alertsOff` | Turn off alerts | அலர்ட்களை ஆஃப் செய் | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | முதலில் ஒரு தேர்வை ஃபாலோ செய்யுங்கள் (தேர்வுகள் → ஃபாலோ). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} தேர்வுகளுக்கு ஆன். எப்போது சரிபார்க்க வேண்டும் என்பதை Chrome முடிவு செய்யும்; ஆப்பை அடிக்கடி திறந்தால் அடிக்கடி சரிபார்க்கும். அதனால் செய்தி வெளியாகி சில மணி நேரம் கழித்தே உங்களுக்கு வரலாம். | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | இந்தத் தளத்துக்கு நோட்டிஃபிகேஷன்கள் தடுக்கப்பட்டுள்ளன. பிரவுசரின் சைட் செட்டிங்ஸில் அனுமதி கொடுங்கள். | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | இந்த பிரவுசரால் பின்னணியில் சரிபார்க்க முடியாது. Android-இல் முதலில் Chrome-இலிருந்து இந்த ஆப்பை இன்ஸ்டால் செய்யுங்கள். இல்லையெனில், ஒவ்வொரு தேர்வுப் பக்கத்திலும் உள்ள காலண்டர் ரிமைண்டர்களைப் பயன்படுத்துங்கள். | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | உங்கள் தேர்வுகளுக்கு மேலும் {n} அப்டேட்கள் | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: விண்ணப்பம் {n} நாட்களில் முடிகிறது | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: விண்ணப்பிக்க இன்றே கடைசி நாள் | |
| `installTitle` | Install the app | ஆப்பை இன்ஸ்டால் செய்யுங்கள் | |
| `installBtn` | Install | இன்ஸ்டால் | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ஆப் போலவே திறக்கும், இன்டர்நெட் இல்லாமலும் வேலை செய்யும், Android-இல் அலர்ட்களும் கிடைக்கும். | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone-இல்: Share-ஐத் தட்டி, பிறகு "Add to Home Screen"-ஐத் தேர்ந்தெடுங்கள். | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ஃபாலோ செய்கிறீர்கள். போன் அலர்ட்களுக்கு: மேலும் → அலர்ட்களை ஆன் செய் | |
| `rollPlaceholder` | Roll number on your admit card | அட்மிட் கார்டில் உள்ள ரோல் நம்பர் | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} தேர்வுகளுக்கு ஆன். SSC அறிவிப்புகள் பொதுவாக வெளியான ஒரு மணி நேரத்துக்குள் வந்துவிடும்; ரிசல்ட்களும் மற்ற வாரியங்களின் அறிவிப்புகளும் வர 3 மணி நேரம் வரை ஆகலாம். | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | இந்த பிரவுசரில் அலர்ட்களைக் காட்ட முடியாது. iPhone-இல் முதலில் இந்த ஆப்பை Home Screen-இல் சேர்த்து (Share → Add to Home Screen), அங்கிருந்து திறங்கள். | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | அலர்ட்களை ஆன் செய்ய முடியவில்லை. இன்டர்நெட் இணைப்பைச் சரிபார்த்து மீண்டும் முயலுங்கள். | |
| `allBoards` | All recruiters | அனைத்து ஆட்சேர்ப்பு அமைப்புகள் | |
| `newBadge` | NEW | புதிது | |
| `nothing` | Nothing here in the last 120 days. | கடந்த 120 நாட்களில் இங்கு எதுவும் இல்லை. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | லோடு ஆகவில்லை. இன்டர்நெட் இணைப்பைச் சரிபார்த்து மீண்டும் முயலுங்கள். | |
| `offline` | Offline: showing the copy saved on this phone. | ஆஃப்லைன்: இந்த போனில் சேமித்த பதிப்பு காட்டப்படுகிறது. | |
| `save` | Save | சேமி | |
| `saved` | Saved | சேமித்தது | |
| `share` | Share | ஷேர் | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | அதிகாரப்பூர்வ பக்கம் | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ அதிகாரப்பூர்வ லிங்க்: {url} ⏎ {app} வழியாக (அரசு தளம் அல்ல) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ஒவ்வொரு தேர்வுக்கும் சமீபத்திய நோட்டிஃபிகேஷன், தேதிகள், கட்டணம், வயது வரம்பு, அதன்பின் வந்த அனைத்து அறிவிப்புகளும். | |
| `follow` | Follow | ஃபாலோ | |
| `following` | Following | ஃபாலோயிங் | |
| `minQual` | Qualification: {q} | தகுதி: {q} | |
| `qualUnknown` | Qualification: see the Notice | தகுதி: நோட்டிஃபிகேஷனைப் பாருங்கள் | |
| `calendarTitle` | SSC's tentative calendar | SSC-யின் தற்காலிக தேர்வு அட்டவணை | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | இவை திட்டங்கள் மட்டுமே, நோட்டிஃபிகேஷன் அல்ல: உண்மையான தேதிகள் ஒவ்வொரு நோட்டிஃபிகேஷனிலும் இருக்கும், அவை மாறலாம். | |
| `resourcesTitle` | Official resources | அதிகாரப்பூர்வ ஆதாரங்கள் | |
| `res_syllabus` | Syllabus | சிலபஸ் | |
| `res_vacancy` | Vacancies (tentative / final) | காலியிடங்கள் (தற்காலிக / இறுதி) | |
| `res_previous_papers` | Previous year papers | முந்தைய ஆண்டு வினாத்தாள்கள் | |
| `res_certificate_formats` | Certificate formats | சான்றிதழ் படிவங்கள் | |
| `res_instructions` | Instructions | அறிவுறுத்தல்கள் | |
| `back` | ← All exams | ← அனைத்து தேர்வுகள் | |
| `latestNotice` | Latest Notice | சமீபத்திய நோட்டிஃபிகேஷன் | |
| `importantDates` | Important dates | முக்கிய தேதிகள் | |
| `applyWindow` | Apply online | ஆன்லைன் விண்ணப்பம் | |
| `feeLastDate` | Fee payment last date | கட்டணம் செலுத்தக் கடைசி தேதி | |
| `correctionWindow` | Form correction window | படிவத் திருத்தக் காலம் | |
| `tentative` | tentative | உத்தேசம் | |
| `applicationFee` | Application fee | விண்ணப்பக் கட்டணம் | |
| `feeGeneral` | General / OBC / EWS | பொது / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | கட்டண விலக்கு (₹0) | |
| `exempt_female` | Women | பெண்கள் | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | முன்னாள் ராணுவத்தினர் | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | திருத்தம்: முதல் முறை ₹{a}, இரண்டாம் முறை ₹{b} (அனைத்து விண்ணப்பதாரர்களுக்கும்) | |
| `ageLimit` | Age limit (as on {date}) | வயது வரம்பு ({date} அன்று) | |
| `ageBand` | {band} years: born {from} to {to} | {band} வயது: {from} முதல் {to} வரை பிறந்தவர்கள் | |
| `relaxation` | Upper age relaxation | உச்ச வயது வரம்பில் தளர்வு | |
| `relaxYears` | {n} years | {n} ஆண்டுகள் | |
| `notComputed` | Not calculated here: {list} | இங்கு கணக்கிடப்படவில்லை: {list} | |
| `vacancies` | Vacancies | காலியிடங்கள் | |
| `vacancyCount` | About {n} ({tentative}) | சுமார் {n} ({tentative}) | |
| `posts` | Posts | பதவிகள் | |
| `importantLinks` | Important links | முக்கிய லிங்க்குகள் | |
| `applyOnline` | Apply online (ssc.gov.in) | ஆன்லைனில் விண்ணப்பிக்க (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | அதிகாரப்பூர்வ நோட்டிஃபிகேஷன் (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} தேதியிட்ட மாற்றம் | |
| `allNotices` | All notices for this exam | இந்தத் தேர்வின் அனைத்து அறிவிப்புகள் | |
| `addCalendar` | Add dates to my calendar | தேதிகளை என் காலண்டரில் சேர் | |
| `checkThis` | Check my eligibility | என் தகுதியைச் சரிபார் | |
| `status_open` | Applications open till {date} | {date} வரை விண்ணப்பிக்கலாம் | |
| `status_upcoming` | Applications open on {date} | விண்ணப்பம் {date} அன்று தொடங்கும் | |
| `status_closed` | Applications closed on {date} | விண்ணப்பம் {date} அன்று முடிந்தது | |
| `daysLeft` | {n} days left | இன்னும் {n} நாட்கள் | |
| `lastDay` | Last day today | இன்றே கடைசி நாள் | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | இந்தத் தேர்வுக்கான தகுதி விதிகள் இன்னும் சேர்க்கப்படவில்லை: நோட்டிஃபிகேஷனைப் படியுங்கள். | |
| `examLabel` | Exam | தேர்வு | |
| `dob` | Date of birth (as in Class 10 certificate) | பிறந்த தேதி (10ஆம் வகுப்புச் சான்றிதழில் உள்ளபடி) | |
| `category` | Category | பிரிவு | |
| `catUR` | General (UR) | பொது (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (கிரீமி லேயர் அல்லாதவர்) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | உயர் கல்வித் தகுதி | |
| `edu10` | Class 10 | 10ஆம் வகுப்பு | |
| `edu12` | Class 12 | 12ஆம் வகுப்பு | |
| `eduGraduate` | Bachelor's degree or higher | இளநிலைப் பட்டம் அல்லது அதற்கு மேல் | |
| `gender` | Gender | பாலினம் | |
| `male` | Male | ஆண் | |
| `female` | Female | பெண் | |
| `other` | Other | மற்றவர் | |
| `appearing` | Final exam of that level taken, result not out yet | அந்த நிலையின் இறுதித் தேர்வு எழுதப்பட்டது, ரிசல்ட் இன்னும் வரவில்லை | |
| `pwbd` | Person with benchmark disability (PwBD) | குறிப்பிட்ட அளவு மாற்றுத்திறன் உள்ளவர் (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | முன்னாள் ராணுவ வீரர், மத்திய அரசு ஊழியர், அல்லது விதவை/விவாகரத்தான பெண் | |
| `check` | Check | சரிபார் | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | உங்கள் பதில்கள் இந்தச் சாதனத்திலேயே இருக்கும். எதுவும் எங்கும் அனுப்பப்படுவதில்லை. | |
| `enterDob` | Enter your date of birth. | உங்கள் பிறந்த தேதியை உள்ளிடுங்கள். | |
| `closedNote` | Shown so you can prepare for the next cycle. | அடுத்த சுற்றுக்குத் தயாராக உதவுவதற்காகக் காட்டப்படுகிறது. | |
| `canApply` | {n} can apply | {n} விண்ணப்பிக்கலாம் | |
| `toCheck` | {n} to check | {n} சரிபார்க்க வேண்டும் | |
| `notEligible` | {n} not eligible | {n} தகுதி இல்லை | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | கட்டணம்: {fee}. வழங்கப்பட்ட வயதுத் தளர்வு: {n} ஆண்டுகள். வயது {date} அன்றைய நிலவரப்படி கணக்கிடப்படும். | |
| `feeNone` | none (exempt) | இல்லை (விலக்கு) | |
| `st_eligible` | You can apply | விண்ணப்பிக்கலாம் | |
| `st_check` | Check one condition | ஒரு நிபந்தனையைச் சரிபாருங்கள் | |
| `st_ineligible` | Not eligible | தகுதி இல்லை | |
| `payLevel` | Pay Level {l} · Group {g} | பே லெவல் {l} · குரூப் {g} | |
| `groupOnly` | Group {g} | குரூப் {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | {from} முதல் {to} வரை பிறந்திருக்க வேண்டும் (உங்களுக்கு) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date} தேதியிட்ட நோட்டிஃபிகேஷனிலிருந்து விதிகள் எடுக்கப்பட்டன ({checked} அன்று சரிபார்க்கப்பட்டது). | |
| `sigTitle` | Signature for SSC forms | SSC படிவத்துக்கான கையொப்பம் | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC {min}-{max} KB அளவுள்ள JPEG கேட்கிறது, சுமார் {w} செ.மீ × {h} செ.மீ. வெள்ளைத் தாளில் கருப்பு மையில் கையெழுத்திட்டு, தெளிவாக போட்டோ எடுங்கள். | |
| `sigPick` | Choose signature photo | கையொப்ப போட்டோவைத் தேர்ந்தெடு | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | தயார்: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ஃபைலை {min} முதல் {max} KB அளவுக்குள் கொண்டுவர முடியவில்லை. இன்னும் தெளிவான போட்டோவை முயலுங்கள். | |
| `sigDownload` | Download JPEG | JPEG டவுன்லோட் | |
| `photoTitle` | Your photograph | உங்கள் புகைப்படம் | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC இப்போது அப்லோட் செய்த போட்டோவை ஏற்பதில்லை: விண்ணப்பப் படிவமே உங்கள் கேமராவில் நேரடியாக போட்டோ எடுக்கும்; பழைய போட்டோவைப் படம் பிடித்தால் படிவம் நிராகரிக்கப்படும். நல்ல வெளிச்சம், வெற்றுப் பின்னணி, கண் உயரத்தில் கேமரா; தொப்பி, மாஸ்க், கண்ணாடி வேண்டாம். | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ஆதாரம்: நோட்டிஃபிகேஷன் பத்தி 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | வயது கால்குலேட்டர் | |
| `ageOn` | Age on | இந்தத் தேதியில் வயது | |
| `ageResult` | {y} years, {m} months, {d} days | {y} ஆண்டுகள், {m} மாதங்கள், {d} நாட்கள் | |
| `ageInvalid` | Date of birth must be before that date. | பிறந்த தேதி அந்தத் தேதிக்கு முன்பாக இருக்க வேண்டும். | |
| `docsTitle` | Certificate formats | சான்றிதழ் படிவங்கள் | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | பிரிவு, மாற்றுத்திறன், முன்னாள் ராணுவத்தினர் சான்றிதழ்களுக்கான SSC படிவங்கள். | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | இன்னும் எதுவும் சேமிக்கவில்லை. எந்த அறிவிப்பிலும் சேமி-யைத் தட்டுங்கள். | |
| `followedExams` | Exams you follow | நீங்கள் ஃபாலோ செய்யும் தேர்வுகள் | |
| `chooseLanguage` | Choose your language | உங்கள் மொழியைத் தேர்ந்தெடுங்கள் | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | அறிவிப்புத் தலைப்புகள் ஆட்சேர்ப்பு அமைப்பு வெளியிடும் மொழியிலேயே இருக்கும் (பெரும்பாலும் ஆங்கிலம் அல்லது இந்தி). | |
| `qaLatest` | Latest jobs | புதிய வேலைகள் | |
| `qaResults` | Results | ரிசல்ட் | |
| `qaAdmit` | Admit cards | அட்மிட் கார்டு | |
| `qaAnswer` | Answer keys | ஆன்சர் கீ | |
| `qaEligibility` | Check eligibility | தகுதி சரிபார் | |
| `qaRoll` | Result by roll no. | ரோல் நம்பர் ரிசல்ட் | |
| `homeSectors` | Jobs by sector | துறை வாரியாக வேலைகள் | |
| `homeStates` | Jobs by state | மாநில வாரியாக வேலைகள் | |
| `homeClosing` | Closing soon | விரைவில் முடிவடைபவை | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ஒவ்வொரு அப்டேட்டையும் WhatsApp அல்லது Telegram-இல் பெறுங்கள் | |
| `homeChannelsNote` | Free. One message per update, with the official link. | இலவசம். ஒவ்வொரு அப்டேட்டுக்கும் ஒரு மெசேஜ், அதிகாரப்பூர்வ லிங்க்குடன். | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | அதிகாரப்பூர்வமற்றது · ஒவ்வொரு அப்டேட்டிலும் அதிகாரப்பூர்வ இணையதள லிங்க் | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | காவல்துறை | |
| `sector_defence` | Defence & CAPF | ராணுவம் & CAPF | |
| `sector_railway` | Railway | ரயில்வே | |
| `sector_bank` | Bank | வங்கி | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | அறிவியல் & விண்வெளி | |
| `sector_state` | State govt | மாநில அரசு | |
| `sector_teaching` | Teaching | ஆசிரியர் பணி | |
| `state_all` | All India | அகில இந்தியா | |
| `state_UP` | Uttar Pradesh | உத்தரப் பிரதேசம் | |
| `state_BR` | Bihar | பீகார் | |
| `state_HR` | Haryana | ஹரியானா | |
| `anySector` | All sectors | அனைத்து துறைகள் | |
| `anyState` | All states | அனைத்து மாநிலங்கள் | |
| `jobsSector` | {name} jobs | {name} வேலைகள் | |
| `jobsState` | Government jobs in {name} | {name} அரசு வேலைகள் | |
| `jobsKind` | {name}: latest | {name}: சமீபத்தியவை | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | இந்தத் துறையிலிருந்து இன்னும் எதுவும் இல்லை. ஒரு ஆட்சேர்ப்பு அமைப்பின் அதிகாரப்பூர்வ இணையதளத்தைச் சரிபார்த்த பிறகே அதைச் சேர்க்கிறோம், எனவே இந்தப் பட்டியல் வளரும். | |
| `centralJobs` | Central govt (all India) | மத்திய அரசு (அகில இந்தியா) | |
| `recruitersTitle` | Recruiters we follow | நாங்கள் பின்தொடரும் ஆட்சேர்ப்பு அமைப்புகள் | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | இந்த அதிகாரப்பூர்வ இணையதளங்களிலிருந்து அப்டேட்கள் எடுக்கப்படுகின்றன. திறக்க ஒன்றைத் தட்டுங்கள். | |
| `moreUpdates` | See all updates | அனைத்து அப்டேட்களையும் பார் | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | வயது குறைவு: {date} அன்று உங்களுக்குக் குறைந்தது {n} வயது இருக்க வேண்டும். | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | வயது வரம்பைத் தாண்டிவிட்டது: {date} அன்று உங்கள் வயது {n}-க்குக் கீழ் இருக்க வேண்டும் (தளர்வு உட்பட). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | பொது வயது வரம்பைத் தாண்டிவிட்டது. முன்னாள் ராணுவத்தினர் / அரசு ஊழியர் / விதவைக்கான தளர்வு இதற்குப் பொருந்தலாம்: நோட்டிஃபிகேஷன் {ref} பாருங்கள். | |
| `r_education_graduate` | Needs a bachelor's degree. | இளநிலைப் பட்டம் தேவை. | |
| `r_education_12` | Needs Class 12 pass. | 12ஆம் வகுப்புத் தேர்ச்சி தேவை. | |
| `r_education_10` | Needs Class 10 pass. | 10ஆம் வகுப்புத் தேர்ச்சி தேவை. | |
| `r_result_needed_by` | Your result must be out by {date}. | {date}-க்குள் உங்கள் ரிசல்ட் வந்திருக்க வேண்டும். | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ரிசல்ட்டுக்குக் காத்திருப்பவர்கள் விண்ணப்பிக்கலாம் என்று நோட்டிஃபிகேஷனில் சொல்லப்படவில்லை; {date}-க்குள் உங்களிடம் தகுதி இருக்க வேண்டும். | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | இந்த நோட்டிஃபிகேஷனில் PwBD வயதுத் தளர்வு குறிப்பிடப்படவில்லை: நோட்டிஃபிகேஷனைச் சரிபாருங்கள். | |
| `r_gender_male` | Only for male candidates. | ஆண் விண்ணப்பதாரர்களுக்கு மட்டும். | |
| `r_gender_female` | Only for female candidates. | பெண் விண்ணப்பதாரர்களுக்கு மட்டும். | |
| `r_gender_other` | Only for {g} candidates. | {g} விண்ணப்பதாரர்களுக்கு மட்டும். | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | மாநிலத்தின் அலுவல்/வட்டார மொழியை இடைநிலைப் பள்ளி (Secondary) அளவில் படித்திருக்க வேண்டும். | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12ஆம் வகுப்பில் கணிதத்தில் 60% அல்லது பட்டப்படிப்பில் புள்ளியியல் ஒரு பாடமாக இருக்க வேண்டும். | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | பட்டம் புள்ளியியல், கணிதம், பொருளாதாரம், மக்கள்தொகையியல் (Demography), மக்கள்தொகை ஆய்வுகள், செயல்பாட்டு ஆராய்ச்சி (Operations Research), IT, கணினி அறிவியல்/பொறியியல்/பயன்பாடுகள், டேட்டா சயின்ஸ் அல்லது AI-இல் இருக்க வேண்டும். | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | உடல் தகுதித் தேர்வு நாளில் செல்லுபடியாகும் LMV (பைக் மற்றும் கார்) ஓட்டுநர் உரிமம் தேவை. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | இயற்பியல், வேதியியல், கணிதத்துடன் B.Sc., அல்லது 10ஆம் வகுப்புடன் மெக்கானிக்கல், சிவில், எலக்ட்ரிக்கல் அல்லது பட்டியலிடப்பட்ட பொறியியல் பிரிவில் 3 ஆண்டு டிப்ளமோ தேவை. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | உடல் மற்றும் மருத்துவத் தகுதிகள் பொருந்தும் (நோட்டிஃபிகேஷனைப் பாருங்கள்). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | Border Roads Organisation காலியிடங்கள்: ஆண்கள் மட்டும், உடல் தகுதி விதிகள் பொருந்தும். | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | சில அலுவலகங்களில் (நுகர்வோர் விவகாரங்கள் & உணவு, SSC, கலாச்சாரம்) இந்தப் பதவிக்குக் கணிதத்துடன் 12ஆம் வகுப்பு அறிவியல் தேவை. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | தேர்வு முறையில் ஸ்டெனோகிராஃபி திறன் தேர்வும் உண்டு. | |
| `listWord` | Lists: {n} | பட்டியல்கள்: {n} | |
