# 힌디어 검수표 (원어민용)

이 앱이 **직접 쓴** 힌디어 문장 전부입니다. SSC·UP 경찰 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
이 파일은 `node research/hindi_review.mjs`로 코드에서 만들어지므로, 문구를 고치면 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (특히 날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 공고에서 쓰는 말과 맞는가 (예: 수험생이 아는 말이 "रिज़ल्ट"인지 "परिणाम"인지)
3. 일상 사용자가 읽기 쉬운가 (너무 딱딱하거나 직역투가 아닌가)
4. 철자: nukta(ज़/फ़), 표준 철자(हिंदी/हिन्दी 혼용), 숫자·{자리표시자} 유지

## 자체 검토에서 이미 고친 것 (2026-10-20)
- 타자 안내에 "힌디 시험은 Mangal 글꼴 필요"라고 썼으나 2026 CHSL·CGL·Steno 공고에 없는 내용이라 삭제 (영어·힌디 모두)
- "결과"를 탭은 रिज़ल्ट, 배지는 परिणाम으로 섞어 쓰던 것을 रिज़ल्ट로 통일

## 원어민 판단이 필요한 것
- `की-डिप्रेशन` (key depression): SSC 힌디 공고의 표기를 확인하지 못했습니다
- `हिंदी` / `हिन्दी`가 섞여 있습니다 (언어 선택은 हिन्दी). 하나로 통일할지
- 시험 이름의 힌디어(exam_families.json)는 SSC 공식 목록·공고 제목에서 옮겼고, UP 경찰 4개는 위원회 공지 제목의 낱말로 만든 이름입니다

## 1. 화면 문구 (web/i18n.js)

| 키 | English | हिन्दी | OK? |
|---|---|---|---|
| `appName` | SSC Job Alerts | SSC जॉब अलर्ट | |
| `tabHome` | Home | होम | |
| `tabMore` | More | और | |
| `loading` | Loading… | लोड हो रहा है… | |
| `homeOpen` | Applications open now | अभी खुले आवेदन | |
| `homeNoneOpen` | No SSC application is open today. | आज SSC का कोई आवेदन खुला नहीं है। | |
| `homeNextPlan` | SSC plans next: {title}, {from} – {to} (tentative calendar) | SSC की अगली योजना: {title}, {from} – {to} (संभावित कैलेंडर) | |
| `homeComing` | Exam dates in the next 30 days | अगले 30 दिनों की परीक्षा तिथियाँ | |
| `homeNoneComing` | No SSC exam date announced for the next 30 days. | अगले 30 दिनों के लिए SSC ने कोई परीक्षा तिथि घोषित नहीं की है। | |
| `homeLatest` | Latest notices | ताज़ा सूचनाएँ | |
| `daysLeftShort` | {n} days left | {n} दिन बाकी | |
| `inDays` | in {n} days | {n} दिन में | |
| `onNow` | on now | अभी चल रही | |
| `today` | today | आज | |
| `theme` | Theme | थीम | |
| `themeAuto` | Auto | ऑटो | |
| `themeLight` | Light | लाइट | |
| `themeDark` | Dark | डार्क | |
| `language` | Language | भाषा | |
| `howToApply` | How to apply | आवेदन कैसे करें | |
| `documents` | Keep these documents ready | ये दस्तावेज़ तैयार रखें | |
| `helplineTitle` | SSC helplines | SSC हेल्पलाइन | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | फ़ॉर्म भरने या कंप्यूटर आधारित परीक्षा से जुड़ी समस्याओं के लिए। | |
| `typingTitle` | Typing / data entry speed practice | टाइपिंग / डेटा एंट्री गति अभ्यास | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | नीचे दिया गया अनुच्छेद टाइप करें। पहली कुंजी दबाते ही टाइमर शुरू होगा। गति प्रति घंटे की-डिप्रेशन में है, जैसे SSC बताता है। | |
| `typingTest` | Standard | मानक | |
| `typingMinutes` | Time | समय | |
| `typingFull` | Full ({n} min) | पूरा ({n} मिनट) | |
| `typingStart` | Start again | फिर से शुरू करें | |
| `typingDone` | Time is up. | समय समाप्त। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | प्रति घंटे {kdph} की-डिप्रेशन (≈ {wpm} शब्द/मिनट), {accuracy}% सटीक, {typed} में से {correct} सही | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} की गति ({kdph}/घंटा) पूरी। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} की गति ({kdph}/घंटा) से कम: अभ्यास जारी रखें। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | केवल अभ्यास: SSC अपनी मूल्यांकन गाइडलाइन से गलतियाँ गिनता है। हिंदी टाइपिंग अभ्यास यहाँ शामिल नहीं है। | |
| `faqTitle` | Frequently asked | अक्सर पूछे जाने वाले प्रश्न | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} में आवेदन की अंतिम तिथि क्या है? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | आवेदन की अंतिम तिथि {date} है; शुल्क भुगतान {fee} को बंद होगा। | |
| `faqAge` | What is the age limit for {exam}? | {exam} की आयु सीमा क्या है? | |
| `faqFee` | What is the application fee? | आवेदन शुल्क कितना है? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | सामान्य, OBC और EWS के लिए ₹{amount}। {exempt} को शुल्क नहीं देना है। | |
| `faqSalary` | What is the salary? | वेतन कितना है? | |
| `faqExam` | When is the {exam} exam? | {exam} परीक्षा कब है? | |
| `faqVacancy` | How many vacancies are there? | कितनी रिक्तियाँ हैं? | |
| `faqQual` | What qualification is needed? | कौन सी योग्यता चाहिए? | |
| `aboutTitle` | About this app | इस ऐप के बारे में | |
| `aboutText` | An unofficial helper for candidates of SSC exams and UP Police recruitment. Notices, dates and rules are copied from ssc.gov.in, uppbpb.gov.in and the official Notices, with paragraph references. It does not collect personal data: your details and roll number stay on this phone. | SSC परीक्षाओं और UP पुलिस भर्ती के उम्मीदवारों के लिए एक अनौपचारिक सहायक। सूचनाएँ, तिथियाँ और नियम ssc.gov.in, uppbpb.gov.in और आधिकारिक नोटिस से पैरा संदर्भ के साथ लिए गए हैं। यह कोई निजी जानकारी इकट्ठा नहीं करता: आपकी जानकारी और रोल नंबर इसी फ़ोन में रहते हैं। | |
| `sourceRef` | Source: {ref} | स्रोत: {ref} | |
| `toastSaved` | Saved | सहेजा गया | |
| `toastRemoved` | Removed | हटाया गया | |
| `toastFollow` | You follow this exam | आप इस परीक्षा को फ़ॉलो कर रहे हैं | |
| `toastUnfollow` | Unfollowed | फ़ॉलो हटाया | |
| `settings` | Settings | सेटिंग्स | |
| `toolsTitle` | Tools | टूल्स | |
| `tabResult` | Result | रिज़ल्ट | |
| `tabForMe` | For me | मेरे लिए | |
| `resultTitle` | Check your result by roll number | रोल नंबर से अपना रिज़ल्ट देखें | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | पिछले 120 दिनों में SSC की रिज़ल्ट सूचियों में खोजता है, बड़ी PDF डाउनलोड किए बिना। छोटी फ़ाइल लाने के लिए केवल रोल नंबर के आख़िरी दो अंक इस्तेमाल होते हैं। | |
| `rollNo` | Roll number | रोल नंबर | |
| `resultExam` | Result | रिज़ल्ट | |
| `find` | Find | खोजें | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | एडमिट कार्ड पर छपा रोल नंबर भरें (8-14 अंक)। | |
| `foundIn` | Found in: {list} | इसमें मिला: {list} | |
| `notIn` | Not in: {list} | इसमें नहीं: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | आपका रोल नंबर इन सूचियों में नहीं है। सिर्फ़ इससे यह तय नहीं होता कि आप पास नहीं हुए: नीचे आधिकारिक PDF देखें। | |
| `officialFiles` | Official files of this notice | इस सूचना की आधिकारिक फ़ाइलें | |
| `listCount` | {n} roll numbers | {n} रोल नंबर | |
| `noLists` | No result lists to search right now. | अभी खोजने के लिए कोई रिज़ल्ट सूची नहीं है। | |
| `forMeTitle` | Exams that suit you | आपके लिए परीक्षाएँ | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | अपनी जानकारी एक बार भरें। आप देखेंगे कि किन SSC परीक्षाओं में आवेदन कर सकते हैं, उनकी स्थिति और आगे क्या है। | |
| `forMeEdit` | Edit my details | मेरी जानकारी बदलें | |
| `forMeNoProfile` | Add your details to see exams for you. | अपने लिए परीक्षाएँ देखने के लिए अपनी जानकारी भरें। | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | आपकी योग्यता के लिए (नियम अभी नहीं जोड़े गए: नोटिस पढ़ें) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | आपकी योग्यता के लिए आने वाली परीक्षाएँ (संभावित) | |
| `saveDetails` | Show my exams | मेरी परीक्षाएँ दिखाएँ | |
| `whenTitle` | What has happened and what is next | अब तक क्या हुआ और आगे क्या है | |
| `st_done` | Done | हो गया | |
| `st_now` | Now | अभी | |
| `st_next` | Next | अगला | |
| `st_later` | Later | बाद में | |
| `stage_notice` | Notice published | नोटिस जारी | |
| `stage_apply` | Online application | ऑनलाइन आवेदन | |
| `stage_admit_card` | Admit card / exam city | एडमिट कार्ड / परीक्षा शहर | |
| `stage_answer_key` | Answer key | उत्तर कुंजी | |
| `stage_result` | Result | रिज़ल्ट | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ने अभी तिथि घोषित नहीं की है | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | मूल वेतन ₹{from}–{to} प्रति माह (लेवल {l}; भत्ते अलग) | |
| `savedLink` | ★ Saved | ★ सहेजे गए | |
| `disclaimer` | Not a government website and not affiliated with the Staff Selection Commission, the UP Police Recruitment & Promotion Board or any government. Everything here is copied from ssc.gov.in and uppbpb.gov.in; every item links to the official file. Always confirm with the official Notice before applying. | यह सरकारी वेबसाइट नहीं है और कर्मचारी चयन आयोग, उत्तर प्रदेश पुलिस भर्ती एवं प्रोन्नति बोर्ड या किसी भी सरकार से जुड़ी नहीं है। यहाँ सब कुछ ssc.gov.in और uppbpb.gov.in से लिया गया है; हर सूचना आधिकारिक फ़ाइल से जुड़ी है। आवेदन से पहले आधिकारिक नोटिस ज़रूर पढ़ें। | |
| `tabExams` | Exams | परीक्षाएँ | |
| `shortDisclaimer` | Unofficial: not affiliated with SSC, UPPRPB or any government. Sources: ssc.gov.in, uppbpb.gov.in | अनौपचारिक: SSC, UPPRPB या किसी सरकार से संबद्ध नहीं। स्रोत: ssc.gov.in, uppbpb.gov.in | |
| `filters` | Filters | फ़िल्टर | |
| `langToggle` | हिन्दी | English | |
| `kindAll` | All | सभी | |
| `kindRecruitment` | New jobs | नई भर्ती | |
| `kindResult` | Results | रिज़ल्ट | |
| `kindAdmit` | Admit cards | एडमिट कार्ड | |
| `kindAnswer` | Answer keys | उत्तर कुंजी | |
| `kindAmendment` | Changes | बदलाव | |
| `kindVacancy` | Vacancies | रिक्तियाँ | |
| `kind_recruitment` | New job | नई भर्ती | |
| `kind_amendment` | Change | बदलाव | |
| `kind_result` | Result | रिज़ल्ट | |
| `kind_answer_key` | Answer key | उत्तर कुंजी | |
| `kind_admit_card` | Admit card | एडमिट कार्ड | |
| `kind_vacancy` | Vacancy | रिक्ति | |
| `kind_schedule` | Exam dates | परीक्षा तिथि | |
| `kind_notice` | Notice | सूचना | |
| `search` | Search notices | सूचनाएँ खोजें | |
| `allExams` | All exams | सभी परीक्षाएँ | |
| `myExams` | My exams only | केवल मेरी परीक्षाएँ | |
| `anyQualification` | Any qualification | कोई भी योग्यता | |
| `qual_10` | Class 10 | 10वीं | |
| `qual_12` | Class 12 | 12वीं | |
| `qual_diploma` | Diploma | डिप्लोमा | |
| `qual_graduate` | Bachelor's degree | स्नातक | |
| `qual_postgraduate` | Master's degree | स्नातकोत्तर | |
| `langEn` | English | English | |
| `langHi` | हिन्दी | हिन्दी | |
| `langBoth` | English + हिन्दी | English + हिन्दी | |
| `departmental` | Departmental exams | विभागीय परीक्षाएँ | |
| `updated` | Updated {time} IST · sources: ssc.gov.in, uppbpb.gov.in | अपडेट {time} IST · स्रोत: ssc.gov.in, uppbpb.gov.in | |
| `newSince` | {n} new since your last visit | आपकी पिछली विज़िट के बाद {n} नई | |
| `cutoffTitle` | Cut-off marks | कट-ऑफ़ अंक | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC कट-ऑफ़ केवल रिज़ल्ट राइट-अप में देता है। यहाँ अंक नकल नहीं किए जाते (एक भी गलत अंक भ्रम पैदा करेगा): हर लिंक SSC की PDF को उसी पेज पर खोलता है जहाँ से कट-ऑफ़ विवरण शुरू होता है। | |
| `cutoffFrom` | Open at page {p} | पेज {p} पर खोलें | |
| `cutoffPages` | mentioned on p. {pages} | पेज {pages} पर उल्लेख | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | पिछले 120 दिनों के रिज़ल्ट राइट-अप में कोई कट-ऑफ़ नहीं मिला। | |
| `newBadge` | NEW | नया | |
| `nothing` | Nothing here in the last 120 days. | पिछले 120 दिनों में यहाँ कुछ नहीं है। | |
| `loadError` | Couldn't load. Check your connection, or see ssc.gov.in. | लोड नहीं हो सका। इंटरनेट जाँचें या ssc.gov.in देखें। | |
| `offline` | Offline: showing the copy saved on this phone. | ऑफ़लाइन: इस फ़ोन में सहेजी गई कॉपी दिख रही है। | |
| `save` | Save | सहेजें | |
| `saved` | Saved | सहेजा गया | |
| `share` | Share | शेयर करें | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | आधिकारिक पेज | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official PDF: {url} ⏎ via SSC Job Alerts (not a government site) | {title} ⏎ आधिकारिक PDF: {url} ⏎ SSC जॉब अलर्ट से (सरकारी साइट नहीं) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | हर परीक्षा का ताज़ा नोटिस, तिथियाँ, शुल्क, आयु सीमा और अब तक की सभी सूचनाएँ। | |
| `follow` | Follow | फ़ॉलो करें | |
| `following` | Following | फ़ॉलो कर रहे हैं | |
| `minQual` | Qualification: {q} | योग्यता: {q} | |
| `qualUnknown` | Qualification: see the Notice | योग्यता: नोटिस देखें | |
| `calendarTitle` | SSC's tentative calendar | SSC का संभावित कैलेंडर | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | यह योजना है, नोटिस नहीं: असली तिथियाँ हर नोटिस में होती हैं और अलग हो सकती हैं। | |
| `resourcesTitle` | Official resources | आधिकारिक सामग्री | |
| `res_syllabus` | Syllabus | पाठ्यक्रम | |
| `res_vacancy` | Vacancies (tentative / final) | रिक्तियाँ (संभावित / अंतिम) | |
| `res_previous_papers` | Previous year papers | पिछले वर्ष के प्रश्नपत्र | |
| `res_certificate_formats` | Certificate formats | प्रमाण पत्र के प्रारूप | |
| `res_instructions` | Instructions | निर्देश | |
| `back` | ← All exams | ← सभी परीक्षाएँ | |
| `latestNotice` | Latest Notice | ताज़ा नोटिस | |
| `importantDates` | Important dates | महत्वपूर्ण तिथियाँ | |
| `applyWindow` | Apply online | ऑनलाइन आवेदन | |
| `feeLastDate` | Fee payment last date | शुल्क भुगतान की अंतिम तिथि | |
| `correctionWindow` | Form correction window | फ़ॉर्म सुधार की अवधि | |
| `tentative` | tentative | संभावित | |
| `applicationFee` | Application fee | आवेदन शुल्क | |
| `feeGeneral` | General / OBC / EWS | सामान्य / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | छूट (₹0) | |
| `exempt_female` | Women | महिलाएँ | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | दिव्यांग (PwBD) | |
| `exempt_ESM` | Ex-servicemen | भूतपूर्व सैनिक | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | सुधार: पहली बार ₹{a}, दूसरी बार ₹{b} (सभी उम्मीदवार) | |
| `ageLimit` | Age limit (as on {date}) | आयु सीमा ({date} को) | |
| `ageBand` | {band} years: born {from} to {to} | {band} वर्ष: जन्म {from} से {to} तक | |
| `relaxation` | Upper age relaxation | ऊपरी आयु में छूट | |
| `relaxYears` | {n} years | {n} वर्ष | |
| `notComputed` | Not calculated here: {list} | यहाँ गणना नहीं होती: {list} | |
| `vacancies` | Vacancies | रिक्तियाँ | |
| `vacancyCount` | About {n} ({tentative}) | लगभग {n} ({tentative}) | |
| `posts` | Posts | पद | |
| `importantLinks` | Important links | महत्वपूर्ण लिंक | |
| `applyOnline` | Apply online (ssc.gov.in) | ऑनलाइन आवेदन (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | आधिकारिक नोटिस (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} का बदलाव | |
| `allNotices` | All notices for this exam | इस परीक्षा की सभी सूचनाएँ | |
| `addCalendar` | Add dates to my calendar | तिथियाँ मेरे कैलेंडर में जोड़ें | |
| `checkThis` | Check my eligibility | मेरी योग्यता जाँचें | |
| `status_open` | Applications open till {date} | {date} तक आवेदन खुले हैं | |
| `status_upcoming` | Applications open on {date} | {date} से आवेदन खुलेंगे | |
| `status_closed` | Applications closed on {date} | {date} को आवेदन बंद हो गए | |
| `daysLeft` | {n} days left | {n} दिन बाकी | |
| `lastDay` | Last day today | आज अंतिम दिन | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | इस परीक्षा के योग्यता नियम अभी नहीं जोड़े गए: नोटिस पढ़ें। | |
| `examLabel` | Exam | परीक्षा | |
| `dob` | Date of birth (as in Class 10 certificate) | जन्म तिथि (10वीं के प्रमाण पत्र के अनुसार) | |
| `category` | Category | वर्ग | |
| `catUR` | General (UR) | सामान्य (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (नॉन-क्रीमी लेयर) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | उच्चतम योग्यता | |
| `edu10` | Class 10 | 10वीं | |
| `edu12` | Class 12 | 12वीं | |
| `eduGraduate` | Bachelor's degree or higher | स्नातक या उससे ऊपर | |
| `gender` | Gender | लिंग | |
| `male` | Male | पुरुष | |
| `female` | Female | महिला | |
| `other` | Other | अन्य | |
| `appearing` | Final exam of that level taken, result not out yet | उस स्तर की अंतिम परीक्षा दी है, परिणाम अभी नहीं आया | |
| `pwbd` | Person with benchmark disability (PwBD) | बेंचमार्क दिव्यांगता वाले व्यक्ति (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | भूतपूर्व सैनिक, केंद्र सरकार कर्मचारी, या विधवा/तलाकशुदा महिला | |
| `check` | Check | जाँचें | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | आपके उत्तर इसी फ़ोन में रहते हैं। कुछ भी कहीं नहीं भेजा जाता। | |
| `enterDob` | Enter your date of birth. | अपनी जन्म तिथि भरें। | |
| `closedNote` | Shown so you can prepare for the next cycle. | अगले चक्र की तैयारी के लिए दिखाया गया है। | |
| `canApply` | {n} can apply | {n} में आवेदन कर सकते हैं | |
| `toCheck` | {n} to check | {n} में जाँच ज़रूरी | |
| `notEligible` | {n} not eligible | {n} में योग्य नहीं | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | शुल्क: {fee}। आयु छूट: {n} वर्ष। आयु की गणना {date} को। | |
| `feeNone` | none (exempt) | नहीं (छूट) | |
| `st_eligible` | You can apply | आवेदन कर सकते हैं | |
| `st_check` | Check one condition | एक शर्त जाँचें | |
| `st_ineligible` | Not eligible | योग्य नहीं | |
| `payLevel` | Pay Level {l} · Group {g} | पे लेवल {l} · ग्रुप {g} | |
| `groupOnly` | Group {g} | ग्रुप {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | जन्म {from} और {to} के बीच (आपके लिए) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | नियम {date} के नोटिस से लिए गए ({checked} को जाँचे गए)। | |
| `sigTitle` | Signature for SSC forms | SSC फ़ॉर्म के लिए हस्ताक्षर | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC को {min}-{max} KB का JPEG चाहिए, लगभग {w} सेमी × {h} सेमी। सफ़ेद कागज़ पर काली स्याही से हस्ताक्षर करके साफ़ फ़ोटो लें। | |
| `sigPick` | Choose signature photo | हस्ताक्षर की फ़ोटो चुनें | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | तैयार: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | फ़ाइल {min} से {max} KB के बीच नहीं बन सकी। ज़्यादा साफ़ फ़ोटो लें। | |
| `sigDownload` | Download JPEG | JPEG डाउनलोड करें | |
| `photoTitle` | Your photograph | आपकी फ़ोटो | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC अब फ़ोटो अपलोड नहीं लेता: आवेदन फ़ॉर्म आपके कैमरे से फ़ोटो लेता है, और पुरानी फ़ोटो की फ़ोटो खींचने पर फ़ॉर्म रद्द हो जाता है। अच्छी रोशनी, सादी पृष्ठभूमि, आँखों के स्तर पर कैमरा रखें; टोपी, मास्क या चश्मा न पहनें। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | स्रोत: नोटिस पैरा 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | आयु कैलकुलेटर | |
| `ageOn` | Age on | इस तिथि को आयु | |
| `ageResult` | {y} years, {m} months, {d} days | {y} वर्ष, {m} महीने, {d} दिन | |
| `ageInvalid` | Date of birth must be before that date. | जन्म तिथि उस तिथि से पहले होनी चाहिए। | |
| `docsTitle` | Certificate formats | प्रमाण पत्र के प्रारूप | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | वर्ग, दिव्यांगता और भूतपूर्व सैनिक प्रमाण पत्रों के लिए SSC के प्रारूप। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | अभी कुछ सहेजा नहीं गया। किसी सूचना पर "सहेजें" दबाएँ। | |
| `followedExams` | Exams you follow | आप जिन परीक्षाओं को फ़ॉलो करते हैं | |

## 2. 지원 방법·서류 (pipeline/ssc_guide.json, 공고 문단 번호 포함)

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

## 3. 시험 이름 (pipeline/exam_families.json)

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
