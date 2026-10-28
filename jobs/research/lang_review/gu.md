# ગુજરાતી (gu) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/gu.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/gu.js)

| 키 | English | ગુજરાતી | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | સરકારી જોબ અલર્ટ | |
| `tabHome` | Home | હોમ | |
| `tabMore` | More | વધુ | |
| `loading` | Loading… | લોડ થઈ રહ્યું છે… | |
| `homeComing` | Exam dates in the next 30 days | આવતા 30 દિવસની પરીક્ષા તારીખો | |
| `homeLatest` | Latest updates | તાજા અપડેટ્સ | |
| `inDays` | in {n} days | {n} દિવસમાં | |
| `onNow` | on now | ચાલુ છે | |
| `today` | today | આજે | |
| `theme` | Theme | થીમ | |
| `themeAuto` | Auto | ઓટો | |
| `themeLight` | Light | લાઇટ | |
| `themeDark` | Dark | ડાર્ક | |
| `language` | Language | ભાષા | |
| `howToApply` | How to apply | અરજી કેવી રીતે કરવી | |
| `documents` | Keep these documents ready | આ દસ્તાવેજો તૈયાર રાખો | |
| `helplineTitle` | SSC helplines | SSC હેલ્પલાઇન | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ફોર્મ ભરવામાં કે કોમ્પ્યુટર આધારિત પરીક્ષામાં તકલીફ હોય તો. | |
| `typingTitle` | Typing / data entry speed practice | ટાઇપિંગ / ડેટા એન્ટ્રી સ્પીડ પ્રેક્ટિસ | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | નીચેનો ફકરો ટાઇપ કરો. પહેલી કી દબાવતાં જ ટાઇમર શરૂ થાય છે. સ્પીડ SSC ની જેમ જ પ્રતિ કલાક કી ડિપ્રેશન્સમાં બતાવી છે. | |
| `typingTest` | Standard | સ્ટાન્ડર્ડ | |
| `typingMinutes` | Time | સમય | |
| `typingFull` | Full ({n} min) | પૂરું ({n} મિનિટ) | |
| `typingStart` | Start again | ફરી શરૂ કરો | |
| `typingDone` | Time is up. | સમય પૂરો થયો. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | પ્રતિ કલાક {kdph} કી ડિપ્રેશન્સ (≈ {wpm} w.p.m.), {accuracy}% ચોકસાઈ, {typed} માંથી {correct} સાચા | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} ની સ્પીડ ({kdph}/કલાક) પૂરી થાય છે. | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} ની સ્પીડ ({kdph}/કલાક) કરતાં ઓછી: પ્રેક્ટિસ ચાલુ રાખો. | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | ફક્ત પ્રેક્ટિસ માટે: SSC પોતાના મૂલ્યાંકન નિયમો મુજબ ભૂલો ગણે છે. હિન્દી ટાઇપિંગની પ્રેક્ટિસ અહીં ઉપલબ્ધ નથી. | |
| `faqTitle` | Frequently asked | વારંવાર પૂછાતા પ્રશ્નો | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} માટે અરજી કરવાની છેલ્લી તારીખ કઈ છે? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | અરજી કરવાની છેલ્લી તારીખ {date} છે; ફી ભરવાની મુદત {fee} એ પૂરી થાય છે. | |
| `faqAge` | What is the age limit for {exam}? | {exam} માટે વય મર્યાદા કેટલી છે? | |
| `faqFee` | What is the application fee? | અરજી ફી કેટલી છે? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | જનરલ, OBC અને EWS ઉમેદવારો માટે ₹{amount}. {exempt} માટે કોઈ ફી નથી. | |
| `faqSalary` | What is the salary? | પગાર કેટલો છે? | |
| `faqExam` | When is the {exam} exam? | {exam} ની પરીક્ષા ક્યારે છે? | |
| `faqVacancy` | How many vacancies are there? | કેટલી જગ્યાઓ છે? | |
| `faqQual` | What qualification is needed? | કઈ લાયકાત જોઈએ? | |
| `aboutTitle` | About this app | આ એપ વિશે | |
| `privacyPolicy` | Privacy policy | ગોપનીયતા નીતિ | |
| `termsOfUse` | Terms of use | નિયમો અને શરતો | |
| `contactUs` | Contact us | સંપર્ક કરો | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ભારતમાં સરકારી નોકરીની તૈયારી કરતા લોકો માટે એક મફત, બિનસત્તાવાર મદદગાર. નોટિસ, તારીખો અને નિયમો ભરતી સંસ્થાઓની સત્તાવાર વેબસાઇટ્સ અને નોટિસમાંથી, સંદર્ભ સાથે લેવાયા છે. સાઇન-અપ નહીં, કોઈ અંગત ડેટા એકત્ર થતો નથી: તમારી વિગતો અને રોલ નંબર આ જ ફોનમાં રહે છે. | |
| `sourceRef` | Source: {ref} | સ્રોત: {ref} | |
| `toastSaved` | Saved | સેવ થયું | |
| `toastRemoved` | Removed | દૂર કર્યું | |
| `toastFollow` | You follow this exam | તમે આ પરીક્ષા ફોલો કરો છો | |
| `toastUnfollow` | Unfollowed | અનફોલો કર્યું | |
| `settings` | Settings | સેટિંગ્સ | |
| `toolsTitle` | Tools | ટૂલ્સ | |
| `tabResult` | Result | રિઝલ્ટ | |
| `tabForMe` | For me | મારા માટે | |
| `resultTitle` | Check your result by roll number | રોલ નંબરથી તમારું રિઝલ્ટ જુઓ | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | મોટી PDF ડાઉનલોડ કર્યા વગર, SSC એ છેલ્લા 120 દિવસમાં બહાર પાડેલી રિઝલ્ટ યાદીઓમાં શોધે છે. નાની ફાઇલ મેળવવા માટે તમારા રોલ નંબરના ફક્ત છેલ્લા બે અંક વપરાય છે. | |
| `rollNo` | Roll number | રોલ નંબર | |
| `resultExam` | Result | રિઝલ્ટ | |
| `find` | Find | શોધો | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | એડમિટ કાર્ડ પર છપાયેલો રોલ નંબર દાખલ કરો (8-14 અંક). | |
| `foundIn` | Found in: {list} | આમાં મળ્યો: {list} | |
| `notIn` | Not in: {list} | આમાં નથી: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | તમારો રોલ નંબર આ યાદીઓમાં નથી. ફક્ત એટલાથી એવું નથી કે તમે ક્વોલિફાય નથી થયા: નીચેની સત્તાવાર PDF તપાસો. | |
| `officialFiles` | Official files of this notice | આ નોટિસની સત્તાવાર ફાઇલો | |
| `listCount` | {n} roll numbers | {n} રોલ નંબર | |
| `noLists` | No result lists to search right now. | અત્યારે શોધવા માટે કોઈ રિઝલ્ટ યાદી નથી. | |
| `forMeTitle` | Exams that suit you | તમારા માટે યોગ્ય પરીક્ષાઓ | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | તમારી વિગતો એક જ વાર ભરો. તમે અરજી કરી શકો તેવી દરેક SSC પરીક્ષા, તેનું સ્ટેટસ અને આગળ શું છે તે દેખાશે. | |
| `forMeEdit` | Edit my details | મારી વિગતો બદલો | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | તમારી લાયકાત મુજબ (નિયમો હજી ઉમેર્યા નથી: નોટિસ વાંચો) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | તમારી લાયકાત મુજબ આવનારી પરીક્ષાઓ (સંભવિત) | |
| `saveDetails` | Show my exams | મારી પરીક્ષાઓ બતાવો | |
| `whenTitle` | What has happened and what is next | શું થઈ ગયું અને આગળ શું | |
| `st_done` | Done | થઈ ગયું | |
| `st_now` | Now | હમણાં | |
| `st_next` | Next | આગળ | |
| `st_later` | Later | પછી | |
| `stage_notice` | Notice published | નોટિસ બહાર પડી | |
| `stage_apply` | Online application | ઓનલાઇન અરજી | |
| `stage_admit_card` | Admit card / exam city | એડમિટ કાર્ડ / પરીક્ષા શહેર | |
| `stage_answer_key` | Answer key | આન્સર કી | |
| `stage_result` | Result | રિઝલ્ટ | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC એ હજી તારીખ જાહેર કરી નથી | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | બેઝિક પગાર ₹{from}–{to} પ્રતિ મહિને (લેવલ {l}; ભથ્થાં અલગ) | |
| `savedLink` | ★ Saved | ★ સેવ કરેલું | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | આ સરકારી વેબસાઇટ નથી અને કોઈ ભરતી સંસ્થા કે સરકાર સાથે જોડાયેલી નથી. અહીંની બધી માહિતી ભરતી સંસ્થાઓની સત્તાવાર વેબસાઇટ્સ પરથી લેવાઈ છે, અને દરેક અપડેટ સાથે સત્તાવાર ફાઇલ કે પેજની લિંક છે. અરજી કરતાં પહેલાં હંમેશા સત્તાવાર નોટિસ તપાસો. | |
| `tabExams` | Exams | પરીક્ષાઓ | |
| `filters` | Filters | ફિલ્ટર | |
| `kindAll` | All | બધું | |
| `kindRecruitment` | New jobs | નવી ભરતી | |
| `kindResult` | Results | રિઝલ્ટ | |
| `kindAdmit` | Admit cards | એડમિટ કાર્ડ | |
| `kindAnswer` | Answer keys | આન્સર કી | |
| `kindAmendment` | Changes | ફેરફાર | |
| `kindVacancy` | Vacancies | ખાલી જગ્યાઓ | |
| `kind_recruitment` | New job | નવી ભરતી | |
| `kind_amendment` | Change | ફેરફાર | |
| `kind_result` | Result | રિઝલ્ટ | |
| `kind_answer_key` | Answer key | આન્સર કી | |
| `kind_admit_card` | Admit card | એડમિટ કાર્ડ | |
| `kind_vacancy` | Vacancy | ખાલી જગ્યા | |
| `kind_schedule` | Exam dates | પરીક્ષા તારીખો | |
| `kind_notice` | Notice | નોટિસ | |
| `search` | Search jobs, exams, results | નોકરી, પરીક્ષા, રિઝલ્ટ શોધો | |
| `allExams` | All exams | બધી પરીક્ષાઓ | |
| `myExams` | My exams only | ફક્ત મારી પરીક્ષાઓ | |
| `anyQualification` | Any qualification | કોઈ પણ લાયકાત | |
| `qual_10` | Class 10 | ધોરણ 10 | |
| `qual_12` | Class 12 | ધોરણ 12 | |
| `qual_diploma` | Diploma | ડિપ્લોમા | |
| `qual_graduate` | Bachelor's degree | સ્નાતક (ગ્રેજ્યુએશન) | |
| `qual_postgraduate` | Master's degree | અનુસ્નાતક (પોસ્ટ ગ્રેજ્યુએશન) | |
| `departmental` | Departmental exams | ખાતાકીય પરીક્ષાઓ | |
| `updated` | Updated {time} IST | અપડેટ: {time} IST | |
| `newSince` | {n} new since your last visit | તમારી છેલ્લી મુલાકાત પછી {n} નવા | |
| `cutoffTitle` | Cut-off marks | કટ-ઓફ માર્ક્સ | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC કટ-ઓફ ફક્ત રિઝલ્ટના લખાણમાં જ આપે છે. આંકડા અહીં કોપી કર્યા નથી (એક ખોટો આંકડો પણ ગેરમાર્ગે દોરી શકે): દરેક લિંક SSC ની PDF, જ્યાંથી કટ-ઓફની વિગતો શરૂ થાય છે તે પાના પર ખોલે છે. | |
| `cutoffFrom` | Open at page {p} | પાના {p} પર ખોલો | |
| `cutoffPages` | mentioned on p. {pages} | પાના {pages} પર ઉલ્લેખ | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | છેલ્લા 120 દિવસના રિઝલ્ટમાં કોઈ કટ-ઓફ મળ્યો નથી. | |
| `alertsTitle` | Alerts on this phone | આ ફોન પર અલર્ટ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | તમે ફોલો કરો છો તે પરીક્ષાના સમાચાર આવે ત્યારે, અને અરજીની મુદત પૂરી થવાના બે દિવસ પહેલાં નોટિફિકેશન મળશે. | |
| `alertsOn` | Turn on alerts | અલર્ટ ચાલુ કરો | |
| `alertsOff` | Turn off alerts | અલર્ટ બંધ કરો | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | પહેલાં કોઈ પરીક્ષા ફોલો કરો (પરીક્ષાઓ → ફોલો કરો). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} પરીક્ષાઓ માટે ચાલુ. ક્યારે તપાસવું તે Chrome નક્કી કરે છે, તમે એપ વારંવાર ખોલો તો વધુ વાર. એટલે સમાચાર પોસ્ટ થયાના થોડા કલાક પછી પહોંચી શકે છે. | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | આ સાઇટ માટે નોટિફિકેશન બ્લોક છે. બ્રાઉઝરના સાઇટ સેટિંગ્સમાં મંજૂરી આપો. | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | આ બ્રાઉઝર બેકગ્રાઉન્ડમાં તપાસી શકતું નથી. Android પર પહેલાં Chrome માંથી આ એપ ઇન્સ્ટોલ કરો. નહીં તો દરેક પરીક્ષાના પેજ પરના કેલેન્ડર રિમાઇન્ડર વાપરો. | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | તમારી પરીક્ષાઓ માટે વધુ {n} અપડેટ્સ | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: અરજીની મુદત {n} દિવસમાં પૂરી થાય છે | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: આજે અરજી કરવાનો છેલ્લો દિવસ | |
| `installTitle` | Install the app | એપ ઇન્સ્ટોલ કરો | |
| `installBtn` | Install | ઇન્સ્ટોલ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | એપની જેમ ખુલે છે, ઓફલાઇન ચાલે છે અને Android પર અલર્ટ મળે છે. | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone પર: Share પર ટેપ કરો, પછી "Add to Home Screen". | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ફોલો કર્યું. ફોન પર અલર્ટ માટે: વધુ → અલર્ટ ચાલુ કરો | |
| `rollPlaceholder` | Roll number on your admit card | એડમિટ કાર્ડ પરનો રોલ નંબર | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} પરીક્ષાઓ માટે ચાલુ. SSC ની નોટિસ સામાન્ય રીતે પોસ્ટ થયાના એક કલાકમાં પહોંચે છે; રિઝલ્ટ અને બીજા બોર્ડની નોટિસમાં 3 કલાક સુધી લાગી શકે છે. | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | આ બ્રાઉઝર અલર્ટ બતાવી શકતું નથી. iPhone પર પહેલાં આ એપ હોમ સ્ક્રીન પર ઉમેરો (Share → Add to Home Screen), પછી ત્યાંથી જ ખોલો. | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | અલર્ટ ચાલુ ન થઈ શક્યા. ઇન્ટરનેટ કનેક્શન તપાસો અને ફરી પ્રયાસ કરો. | |
| `allBoards` | All recruiters | બધી ભરતી સંસ્થાઓ | |
| `newBadge` | NEW | નવું | |
| `nothing` | Nothing here in the last 120 days. | છેલ્લા 120 દિવસમાં અહીં કંઈ નથી. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | લોડ ન થયું. ઇન્ટરનેટ કનેક્શન તપાસો અને ફરી પ્રયાસ કરો. | |
| `offline` | Offline: showing the copy saved on this phone. | ઓફલાઇન: આ ફોનમાં સેવ કરેલી કોપી બતાવીએ છીએ. | |
| `save` | Save | સેવ કરો | |
| `saved` | Saved | સેવ થયું | |
| `share` | Share | શેર કરો | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | સત્તાવાર પેજ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ સત્તાવાર લિંક: {url} ⏎ {app} દ્વારા (સરકારી સાઇટ નથી) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | દરેક પરીક્ષાની તાજી નોટિસ, તારીખો, ફી, વય મર્યાદા અને ત્યાર પછીની બધી નોટિસ. | |
| `follow` | Follow | ફોલો કરો | |
| `following` | Following | ફોલો કરો છો | |
| `minQual` | Qualification: {q} | લાયકાત: {q} | |
| `qualUnknown` | Qualification: see the Notice | લાયકાત: નોટિસ જુઓ | |
| `calendarTitle` | SSC's tentative calendar | SSC નું સંભવિત કેલેન્ડર | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | આ આયોજન છે, નોટિસ નથી: સાચી તારીખો દરેક નોટિસમાં હોય છે અને અલગ હોઈ શકે છે. | |
| `resourcesTitle` | Official resources | સત્તાવાર સામગ્રી | |
| `res_syllabus` | Syllabus | સિલેબસ | |
| `res_vacancy` | Vacancies (tentative / final) | ખાલી જગ્યાઓ (સંભવિત / અંતિમ) | |
| `res_previous_papers` | Previous year papers | પાછલા વર્ષના પેપર | |
| `res_certificate_formats` | Certificate formats | પ્રમાણપત્રોના નમૂના | |
| `res_instructions` | Instructions | સૂચનાઓ | |
| `back` | ← All exams | ← બધી પરીક્ષાઓ | |
| `latestNotice` | Latest Notice | તાજી નોટિસ | |
| `importantDates` | Important dates | મહત્વની તારીખો | |
| `applyWindow` | Apply online | ઓનલાઇન અરજી | |
| `feeLastDate` | Fee payment last date | ફી ભરવાની છેલ્લી તારીખ | |
| `correctionWindow` | Form correction window | ફોર્મ સુધારવાની મુદત | |
| `tentative` | tentative | સંભવિત | |
| `applicationFee` | Application fee | અરજી ફી | |
| `feeGeneral` | General / OBC / EWS | જનરલ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ફી માફ (₹0) | |
| `exempt_female` | Women | મહિલાઓ | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | માજી સૈનિકો | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | સુધારો: પહેલી વાર ₹{a}, બીજી વાર ₹{b} (બધા ઉમેદવારો માટે) | |
| `ageLimit` | Age limit (as on {date}) | વય મર્યાદા ({date} ના રોજ) | |
| `ageBand` | {band} years: born {from} to {to} | {band} વર્ષ: જન્મ {from} થી {to} | |
| `relaxation` | Upper age relaxation | મહત્તમ વય મર્યાદામાં છૂટ | |
| `relaxYears` | {n} years | {n} વર્ષ | |
| `notComputed` | Not calculated here: {list} | અહીં ગણતરી નથી કરી: {list} | |
| `vacancies` | Vacancies | ખાલી જગ્યાઓ | |
| `vacancyCount` | About {n} ({tentative}) | આશરે {n} ({tentative}) | |
| `posts` | Posts | જગ્યાઓ (પોસ્ટ) | |
| `importantLinks` | Important links | મહત્વની લિંક્સ | |
| `applyOnline` | Apply online (ssc.gov.in) | ઓનલાઇન અરજી કરો (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | સત્તાવાર નોટિસ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} નો ફેરફાર | |
| `allNotices` | All notices for this exam | આ પરીક્ષાની બધી નોટિસ | |
| `addCalendar` | Add dates to my calendar | તારીખો મારા કેલેન્ડરમાં ઉમેરો | |
| `checkThis` | Check my eligibility | મારી લાયકાત તપાસો | |
| `status_open` | Applications open till {date} | {date} સુધી અરજી ચાલુ | |
| `status_upcoming` | Applications open on {date} | {date} થી અરજી શરૂ થશે | |
| `status_closed` | Applications closed on {date} | {date} એ અરજી બંધ થઈ | |
| `daysLeft` | {n} days left | {n} દિવસ બાકી | |
| `lastDay` | Last day today | આજે છેલ્લો દિવસ | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | આ પરીક્ષાના લાયકાતના નિયમો હજી ઉમેર્યા નથી: નોટિસ વાંચો. | |
| `examLabel` | Exam | પરીક્ષા | |
| `dob` | Date of birth (as in Class 10 certificate) | જન્મ તારીખ (ધોરણ 10 ના પ્રમાણપત્ર મુજબ) | |
| `category` | Category | કેટેગરી | |
| `catUR` | General (UR) | જનરલ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (નોન-ક્રીમી લેયર) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | સૌથી ઊંચી લાયકાત | |
| `edu10` | Class 10 | ધોરણ 10 | |
| `edu12` | Class 12 | ધોરણ 12 | |
| `eduGraduate` | Bachelor's degree or higher | સ્નાતક કે તેથી વધુ | |
| `gender` | Gender | લિંગ | |
| `male` | Male | પુરુષ | |
| `female` | Female | સ્ત્રી | |
| `other` | Other | અન્ય | |
| `appearing` | Final exam of that level taken, result not out yet | તે સ્તરની છેલ્લી પરીક્ષા આપી દીધી, રિઝલ્ટ હજી આવ્યું નથી | |
| `pwbd` | Person with benchmark disability (PwBD) | બેન્ચમાર્ક દિવ્યાંગતા ધરાવતી વ્યક્તિ (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | માજી સૈનિક, કેન્દ્ર સરકારના કર્મચારી, કે વિધવા/છૂટાછેડા લીધેલ મહિલા | |
| `check` | Check | તપાસો | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | તમારા જવાબો આ જ ડિવાઇસમાં રહે છે. કંઈ પણ ક્યાંય મોકલાતું નથી. | |
| `enterDob` | Enter your date of birth. | તમારી જન્મ તારીખ દાખલ કરો. | |
| `closedNote` | Shown so you can prepare for the next cycle. | આગામી ભરતીની તૈયારી કરી શકો એટલે બતાવ્યું છે. | |
| `canApply` | {n} can apply | {n} માં અરજી કરી શકો છો | |
| `toCheck` | {n} to check | {n} તપાસવાની બાકી | |
| `notEligible` | {n} not eligible | {n} માં લાયક નથી | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ફી: {fee}. લાગુ કરેલી વય છૂટ: {n} વર્ષ. ઉંમર {date} ના રોજની ગણાય છે. | |
| `feeNone` | none (exempt) | કોઈ નહીં (ફી માફ) | |
| `st_eligible` | You can apply | તમે અરજી કરી શકો છો | |
| `st_check` | Check one condition | એક શરત તપાસો | |
| `st_ineligible` | Not eligible | લાયક નથી | |
| `payLevel` | Pay Level {l} · Group {g} | પે લેવલ {l} · ગ્રુપ {g} | |
| `groupOnly` | Group {g} | ગ્રુપ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | જન્મ {from} અને {to} વચ્ચે (તમારા માટે) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date} ની નોટિસમાંથી લીધેલા નિયમો (તપાસ્યું: {checked}). | |
| `sigTitle` | Signature for SSC forms | SSC ફોર્મ માટે સહી | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC ને {min}-{max} KB ની JPEG ફાઇલ જોઈએ, આશરે {w} cm × {h} cm. સફેદ કાગળ પર કાળી શાહીથી સહી કરો અને સ્પષ્ટ ફોટો પાડો. | |
| `sigPick` | Choose signature photo | સહીનો ફોટો પસંદ કરો | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | તૈયાર: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | {min} થી {max} KB વચ્ચે ફાઇલ બની નહીં. વધુ સ્પષ્ટ ફોટો અજમાવો. | |
| `sigDownload` | Download JPEG | JPEG ડાઉનલોડ કરો | |
| `photoTitle` | Your photograph | તમારો ફોટો | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC હવે અપલોડ કરેલો ફોટો લેતું નથી: અરજી ફોર્મ તમારા કેમેરાથી લાઇવ ફોટો લે છે, અને જૂના ફોટાનો ફોટો પાડશો તો ફોર્મ રિજેક્ટ થાય છે. સારો પ્રકાશ, સાદું બેકગ્રાઉન્ડ, કેમેરા આંખના લેવલે રાખો; ટોપી, માસ્ક કે ચશ્મા નહીં. | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | સ્રોત: નોટિસ ફકરો 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | ઉંમર કેલ્ક્યુલેટર | |
| `ageOn` | Age on | આ તારીખે ઉંમર | |
| `ageResult` | {y} years, {m} months, {d} days | {y} વર્ષ, {m} મહિના, {d} દિવસ | |
| `ageInvalid` | Date of birth must be before that date. | જન્મ તારીખ તે તારીખ પહેલાંની હોવી જોઈએ. | |
| `docsTitle` | Certificate formats | પ્રમાણપત્રોના નમૂના | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | કેટેગરી, દિવ્યાંગતા અને માજી સૈનિક પ્રમાણપત્રો માટે SSC ના નમૂના. | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | હજી કંઈ સેવ કર્યું નથી. કોઈ પણ નોટિસ પર સેવ કરો ટેપ કરો. | |
| `followedExams` | Exams you follow | તમે ફોલો કરો છો તે પરીક્ષાઓ | |
| `chooseLanguage` | Choose your language | તમારી ભાષા પસંદ કરો | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | નોટિસના ટાઇટલ ભરતી સંસ્થા જે ભાષામાં બહાર પાડે તે જ ભાષામાં રહે છે (મોટે ભાગે અંગ્રેજી કે હિન્દી). | |
| `qaLatest` | Latest jobs | નવી ભરતી | |
| `qaResults` | Results | રિઝલ્ટ | |
| `qaAdmit` | Admit cards | એડમિટ કાર્ડ | |
| `qaAnswer` | Answer keys | આન્સર કી | |
| `qaEligibility` | Check eligibility | લાયકાત તપાસો | |
| `qaRoll` | Result by roll no. | રોલ નં. થી રિઝલ્ટ | |
| `homeSectors` | Jobs by sector | ક્ષેત્ર મુજબ નોકરીઓ | |
| `homeStates` | Jobs by state | રાજ્ય મુજબ નોકરીઓ | |
| `homeClosing` | Closing soon | મુદત જલદી પૂરી થશે | |
| `homeChannels` | Get every update on WhatsApp or Telegram | દરેક અપડેટ WhatsApp કે Telegram પર મેળવો | |
| `homeChannelsNote` | Free. One message per update, with the official link. | મફત. દરેક અપડેટ માટે એક મેસેજ, સત્તાવાર લિંક સાથે. | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | બિનસત્તાવાર · દરેક અપડેટ સાથે સત્તાવાર વેબસાઇટની લિંક | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | પોલીસ | |
| `sector_defence` | Defence & CAPF | સંરક્ષણ અને CAPF | |
| `sector_railway` | Railway | રેલવે | |
| `sector_bank` | Bank | બેંક | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | વિજ્ઞાન અને અવકાશ | |
| `sector_state` | State govt | રાજ્ય સરકાર | |
| `sector_teaching` | Teaching | શિક્ષક | |
| `state_all` | All India | સમગ્ર ભારત | |
| `state_UP` | Uttar Pradesh | ઉત્તર પ્રદેશ | |
| `state_BR` | Bihar | બિહાર | |
| `state_HR` | Haryana | હરિયાણા | |
| `anySector` | All sectors | બધા ક્ષેત્રો | |
| `anyState` | All states | બધા રાજ્યો | |
| `jobsSector` | {name} jobs | {name} નોકરીઓ | |
| `jobsState` | Government jobs in {name} | {name} માં સરકારી નોકરીઓ | |
| `jobsKind` | {name}: latest | {name}: તાજું | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | આ ક્ષેત્રમાંથી હજી કંઈ નથી. ભરતી સંસ્થાની સત્તાવાર વેબસાઇટ તપાસ્યા પછી જ અમે તેને ઉમેરીએ છીએ, એટલે આ યાદી વધતી જશે. | |
| `centralJobs` | Central govt (all India) | કેન્દ્ર સરકાર (સમગ્ર ભારત) | |
| `recruitersTitle` | Recruiters we follow | અમે ફોલો કરીએ છીએ તે ભરતી સંસ્થાઓ | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | અપડેટ્સ આ સત્તાવાર વેબસાઇટ્સ પરથી લેવાય છે. ખોલવા માટે ટેપ કરો. | |
| `moreUpdates` | See all updates | બધા અપડેટ્સ જુઓ | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | ઉંમર ઓછી છે: {date} ના રોજ તમારી ઉંમર ઓછામાં ઓછી {n} હોવી જોઈએ. | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | વય મર્યાદા વટાવી: {date} ના રોજ તમારી ઉંમર {n} થી ઓછી હોવી જોઈએ (છૂટ સહિત). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | સામાન્ય વય મર્યાદા વટાવી. માજી સૈનિક / સરકારી કર્મચારી / વિધવા માટેની છૂટ લાગુ પડી શકે: નોટિસ {ref} જુઓ. | |
| `r_education_graduate` | Needs a bachelor's degree. | સ્નાતક ડિગ્રી જરૂરી. | |
| `r_education_12` | Needs Class 12 pass. | ધોરણ 12 પાસ જરૂરી. | |
| `r_education_10` | Needs Class 10 pass. | ધોરણ 10 પાસ જરૂરી. | |
| `r_result_needed_by` | Your result must be out by {date}. | તમારું રિઝલ્ટ {date} સુધીમાં આવી જવું જોઈએ. | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | રિઝલ્ટની રાહ જોતા ઉમેદવારો અરજી કરી શકે એવું નોટિસમાં કહ્યું નથી; {date} સુધીમાં તમારી પાસે લાયકાત હોવી જરૂરી છે. | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | આ નોટિસમાં PwBD માટે વય છૂટ આપી નથી: નોટિસ તપાસો. | |
| `r_gender_male` | Only for male candidates. | ફક્ત પુરુષ ઉમેદવારો માટે. | |
| `r_gender_female` | Only for female candidates. | ફક્ત મહિલા ઉમેદવારો માટે. | |
| `r_gender_other` | Only for {g} candidates. | ફક્ત {g} ઉમેદવારો માટે. | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | માધ્યમિક શાળા સ્તરે રાજ્યની સત્તાવાર/પ્રાદેશિક ભાષા જરૂરી. | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | ધોરણ 12 માં ગણિતમાં 60%, અથવા ડિગ્રીમાં આંકડાશાસ્ત્ર (Statistics) વિષય જરૂરી. | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ડિગ્રી આંકડાશાસ્ત્ર, ગણિત, અર્થશાસ્ત્ર, ડેમોગ્રાફી, પોપ્યુલેશન સ્ટડીઝ, ઓપરેશન્સ રિસર્ચ, IT, કોમ્પ્યુટર સાયન્સ/એન્જિનિયરિંગ/એપ્લિકેશન્સ, ડેટા સાયન્સ કે AI માંથી કોઈ એક વિષયમાં હોવી જોઈએ. | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | શારીરિક કસોટીના દિવસે માન્ય LMV (મોટરસાઇકલ અને કાર) ડ્રાઇવિંગ લાઇસન્સ જરૂરી. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ભૌતિકશાસ્ત્ર, રસાયણશાસ્ત્ર અને ગણિત સાથે B.Sc., અથવા ધોરણ 10 ઉપરાંત મિકેનિકલ, સિવિલ, ઇલેક્ટ્રિકલ કે યાદીમાંની એન્જિનિયરિંગ શાખામાં 3 વર્ષનો ડિપ્લોમા જરૂરી. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | શારીરિક અને મેડિકલ ધોરણો લાગુ (નોટિસ જુઓ). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | બોર્ડર રોડ્સ ઓર્ગેનાઇઝેશનની જગ્યાઓ: ફક્ત પુરુષો, શારીરિક ધોરણો લાગુ. | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | કેટલીક ઓફિસોમાં (ગ્રાહક બાબતો અને ખાદ્ય, SSC, સંસ્કૃતિ) આ જગ્યા માટે ગણિત સાથે ધોરણ 12 સાયન્સ જરૂરી. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | પસંદગી પ્રક્રિયામાં સ્ટેનોગ્રાફી સ્કિલ ટેસ્ટ હોય છે. | |
| `listWord` | Lists: {n} | યાદીઓ: {n} | |
