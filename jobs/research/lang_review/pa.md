# ਪੰਜਾਬੀ (pa) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/pa.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/pa.js)

| 키 | English | ਪੰਜਾਬੀ | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | ਸਰਕਾਰੀ ਜੌਬ ਅਲਰਟ | |
| `tabHome` | Home | ਹੋਮ | |
| `tabMore` | More | ਹੋਰ | |
| `loading` | Loading… | ਲੋਡ ਹੋ ਰਿਹਾ ਹੈ… | |
| `homeComing` | Exam dates in the next 30 days | ਅਗਲੇ 30 ਦਿਨਾਂ ਦੀਆਂ ਪ੍ਰੀਖਿਆ ਤਾਰੀਖਾਂ | |
| `homeLatest` | Latest updates | ਤਾਜ਼ਾ ਅਪਡੇਟ | |
| `inDays` | in {n} days | {n} ਦਿਨਾਂ ਵਿੱਚ | |
| `onNow` | on now | ਹੁਣ ਚੱਲ ਰਿਹਾ ਹੈ | |
| `today` | today | ਅੱਜ | |
| `theme` | Theme | ਥੀਮ | |
| `themeAuto` | Auto | ਆਟੋ | |
| `themeLight` | Light | ਲਾਈਟ | |
| `themeDark` | Dark | ਡਾਰਕ | |
| `language` | Language | ਭਾਸ਼ਾ | |
| `howToApply` | How to apply | ਅਪਲਾਈ ਕਿਵੇਂ ਕਰੀਏ | |
| `documents` | Keep these documents ready | ਇਹ ਦਸਤਾਵੇਜ਼ ਤਿਆਰ ਰੱਖੋ | |
| `helplineTitle` | SSC helplines | SSC ਹੈਲਪਲਾਈਨ | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ਫਾਰਮ ਭਰਨ ਵਿੱਚ ਜਾਂ ਕੰਪਿਊਟਰ ਅਧਾਰਤ ਪ੍ਰੀਖਿਆ ਵਿੱਚ ਕੋਈ ਦਿੱਕਤ ਹੋਵੇ ਤਾਂ। | |
| `typingTitle` | Typing / data entry speed practice | ਟਾਈਪਿੰਗ / ਡਾਟਾ ਐਂਟਰੀ ਸਪੀਡ ਦੀ ਪ੍ਰੈਕਟਿਸ | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | ਹੇਠਾਂ ਦਿੱਤਾ ਪੈਰਾ ਟਾਈਪ ਕਰੋ। ਪਹਿਲਾ ਬਟਨ ਦਬਾਉਂਦੇ ਹੀ ਟਾਈਮਰ ਸ਼ੁਰੂ ਹੋ ਜਾਵੇਗਾ। ਸਪੀਡ ਪ੍ਰਤੀ ਘੰਟਾ ਕੀ-ਸਟ੍ਰੋਕ (key depressions per hour) ਵਿੱਚ ਦਿਖਾਈ ਜਾਂਦੀ ਹੈ, ਜਿਵੇਂ SSC ਦੱਸਦਾ ਹੈ। | |
| `typingTest` | Standard | ਸਟੈਂਡਰਡ | |
| `typingMinutes` | Time | ਸਮਾਂ | |
| `typingFull` | Full ({n} min) | ਪੂਰਾ ({n} ਮਿੰਟ) | |
| `typingStart` | Start again | ਮੁੜ ਸ਼ੁਰੂ ਕਰੋ | |
| `typingDone` | Time is up. | ਸਮਾਂ ਖ਼ਤਮ। | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | ਪ੍ਰਤੀ ਘੰਟਾ {kdph} ਕੀ-ਸਟ੍ਰੋਕ (≈ {wpm} w.p.m.), {accuracy}% ਸ਼ੁੱਧਤਾ, {typed} ਵਿੱਚੋਂ {correct} ਸਹੀ | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} ਦੀ ਸਪੀਡ ({kdph}/ਘੰਟਾ) ਪੂਰੀ ਹੋ ਗਈ। | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} ਦੀ ਸਪੀਡ ({kdph}/ਘੰਟਾ) ਤੋਂ ਘੱਟ: ਪ੍ਰੈਕਟਿਸ ਜਾਰੀ ਰੱਖੋ। | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | ਸਿਰਫ਼ ਪ੍ਰੈਕਟਿਸ ਲਈ: SSC ਗਲਤੀਆਂ ਨੂੰ ਆਪਣੀਆਂ ਇਵੈਲੂਏਸ਼ਨ ਗਾਈਡਲਾਈਨਾਂ ਅਨੁਸਾਰ ਜਾਂਚਦਾ ਹੈ। ਹਿੰਦੀ ਟਾਈਪਿੰਗ ਦੀ ਪ੍ਰੈਕਟਿਸ ਇੱਥੇ ਸ਼ਾਮਲ ਨਹੀਂ ਹੈ। | |
| `faqTitle` | Frequently asked | ਅਕਸਰ ਪੁੱਛੇ ਜਾਣ ਵਾਲੇ ਸਵਾਲ | |
| `faqLast` | What is the last date to apply for {exam}? | {exam} ਲਈ ਅਪਲਾਈ ਕਰਨ ਦੀ ਆਖਰੀ ਤਾਰੀਖ ਕੀ ਹੈ? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | ਅਪਲਾਈ ਕਰਨ ਦੀ ਆਖਰੀ ਤਾਰੀਖ {date} ਹੈ; ਫੀਸ {fee} ਤੱਕ ਭਰੀ ਜਾ ਸਕਦੀ ਹੈ। | |
| `faqAge` | What is the age limit for {exam}? | {exam} ਲਈ ਉਮਰ ਸੀਮਾ ਕੀ ਹੈ? | |
| `faqFee` | What is the application fee? | ਅਰਜ਼ੀ ਫੀਸ ਕਿੰਨੀ ਹੈ? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | ਜਨਰਲ, OBC ਅਤੇ EWS ਉਮੀਦਵਾਰਾਂ ਲਈ ₹{amount}। {exempt} ਨੂੰ ਕੋਈ ਫੀਸ ਨਹੀਂ ਦੇਣੀ ਪੈਂਦੀ। | |
| `faqSalary` | What is the salary? | ਤਨਖਾਹ ਕਿੰਨੀ ਹੈ? | |
| `faqExam` | When is the {exam} exam? | {exam} ਦੀ ਪ੍ਰੀਖਿਆ ਕਦੋਂ ਹੈ? | |
| `faqVacancy` | How many vacancies are there? | ਕਿੰਨੀਆਂ ਅਸਾਮੀਆਂ ਹਨ? | |
| `faqQual` | What qualification is needed? | ਕਿਹੜੀ ਯੋਗਤਾ ਚਾਹੀਦੀ ਹੈ? | |
| `aboutTitle` | About this app | ਇਸ ਐਪ ਬਾਰੇ | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ਭਾਰਤ ਵਿੱਚ ਸਰਕਾਰੀ ਨੌਕਰੀ ਦੀ ਤਿਆਰੀ ਕਰਨ ਵਾਲਿਆਂ ਲਈ ਇੱਕ ਮੁਫ਼ਤ, ਗ਼ੈਰ-ਸਰਕਾਰੀ ਮਦਦਗਾਰ। ਨੋਟਿਸ, ਤਾਰੀਖਾਂ ਅਤੇ ਨਿਯਮ ਭਰਤੀ ਕਰਨ ਵਾਲੀਆਂ ਸੰਸਥਾਵਾਂ ਦੀਆਂ ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈਟਾਂ ਅਤੇ ਨੋਟਿਸਾਂ ਤੋਂ ਲਏ ਗਏ ਹਨ, ਹਵਾਲਿਆਂ ਸਮੇਤ। ਕੋਈ ਸਾਈਨ-ਅੱਪ ਨਹੀਂ, ਕੋਈ ਨਿੱਜੀ ਡਾਟਾ ਇਕੱਠਾ ਨਹੀਂ ਕੀਤਾ ਜਾਂਦਾ: ਤੁਹਾਡੇ ਵੇਰਵੇ ਅਤੇ ਰੋਲ ਨੰਬਰ ਇਸੇ ਫ਼ੋਨ ਵਿੱਚ ਰਹਿੰਦੇ ਹਨ। | |
| `sourceRef` | Source: {ref} | ਸਰੋਤ: {ref} | |
| `toastSaved` | Saved | ਸੇਵ ਹੋ ਗਿਆ | |
| `toastRemoved` | Removed | ਹਟਾ ਦਿੱਤਾ | |
| `toastFollow` | You follow this exam | ਤੁਸੀਂ ਇਹ ਪ੍ਰੀਖਿਆ ਫਾਲੋ ਕਰ ਰਹੇ ਹੋ | |
| `toastUnfollow` | Unfollowed | ਅਨਫਾਲੋ ਕਰ ਦਿੱਤਾ | |
| `settings` | Settings | ਸੈਟਿੰਗਾਂ | |
| `toolsTitle` | Tools | ਟੂਲ | |
| `tabResult` | Result | ਰਿਜ਼ਲਟ | |
| `tabForMe` | For me | ਮੇਰੇ ਲਈ | |
| `resultTitle` | Check your result by roll number | ਰੋਲ ਨੰਬਰ ਨਾਲ ਆਪਣਾ ਰਿਜ਼ਲਟ ਦੇਖੋ | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | ਪਿਛਲੇ 120 ਦਿਨਾਂ ਵਿੱਚ SSC ਵੱਲੋਂ ਜਾਰੀ ਰਿਜ਼ਲਟ ਲਿਸਟਾਂ ਵਿੱਚ ਲੱਭਦਾ ਹੈ, ਵੱਡੀਆਂ PDF ਡਾਊਨਲੋਡ ਕੀਤੇ ਬਿਨਾਂ। ਇੱਕ ਛੋਟੀ ਫਾਈਲ ਲਿਆਉਣ ਲਈ ਤੁਹਾਡੇ ਰੋਲ ਨੰਬਰ ਦੇ ਸਿਰਫ਼ ਆਖਰੀ ਦੋ ਅੰਕ ਵਰਤੇ ਜਾਂਦੇ ਹਨ। | |
| `rollNo` | Roll number | ਰੋਲ ਨੰਬਰ | |
| `resultExam` | Result | ਰਿਜ਼ਲਟ | |
| `find` | Find | ਲੱਭੋ | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | ਰੋਲ ਨੰਬਰ ਉਵੇਂ ਹੀ ਭਰੋ ਜਿਵੇਂ ਤੁਹਾਡੇ ਐਡਮਿਟ ਕਾਰਡ 'ਤੇ ਛਪਿਆ ਹੈ (8-14 ਅੰਕ)। | |
| `foundIn` | Found in: {list} | ਇਸ ਵਿੱਚ ਮਿਲਿਆ: {list} | |
| `notIn` | Not in: {list} | ਇਸ ਵਿੱਚ ਨਹੀਂ: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | ਤੁਹਾਡਾ ਰੋਲ ਨੰਬਰ ਇਨ੍ਹਾਂ ਲਿਸਟਾਂ ਵਿੱਚ ਨਹੀਂ ਹੈ। ਸਿਰਫ਼ ਇਸ ਦਾ ਮਤਲਬ ਇਹ ਨਹੀਂ ਕਿ ਤੁਸੀਂ ਕੁਆਲੀਫਾਈ ਨਹੀਂ ਕੀਤਾ: ਹੇਠਾਂ ਦਿੱਤੀਆਂ ਅਧਿਕਾਰਤ PDF ਜ਼ਰੂਰ ਦੇਖੋ। | |
| `officialFiles` | Official files of this notice | ਇਸ ਨੋਟਿਸ ਦੀਆਂ ਅਧਿਕਾਰਤ ਫਾਈਲਾਂ | |
| `listCount` | {n} roll numbers | {n} ਰੋਲ ਨੰਬਰ | |
| `noLists` | No result lists to search right now. | ਹਾਲੇ ਲੱਭਣ ਲਈ ਕੋਈ ਰਿਜ਼ਲਟ ਲਿਸਟ ਨਹੀਂ ਹੈ। | |
| `forMeTitle` | Exams that suit you | ਤੁਹਾਡੇ ਲਈ ਢੁਕਵੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | ਆਪਣੇ ਵੇਰਵੇ ਇੱਕ ਵਾਰ ਭਰ ਦਿਓ। ਤੁਹਾਨੂੰ ਹਰ ਉਹ SSC ਪ੍ਰੀਖਿਆ ਦਿਖੇਗੀ ਜਿਸ ਲਈ ਤੁਸੀਂ ਅਪਲਾਈ ਕਰ ਸਕਦੇ ਹੋ, ਉਸ ਦਾ ਸਟੇਟਸ, ਅਤੇ ਅੱਗੇ ਕੀ ਹੋਣਾ ਹੈ। | |
| `forMeEdit` | Edit my details | ਮੇਰੇ ਵੇਰਵੇ ਬਦਲੋ | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | ਤੁਹਾਡੀ ਯੋਗਤਾ ਮੁਤਾਬਕ (ਨਿਯਮ ਹਾਲੇ ਸ਼ਾਮਲ ਨਹੀਂ: ਨੋਟਿਸ ਪੜ੍ਹੋ) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | ਤੁਹਾਡੀ ਯੋਗਤਾ ਲਈ ਆਉਣ ਵਾਲੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ (ਸੰਭਾਵਿਤ) | |
| `saveDetails` | Show my exams | ਮੇਰੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ ਦਿਖਾਓ | |
| `whenTitle` | What has happened and what is next | ਕੀ ਹੋ ਚੁੱਕਾ ਹੈ ਅਤੇ ਅੱਗੇ ਕੀ ਹੈ | |
| `st_done` | Done | ਹੋ ਗਿਆ | |
| `st_now` | Now | ਹੁਣ | |
| `st_next` | Next | ਅਗਲਾ | |
| `st_later` | Later | ਬਾਅਦ ਵਿੱਚ | |
| `stage_notice` | Notice published | ਨੋਟਿਸ ਜਾਰੀ | |
| `stage_apply` | Online application | ਆਨਲਾਈਨ ਅਰਜ਼ੀ | |
| `stage_admit_card` | Admit card / exam city | ਐਡਮਿਟ ਕਾਰਡ / ਪ੍ਰੀਖਿਆ ਸ਼ਹਿਰ | |
| `stage_answer_key` | Answer key | ਆਂਸਰ ਕੀ | |
| `stage_result` | Result | ਰਿਜ਼ਲਟ | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ਨੇ ਹਾਲੇ ਤਾਰੀਖ ਦਾ ਐਲਾਨ ਨਹੀਂ ਕੀਤਾ | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | ਬੇਸਿਕ ਪੇ ₹{from}–{to} ਮਹੀਨਾ (ਲੈਵਲ {l}; ਭੱਤੇ ਵੱਖਰੇ) | |
| `savedLink` | ★ Saved | ★ ਸੇਵ ਕੀਤੇ | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ਇਹ ਸਰਕਾਰੀ ਵੈੱਬਸਾਈਟ ਨਹੀਂ ਹੈ ਅਤੇ ਕਿਸੇ ਭਰਤੀ ਸੰਸਥਾ ਜਾਂ ਸਰਕਾਰ ਨਾਲ ਜੁੜੀ ਨਹੀਂ ਹੈ। ਇੱਥੇ ਸਭ ਕੁਝ ਭਰਤੀ ਕਰਨ ਵਾਲਿਆਂ ਦੀਆਂ ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈਟਾਂ ਤੋਂ ਲਿਆ ਗਿਆ ਹੈ, ਅਤੇ ਹਰ ਅਪਡੇਟ ਨਾਲ ਅਧਿਕਾਰਤ ਫਾਈਲ ਜਾਂ ਪੇਜ ਦਾ ਲਿੰਕ ਹੈ। ਅਪਲਾਈ ਕਰਨ ਤੋਂ ਪਹਿਲਾਂ ਹਮੇਸ਼ਾ ਅਧਿਕਾਰਤ ਨੋਟਿਸ ਜ਼ਰੂਰ ਦੇਖੋ। | |
| `tabExams` | Exams | ਪ੍ਰੀਖਿਆਵਾਂ | |
| `filters` | Filters | ਫਿਲਟਰ | |
| `kindAll` | All | ਸਾਰੇ | |
| `kindRecruitment` | New jobs | ਨਵੀਆਂ ਨੌਕਰੀਆਂ | |
| `kindResult` | Results | ਰਿਜ਼ਲਟ | |
| `kindAdmit` | Admit cards | ਐਡਮਿਟ ਕਾਰਡ | |
| `kindAnswer` | Answer keys | ਆਂਸਰ ਕੀ | |
| `kindAmendment` | Changes | ਬਦਲਾਅ | |
| `kindVacancy` | Vacancies | ਅਸਾਮੀਆਂ | |
| `kind_recruitment` | New job | ਨਵੀਂ ਨੌਕਰੀ | |
| `kind_amendment` | Change | ਬਦਲਾਅ | |
| `kind_result` | Result | ਰਿਜ਼ਲਟ | |
| `kind_answer_key` | Answer key | ਆਂਸਰ ਕੀ | |
| `kind_admit_card` | Admit card | ਐਡਮਿਟ ਕਾਰਡ | |
| `kind_vacancy` | Vacancy | ਅਸਾਮੀ | |
| `kind_schedule` | Exam dates | ਪ੍ਰੀਖਿਆ ਤਾਰੀਖਾਂ | |
| `kind_notice` | Notice | ਨੋਟਿਸ | |
| `search` | Search jobs, exams, results | ਨੌਕਰੀ, ਪ੍ਰੀਖਿਆ, ਰਿਜ਼ਲਟ ਲੱਭੋ | |
| `allExams` | All exams | ਸਾਰੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `myExams` | My exams only | ਸਿਰਫ਼ ਮੇਰੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `anyQualification` | Any qualification | ਕੋਈ ਵੀ ਯੋਗਤਾ | |
| `qual_10` | Class 10 | 10ਵੀਂ | |
| `qual_12` | Class 12 | 12ਵੀਂ | |
| `qual_diploma` | Diploma | ਡਿਪਲੋਮਾ | |
| `qual_graduate` | Bachelor's degree | ਗ੍ਰੈਜੂਏਸ਼ਨ | |
| `qual_postgraduate` | Master's degree | ਪੋਸਟ ਗ੍ਰੈਜੂਏਸ਼ਨ | |
| `departmental` | Departmental exams | ਵਿਭਾਗੀ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `updated` | Updated {time} IST | ਅਪਡੇਟ: {time} IST | |
| `newSince` | {n} new since your last visit | ਤੁਹਾਡੀ ਪਿਛਲੀ ਵਿਜ਼ਿਟ ਤੋਂ ਬਾਅਦ {n} ਨਵੇਂ | |
| `cutoffTitle` | Cut-off marks | ਕੱਟ-ਆਫ਼ ਅੰਕ | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC ਕੱਟ-ਆਫ਼ ਸਿਰਫ਼ ਰਿਜ਼ਲਟ ਦੇ ਵੇਰਵੇ ਵਿੱਚ ਹੀ ਦਿੰਦਾ ਹੈ। ਅੰਕ ਇੱਥੇ ਕਾਪੀ ਨਹੀਂ ਕੀਤੇ ਗਏ (ਇੱਕ ਗਲਤ ਅੰਕ ਵੀ ਗੁਮਰਾਹ ਕਰ ਸਕਦਾ ਹੈ): ਹਰ ਲਿੰਕ SSC ਦੀ PDF ਉਸੇ ਪੇਜ 'ਤੇ ਖੋਲ੍ਹਦਾ ਹੈ ਜਿੱਥੋਂ ਕੱਟ-ਆਫ਼ ਦਾ ਵੇਰਵਾ ਸ਼ੁਰੂ ਹੁੰਦਾ ਹੈ। | |
| `cutoffFrom` | Open at page {p} | ਪੇਜ {p} 'ਤੇ ਖੋਲ੍ਹੋ | |
| `cutoffPages` | mentioned on p. {pages} | ਪੇਜ {pages} 'ਤੇ ਦੱਸਿਆ ਗਿਆ | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | ਪਿਛਲੇ 120 ਦਿਨਾਂ ਦੇ ਰਿਜ਼ਲਟ ਵੇਰਵਿਆਂ ਵਿੱਚ ਕੋਈ ਕੱਟ-ਆਫ਼ ਨਹੀਂ ਮਿਲਿਆ। | |
| `alertsTitle` | Alerts on this phone | ਇਸ ਫ਼ੋਨ 'ਤੇ ਅਲਰਟ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | ਜਿਸ ਪ੍ਰੀਖਿਆ ਨੂੰ ਤੁਸੀਂ ਫਾਲੋ ਕਰਦੇ ਹੋ ਉਸ ਦੀ ਕੋਈ ਖ਼ਬਰ ਆਉਣ 'ਤੇ, ਅਤੇ ਅਰਜ਼ੀ ਬੰਦ ਹੋਣ ਤੋਂ ਦੋ ਦਿਨ ਪਹਿਲਾਂ ਨੋਟੀਫਿਕੇਸ਼ਨ। | |
| `alertsOn` | Turn on alerts | ਅਲਰਟ ਚਾਲੂ ਕਰੋ | |
| `alertsOff` | Turn off alerts | ਅਲਰਟ ਬੰਦ ਕਰੋ | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | ਪਹਿਲਾਂ ਕੋਈ ਪ੍ਰੀਖਿਆ ਫਾਲੋ ਕਰੋ (ਪ੍ਰੀਖਿਆਵਾਂ → ਫਾਲੋ ਕਰੋ)। | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} ਪ੍ਰੀਖਿਆਵਾਂ ਲਈ ਚਾਲੂ ਹੈ। ਕਦੋਂ ਚੈੱਕ ਕਰਨਾ ਹੈ ਇਹ Chrome ਤੈਅ ਕਰਦਾ ਹੈ, ਜੇ ਤੁਸੀਂ ਐਪ ਅਕਸਰ ਖੋਲ੍ਹਦੇ ਹੋ ਤਾਂ ਵੱਧ ਵਾਰ, ਇਸ ਲਈ ਖ਼ਬਰ ਪੋਸਟ ਹੋਣ ਤੋਂ ਕਈ ਘੰਟੇ ਬਾਅਦ ਵੀ ਪਹੁੰਚ ਸਕਦੀ ਹੈ। | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | ਇਸ ਸਾਈਟ ਲਈ ਨੋਟੀਫਿਕੇਸ਼ਨ ਬਲੌਕ ਹਨ। ਬ੍ਰਾਊਜ਼ਰ ਦੀਆਂ ਸਾਈਟ ਸੈਟਿੰਗਾਂ ਵਿੱਚ ਜਾ ਕੇ ਇਜਾਜ਼ਤ ਦਿਓ। | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | ਇਹ ਬ੍ਰਾਊਜ਼ਰ ਬੈਕਗ੍ਰਾਊਂਡ ਵਿੱਚ ਚੈੱਕ ਨਹੀਂ ਕਰ ਸਕਦਾ। Android 'ਤੇ ਪਹਿਲਾਂ ਇਹ ਐਪ Chrome ਤੋਂ ਇੰਸਟਾਲ ਕਰੋ। ਨਹੀਂ ਤਾਂ ਹਰ ਪ੍ਰੀਖਿਆ ਦੇ ਪੇਜ 'ਤੇ ਕੈਲੰਡਰ ਰਿਮਾਈਂਡਰ ਵਰਤੋ। | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | ਤੁਹਾਡੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ ਦੇ {n} ਹੋਰ ਅਪਡੇਟ | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: ਅਰਜ਼ੀਆਂ {n} ਦਿਨਾਂ ਵਿੱਚ ਬੰਦ ਹੋ ਜਾਣਗੀਆਂ | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: ਅੱਜ ਅਪਲਾਈ ਕਰਨ ਦਾ ਆਖਰੀ ਦਿਨ ਹੈ | |
| `installTitle` | Install the app | ਐਪ ਇੰਸਟਾਲ ਕਰੋ | |
| `installBtn` | Install | ਇੰਸਟਾਲ ਕਰੋ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ਐਪ ਵਾਂਗ ਖੁੱਲ੍ਹਦੀ ਹੈ, ਬਿਨਾਂ ਇੰਟਰਨੈੱਟ ਦੇ ਚੱਲਦੀ ਹੈ, ਅਤੇ Android 'ਤੇ ਅਲਰਟ ਭੇਜ ਸਕਦੀ ਹੈ। | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone 'ਤੇ: Share ਦਬਾਓ, ਫਿਰ "Add to Home Screen"। | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ਫਾਲੋ ਕਰ ਲਿਆ। ਫ਼ੋਨ 'ਤੇ ਅਲਰਟ ਲਈ: ਹੋਰ → ਅਲਰਟ ਚਾਲੂ ਕਰੋ | |
| `rollPlaceholder` | Roll number on your admit card | ਐਡਮਿਟ ਕਾਰਡ 'ਤੇ ਲਿਖਿਆ ਰੋਲ ਨੰਬਰ | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} ਪ੍ਰੀਖਿਆਵਾਂ ਲਈ ਚਾਲੂ ਹੈ। SSC ਦੇ ਨੋਟਿਸ ਆਮ ਤੌਰ 'ਤੇ ਪੋਸਟ ਹੋਣ ਦੇ ਇੱਕ ਘੰਟੇ ਅੰਦਰ ਤੁਹਾਡੇ ਤੱਕ ਪਹੁੰਚ ਜਾਂਦੇ ਹਨ; ਰਿਜ਼ਲਟ ਅਤੇ ਦੂਜੇ ਬੋਰਡਾਂ ਦੇ ਨੋਟਿਸਾਂ ਨੂੰ 3 ਘੰਟੇ ਤੱਕ ਲੱਗ ਸਕਦੇ ਹਨ। | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | ਇਹ ਬ੍ਰਾਊਜ਼ਰ ਅਲਰਟ ਨਹੀਂ ਦਿਖਾ ਸਕਦਾ। iPhone 'ਤੇ ਪਹਿਲਾਂ ਇਹ ਐਪ ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਜੋੜੋ (Share → Add to Home Screen), ਫਿਰ ਉੱਥੋਂ ਖੋਲ੍ਹੋ। | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | ਅਲਰਟ ਚਾਲੂ ਨਹੀਂ ਹੋ ਸਕੇ। ਆਪਣਾ ਕਨੈਕਸ਼ਨ ਚੈੱਕ ਕਰੋ ਅਤੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ। | |
| `allBoards` | All recruiters | ਸਾਰੀਆਂ ਭਰਤੀ ਸੰਸਥਾਵਾਂ | |
| `newBadge` | NEW | ਨਵਾਂ | |
| `nothing` | Nothing here in the last 120 days. | ਪਿਛਲੇ 120 ਦਿਨਾਂ ਵਿੱਚ ਇੱਥੇ ਕੁਝ ਨਹੀਂ। | |
| `loadError` | Couldn't load. Check your internet connection and try again. | ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ। ਆਪਣਾ ਇੰਟਰਨੈੱਟ ਕਨੈਕਸ਼ਨ ਚੈੱਕ ਕਰੋ ਅਤੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ। | |
| `offline` | Offline: showing the copy saved on this phone. | ਆਫ਼ਲਾਈਨ: ਇਸ ਫ਼ੋਨ ਵਿੱਚ ਸੇਵ ਕੀਤੀ ਕਾਪੀ ਦਿਖਾਈ ਜਾ ਰਹੀ ਹੈ। | |
| `save` | Save | ਸੇਵ ਕਰੋ | |
| `saved` | Saved | ਸੇਵ ਕੀਤਾ | |
| `share` | Share | ਸ਼ੇਅਰ ਕਰੋ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | ਅਧਿਕਾਰਤ ਪੇਜ | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ ਅਧਿਕਾਰਤ ਲਿੰਕ: {url} ⏎ {app} ਰਾਹੀਂ (ਸਰਕਾਰੀ ਸਾਈਟ ਨਹੀਂ) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ਹਰ ਪ੍ਰੀਖਿਆ, ਉਸ ਦਾ ਤਾਜ਼ਾ ਨੋਟਿਸ, ਤਾਰੀਖਾਂ, ਫੀਸ, ਉਮਰ ਸੀਮਾ ਅਤੇ ਉਸ ਤੋਂ ਬਾਅਦ ਦੇ ਸਾਰੇ ਨੋਟਿਸ। | |
| `follow` | Follow | ਫਾਲੋ ਕਰੋ | |
| `following` | Following | ਫਾਲੋ ਕਰ ਰਹੇ ਹੋ | |
| `minQual` | Qualification: {q} | ਯੋਗਤਾ: {q} | |
| `qualUnknown` | Qualification: see the Notice | ਯੋਗਤਾ: ਨੋਟਿਸ ਦੇਖੋ | |
| `calendarTitle` | SSC's tentative calendar | SSC ਦਾ ਸੰਭਾਵਿਤ ਕੈਲੰਡਰ | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | ਇਹ ਯੋਜਨਾ ਹੈ, ਨੋਟਿਸ ਨਹੀਂ: ਅਸਲ ਤਾਰੀਖਾਂ ਹਰ ਨੋਟਿਸ ਵਿੱਚ ਹੁੰਦੀਆਂ ਹਨ ਅਤੇ ਵੱਖਰੀਆਂ ਹੋ ਸਕਦੀਆਂ ਹਨ। | |
| `resourcesTitle` | Official resources | ਅਧਿਕਾਰਤ ਸਰੋਤ | |
| `res_syllabus` | Syllabus | ਸਿਲੇਬਸ | |
| `res_vacancy` | Vacancies (tentative / final) | ਅਸਾਮੀਆਂ (ਸੰਭਾਵਿਤ / ਫਾਈਨਲ) | |
| `res_previous_papers` | Previous year papers | ਪਿਛਲੇ ਸਾਲ ਦੇ ਪੇਪਰ | |
| `res_certificate_formats` | Certificate formats | ਸਰਟੀਫਿਕੇਟ ਫਾਰਮੈਟ | |
| `res_instructions` | Instructions | ਹਦਾਇਤਾਂ | |
| `back` | ← All exams | ← ਸਾਰੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `latestNotice` | Latest Notice | ਤਾਜ਼ਾ ਨੋਟਿਸ | |
| `importantDates` | Important dates | ਅਹਿਮ ਤਾਰੀਖਾਂ | |
| `applyWindow` | Apply online | ਆਨਲਾਈਨ ਅਪਲਾਈ | |
| `feeLastDate` | Fee payment last date | ਫੀਸ ਭਰਨ ਦੀ ਆਖਰੀ ਤਾਰੀਖ | |
| `correctionWindow` | Form correction window | ਫਾਰਮ ਸੁਧਾਰ ਦੀ ਮਿਆਦ | |
| `tentative` | tentative | ਸੰਭਾਵਿਤ | |
| `applicationFee` | Application fee | ਅਰਜ਼ੀ ਫੀਸ | |
| `feeGeneral` | General / OBC / EWS | ਜਨਰਲ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ਫੀਸ ਮੁਆਫ਼ (₹0) | |
| `exempt_female` | Women | ਔਰਤਾਂ | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | ਸਾਬਕਾ ਫੌਜੀ | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | ਸੁਧਾਰ: ਪਹਿਲੀ ਵਾਰ ₹{a}, ਦੂਜੀ ਵਾਰ ₹{b} (ਸਾਰੇ ਉਮੀਦਵਾਰ) | |
| `ageLimit` | Age limit (as on {date}) | ਉਮਰ ਸੀਮਾ ({date} ਨੂੰ) | |
| `ageBand` | {band} years: born {from} to {to} | {band} ਸਾਲ: ਜਨਮ {from} ਤੋਂ {to} ਵਿਚਕਾਰ | |
| `relaxation` | Upper age relaxation | ਵੱਧ ਤੋਂ ਵੱਧ ਉਮਰ ਵਿੱਚ ਛੋਟ | |
| `relaxYears` | {n} years | {n} ਸਾਲ | |
| `notComputed` | Not calculated here: {list} | ਇੱਥੇ ਹਿਸਾਬ ਨਹੀਂ ਲਾਇਆ ਗਿਆ: {list} | |
| `vacancies` | Vacancies | ਅਸਾਮੀਆਂ | |
| `vacancyCount` | About {n} ({tentative}) | ਲਗਭਗ {n} ({tentative}) | |
| `posts` | Posts | ਪੋਸਟਾਂ | |
| `importantLinks` | Important links | ਅਹਿਮ ਲਿੰਕ | |
| `applyOnline` | Apply online (ssc.gov.in) | ਆਨਲਾਈਨ ਅਪਲਾਈ ਕਰੋ (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | ਅਧਿਕਾਰਤ ਨੋਟਿਸ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date} ਦਾ ਬਦਲਾਅ | |
| `allNotices` | All notices for this exam | ਇਸ ਪ੍ਰੀਖਿਆ ਦੇ ਸਾਰੇ ਨੋਟਿਸ | |
| `addCalendar` | Add dates to my calendar | ਤਾਰੀਖਾਂ ਮੇਰੇ ਕੈਲੰਡਰ ਵਿੱਚ ਜੋੜੋ | |
| `checkThis` | Check my eligibility | ਮੇਰੀ ਯੋਗਤਾ ਚੈੱਕ ਕਰੋ | |
| `status_open` | Applications open till {date} | ਅਰਜ਼ੀਆਂ {date} ਤੱਕ ਖੁੱਲ੍ਹੀਆਂ ਹਨ | |
| `status_upcoming` | Applications open on {date} | ਅਰਜ਼ੀਆਂ {date} ਨੂੰ ਖੁੱਲ੍ਹਣਗੀਆਂ | |
| `status_closed` | Applications closed on {date} | ਅਰਜ਼ੀਆਂ {date} ਨੂੰ ਬੰਦ ਹੋ ਗਈਆਂ | |
| `daysLeft` | {n} days left | {n} ਦਿਨ ਬਾਕੀ | |
| `lastDay` | Last day today | ਅੱਜ ਆਖਰੀ ਦਿਨ | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | ਇਸ ਪ੍ਰੀਖਿਆ ਦੇ ਯੋਗਤਾ ਨਿਯਮ ਹਾਲੇ ਸ਼ਾਮਲ ਨਹੀਂ: ਨੋਟਿਸ ਪੜ੍ਹੋ। | |
| `examLabel` | Exam | ਪ੍ਰੀਖਿਆ | |
| `dob` | Date of birth (as in Class 10 certificate) | ਜਨਮ ਤਾਰੀਖ (10ਵੀਂ ਦੇ ਸਰਟੀਫਿਕੇਟ ਮੁਤਾਬਕ) | |
| `category` | Category | ਸ਼੍ਰੇਣੀ | |
| `catUR` | General (UR) | ਜਨਰਲ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (ਨਾਨ-ਕ੍ਰੀਮੀ ਲੇਅਰ) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | ਸਭ ਤੋਂ ਉੱਚੀ ਯੋਗਤਾ | |
| `edu10` | Class 10 | 10ਵੀਂ | |
| `edu12` | Class 12 | 12ਵੀਂ | |
| `eduGraduate` | Bachelor's degree or higher | ਗ੍ਰੈਜੂਏਸ਼ਨ ਜਾਂ ਉਸ ਤੋਂ ਉੱਪਰ | |
| `gender` | Gender | ਲਿੰਗ | |
| `male` | Male | ਮਰਦ | |
| `female` | Female | ਔਰਤ | |
| `other` | Other | ਹੋਰ | |
| `appearing` | Final exam of that level taken, result not out yet | ਉਸ ਲੈਵਲ ਦੀ ਫਾਈਨਲ ਪ੍ਰੀਖਿਆ ਦੇ ਦਿੱਤੀ, ਰਿਜ਼ਲਟ ਹਾਲੇ ਨਹੀਂ ਆਇਆ | |
| `pwbd` | Person with benchmark disability (PwBD) | ਬੈਂਚਮਾਰਕ ਅਪੰਗਤਾ ਵਾਲਾ ਵਿਅਕਤੀ (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | ਸਾਬਕਾ ਫੌਜੀ, ਕੇਂਦਰ ਸਰਕਾਰ ਦਾ ਮੁਲਾਜ਼ਮ, ਜਾਂ ਵਿਧਵਾ/ਤਲਾਕਸ਼ੁਦਾ ਔਰਤ | |
| `check` | Check | ਚੈੱਕ ਕਰੋ | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | ਤੁਹਾਡੇ ਜਵਾਬ ਇਸੇ ਡਿਵਾਈਸ 'ਤੇ ਰਹਿੰਦੇ ਹਨ। ਕਿਤੇ ਵੀ ਕੁਝ ਨਹੀਂ ਭੇਜਿਆ ਜਾਂਦਾ। | |
| `enterDob` | Enter your date of birth. | ਆਪਣੀ ਜਨਮ ਤਾਰੀਖ ਭਰੋ। | |
| `closedNote` | Shown so you can prepare for the next cycle. | ਇਸ ਲਈ ਦਿਖਾਇਆ ਗਿਆ ਹੈ ਤਾਂ ਜੋ ਤੁਸੀਂ ਅਗਲੇ ਸਾਈਕਲ ਦੀ ਤਿਆਰੀ ਕਰ ਸਕੋ। | |
| `canApply` | {n} can apply | {n} ਵਿੱਚ ਅਪਲਾਈ ਕਰ ਸਕਦੇ ਹੋ | |
| `toCheck` | {n} to check | {n} ਚੈੱਕ ਕਰਨੇ ਹਨ | |
| `notEligible` | {n} not eligible | {n} ਵਿੱਚ ਯੋਗ ਨਹੀਂ | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ਫੀਸ: {fee}। ਉਮਰ ਵਿੱਚ ਦਿੱਤੀ ਛੋਟ: {n} ਸਾਲ। ਉਮਰ {date} ਨੂੰ ਗਿਣੀ ਜਾਂਦੀ ਹੈ। | |
| `feeNone` | none (exempt) | ਕੋਈ ਨਹੀਂ (ਮੁਆਫ਼) | |
| `st_eligible` | You can apply | ਤੁਸੀਂ ਅਪਲਾਈ ਕਰ ਸਕਦੇ ਹੋ | |
| `st_check` | Check one condition | ਇੱਕ ਸ਼ਰਤ ਚੈੱਕ ਕਰੋ | |
| `st_ineligible` | Not eligible | ਯੋਗ ਨਹੀਂ | |
| `payLevel` | Pay Level {l} · Group {g} | ਪੇ ਲੈਵਲ {l} · ਗਰੁੱਪ {g} | |
| `groupOnly` | Group {g} | ਗਰੁੱਪ {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | ਜਨਮ {from} ਤੋਂ {to} ਵਿਚਕਾਰ (ਤੁਹਾਡੇ ਲਈ) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | ਨਿਯਮ {date} ਦੇ ਨੋਟਿਸ ਤੋਂ ਲਏ ਗਏ ({checked} ਨੂੰ ਚੈੱਕ ਕੀਤੇ)। | |
| `sigTitle` | Signature for SSC forms | SSC ਫਾਰਮ ਲਈ ਦਸਤਖ਼ਤ | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC ਨੂੰ {min}-{max} KB ਦੀ JPEG ਚਾਹੀਦੀ ਹੈ, ਲਗਭਗ {w} cm × {h} cm। ਚਿੱਟੇ ਕਾਗਜ਼ 'ਤੇ ਕਾਲੀ ਸਿਆਹੀ ਨਾਲ ਦਸਤਖ਼ਤ ਕਰੋ ਅਤੇ ਸਾਫ਼ ਫੋਟੋ ਖਿੱਚੋ। | |
| `sigPick` | Choose signature photo | ਦਸਤਖ਼ਤ ਦੀ ਫੋਟੋ ਚੁਣੋ | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | ਤਿਆਰ: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ਫਾਈਲ {min} ਅਤੇ {max} KB ਵਿਚਕਾਰ ਨਹੀਂ ਬਣ ਸਕੀ। ਹੋਰ ਸਾਫ਼ ਫੋਟੋ ਨਾਲ ਕੋਸ਼ਿਸ਼ ਕਰੋ। | |
| `sigDownload` | Download JPEG | JPEG ਡਾਊਨਲੋਡ ਕਰੋ | |
| `photoTitle` | Your photograph | ਤੁਹਾਡੀ ਫੋਟੋ | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC ਹੁਣ ਅਪਲੋਡ ਕੀਤੀ ਫੋਟੋ ਨਹੀਂ ਲੈਂਦਾ: ਅਰਜ਼ੀ ਫਾਰਮ ਤੁਹਾਡੇ ਕੈਮਰੇ ਨਾਲ ਲਾਈਵ ਫੋਟੋ ਲੈਂਦਾ ਹੈ, ਅਤੇ ਪੁਰਾਣੀ ਫੋਟੋ ਦੀ ਫੋਟੋ ਖਿੱਚਣ 'ਤੇ ਫਾਰਮ ਰੱਦ ਹੋ ਜਾਂਦਾ ਹੈ। ਚੰਗੀ ਰੋਸ਼ਨੀ, ਸਾਦਾ ਬੈਕਗ੍ਰਾਊਂਡ, ਕੈਮਰਾ ਅੱਖਾਂ ਦੀ ਉਚਾਈ 'ਤੇ ਰੱਖੋ; ਟੋਪੀ, ਮਾਸਕ ਜਾਂ ਐਨਕ ਨਾ ਹੋਵੇ। | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ਸਰੋਤ: ਨੋਟਿਸ ਪੈਰਾ 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | ਉਮਰ ਕੈਲਕੁਲੇਟਰ | |
| `ageOn` | Age on | ਇਸ ਤਾਰੀਖ ਨੂੰ ਉਮਰ | |
| `ageResult` | {y} years, {m} months, {d} days | {y} ਸਾਲ, {m} ਮਹੀਨੇ, {d} ਦਿਨ | |
| `ageInvalid` | Date of birth must be before that date. | ਜਨਮ ਤਾਰੀਖ ਉਸ ਤਾਰੀਖ ਤੋਂ ਪਹਿਲਾਂ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ। | |
| `docsTitle` | Certificate formats | ਸਰਟੀਫਿਕੇਟ ਫਾਰਮੈਟ | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | ਸ਼੍ਰੇਣੀ, ਅਪੰਗਤਾ ਅਤੇ ਸਾਬਕਾ ਫੌਜੀ ਸਰਟੀਫਿਕੇਟਾਂ ਲਈ SSC ਦੇ ਫਾਰਮੈਟ। | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ਹਾਲੇ ਕੁਝ ਸੇਵ ਨਹੀਂ ਕੀਤਾ। ਕਿਸੇ ਵੀ ਨੋਟਿਸ 'ਤੇ ਸੇਵ ਕਰੋ ਦਬਾਓ। | |
| `followedExams` | Exams you follow | ਤੁਹਾਡੀਆਂ ਫਾਲੋ ਕੀਤੀਆਂ ਪ੍ਰੀਖਿਆਵਾਂ | |
| `chooseLanguage` | Choose your language | ਆਪਣੀ ਭਾਸ਼ਾ ਚੁਣੋ | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | ਨੋਟਿਸਾਂ ਦੇ ਸਿਰਲੇਖ ਉਸੇ ਭਾਸ਼ਾ ਵਿੱਚ ਰਹਿੰਦੇ ਹਨ ਜਿਸ ਵਿੱਚ ਭਰਤੀ ਸੰਸਥਾ ਉਨ੍ਹਾਂ ਨੂੰ ਜਾਰੀ ਕਰਦੀ ਹੈ (ਜ਼ਿਆਦਾਤਰ ਅੰਗਰੇਜ਼ੀ ਜਾਂ ਹਿੰਦੀ)। | |
| `qaLatest` | Latest jobs | ਨਵੀਆਂ ਨੌਕਰੀਆਂ | |
| `qaResults` | Results | ਰਿਜ਼ਲਟ | |
| `qaAdmit` | Admit cards | ਐਡਮਿਟ ਕਾਰਡ | |
| `qaAnswer` | Answer keys | ਆਂਸਰ ਕੀ | |
| `qaEligibility` | Check eligibility | ਯੋਗਤਾ ਚੈੱਕ | |
| `qaRoll` | Result by roll no. | ਰੋਲ ਨੰ. ਨਾਲ ਰਿਜ਼ਲਟ | |
| `homeSectors` | Jobs by sector | ਸੈਕਟਰ ਮੁਤਾਬਕ ਨੌਕਰੀਆਂ | |
| `homeStates` | Jobs by state | ਰਾਜ ਮੁਤਾਬਕ ਨੌਕਰੀਆਂ | |
| `homeClosing` | Closing soon | ਜਲਦੀ ਬੰਦ ਹੋਣ ਵਾਲੀਆਂ | |
| `homeChannels` | Get every update on WhatsApp or Telegram | ਹਰ ਅਪਡੇਟ WhatsApp ਜਾਂ Telegram 'ਤੇ ਪਾਓ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | ਮੁਫ਼ਤ। ਹਰ ਅਪਡੇਟ 'ਤੇ ਇੱਕ ਮੈਸੇਜ, ਅਧਿਕਾਰਤ ਲਿੰਕ ਦੇ ਨਾਲ। | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | ਗ਼ੈਰ-ਸਰਕਾਰੀ · ਹਰ ਅਪਡੇਟ ਨਾਲ ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈਟ ਦਾ ਲਿੰਕ | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | ਪੁਲਿਸ | |
| `sector_defence` | Defence & CAPF | ਡਿਫੈਂਸ ਅਤੇ CAPF | |
| `sector_railway` | Railway | ਰੇਲਵੇ | |
| `sector_bank` | Bank | ਬੈਂਕ | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | ਸਾਇੰਸ ਅਤੇ ਸਪੇਸ | |
| `sector_state` | State govt | ਰਾਜ ਸਰਕਾਰ | |
| `sector_teaching` | Teaching | ਟੀਚਿੰਗ | |
| `state_all` | All India | ਆਲ ਇੰਡੀਆ | |
| `state_UP` | Uttar Pradesh | ਉੱਤਰ ਪ੍ਰਦੇਸ਼ | |
| `state_BR` | Bihar | ਬਿਹਾਰ | |
| `state_HR` | Haryana | ਹਰਿਆਣਾ | |
| `anySector` | All sectors | ਸਾਰੇ ਸੈਕਟਰ | |
| `anyState` | All states | ਸਾਰੇ ਰਾਜ | |
| `jobsSector` | {name} jobs | {name} ਨੌਕਰੀਆਂ | |
| `jobsState` | Government jobs in {name} | {name} ਵਿੱਚ ਸਰਕਾਰੀ ਨੌਕਰੀਆਂ | |
| `jobsKind` | {name}: latest | {name}: ਤਾਜ਼ਾ | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | ਇਸ ਸੈਕਟਰ ਤੋਂ ਹਾਲੇ ਕੁਝ ਨਹੀਂ। ਅਸੀਂ ਕਿਸੇ ਭਰਤੀ ਸੰਸਥਾ ਨੂੰ ਉਸ ਦੀ ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈਟ ਚੈੱਕ ਕਰਨ ਤੋਂ ਬਾਅਦ ਹੀ ਜੋੜਦੇ ਹਾਂ, ਇਸ ਲਈ ਇਹ ਲਿਸਟ ਵਧਦੀ ਜਾਵੇਗੀ। | |
| `centralJobs` | Central govt (all India) | ਕੇਂਦਰ ਸਰਕਾਰ (ਆਲ ਇੰਡੀਆ) | |
| `recruitersTitle` | Recruiters we follow | ਜਿਨ੍ਹਾਂ ਭਰਤੀ ਸੰਸਥਾਵਾਂ ਨੂੰ ਅਸੀਂ ਫਾਲੋ ਕਰਦੇ ਹਾਂ | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | ਅਪਡੇਟ ਇਨ੍ਹਾਂ ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈਟਾਂ ਤੋਂ ਲਏ ਜਾਂਦੇ ਹਨ। ਖੋਲ੍ਹਣ ਲਈ ਕਿਸੇ 'ਤੇ ਟੈਪ ਕਰੋ। | |
| `moreUpdates` | See all updates | ਸਾਰੇ ਅਪਡੇਟ ਦੇਖੋ | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | ਉਮਰ ਘੱਟ ਹੈ: {date} ਨੂੰ ਤੁਹਾਡੀ ਉਮਰ ਘੱਟੋ-ਘੱਟ {n} ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ। | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | ਉਮਰ ਸੀਮਾ ਤੋਂ ਵੱਧ: {date} ਨੂੰ ਤੁਹਾਡੀ ਉਮਰ {n} ਤੋਂ ਘੱਟ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ (ਛੋਟ ਸਮੇਤ)। | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | ਆਮ ਉਮਰ ਸੀਮਾ ਤੋਂ ਵੱਧ। ਸਾਬਕਾ ਫੌਜੀ / ਸਰਕਾਰੀ ਮੁਲਾਜ਼ਮ / ਵਿਧਵਾ ਛੋਟ ਨਾਲ ਗੱਲ ਬਣ ਸਕਦੀ ਹੈ: ਨੋਟਿਸ {ref} ਦੇਖੋ। | |
| `r_education_graduate` | Needs a bachelor's degree. | ਗ੍ਰੈਜੂਏਸ਼ਨ ਦੀ ਡਿਗਰੀ ਚਾਹੀਦੀ ਹੈ। | |
| `r_education_12` | Needs Class 12 pass. | 12ਵੀਂ ਪਾਸ ਹੋਣਾ ਚਾਹੀਦਾ ਹੈ। | |
| `r_education_10` | Needs Class 10 pass. | 10ਵੀਂ ਪਾਸ ਹੋਣਾ ਚਾਹੀਦਾ ਹੈ। | |
| `r_result_needed_by` | Your result must be out by {date}. | ਤੁਹਾਡਾ ਰਿਜ਼ਲਟ {date} ਤੱਕ ਆ ਜਾਣਾ ਚਾਹੀਦਾ ਹੈ। | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | ਨੋਟਿਸ ਵਿੱਚ ਇਹ ਨਹੀਂ ਲਿਖਿਆ ਕਿ ਰਿਜ਼ਲਟ ਦੀ ਉਡੀਕ ਕਰ ਰਹੇ ਉਮੀਦਵਾਰ ਅਪਲਾਈ ਕਰ ਸਕਦੇ ਹਨ; {date} ਤੱਕ ਤੁਹਾਡੇ ਕੋਲ ਯੋਗਤਾ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ। | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | ਇਸ ਨੋਟਿਸ ਵਿੱਚ PwBD ਲਈ ਉਮਰ ਵਿੱਚ ਕੋਈ ਛੋਟ ਨਹੀਂ ਦਿੱਤੀ ਗਈ: ਨੋਟਿਸ ਚੈੱਕ ਕਰੋ। | |
| `r_gender_male` | Only for male candidates. | ਸਿਰਫ਼ ਮਰਦ ਉਮੀਦਵਾਰਾਂ ਲਈ। | |
| `r_gender_female` | Only for female candidates. | ਸਿਰਫ਼ ਮਹਿਲਾ ਉਮੀਦਵਾਰਾਂ ਲਈ। | |
| `r_gender_other` | Only for {g} candidates. | ਸਿਰਫ਼ {g} ਉਮੀਦਵਾਰਾਂ ਲਈ। | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | ਸੈਕੰਡਰੀ ਸਕੂਲ ਪੱਧਰ 'ਤੇ ਰਾਜ ਦੀ ਸਰਕਾਰੀ/ਖੇਤਰੀ ਭਾਸ਼ਾ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ। | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | 12ਵੀਂ ਵਿੱਚ ਗਣਿਤ ਵਿੱਚ 60% ਜਾਂ ਡਿਗਰੀ ਵਿੱਚ ਸਟੈਟਿਸਟਿਕਸ ਇੱਕ ਵਿਸ਼ੇ ਵਜੋਂ ਹੋਣਾ ਚਾਹੀਦਾ ਹੈ। | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ਡਿਗਰੀ ਸਟੈਟਿਸਟਿਕਸ, ਗਣਿਤ, ਇਕਨਾਮਿਕਸ, ਡੈਮੋਗ੍ਰਾਫੀ, ਪਾਪੂਲੇਸ਼ਨ ਸਟੱਡੀਜ਼, ਆਪ੍ਰੇਸ਼ਨਜ਼ ਰਿਸਰਚ, IT, ਕੰਪਿਊਟਰ ਸਾਇੰਸ/ਇੰਜੀਨੀਅਰਿੰਗ/ਐਪਲੀਕੇਸ਼ਨਜ਼, ਡਾਟਾ ਸਾਇੰਸ ਜਾਂ AI ਵਿੱਚ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ। | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ਫਿਜ਼ੀਕਲ ਟੈਸਟ ਵਾਲੇ ਦਿਨ ਵੈਧ LMV (ਮੋਟਰਸਾਈਕਲ ਅਤੇ ਕਾਰ) ਡਰਾਈਵਿੰਗ ਲਾਇਸੈਂਸ ਹੋਣਾ ਚਾਹੀਦਾ ਹੈ। | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ਫਿਜ਼ਿਕਸ, ਕੈਮਿਸਟਰੀ ਅਤੇ ਗਣਿਤ ਨਾਲ B.Sc. ਚਾਹੀਦੀ ਹੈ, ਜਾਂ 10ਵੀਂ ਦੇ ਨਾਲ ਮਕੈਨੀਕਲ, ਸਿਵਲ, ਇਲੈਕਟ੍ਰੀਕਲ ਜਾਂ ਕਿਸੇ ਦਰਜ ਇੰਜੀਨੀਅਰਿੰਗ ਬ੍ਰਾਂਚ ਵਿੱਚ 3 ਸਾਲਾ ਡਿਪਲੋਮਾ। | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | ਫਿਜ਼ੀਕਲ ਅਤੇ ਮੈਡੀਕਲ ਮਾਪਦੰਡ ਲਾਗੂ ਹਨ (ਨੋਟਿਸ ਦੇਖੋ)। | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | ਬਾਰਡਰ ਰੋਡਜ਼ ਆਰਗੇਨਾਈਜ਼ੇਸ਼ਨ ਦੀਆਂ ਅਸਾਮੀਆਂ: ਸਿਰਫ਼ ਮਰਦ, ਫਿਜ਼ੀਕਲ ਮਾਪਦੰਡ ਲਾਗੂ। | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | ਕੁਝ ਦਫ਼ਤਰਾਂ (ਖਪਤਕਾਰ ਮਾਮਲੇ ਅਤੇ ਖੁਰਾਕ, SSC, ਸੱਭਿਆਚਾਰ) ਵਿੱਚ ਇਸ ਪੋਸਟ ਲਈ ਗਣਿਤ ਸਮੇਤ 12ਵੀਂ ਸਾਇੰਸ ਚਾਹੀਦੀ ਹੈ। | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | ਚੋਣ ਵਿੱਚ ਸਟੈਨੋਗ੍ਰਾਫੀ ਸਕਿੱਲ ਟੈਸਟ ਵੀ ਸ਼ਾਮਲ ਹੈ। | |
| `listWord` | Lists: {n} | ਲਿਸਟਾਂ: {n} | |
