# ଓଡ଼ିଆ (or) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/or.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/or.js)

| 키 | English | ଓଡ଼ିଆ | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | ସରକାରୀ ଜବ୍ ଆଲର୍ଟ | |
| `tabHome` | Home | ହୋମ୍ | |
| `tabMore` | More | ଅଧିକ | |
| `loading` | Loading… | ଲୋଡ୍ ହେଉଛି… | |
| `homeComing` | Exam dates in the next 30 days | ଆସନ୍ତା 30 ଦିନର ପରୀକ୍ଷା ତାରିଖ | |
| `homeLatest` | Latest updates | ନୂଆ ଅପଡେଟ୍ | |
| `inDays` | in {n} days | {n} ଦିନରେ | |
| `onNow` | on now | ଏବେ ଚାଲିଛି | |
| `today` | today | ଆଜି | |
| `theme` | Theme | ଥିମ୍ | |
| `themeAuto` | Auto | ଅଟୋ | |
| `themeLight` | Light | ଲାଇଟ୍ | |
| `themeDark` | Dark | ଡାର୍କ | |
| `language` | Language | ଭାଷା | |
| `howToApply` | How to apply | କିପରି ଆବେଦନ କରିବେ | |
| `documents` | Keep these documents ready | ଏହି ଡକ୍ୟୁମେଣ୍ଟ ପ୍ରସ୍ତୁତ ରଖନ୍ତୁ | |
| `helplineTitle` | SSC helplines | SSC ହେଲ୍ପଲାଇନ୍ | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ଫର୍ମ ପୂରଣ କରିବାରେ କିମ୍ବା କମ୍ପ୍ୟୁଟର ଆଧାରିତ ପରୀକ୍ଷାରେ କିଛି ସମସ୍ୟା ହେଲେ। | |
| `typingTitle` | Typing / data entry speed practice | ଟାଇପିଂ / ଡାଟା ଏଣ୍ଟ୍ରି ସ୍ପିଡ୍ ପ୍ରାକ୍ଟିସ୍ | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | ତଳେ ଦିଆଯାଇଥିବା ଅନୁଚ୍ଛେଦଟି ଟାଇପ୍ କରନ୍ତୁ। ପ୍ରଥମ କି' ଦବାଇବା ମାତ୍ରେ ଟାଇମର ଆରମ୍ଭ ହେବ। ସ୍ପିଡ୍ ଘଣ୍ଟା ପ୍ରତି କି-ଷ୍ଟ୍ରୋକ୍ (key depressions per hour)ରେ ଦେଖାଯାଏ, ଯେପରି SSC କହେ। | |
| `typingTest` | Standard | ଷ୍ଟାଣ୍ଡାର୍ଡ | |
| `typingMinutes` | Time | ସମୟ | |
| `typingFull` | Full ({n} min) | ପୂରା ({n} ମିନିଟ୍) | |
| `typingStart` | Start again | ପୁଣି ଆରମ୍ଭ କରନ୍ତୁ | |
| `typingDone` | Time is up. | ସମୟ ସରିଗଲା। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | ଘଣ୍ଟା ପ୍ରତି {kdph} କି-ଷ୍ଟ୍ରୋକ୍ (≈ {wpm} w.p.m.), {accuracy}% ସଠିକତା, {typed} ମଧ୍ୟରୁ {correct} ଠିକ୍ | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} ସ୍ପିଡ୍ ({kdph}/ଘଣ୍ଟା) ପୂରଣ ହେଲା। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} ସ୍ପିଡ୍ ({kdph}/ଘଣ୍ଟା)ଠାରୁ କମ୍: ପ୍ରାକ୍ଟିସ୍ ଜାରି ରଖନ୍ତୁ। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | କେବଳ ପ୍ରାକ୍ଟିସ୍ ପାଇଁ: SSC ନିଜର ମୂଲ୍ୟାୟନ ଗାଇଡଲାଇନ୍ ଅନୁଯାୟୀ ଭୁଲ ଗଣନା କରେ। ହିନ୍ଦୀ ଟାଇପିଂ ପ୍ରାକ୍ଟିସ୍ ଏଠାରେ ନାହିଁ। | |
| `faqTitle` | Frequently asked | ବାରମ୍ବାର ପଚରାଯାଉଥିବା ପ୍ରଶ୍ନ | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} ପାଇଁ ଆବେଦନର ଶେଷ ତାରିଖ କେବେ? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | ଆବେଦନର ଶେଷ ତାରିଖ {date}; ଫି' ପୈଠ {fee} ରେ ବନ୍ଦ ହେବ। | |
| `faqAge` | What is the age limit for {exam}? | {exam} ପାଇଁ ବୟସ ସୀମା କେତେ? | |
| `faqFee` | What is the application fee? | ଆବେଦନ ଫି' କେତେ? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | ଜେନେରାଲ୍, OBC ଓ EWS ପ୍ରାର୍ଥୀଙ୍କ ପାଇଁ ₹{amount}। {exempt} ଙ୍କୁ କୌଣସି ଫି' ଦେବାକୁ ପଡ଼ିବ ନାହିଁ। | |
| `faqSalary` | What is the salary? | ଦରମା କେତେ? | |
| `faqExam` | When is the {exam} exam? | {exam} ପରୀକ୍ଷା କେବେ? | |
| `faqVacancy` | How many vacancies are there? | କେତୋଟି ପଦ ଖାଲି ଅଛି? | |
| `faqQual` | What qualification is needed? | କେଉଁ ଯୋଗ୍ୟତା ଦରକାର? | |
| `aboutTitle` | About this app | ଏହି ଆପ୍ ବିଷୟରେ | |
| `privacyPolicy` | Privacy policy | ଗୋପନୀୟତା ନୀତି | |
| `termsOfUse` | Terms of use | ନିୟମ ଓ ସର୍ତ୍ତାବଳୀ | |
| `contactUs` | Contact us | ଯୋଗାଯୋଗ କରନ୍ତୁ | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ଭାରତରେ ସରକାରୀ ଚାକିରି ପାଇଁ ପ୍ରସ୍ତୁତି କରୁଥିବା ଲୋକଙ୍କ ପାଇଁ ଏକ ମାଗଣା, ଅଣସରକାରୀ ସହାୟକ। ନୋଟିସ୍, ତାରିଖ ଓ ନିୟମ ନିଯୁକ୍ତି ସଂସ୍ଥାଙ୍କ ଅଫିସିଆଲ୍ ୱେବସାଇଟ୍ ଓ ନୋଟିସ୍‌ରୁ ନିଆଯାଇଛି, ରେଫରେନ୍ସ ସହିତ। କୌଣସି ସାଇନ୍-ଅପ୍ ନାହିଁ, କୌଣସି ବ୍ୟକ୍ତିଗତ ତଥ୍ୟ ସଂଗ୍ରହ କରାଯାଏ ନାହିଁ: ଆପଣଙ୍କ ବିବରଣୀ ଓ ରୋଲ୍ ନମ୍ବର ଏହି ଫୋନ୍‌ରେ ହିଁ ରହେ। | |
| `sourceRef` | Source: {ref} | ସୂତ୍ର: {ref} | |
| `toastSaved` | Saved | ସେଭ୍ ହେଲା | |
| `toastRemoved` | Removed | ହଟାଗଲା | |
| `toastFollow` | You follow this exam | ଆପଣ ଏହି ପରୀକ୍ଷାକୁ ଫଲୋ କରୁଛନ୍ତି | |
| `toastUnfollow` | Unfollowed | ଅନଫଲୋ କଲେ | |
| `settings` | Settings | ସେଟିଂସ୍ | |
| `toolsTitle` | Tools | ଟୁଲ୍ସ | |
| `tabResult` | Result | ରେଜଲ୍ଟ | |
| `tabForMe` | For me | ମୋ ପାଇଁ | |
| `resultTitle` | Check your result by roll number | ରୋଲ୍ ନମ୍ବର ଦେଇ ଆପଣଙ୍କ ରେଜଲ୍ଟ ଦେଖନ୍ତୁ | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | ଗତ 120 ଦିନରେ SSC ପ୍ରକାଶ କରିଥିବା ରେଜଲ୍ଟ ତାଲିକାରେ ଖୋଜେ, ବଡ଼ PDF ଡାଉନଲୋଡ୍ ନକରି। ଏକ ଛୋଟ ଫାଇଲ୍ ଆଣିବା ପାଇଁ ଆପଣଙ୍କ ରୋଲ୍ ନମ୍ବରର କେବଳ ଶେଷ ଦୁଇଟି ଅଙ୍କ ବ୍ୟବହାର ହୁଏ। | |
| `rollNo` | Roll number | ରୋଲ୍ ନମ୍ବର | |
| `resultExam` | Result | ରେଜଲ୍ଟ | |
| `find` | Find | ଖୋଜନ୍ତୁ | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | ଆପଣଙ୍କ ଆଡମିଟ୍ କାର୍ଡରେ ଯେପରି ଛପା ହୋଇଛି ସେହିପରି ରୋଲ୍ ନମ୍ବର ଦିଅନ୍ତୁ (8-14 ଅଙ୍କ)। | |
| `foundIn` | Found in: {list} | ଏଥିରେ ମିଳିଲା: {list} | |
| `notIn` | Not in: {list} | ଏଥିରେ ନାହିଁ: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | ଆପଣଙ୍କ ରୋଲ୍ ନମ୍ବର ଏହି ତାଲିକାରେ ନାହିଁ। କେବଳ ଏଥିରୁ ଏହା ବୁଝାଏ ନାହିଁ ଯେ ଆପଣ କ୍ୱାଲିଫାଇ କରିନାହାନ୍ତି: ତଳେ ଥିବା ଅଫିସିଆଲ୍ PDF ନିଶ୍ଚୟ ଦେଖନ୍ତୁ। | |
| `officialFiles` | Official files of this notice | ଏହି ନୋଟିସ୍‌ର ଅଫିସିଆଲ୍ ଫାଇଲ୍ | |
| `listCount` | {n} roll numbers | {n} ରୋଲ୍ ନମ୍ବର | |
| `noLists` | No result lists to search right now. | ଏବେ ଖୋଜିବା ପାଇଁ କୌଣସି ରେଜଲ୍ଟ ତାଲିକା ନାହିଁ। | |
| `forMeTitle` | Exams that suit you | ଆପଣଙ୍କ ପାଇଁ ଉପଯୁକ୍ତ ପରୀକ୍ଷା | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | ଆପଣଙ୍କ ବିବରଣୀ ଥରେ ଭରନ୍ତୁ। ଆପଣ ଆବେଦନ କରିପାରିବେ ଏପରି ପ୍ରତ୍ୟେକ SSC ପରୀକ୍ଷା, ତାହାର ସ୍ଥିତି, ଏବଂ ପରେ କ'ଣ ହେବ ତାହା ଦେଖିବେ। | |
| `forMeEdit` | Edit my details | ମୋ ବିବରଣୀ ବଦଳାନ୍ତୁ | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | ଆପଣଙ୍କ ଯୋଗ୍ୟତା ଅନୁସାରେ (ନିୟମ ଏପର୍ଯ୍ୟନ୍ତ ଯୋଡ଼ାଯାଇନାହିଁ: ନୋଟିସ୍ ପଢ଼ନ୍ତୁ) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | ଆପଣଙ୍କ ଯୋଗ୍ୟତା ପାଇଁ ଆସୁଥିବା ପରୀକ୍ଷା (ସମ୍ଭାବ୍ୟ) | |
| `saveDetails` | Show my exams | ମୋ ପରୀକ୍ଷା ଦେଖାନ୍ତୁ | |
| `whenTitle` | What has happened and what is next | କ'ଣ ହୋଇସାରିଛି ଓ ପରେ କ'ଣ | |
| `st_done` | Done | ହୋଇଗଲା | |
| `st_now` | Now | ଏବେ | |
| `st_next` | Next | ପରବର୍ତ୍ତୀ | |
| `st_later` | Later | ପରେ | |
| `stage_notice` | Notice published | ନୋଟିସ୍ ପ୍ରକାଶିତ | |
| `stage_apply` | Online application | ଅନଲାଇନ୍ ଆବେଦନ | |
| `stage_admit_card` | Admit card / exam city | ଆଡମିଟ୍ କାର୍ଡ / ପରୀକ୍ଷା ସହର | |
| `stage_answer_key` | Answer key | ଆନ୍ସର୍ କି | |
| `stage_result` | Result | ରେଜଲ୍ଟ | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ଏପର୍ଯ୍ୟନ୍ତ ତାରିଖ ଘୋଷଣା କରିନାହିଁ | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | ବେସିକ୍ ପେ ₹{from}–{to} ମାସିକ (ଲେଭେଲ୍ {l}; ଭତ୍ତା ଅଲଗା) | |
| `savedLink` | ★ Saved | ★ ସେଭ୍ କରାଯାଇଛି | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ଏହା ସରକାରୀ ୱେବସାଇଟ୍ ନୁହେଁ ଏବଂ କୌଣସି ନିଯୁକ୍ତି ସଂସ୍ଥା କିମ୍ବା ସରକାର ସହ ଜଡ଼ିତ ନୁହେଁ। ଏଠାରେ ସବୁକିଛି ନିଯୁକ୍ତି ସଂସ୍ଥାଙ୍କ ଅଫିସିଆଲ୍ ୱେବସାଇଟ୍‌ରୁ ଆସେ, ଏବଂ ପ୍ରତ୍ୟେକ ଅପଡେଟ୍ ସହ ଅଫିସିଆଲ୍ ଫାଇଲ୍ କିମ୍ବା ପେଜ୍‌ର ଲିଙ୍କ ଅଛି। ଆବେଦନ କରିବା ପୂର୍ବରୁ ସର୍ବଦା ଅଫିସିଆଲ୍ ନୋଟିସ୍ ଦେଖନ୍ତୁ। | |
| `tabExams` | Exams | ପରୀକ୍ଷା | |
| `filters` | Filters | ଫିଲ୍ଟର୍ | |
| `kindAll` | All | ସବୁ | |
| `kindRecruitment` | New jobs | ନୂଆ ଚାକିରି | |
| `kindResult` | Results | ରେଜଲ୍ଟ | |
| `kindAdmit` | Admit cards | ଆଡମିଟ୍ କାର୍ଡ | |
| `kindAnswer` | Answer keys | ଆନ୍ସର୍ କି | |
| `kindAmendment` | Changes | ସଂଶୋଧନ | |
| `kindVacancy` | Vacancies | ଖାଲି ପଦ | |
| `kind_recruitment` | New job | ନୂଆ ଚାକିରି | |
| `kind_amendment` | Change | ସଂଶୋଧନ | |
| `kind_result` | Result | ରେଜଲ୍ଟ | |
| `kind_answer_key` | Answer key | ଆନ୍ସର୍ କି | |
| `kind_admit_card` | Admit card | ଆଡମିଟ୍ କାର୍ଡ | |
| `kind_vacancy` | Vacancy | ଖାଲି ପଦ | |
| `kind_schedule` | Exam dates | ପରୀକ୍ଷା ତାରିଖ | |
| `kind_notice` | Notice | ନୋଟିସ୍ | |
| `search` | Search jobs, exams, results | ଚାକିରି, ପରୀକ୍ଷା, ରେଜଲ୍ଟ ଖୋଜନ୍ତୁ | |
| `allExams` | All exams | ସବୁ ପରୀକ୍ଷା | |
| `myExams` | My exams only | କେବଳ ମୋ ପରୀକ୍ଷା | |
| `anyQualification` | Any qualification | ଯେକୌଣସି ଯୋଗ୍ୟତା | |
| `qual_10` | Class 10 | ଦଶମ | |
| `qual_12` | Class 12 | ଦ୍ୱାଦଶ (+2) | |
| `qual_diploma` | Diploma | ଡିପ୍ଲୋମା | |
| `qual_graduate` | Bachelor's degree | ଗ୍ରାଜୁଏସନ୍ | |
| `qual_postgraduate` | Master's degree | ପୋଷ୍ଟ ଗ୍ରାଜୁଏସନ୍ | |
| `departmental` | Departmental exams | ବିଭାଗୀୟ ପରୀକ୍ଷା | |
| `updated` | Updated {time} IST | ଅପଡେଟ୍: {time} IST | |
| `newSince` | {n} new since your last visit | ଆପଣଙ୍କ ଗତ ଭିଜିଟ୍ ପରଠାରୁ {n} ନୂଆ | |
| `cutoffTitle` | Cut-off marks | କଟ୍-ଅଫ୍ ମାର୍କ | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC କଟ୍-ଅଫ୍ କେବଳ ରେଜଲ୍ଟ ବିବରଣୀ ଭିତରେ ଦିଏ। ସଂଖ୍ୟାଗୁଡ଼ିକ ଏଠାରେ କପି କରାଯାଇନାହିଁ (ଗୋଟିଏ ଭୁଲ ସଂଖ୍ୟା ମଧ୍ୟ ଭୁଲ ବାଟରେ ନେଇପାରେ): ପ୍ରତ୍ୟେକ ଲିଙ୍କ SSC ର PDF କୁ ସେହି ପେଜ୍‌ରେ ଖୋଲେ ଯେଉଁଠାରୁ କଟ୍-ଅଫ୍ ବିବରଣୀ ଆରମ୍ଭ ହୁଏ। | |
| `cutoffFrom` | Open at page {p} | ପେଜ୍ {p} ରେ ଖୋଲନ୍ତୁ | |
| `cutoffPages` | mentioned on p. {pages} | ପୃଷ୍ଠା {pages} ରେ ଉଲ୍ଲେଖ | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | ଗତ 120 ଦିନର ରେଜଲ୍ଟ ବିବରଣୀରେ କୌଣସି କଟ୍-ଅଫ୍ ମିଳିଲା ନାହିଁ। | |
| `alertsTitle` | Alerts on this phone | ଏହି ଫୋନ୍‌ରେ ଆଲର୍ଟ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | ଆପଣ ଫଲୋ କରୁଥିବା ପରୀକ୍ଷାର କିଛି ଖବର ଆସିଲେ, ଏବଂ ଆବେଦନ ବନ୍ଦ ହେବାର ଦୁଇ ଦିନ ପୂର୍ବରୁ ଏକ ନୋଟିଫିକେସନ୍। | |
| `alertsOn` | Turn on alerts | ଆଲର୍ଟ ଚାଲୁ କରନ୍ତୁ | |
| `alertsOff` | Turn off alerts | ଆଲର୍ଟ ବନ୍ଦ କରନ୍ତୁ | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | ପ୍ରଥମେ ଗୋଟିଏ ପରୀକ୍ଷା ଫଲୋ କରନ୍ତୁ (ପରୀକ୍ଷା → ଫଲୋ)। | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n}ଟି ପରୀକ୍ଷା ପାଇଁ ଚାଲୁ ଅଛି। କେବେ ଯାଞ୍ଚ କରିବ ତାହା Chrome ସ୍ଥିର କରେ, ଆପଣ ଆପ୍ ବାରମ୍ବାର ଖୋଲିଲେ ଅଧିକ ଥର, ତେଣୁ ଖବର ପୋଷ୍ଟ ହେବାର କିଛି ଘଣ୍ଟା ପରେ ମଧ୍ୟ ଆପଣଙ୍କ ପାଖରେ ପହଞ୍ଚିପାରେ। | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | ଏହି ସାଇଟ୍ ପାଇଁ ନୋଟିଫିକେସନ୍ ବ୍ଲକ୍ ଅଛି। ବ୍ରାଉଜରର ସାଇଟ୍ ସେଟିଂସ୍‌ରେ ଅନୁମତି ଦିଅନ୍ତୁ। | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | ଏହି ବ୍ରାଉଜର ବ୍ୟାକଗ୍ରାଉଣ୍ଡରେ ଯାଞ୍ଚ କରିପାରେ ନାହିଁ। Android ରେ ପ୍ରଥମେ Chrome ରୁ ଏହି ଆପ୍ ଇନଷ୍ଟଲ୍ କରନ୍ତୁ। ନଚେତ୍ ପ୍ରତ୍ୟେକ ପରୀକ୍ଷା ପେଜ୍‌ରେ କ୍ୟାଲେଣ୍ଡର ରିମାଇଣ୍ଡର ବ୍ୟବହାର କରନ୍ତୁ। | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | ଆପଣଙ୍କ ପରୀକ୍ଷାର ଆଉ {n}ଟି ଅପଡେଟ୍ | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: {n} ଦିନରେ ଆବେଦନ ବନ୍ଦ ହେବ | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: ଆଜି ଆବେଦନର ଶେଷ ଦିନ | |
| `installTitle` | Install the app | ଆପ୍ ଇନଷ୍ଟଲ୍ କରନ୍ତୁ | |
| `installBtn` | Install | ଇନଷ୍ଟଲ୍ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ଆପ୍ ପରି ଖୋଲେ, ଇଣ୍ଟରନେଟ୍ ବିନା ଚାଲେ, ଏବଂ Android ରେ ଆଲର୍ଟ ଦେଇପାରେ। | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone ରେ: Share ଦବାନ୍ତୁ, ତା'ପରେ "Add to Home Screen"। | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ଫଲୋ କଲେ। ଫୋନ୍ ଆଲର୍ଟ ପାଇଁ: ଅଧିକ → ଆଲର୍ଟ ଚାଲୁ କରନ୍ତୁ | |
| `rollPlaceholder` | Roll number on your admit card | ଆଡମିଟ୍ କାର୍ଡରେ ଥିବା ରୋଲ୍ ନମ୍ବର | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n}ଟି ପରୀକ୍ଷା ପାଇଁ ଚାଲୁ ଅଛି। SSC ନୋଟିସ୍ ସାଧାରଣତଃ ପୋଷ୍ଟ ହେବାର ଏକ ଘଣ୍ଟା ଭିତରେ ଆପଣଙ୍କ ପାଖରେ ପହଞ୍ଚେ; ରେଜଲ୍ଟ ଓ ଅନ୍ୟ ବୋର୍ଡର ନୋଟିସ୍ ପାଇଁ 3 ଘଣ୍ଟା ପର୍ଯ୍ୟନ୍ତ ଲାଗିପାରେ। | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | ଏହି ବ୍ରାଉଜର ଆଲର୍ଟ ଦେଖାଇପାରେ ନାହିଁ। iPhone ରେ ପ୍ରଥମେ ଏହି ଆପ୍‌କୁ ହୋମ୍ ସ୍କ୍ରିନ୍‌ରେ ଯୋଡ଼ନ୍ତୁ (Share → Add to Home Screen), ତା'ପରେ ସେଠାରୁ ଖୋଲନ୍ତୁ। | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | ଆଲର୍ଟ ଚାଲୁ ହୋଇପାରିଲା ନାହିଁ। ଆପଣଙ୍କ କନେକ୍ସନ୍ ଯାଞ୍ଚ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ। | |
| `allBoards` | All recruiters | ସବୁ ନିଯୁକ୍ତି ସଂସ୍ଥା | |
| `newBadge` | NEW | ନୂଆ | |
| `nothing` | Nothing here in the last 120 days. | ଗତ 120 ଦିନରେ ଏଠାରେ କିଛି ନାହିଁ। | |
| `loadError` | Couldn't load. Check your internet connection and try again. | ଲୋଡ୍ ହୋଇପାରିଲା ନାହିଁ। ଆପଣଙ୍କ ଇଣ୍ଟରନେଟ୍ କନେକ୍ସନ୍ ଯାଞ୍ଚ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ। | |
| `offline` | Offline: showing the copy saved on this phone. | ଅଫଲାଇନ୍: ଏହି ଫୋନ୍‌ରେ ସେଭ୍ ଥିବା କପି ଦେଖାଯାଉଛି। | |
| `save` | Save | ସେଭ୍ କରନ୍ତୁ | |
| `saved` | Saved | ସେଭ୍ ହୋଇଛି | |
| `share` | Share | ସେୟାର୍ କରନ୍ତୁ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | ଅଫିସିଆଲ୍ ପେଜ୍ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ ଅଫିସିଆଲ୍ ଲିଙ୍କ: {url} ⏎ {app} ମାଧ୍ୟମରେ (ସରକାରୀ ସାଇଟ୍ ନୁହେଁ) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ପ୍ରତ୍ୟେକ ପରୀକ୍ଷା, ତାହାର ନୂଆ ନୋଟିସ୍, ତାରିଖ, ଫି', ବୟସ ସୀମା ଓ ତା' ପରର ସବୁ ନୋଟିସ୍। | |
| `follow` | Follow | ଫଲୋ କରନ୍ତୁ | |
| `following` | Following | ଫଲୋ କରୁଛନ୍ତି | |
| `minQual` | Qualification: {q} | ଯୋଗ୍ୟତା: {q} | |
| `qualUnknown` | Qualification: see the Notice | ଯୋଗ୍ୟତା: ନୋଟିସ୍ ଦେଖନ୍ତୁ | |
| `calendarTitle` | SSC's tentative calendar | SSC ର ସମ୍ଭାବ୍ୟ କ୍ୟାଲେଣ୍ଡର | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | ଏହା ଯୋଜନା, ନୋଟିସ୍ ନୁହେଁ: ପ୍ରକୃତ ତାରିଖ ପ୍ରତ୍ୟେକ ନୋଟିସ୍‌ରେ ଥାଏ ଏବଂ ଅଲଗା ହୋଇପାରେ। | |
| `resourcesTitle` | Official resources | ଅଫିସିଆଲ୍ ରିସୋର୍ସ | |
| `res_syllabus` | Syllabus | ସିଲାବସ୍ | |
| `res_vacancy` | Vacancies (tentative / final) | ଖାଲି ପଦ (ସମ୍ଭାବ୍ୟ / ଚୂଡ଼ାନ୍ତ) | |
| `res_previous_papers` | Previous year papers | ପୂର୍ବ ବର୍ଷର ପ୍ରଶ୍ନପତ୍ର | |
| `res_certificate_formats` | Certificate formats | ସାର୍ଟିଫିକେଟ୍ ଫର୍ମାଟ୍ | |
| `res_instructions` | Instructions | ନିର୍ଦ୍ଦେଶାବଳୀ | |
| `back` | ← All exams | ← ସବୁ ପରୀକ୍ଷା | |
| `latestNotice` | Latest Notice | ନୂଆ ନୋଟିସ୍ | |
| `importantDates` | Important dates | ଗୁରୁତ୍ୱପୂର୍ଣ୍ଣ ତାରିଖ | |
| `applyWindow` | Apply online | ଅନଲାଇନ୍ ଆବେଦନ | |
| `feeLastDate` | Fee payment last date | ଫି' ପୈଠର ଶେଷ ତାରିଖ | |
| `correctionWindow` | Form correction window | ଫର୍ମ ସଂଶୋଧନ ସମୟ | |
| `tentative` | tentative | ସମ୍ଭାବ୍ୟ | |
| `applicationFee` | Application fee | ଆବେଦନ ଫି' | |
| `feeGeneral` | General / OBC / EWS | ଜେନେରାଲ୍ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ଫି' ଛାଡ଼ (₹0) | |
| `exempt_female` | Women | ମହିଳା | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | ପୂର୍ବତନ ସୈନିକ | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | ସଂଶୋଧନ: ପ୍ରଥମ ଥର ₹{a}, ଦ୍ୱିତୀୟ ଥର ₹{b} (ସବୁ ପ୍ରାର୍ଥୀ) | |
| `ageLimit` | Age limit (as on {date}) | ବୟସ ସୀମା ({date} ସୁଦ୍ଧା) | |
| `ageBand` | {band} years: born {from} to {to} | {band} ବର୍ଷ: ଜନ୍ମ {from} ରୁ {to} ମଧ୍ୟରେ | |
| `relaxation` | Upper age relaxation | ଉପର ବୟସ ସୀମାରେ ଛାଡ଼ | |
| `relaxYears` | {n} years | {n} ବର୍ଷ | |
| `notComputed` | Not calculated here: {list} | ଏଠାରେ ହିସାବ କରାଯାଇନାହିଁ: {list} | |
| `vacancies` | Vacancies | ଖାଲି ପଦ | |
| `vacancyCount` | About {n} ({tentative}) | ପ୍ରାୟ {n} ({tentative}) | |
| `posts` | Posts | ପଦ | |
| `importantLinks` | Important links | ଗୁରୁତ୍ୱପୂର୍ଣ୍ଣ ଲିଙ୍କ | |
| `applyOnline` | Apply online (ssc.gov.in) | ଅନଲାଇନ୍ ଆବେଦନ କରନ୍ତୁ (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | ଅଫିସିଆଲ୍ ନୋଟିସ୍ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} ତାରିଖର ସଂଶୋଧନ | |
| `allNotices` | All notices for this exam | ଏହି ପରୀକ୍ଷାର ସବୁ ନୋଟିସ୍ | |
| `addCalendar` | Add dates to my calendar | ତାରିଖଗୁଡ଼ିକ ମୋ କ୍ୟାଲେଣ୍ଡରରେ ଯୋଡ଼ନ୍ତୁ | |
| `checkThis` | Check my eligibility | ମୋ ଯୋଗ୍ୟତା ଯାଞ୍ଚ କରନ୍ତୁ | |
| `status_open` | Applications open till {date} | {date} ପର୍ଯ୍ୟନ୍ତ ଆବେଦନ ଖୋଲା | |
| `status_upcoming` | Applications open on {date} | {date} ରେ ଆବେଦନ ଖୋଲିବ | |
| `status_closed` | Applications closed on {date} | {date} ରେ ଆବେଦନ ବନ୍ଦ ହୋଇଗଲା | |
| `daysLeft` | {n} days left | {n} ଦିନ ବାକି | |
| `lastDay` | Last day today | ଆଜି ଶେଷ ଦିନ | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | ଏହି ପରୀକ୍ଷାର ଯୋଗ୍ୟତା ନିୟମ ଏପର୍ଯ୍ୟନ୍ତ ଯୋଡ଼ାଯାଇନାହିଁ: ନୋଟିସ୍ ପଢ଼ନ୍ତୁ। | |
| `examLabel` | Exam | ପରୀକ୍ଷା | |
| `dob` | Date of birth (as in Class 10 certificate) | ଜନ୍ମ ତାରିଖ (ଦଶମ ସାର୍ଟିଫିକେଟ୍ ଅନୁସାରେ) | |
| `category` | Category | ବର୍ଗ | |
| `catUR` | General (UR) | ଜେନେରାଲ୍ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (ନନ୍-କ୍ରିମି ଲେୟର୍) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | ସର୍ବୋଚ୍ଚ ଯୋଗ୍ୟତା | |
| `edu10` | Class 10 | ଦଶମ | |
| `edu12` | Class 12 | ଦ୍ୱାଦଶ (+2) | |
| `eduGraduate` | Bachelor's degree or higher | ଗ୍ରାଜୁଏସନ୍ କିମ୍ବା ତା'ଠାରୁ ଅଧିକ | |
| `gender` | Gender | ଲିଙ୍ଗ | |
| `male` | Male | ପୁରୁଷ | |
| `female` | Female | ମହିଳା | |
| `other` | Other | ଅନ୍ୟ | |
| `appearing` | Final exam of that level taken, result not out yet | ସେହି ସ୍ତରର ଫାଇନାଲ୍ ପରୀକ୍ଷା ଦେଇସାରିଛି, ରେଜଲ୍ଟ ଏପର୍ଯ୍ୟନ୍ତ ବାହାରିନାହିଁ | |
| `pwbd` | Person with benchmark disability (PwBD) | ବେଞ୍ଚମାର୍କ ଅକ୍ଷମତା ଥିବା ବ୍ୟକ୍ତି (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | ପୂର୍ବତନ ସୈନିକ, କେନ୍ଦ୍ର ସରକାରୀ କର୍ମଚାରୀ, କିମ୍ବା ବିଧବା/ଛାଡ଼ପତ୍ରପ୍ରାପ୍ତ ମହିଳା | |
| `check` | Check | ଯାଞ୍ଚ କରନ୍ତୁ | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | ଆପଣଙ୍କ ଉତ୍ତର ଏହି ଡିଭାଇସ୍‌ରେ ହିଁ ରହେ। କୌଣସି ଜାଗାକୁ କିଛି ପଠାଯାଏ ନାହିଁ। | |
| `enterDob` | Enter your date of birth. | ଆପଣଙ୍କ ଜନ୍ମ ତାରିଖ ଦିଅନ୍ତୁ। | |
| `closedNote` | Shown so you can prepare for the next cycle. | ଦେଖାଯାଇଛି ଯାହାଦ୍ୱାରା ଆପଣ ପରବର୍ତ୍ତୀ ସାଇକେଲ୍ ପାଇଁ ପ୍ରସ୍ତୁତି କରିପାରିବେ। | |
| `canApply` | {n} can apply | {n}ଟିରେ ଆବେଦନ କରିପାରିବେ | |
| `toCheck` | {n} to check | {n}ଟି ଯାଞ୍ଚ କରିବାକୁ ଅଛି | |
| `notEligible` | {n} not eligible | {n}ଟିରେ ଯୋଗ୍ୟ ନୁହଁନ୍ତି | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ଫି': {fee}। ବୟସରେ ଦିଆଯାଇଥିବା ଛାଡ଼: {n} ବର୍ଷ। ବୟସ {date} ସୁଦ୍ଧା ଗଣାଯାଏ। | |
| `feeNone` | none (exempt) | ନାହିଁ (ଛାଡ଼) | |
| `st_eligible` | You can apply | ଆପଣ ଆବେଦନ କରିପାରିବେ | |
| `st_check` | Check one condition | ଗୋଟିଏ ସର୍ତ୍ତ ଯାଞ୍ଚ କରନ୍ତୁ | |
| `st_ineligible` | Not eligible | ଯୋଗ୍ୟ ନୁହଁନ୍ତି | |
| `payLevel` | Pay Level {l} · Group {g} | ପେ ଲେଭେଲ୍ {l} · ଗ୍ରୁପ୍ {g} | |
| `groupOnly` | Group {g} | ଗ୍ରୁପ୍ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | ଜନ୍ମ {from} ରୁ {to} ମଧ୍ୟରେ (ଆପଣଙ୍କ ପାଇଁ) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | ନିୟମ {date} ତାରିଖର ନୋଟିସ୍‌ରୁ ନିଆଯାଇଛି ({checked} ରେ ଯାଞ୍ଚ ହୋଇଛି)। | |
| `sigTitle` | Signature for SSC forms | SSC ଫର୍ମ ପାଇଁ ଦସ୍ତଖତ | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC {min}-{max} KB ର JPEG ମାଗେ, ପ୍ରାୟ {w} cm × {h} cm। ଧଳା କାଗଜରେ କଳା କାଳିରେ ଦସ୍ତଖତ କରି ଏକ ସ୍ପଷ୍ଟ ଫଟୋ ନିଅନ୍ତୁ। | |
| `sigPick` | Choose signature photo | ଦସ୍ତଖତ ଫଟୋ ବାଛନ୍ତୁ | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | ପ୍ରସ୍ତୁତ: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | {min} ଓ {max} KB ମଧ୍ୟରେ ଫାଇଲ୍ ତିଆରି ହୋଇପାରିଲା ନାହିଁ। ଆହୁରି ସ୍ପଷ୍ଟ ଫଟୋ ଚେଷ୍ଟା କରନ୍ତୁ। | |
| `sigDownload` | Download JPEG | JPEG ଡାଉନଲୋଡ୍ କରନ୍ତୁ | |
| `photoTitle` | Your photograph | ଆପଣଙ୍କ ଫଟୋ | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC ଆଉ ଅପଲୋଡ୍ କରାଯାଇଥିବା ଫଟୋ ନିଏ ନାହିଁ: ଆବେଦନ ଫର୍ମ ଆପଣଙ୍କ କ୍ୟାମେରାରେ ଲାଇଭ୍ ଫଟୋ ନିଏ, ଏବଂ ପୁରୁଣା ଫଟୋର ଫଟୋ ଉଠାଇଲେ ଫର୍ମ ରିଜେକ୍ଟ ହୋଇଯାଏ। ଭଲ ଆଲୋକ, ସାଦା ପୃଷ୍ଠଭୂମି, କ୍ୟାମେରା ଆଖି ସ୍ତରରେ ରଖନ୍ତୁ; ଟୋପି, ମାସ୍କ କିମ୍ବା ଚଷମା ନଥିବ। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ସୂତ୍ର: ନୋଟିସ୍ ଅନୁଚ୍ଛେଦ 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | ବୟସ କ୍ୟାଲକୁଲେଟର | |
| `ageOn` | Age on | ଏହି ତାରିଖରେ ବୟସ | |
| `ageResult` | {y} years, {m} months, {d} days | {y} ବର୍ଷ, {m} ମାସ, {d} ଦିନ | |
| `ageInvalid` | Date of birth must be before that date. | ଜନ୍ମ ତାରିଖ ସେହି ତାରିଖ ପୂର୍ବରୁ ହେବା ଦରକାର। | |
| `docsTitle` | Certificate formats | ସାର୍ଟିଫିକେଟ୍ ଫର୍ମାଟ୍ | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | ବର୍ଗ, ଅକ୍ଷମତା ଓ ପୂର୍ବତନ ସୈନିକ ସାର୍ଟିଫିକେଟ୍ ପାଇଁ SSC ର ଫର୍ମାଟ୍। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ଏପର୍ଯ୍ୟନ୍ତ କିଛି ସେଭ୍ ହୋଇନାହିଁ। ଯେକୌଣସି ନୋଟିସ୍‌ରେ ସେଭ୍ ଦବାନ୍ତୁ। | |
| `followedExams` | Exams you follow | ଆପଣ ଫଲୋ କରୁଥିବା ପରୀକ୍ଷା | |
| `chooseLanguage` | Choose your language | ଆପଣଙ୍କ ଭାଷା ବାଛନ୍ତୁ | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | ନୋଟିସ୍ ଶୀର୍ଷକ ସେହି ଭାଷାରେ ରହେ ଯେଉଁ ଭାଷାରେ ନିଯୁକ୍ତି ସଂସ୍ଥା ପ୍ରକାଶ କରେ (ଅଧିକାଂଶ ଇଂରାଜୀ କିମ୍ବା ହିନ୍ଦୀ)। | |
| `qaLatest` | Latest jobs | ନୂଆ ଚାକିରି | |
| `qaResults` | Results | ରେଜଲ୍ଟ | |
| `qaAdmit` | Admit cards | ଆଡମିଟ୍ କାର୍ଡ | |
| `qaAnswer` | Answer keys | ଆନ୍ସର୍ କି | |
| `qaEligibility` | Check eligibility | ଯୋଗ୍ୟତା ଯାଞ୍ଚ | |
| `qaRoll` | Result by roll no. | ରୋଲ୍ ନଂ. ରେଜଲ୍ଟ | |
| `homeSectors` | Jobs by sector | ସେକ୍ଟର ଅନୁସାରେ ଚାକିରି | |
| `homeStates` | Jobs by state | ରାଜ୍ୟ ଅନୁସାରେ ଚାକିରି | |
| `homeClosing` | Closing soon | ଶୀଘ୍ର ବନ୍ଦ ହେବ | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ପ୍ରତ୍ୟେକ ଅପଡେଟ୍ WhatsApp କିମ୍ବା Telegram ରେ ପାଆନ୍ତୁ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | ମାଗଣା। ପ୍ରତ୍ୟେକ ଅପଡେଟ୍ ପାଇଁ ଗୋଟିଏ ମେସେଜ୍, ଅଫିସିଆଲ୍ ଲିଙ୍କ ସହିତ। | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | ଅଣସରକାରୀ · ପ୍ରତ୍ୟେକ ଅପଡେଟ୍ ସହ ଅଫିସିଆଲ୍ ୱେବସାଇଟ୍ ଲିଙ୍କ | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | ପୋଲିସ୍ | |
| `sector_defence` | Defence & CAPF | ଡିଫେନ୍ସ ଓ CAPF | |
| `sector_railway` | Railway | ରେଳବାଇ | |
| `sector_bank` | Bank | ବ୍ୟାଙ୍କ | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | ବିଜ୍ଞାନ ଓ ମହାକାଶ | |
| `sector_state` | State govt | ରାଜ୍ୟ ସରକାର | |
| `sector_teaching` | Teaching | ଶିକ୍ଷକତା | |
| `state_all` | All India | ସର୍ବଭାରତୀୟ | |
| `state_UP` | Uttar Pradesh | ଉତ୍ତର ପ୍ରଦେଶ | |
| `state_BR` | Bihar | ବିହାର | |
| `state_HR` | Haryana | ହରିୟାଣା | |
| `anySector` | All sectors | ସବୁ ସେକ୍ଟର | |
| `anyState` | All states | ସବୁ ରାଜ୍ୟ | |
| `jobsSector` | {name} jobs | {name} ଚାକିରି | |
| `jobsState` | Government jobs in {name} | {name}ରେ ସରକାରୀ ଚାକିରି | |
| `jobsKind` | {name}: latest | {name}: ନୂଆ | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | ଏହି ସେକ୍ଟରରୁ ଏପର୍ଯ୍ୟନ୍ତ କିଛି ନାହିଁ। ଆମେ କୌଣସି ନିଯୁକ୍ତି ସଂସ୍ଥାକୁ ତାହାର ଅଫିସିଆଲ୍ ୱେବସାଇଟ୍ ଯାଞ୍ଚ କରିବା ପରେ ହିଁ ଯୋଡ଼ୁ, ତେଣୁ ଏହି ତାଲିକା ବଢ଼ିବ। | |
| `centralJobs` | Central govt (all India) | କେନ୍ଦ୍ର ସରକାର (ସର୍ବଭାରତୀୟ) | |
| `recruitersTitle` | Recruiters we follow | ଆମେ ଫଲୋ କରୁଥିବା ନିଯୁକ୍ତି ସଂସ୍ଥା | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | ଅପଡେଟ୍ ଏହି ଅଫିସିଆଲ୍ ୱେବସାଇଟ୍‌ରୁ ନିଆଯାଏ। ଖୋଲିବା ପାଇଁ ଗୋଟିଏରେ ଟ୍ୟାପ୍ କରନ୍ତୁ। | |
| `moreUpdates` | See all updates | ସବୁ ଅପଡେଟ୍ ଦେଖନ୍ତୁ | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | ବୟସ କମ୍: {date} ସୁଦ୍ଧା ଆପଣଙ୍କ ବୟସ ଅତି କମରେ {n} ହେବା ଦରକାର। | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | ବୟସ ସୀମାଠାରୁ ଅଧିକ: {date} ସୁଦ୍ଧା ଆପଣଙ୍କ ବୟସ {n} ରୁ କମ୍ ହେବା ଦରକାର (ଛାଡ଼ ସହିତ)। | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | ସାଧାରଣ ବୟସ ସୀମାଠାରୁ ଅଧିକ। ପୂର୍ବତନ ସୈନିକ / ସରକାରୀ କର୍ମଚାରୀ / ବିଧବା ଛାଡ଼ରେ ହୋଇପାରେ: ନୋଟିସ୍ {ref} ଦେଖନ୍ତୁ। | |
| `r_education_graduate` | Needs a bachelor's degree. | ଗ୍ରାଜୁଏସନ୍ ଡିଗ୍ରୀ ଦରକାର। | |
| `r_education_12` | Needs Class 12 pass. | ଦ୍ୱାଦଶ (+2) ପାସ୍ ଦରକାର। | |
| `r_education_10` | Needs Class 10 pass. | ଦଶମ ପାସ୍ ଦରକାର। | |
| `r_result_needed_by` | Your result must be out by {date}. | ଆପଣଙ୍କ ରେଜଲ୍ଟ {date} ସୁଦ୍ଧା ବାହାରିବା ଦରକାର। | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ରେଜଲ୍ଟ ଅପେକ୍ଷାରେ ଥିବା ପ୍ରାର୍ଥୀ ଆବେଦନ କରିପାରିବେ ବୋଲି ନୋଟିସ୍‌ରେ କୁହାଯାଇନାହିଁ; {date} ସୁଦ୍ଧା ଆପଣଙ୍କ ପାଖରେ ଯୋଗ୍ୟତା ଥିବା ଦରକାର। | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | ଏହି ନୋଟିସ୍‌ରେ PwBD ପାଇଁ କୌଣସି ବୟସ ଛାଡ଼ ନାହିଁ: ନୋଟିସ୍ ଯାଞ୍ଚ କରନ୍ତୁ। | |
| `r_gender_male` | Only for male candidates. | କେବଳ ପୁରୁଷ ପ୍ରାର୍ଥୀଙ୍କ ପାଇଁ। | |
| `r_gender_female` | Only for female candidates. | କେବଳ ମହିଳା ପ୍ରାର୍ଥୀଙ୍କ ପାଇଁ। | |
| `r_gender_other` | Only for {g} candidates. | କେବଳ {g} ପ୍ରାର୍ଥୀଙ୍କ ପାଇଁ। | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | ମାଧ୍ୟମିକ ବିଦ୍ୟାଳୟ ସ୍ତରରେ ରାଜ୍ୟର ସରକାରୀ/ଆଞ୍ଚଳିକ ଭାଷା ଥିବା ଦରକାର। | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | ଦ୍ୱାଦଶରେ ଗଣିତରେ 60% କିମ୍ବା ଡିଗ୍ରୀରେ ପରିସଂଖ୍ୟାନ (Statistics) ଏକ ବିଷୟ ଭାବେ ଦରକାର। | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ଡିଗ୍ରୀ ପରିସଂଖ୍ୟାନ, ଗଣିତ, ଅର୍ଥନୀତି, ଡେମୋଗ୍ରାଫି, ପପୁଲେସନ୍ ଷ୍ଟଡିଜ୍, ଅପରେସନ୍ସ ରିସର୍ଚ୍ଚ, IT, କମ୍ପ୍ୟୁଟର ସାଇନ୍ସ/ଇଞ୍ଜିନିୟରିଂ/ଆପ୍ଲିକେସନ୍ସ, ଡାଟା ସାଇନ୍ସ କିମ୍ବା AI ରେ ହେବା ଦରକାର। | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ଫିଜିକାଲ୍ ଟେଷ୍ଟ ଦିନ ଏକ ବୈଧ LMV (ମୋଟରସାଇକେଲ ଓ କାର୍) ଡ୍ରାଇଭିଂ ଲାଇସେନ୍ସ ଦରକାର। | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ପଦାର୍ଥ ବିଜ୍ଞାନ, ରସାୟନ ବିଜ୍ଞାନ ଓ ଗଣିତ ସହ B.Sc. ଦରକାର, କିମ୍ବା ଦଶମ ସହ ମେକାନିକାଲ୍, ସିଭିଲ୍, ଇଲେକ୍ଟ୍ରିକାଲ୍ କିମ୍ବା ତାଲିକାଭୁକ୍ତ ଇଞ୍ଜିନିୟରିଂ ଶାଖାରେ 3 ବର୍ଷର ଡିପ୍ଲୋମା। | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | ଫିଜିକାଲ୍ ଓ ମେଡିକାଲ୍ ମାନଦଣ୍ଡ ଲାଗୁ (ନୋଟିସ୍ ଦେଖନ୍ତୁ)। | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | ବର୍ଡର ରୋଡ୍ସ ଅର୍ଗାନାଇଜେସନ୍ ଖାଲି ପଦ: କେବଳ ପୁରୁଷ, ଫିଜିକାଲ୍ ମାନଦଣ୍ଡ ଲାଗୁ। | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | କିଛି ଅଫିସରେ (କନଜ୍ୟୁମର ଆଫେୟାର୍ସ ଓ ଫୁଡ୍, SSC, ସଂସ୍କୃତି) ଏହି ପଦ ପାଇଁ ଗଣିତ ସହ ଦ୍ୱାଦଶ ବିଜ୍ଞାନ ଦରକାର। | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | ଚୟନ ପ୍ରକ୍ରିୟାରେ ଷ୍ଟେନୋଗ୍ରାଫି ସ୍କିଲ୍ ଟେଷ୍ଟ ମଧ୍ୟ ଅଛି। | |
| `listWord` | Lists: {n} | ତାଲିକା: {n} | |
