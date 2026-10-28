# हिन्दी (hi) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/hi.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/hi.js)

| 키 | English | हिन्दी | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | सरकारी जॉब अलर्ट | |
| `tabHome` | Home | होम | |
| `tabMore` | More | और | |
| `loading` | Loading… | लोड हो रहा है… | |
| `homeComing` | Exam dates in the next 30 days | अगले 30 दिनों की परीक्षाएँ | |
| `homeLatest` | Latest updates | ताज़ा अपडेट | |
| `inDays` | in {n} days | {n} दिन में | |
| `onNow` | on now | अभी चालू | |
| `today` | today | आज | |
| `theme` | Theme | थीम | |
| `themeAuto` | Auto | ऑटो | |
| `themeLight` | Light | लाइट | |
| `themeDark` | Dark | डार्क | |
| `language` | Language | भाषा | |
| `howToApply` | How to apply | आवेदन कैसे करें | |
| `documents` | Keep these documents ready | ये डॉक्यूमेंट तैयार रखें | |
| `helplineTitle` | SSC helplines | SSC हेल्पलाइन | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | फ़ॉर्म भरने में या कंप्यूटर आधारित परीक्षा (CBT) में कोई दिक्कत हो तो। | |
| `typingTitle` | Typing / data entry speed practice | टाइपिंग / डेटा एंट्री स्पीड प्रैक्टिस | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | नीचे दिया गया पैराग्राफ़ टाइप करें। पहला बटन दबाते ही टाइमर शुरू हो जाएगा। स्पीड की-डिप्रेशन प्रति घंटा में दिखती है, जैसे SSC बताता है। | |
| `typingTest` | Standard | स्टैंडर्ड | |
| `typingMinutes` | Time | समय | |
| `typingFull` | Full ({n} min) | पूरा ({n} मिनट) | |
| `typingStart` | Start again | फिर से शुरू करें | |
| `typingDone` | Time is up. | समय पूरा हुआ। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | {kdph} की-डिप्रेशन प्रति घंटा (≈ {wpm} w.p.m.), {accuracy}% एक्यूरेसी, {typed} में से {correct} सही | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} की स्पीड ({kdph}/घंटा) पूरी है। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} की स्पीड ({kdph}/घंटा) से कम है: प्रैक्टिस जारी रखें। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | सिर्फ़ प्रैक्टिस के लिए: SSC गलतियाँ अपनी मूल्यांकन गाइडलाइन के हिसाब से गिनता है। हिंदी टाइपिंग की प्रैक्टिस यहाँ नहीं है। | |
| `faqTitle` | Frequently asked | अक्सर पूछे जाने वाले सवाल | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} के लिए आवेदन की आख़िरी तारीख क्या है? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | आवेदन की आख़िरी तारीख {date} है; फ़ीस {fee} तक जमा कर सकते हैं। | |
| `faqAge` | What is the age limit for {exam}? | {exam} की आयु सीमा क्या है? | |
| `faqFee` | What is the application fee? | आवेदन फ़ीस कितनी है? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | सामान्य, OBC और EWS उम्मीदवारों के लिए ₹{amount}। {exempt} को कोई फ़ीस नहीं देनी है। | |
| `faqSalary` | What is the salary? | सैलरी कितनी है? | |
| `faqExam` | When is the {exam} exam? | {exam} की परीक्षा कब है? | |
| `faqVacancy` | How many vacancies are there? | कितनी वैकेंसी हैं? | |
| `faqQual` | What qualification is needed? | कौन सी योग्यता चाहिए? | |
| `aboutTitle` | About this app | इस ऐप के बारे में | |
| `privacyPolicy` | Privacy policy | गोपनीयता नीति | |
| `termsOfUse` | Terms of use | नियम और शर्तें | |
| `contactUs` | Contact us | संपर्क करें | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | भारत में सरकारी नौकरी की तैयारी करने वालों के लिए एक फ़्री, अनौपचारिक (unofficial) मददगार ऐप। नोटिस, तारीखें और नियम भर्ती संस्थाओं की आधिकारिक वेबसाइट और नोटिस से, सोर्स के साथ लिए गए हैं। कोई साइन-अप नहीं, कोई निजी जानकारी इकट्ठा नहीं की जाती: आपकी डिटेल्स और रोल नंबर इसी फ़ोन में रहते हैं। | |
| `sourceRef` | Source: {ref} | सोर्स: {ref} | |
| `toastSaved` | Saved | सेव हो गया | |
| `toastRemoved` | Removed | हटा दिया | |
| `toastFollow` | You follow this exam | आप इस परीक्षा को फ़ॉलो कर रहे हैं | |
| `toastUnfollow` | Unfollowed | अनफ़ॉलो कर दिया | |
| `settings` | Settings | सेटिंग्स | |
| `toolsTitle` | Tools | टूल्स | |
| `tabResult` | Result | रिजल्ट | |
| `tabForMe` | For me | मेरे लिए | |
| `resultTitle` | Check your result by roll number | रोल नंबर से अपना रिजल्ट देखें | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | पिछले 120 दिनों में SSC की जारी रिजल्ट लिस्ट में खोजता है, बड़ी PDF डाउनलोड किए बिना। एक छोटी फ़ाइल लाने के लिए सिर्फ़ आपके रोल नंबर के आख़िरी दो अंक इस्तेमाल होते हैं। | |
| `rollNo` | Roll number | रोल नंबर | |
| `resultExam` | Result | रिजल्ट | |
| `find` | Find | खोजें | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | एडमिट कार्ड पर छपा रोल नंबर डालें (8-14 अंक)। | |
| `foundIn` | Found in: {list} | इसमें मिला: {list} | |
| `notIn` | Not in: {list} | इसमें नहीं: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | आपका रोल नंबर इन लिस्ट में नहीं है। सिर्फ़ इससे यह मतलब नहीं कि आप क्वालिफ़ाई नहीं हुए: नीचे आधिकारिक PDF ज़रूर देखें। | |
| `officialFiles` | Official files of this notice | इस नोटिस की आधिकारिक फ़ाइलें | |
| `listCount` | {n} roll numbers | {n} रोल नंबर | |
| `noLists` | No result lists to search right now. | अभी खोजने के लिए कोई रिजल्ट लिस्ट नहीं है। | |
| `forMeTitle` | Exams that suit you | आपके लिए सही परीक्षाएँ | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | अपनी डिटेल्स एक बार भरें। आपको वे सभी SSC परीक्षाएँ दिखेंगी जिनमें आप आवेदन कर सकते हैं, उनका स्टेटस और आगे क्या होगा। | |
| `forMeEdit` | Edit my details | मेरी डिटेल्स बदलें | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | आपकी योग्यता के हिसाब से (नियम अभी जोड़े नहीं गए: नोटिस पढ़ें) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | आपकी योग्यता के लिए आने वाली परीक्षाएँ (संभावित) | |
| `saveDetails` | Show my exams | मेरी परीक्षाएँ दिखाएँ | |
| `whenTitle` | What has happened and what is next | अब तक क्या हुआ, आगे क्या है | |
| `st_done` | Done | हो गया | |
| `st_now` | Now | अभी | |
| `st_next` | Next | अगला | |
| `st_later` | Later | बाद में | |
| `stage_notice` | Notice published | नोटिफिकेशन जारी | |
| `stage_apply` | Online application | ऑनलाइन आवेदन | |
| `stage_admit_card` | Admit card / exam city | एडमिट कार्ड / परीक्षा शहर | |
| `stage_answer_key` | Answer key | आंसर की | |
| `stage_result` | Result | रिजल्ट | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ने अभी तारीख घोषित नहीं की है | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | बेसिक पे ₹{from}–{to} प्रति माह (लेवल {l}; भत्ते अलग से) | |
| `savedLink` | ★ Saved | ★ सेव किए गए | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | यह सरकारी वेबसाइट नहीं है और किसी भर्ती संस्था या सरकार से जुड़ी नहीं है। यहाँ सब कुछ भर्ती संस्थाओं की आधिकारिक वेबसाइट से लिया गया है, और हर अपडेट के साथ आधिकारिक फ़ाइल या पेज का लिंक है। आवेदन करने से पहले आधिकारिक नोटिस ज़रूर देखें। | |
| `tabExams` | Exams | परीक्षाएँ | |
| `filters` | Filters | फ़िल्टर | |
| `kindAll` | All | सभी | |
| `kindRecruitment` | New jobs | नई भर्तियाँ | |
| `kindResult` | Results | रिजल्ट | |
| `kindAdmit` | Admit cards | एडमिट कार्ड | |
| `kindAnswer` | Answer keys | आंसर की | |
| `kindAmendment` | Changes | बदलाव | |
| `kindVacancy` | Vacancies | वैकेंसी | |
| `kind_recruitment` | New job | नई भर्ती | |
| `kind_amendment` | Change | बदलाव | |
| `kind_result` | Result | रिजल्ट | |
| `kind_answer_key` | Answer key | आंसर की | |
| `kind_admit_card` | Admit card | एडमिट कार्ड | |
| `kind_vacancy` | Vacancy | वैकेंसी | |
| `kind_schedule` | Exam dates | परीक्षा की तारीखें | |
| `kind_notice` | Notice | नोटिस | |
| `search` | Search jobs, exams, results | नौकरी, परीक्षा, रिजल्ट खोजें | |
| `allExams` | All exams | सभी परीक्षाएँ | |
| `myExams` | My exams only | सिर्फ़ मेरी परीक्षाएँ | |
| `anyQualification` | Any qualification | कोई भी योग्यता | |
| `qual_10` | Class 10 | 10वीं | |
| `qual_12` | Class 12 | 12वीं | |
| `qual_diploma` | Diploma | डिप्लोमा | |
| `qual_graduate` | Bachelor's degree | ग्रेजुएशन | |
| `qual_postgraduate` | Master's degree | पोस्ट ग्रेजुएशन | |
| `departmental` | Departmental exams | विभागीय परीक्षाएँ | |
| `updated` | Updated {time} IST | अपडेट: {time} IST | |
| `newSince` | {n} new since your last visit | आपकी पिछली विज़िट के बाद {n} नए | |
| `cutoffTitle` | Cut-off marks | कट-ऑफ़ मार्क्स | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC कट-ऑफ़ सिर्फ़ रिजल्ट नोटिस के अंदर देता है। नंबर यहाँ कॉपी नहीं किए गए (एक भी गलत नंबर गुमराह कर सकता है): हर लिंक SSC की PDF को उसी पेज पर खोलता है जहाँ से कट-ऑफ़ की जानकारी शुरू होती है। | |
| `cutoffFrom` | Open at page {p} | पेज {p} पर खोलें | |
| `cutoffPages` | mentioned on p. {pages} | पेज {pages} पर दिया है | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | पिछले 120 दिनों के रिजल्ट नोटिस में कोई कट-ऑफ़ नहीं मिला। | |
| `alertsTitle` | Alerts on this phone | इस फ़ोन पर अलर्ट | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | जिस परीक्षा को आप फ़ॉलो करते हैं, उसकी कोई नई खबर आने पर और आवेदन बंद होने से दो दिन पहले नोटिफिकेशन मिलेगा। | |
| `alertsOn` | Turn on alerts | अलर्ट चालू करें | |
| `alertsOff` | Turn off alerts | अलर्ट बंद करें | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | पहले कोई परीक्षा फ़ॉलो करें (परीक्षाएँ → फ़ॉलो करें)। | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} परीक्षाओं के लिए चालू। कब चेक करना है, यह Chrome खुद तय करता है (ऐप ज़्यादा खोलेंगे तो ज़्यादा बार), इसलिए खबर पोस्ट होने के कुछ घंटे बाद भी पहुँच सकती है। | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | इस साइट के नोटिफिकेशन ब्लॉक हैं। ब्राउज़र की साइट सेटिंग्स में जाकर इन्हें अनुमति दें। | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | यह ब्राउज़र बैकग्राउंड में चेक नहीं कर सकता। Android पर पहले Chrome से यह ऐप इंस्टॉल करें। नहीं तो हर परीक्षा के पेज पर दिए कैलेंडर रिमाइंडर इस्तेमाल करें। | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | आपकी परीक्षाओं के {n} और अपडेट | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: {n} दिन में आवेदन बंद होंगे | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: आज आवेदन का आख़िरी दिन है | |
| `installTitle` | Install the app | ऐप इंस्टॉल करें | |
| `installBtn` | Install | इंस्टॉल करें | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ऐप की तरह खुलता है, बिना इंटरनेट भी चलता है और Android पर अलर्ट देता है। | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone पर: Share दबाएँ, फिर "Add to Home Screen" चुनें। | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | फ़ॉलो कर लिया। फ़ोन पर अलर्ट के लिए: और → अलर्ट चालू करें | |
| `rollPlaceholder` | Roll number on your admit card | एडमिट कार्ड पर लिखा रोल नंबर | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} परीक्षाओं के लिए चालू। SSC के नोटिस आम तौर पर पोस्ट होने के एक घंटे के अंदर आप तक पहुँच जाते हैं; रिजल्ट और दूसरे बोर्ड के नोटिस में 3 घंटे तक लग सकते हैं। | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | यह ब्राउज़र अलर्ट नहीं दिखा सकता। iPhone पर पहले इस ऐप को होम स्क्रीन पर जोड़ें (Share → Add to Home Screen), फिर वहीं से खोलें। | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | अलर्ट चालू नहीं हो पाए। इंटरनेट कनेक्शन चेक करके फिर कोशिश करें। | |
| `allBoards` | All recruiters | सभी भर्ती संस्थाएँ | |
| `newBadge` | NEW | नया | |
| `nothing` | Nothing here in the last 120 days. | पिछले 120 दिनों में यहाँ कुछ नहीं है। | |
| `loadError` | Couldn't load. Check your internet connection and try again. | लोड नहीं हो पाया। इंटरनेट कनेक्शन चेक करके फिर कोशिश करें। | |
| `offline` | Offline: showing the copy saved on this phone. | ऑफ़लाइन: इस फ़ोन में सेव की गई कॉपी दिखा रहे हैं। | |
| `save` | Save | सेव करें | |
| `saved` | Saved | सेव किया | |
| `share` | Share | शेयर करें | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | आधिकारिक पेज | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ आधिकारिक लिंक: {url} ⏎ {app} से (सरकारी साइट नहीं) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | हर परीक्षा का लेटेस्ट नोटिफिकेशन, तारीखें, फ़ीस, आयु सीमा और तब से आए सभी नोटिस। | |
| `follow` | Follow | फ़ॉलो करें | |
| `following` | Following | फ़ॉलो कर रहे हैं | |
| `minQual` | Qualification: {q} | योग्यता: {q} | |
| `qualUnknown` | Qualification: see the Notice | योग्यता: नोटिफिकेशन देखें | |
| `calendarTitle` | SSC's tentative calendar | SSC का संभावित कैलेंडर | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | यह सिर्फ़ प्लान है, नोटिफिकेशन नहीं: असली तारीखें हर नोटिफिकेशन में होती हैं और अलग हो सकती हैं। | |
| `resourcesTitle` | Official resources | आधिकारिक सामग्री | |
| `res_syllabus` | Syllabus | सिलेबस | |
| `res_vacancy` | Vacancies (tentative / final) | वैकेंसी (संभावित / फ़ाइनल) | |
| `res_previous_papers` | Previous year papers | पिछले साल के पेपर | |
| `res_certificate_formats` | Certificate formats | सर्टिफ़िकेट फ़ॉर्मेट | |
| `res_instructions` | Instructions | निर्देश | |
| `back` | ← All exams | ← सभी परीक्षाएँ | |
| `latestNotice` | Latest Notice | लेटेस्ट नोटिफिकेशन | |
| `importantDates` | Important dates | ज़रूरी तारीखें | |
| `applyWindow` | Apply online | ऑनलाइन आवेदन | |
| `feeLastDate` | Fee payment last date | फ़ीस जमा करने की आख़िरी तारीख | |
| `correctionWindow` | Form correction window | फ़ॉर्म करेक्शन विंडो | |
| `tentative` | tentative | संभावित | |
| `applicationFee` | Application fee | आवेदन फ़ीस | |
| `feeGeneral` | General / OBC / EWS | सामान्य / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | छूट (₹0) | |
| `exempt_female` | Women | महिलाएँ | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD (दिव्यांग) | |
| `exempt_ESM` | Ex-servicemen | भूतपूर्व सैनिक | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | करेक्शन: पहली बार ₹{a}, दूसरी बार ₹{b} (सभी उम्मीदवार) | |
| `ageLimit` | Age limit (as on {date}) | आयु सीमा ({date} को) | |
| `ageBand` | {band} years: born {from} to {to} | {band} साल: जन्म {from} से {to} के बीच | |
| `relaxation` | Upper age relaxation | ऊपरी आयु सीमा में छूट | |
| `relaxYears` | {n} years | {n} साल | |
| `notComputed` | Not calculated here: {list} | यहाँ कैलकुलेट नहीं किया गया: {list} | |
| `vacancies` | Vacancies | वैकेंसी | |
| `vacancyCount` | About {n} ({tentative}) | लगभग {n} ({tentative}) | |
| `posts` | Posts | पद | |
| `importantLinks` | Important links | ज़रूरी लिंक | |
| `applyOnline` | Apply online (ssc.gov.in) | ऑनलाइन आवेदन करें (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | आधिकारिक नोटिफिकेशन (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} का बदलाव | |
| `allNotices` | All notices for this exam | इस परीक्षा के सभी नोटिस | |
| `addCalendar` | Add dates to my calendar | तारीखें मेरे कैलेंडर में जोड़ें | |
| `checkThis` | Check my eligibility | मेरी योग्यता चेक करें | |
| `status_open` | Applications open till {date} | {date} तक आवेदन चालू | |
| `status_upcoming` | Applications open on {date} | {date} से आवेदन शुरू | |
| `status_closed` | Applications closed on {date} | {date} को आवेदन बंद हो गए | |
| `daysLeft` | {n} days left | {n} दिन बाकी | |
| `lastDay` | Last day today | आज आख़िरी दिन | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | इस परीक्षा के योग्यता नियम अभी नहीं जुड़े हैं: नोटिफिकेशन पढ़ें। | |
| `examLabel` | Exam | परीक्षा | |
| `dob` | Date of birth (as in Class 10 certificate) | जन्म तिथि (10वीं के सर्टिफ़िकेट के अनुसार) | |
| `category` | Category | कैटेगरी | |
| `catUR` | General (UR) | सामान्य (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (नॉन-क्रीमी लेयर) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | सबसे ऊँची योग्यता | |
| `edu10` | Class 10 | 10वीं | |
| `edu12` | Class 12 | 12वीं | |
| `eduGraduate` | Bachelor's degree or higher | ग्रेजुएशन या उससे ऊपर | |
| `gender` | Gender | लिंग | |
| `male` | Male | पुरुष | |
| `female` | Female | महिला | |
| `other` | Other | अन्य | |
| `appearing` | Final exam of that level taken, result not out yet | उस लेवल की फ़ाइनल परीक्षा दे दी है, रिजल्ट अभी नहीं आया | |
| `pwbd` | Person with benchmark disability (PwBD) | बेंचमार्क दिव्यांगता वाले व्यक्ति (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | भूतपूर्व सैनिक, केंद्र सरकार के कर्मचारी, या विधवा/तलाकशुदा महिला | |
| `check` | Check | चेक करें | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | आपके जवाब इसी डिवाइस में रहते हैं। कुछ भी कहीं नहीं भेजा जाता। | |
| `enterDob` | Enter your date of birth. | अपनी जन्म तिथि डालें। | |
| `closedNote` | Shown so you can prepare for the next cycle. | अगली भर्ती की तैयारी के लिए दिखाया गया है। | |
| `canApply` | {n} can apply | {n} में आवेदन कर सकते हैं | |
| `toCheck` | {n} to check | {n} में चेक करना है | |
| `notEligible` | {n} not eligible | {n} में योग्य नहीं | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | फ़ीस: {fee}। आयु में छूट: {n} साल। आयु {date} के हिसाब से गिनी गई है। | |
| `feeNone` | none (exempt) | कोई नहीं (छूट) | |
| `st_eligible` | You can apply | आवेदन कर सकते हैं | |
| `st_check` | Check one condition | एक शर्त चेक करें | |
| `st_ineligible` | Not eligible | योग्य नहीं | |
| `payLevel` | Pay Level {l} · Group {g} | पे लेवल {l} · ग्रुप {g} | |
| `groupOnly` | Group {g} | ग्रुप {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | जन्म {from} और {to} के बीच (आपके लिए) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | नियम {date} के नोटिफिकेशन से लिए गए ({checked} को चेक किए)। | |
| `sigTitle` | Signature for SSC forms | SSC फ़ॉर्म के लिए सिग्नेचर | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC को {min}-{max} KB का JPEG चाहिए, लगभग {w} cm × {h} cm। सफ़ेद कागज़ पर काले पेन से साइन करें और साफ़ फ़ोटो लें। | |
| `sigPick` | Choose signature photo | सिग्नेचर की फ़ोटो चुनें | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | तैयार: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | फ़ाइल {min} से {max} KB के बीच नहीं बन पाई। ज़्यादा साफ़ फ़ोटो लेकर देखें। | |
| `sigDownload` | Download JPEG | JPEG डाउनलोड करें | |
| `photoTitle` | Your photograph | आपकी फ़ोटो | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC अब अपलोड की हुई फ़ोटो नहीं लेता: आवेदन फ़ॉर्म आपके कैमरे से लाइव फ़ोटो लेता है, और पुरानी फ़ोटो की फ़ोटो खींचने पर फ़ॉर्म रिजेक्ट हो जाता है। अच्छी रोशनी, सादा बैकग्राउंड और आँखों की ऊँचाई पर कैमरा रखें; टोपी, मास्क या चश्मा न पहनें। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | सोर्स: नोटिस पैरा 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | आयु कैलकुलेटर | |
| `ageOn` | Age on | इस तारीख को आयु | |
| `ageResult` | {y} years, {m} months, {d} days | {y} साल, {m} महीने, {d} दिन | |
| `ageInvalid` | Date of birth must be before that date. | जन्म तिथि उस तारीख से पहले की होनी चाहिए। | |
| `docsTitle` | Certificate formats | सर्टिफ़िकेट फ़ॉर्मेट | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | कैटेगरी, दिव्यांगता और भूतपूर्व सैनिक सर्टिफ़िकेट के लिए SSC के फ़ॉर्मेट। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | अभी कुछ सेव नहीं किया है। किसी भी नोटिस पर "सेव करें" दबाएँ। | |
| `followedExams` | Exams you follow | आपकी फ़ॉलो की गई परीक्षाएँ | |
| `chooseLanguage` | Choose your language | अपनी भाषा चुनें | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | नोटिस के टाइटल उसी भाषा में रहते हैं जिसमें भर्ती संस्था उन्हें जारी करती है (ज़्यादातर अंग्रेज़ी या हिंदी)। | |
| `qaLatest` | Latest jobs | लेटेस्ट जॉब | |
| `qaResults` | Results | रिजल्ट | |
| `qaAdmit` | Admit cards | एडमिट कार्ड | |
| `qaAnswer` | Answer keys | आंसर की | |
| `qaEligibility` | Check eligibility | योग्यता चेक | |
| `qaRoll` | Result by roll no. | रोल नंबर से रिजल्ट | |
| `homeSectors` | Jobs by sector | सेक्टर के हिसाब से नौकरियाँ | |
| `homeStates` | Jobs by state | राज्य के हिसाब से नौकरियाँ | |
| `homeClosing` | Closing soon | जल्द बंद होने वाले | |
| `homeChannels` | Get every update on WhatsApp or Telegram | हर अपडेट WhatsApp या Telegram पर पाएँ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | फ़्री। हर अपडेट पर एक मैसेज, आधिकारिक लिंक के साथ। | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | अनौपचारिक · हर अपडेट के साथ आधिकारिक वेबसाइट का लिंक | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | पुलिस | |
| `sector_defence` | Defence & CAPF | डिफ़ेंस और CAPF | |
| `sector_railway` | Railway | रेलवे | |
| `sector_bank` | Bank | बैंक | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | साइंस और स्पेस | |
| `sector_state` | State govt | राज्य सरकार | |
| `sector_teaching` | Teaching | टीचिंग | |
| `state_all` | All India | ऑल इंडिया | |
| `state_UP` | Uttar Pradesh | उत्तर प्रदेश | |
| `state_BR` | Bihar | बिहार | |
| `state_HR` | Haryana | हरियाणा | |
| `anySector` | All sectors | सभी सेक्टर | |
| `anyState` | All states | सभी राज्य | |
| `jobsSector` | {name} jobs | {name} की नौकरियाँ | |
| `jobsState` | Government jobs in {name} | {name} में सरकारी नौकरियाँ | |
| `jobsKind` | {name}: latest | {name}: लेटेस्ट | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | इस सेक्टर से अभी कुछ नहीं है। हम किसी भर्ती संस्था को उसकी आधिकारिक वेबसाइट जाँचने के बाद ही जोड़ते हैं, इसलिए यह लिस्ट बढ़ती जाएगी। | |
| `centralJobs` | Central govt (all India) | केंद्र सरकार (ऑल इंडिया) | |
| `recruitersTitle` | Recruiters we follow | हम इन भर्ती संस्थाओं को फ़ॉलो करते हैं | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | अपडेट इन्हीं आधिकारिक वेबसाइटों से लिए जाते हैं। खोलने के लिए किसी पर टैप करें। | |
| `moreUpdates` | See all updates | सभी अपडेट देखें | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | उम्र कम है: {date} को आपकी उम्र कम से कम {n} साल होनी चाहिए। | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | उम्र सीमा से ज़्यादा: {date} को आपकी उम्र {n} साल से कम होनी चाहिए (छूट जोड़कर)। | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | सामान्य आयु सीमा से ज़्यादा। भूतपूर्व सैनिक / सरकारी कर्मचारी / विधवा वाली आयु छूट लागू हो सकती है: नोटिस {ref} देखें। | |
| `r_education_graduate` | Needs a bachelor's degree. | ग्रेजुएशन की डिग्री ज़रूरी है। | |
| `r_education_12` | Needs Class 12 pass. | 12वीं पास होना ज़रूरी है। | |
| `r_education_10` | Needs Class 10 pass. | 10वीं पास होना ज़रूरी है। | |
| `r_result_needed_by` | Your result must be out by {date}. | आपका रिजल्ट {date} तक आ जाना चाहिए। | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | नोटिस में यह नहीं लिखा कि रिजल्ट का इंतज़ार कर रहे उम्मीदवार आवेदन कर सकते हैं; {date} तक आपके पास योग्यता होनी चाहिए। | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | इस नोटिस में PwBD के लिए आयु में छूट नहीं दी गई है: नोटिस देखें। | |
| `r_gender_male` | Only for male candidates. | सिर्फ़ पुरुष उम्मीदवारों के लिए। | |
| `r_gender_female` | Only for female candidates. | सिर्फ़ महिला उम्मीदवारों के लिए। | |
| `r_gender_other` | Only for {g} candidates. | सिर्फ़ {g} उम्मीदवारों के लिए। | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | राज्य की आधिकारिक/क्षेत्रीय भाषा सेकेंडरी स्कूल (10वीं) लेवल तक पढ़ी होनी चाहिए। | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12वीं में गणित में 60% अंक, या डिग्री में सांख्यिकी (Statistics) विषय होना ज़रूरी है। | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | डिग्री इनमें से किसी विषय में होनी चाहिए: सांख्यिकी, गणित, अर्थशास्त्र, जनसांख्यिकी (Demography), जनसंख्या अध्ययन, ऑपरेशंस रिसर्च, IT, कंप्यूटर साइंस/इंजीनियरिंग/एप्लीकेशन, डेटा साइंस या AI। | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | फ़िज़िकल टेस्ट के दिन वैध LMV (मोटरसाइकिल और कार) ड्राइविंग लाइसेंस होना ज़रूरी है। | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | फ़िज़िक्स, केमिस्ट्री और गणित के साथ B.Sc., या 10वीं के साथ मैकेनिकल, सिविल, इलेक्ट्रिकल या किसी लिस्टेड इंजीनियरिंग ब्रांच में 3 साल का डिप्लोमा ज़रूरी है। | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | शारीरिक और मेडिकल मानक लागू हैं (नोटिस देखें)। | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | सीमा सड़क संगठन (BRO) की वैकेंसी: सिर्फ़ पुरुष, शारीरिक मानक लागू। | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | कुछ दफ़्तरों (उपभोक्ता मामले एवं खाद्य, SSC, संस्कृति) में इस पद के लिए 12वीं साइंस गणित के साथ ज़रूरी है। | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | चयन में स्टेनोग्राफ़ी स्किल टेस्ट भी होता है। | |
| `listWord` | Lists: {n} | लिस्ट: {n} | |

## 지원 방법·서류 (pipeline/ssc_guide.json, 공고 문단 번호 포함; 다른 언어는 영어로 표시)

| 문단 | English | हिन्दी | OK? |
|---|---|---|---|
| para 9.1-9.2 | Do the One-Time Registration (OTR) on ssc.gov.in or in the mySSC app. An OTR made on the old site ssc.nic.in does not work any more. | ssc.gov.in या mySSC ऐप पर वन-टाइम रजिस्ट्रेशन (OTR) करें। पुरानी साइट ssc.nic.in पर किया गया OTR अब काम नहीं करता। | |
| para 9.3 | Choose Aadhaar-based authentication in the OTR. Forms authenticated with Aadhaar are not rejected over photo or signature problems. | OTR में आधार आधारित प्रमाणीकरण चुनें। आधार से प्रमाणित फ़ॉर्म फ़ोटो या हस्ताक्षर की समस्या के कारण रद्द नहीं होते। | |
| para 9.4-9.5 | Your photo is taken live by the camera inside the form: good light, plain background, camera at eye level, no cap, mask or glasses. Photographing an old photo gets the form rejected. | आपकी फ़ोटो फ़ॉर्म के अंदर कैमरे से ली जाती है: अच्छी रोशनी, सादी पृष्ठभूमि, आँखों के स्तर पर कैमरा; टोपी, मास्क या चश्मा नहीं। पुरानी फ़ोटो की फ़ोटो खींचने पर फ़ॉर्म रद्द हो जाता है। | |
| para 9.6 | Upload your signature as a JPEG of 10-20 KB, about 6 cm wide and 2 cm high. | हस्ताक्षर 10-20 KB के JPEG में अपलोड करें, लगभग 6 सेमी चौड़ा और 2 सेमी ऊँचा। | |
| para 9.7 | Persons with benchmark disabilities who want reservation, a scribe or extra time upload the disability certificate in the form. | आरक्षण, लेखक (स्क्राइब) या अतिरिक्त समय चाहने वाले दिव्यांग उम्मीदवार फ़ॉर्म में दिव्यांगता प्रमाण पत्र अपलोड करें। | |
| para 9.9-9.11 | Check the preview before you submit, and keep a copy of the form. Apply well before the last day: the site gets very slow at the end. | जमा करने से पहले प्रीव्यू जाँचें और फ़ॉर्म की कॉपी रखें। अंतिम दिन से काफ़ी पहले आवेदन करें: आख़िरी दिनों में साइट बहुत धीमी हो जाती है। | |
| para 10.1-10.4 | Pay the ₹100 fee online (BHIM UPI, net banking, or Visa, Mastercard, Maestro, RuPay debit card) unless you are exempt. Without the fee the form stays 'Incomplete'. | ₹100 शुल्क ऑनलाइन भरें (BHIM UPI, नेट बैंकिंग, या Visa, Mastercard, Maestro, RuPay डेबिट कार्ड), यदि आपको छूट नहीं है। शुल्क के बिना फ़ॉर्म 'Incomplete' रहता है। | |
| para 11.4 | Made a mistake? Use the correction window: ₹200 the first time, ₹500 the second time, for every candidate. | कोई गलती हुई? सुधार विंडो का उपयोग करें: पहली बार ₹200, दूसरी बार ₹500, सभी उम्मीदवारों के लिए। | |
| para 5.3 | Class 10 (matriculation) certificate: your date of birth is taken from it and cannot be changed later. | 10वीं का प्रमाण पत्र: जन्म तिथि इसी से ली जाती है और बाद में बदली नहीं जा सकती। | |
| para 6.1 | Category certificate (SC/ST/OBC/EWS/PwBD/ESM) in SSC's own format, shown at document verification. Other formats can be rejected. | वर्ग प्रमाण पत्र (SC/ST/OBC/EWS/PwBD/ESM) SSC के प्रारूप में, दस्तावेज़ सत्यापन के समय। दूसरे प्रारूप रद्द हो सकते हैं। | |
| para 6.4-6.5 | OBC: you must not be in the creamy layer on the closing date. EWS: an Income & Asset certificate valid for the financial year the Notice names. | OBC: अंतिम तिथि पर आप क्रीमी लेयर में नहीं होने चाहिए। EWS: नोटिस में बताए गए वित्तीय वर्ष के लिए मान्य आय एवं संपत्ति प्रमाण पत्र। | |
| para 8.6 | Mark sheets / certificates showing you had the qualification by the cut-off date (a result declared by then counts; one still being processed does not). | अंतिम तिथि तक योग्यता होने के प्रमाण में अंकतालिका/प्रमाण पत्र (तब तक घोषित परिणाम मान्य है; प्रक्रिया में चल रहा नहीं)। | |

## 시험 이름 (pipeline/exam_families.json)

| 코드 | English | हिन्दी | OK? |
|---|---|---|---|
| CGL | Combined Graduate Level (CGL) | संयुक्त स्नातक स्तरीय परीक्षा | |
| CHSL | Combined Higher Secondary Level (CHSL, 10+2) | संयुक्त उच्चतर माध्यमिक स्तर (10+2) परीक्षा | |
| STENO | Stenographer Grade C & D | आशुलिपिक ग्रेड 'ग' और 'घ' परीक्षा | |
| CAPF | Sub-Inspector in Delhi Police & CAPFs | दिल्ली पुलिस और केंद्रीय सशस्त्र पुलिस बल में उप-निरीक्षक | |
| JE | Junior Engineer (Civil, Mechanical, Electrical) | कनिष्ठ अभियंता (सिविल, यांत्रिकी एवं वैद्युत) परीक्षा | |
| JHT | Combined Hindi Translators | संयुक्त हिंदी अनुवादक परीक्षा | |
| MTS | Multi-Tasking Staff & Havaldar | मल्टी टास्किंग (गैर-तकनीकी) स्टाफ और हवलदार | |
| CTGD | Constable (GD) in CAPFs, SSF & Rifleman in Assam Rifles | कांस्टेबल (जीडी) | |
| RHQ | Selection Posts | चयन पद परीक्षा | |
| DP | Delhi Police (Constable, Head Constable) | दिल्ली पुलिस | |
| LDCE | Departmental exams (serving Govt employees only) | विभागीय परीक्षाएं | |
| UPPC | UP Police Constable (UPPRPB) | उत्तर प्रदेश पुलिस आरक्षी भर्ती | |
| UPSI | UP Police Sub-Inspector (UPPRPB) | उत्तर प्रदेश पुलिस उप निरीक्षक भर्ती | |
| UPHG | UP Home Guard enrolment (UPPRPB) | उत्तर प्रदेश होमगार्ड्स एनरोलमेंट | |
| UPP | UP Police, other posts (UPPRPB) | उत्तर प्रदेश पुलिस भर्ती एवं प्रोन्नति बोर्ड | |
| HSSC | Haryana SSC (HSSC): Group C & D, CET, Haryana Police | हरियाणा कर्मचारी चयन आयोग | |
| CSBC | Bihar Police Constable (CSBC) | बिहार पुलिस सिपाही भर्ती | |
| BPSSC | Bihar Police SI & ASI (BPSSC) | बिहार पुलिस अधीनस्थ सेवा आयोग: दारोगा (SI) व ASI भर्ती | |
| ISRO | ISRO: scientists, engineers, technicians, assistants | इसरो भर्ती | |
| ONGC | ONGC: engineers, geologists, consultants, apprentices | ओएनजीसी भर्ती | |
