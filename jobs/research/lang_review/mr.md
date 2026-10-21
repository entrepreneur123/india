# मराठी (mr) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/mr.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/mr.js)

| 키 | English | मराठी | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | सरकारी जॉब अलर्ट | |
| `tabHome` | Home | होम | |
| `tabMore` | More | अधिक | |
| `loading` | Loading… | लोड होत आहे… | |
| `homeComing` | Exam dates in the next 30 days | पुढील 30 दिवसांतील परीक्षा तारखा | |
| `homeLatest` | Latest updates | ताजे अपडेट्स | |
| `inDays` | in {n} days | {n} दिवसांनी | |
| `onNow` | on now | सुरू आहे | |
| `today` | today | आज | |
| `theme` | Theme | थीम | |
| `themeAuto` | Auto | ऑटो | |
| `themeLight` | Light | लाइट | |
| `themeDark` | Dark | डार्क | |
| `language` | Language | भाषा | |
| `howToApply` | How to apply | अर्ज कसा करावा | |
| `documents` | Keep these documents ready | ही कागदपत्रे तयार ठेवा | |
| `helplineTitle` | SSC helplines | SSC हेल्पलाइन | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | फॉर्म भरताना किंवा कॉम्प्युटर आधारित परीक्षेत अडचण आल्यास. | |
| `typingTitle` | Typing / data entry speed practice | टायपिंग / डेटा एंट्री स्पीड सराव | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | खालील उतारा टाइप करा. पहिली की दाबताच टायमर सुरू होतो. स्पीड SSC प्रमाणेच प्रति तास की डिप्रेशन्समध्ये दाखवला आहे. | |
| `typingTest` | Standard | स्टँडर्ड | |
| `typingMinutes` | Time | वेळ | |
| `typingFull` | Full ({n} min) | पूर्ण ({n} मिनिटे) | |
| `typingStart` | Start again | पुन्हा सुरू करा | |
| `typingDone` | Time is up. | वेळ संपली. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | प्रति तास {kdph} की डिप्रेशन्स (≈ {wpm} w.p.m.), {accuracy}% अचूकता, {typed} पैकी {correct} बरोबर | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} चा स्पीड ({kdph}/तास) पूर्ण होतो. | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} च्या स्पीडपेक्षा ({kdph}/तास) कमी: सराव सुरू ठेवा. | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | फक्त सरावासाठी: SSC स्वतःच्या मूल्यमापन नियमांनुसार चुका मोजते. हिंदी टायपिंगचा सराव इथे उपलब्ध नाही. | |
| `faqTitle` | Frequently asked | नेहमी विचारले जाणारे प्रश्न | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} साठी अर्ज करण्याची शेवटची तारीख कोणती? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | अर्ज करण्याची शेवटची तारीख {date} आहे; फी भरण्याची मुदत {fee} रोजी संपते. | |
| `faqAge` | What is the age limit for {exam}? | {exam} साठी वयोमर्यादा किती आहे? | |
| `faqFee` | What is the application fee? | अर्ज फी किती आहे? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | जनरल, OBC आणि EWS उमेदवारांसाठी ₹{amount}. {exempt} यांना फी नाही. | |
| `faqSalary` | What is the salary? | पगार किती आहे? | |
| `faqExam` | When is the {exam} exam? | {exam} परीक्षा कधी आहे? | |
| `faqVacancy` | How many vacancies are there? | किती जागा आहेत? | |
| `faqQual` | What qualification is needed? | कोणती पात्रता लागते? | |
| `aboutTitle` | About this app | या ॲपबद्दल | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | भारतात सरकारी नोकरीची तयारी करणाऱ्यांसाठी एक मोफत, अनधिकृत मदतनीस. नोटिसा, तारखा आणि नियम भरती संस्थांच्या अधिकृत वेबसाइट्स आणि नोटिसांमधून, संदर्भांसह घेतले आहेत. साइन-अप नाही, कोणताही वैयक्तिक डेटा गोळा केला जात नाही: तुमची माहिती आणि रोल नंबर याच फोनवर राहतात. | |
| `sourceRef` | Source: {ref} | स्रोत: {ref} | |
| `toastSaved` | Saved | सेव्ह केले | |
| `toastRemoved` | Removed | काढून टाकले | |
| `toastFollow` | You follow this exam | तुम्ही ही परीक्षा फॉलो करत आहात | |
| `toastUnfollow` | Unfollowed | अनफॉलो केले | |
| `settings` | Settings | सेटिंग्ज | |
| `toolsTitle` | Tools | टूल्स | |
| `tabResult` | Result | रिझल्ट | |
| `tabForMe` | For me | माझ्यासाठी | |
| `resultTitle` | Check your result by roll number | रोल नंबरने तुमचा रिझल्ट पहा | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | मोठ्या PDF डाउनलोड न करता, SSC ने मागील 120 दिवसांत प्रसिद्ध केलेल्या रिझल्ट याद्यांमध्ये शोधते. छोटी फाइल आणण्यासाठी तुमच्या रोल नंबरचे फक्त शेवटचे दोन अंक वापरले जातात. | |
| `rollNo` | Roll number | रोल नंबर | |
| `resultExam` | Result | रिझल्ट | |
| `find` | Find | शोधा | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | ॲडमिट कार्डवर छापलेला रोल नंबर टाका (8-14 अंक). | |
| `foundIn` | Found in: {list} | यात सापडला: {list} | |
| `notIn` | Not in: {list} | यात नाही: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | तुमचा रोल नंबर या याद्यांमध्ये नाही. फक्त एवढ्यावरून तुम्ही पात्र ठरला नाहीत असे नाही: खालील अधिकृत PDF तपासा. | |
| `officialFiles` | Official files of this notice | या नोटिसच्या अधिकृत फाइल्स | |
| `listCount` | {n} roll numbers | {n} रोल नंबर | |
| `noLists` | No result lists to search right now. | सध्या शोधण्यासाठी कोणतीही रिझल्ट यादी नाही. | |
| `forMeTitle` | Exams that suit you | तुमच्यासाठी योग्य परीक्षा | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | तुमची माहिती एकदाच भरा. तुम्ही अर्ज करू शकता अशा सर्व SSC परीक्षा, त्यांची स्थिती आणि पुढे काय आहे ते दिसेल. | |
| `forMeEdit` | Edit my details | माझी माहिती बदला | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | तुमच्या पात्रतेनुसार (नियम अजून जोडलेले नाहीत: नोटिस वाचा) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | तुमच्या पात्रतेनुसार येणाऱ्या परीक्षा (संभाव्य) | |
| `saveDetails` | Show my exams | माझ्या परीक्षा दाखवा | |
| `whenTitle` | What has happened and what is next | काय झाले आणि पुढे काय | |
| `st_done` | Done | झाले | |
| `st_now` | Now | आता | |
| `st_next` | Next | पुढे | |
| `st_later` | Later | नंतर | |
| `stage_notice` | Notice published | नोटिस प्रसिद्ध | |
| `stage_apply` | Online application | ऑनलाइन अर्ज | |
| `stage_admit_card` | Admit card / exam city | ॲडमिट कार्ड / परीक्षा शहर | |
| `stage_answer_key` | Answer key | आन्सर की | |
| `stage_result` | Result | रिझल्ट | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ने अजून तारीख जाहीर केलेली नाही | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | मूळ वेतन ₹{from}–{to} दरमहा (लेव्हल {l}; भत्ते वेगळे) | |
| `savedLink` | ★ Saved | ★ सेव्ह केलेले | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ही सरकारी वेबसाइट नाही आणि कोणत्याही भरती संस्थेशी किंवा सरकारशी जोडलेली नाही. येथील सर्व माहिती भरती संस्थांच्या अधिकृत वेबसाइट्सवरून घेतली आहे आणि प्रत्येक अपडेटसोबत अधिकृत फाइल किंवा पेजची लिंक आहे. अर्ज करण्यापूर्वी नेहमी अधिकृत नोटिस तपासा. | |
| `tabExams` | Exams | परीक्षा | |
| `filters` | Filters | फिल्टर | |
| `kindAll` | All | सर्व | |
| `kindRecruitment` | New jobs | नवीन भरती | |
| `kindResult` | Results | रिझल्ट | |
| `kindAdmit` | Admit cards | ॲडमिट कार्ड | |
| `kindAnswer` | Answer keys | आन्सर की | |
| `kindAmendment` | Changes | बदल | |
| `kindVacancy` | Vacancies | रिक्त जागा | |
| `kind_recruitment` | New job | नवीन भरती | |
| `kind_amendment` | Change | बदल | |
| `kind_result` | Result | रिझल्ट | |
| `kind_answer_key` | Answer key | आन्सर की | |
| `kind_admit_card` | Admit card | ॲडमिट कार्ड | |
| `kind_vacancy` | Vacancy | रिक्त जागा | |
| `kind_schedule` | Exam dates | परीक्षा तारखा | |
| `kind_notice` | Notice | नोटिस | |
| `search` | Search jobs, exams, results | नोकरी, परीक्षा, रिझल्ट शोधा | |
| `allExams` | All exams | सर्व परीक्षा | |
| `myExams` | My exams only | फक्त माझ्या परीक्षा | |
| `anyQualification` | Any qualification | कोणतीही पात्रता | |
| `qual_10` | Class 10 | 10वी | |
| `qual_12` | Class 12 | 12वी | |
| `qual_diploma` | Diploma | डिप्लोमा | |
| `qual_graduate` | Bachelor's degree | पदवी | |
| `qual_postgraduate` | Master's degree | पदव्युत्तर पदवी | |
| `departmental` | Departmental exams | खातेनिहाय परीक्षा | |
| `updated` | Updated {time} IST | अपडेट: {time} IST | |
| `newSince` | {n} new since your last visit | तुमच्या मागील भेटीनंतर {n} नवीन | |
| `cutoffTitle` | Cut-off marks | कट-ऑफ गुण | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC कट-ऑफ फक्त रिझल्टच्या मजकुरातच देते. आकडे इथे कॉपी केलेले नाहीत (एक चुकीचा आकडाही दिशाभूल करू शकतो): प्रत्येक लिंक SSC ची PDF, कट-ऑफची माहिती सुरू होते त्या पानावर उघडते. | |
| `cutoffFrom` | Open at page {p} | पान {p} वर उघडा | |
| `cutoffPages` | mentioned on p. {pages} | पान {pages} वर उल्लेख | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | मागील 120 दिवसांतील रिझल्टमध्ये कोणताही कट-ऑफ सापडला नाही. | |
| `alertsTitle` | Alerts on this phone | या फोनवर अलर्ट | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | तुम्ही फॉलो करत असलेल्या परीक्षेची नवीन माहिती आल्यावर, आणि अर्जाची मुदत संपण्याच्या दोन दिवस आधी नोटिफिकेशन येईल. | |
| `alertsOn` | Turn on alerts | अलर्ट चालू करा | |
| `alertsOff` | Turn off alerts | अलर्ट बंद करा | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | आधी एखादी परीक्षा फॉलो करा (परीक्षा → फॉलो करा). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} परीक्षांसाठी चालू. कधी तपासायचे ते Chrome ठरवते, तुम्ही ॲप वारंवार उघडल्यास जास्त वेळा. त्यामुळे बातमी पोस्ट झाल्यानंतर काही तासांनी पोहोचू शकते. | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | या साइटसाठी नोटिफिकेशन ब्लॉक आहेत. ब्राउझरच्या साइट सेटिंग्जमध्ये परवानगी द्या. | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | हा ब्राउझर बॅकग्राउंडमध्ये तपासू शकत नाही. Android वर आधी Chrome मधून हे ॲप इन्स्टॉल करा. नाहीतर प्रत्येक परीक्षेच्या पेजवरील कॅलेंडर रिमाइंडर वापरा. | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | तुमच्या परीक्षांसाठी आणखी {n} अपडेट्स | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: अर्जाची मुदत {n} दिवसांत संपते | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: आज अर्ज करण्याचा शेवटचा दिवस | |
| `installTitle` | Install the app | ॲप इन्स्टॉल करा | |
| `installBtn` | Install | इन्स्टॉल | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ॲपसारखे उघडते, ऑफलाइन चालते आणि Android वर अलर्ट मिळतात. | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone वर: Share वर टॅप करा, मग "Add to Home Screen". | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | फॉलो केले. फोनवर अलर्टसाठी: अधिक → अलर्ट चालू करा | |
| `rollPlaceholder` | Roll number on your admit card | ॲडमिट कार्डवरील रोल नंबर | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} परीक्षांसाठी चालू. SSC च्या नोटिसा साधारणपणे पोस्ट झाल्यापासून एका तासात पोहोचतात; रिझल्ट आणि इतर बोर्डांच्या नोटिसांना 3 तासांपर्यंत लागू शकतात. | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | हा ब्राउझर अलर्ट दाखवू शकत नाही. iPhone वर आधी हे ॲप होम स्क्रीनवर जोडा (Share → Add to Home Screen), मग तिथूनच उघडा. | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | अलर्ट चालू करता आले नाहीत. इंटरनेट कनेक्शन तपासा आणि पुन्हा प्रयत्न करा. | |
| `allBoards` | All recruiters | सर्व भरती संस्था | |
| `newBadge` | NEW | नवीन | |
| `nothing` | Nothing here in the last 120 days. | मागील 120 दिवसांत इथे काहीही नाही. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | लोड झाले नाही. इंटरनेट कनेक्शन तपासा आणि पुन्हा प्रयत्न करा. | |
| `offline` | Offline: showing the copy saved on this phone. | ऑफलाइन: या फोनवर सेव्ह केलेली कॉपी दाखवत आहोत. | |
| `save` | Save | सेव्ह करा | |
| `saved` | Saved | सेव्ह केले | |
| `share` | Share | शेअर करा | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | अधिकृत पेज | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ अधिकृत लिंक: {url} ⏎ {app} वरून (सरकारी साइट नाही) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | प्रत्येक परीक्षेची ताजी नोटिस, तारखा, फी, वयोमर्यादा आणि त्यानंतरच्या सर्व नोटिसा. | |
| `follow` | Follow | फॉलो करा | |
| `following` | Following | फॉलो करत आहात | |
| `minQual` | Qualification: {q} | पात्रता: {q} | |
| `qualUnknown` | Qualification: see the Notice | पात्रता: नोटिस पहा | |
| `calendarTitle` | SSC's tentative calendar | SSC चे संभाव्य वेळापत्रक | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | हे नियोजन आहे, नोटिस नाही: खऱ्या तारखा प्रत्येक नोटिसमध्ये असतात आणि वेगळ्या असू शकतात. | |
| `resourcesTitle` | Official resources | अधिकृत साहित्य | |
| `res_syllabus` | Syllabus | अभ्यासक्रम | |
| `res_vacancy` | Vacancies (tentative / final) | रिक्त जागा (संभाव्य / अंतिम) | |
| `res_previous_papers` | Previous year papers | मागील वर्षांचे पेपर | |
| `res_certificate_formats` | Certificate formats | प्रमाणपत्रांचे नमुने | |
| `res_instructions` | Instructions | सूचना | |
| `back` | ← All exams | ← सर्व परीक्षा | |
| `latestNotice` | Latest Notice | ताजी नोटिस | |
| `importantDates` | Important dates | महत्त्वाच्या तारखा | |
| `applyWindow` | Apply online | ऑनलाइन अर्ज | |
| `feeLastDate` | Fee payment last date | फी भरण्याची शेवटची तारीख | |
| `correctionWindow` | Form correction window | फॉर्म दुरुस्तीची मुदत | |
| `tentative` | tentative | संभाव्य | |
| `applicationFee` | Application fee | अर्ज फी | |
| `feeGeneral` | General / OBC / EWS | जनरल / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | फी माफ (₹0) | |
| `exempt_female` | Women | महिला | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | माजी सैनिक | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | दुरुस्ती: पहिल्यांदा ₹{a}, दुसऱ्यांदा ₹{b} (सर्व उमेदवारांसाठी) | |
| `ageLimit` | Age limit (as on {date}) | वयोमर्यादा ({date} रोजी) | |
| `ageBand` | {band} years: born {from} to {to} | {band} वर्षे: जन्म {from} ते {to} | |
| `relaxation` | Upper age relaxation | कमाल वयोमर्यादेत सूट | |
| `relaxYears` | {n} years | {n} वर्षे | |
| `notComputed` | Not calculated here: {list} | इथे मोजलेले नाही: {list} | |
| `vacancies` | Vacancies | रिक्त जागा | |
| `vacancyCount` | About {n} ({tentative}) | सुमारे {n} ({tentative}) | |
| `posts` | Posts | पदे | |
| `importantLinks` | Important links | महत्त्वाच्या लिंक्स | |
| `applyOnline` | Apply online (ssc.gov.in) | ऑनलाइन अर्ज करा (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | अधिकृत नोटिस (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} चा बदल | |
| `allNotices` | All notices for this exam | या परीक्षेच्या सर्व नोटिसा | |
| `addCalendar` | Add dates to my calendar | तारखा माझ्या कॅलेंडरमध्ये जोडा | |
| `checkThis` | Check my eligibility | माझी पात्रता तपासा | |
| `status_open` | Applications open till {date} | {date} पर्यंत अर्ज सुरू | |
| `status_upcoming` | Applications open on {date} | {date} पासून अर्ज सुरू होतील | |
| `status_closed` | Applications closed on {date} | {date} रोजी अर्ज बंद झाले | |
| `daysLeft` | {n} days left | {n} दिवस बाकी | |
| `lastDay` | Last day today | आज शेवटचा दिवस | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | या परीक्षेचे पात्रता नियम अजून जोडलेले नाहीत: नोटिस वाचा. | |
| `examLabel` | Exam | परीक्षा | |
| `dob` | Date of birth (as in Class 10 certificate) | जन्मतारीख (10वीच्या प्रमाणपत्रानुसार) | |
| `category` | Category | प्रवर्ग | |
| `catUR` | General (UR) | जनरल (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (नॉन-क्रिमी लेयर) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | सर्वोच्च शिक्षण | |
| `edu10` | Class 10 | 10वी | |
| `edu12` | Class 12 | 12वी | |
| `eduGraduate` | Bachelor's degree or higher | पदवी किंवा त्याहून जास्त | |
| `gender` | Gender | लिंग | |
| `male` | Male | पुरुष | |
| `female` | Female | स्त्री | |
| `other` | Other | इतर | |
| `appearing` | Final exam of that level taken, result not out yet | त्या स्तराची अंतिम परीक्षा दिली, रिझल्ट अजून आलेला नाही | |
| `pwbd` | Person with benchmark disability (PwBD) | बेंचमार्क दिव्यांग व्यक्ती (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | माजी सैनिक, केंद्र सरकारी कर्मचारी, किंवा विधवा/घटस्फोटित महिला | |
| `check` | Check | तपासा | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | तुमची उत्तरे याच डिव्हाइसवर राहतात. काहीही कुठेही पाठवले जात नाही. | |
| `enterDob` | Enter your date of birth. | तुमची जन्मतारीख टाका. | |
| `closedNote` | Shown so you can prepare for the next cycle. | पुढच्या भरतीची तयारी करता यावी म्हणून दाखवले आहे. | |
| `canApply` | {n} can apply | {n} साठी अर्ज करू शकता | |
| `toCheck` | {n} to check | {n} तपासायच्या आहेत | |
| `notEligible` | {n} not eligible | {n} साठी पात्र नाही | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | फी: {fee}. लागू केलेली वयातील सूट: {n} वर्षे. वय {date} रोजीचे धरले जाते. | |
| `feeNone` | none (exempt) | नाही (फी माफ) | |
| `st_eligible` | You can apply | तुम्ही अर्ज करू शकता | |
| `st_check` | Check one condition | एक अट तपासा | |
| `st_ineligible` | Not eligible | पात्र नाही | |
| `payLevel` | Pay Level {l} · Group {g} | पे लेव्हल {l} · ग्रुप {g} | |
| `groupOnly` | Group {g} | ग्रुप {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | जन्म {from} ते {to} दरम्यान (तुमच्यासाठी) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date} च्या नोटिसमधून घेतलेले नियम (तपासले: {checked}). | |
| `sigTitle` | Signature for SSC forms | SSC फॉर्मसाठी सही | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC ला {min}-{max} KB ची JPEG फाइल हवी, साधारण {w} cm × {h} cm. पांढऱ्या कागदावर काळ्या शाईने सही करा आणि स्पष्ट फोटो काढा. | |
| `sigPick` | Choose signature photo | सहीचा फोटो निवडा | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | तयार: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | {min} ते {max} KB मध्ये फाइल बनवता आली नाही. अधिक स्पष्ट फोटो वापरून पहा. | |
| `sigDownload` | Download JPEG | JPEG डाउनलोड करा | |
| `photoTitle` | Your photograph | तुमचा फोटो | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC आता अपलोड केलेला फोटो घेत नाही: अर्ज फॉर्म तुमच्या कॅमेऱ्याने थेट फोटो घेतो, आणि जुन्या फोटोचा फोटो काढल्यास फॉर्म रिजेक्ट होतो. चांगला उजेड, साधी पार्श्वभूमी, कॅमेरा डोळ्यांच्या उंचीवर; टोपी, मास्क किंवा चष्मा नको. | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | स्रोत: नोटिस परिच्छेद 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | वय कॅल्क्युलेटर | |
| `ageOn` | Age on | या तारखेला वय | |
| `ageResult` | {y} years, {m} months, {d} days | {y} वर्षे, {m} महिने, {d} दिवस | |
| `ageInvalid` | Date of birth must be before that date. | जन्मतारीख त्या तारखेच्या आधीची असायला हवी. | |
| `docsTitle` | Certificate formats | प्रमाणपत्रांचे नमुने | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | प्रवर्ग, दिव्यांगत्व आणि माजी सैनिक प्रमाणपत्रांसाठी SSC चे नमुने. | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | अजून काहीही सेव्ह केलेले नाही. कोणत्याही नोटिसवर सेव्ह करा टॅप करा. | |
| `followedExams` | Exams you follow | तुम्ही फॉलो करत असलेल्या परीक्षा | |
| `chooseLanguage` | Choose your language | तुमची भाषा निवडा | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | नोटिसांची शीर्षके भरती संस्था ज्या भाषेत प्रसिद्ध करते त्याच भाषेत राहतात (बहुतेक इंग्रजी किंवा हिंदी). | |
| `qaLatest` | Latest jobs | नवीन भरती | |
| `qaResults` | Results | रिझल्ट | |
| `qaAdmit` | Admit cards | ॲडमिट कार्ड | |
| `qaAnswer` | Answer keys | आन्सर की | |
| `qaEligibility` | Check eligibility | पात्रता तपासा | |
| `qaRoll` | Result by roll no. | रोल नं. ने रिझल्ट | |
| `homeSectors` | Jobs by sector | क्षेत्रानुसार नोकऱ्या | |
| `homeStates` | Jobs by state | राज्यानुसार नोकऱ्या | |
| `homeClosing` | Closing soon | मुदत लवकरच संपणार | |
| `homeChannels` | Get every update on WhatsApp or Telegram | प्रत्येक अपडेट WhatsApp किंवा Telegram वर मिळवा | |
| `homeChannelsNote` | Free. One message per update, with the official link. | मोफत. प्रत्येक अपडेटसाठी एक मेसेज, अधिकृत लिंकसह. | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | अनधिकृत · प्रत्येक अपडेटसोबत अधिकृत वेबसाइटची लिंक | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | पोलीस | |
| `sector_defence` | Defence & CAPF | संरक्षण व CAPF | |
| `sector_railway` | Railway | रेल्वे | |
| `sector_bank` | Bank | बँक | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | विज्ञान व अंतराळ | |
| `sector_state` | State govt | राज्य सरकार | |
| `sector_teaching` | Teaching | शिक्षक | |
| `state_all` | All India | संपूर्ण भारत | |
| `state_UP` | Uttar Pradesh | उत्तर प्रदेश | |
| `state_BR` | Bihar | बिहार | |
| `state_HR` | Haryana | हरियाणा | |
| `anySector` | All sectors | सर्व क्षेत्रे | |
| `anyState` | All states | सर्व राज्ये | |
| `jobsSector` | {name} jobs | {name} नोकऱ्या | |
| `jobsState` | Government jobs in {name} | {name} मधील सरकारी नोकऱ्या | |
| `jobsKind` | {name}: latest | {name}: ताजे | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | या क्षेत्रातील अजून काहीही नाही. भरती संस्थेची अधिकृत वेबसाइट तपासल्यानंतरच आम्ही ती जोडतो, त्यामुळे ही यादी वाढत जाईल. | |
| `centralJobs` | Central govt (all India) | केंद्र सरकार (संपूर्ण भारत) | |
| `recruitersTitle` | Recruiters we follow | आम्ही फॉलो करत असलेल्या भरती संस्था | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | अपडेट्स या अधिकृत वेबसाइट्सवरून घेतले जातात. उघडण्यासाठी टॅप करा. | |
| `moreUpdates` | See all updates | सर्व अपडेट्स पहा | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | वय कमी आहे: {date} रोजी तुमचे वय किमान {n} असायला हवे. | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | वयोमर्यादा ओलांडली: {date} रोजी तुमचे वय {n} पेक्षा कमी असायला हवे (सूट धरून). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | सामान्य वयोमर्यादा ओलांडली. माजी सैनिक / सरकारी कर्मचारी / विधवा यांना मिळणारी सूट लागू होऊ शकते: नोटिस {ref} पहा. | |
| `r_education_graduate` | Needs a bachelor's degree. | पदवी आवश्यक. | |
| `r_education_12` | Needs Class 12 pass. | 12वी उत्तीर्ण आवश्यक. | |
| `r_education_10` | Needs Class 10 pass. | 10वी उत्तीर्ण आवश्यक. | |
| `r_result_needed_by` | Your result must be out by {date}. | तुमचा रिझल्ट {date} पर्यंत लागलेला असायला हवा. | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | रिझल्टची वाट पाहणारे उमेदवार अर्ज करू शकतात असे नोटिसमध्ये म्हटलेले नाही; {date} पर्यंत तुमच्याकडे पात्रता असणे आवश्यक आहे. | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | या नोटिसमध्ये PwBD साठी वयात सूट दिलेली नाही: नोटिस तपासा. | |
| `r_gender_male` | Only for male candidates. | फक्त पुरुष उमेदवारांसाठी. | |
| `r_gender_female` | Only for female candidates. | फक्त महिला उमेदवारांसाठी. | |
| `r_gender_other` | Only for {g} candidates. | फक्त {g} उमेदवारांसाठी. | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | माध्यमिक शाळा स्तरावर राज्याची अधिकृत/प्रादेशिक भाषा आवश्यक. | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12वीत गणितात 60% गुण, किंवा पदवीत सांख्यिकी (Statistics) हा विषय आवश्यक. | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | पदवी सांख्यिकी, गणित, अर्थशास्त्र, लोकसंख्याशास्त्र (Demography), पॉप्युलेशन स्टडीज, ऑपरेशन्स रिसर्च, IT, कॉम्प्युटर सायन्स/इंजिनिअरिंग/ॲप्लिकेशन्स, डेटा सायन्स किंवा AI यापैकी एका विषयात असावी. | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | शारीरिक चाचणीच्या दिवशी वैध LMV (मोटारसायकल आणि कार) ड्रायव्हिंग लायसन्स आवश्यक. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | भौतिकशास्त्र, रसायनशास्त्र आणि गणितासह B.Sc., किंवा 10वी अधिक मेकॅनिकल, सिव्हिल, इलेक्ट्रिकल किंवा यादीतील इंजिनिअरिंग शाखेतील 3 वर्षांचा डिप्लोमा आवश्यक. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | शारीरिक व वैद्यकीय निकष लागू (नोटिस पहा). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | बॉर्डर रोड्स ऑर्गनायझेशनच्या जागा: फक्त पुरुष, शारीरिक निकष लागू. | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | काही कार्यालयांत (ग्राहक व्यवहार व अन्न, SSC, संस्कृती) या पदासाठी गणितासह 12वी विज्ञान आवश्यक. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | निवड प्रक्रियेत स्टेनोग्राफी स्किल टेस्ट असते. | |
| `listWord` | Lists: {n} | याद्या: {n} | |
