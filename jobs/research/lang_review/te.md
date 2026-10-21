# తెలుగు (te) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/te.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/te.js)

| 키 | English | తెలుగు | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | సర్కారీ జాబ్ అలర్ట్స్ | |
| `tabHome` | Home | హోమ్ | |
| `tabMore` | More | మరిన్ని | |
| `loading` | Loading… | లోడ్ అవుతోంది… | |
| `homeComing` | Exam dates in the next 30 days | రాబోయే 30 రోజుల్లో పరీక్ష తేదీలు | |
| `homeLatest` | Latest updates | తాజా అప్‌డేట్స్ | |
| `inDays` | in {n} days | {n} రోజుల్లో | |
| `onNow` | on now | ఇప్పుడు జరుగుతోంది | |
| `today` | today | ఈరోజు | |
| `theme` | Theme | థీమ్ | |
| `themeAuto` | Auto | ఆటో | |
| `themeLight` | Light | లైట్ | |
| `themeDark` | Dark | డార్క్ | |
| `language` | Language | భాష | |
| `howToApply` | How to apply | ఎలా అప్లై చేయాలి | |
| `documents` | Keep these documents ready | ఈ డాక్యుమెంట్లు రెడీగా ఉంచుకోండి | |
| `helplineTitle` | SSC helplines | SSC హెల్ప్‌లైన్ నంబర్లు | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ఫారం నింపడంలో లేదా కంప్యూటర్ ఆధారిత పరీక్షలో సమస్యలు ఉంటే. | |
| `typingTitle` | Typing / data entry speed practice | టైపింగ్ / డేటా ఎంట్రీ స్పీడ్ ప్రాక్టీస్ | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | కింద ఉన్న పేరాను టైప్ చేయండి. మీరు మొదటి కీ నొక్కగానే టైమర్ మొదలవుతుంది. స్పీడ్‌ను SSC చెప్పే విధంగానే గంటకు కీ డిప్రెషన్లలో చూపిస్తాం. | |
| `typingTest` | Standard | స్టాండర్డ్ | |
| `typingMinutes` | Time | సమయం | |
| `typingFull` | Full ({n} min) | పూర్తి ({n} నిమి.) | |
| `typingStart` | Start again | మళ్లీ మొదలుపెట్టండి | |
| `typingDone` | Time is up. | సమయం అయిపోయింది. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | గంటకు {kdph} కీ డిప్రెషన్లు (≈ {wpm} w.p.m.), {accuracy}% కచ్చితత్వం, టైప్ చేసిన {typed}లో {correct} సరైనవి | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} స్పీడ్ ({kdph}/గంట) అందుకున్నారు. | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} స్పీడ్ ({kdph}/గంట) కంటే తక్కువ: ప్రాక్టీస్ కొనసాగించండి. | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | ఇది ప్రాక్టీస్ కోసం మాత్రమే: SSC తన సొంత మూల్యాంకన నియమాల ప్రకారం తప్పులను లెక్కిస్తుంది. హిందీ టైపింగ్ ప్రాక్టీస్ ఇక్కడ లేదు. | |
| `faqTitle` | Frequently asked | తరచుగా అడిగే ప్రశ్నలు | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}కు అప్లై చేయడానికి చివరి తేదీ ఏది? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | అప్లై చేయడానికి చివరి తేదీ {date}; ఫీజు చెల్లింపు {fee}తో ముగుస్తుంది. | |
| `faqAge` | What is the age limit for {exam}? | {exam}కు వయోపరిమితి ఎంత? | |
| `faqFee` | What is the application fee? | అప్లికేషన్ ఫీజు ఎంత? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | జనరల్, OBC, EWS అభ్యర్థులకు ₹{amount}. {exempt} ఫీజు చెల్లించనవసరం లేదు. | |
| `faqSalary` | What is the salary? | జీతం ఎంత? | |
| `faqExam` | When is the {exam} exam? | {exam} పరీక్ష ఎప్పుడు? | |
| `faqVacancy` | How many vacancies are there? | ఎన్ని ఖాళీలు ఉన్నాయి? | |
| `faqQual` | What qualification is needed? | ఏ అర్హత కావాలి? | |
| `aboutTitle` | About this app | ఈ యాప్ గురించి | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | భారతదేశంలో ప్రభుత్వ ఉద్యోగాలకు సిద్ధమయ్యే వారి కోసం ఉచిత, అనధికారిక సహాయ యాప్. నోటీసులు, తేదీలు, నియమాలు అన్నీ రిక్రూట్‌మెంట్ సంస్థల అధికారిక వెబ్‌సైట్లు, నోటిఫికేషన్ల నుంచి రిఫరెన్స్‌లతో తీసుకున్నవి. సైన్-అప్ అవసరం లేదు, ఎలాంటి వ్యక్తిగత డేటా సేకరించం: మీ వివరాలు, రోల్ నంబర్ ఈ ఫోన్‌లోనే ఉంటాయి. | |
| `sourceRef` | Source: {ref} | మూలం: {ref} | |
| `toastSaved` | Saved | సేవ్ అయింది | |
| `toastRemoved` | Removed | తీసేశాం | |
| `toastFollow` | You follow this exam | మీరు ఈ పరీక్షను ఫాలో అవుతున్నారు | |
| `toastUnfollow` | Unfollowed | అన్‌ఫాలో చేశారు | |
| `settings` | Settings | సెట్టింగ్స్ | |
| `toolsTitle` | Tools | టూల్స్ | |
| `tabResult` | Result | ఫలితం | |
| `tabForMe` | For me | నా కోసం | |
| `resultTitle` | Check your result by roll number | రోల్ నంబర్‌తో మీ ఫలితం చూడండి | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | గత 120 రోజుల్లో SSC విడుదల చేసిన ఫలితాల లిస్టుల్లో వెతుకుతుంది, పెద్ద PDFలు డౌన్‌లోడ్ చేయకుండానే. చిన్న ఫైల్ తెచ్చుకోవడానికి మీ రోల్ నంబర్‌లోని చివరి రెండు అంకెలు మాత్రమే వాడతాం. | |
| `rollNo` | Roll number | రోల్ నంబర్ | |
| `resultExam` | Result | ఫలితం | |
| `find` | Find | వెతకండి | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | మీ అడ్మిట్ కార్డ్‌పై ఉన్నట్టే రోల్ నంబర్ ఎంటర్ చేయండి (8-14 అంకెలు). | |
| `foundIn` | Found in: {list} | ఉన్న లిస్టులు: {list} | |
| `notIn` | Not in: {list} | లేని లిస్టులు: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | మీ రోల్ నంబర్ ఈ లిస్టుల్లో లేదు. అంత మాత్రాన మీరు క్వాలిఫై కాలేదని కాదు: కింద ఉన్న అధికారిక PDFలు చూడండి. | |
| `officialFiles` | Official files of this notice | ఈ నోటీసు అధికారిక ఫైళ్లు | |
| `listCount` | {n} roll numbers | {n} రోల్ నంబర్లు | |
| `noLists` | No result lists to search right now. | ప్రస్తుతం వెతకడానికి ఫలితాల లిస్టులు లేవు. | |
| `forMeTitle` | Exams that suit you | మీకు సరిపోయే పరీక్షలు | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | మీ వివరాలు ఒక్కసారి నింపండి. మీరు అప్లై చేయగల ప్రతి SSC పరీక్ష, దాని స్టేటస్, తర్వాత ఏం జరుగుతుందో చూడవచ్చు. | |
| `forMeEdit` | Edit my details | నా వివరాలు మార్చండి | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | మీ అర్హతకు సరిపోయేవి (నియమాలు ఇంకా జోడించలేదు: నోటిఫికేషన్ చదవండి) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | మీ అర్హతకు రాబోయేవి (తాత్కాలికం) | |
| `saveDetails` | Show my exams | నా పరీక్షలు చూపించు | |
| `whenTitle` | What has happened and what is next | ఇప్పటివరకు ఏం జరిగింది, తర్వాత ఏంటి | |
| `st_done` | Done | పూర్తయింది | |
| `st_now` | Now | ఇప్పుడు | |
| `st_next` | Next | తర్వాత | |
| `st_later` | Later | ఆ తర్వాత | |
| `stage_notice` | Notice published | నోటిఫికేషన్ విడుదల | |
| `stage_apply` | Online application | ఆన్‌లైన్ అప్లికేషన్ | |
| `stage_admit_card` | Admit card / exam city | అడ్మిట్ కార్డ్ / పరీక్ష కేంద్రం సిటీ | |
| `stage_answer_key` | Answer key | ఆన్సర్ కీ | |
| `stage_result` | Result | ఫలితం | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ఇంకా తేదీ ప్రకటించలేదు | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | బేసిక్ పే నెలకు ₹{from}–{to} (లెవెల్ {l}; అలవెన్సులు అదనం) | |
| `savedLink` | ★ Saved | ★ సేవ్ చేసినవి | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ఇది ప్రభుత్వ వెబ్‌సైట్ కాదు, ఏ రిక్రూట్‌మెంట్ సంస్థతో గానీ ప్రభుత్వంతో గానీ సంబంధం లేదు. ఇక్కడి సమాచారం అంతా రిక్రూట్‌మెంట్ సంస్థల అధికారిక వెబ్‌సైట్ల నుంచే వస్తుంది, ప్రతి అప్‌డేట్‌కు అధికారిక ఫైల్ లేదా పేజీ లింక్ ఉంటుంది. అప్లై చేసే ముందు ఎప్పుడూ అధికారిక నోటిఫికేషన్ చూడండి. | |
| `tabExams` | Exams | పరీక్షలు | |
| `filters` | Filters | ఫిల్టర్లు | |
| `kindAll` | All | అన్నీ | |
| `kindRecruitment` | New jobs | కొత్త జాబ్స్ | |
| `kindResult` | Results | ఫలితాలు | |
| `kindAdmit` | Admit cards | అడ్మిట్ కార్డ్‌లు | |
| `kindAnswer` | Answer keys | ఆన్సర్ కీలు | |
| `kindAmendment` | Changes | మార్పులు | |
| `kindVacancy` | Vacancies | ఖాళీలు | |
| `kind_recruitment` | New job | కొత్త జాబ్ | |
| `kind_amendment` | Change | మార్పు | |
| `kind_result` | Result | ఫలితం | |
| `kind_answer_key` | Answer key | ఆన్సర్ కీ | |
| `kind_admit_card` | Admit card | అడ్మిట్ కార్డ్ | |
| `kind_vacancy` | Vacancy | ఖాళీలు | |
| `kind_schedule` | Exam dates | పరీక్ష తేదీలు | |
| `kind_notice` | Notice | నోటీసు | |
| `search` | Search jobs, exams, results | జాబ్స్, పరీక్షలు, ఫలితాలు వెతకండి | |
| `allExams` | All exams | అన్ని పరీక్షలు | |
| `myExams` | My exams only | నా పరీక్షలు మాత్రమే | |
| `anyQualification` | Any qualification | ఏ అర్హత అయినా | |
| `qual_10` | Class 10 | 10వ తరగతి | |
| `qual_12` | Class 12 | ఇంటర్ / 12వ తరగతి | |
| `qual_diploma` | Diploma | డిప్లొమా | |
| `qual_graduate` | Bachelor's degree | డిగ్రీ | |
| `qual_postgraduate` | Master's degree | పీజీ (మాస్టర్స్ డిగ్రీ) | |
| `departmental` | Departmental exams | డిపార్ట్‌మెంటల్ పరీక్షలు | |
| `updated` | Updated {time} IST | అప్‌డేట్: {time} IST | |
| `newSince` | {n} new since your last visit | మీరు చివరిసారి చూసిన తర్వాత {n} కొత్తవి | |
| `cutoffTitle` | Cut-off marks | కటాఫ్ మార్కులు | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC కటాఫ్‌లను ఫలితాల వివరణలో మాత్రమే ఇస్తుంది. ఆ నంబర్లను ఇక్కడ కాపీ చేయలేదు (ఒక్క అంకె తప్పైనా తప్పుదారి పట్టిస్తుంది): ప్రతి లింక్ కటాఫ్ వివరాలు మొదలయ్యే పేజీ వద్దే SSC PDFను తెరుస్తుంది. | |
| `cutoffFrom` | Open at page {p} | పేజీ {p} వద్ద తెరవండి | |
| `cutoffPages` | mentioned on p. {pages} | పేజీ {pages}లో ఉంది | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | గత 120 రోజుల ఫలితాల వివరణల్లో కటాఫ్ దొరకలేదు. | |
| `alertsTitle` | Alerts on this phone | ఈ ఫోన్‌లో అలర్ట్స్ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | మీరు ఫాలో అయ్యే పరీక్షకు ఏదైనా కొత్త సమాచారం వచ్చినప్పుడు, అప్లికేషన్ గడువు ముగియడానికి రెండు రోజుల ముందు నోటిఫికేషన్ వస్తుంది. | |
| `alertsOn` | Turn on alerts | అలర్ట్స్ ఆన్ చేయండి | |
| `alertsOff` | Turn off alerts | అలర్ట్స్ ఆఫ్ చేయండి | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | ముందు ఒక పరీక్షను ఫాలో అవ్వండి (పరీక్షలు → ఫాలో). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} పరీక్షలకు ఆన్‌లో ఉంది. ఎప్పుడు చెక్ చేయాలో Chrome నిర్ణయిస్తుంది, మీరు యాప్ తరచుగా ఓపెన్ చేస్తే ఎక్కువసార్లు చెక్ చేస్తుంది. అందుకే అప్‌డేట్ పోస్ట్ అయిన కొన్ని గంటల తర్వాత మీకు చేరవచ్చు. | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | ఈ సైట్‌కు నోటిఫికేషన్లు బ్లాక్ అయ్యాయి. బ్రౌజర్ సైట్ సెట్టింగ్స్‌లో వాటిని అనుమతించండి. | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | ఈ బ్రౌజర్ బ్యాక్‌గ్రౌండ్‌లో చెక్ చేయలేదు. Androidలో ముందు Chrome నుంచి ఈ యాప్ ఇన్‌స్టాల్ చేయండి. లేదంటే ప్రతి పరీక్ష పేజీలో ఉన్న క్యాలెండర్ రిమైండర్లు వాడండి. | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | మీ పరీక్షలకు మరో {n} అప్‌డేట్స్ | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: అప్లికేషన్లు {n} రోజుల్లో ముగుస్తాయి | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: అప్లై చేయడానికి ఈరోజే చివరి రోజు | |
| `installTitle` | Install the app | యాప్ ఇన్‌స్టాల్ చేయండి | |
| `installBtn` | Install | ఇన్‌స్టాల్ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | యాప్ లాగా ఓపెన్ అవుతుంది, ఇంటర్నెట్ లేకపోయినా పనిచేస్తుంది, Androidలో అలర్ట్స్ పొందవచ్చు. | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhoneలో: Share నొక్కి, తర్వాత "Add to Home Screen" ఎంచుకోండి. | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ఫాలో అవుతున్నారు. ఫోన్ అలర్ట్స్ కోసం: మరిన్ని → అలర్ట్స్ ఆన్ చేయండి | |
| `rollPlaceholder` | Roll number on your admit card | మీ అడ్మిట్ కార్డ్‌పై ఉన్న రోల్ నంబర్ | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} పరీక్షలకు ఆన్‌లో ఉంది. SSC నోటీసులు సాధారణంగా పోస్ట్ అయిన గంటలోపే మీకు వస్తాయి; ఫలితాలు, ఇతర బోర్డుల నోటీసులకు 3 గంటల వరకు పట్టవచ్చు. | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | ఈ బ్రౌజర్ అలర్ట్స్ చూపించలేదు. iPhoneలో ముందు ఈ యాప్‌ను Home Screenకు జోడించండి (Share → Add to Home Screen), తర్వాత అక్కడి నుంచి ఓపెన్ చేయండి. | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | అలర్ట్స్ ఆన్ కాలేదు. మీ కనెక్షన్ చెక్ చేసి మళ్లీ ప్రయత్నించండి. | |
| `allBoards` | All recruiters | అన్ని రిక్రూట్‌మెంట్ సంస్థలు | |
| `newBadge` | NEW | కొత్త | |
| `nothing` | Nothing here in the last 120 days. | గత 120 రోజుల్లో ఇక్కడ ఏమీ లేదు. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | లోడ్ కాలేదు. మీ ఇంటర్నెట్ కనెక్షన్ చెక్ చేసి మళ్లీ ప్రయత్నించండి. | |
| `offline` | Offline: showing the copy saved on this phone. | ఆఫ్‌లైన్: ఈ ఫోన్‌లో సేవ్ అయిన కాపీ చూపిస్తున్నాం. | |
| `save` | Save | సేవ్ | |
| `saved` | Saved | సేవ్ అయింది | |
| `share` | Share | షేర్ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | అధికారిక పేజీ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ అధికారిక లింక్: {url} ⏎ {app} ద్వారా (ఇది ప్రభుత్వ సైట్ కాదు) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ప్రతి పరీక్షకు తాజా నోటిఫికేషన్, తేదీలు, ఫీజు, వయోపరిమితి, అప్పటి నుంచి వచ్చిన అన్ని నోటీసులు. | |
| `follow` | Follow | ఫాలో | |
| `following` | Following | ఫాలో అవుతున్నారు | |
| `minQual` | Qualification: {q} | అర్హత: {q} | |
| `qualUnknown` | Qualification: see the Notice | అర్హత: నోటిఫికేషన్ చూడండి | |
| `calendarTitle` | SSC's tentative calendar | SSC తాత్కాలిక క్యాలెండర్ | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | ఇవి ప్లాన్లు మాత్రమే, నోటిఫికేషన్ కాదు: అసలు తేదీలు ప్రతి నోటిఫికేషన్‌లో ఉంటాయి, అవి వేరుగా ఉండవచ్చు. | |
| `resourcesTitle` | Official resources | అధికారిక వనరులు | |
| `res_syllabus` | Syllabus | సిలబస్ | |
| `res_vacancy` | Vacancies (tentative / final) | ఖాళీలు (తాత్కాలిక / తుది) | |
| `res_previous_papers` | Previous year papers | పాత ప్రశ్నపత్రాలు | |
| `res_certificate_formats` | Certificate formats | సర్టిఫికెట్ ఫార్మాట్లు | |
| `res_instructions` | Instructions | సూచనలు | |
| `back` | ← All exams | ← అన్ని పరీక్షలు | |
| `latestNotice` | Latest Notice | తాజా నోటిఫికేషన్ | |
| `importantDates` | Important dates | ముఖ్యమైన తేదీలు | |
| `applyWindow` | Apply online | ఆన్‌లైన్ అప్లికేషన్ | |
| `feeLastDate` | Fee payment last date | ఫీజు చెల్లింపు చివరి తేదీ | |
| `correctionWindow` | Form correction window | ఫారం కరెక్షన్ తేదీలు | |
| `tentative` | tentative | తాత్కాలికం | |
| `applicationFee` | Application fee | అప్లికేషన్ ఫీజు | |
| `feeGeneral` | General / OBC / EWS | జనరల్ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | మినహాయింపు (₹0) | |
| `exempt_female` | Women | మహిళలు | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | మాజీ సైనికులు | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | కరెక్షన్: మొదటిసారి ₹{a}, రెండోసారి ₹{b} (అభ్యర్థులందరికీ) | |
| `ageLimit` | Age limit (as on {date}) | వయోపరిమితి ({date} నాటికి) | |
| `ageBand` | {band} years: born {from} to {to} | {band} ఏళ్లు: {from} నుంచి {to} మధ్య పుట్టినవారు | |
| `relaxation` | Upper age relaxation | గరిష్ఠ వయసులో సడలింపు | |
| `relaxYears` | {n} years | {n} ఏళ్లు | |
| `notComputed` | Not calculated here: {list} | ఇక్కడ లెక్కించలేదు: {list} | |
| `vacancies` | Vacancies | ఖాళీలు | |
| `vacancyCount` | About {n} ({tentative}) | సుమారు {n} ({tentative}) | |
| `posts` | Posts | పోస్టులు | |
| `importantLinks` | Important links | ముఖ్యమైన లింకులు | |
| `applyOnline` | Apply online (ssc.gov.in) | ఆన్‌లైన్‌లో అప్లై చేయండి (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | అధికారిక నోటిఫికేషన్ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} నాటి మార్పు | |
| `allNotices` | All notices for this exam | ఈ పరీక్షకు సంబంధించిన అన్ని నోటీసులు | |
| `addCalendar` | Add dates to my calendar | తేదీలను నా క్యాలెండర్‌లో జోడించు | |
| `checkThis` | Check my eligibility | నా అర్హత చెక్ చేయండి | |
| `status_open` | Applications open till {date} | {date} వరకు అప్లికేషన్లు ఓపెన్ | |
| `status_upcoming` | Applications open on {date} | అప్లికేషన్లు {date}న మొదలవుతాయి | |
| `status_closed` | Applications closed on {date} | అప్లికేషన్లు {date}న ముగిశాయి | |
| `daysLeft` | {n} days left | {n} రోజులు మిగిలాయి | |
| `lastDay` | Last day today | ఈరోజే చివరి రోజు | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | ఈ పరీక్షకు ఇంకా అర్హత నియమాలు జోడించలేదు: నోటిఫికేషన్ చదవండి. | |
| `examLabel` | Exam | పరీక్ష | |
| `dob` | Date of birth (as in Class 10 certificate) | పుట్టిన తేదీ (10వ తరగతి సర్టిఫికెట్‌లో ఉన్నట్టు) | |
| `category` | Category | కేటగిరీ | |
| `catUR` | General (UR) | జనరల్ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (నాన్-క్రీమీ లేయర్) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | అత్యున్నత విద్యార్హత | |
| `edu10` | Class 10 | 10వ తరగతి | |
| `edu12` | Class 12 | ఇంటర్ / 12వ తరగతి | |
| `eduGraduate` | Bachelor's degree or higher | డిగ్రీ లేదా అంతకంటే ఎక్కువ | |
| `gender` | Gender | లింగం | |
| `male` | Male | పురుషుడు | |
| `female` | Female | స్త్రీ | |
| `other` | Other | ఇతరులు | |
| `appearing` | Final exam of that level taken, result not out yet | ఆ స్థాయి ఫైనల్ పరీక్ష రాశాను, ఫలితం ఇంకా రాలేదు | |
| `pwbd` | Person with benchmark disability (PwBD) | బెంచ్‌మార్క్ వైకల్యం ఉన్న వ్యక్తి (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | మాజీ సైనికుడు, కేంద్ర ప్రభుత్వ ఉద్యోగి, లేదా వితంతువు/విడాకులు పొందిన మహిళ | |
| `check` | Check | చెక్ చేయండి | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | మీ జవాబులు ఈ డివైస్‌లోనే ఉంటాయి. ఎక్కడికీ ఏమీ పంపించం. | |
| `enterDob` | Enter your date of birth. | మీ పుట్టిన తేదీ ఎంటర్ చేయండి. | |
| `closedNote` | Shown so you can prepare for the next cycle. | తర్వాతి సైకిల్‌కు సిద్ధం కావడానికి చూపిస్తున్నాం. | |
| `canApply` | {n} can apply | {n} అప్లై చేయవచ్చు | |
| `toCheck` | {n} to check | {n} చెక్ చేయాలి | |
| `notEligible` | {n} not eligible | {n} అర్హత లేదు | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ఫీజు: {fee}. వర్తించిన వయో సడలింపు: {n} ఏళ్లు. వయసును {date} నాటికి లెక్కిస్తారు. | |
| `feeNone` | none (exempt) | లేదు (మినహాయింపు) | |
| `st_eligible` | You can apply | మీరు అప్లై చేయవచ్చు | |
| `st_check` | Check one condition | ఒక షరతు చెక్ చేయండి | |
| `st_ineligible` | Not eligible | అర్హత లేదు | |
| `payLevel` | Pay Level {l} · Group {g} | పే లెవెల్ {l} · గ్రూప్ {g} | |
| `groupOnly` | Group {g} | గ్రూప్ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | {from} నుంచి {to} మధ్య పుట్టి ఉండాలి (మీకు) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | నియమాలు {date} నాటి నోటిఫికేషన్ నుంచి తీసుకున్నాం ({checked}న చెక్ చేశాం). | |
| `sigTitle` | Signature for SSC forms | SSC ఫారాల కోసం సంతకం | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC {min}-{max} KB సైజులో, సుమారు {w} cm × {h} cm ఉన్న JPEG అడుగుతుంది. తెల్ల కాగితంపై నల్ల ఇంకుతో సంతకం చేసి స్పష్టమైన ఫోటో తీయండి. | |
| `sigPick` | Choose signature photo | సంతకం ఫోటో ఎంచుకోండి | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | రెడీ: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ఫైల్‌ను {min} నుంచి {max} KB మధ్యకు తేలేకపోయాం. ఇంకా స్పష్టమైన ఫోటో ప్రయత్నించండి. | |
| `sigDownload` | Download JPEG | JPEG డౌన్‌లోడ్ చేయండి | |
| `photoTitle` | Your photograph | మీ ఫోటో | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC ఇప్పుడు అప్‌లోడ్ చేసిన ఫోటో తీసుకోదు: అప్లికేషన్ ఫారమే మీ కెమెరాతో లైవ్‌గా ఫోటో తీస్తుంది, పాత ఫోటోను ఫోటో తీస్తే ఫారం రిజెక్ట్ అవుతుంది. మంచి వెలుతురు, సాదా బ్యాక్‌గ్రౌండ్ ఉండాలి, కెమెరా కంటి ఎత్తులో ఉంచండి, టోపీ, మాస్క్, కళ్లజోడు వద్దు. | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | మూలం: నోటిఫికేషన్ పేరా 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | వయసు కాలిక్యులేటర్ | |
| `ageOn` | Age on | ఈ తేదీ నాటికి వయసు | |
| `ageResult` | {y} years, {m} months, {d} days | {y} ఏళ్లు, {m} నెలలు, {d} రోజులు | |
| `ageInvalid` | Date of birth must be before that date. | పుట్టిన తేదీ ఆ తేదీ కంటే ముందు ఉండాలి. | |
| `docsTitle` | Certificate formats | సర్టిఫికెట్ ఫార్మాట్లు | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | కేటగిరీ, వైకల్యం, మాజీ సైనికుల సర్టిఫికెట్ల కోసం SSC ఫార్మాట్లు. | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ఇంకా ఏమీ సేవ్ చేయలేదు. ఏ నోటీసుపైనైనా సేవ్ నొక్కండి. | |
| `followedExams` | Exams you follow | మీరు ఫాలో అయ్యే పరీక్షలు | |
| `chooseLanguage` | Choose your language | మీ భాషను ఎంచుకోండి | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | నోటీసుల టైటిల్స్ రిక్రూట్‌మెంట్ సంస్థ ప్రచురించిన భాషలోనే ఉంటాయి (ఎక్కువగా ఇంగ్లీష్ లేదా హిందీ). | |
| `qaLatest` | Latest jobs | తాజా జాబ్స్ | |
| `qaResults` | Results | ఫలితాలు | |
| `qaAdmit` | Admit cards | అడ్మిట్ కార్డ్‌లు | |
| `qaAnswer` | Answer keys | ఆన్సర్ కీలు | |
| `qaEligibility` | Check eligibility | అర్హత చెక్ | |
| `qaRoll` | Result by roll no. | రోల్ నం.తో ఫలితం | |
| `homeSectors` | Jobs by sector | రంగాల వారీగా జాబ్స్ | |
| `homeStates` | Jobs by state | రాష్ట్రాల వారీగా జాబ్స్ | |
| `homeClosing` | Closing soon | త్వరలో ముగిసేవి | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ప్రతి అప్‌డేట్ WhatsApp లేదా Telegramలో పొందండి | |
| `homeChannelsNote` | Free. One message per update, with the official link. | ఉచితం. ప్రతి అప్‌డేట్‌కు ఒక మెసేజ్, అధికారిక లింక్‌తో. | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | అనధికారికం · ప్రతి అప్‌డేట్‌కు అధికారిక వెబ్‌సైట్ లింక్ ఉంటుంది | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | పోలీస్ | |
| `sector_defence` | Defence & CAPF | డిఫెన్స్ & CAPF | |
| `sector_railway` | Railway | రైల్వే | |
| `sector_bank` | Bank | బ్యాంక్ | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | సైన్స్ & స్పేస్ | |
| `sector_state` | State govt | రాష్ట్ర ప్రభుత్వం | |
| `sector_teaching` | Teaching | టీచింగ్ | |
| `state_all` | All India | ఆల్ ఇండియా | |
| `state_UP` | Uttar Pradesh | ఉత్తర ప్రదేశ్ | |
| `state_BR` | Bihar | బీహార్ | |
| `state_HR` | Haryana | హర్యానా | |
| `anySector` | All sectors | అన్ని రంగాలు | |
| `anyState` | All states | అన్ని రాష్ట్రాలు | |
| `jobsSector` | {name} jobs | {name} జాబ్స్ | |
| `jobsState` | Government jobs in {name} | {name} ప్రభుత్వ ఉద్యోగాలు | |
| `jobsKind` | {name}: latest | {name}: తాజావి | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | ఈ రంగం నుంచి ఇంకా ఏమీ లేదు. ఒక రిక్రూట్‌మెంట్ సంస్థ అధికారిక వెబ్‌సైట్‌ను చెక్ చేసిన తర్వాతే దాన్ని జోడిస్తాం, కాబట్టి ఈ లిస్టు పెరుగుతుంది. | |
| `centralJobs` | Central govt (all India) | కేంద్ర ప్రభుత్వం (ఆల్ ఇండియా) | |
| `recruitersTitle` | Recruiters we follow | మేము ఫాలో అయ్యే రిక్రూట్‌మెంట్ సంస్థలు | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | అప్‌డేట్స్ ఈ అధికారిక వెబ్‌సైట్ల నుంచే తీసుకుంటాం. ఓపెన్ చేయడానికి దేనిపైనైనా నొక్కండి. | |
| `moreUpdates` | See all updates | అన్ని అప్‌డేట్స్ చూడండి | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | వయసు తక్కువ: {date} నాటికి మీకు కనీసం {n} ఏళ్లు ఉండాలి. | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | వయోపరిమితి దాటింది: {date} నాటికి మీ వయసు {n} ఏళ్ల లోపు ఉండాలి (సడలింపుతో కలిపి). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | సాధారణ వయోపరిమితి దాటింది. మాజీ సైనికులు / ప్రభుత్వ ఉద్యోగులు / వితంతువులకు ఉండే సడలింపు వర్తించవచ్చు: నోటిఫికేషన్ {ref} చూడండి. | |
| `r_education_graduate` | Needs a bachelor's degree. | డిగ్రీ ఉండాలి. | |
| `r_education_12` | Needs Class 12 pass. | ఇంటర్ / 12వ తరగతి పాస్ అయి ఉండాలి. | |
| `r_education_10` | Needs Class 10 pass. | 10వ తరగతి పాస్ అయి ఉండాలి. | |
| `r_result_needed_by` | Your result must be out by {date}. | మీ ఫలితం {date} లోపు రావాలి. | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ఫలితాల కోసం ఎదురుచూస్తున్న అభ్యర్థులు అప్లై చేయవచ్చని నోటిఫికేషన్‌లో లేదు; {date} నాటికి మీకు అర్హత ఉండాలి. | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | ఈ నోటిఫికేషన్‌లో PwBDకి వయో సడలింపు లేదు: నోటిఫికేషన్ చూడండి. | |
| `r_gender_male` | Only for male candidates. | పురుష అభ్యర్థులకు మాత్రమే. | |
| `r_gender_female` | Only for female candidates. | మహిళా అభ్యర్థులకు మాత్రమే. | |
| `r_gender_other` | Only for {g} candidates. | {g} అభ్యర్థులకు మాత్రమే. | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | సెకండరీ స్కూల్ స్థాయిలో ఆ రాష్ట్ర అధికారిక/ప్రాంతీయ భాష చదివి ఉండాలి. | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12వ తరగతిలో మ్యాథ్స్‌లో 60% ఉండాలి, లేదా డిగ్రీలో స్టాటిస్టిక్స్ ఒక సబ్జెక్టుగా ఉండాలి. | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | డిగ్రీ స్టాటిస్టిక్స్, మ్యాథమెటిక్స్, ఎకనామిక్స్, డెమోగ్రఫీ, పాపులేషన్ స్టడీస్, ఆపరేషన్స్ రీసెర్చ్, IT, కంప్యూటర్ సైన్స్/ఇంజినీరింగ్/అప్లికేషన్స్, డేటా సైన్స్ లేదా AIలో ఉండాలి. | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ఫిజికల్ టెస్ట్ రోజున చెల్లుబాటయ్యే LMV (బైక్, కార్) డ్రైవింగ్ లైసెన్స్ ఉండాలి. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ఫిజిక్స్, కెమిస్ట్రీ, మ్యాథమెటిక్స్‌తో B.Sc. ఉండాలి, లేదా 10వ తరగతితో పాటు మెకానికల్, సివిల్, ఎలక్ట్రికల్ లేదా జాబితాలోని ఇంజినీరింగ్ బ్రాంచ్‌లో 3 ఏళ్ల డిప్లొమా ఉండాలి. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | ఫిజికల్, మెడికల్ ప్రమాణాలు వర్తిస్తాయి (నోటిఫికేషన్ చూడండి). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | బార్డర్ రోడ్స్ ఆర్గనైజేషన్ ఖాళీలు: పురుషులకు మాత్రమే, ఫిజికల్ ప్రమాణాలు వర్తిస్తాయి. | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | కొన్ని ఆఫీసుల్లో (Consumer Affairs & Food, SSC, Culture) ఈ పోస్టుకు మ్యాథమెటిక్స్‌తో 12వ తరగతి సైన్స్ ఉండాలి. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | సెలెక్షన్‌లో స్టెనోగ్రఫీ స్కిల్ టెస్ట్ కూడా ఉంటుంది. | |
| `listWord` | Lists: {n} | లిస్టులు: {n} | |
