# ಕನ್ನಡ (kn) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/kn.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/kn.js)

| 키 | English | ಕನ್ನಡ | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | ಸರ್ಕಾರಿ ಜಾಬ್ ಅಲರ್ಟ್ಸ್ | |
| `tabHome` | Home | ಹೋಮ್ | |
| `tabMore` | More | ಇನ್ನಷ್ಟು | |
| `loading` | Loading… | ಲೋಡ್ ಆಗುತ್ತಿದೆ… | |
| `homeComing` | Exam dates in the next 30 days | ಮುಂದಿನ 30 ದಿನಗಳಲ್ಲಿ ಪರೀಕ್ಷೆ ದಿನಾಂಕಗಳು | |
| `homeLatest` | Latest updates | ಇತ್ತೀಚಿನ ಅಪ್‌ಡೇಟ್‌ಗಳು | |
| `inDays` | in {n} days | {n} ದಿನಗಳಲ್ಲಿ | |
| `onNow` | on now | ಈಗ ನಡೆಯುತ್ತಿದೆ | |
| `today` | today | ಇಂದು | |
| `theme` | Theme | ಥೀಮ್ | |
| `themeAuto` | Auto | ಆಟೋ | |
| `themeLight` | Light | ಲೈಟ್ | |
| `themeDark` | Dark | ಡಾರ್ಕ್ | |
| `language` | Language | ಭಾಷೆ | |
| `howToApply` | How to apply | ಅರ್ಜಿ ಸಲ್ಲಿಸುವುದು ಹೇಗೆ | |
| `documents` | Keep these documents ready | ಈ ದಾಖಲೆಗಳನ್ನು ಸಿದ್ಧವಾಗಿಟ್ಟುಕೊಳ್ಳಿ | |
| `helplineTitle` | SSC helplines | SSC ಸಹಾಯವಾಣಿ ಸಂಖ್ಯೆಗಳು | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ಫಾರ್ಮ್ ತುಂಬುವಾಗ ಅಥವಾ ಕಂಪ್ಯೂಟರ್ ಆಧಾರಿತ ಪರೀಕ್ಷೆಯಲ್ಲಿ ಸಮಸ್ಯೆ ಎದುರಾದರೆ. | |
| `typingTitle` | Typing / data entry speed practice | ಟೈಪಿಂಗ್ / ಡೇಟಾ ಎಂಟ್ರಿ ಸ್ಪೀಡ್ ಅಭ್ಯಾಸ | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | ಕೆಳಗಿನ ಪ್ಯಾರಾವನ್ನು ಟೈಪ್ ಮಾಡಿ. ನೀವು ಮೊದಲ ಕೀ ಒತ್ತಿದ ಕೂಡಲೇ ಟೈಮರ್ ಶುರುವಾಗುತ್ತದೆ. ಸ್ಪೀಡ್ ಅನ್ನು SSC ಹೇಳುವಂತೆಯೇ ಗಂಟೆಗೆ ಕೀ ಡಿಪ್ರೆಶನ್‌ಗಳಲ್ಲಿ ತೋರಿಸಲಾಗುತ್ತದೆ. | |
| `typingTest` | Standard | ಸ್ಟ್ಯಾಂಡರ್ಡ್ | |
| `typingMinutes` | Time | ಸಮಯ | |
| `typingFull` | Full ({n} min) | ಪೂರ್ಣ ({n} ನಿಮಿಷ) | |
| `typingStart` | Start again | ಮತ್ತೆ ಶುರು ಮಾಡಿ | |
| `typingDone` | Time is up. | ಸಮಯ ಮುಗಿಯಿತು. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | ಗಂಟೆಗೆ {kdph} ಕೀ ಡಿಪ್ರೆಶನ್‌ಗಳು (≈ {wpm} w.p.m.), {accuracy}% ನಿಖರತೆ, ಟೈಪ್ ಮಾಡಿದ {typed}ರಲ್ಲಿ {correct} ಸರಿ | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} ಸ್ಪೀಡ್ ({kdph}/ಗಂಟೆ) ತಲುಪಿದ್ದೀರಿ. | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} ಸ್ಪೀಡ್‌ಗಿಂತ ({kdph}/ಗಂಟೆ) ಕಡಿಮೆ: ಅಭ್ಯಾಸ ಮುಂದುವರಿಸಿ. | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | ಇದು ಅಭ್ಯಾಸಕ್ಕೆ ಮಾತ್ರ: SSC ತನ್ನದೇ ಮೌಲ್ಯಮಾಪನ ಮಾರ್ಗಸೂಚಿಗಳ ಪ್ರಕಾರ ತಪ್ಪುಗಳನ್ನು ಲೆಕ್ಕ ಹಾಕುತ್ತದೆ. ಹಿಂದಿ ಟೈಪಿಂಗ್ ಅಭ್ಯಾಸ ಇಲ್ಲಿ ಇಲ್ಲ. | |
| `faqTitle` | Frequently asked | ಪದೇ ಪದೇ ಕೇಳುವ ಪ್ರಶ್ನೆಗಳು | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}ಗೆ ಅರ್ಜಿ ಸಲ್ಲಿಸಲು ಕೊನೆಯ ದಿನಾಂಕ ಯಾವುದು? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | ಅರ್ಜಿ ಸಲ್ಲಿಸಲು ಕೊನೆಯ ದಿನಾಂಕ {date}; ಶುಲ್ಕ ಪಾವತಿ {fee}ಕ್ಕೆ ಮುಕ್ತಾಯವಾಗುತ್ತದೆ. | |
| `faqAge` | What is the age limit for {exam}? | {exam}ಗೆ ವಯೋಮಿತಿ ಎಷ್ಟು? | |
| `faqFee` | What is the application fee? | ಅರ್ಜಿ ಶುಲ್ಕ ಎಷ್ಟು? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | ಸಾಮಾನ್ಯ, OBC ಮತ್ತು EWS ಅಭ್ಯರ್ಥಿಗಳಿಗೆ ₹{amount}. {exempt} ಶುಲ್ಕ ಕಟ್ಟಬೇಕಿಲ್ಲ. | |
| `faqSalary` | What is the salary? | ಸಂಬಳ ಎಷ್ಟು? | |
| `faqExam` | When is the {exam} exam? | {exam} ಪರೀಕ್ಷೆ ಯಾವಾಗ? | |
| `faqVacancy` | How many vacancies are there? | ಎಷ್ಟು ಹುದ್ದೆಗಳು ಖಾಲಿ ಇವೆ? | |
| `faqQual` | What qualification is needed? | ಯಾವ ವಿದ್ಯಾರ್ಹತೆ ಬೇಕು? | |
| `aboutTitle` | About this app | ಈ ಆ್ಯಪ್ ಬಗ್ಗೆ | |
| `privacyPolicy` | Privacy policy | ಗೌಪ್ಯತಾ ನೀತಿ | |
| `termsOfUse` | Terms of use | ನಿಯಮಗಳು | |
| `contactUs` | Contact us | ಸಂಪರ್ಕಿಸಿ | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ಭಾರತದಲ್ಲಿ ಸರ್ಕಾರಿ ಉದ್ಯೋಗಗಳಿಗೆ ತಯಾರಿ ನಡೆಸುವವರಿಗಾಗಿ ಉಚಿತ, ಅನಧಿಕೃತ ಸಹಾಯಕ ಆ್ಯಪ್. ನೋಟಿಸ್‌ಗಳು, ದಿನಾಂಕಗಳು ಮತ್ತು ನಿಯಮಗಳನ್ನು ನೇಮಕಾತಿ ಸಂಸ್ಥೆಗಳ ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್‌ಗಳು ಮತ್ತು ಅಧಿಸೂಚನೆಗಳಿಂದ, ಉಲ್ಲೇಖಗಳೊಂದಿಗೆ ತೆಗೆದುಕೊಳ್ಳಲಾಗಿದೆ. ಸೈನ್-ಅಪ್ ಬೇಕಿಲ್ಲ, ಯಾವುದೇ ವೈಯಕ್ತಿಕ ಮಾಹಿತಿ ಸಂಗ್ರಹಿಸುವುದಿಲ್ಲ: ನಿಮ್ಮ ವಿವರಗಳು ಮತ್ತು ರೋಲ್ ನಂಬರ್ ಈ ಫೋನ್‌ನಲ್ಲೇ ಇರುತ್ತವೆ. | |
| `sourceRef` | Source: {ref} | ಮೂಲ: {ref} | |
| `toastSaved` | Saved | ಸೇವ್ ಆಯಿತು | |
| `toastRemoved` | Removed | ತೆಗೆದುಹಾಕಲಾಗಿದೆ | |
| `toastFollow` | You follow this exam | ನೀವು ಈ ಪರೀಕ್ಷೆಯನ್ನು ಫಾಲೋ ಮಾಡುತ್ತಿದ್ದೀರಿ | |
| `toastUnfollow` | Unfollowed | ಅನ್‌ಫಾಲೋ ಮಾಡಲಾಗಿದೆ | |
| `settings` | Settings | ಸೆಟ್ಟಿಂಗ್‌ಗಳು | |
| `toolsTitle` | Tools | ಟೂಲ್‌ಗಳು | |
| `tabResult` | Result | ಫಲಿತಾಂಶ | |
| `tabForMe` | For me | ನನಗಾಗಿ | |
| `resultTitle` | Check your result by roll number | ರೋಲ್ ನಂಬರ್ ಮೂಲಕ ನಿಮ್ಮ ಫಲಿತಾಂಶ ನೋಡಿ | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | ಕಳೆದ 120 ದಿನಗಳಲ್ಲಿ SSC ಪ್ರಕಟಿಸಿದ ಫಲಿತಾಂಶ ಪಟ್ಟಿಗಳಲ್ಲಿ ಹುಡುಕುತ್ತದೆ, ದೊಡ್ಡ PDFಗಳನ್ನು ಡೌನ್‌ಲೋಡ್ ಮಾಡದೆಯೇ. ಸಣ್ಣ ಫೈಲ್ ಪಡೆಯಲು ನಿಮ್ಮ ರೋಲ್ ನಂಬರ್‌ನ ಕೊನೆಯ ಎರಡು ಅಂಕಿಗಳನ್ನು ಮಾತ್ರ ಬಳಸಲಾಗುತ್ತದೆ. | |
| `rollNo` | Roll number | ರೋಲ್ ನಂಬರ್ | |
| `resultExam` | Result | ಫಲಿತಾಂಶ | |
| `find` | Find | ಹುಡುಕಿ | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | ನಿಮ್ಮ ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್‌ನಲ್ಲಿ ಇರುವಂತೆಯೇ ರೋಲ್ ನಂಬರ್ ನಮೂದಿಸಿ (8-14 ಅಂಕಿಗಳು). | |
| `foundIn` | Found in: {list} | ಇರುವ ಪಟ್ಟಿಗಳು: {list} | |
| `notIn` | Not in: {list} | ಇಲ್ಲದ ಪಟ್ಟಿಗಳು: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | ನಿಮ್ಮ ರೋಲ್ ನಂಬರ್ ಈ ಪಟ್ಟಿಗಳಲ್ಲಿ ಇಲ್ಲ. ಅಷ್ಟು ಮಾತ್ರಕ್ಕೆ ನೀವು ಅರ್ಹತೆ ಪಡೆದಿಲ್ಲ ಎಂದರ್ಥವಲ್ಲ: ಕೆಳಗಿನ ಅಧಿಕೃತ PDFಗಳನ್ನು ನೋಡಿ. | |
| `officialFiles` | Official files of this notice | ಈ ನೋಟಿಸ್‌ನ ಅಧಿಕೃತ ಫೈಲ್‌ಗಳು | |
| `listCount` | {n} roll numbers | {n} ರೋಲ್ ನಂಬರ್‌ಗಳು | |
| `noLists` | No result lists to search right now. | ಸದ್ಯಕ್ಕೆ ಹುಡುಕಲು ಯಾವುದೇ ಫಲಿತಾಂಶ ಪಟ್ಟಿ ಇಲ್ಲ. | |
| `forMeTitle` | Exams that suit you | ನಿಮಗೆ ಸೂಕ್ತವಾದ ಪರೀಕ್ಷೆಗಳು | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | ನಿಮ್ಮ ವಿವರಗಳನ್ನು ಒಮ್ಮೆ ತುಂಬಿ. ನೀವು ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದಾದ ಪ್ರತಿ SSC ಪರೀಕ್ಷೆ, ಅದರ ಸ್ಥಿತಿ ಮತ್ತು ಮುಂದೇನು ಎಂಬುದನ್ನು ನೋಡಬಹುದು. | |
| `forMeEdit` | Edit my details | ನನ್ನ ವಿವರ ಬದಲಿಸಿ | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | ನಿಮ್ಮ ವಿದ್ಯಾರ್ಹತೆಗೆ ಸರಿಹೊಂದುವವು (ನಿಯಮಗಳನ್ನು ಇನ್ನೂ ಸೇರಿಸಿಲ್ಲ: ಅಧಿಸೂಚನೆ ಓದಿ) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | ನಿಮ್ಮ ವಿದ್ಯಾರ್ಹತೆಗೆ ಮುಂಬರುವವು (ತಾತ್ಕಾಲಿಕ) | |
| `saveDetails` | Show my exams | ನನ್ನ ಪರೀಕ್ಷೆಗಳನ್ನು ತೋರಿಸಿ | |
| `whenTitle` | What has happened and what is next | ಏನು ಮುಗಿದಿದೆ, ಮುಂದೇನು | |
| `st_done` | Done | ಮುಗಿದಿದೆ | |
| `st_now` | Now | ಈಗ | |
| `st_next` | Next | ಮುಂದೆ | |
| `st_later` | Later | ನಂತರ | |
| `stage_notice` | Notice published | ಅಧಿಸೂಚನೆ ಪ್ರಕಟ | |
| `stage_apply` | Online application | ಆನ್‌ಲೈನ್ ಅರ್ಜಿ | |
| `stage_admit_card` | Admit card / exam city | ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್ / ಪರೀಕ್ಷಾ ನಗರ | |
| `stage_answer_key` | Answer key | ಆನ್ಸರ್ ಕೀ | |
| `stage_result` | Result | ಫಲಿತಾಂಶ | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ಇನ್ನೂ ದಿನಾಂಕ ಪ್ರಕಟಿಸಿಲ್ಲ | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | ಮೂಲ ವೇತನ ತಿಂಗಳಿಗೆ ₹{from}–{to} (ಲೆವೆಲ್ {l}; ಭತ್ಯೆಗಳು ಹೆಚ್ಚುವರಿ) | |
| `savedLink` | ★ Saved | ★ ಸೇವ್ ಮಾಡಿದವು | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ಇದು ಸರ್ಕಾರಿ ವೆಬ್‌ಸೈಟ್ ಅಲ್ಲ, ಯಾವುದೇ ನೇಮಕಾತಿ ಸಂಸ್ಥೆ ಅಥವಾ ಸರ್ಕಾರದೊಂದಿಗೆ ಸಂಬಂಧ ಹೊಂದಿಲ್ಲ. ಇಲ್ಲಿನ ಎಲ್ಲ ಮಾಹಿತಿ ನೇಮಕಾತಿ ಸಂಸ್ಥೆಗಳ ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್‌ಗಳಿಂದಲೇ ಬರುತ್ತದೆ, ಮತ್ತು ಪ್ರತಿ ಅಪ್‌ಡೇಟ್‌ಗೆ ಅಧಿಕೃತ ಫೈಲ್ ಅಥವಾ ಪುಟದ ಲಿಂಕ್ ಇರುತ್ತದೆ. ಅರ್ಜಿ ಸಲ್ಲಿಸುವ ಮೊದಲು ಯಾವಾಗಲೂ ಅಧಿಕೃತ ಅಧಿಸೂಚನೆ ನೋಡಿ. | |
| `tabExams` | Exams | ಪರೀಕ್ಷೆಗಳು | |
| `filters` | Filters | ಫಿಲ್ಟರ್‌ಗಳು | |
| `kindAll` | All | ಎಲ್ಲಾ | |
| `kindRecruitment` | New jobs | ಹೊಸ ಉದ್ಯೋಗಗಳು | |
| `kindResult` | Results | ಫಲಿತಾಂಶಗಳು | |
| `kindAdmit` | Admit cards | ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್‌ಗಳು | |
| `kindAnswer` | Answer keys | ಆನ್ಸರ್ ಕೀಗಳು | |
| `kindAmendment` | Changes | ಬದಲಾವಣೆಗಳು | |
| `kindVacancy` | Vacancies | ಖಾಲಿ ಹುದ್ದೆಗಳು | |
| `kind_recruitment` | New job | ಹೊಸ ಉದ್ಯೋಗ | |
| `kind_amendment` | Change | ಬದಲಾವಣೆ | |
| `kind_result` | Result | ಫಲಿತಾಂಶ | |
| `kind_answer_key` | Answer key | ಆನ್ಸರ್ ಕೀ | |
| `kind_admit_card` | Admit card | ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್ | |
| `kind_vacancy` | Vacancy | ಖಾಲಿ ಹುದ್ದೆ | |
| `kind_schedule` | Exam dates | ಪರೀಕ್ಷೆ ದಿನಾಂಕಗಳು | |
| `kind_notice` | Notice | ನೋಟಿಸ್ | |
| `search` | Search jobs, exams, results | ಉದ್ಯೋಗ, ಪರೀಕ್ಷೆ, ಫಲಿತಾಂಶ ಹುಡುಕಿ | |
| `allExams` | All exams | ಎಲ್ಲಾ ಪರೀಕ್ಷೆಗಳು | |
| `myExams` | My exams only | ನನ್ನ ಪರೀಕ್ಷೆಗಳು ಮಾತ್ರ | |
| `anyQualification` | Any qualification | ಯಾವುದೇ ವಿದ್ಯಾರ್ಹತೆ | |
| `qual_10` | Class 10 | SSLC / 10ನೇ ತರಗತಿ | |
| `qual_12` | Class 12 | PUC / 12ನೇ ತರಗತಿ | |
| `qual_diploma` | Diploma | ಡಿಪ್ಲೊಮಾ | |
| `qual_graduate` | Bachelor's degree | ಪದವಿ | |
| `qual_postgraduate` | Master's degree | ಸ್ನಾತಕೋತ್ತರ ಪದವಿ | |
| `departmental` | Departmental exams | ಇಲಾಖಾ ಪರೀಕ್ಷೆಗಳು | |
| `updated` | Updated {time} IST | ಅಪ್‌ಡೇಟ್: {time} IST | |
| `newSince` | {n} new since your last visit | ನೀವು ಕೊನೆಯ ಬಾರಿ ನೋಡಿದ ನಂತರ {n} ಹೊಸವು | |
| `cutoffTitle` | Cut-off marks | ಕಟ್-ಆಫ್ ಅಂಕಗಳು | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC ಕಟ್-ಆಫ್‌ಗಳನ್ನು ಫಲಿತಾಂಶದ ವಿವರಣೆಯೊಳಗೆ ಮಾತ್ರ ನೀಡುತ್ತದೆ. ಆ ಸಂಖ್ಯೆಗಳನ್ನು ಇಲ್ಲಿ ನಕಲು ಮಾಡಿಲ್ಲ (ಒಂದು ಅಂಕಿ ತಪ್ಪಾದರೂ ದಾರಿ ತಪ್ಪಿಸುತ್ತದೆ): ಪ್ರತಿ ಲಿಂಕ್ ಕಟ್-ಆಫ್ ವಿವರ ಶುರುವಾಗುವ ಪುಟದಲ್ಲೇ SSC PDF ತೆರೆಯುತ್ತದೆ. | |
| `cutoffFrom` | Open at page {p} | ಪುಟ {p}ರಲ್ಲಿ ತೆರೆಯಿರಿ | |
| `cutoffPages` | mentioned on p. {pages} | ಪುಟ {pages}ರಲ್ಲಿ ಇದೆ | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | ಕಳೆದ 120 ದಿನಗಳ ಫಲಿತಾಂಶ ವಿವರಣೆಗಳಲ್ಲಿ ಕಟ್-ಆಫ್ ಸಿಗಲಿಲ್ಲ. | |
| `alertsTitle` | Alerts on this phone | ಈ ಫೋನ್‌ನಲ್ಲಿ ಅಲರ್ಟ್‌ಗಳು | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | ನೀವು ಫಾಲೋ ಮಾಡುವ ಪರೀಕ್ಷೆಗೆ ಹೊಸ ಸುದ್ದಿ ಬಂದಾಗ ಮತ್ತು ಅರ್ಜಿ ಮುಕ್ತಾಯಕ್ಕೆ ಎರಡು ದಿನ ಮೊದಲು ನೋಟಿಫಿಕೇಶನ್ ಬರುತ್ತದೆ. | |
| `alertsOn` | Turn on alerts | ಅಲರ್ಟ್ ಆನ್ ಮಾಡಿ | |
| `alertsOff` | Turn off alerts | ಅಲರ್ಟ್ ಆಫ್ ಮಾಡಿ | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | ಮೊದಲು ಒಂದು ಪರೀಕ್ಷೆಯನ್ನು ಫಾಲೋ ಮಾಡಿ (ಪರೀಕ್ಷೆಗಳು → ಫಾಲೋ). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} ಪರೀಕ್ಷೆಗಳಿಗೆ ಆನ್ ಆಗಿದೆ. ಯಾವಾಗ ಚೆಕ್ ಮಾಡಬೇಕೆಂದು Chrome ನಿರ್ಧರಿಸುತ್ತದೆ, ನೀವು ಆ್ಯಪ್ ಹೆಚ್ಚಾಗಿ ತೆರೆದರೆ ಹೆಚ್ಚು ಬಾರಿ ಚೆಕ್ ಮಾಡುತ್ತದೆ. ಹಾಗಾಗಿ ಸುದ್ದಿ ಪೋಸ್ಟ್ ಆದ ಕೆಲವು ಗಂಟೆಗಳ ನಂತರ ನಿಮಗೆ ತಲುಪಬಹುದು. | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | ಈ ಸೈಟ್‌ಗೆ ನೋಟಿಫಿಕೇಶನ್‌ಗಳನ್ನು ಬ್ಲಾಕ್ ಮಾಡಲಾಗಿದೆ. ಬ್ರೌಸರ್‌ನ ಸೈಟ್ ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಅವುಗಳಿಗೆ ಅನುಮತಿ ನೀಡಿ. | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | ಈ ಬ್ರೌಸರ್ ಬ್ಯಾಕ್‌ಗ್ರೌಂಡ್‌ನಲ್ಲಿ ಚೆಕ್ ಮಾಡಲು ಆಗುವುದಿಲ್ಲ. Androidನಲ್ಲಿ ಮೊದಲು Chrome ಮೂಲಕ ಈ ಆ್ಯಪ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ. ಇಲ್ಲದಿದ್ದರೆ, ಪ್ರತಿ ಪರೀಕ್ಷೆಯ ಪುಟದಲ್ಲಿರುವ ಕ್ಯಾಲೆಂಡರ್ ರಿಮೈಂಡರ್ ಬಳಸಿ. | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | ನಿಮ್ಮ ಪರೀಕ್ಷೆಗಳಿಗೆ ಇನ್ನೂ {n} ಅಪ್‌ಡೇಟ್‌ಗಳು | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: {n} ದಿನಗಳಲ್ಲಿ ಅರ್ಜಿ ಮುಕ್ತಾಯ | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: ಅರ್ಜಿ ಸಲ್ಲಿಸಲು ಇಂದೇ ಕೊನೆಯ ದಿನ | |
| `installTitle` | Install the app | ಆ್ಯಪ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ | |
| `installBtn` | Install | ಇನ್‌ಸ್ಟಾಲ್ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ಆ್ಯಪ್‌ನಂತೆ ತೆರೆಯುತ್ತದೆ, ಇಂಟರ್ನೆಟ್ ಇಲ್ಲದೆಯೂ ಕೆಲಸ ಮಾಡುತ್ತದೆ, Androidನಲ್ಲಿ ಅಲರ್ಟ್‌ಗಳನ್ನು ಪಡೆಯಬಹುದು. | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhoneನಲ್ಲಿ: Share ಒತ್ತಿ, ನಂತರ "Add to Home Screen" ಆಯ್ಕೆ ಮಾಡಿ. | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ಫಾಲೋ ಮಾಡಲಾಗುತ್ತಿದೆ. ಫೋನ್ ಅಲರ್ಟ್‌ಗಾಗಿ: ಇನ್ನಷ್ಟು → ಅಲರ್ಟ್ ಆನ್ ಮಾಡಿ | |
| `rollPlaceholder` | Roll number on your admit card | ನಿಮ್ಮ ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್‌ನಲ್ಲಿರುವ ರೋಲ್ ನಂಬರ್ | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} ಪರೀಕ್ಷೆಗಳಿಗೆ ಆನ್ ಆಗಿದೆ. SSC ನೋಟಿಸ್‌ಗಳು ಸಾಮಾನ್ಯವಾಗಿ ಪೋಸ್ಟ್ ಆದ ಒಂದು ಗಂಟೆಯೊಳಗೆ ನಿಮಗೆ ತಲುಪುತ್ತವೆ; ಫಲಿತಾಂಶಗಳು ಮತ್ತು ಇತರ ಮಂಡಳಿಗಳ ನೋಟಿಸ್‌ಗಳಿಗೆ 3 ಗಂಟೆಗಳವರೆಗೆ ಬೇಕಾಗಬಹುದು. | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | ಈ ಬ್ರೌಸರ್ ಅಲರ್ಟ್ ತೋರಿಸಲು ಆಗುವುದಿಲ್ಲ. iPhoneನಲ್ಲಿ ಮೊದಲು ಈ ಆ್ಯಪ್ ಅನ್ನು Home Screenಗೆ ಸೇರಿಸಿ (Share → Add to Home Screen), ನಂತರ ಅಲ್ಲಿಂದ ತೆರೆಯಿರಿ. | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | ಅಲರ್ಟ್ ಆನ್ ಆಗಲಿಲ್ಲ. ನಿಮ್ಮ ಕನೆಕ್ಷನ್ ಚೆಕ್ ಮಾಡಿ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ. | |
| `allBoards` | All recruiters | ಎಲ್ಲಾ ನೇಮಕಾತಿ ಸಂಸ್ಥೆಗಳು | |
| `newBadge` | NEW | ಹೊಸದು | |
| `nothing` | Nothing here in the last 120 days. | ಕಳೆದ 120 ದಿನಗಳಲ್ಲಿ ಇಲ್ಲಿ ಏನೂ ಇಲ್ಲ. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | ಲೋಡ್ ಆಗಲಿಲ್ಲ. ನಿಮ್ಮ ಇಂಟರ್ನೆಟ್ ಕನೆಕ್ಷನ್ ಚೆಕ್ ಮಾಡಿ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ. | |
| `offline` | Offline: showing the copy saved on this phone. | ಆಫ್‌ಲೈನ್: ಈ ಫೋನ್‌ನಲ್ಲಿ ಸೇವ್ ಆಗಿರುವ ಪ್ರತಿಯನ್ನು ತೋರಿಸಲಾಗುತ್ತಿದೆ. | |
| `save` | Save | ಸೇವ್ | |
| `saved` | Saved | ಸೇವ್ ಆಗಿದೆ | |
| `share` | Share | ಶೇರ್ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | ಅಧಿಕೃತ ಪುಟ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ ಅಧಿಕೃತ ಲಿಂಕ್: {url} ⏎ {app} ಮೂಲಕ (ಇದು ಸರ್ಕಾರಿ ಸೈಟ್ ಅಲ್ಲ) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ಪ್ರತಿ ಪರೀಕ್ಷೆಗೆ ಇತ್ತೀಚಿನ ಅಧಿಸೂಚನೆ, ದಿನಾಂಕಗಳು, ಶುಲ್ಕ, ವಯೋಮಿತಿ ಮತ್ತು ನಂತರ ಬಂದ ಎಲ್ಲಾ ನೋಟಿಸ್‌ಗಳು. | |
| `follow` | Follow | ಫಾಲೋ | |
| `following` | Following | ಫಾಲೋ ಮಾಡುತ್ತಿದ್ದೀರಿ | |
| `minQual` | Qualification: {q} | ವಿದ್ಯಾರ್ಹತೆ: {q} | |
| `qualUnknown` | Qualification: see the Notice | ವಿದ್ಯಾರ್ಹತೆ: ಅಧಿಸೂಚನೆ ನೋಡಿ | |
| `calendarTitle` | SSC's tentative calendar | SSC ತಾತ್ಕಾಲಿಕ ಕ್ಯಾಲೆಂಡರ್ | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | ಇವು ಯೋಜನೆಗಳು ಮಾತ್ರ, ಅಧಿಸೂಚನೆ ಅಲ್ಲ: ನಿಜವಾದ ದಿನಾಂಕಗಳು ಪ್ರತಿ ಅಧಿಸೂಚನೆಯಲ್ಲಿರುತ್ತವೆ ಮತ್ತು ಬೇರೆಯಾಗಿರಬಹುದು. | |
| `resourcesTitle` | Official resources | ಅಧಿಕೃತ ಸಂಪನ್ಮೂಲಗಳು | |
| `res_syllabus` | Syllabus | ಸಿಲಬಸ್ | |
| `res_vacancy` | Vacancies (tentative / final) | ಖಾಲಿ ಹುದ್ದೆಗಳು (ತಾತ್ಕಾಲಿಕ / ಅಂತಿಮ) | |
| `res_previous_papers` | Previous year papers | ಹಿಂದಿನ ವರ್ಷದ ಪ್ರಶ್ನೆಪತ್ರಿಕೆಗಳು | |
| `res_certificate_formats` | Certificate formats | ಪ್ರಮಾಣಪತ್ರ ನಮೂನೆಗಳು | |
| `res_instructions` | Instructions | ಸೂಚನೆಗಳು | |
| `back` | ← All exams | ← ಎಲ್ಲಾ ಪರೀಕ್ಷೆಗಳು | |
| `latestNotice` | Latest Notice | ಇತ್ತೀಚಿನ ಅಧಿಸೂಚನೆ | |
| `importantDates` | Important dates | ಪ್ರಮುಖ ದಿನಾಂಕಗಳು | |
| `applyWindow` | Apply online | ಆನ್‌ಲೈನ್ ಅರ್ಜಿ | |
| `feeLastDate` | Fee payment last date | ಶುಲ್ಕ ಪಾವತಿ ಕೊನೆಯ ದಿನಾಂಕ | |
| `correctionWindow` | Form correction window | ಅರ್ಜಿ ತಿದ್ದುಪಡಿ ಅವಧಿ | |
| `tentative` | tentative | ತಾತ್ಕಾಲಿಕ | |
| `applicationFee` | Application fee | ಅರ್ಜಿ ಶುಲ್ಕ | |
| `feeGeneral` | General / OBC / EWS | ಸಾಮಾನ್ಯ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ವಿನಾಯಿತಿ (₹0) | |
| `exempt_female` | Women | ಮಹಿಳೆಯರು | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | ಮಾಜಿ ಸೈನಿಕರು | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | ತಿದ್ದುಪಡಿ: ಮೊದಲ ಬಾರಿ ₹{a}, ಎರಡನೇ ಬಾರಿ ₹{b} (ಎಲ್ಲಾ ಅಭ್ಯರ್ಥಿಗಳಿಗೆ) | |
| `ageLimit` | Age limit (as on {date}) | ವಯೋಮಿತಿ ({date}ರಂತೆ) | |
| `ageBand` | {band} years: born {from} to {to} | {band} ವರ್ಷ: {from} ರಿಂದ {to} ನಡುವೆ ಜನಿಸಿದವರು | |
| `relaxation` | Upper age relaxation | ಗರಿಷ್ಠ ವಯೋಮಿತಿ ಸಡಿಲಿಕೆ | |
| `relaxYears` | {n} years | {n} ವರ್ಷ | |
| `notComputed` | Not calculated here: {list} | ಇಲ್ಲಿ ಲೆಕ್ಕ ಹಾಕಿಲ್ಲ: {list} | |
| `vacancies` | Vacancies | ಖಾಲಿ ಹುದ್ದೆಗಳು | |
| `vacancyCount` | About {n} ({tentative}) | ಸುಮಾರು {n} ({tentative}) | |
| `posts` | Posts | ಹುದ್ದೆಗಳು | |
| `importantLinks` | Important links | ಪ್ರಮುಖ ಲಿಂಕ್‌ಗಳು | |
| `applyOnline` | Apply online (ssc.gov.in) | ಆನ್‌ಲೈನ್‌ನಲ್ಲಿ ಅರ್ಜಿ ಸಲ್ಲಿಸಿ (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | ಅಧಿಕೃತ ಅಧಿಸೂಚನೆ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date}ರ ಬದಲಾವಣೆ | |
| `allNotices` | All notices for this exam | ಈ ಪರೀಕ್ಷೆಯ ಎಲ್ಲಾ ನೋಟಿಸ್‌ಗಳು | |
| `addCalendar` | Add dates to my calendar | ದಿನಾಂಕಗಳನ್ನು ನನ್ನ ಕ್ಯಾಲೆಂಡರ್‌ಗೆ ಸೇರಿಸಿ | |
| `checkThis` | Check my eligibility | ನನ್ನ ಅರ್ಹತೆ ಚೆಕ್ ಮಾಡಿ | |
| `status_open` | Applications open till {date} | {date}ರವರೆಗೆ ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದು | |
| `status_upcoming` | Applications open on {date} | ಅರ್ಜಿ {date}ರಂದು ಶುರುವಾಗುತ್ತದೆ | |
| `status_closed` | Applications closed on {date} | ಅರ್ಜಿ {date}ರಂದು ಮುಕ್ತಾಯವಾಗಿದೆ | |
| `daysLeft` | {n} days left | {n} ದಿನ ಬಾಕಿ | |
| `lastDay` | Last day today | ಇಂದೇ ಕೊನೆಯ ದಿನ | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | ಈ ಪರೀಕ್ಷೆಗೆ ಇನ್ನೂ ಅರ್ಹತಾ ನಿಯಮಗಳನ್ನು ಸೇರಿಸಿಲ್ಲ: ಅಧಿಸೂಚನೆ ಓದಿ. | |
| `examLabel` | Exam | ಪರೀಕ್ಷೆ | |
| `dob` | Date of birth (as in Class 10 certificate) | ಜನ್ಮ ದಿನಾಂಕ (SSLC / 10ನೇ ತರಗತಿ ಪ್ರಮಾಣಪತ್ರದಲ್ಲಿರುವಂತೆ) | |
| `category` | Category | ವರ್ಗ | |
| `catUR` | General (UR) | ಸಾಮಾನ್ಯ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (ನಾನ್-ಕ್ರೀಮಿ ಲೇಯರ್) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | ಅತ್ಯುನ್ನತ ವಿದ್ಯಾರ್ಹತೆ | |
| `edu10` | Class 10 | SSLC / 10ನೇ ತರಗತಿ | |
| `edu12` | Class 12 | PUC / 12ನೇ ತರಗತಿ | |
| `eduGraduate` | Bachelor's degree or higher | ಪದವಿ ಅಥವಾ ಅದಕ್ಕಿಂತ ಹೆಚ್ಚು | |
| `gender` | Gender | ಲಿಂಗ | |
| `male` | Male | ಪುರುಷ | |
| `female` | Female | ಮಹಿಳೆ | |
| `other` | Other | ಇತರೆ | |
| `appearing` | Final exam of that level taken, result not out yet | ಆ ಹಂತದ ಅಂತಿಮ ಪರೀಕ್ಷೆ ಬರೆದಿದ್ದೇನೆ, ಫಲಿತಾಂಶ ಇನ್ನೂ ಬಂದಿಲ್ಲ | |
| `pwbd` | Person with benchmark disability (PwBD) | ಬೆಂಚ್‌ಮಾರ್ಕ್ ಅಂಗವೈಕಲ್ಯ ಹೊಂದಿರುವ ವ್ಯಕ್ತಿ (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | ಮಾಜಿ ಸೈನಿಕ, ಕೇಂದ್ರ ಸರ್ಕಾರಿ ನೌಕರ, ಅಥವಾ ವಿಧವೆ/ವಿಚ್ಛೇದಿತ ಮಹಿಳೆ | |
| `check` | Check | ಚೆಕ್ ಮಾಡಿ | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | ನಿಮ್ಮ ಉತ್ತರಗಳು ಈ ಡಿವೈಸ್‌ನಲ್ಲೇ ಇರುತ್ತವೆ. ಎಲ್ಲಿಗೂ ಏನನ್ನೂ ಕಳುಹಿಸುವುದಿಲ್ಲ. | |
| `enterDob` | Enter your date of birth. | ನಿಮ್ಮ ಜನ್ಮ ದಿನಾಂಕ ನಮೂದಿಸಿ. | |
| `closedNote` | Shown so you can prepare for the next cycle. | ಮುಂದಿನ ಸುತ್ತಿಗೆ ತಯಾರಾಗಲು ತೋರಿಸಲಾಗಿದೆ. | |
| `canApply` | {n} can apply | {n} ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದು | |
| `toCheck` | {n} to check | {n} ಚೆಕ್ ಮಾಡಬೇಕು | |
| `notEligible` | {n} not eligible | {n} ಅರ್ಹತೆ ಇಲ್ಲ | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ಶುಲ್ಕ: {fee}. ಅನ್ವಯಿಸಿದ ವಯೋಮಿತಿ ಸಡಿಲಿಕೆ: {n} ವರ್ಷ. ವಯಸ್ಸನ್ನು {date}ರಂತೆ ಲೆಕ್ಕ ಹಾಕಲಾಗುತ್ತದೆ. | |
| `feeNone` | none (exempt) | ಇಲ್ಲ (ವಿನಾಯಿತಿ) | |
| `st_eligible` | You can apply | ನೀವು ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದು | |
| `st_check` | Check one condition | ಒಂದು ಷರತ್ತು ಚೆಕ್ ಮಾಡಿ | |
| `st_ineligible` | Not eligible | ಅರ್ಹತೆ ಇಲ್ಲ | |
| `payLevel` | Pay Level {l} · Group {g} | ಪೇ ಲೆವೆಲ್ {l} · ಗ್ರೂಪ್ {g} | |
| `groupOnly` | Group {g} | ಗ್ರೂಪ್ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | {from} ಮತ್ತು {to} ನಡುವೆ ಜನಿಸಿರಬೇಕು (ನಿಮಗೆ) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | ನಿಯಮಗಳನ್ನು {date}ರ ಅಧಿಸೂಚನೆಯಿಂದ ತೆಗೆದುಕೊಳ್ಳಲಾಗಿದೆ ({checked}ರಂದು ಪರಿಶೀಲಿಸಲಾಗಿದೆ). | |
| `sigTitle` | Signature for SSC forms | SSC ಫಾರ್ಮ್‌ಗಳಿಗೆ ಸಹಿ | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC {min}-{max} KB ಗಾತ್ರದ, ಸುಮಾರು {w} cm × {h} cm ಇರುವ JPEG ಕೇಳುತ್ತದೆ. ಬಿಳಿ ಕಾಗದದ ಮೇಲೆ ಕಪ್ಪು ಶಾಯಿಯಲ್ಲಿ ಸಹಿ ಮಾಡಿ, ಸ್ಪಷ್ಟವಾದ ಫೋಟೋ ತೆಗೆಯಿರಿ. | |
| `sigPick` | Choose signature photo | ಸಹಿಯ ಫೋಟೋ ಆಯ್ಕೆ ಮಾಡಿ | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | ರೆಡಿ: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ಫೈಲ್ ಅನ್ನು {min} ಮತ್ತು {max} KB ನಡುವೆ ತರಲು ಆಗಲಿಲ್ಲ. ಇನ್ನಷ್ಟು ಸ್ಪಷ್ಟ ಫೋಟೋ ಪ್ರಯತ್ನಿಸಿ. | |
| `sigDownload` | Download JPEG | JPEG ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ | |
| `photoTitle` | Your photograph | ನಿಮ್ಮ ಫೋಟೋ | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC ಈಗ ಅಪ್‌ಲೋಡ್ ಮಾಡಿದ ಫೋಟೋ ತೆಗೆದುಕೊಳ್ಳುವುದಿಲ್ಲ: ಅರ್ಜಿ ಫಾರ್ಮ್ ನಿಮ್ಮ ಕ್ಯಾಮೆರಾದಿಂದ ಲೈವ್ ಆಗಿ ಫೋಟೋ ತೆಗೆಯುತ್ತದೆ, ಹಳೆಯ ಫೋಟೋದ ಫೋಟೋ ತೆಗೆದರೆ ಫಾರ್ಮ್ ರಿಜೆಕ್ಟ್ ಆಗುತ್ತದೆ. ಚೆನ್ನಾಗಿ ಬೆಳಕು, ಸಾದಾ ಹಿನ್ನೆಲೆ ಇರಲಿ, ಕ್ಯಾಮೆರಾ ಕಣ್ಣಿನ ಮಟ್ಟದಲ್ಲಿರಲಿ, ಟೋಪಿ, ಮಾಸ್ಕ್ ಅಥವಾ ಕನ್ನಡಕ ಬೇಡ. | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ಮೂಲ: ಅಧಿಸೂಚನೆ ಪ್ಯಾರಾ 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | ವಯಸ್ಸು ಕ್ಯಾಲ್ಕುಲೇಟರ್ | |
| `ageOn` | Age on | ಈ ದಿನಾಂಕಕ್ಕೆ ವಯಸ್ಸು | |
| `ageResult` | {y} years, {m} months, {d} days | {y} ವರ್ಷ, {m} ತಿಂಗಳು, {d} ದಿನ | |
| `ageInvalid` | Date of birth must be before that date. | ಜನ್ಮ ದಿನಾಂಕ ಆ ದಿನಾಂಕಕ್ಕಿಂತ ಮೊದಲು ಇರಬೇಕು. | |
| `docsTitle` | Certificate formats | ಪ್ರಮಾಣಪತ್ರ ನಮೂನೆಗಳು | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | ವರ್ಗ, ಅಂಗವೈಕಲ್ಯ ಮತ್ತು ಮಾಜಿ ಸೈನಿಕ ಪ್ರಮಾಣಪತ್ರಗಳಿಗೆ SSC ನಮೂನೆಗಳು. | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ಇನ್ನೂ ಏನೂ ಸೇವ್ ಮಾಡಿಲ್ಲ. ಯಾವುದೇ ನೋಟಿಸ್‌ನಲ್ಲಿ ಸೇವ್ ಒತ್ತಿ. | |
| `followedExams` | Exams you follow | ನೀವು ಫಾಲೋ ಮಾಡುವ ಪರೀಕ್ಷೆಗಳು | |
| `chooseLanguage` | Choose your language | ನಿಮ್ಮ ಭಾಷೆ ಆಯ್ಕೆ ಮಾಡಿ | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | ನೋಟಿಸ್‌ಗಳ ಶೀರ್ಷಿಕೆಗಳು ನೇಮಕಾತಿ ಸಂಸ್ಥೆ ಪ್ರಕಟಿಸಿದ ಭಾಷೆಯಲ್ಲೇ ಇರುತ್ತವೆ (ಹೆಚ್ಚಾಗಿ ಇಂಗ್ಲಿಷ್ ಅಥವಾ ಹಿಂದಿ). | |
| `qaLatest` | Latest jobs | ಹೊಸ ಉದ್ಯೋಗಗಳು | |
| `qaResults` | Results | ಫಲಿತಾಂಶಗಳು | |
| `qaAdmit` | Admit cards | ಅಡ್ಮಿಟ್ ಕಾರ್ಡ್ | |
| `qaAnswer` | Answer keys | ಆನ್ಸರ್ ಕೀ | |
| `qaEligibility` | Check eligibility | ಅರ್ಹತೆ ಚೆಕ್ | |
| `qaRoll` | Result by roll no. | ರೋಲ್ ನಂ. ಫಲಿತಾಂಶ | |
| `homeSectors` | Jobs by sector | ವಲಯವಾರು ಉದ್ಯೋಗಗಳು | |
| `homeStates` | Jobs by state | ರಾಜ್ಯವಾರು ಉದ್ಯೋಗಗಳು | |
| `homeClosing` | Closing soon | ಶೀಘ್ರದಲ್ಲೇ ಮುಕ್ತಾಯ | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ಪ್ರತಿ ಅಪ್‌ಡೇಟ್ WhatsApp ಅಥವಾ Telegramನಲ್ಲಿ ಪಡೆಯಿರಿ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | ಉಚಿತ. ಪ್ರತಿ ಅಪ್‌ಡೇಟ್‌ಗೆ ಒಂದು ಮೆಸೇಜ್, ಅಧಿಕೃತ ಲಿಂಕ್ ಜೊತೆ. | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | ಅನಧಿಕೃತ · ಪ್ರತಿ ಅಪ್‌ಡೇಟ್‌ಗೆ ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್ ಲಿಂಕ್ ಇರುತ್ತದೆ | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | ಪೊಲೀಸ್ | |
| `sector_defence` | Defence & CAPF | ರಕ್ಷಣೆ & CAPF | |
| `sector_railway` | Railway | ರೈಲ್ವೆ | |
| `sector_bank` | Bank | ಬ್ಯಾಂಕ್ | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | ವಿಜ್ಞಾನ & ಬಾಹ್ಯಾಕಾಶ | |
| `sector_state` | State govt | ರಾಜ್ಯ ಸರ್ಕಾರ | |
| `sector_teaching` | Teaching | ಶಿಕ್ಷಕರ ಹುದ್ದೆ | |
| `state_all` | All India | ಅಖಿಲ ಭಾರತ | |
| `state_UP` | Uttar Pradesh | ಉತ್ತರ ಪ್ರದೇಶ | |
| `state_BR` | Bihar | ಬಿಹಾರ | |
| `state_HR` | Haryana | ಹರಿಯಾಣ | |
| `anySector` | All sectors | ಎಲ್ಲಾ ವಲಯಗಳು | |
| `anyState` | All states | ಎಲ್ಲಾ ರಾಜ್ಯಗಳು | |
| `jobsSector` | {name} jobs | {name} ಉದ್ಯೋಗಗಳು | |
| `jobsState` | Government jobs in {name} | {name} ಸರ್ಕಾರಿ ಉದ್ಯೋಗಗಳು | |
| `jobsKind` | {name}: latest | {name}: ಇತ್ತೀಚಿನವು | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | ಈ ವಲಯದಿಂದ ಇನ್ನೂ ಏನೂ ಇಲ್ಲ. ನೇಮಕಾತಿ ಸಂಸ್ಥೆಯ ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್ ಪರಿಶೀಲಿಸಿದ ನಂತರವೇ ಅದನ್ನು ಸೇರಿಸುತ್ತೇವೆ, ಹಾಗಾಗಿ ಈ ಪಟ್ಟಿ ಬೆಳೆಯುತ್ತದೆ. | |
| `centralJobs` | Central govt (all India) | ಕೇಂದ್ರ ಸರ್ಕಾರ (ಅಖಿಲ ಭಾರತ) | |
| `recruitersTitle` | Recruiters we follow | ನಾವು ಫಾಲೋ ಮಾಡುವ ನೇಮಕಾತಿ ಸಂಸ್ಥೆಗಳು | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | ಅಪ್‌ಡೇಟ್‌ಗಳನ್ನು ಈ ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್‌ಗಳಿಂದಲೇ ಪಡೆಯಲಾಗುತ್ತದೆ. ತೆರೆಯಲು ಯಾವುದಾದರೂ ಒಂದನ್ನು ಒತ್ತಿ. | |
| `moreUpdates` | See all updates | ಎಲ್ಲಾ ಅಪ್‌ಡೇಟ್‌ಗಳನ್ನು ನೋಡಿ | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | ವಯಸ್ಸು ಕಡಿಮೆ: {date}ರಂತೆ ನಿಮಗೆ ಕನಿಷ್ಠ {n} ವರ್ಷ ಆಗಿರಬೇಕು. | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | ವಯೋಮಿತಿ ಮೀರಿದೆ: {date}ರಂತೆ ನಿಮ್ಮ ವಯಸ್ಸು {n} ವರ್ಷಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು (ಸಡಿಲಿಕೆ ಸೇರಿ). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | ಸಾಮಾನ್ಯ ವಯೋಮಿತಿ ಮೀರಿದೆ. ಮಾಜಿ ಸೈನಿಕ / ಸರ್ಕಾರಿ ನೌಕರ / ವಿಧವೆಯರ ಸಡಿಲಿಕೆ ಅನ್ವಯವಾಗಬಹುದು: ಅಧಿಸೂಚನೆ {ref} ನೋಡಿ. | |
| `r_education_graduate` | Needs a bachelor's degree. | ಪದವಿ ಬೇಕು. | |
| `r_education_12` | Needs Class 12 pass. | PUC / 12ನೇ ತರಗತಿ ಪಾಸ್ ಆಗಿರಬೇಕು. | |
| `r_education_10` | Needs Class 10 pass. | SSLC / 10ನೇ ತರಗತಿ ಪಾಸ್ ಆಗಿರಬೇಕು. | |
| `r_result_needed_by` | Your result must be out by {date}. | ನಿಮ್ಮ ಫಲಿತಾಂಶ {date}ರೊಳಗೆ ಬಂದಿರಬೇಕು. | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ಫಲಿತಾಂಶಕ್ಕಾಗಿ ಕಾಯುತ್ತಿರುವ ಅಭ್ಯರ್ಥಿಗಳು ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದು ಎಂದು ಅಧಿಸೂಚನೆಯಲ್ಲಿ ಹೇಳಿಲ್ಲ; {date}ರೊಳಗೆ ನಿಮ್ಮ ಬಳಿ ವಿದ್ಯಾರ್ಹತೆ ಇರಬೇಕು. | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | ಈ ಅಧಿಸೂಚನೆಯಲ್ಲಿ PwBDಗೆ ವಯೋಮಿತಿ ಸಡಿಲಿಕೆ ಇಲ್ಲ: ಅಧಿಸೂಚನೆ ನೋಡಿ. | |
| `r_gender_male` | Only for male candidates. | ಪುರುಷ ಅಭ್ಯರ್ಥಿಗಳಿಗೆ ಮಾತ್ರ. | |
| `r_gender_female` | Only for female candidates. | ಮಹಿಳಾ ಅಭ್ಯರ್ಥಿಗಳಿಗೆ ಮಾತ್ರ. | |
| `r_gender_other` | Only for {g} candidates. | {g} ಅಭ್ಯರ್ಥಿಗಳಿಗೆ ಮಾತ್ರ. | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | ಸೆಕೆಂಡರಿ ಶಾಲಾ ಹಂತದಲ್ಲಿ ಆ ರಾಜ್ಯದ ಅಧಿಕೃತ/ಪ್ರಾದೇಶಿಕ ಭಾಷೆ ಓದಿರಬೇಕು. | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12ನೇ ತರಗತಿಯಲ್ಲಿ ಗಣಿತದಲ್ಲಿ 60% ಇರಬೇಕು, ಅಥವಾ ಪದವಿಯಲ್ಲಿ ಸಂಖ್ಯಾಶಾಸ್ತ್ರ ಒಂದು ವಿಷಯವಾಗಿರಬೇಕು. | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ಪದವಿ ಸಂಖ್ಯಾಶಾಸ್ತ್ರ, ಗಣಿತ, ಅರ್ಥಶಾಸ್ತ್ರ, ಜನಸಂಖ್ಯಾಶಾಸ್ತ್ರ (Demography), ಪಾಪ್ಯುಲೇಶನ್ ಸ್ಟಡೀಸ್, ಆಪರೇಷನ್ಸ್ ರಿಸರ್ಚ್, IT, ಕಂಪ್ಯೂಟರ್ ಸೈನ್ಸ್/ಎಂಜಿನಿಯರಿಂಗ್/ಅಪ್ಲಿಕೇಶನ್ಸ್, ಡೇಟಾ ಸೈನ್ಸ್ ಅಥವಾ AIನಲ್ಲಿ ಇರಬೇಕು. | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ದೈಹಿಕ ಪರೀಕ್ಷೆಯ ದಿನದಂದು ಮಾನ್ಯವಾದ LMV (ಬೈಕ್ ಮತ್ತು ಕಾರ್) ಡ್ರೈವಿಂಗ್ ಲೈಸೆನ್ಸ್ ಇರಬೇಕು. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ಭೌತಶಾಸ್ತ್ರ, ರಸಾಯನಶಾಸ್ತ್ರ ಮತ್ತು ಗಣಿತದೊಂದಿಗೆ B.Sc. ಇರಬೇಕು, ಅಥವಾ 10ನೇ ತರಗತಿ ಜೊತೆಗೆ ಮೆಕ್ಯಾನಿಕಲ್, ಸಿವಿಲ್, ಎಲೆಕ್ಟ್ರಿಕಲ್ ಅಥವಾ ಪಟ್ಟಿಯಲ್ಲಿರುವ ಎಂಜಿನಿಯರಿಂಗ್ ಶಾಖೆಯಲ್ಲಿ 3 ವರ್ಷದ ಡಿಪ್ಲೊಮಾ ಇರಬೇಕು. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | ದೈಹಿಕ ಮತ್ತು ವೈದ್ಯಕೀಯ ಮಾನದಂಡಗಳು ಅನ್ವಯಿಸುತ್ತವೆ (ಅಧಿಸೂಚನೆ ನೋಡಿ). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | ಬಾರ್ಡರ್ ರೋಡ್ಸ್ ಆರ್ಗನೈಸೇಶನ್ ಹುದ್ದೆಗಳು: ಪುರುಷರಿಗೆ ಮಾತ್ರ, ದೈಹಿಕ ಮಾನದಂಡಗಳು ಅನ್ವಯಿಸುತ್ತವೆ. | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | ಕೆಲವು ಕಚೇರಿಗಳಲ್ಲಿ (Consumer Affairs & Food, SSC, Culture) ಈ ಹುದ್ದೆಗೆ ಗಣಿತದೊಂದಿಗೆ 12ನೇ ತರಗತಿ ವಿಜ್ಞಾನ ಬೇಕು. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | ಆಯ್ಕೆ ಪ್ರಕ್ರಿಯೆಯಲ್ಲಿ ಸ್ಟೆನೋಗ್ರಫಿ ಕೌಶಲ ಪರೀಕ್ಷೆಯೂ ಇರುತ್ತದೆ. | |
| `listWord` | Lists: {n} | ಪಟ್ಟಿಗಳು: {n} | |
