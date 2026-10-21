# മലയാളം (ml) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
`node research/lang_review.mjs`로 코드에서 만들어지므로, 문구를 고치면(web/lang/ml.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/ml.js)

| 키 | English | മലയാളം | OK? |
|---|---|---|---|
| `appName` | Sarkari Job Alerts | സർക്കാരി ജോബ് അലർട്ട് | |
| `tabHome` | Home | ഹോം | |
| `tabMore` | More | കൂടുതൽ | |
| `loading` | Loading… | ലോഡ് ചെയ്യുന്നു… | |
| `homeComing` | Exam dates in the next 30 days | അടുത്ത 30 ദിവസത്തെ പരീക്ഷാ തീയതികൾ | |
| `homeLatest` | Latest updates | പുതിയ അപ്ഡേറ്റുകൾ | |
| `inDays` | in {n} days | {n} ദിവസത്തിനുള്ളിൽ | |
| `onNow` | on now | ഇപ്പോൾ നടക്കുന്നു | |
| `today` | today | ഇന്ന് | |
| `theme` | Theme | തീം | |
| `themeAuto` | Auto | ഓട്ടോ | |
| `themeLight` | Light | ലൈറ്റ് | |
| `themeDark` | Dark | ഡാർക്ക് | |
| `language` | Language | ഭാഷ | |
| `howToApply` | How to apply | എങ്ങനെ അപേക്ഷിക്കാം | |
| `documents` | Keep these documents ready | ഈ രേഖകൾ തയ്യാറാക്കി വെക്കുക | |
| `helplineTitle` | SSC helplines | SSC ഹെൽപ്‌ലൈനുകൾ | |
| `helplineNote` | For problems filling the form or with the computer-based exam. | ഫോം പൂരിപ്പിക്കുന്നതിലോ കമ്പ്യൂട്ടർ അധിഷ്ഠിത പരീക്ഷയിലോ പ്രശ്നമുണ്ടെങ്കിൽ. | |
| `typingTitle` | Typing / data entry speed practice | ടൈപ്പിംഗ് / ഡാറ്റാ എൻട്രി സ്പീഡ് പരിശീലനം | |
| `typingIntro` | Type the passage below. The timer starts with your first key. Speeds are in key depressions per hour, the way SSC states them. | താഴെയുള്ള ഭാഗം ടൈപ്പ് ചെയ്യുക. ആദ്യത്തെ കീ അമർത്തുമ്പോൾ ടൈമർ തുടങ്ങും. SSC പറയുന്നതുപോലെ, ഒരു മണിക്കൂറിലെ കീ ഡിപ്രഷനുകളിലാണ് (key depressions per hour) സ്പീഡ് കാണിക്കുന്നത്. | |
| `typingTest` | Standard | സ്റ്റാൻഡേർഡ് | |
| `typingMinutes` | Time | സമയം | |
| `typingFull` | Full ({n} min) | മുഴുവൻ ({n} മിനിറ്റ്) | |
| `typingStart` | Start again | വീണ്ടും തുടങ്ങുക | |
| `typingDone` | Time is up. | സമയം കഴിഞ്ഞു. | |
| `typingResult` | {kdph} key depressions an hour (≈ {wpm} w.p.m.), {accuracy}% accurate, {correct} correct of {typed} typed | മണിക്കൂറിൽ {kdph} കീ ഡിപ്രഷനുകൾ (≈ {wpm} w.p.m.), {accuracy}% കൃത്യത, ടൈപ്പ് ചെയ്ത {typed}-ൽ {correct} ശരി | |
| `typingMeets` | Meets the {name} speed ({kdph}/hour). | {name} സ്പീഡ് എത്തി ({kdph}/മണിക്കൂർ). | |
| `typingBelow` | Below the {name} speed ({kdph}/hour): keep practising. | {name} സ്പീഡിനേക്കാൾ കുറവ് ({kdph}/മണിക്കൂർ): പരിശീലനം തുടരുക. | |
| `typingNote` | Practice only: SSC marks errors under its own evaluation guidelines. Hindi typing practice is not covered here. | പരിശീലനത്തിന് മാത്രം: SSC സ്വന്തം മൂല്യനിർണയ മാർഗനിർദേശങ്ങൾ പ്രകാരമാണ് തെറ്റുകൾ കണക്കാക്കുന്നത്. ഹിന്ദി ടൈപ്പിംഗ് പരിശീലനം ഇവിടെ ഇല്ല. | |
| `faqTitle` | Frequently asked | പതിവ് ചോദ്യങ്ങൾ | |
| `faqLast` | What is the last date to apply for {exam}? | {exam}-ന് അപേക്ഷിക്കാനുള്ള അവസാന തീയതി എന്നാണ്? | |
| `faqLastA` | The last date to apply is {date}; fee payment closes on {fee}. | അപേക്ഷിക്കാനുള്ള അവസാന തീയതി {date}; ഫീസ് അടയ്ക്കാനുള്ള അവസാന തീയതി {fee}. | |
| `faqAge` | What is the age limit for {exam}? | {exam}-ന്റെ പ്രായപരിധി എത്രയാണ്? | |
| `faqFee` | What is the application fee? | അപേക്ഷാ ഫീസ് എത്രയാണ്? | |
| `faqFeeA` | ₹{amount} for General, OBC and EWS candidates. {exempt} pay no fee. | ജനറൽ, OBC, EWS ഉദ്യോഗാർഥികൾക്ക് ₹{amount}. {exempt} ഫീസ് അടയ്ക്കേണ്ടതില്ല. | |
| `faqSalary` | What is the salary? | ശമ്പളം എത്രയാണ്? | |
| `faqExam` | When is the {exam} exam? | {exam} പരീക്ഷ എന്നാണ്? | |
| `faqVacancy` | How many vacancies are there? | എത്ര ഒഴിവുകളുണ്ട്? | |
| `faqQual` | What qualification is needed? | എന്ത് യോഗ്യത വേണം? | |
| `aboutTitle` | About this app | ഈ ആപ്പിനെക്കുറിച്ച് | |
| `aboutText` | A free, unofficial helper for people preparing for government jobs in India. Notices, dates and rules are taken from the recruiters' official websites and notices, with references. No sign-up, no personal data collected: your details and roll number stay on this phone. | ഇന്ത്യയിൽ സർക്കാർ ജോലിക്ക് തയ്യാറെടുക്കുന്നവർക്കുള്ള സൗജന്യവും അനൗദ്യോഗികവുമായ ഒരു സഹായി. അറിയിപ്പുകൾ, തീയതികൾ, നിയമങ്ങൾ എന്നിവ റിക്രൂട്ടിംഗ് ഏജൻസികളുടെ ഔദ്യോഗിക വെബ്സൈറ്റുകളിൽ നിന്നും നോട്ടിഫിക്കേഷനുകളിൽ നിന്നും ഉറവിടം സഹിതം എടുത്തതാണ്. സൈൻ-അപ്പ് വേണ്ട, വ്യക്തിഗത വിവരങ്ങളൊന്നും ശേഖരിക്കുന്നില്ല: നിങ്ങളുടെ വിവരങ്ങളും റോൾ നമ്പറും ഈ ഫോണിൽ തന്നെ ഇരിക്കും. | |
| `sourceRef` | Source: {ref} | ഉറവിടം: {ref} | |
| `toastSaved` | Saved | സേവ് ചെയ്തു | |
| `toastRemoved` | Removed | നീക്കം ചെയ്തു | |
| `toastFollow` | You follow this exam | നിങ്ങൾ ഈ പരീക്ഷ ഫോളോ ചെയ്യുന്നു | |
| `toastUnfollow` | Unfollowed | ഫോളോ ഒഴിവാക്കി | |
| `settings` | Settings | സെറ്റിംഗ്സ് | |
| `toolsTitle` | Tools | ടൂളുകൾ | |
| `tabResult` | Result | റിസൾട്ട് | |
| `tabForMe` | For me | എനിക്കായി | |
| `resultTitle` | Check your result by roll number | റോൾ നമ്പർ ഉപയോഗിച്ച് റിസൾട്ട് നോക്കുക | |
| `resultNote` | Searches the result lists SSC published in the last 120 days, without downloading the big PDFs. Only the last two digits of your roll number are used to fetch a small file. | കഴിഞ്ഞ 120 ദിവസത്തിനുള്ളിൽ SSC പ്രസിദ്ധീകരിച്ച റിസൾട്ട് ലിസ്റ്റുകളിൽ തിരയുന്നു, വലിയ PDF-കൾ ഡൗൺലോഡ് ചെയ്യാതെ തന്നെ. ഒരു ചെറിയ ഫയൽ എടുക്കാൻ നിങ്ങളുടെ റോൾ നമ്പറിന്റെ അവസാന രണ്ട് അക്കങ്ങൾ മാത്രമേ ഉപയോഗിക്കൂ. | |
| `rollNo` | Roll number | റോൾ നമ്പർ | |
| `resultExam` | Result | റിസൾട്ട് | |
| `find` | Find | തിരയുക | |
| `rollInvalid` | Enter the roll number as printed on your admit card (8-14 digits). | അഡ്മിറ്റ് കാർഡിൽ ഉള്ളതുപോലെ റോൾ നമ്പർ നൽകുക (8-14 അക്കങ്ങൾ). | |
| `foundIn` | Found in: {list} | ഇതിൽ ഉണ്ട്: {list} | |
| `notIn` | Not in: {list} | ഇതിൽ ഇല്ല: {list} | |
| `notFoundAny` | Your roll number is not in these lists. That alone does not mean you did not qualify: check the official PDFs below. | ഈ ലിസ്റ്റുകളിൽ നിങ്ങളുടെ റോൾ നമ്പർ ഇല്ല. അതുകൊണ്ട് മാത്രം നിങ്ങൾ യോഗ്യത നേടിയില്ല എന്ന് അർഥമില്ല: താഴെയുള്ള ഔദ്യോഗിക PDF-കൾ പരിശോധിക്കുക. | |
| `officialFiles` | Official files of this notice | ഈ അറിയിപ്പിന്റെ ഔദ്യോഗിക ഫയലുകൾ | |
| `listCount` | {n} roll numbers | {n} റോൾ നമ്പറുകൾ | |
| `noLists` | No result lists to search right now. | ഇപ്പോൾ തിരയാൻ റിസൾട്ട് ലിസ്റ്റുകളൊന്നുമില്ല. | |
| `forMeTitle` | Exams that suit you | നിങ്ങൾക്ക് പറ്റിയ പരീക്ഷകൾ | |
| `forMeIntro` | Fill in your details once. You see every SSC exam you can apply for, its status, and what comes next. | നിങ്ങളുടെ വിവരങ്ങൾ ഒരിക്കൽ പൂരിപ്പിക്കുക. നിങ്ങൾക്ക് അപേക്ഷിക്കാവുന്ന എല്ലാ SSC പരീക്ഷകളും, അവയുടെ സ്റ്റാറ്റസും, അടുത്തത് എന്താണെന്നും കാണാം. | |
| `forMeEdit` | Edit my details | എന്റെ വിവരങ്ങൾ മാറ്റുക | |
| `forMeMatch` | For your qualification (rules not added yet: read the Notice) | നിങ്ങളുടെ യോഗ്യതയ്ക്ക് (നിയമങ്ങൾ ഇതുവരെ ചേർത്തിട്ടില്ല: നോട്ടിഫിക്കേഷൻ വായിക്കുക) | |
| `forMeCalendar` | Coming up for your qualification (tentative) | നിങ്ങളുടെ യോഗ്യതയ്ക്ക് വരാനിരിക്കുന്നവ (താൽക്കാലികം) | |
| `saveDetails` | Show my exams | എന്റെ പരീക്ഷകൾ കാണിക്കുക | |
| `whenTitle` | What has happened and what is next | എന്ത് കഴിഞ്ഞു, അടുത്തത് എന്ത് | |
| `st_done` | Done | കഴിഞ്ഞു | |
| `st_now` | Now | ഇപ്പോൾ | |
| `st_next` | Next | അടുത്തത് | |
| `st_later` | Later | പിന്നീട് | |
| `stage_notice` | Notice published | നോട്ടിഫിക്കേഷൻ പ്രസിദ്ധീകരിച്ചു | |
| `stage_apply` | Online application | ഓൺലൈൻ അപേക്ഷ | |
| `stage_admit_card` | Admit card / exam city | അഡ്മിറ്റ് കാർഡ് / പരീക്ഷാ നഗരം | |
| `stage_answer_key` | Answer key | ആൻസർ കീ | |
| `stage_result` | Result | റിസൾട്ട് | |
| `stage_exam` | {label} | {label} | |
| `notAnnounced` | Date not announced by SSC yet | SSC ഇതുവരെ തീയതി പ്രഖ്യാപിച്ചിട്ടില്ല | |
| `payRange` | Basic pay ₹{from}–{to} a month (Level {l}; allowances extra) | അടിസ്ഥാന ശമ്പളം മാസം ₹{from}–{to} (ലെവൽ {l}; അലവൻസുകൾ പുറമെ) | |
| `savedLink` | ★ Saved | ★ സേവ് ചെയ്തവ | |
| `disclaimer` | Not a government website and not linked to any recruiting body or government. Everything here comes from the recruiters' official websites, and every update links to the official file or page. Always check the official notice before you apply. | ഇതൊരു സർക്കാർ വെബ്സൈറ്റ് അല്ല, ഒരു റിക്രൂട്ടിംഗ് ഏജൻസിയുമായോ സർക്കാരുമായോ ബന്ധവുമില്ല. ഇവിടെയുള്ളതെല്ലാം റിക്രൂട്ടിംഗ് ഏജൻസികളുടെ ഔദ്യോഗിക വെബ്സൈറ്റുകളിൽ നിന്നുള്ളതാണ്, ഓരോ അപ്ഡേറ്റിലും ഔദ്യോഗിക ഫയലിന്റെയോ പേജിന്റെയോ ലിങ്ക് ഉണ്ട്. അപേക്ഷിക്കുന്നതിന് മുമ്പ് എപ്പോഴും ഔദ്യോഗിക നോട്ടിഫിക്കേഷൻ പരിശോധിക്കുക. | |
| `tabExams` | Exams | പരീക്ഷകൾ | |
| `filters` | Filters | ഫിൽട്ടർ | |
| `kindAll` | All | എല്ലാം | |
| `kindRecruitment` | New jobs | പുതിയ ജോലികൾ | |
| `kindResult` | Results | റിസൾട്ടുകൾ | |
| `kindAdmit` | Admit cards | അഡ്മിറ്റ് കാർഡുകൾ | |
| `kindAnswer` | Answer keys | ആൻസർ കീകൾ | |
| `kindAmendment` | Changes | മാറ്റങ്ങൾ | |
| `kindVacancy` | Vacancies | ഒഴിവുകൾ | |
| `kind_recruitment` | New job | പുതിയ ജോലി | |
| `kind_amendment` | Change | മാറ്റം | |
| `kind_result` | Result | റിസൾട്ട് | |
| `kind_answer_key` | Answer key | ആൻസർ കീ | |
| `kind_admit_card` | Admit card | അഡ്മിറ്റ് കാർഡ് | |
| `kind_vacancy` | Vacancy | ഒഴിവ് | |
| `kind_schedule` | Exam dates | പരീക്ഷാ തീയതികൾ | |
| `kind_notice` | Notice | അറിയിപ്പ് | |
| `search` | Search jobs, exams, results | ജോലി, പരീക്ഷ, റിസൾട്ട് തിരയുക | |
| `allExams` | All exams | എല്ലാ പരീക്ഷകളും | |
| `myExams` | My exams only | എന്റെ പരീക്ഷകൾ മാത്രം | |
| `anyQualification` | Any qualification | ഏത് യോഗ്യതയും | |
| `qual_10` | Class 10 | പത്താം ക്ലാസ് | |
| `qual_12` | Class 12 | പ്ലസ് ടു | |
| `qual_diploma` | Diploma | ഡിപ്ലോമ | |
| `qual_graduate` | Bachelor's degree | ബിരുദം | |
| `qual_postgraduate` | Master's degree | ബിരുദാനന്തര ബിരുദം | |
| `departmental` | Departmental exams | ഡിപ്പാർട്ട്മെന്റൽ പരീക്ഷകൾ | |
| `updated` | Updated {time} IST | {time} IST-ന് അപ്ഡേറ്റ് ചെയ്തു | |
| `newSince` | {n} new since your last visit | കഴിഞ്ഞ തവണ വന്നതിന് ശേഷം {n} പുതിയവ | |
| `cutoffTitle` | Cut-off marks | കട്ട്-ഓഫ് മാർക്കുകൾ | |
| `cutoffNote` | SSC gives cut-offs only inside the result write-up. The numbers are not copied here (one wrong figure would mislead): each link opens SSC's PDF at the page where the cut-off details start. | SSC കട്ട്-ഓഫുകൾ റിസൾട്ട് വിശദീകരണക്കുറിപ്പിനുള്ളിൽ മാത്രമേ നൽകുന്നുള്ളൂ. നമ്പറുകൾ ഇവിടെ പകർത്തിയിട്ടില്ല (ഒരു തെറ്റായ നമ്പർ പോലും തെറ്റിദ്ധരിപ്പിക്കും): ഓരോ ലിങ്കും കട്ട്-ഓഫ് വിവരങ്ങൾ തുടങ്ങുന്ന പേജിൽ SSC-യുടെ PDF തുറക്കും. | |
| `cutoffFrom` | Open at page {p} | പേജ് {p}-ൽ തുറക്കുക | |
| `cutoffPages` | mentioned on p. {pages} | പേജ് {pages}-ൽ ഉണ്ട് | |
| `noCutoffs` | No cut-off found in the result write-ups of the last 120 days. | കഴിഞ്ഞ 120 ദിവസത്തെ റിസൾട്ട് കുറിപ്പുകളിൽ കട്ട്-ഓഫ് ഒന്നും കണ്ടെത്തിയില്ല. | |
| `alertsTitle` | Alerts on this phone | ഈ ഫോണിൽ അലർട്ടുകൾ | |
| `alertsIntro` | A notification when there is news for an exam you follow, and two days before its application closes. | നിങ്ങൾ ഫോളോ ചെയ്യുന്ന പരീക്ഷയ്ക്ക് പുതിയ വിവരം വരുമ്പോഴും, അപേക്ഷ അവസാനിക്കുന്നതിന് രണ്ട് ദിവസം മുമ്പും ഒരു അലർട്ട് ലഭിക്കും. | |
| `alertsOn` | Turn on alerts | അലർട്ടുകൾ ഓൺ ചെയ്യുക | |
| `alertsOff` | Turn off alerts | അലർട്ടുകൾ ഓഫ് ചെയ്യുക | |
| `alertsNoExams` | Follow an exam first (Exams → Follow). | ആദ്യം ഒരു പരീക്ഷ ഫോളോ ചെയ്യുക (പരീക്ഷകൾ → ഫോളോ). | |
| `alertsActive` | On for {n} exams. Chrome decides when to check, more often if you open the app often, so news can reach you hours after it is posted. | {n} പരീക്ഷകൾക്ക് ഓൺ ആണ്. എപ്പോൾ പരിശോധിക്കണമെന്ന് Chrome ആണ് തീരുമാനിക്കുന്നത്; ആപ്പ് പതിവായി തുറന്നാൽ കൂടുതൽ തവണ പരിശോധിക്കും. അതിനാൽ വിവരം പോസ്റ്റ് ചെയ്ത് മണിക്കൂറുകൾക്ക് ശേഷമായിരിക്കാം നിങ്ങൾക്ക് ലഭിക്കുന്നത്. | |
| `alertsBlocked` | Notifications are blocked for this site. Allow them in the browser's site settings. | ഈ സൈറ്റിന് നോട്ടിഫിക്കേഷനുകൾ ബ്ലോക്ക് ചെയ്തിരിക്കുന്നു. ബ്രൗസറിന്റെ സൈറ്റ് സെറ്റിംഗ്സിൽ അവ അനുവദിക്കുക. | |
| `alertsUnsupported` | This browser can't check in the background. On Android, install this app from Chrome first. Otherwise, use the calendar reminders on each exam page. | ഈ ബ്രൗസറിന് ബാക്ക്ഗ്രൗണ്ടിൽ പരിശോധിക്കാൻ കഴിയില്ല. Android-ൽ ആദ്യം Chrome-ൽ നിന്ന് ഈ ആപ്പ് ഇൻസ്റ്റാൾ ചെയ്യുക. അല്ലെങ്കിൽ, ഓരോ പരീക്ഷാ പേജിലുമുള്ള കലണ്ടർ റിമൈൻഡറുകൾ ഉപയോഗിക്കുക. | |
| `alertNew` | {exam}: {kind} | {exam}: {kind} | |
| `alertMore` | {n} more updates for your exams | നിങ്ങളുടെ പരീക്ഷകൾക്ക് {n} അപ്ഡേറ്റുകൾ കൂടി | |
| `alertLastDays` | {title}: applications close in {n} days | {title}: അപേക്ഷ {n} ദിവസത്തിനുള്ളിൽ അവസാനിക്കും | |
| `alertLastDay` | {title}: today is the last day to apply | {title}: അപേക്ഷിക്കാനുള്ള അവസാന ദിവസം ഇന്നാണ് | |
| `installTitle` | Install the app | ആപ്പ് ഇൻസ്റ്റാൾ ചെയ്യുക | |
| `installBtn` | Install | ഇൻസ്റ്റാൾ | |
| `installNote` | Opens like an app, works offline, and allows alerts on Android. | ഒരു ആപ്പ് പോലെ തുറക്കും, ഇന്റർനെറ്റ് ഇല്ലാതെയും പ്രവർത്തിക്കും, Android-ൽ അലർട്ടുകളും ലഭിക്കും. | |
| `installIos` | On iPhone: tap Share, then "Add to Home Screen". | iPhone-ൽ: Share ടാപ്പ് ചെയ്ത്, "Add to Home Screen" തിരഞ്ഞെടുക്കുക. | |
| `telegramJoin` | Telegram | Telegram | |
| `toastFollowAlerts` | Following. For phone alerts: More → Turn on alerts | ഫോളോ ചെയ്യുന്നു. ഫോൺ അലർട്ടുകൾക്ക്: കൂടുതൽ → അലർട്ടുകൾ ഓൺ ചെയ്യുക | |
| `rollPlaceholder` | Roll number on your admit card | അഡ്മിറ്റ് കാർഡിലെ റോൾ നമ്പർ | |
| `alertsActivePush` | On for {n} exams. SSC notices usually reach you within an hour of being posted; results and other boards' notices can take up to 3 hours. | {n} പരീക്ഷകൾക്ക് ഓൺ ആണ്. SSC അറിയിപ്പുകൾ സാധാരണയായി പോസ്റ്റ് ചെയ്ത് ഒരു മണിക്കൂറിനുള്ളിൽ ലഭിക്കും; റിസൾട്ടുകൾക്കും മറ്റ് ബോർഡുകളുടെ അറിയിപ്പുകൾക്കും 3 മണിക്കൂർ വരെ എടുക്കാം. | |
| `alertsUnsupportedPush` | This browser can't show alerts. On iPhone, add this app to the Home Screen first (Share → Add to Home Screen), then open it from there. | ഈ ബ്രൗസറിൽ അലർട്ടുകൾ കാണിക്കാൻ കഴിയില്ല. iPhone-ൽ ആദ്യം ഈ ആപ്പ് Home Screen-ലേക്ക് ചേർക്കുക (Share → Add to Home Screen), എന്നിട്ട് അവിടെ നിന്ന് തുറക്കുക. | |
| `alertsFailed` | Couldn't turn on alerts. Check your connection and try again. | അലർട്ടുകൾ ഓൺ ചെയ്യാൻ കഴിഞ്ഞില്ല. ഇന്റർനെറ്റ് കണക്ഷൻ പരിശോധിച്ച് വീണ്ടും ശ്രമിക്കുക. | |
| `allBoards` | All recruiters | എല്ലാ റിക്രൂട്ടിംഗ് ഏജൻസികളും | |
| `newBadge` | NEW | പുതിയത് | |
| `nothing` | Nothing here in the last 120 days. | കഴിഞ്ഞ 120 ദിവസത്തിനുള്ളിൽ ഇവിടെ ഒന്നുമില്ല. | |
| `loadError` | Couldn't load. Check your internet connection and try again. | ലോഡ് ചെയ്യാൻ കഴിഞ്ഞില്ല. ഇന്റർനെറ്റ് കണക്ഷൻ പരിശോധിച്ച് വീണ്ടും ശ്രമിക്കുക. | |
| `offline` | Offline: showing the copy saved on this phone. | ഓഫ്‌ലൈൻ: ഈ ഫോണിൽ സേവ് ചെയ്ത കോപ്പി കാണിക്കുന്നു. | |
| `save` | Save | സേവ് | |
| `saved` | Saved | സേവ് ചെയ്തു | |
| `share` | Share | ഷെയർ | |
| `pdf` | PDF | PDF | |
| `officialPage` | Official page | ഔദ്യോഗിക പേജ് | |
| `ist` | IST | IST | |
| `shareText` | {title} ⏎ Official link: {url} ⏎ via {app} (not a government site) | {title} ⏎ ഔദ്യോഗിക ലിങ്ക്: {url} ⏎ {app} വഴി (സർക്കാർ സൈറ്റ് അല്ല) | |
| `examsIntro` | Each exam with its latest Notice, dates, fees, age limits and every notice since. | ഓരോ പരീക്ഷയും അതിന്റെ പുതിയ നോട്ടിഫിക്കേഷൻ, തീയതികൾ, ഫീസ്, പ്രായപരിധി, പിന്നീട് വന്ന എല്ലാ അറിയിപ്പുകളും സഹിതം. | |
| `follow` | Follow | ഫോളോ | |
| `following` | Following | ഫോളോയിംഗ് | |
| `minQual` | Qualification: {q} | യോഗ്യത: {q} | |
| `qualUnknown` | Qualification: see the Notice | യോഗ്യത: നോട്ടിഫിക്കേഷൻ കാണുക | |
| `calendarTitle` | SSC's tentative calendar | SSC-യുടെ താൽക്കാലിക പരീക്ഷാ കലണ്ടർ | |
| `calendarNote` | Plans, not the Notice: real dates are in each Notice and can differ. | ഇത് പ്ലാൻ മാത്രമാണ്, നോട്ടിഫിക്കേഷൻ അല്ല: യഥാർഥ തീയതികൾ ഓരോ നോട്ടിഫിക്കേഷനിലുമാണ്, അവ വ്യത്യാസപ്പെടാം. | |
| `resourcesTitle` | Official resources | ഔദ്യോഗിക റിസോഴ്സുകൾ | |
| `res_syllabus` | Syllabus | സിലബസ് | |
| `res_vacancy` | Vacancies (tentative / final) | ഒഴിവുകൾ (താൽക്കാലികം / അന്തിമം) | |
| `res_previous_papers` | Previous year papers | മുൻ വർഷ ചോദ്യപേപ്പറുകൾ | |
| `res_certificate_formats` | Certificate formats | സർട്ടിഫിക്കറ്റ് ഫോർമാറ്റുകൾ | |
| `res_instructions` | Instructions | നിർദേശങ്ങൾ | |
| `back` | ← All exams | ← എല്ലാ പരീക്ഷകളും | |
| `latestNotice` | Latest Notice | പുതിയ നോട്ടിഫിക്കേഷൻ | |
| `importantDates` | Important dates | പ്രധാന തീയതികൾ | |
| `applyWindow` | Apply online | ഓൺലൈൻ അപേക്ഷ | |
| `feeLastDate` | Fee payment last date | ഫീസ് അടയ്ക്കാനുള്ള അവസാന തീയതി | |
| `correctionWindow` | Form correction window | ഫോം തിരുത്താനുള്ള സമയം | |
| `tentative` | tentative | താൽക്കാലികം | |
| `applicationFee` | Application fee | അപേക്ഷാ ഫീസ് | |
| `feeGeneral` | General / OBC / EWS | ജനറൽ / OBC / EWS | |
| `feeExempt` | Exempt (₹0) | ഫീസ് ഇളവ് (₹0) | |
| `exempt_female` | Women | വനിതകൾ | |
| `exempt_SC` | SC | SC | |
| `exempt_ST` | ST | ST | |
| `exempt_PwBD` | PwBD | PwBD | |
| `exempt_ESM` | Ex-servicemen | വിമുക്തഭടന്മാർ | |
| `correctionFee` | Correction: ₹{a} first time, ₹{b} second time (all candidates) | തിരുത്തൽ: ആദ്യ തവണ ₹{a}, രണ്ടാം തവണ ₹{b} (എല്ലാ ഉദ്യോഗാർഥികൾക്കും) | |
| `ageLimit` | Age limit (as on {date}) | പ്രായപരിധി ({date}-ലെ കണക്കിൽ) | |
| `ageBand` | {band} years: born {from} to {to} | {band} വയസ്സ്: {from} മുതൽ {to} വരെ ജനിച്ചവർ | |
| `relaxation` | Upper age relaxation | ഉയർന്ന പ്രായപരിധിയിൽ ഇളവ് | |
| `relaxYears` | {n} years | {n} വർഷം | |
| `notComputed` | Not calculated here: {list} | ഇവിടെ കണക്കാക്കിയിട്ടില്ല: {list} | |
| `vacancies` | Vacancies | ഒഴിവുകൾ | |
| `vacancyCount` | About {n} ({tentative}) | ഏകദേശം {n} ({tentative}) | |
| `posts` | Posts | തസ്തികകൾ | |
| `importantLinks` | Important links | പ്രധാന ലിങ്കുകൾ | |
| `applyOnline` | Apply online (ssc.gov.in) | ഓൺലൈനായി അപേക്ഷിക്കുക (ssc.gov.in) | |
| `noticePdf` | Official Notice (PDF) | ഔദ്യോഗിക നോട്ടിഫിക്കേഷൻ (PDF) | |
| `amendmentPdf` | Change dated {date} | {date}-ലെ മാറ്റം | |
| `allNotices` | All notices for this exam | ഈ പരീക്ഷയുടെ എല്ലാ അറിയിപ്പുകളും | |
| `addCalendar` | Add dates to my calendar | തീയതികൾ എന്റെ കലണ്ടറിൽ ചേർക്കുക | |
| `checkThis` | Check my eligibility | എന്റെ യോഗ്യത പരിശോധിക്കുക | |
| `status_open` | Applications open till {date} | {date} വരെ അപേക്ഷിക്കാം | |
| `status_upcoming` | Applications open on {date} | അപേക്ഷ {date}-ന് തുടങ്ങും | |
| `status_closed` | Applications closed on {date} | അപേക്ഷ {date}-ന് അവസാനിച്ചു | |
| `daysLeft` | {n} days left | {n} ദിവസം ബാക്കി | |
| `lastDay` | Last day today | ഇന്ന് അവസാന ദിവസം | |
| `noRules` | No eligibility rules for this exam yet: read the Notice. | ഈ പരീക്ഷയ്ക്കുള്ള യോഗ്യതാ നിയമങ്ങൾ ഇതുവരെ ചേർത്തിട്ടില്ല: നോട്ടിഫിക്കേഷൻ വായിക്കുക. | |
| `examLabel` | Exam | പരീക്ഷ | |
| `dob` | Date of birth (as in Class 10 certificate) | ജനനത്തീയതി (പത്താം ക്ലാസ് സർട്ടിഫിക്കറ്റിലേതുപോലെ) | |
| `category` | Category | വിഭാഗം | |
| `catUR` | General (UR) | ജനറൽ (UR) | |
| `catEWS` | EWS | EWS | |
| `catOBC` | OBC (non-creamy layer) | OBC (നോൺ-ക്രീമി ലെയർ) | |
| `catSC` | SC | SC | |
| `catST` | ST | ST | |
| `education` | Highest qualification | ഏറ്റവും ഉയർന്ന യോഗ്യത | |
| `edu10` | Class 10 | പത്താം ക്ലാസ് | |
| `edu12` | Class 12 | പ്ലസ് ടു | |
| `eduGraduate` | Bachelor's degree or higher | ബിരുദമോ അതിന് മുകളിലോ | |
| `gender` | Gender | ലിംഗം | |
| `male` | Male | പുരുഷൻ | |
| `female` | Female | സ്ത്രീ | |
| `other` | Other | മറ്റുള്ളവ | |
| `appearing` | Final exam of that level taken, result not out yet | ആ തലത്തിലെ അവസാന പരീക്ഷ എഴുതി, റിസൾട്ട് ഇതുവരെ വന്നിട്ടില്ല | |
| `pwbd` | Person with benchmark disability (PwBD) | ബെഞ്ച്മാർക്ക് ഭിന്നശേഷിയുള്ള വ്യക്തി (PwBD) | |
| `otherCategory` | Ex-serviceman, Central Govt employee, or widow/divorced woman | വിമുക്തഭടൻ, കേന്ദ്ര സർക്കാർ ജീവനക്കാരൻ, അല്ലെങ്കിൽ വിധവ/വിവാഹമോചിതയായ സ്ത്രീ | |
| `check` | Check | പരിശോധിക്കുക | |
| `privacy` | Your answers stay on this device. Nothing is sent anywhere. | നിങ്ങളുടെ ഉത്തരങ്ങൾ ഈ ഉപകരണത്തിൽ തന്നെ ഇരിക്കും. ഒന്നും എവിടേക്കും അയയ്ക്കുന്നില്ല. | |
| `enterDob` | Enter your date of birth. | നിങ്ങളുടെ ജനനത്തീയതി നൽകുക. | |
| `closedNote` | Shown so you can prepare for the next cycle. | അടുത്ത തവണത്തേക്ക് തയ്യാറെടുക്കാൻ വേണ്ടി കാണിക്കുന്നു. | |
| `canApply` | {n} can apply | {n} എണ്ണത്തിന് അപേക്ഷിക്കാം | |
| `toCheck` | {n} to check | {n} എണ്ണം പരിശോധിക്കണം | |
| `notEligible` | {n} not eligible | {n} എണ്ണത്തിന് യോഗ്യതയില്ല | |
| `feeLine` | Fee: {fee}. Age relaxation applied: {n} years. Age is counted on {date}. | ഫീസ്: {fee}. പ്രായത്തിൽ നൽകിയ ഇളവ്: {n} വർഷം. പ്രായം കണക്കാക്കുന്നത് {date}-ലെ കണക്കിലാണ്. | |
| `feeNone` | none (exempt) | ഇല്ല (ഇളവ്) | |
| `st_eligible` | You can apply | അപേക്ഷിക്കാം | |
| `st_check` | Check one condition | ഒരു നിബന്ധന പരിശോധിക്കുക | |
| `st_ineligible` | Not eligible | യോഗ്യതയില്ല | |
| `payLevel` | Pay Level {l} · Group {g} | പേ ലെവൽ {l} · ഗ്രൂപ്പ് {g} | |
| `groupOnly` | Group {g} | ഗ്രൂപ്പ് {g} | |
| `bornWindow` | Born between {from} and {to} (for you) | {from} മുതൽ {to} വരെ ജനിച്ചവർ (നിങ്ങൾക്ക്) | |
| `rulesFrom` | Rules copied from the Notice dated {date} (checked {checked}). | {date}-ലെ നോട്ടിഫിക്കേഷനിൽ നിന്ന് എടുത്ത നിയമങ്ങൾ ({checked}-ന് പരിശോധിച്ചത്). | |
| `sigTitle` | Signature for SSC forms | SSC ഫോമിനുള്ള ഒപ്പ് | |
| `sigRule` | SSC asks for a JPEG of {min}-{max} KB, about {w} cm × {h} cm. Sign in black ink on white paper and take a clear photo. | SSC ആവശ്യപ്പെടുന്നത് {min}-{max} KB വലുപ്പമുള്ള JPEG ആണ്, ഏകദേശം {w} സെ.മീ × {h} സെ.മീ. വെള്ള പേപ്പറിൽ കറുത്ത മഷിയിൽ ഒപ്പിട്ട്, വ്യക്തമായ ഫോട്ടോ എടുക്കുക. | |
| `sigPick` | Choose signature photo | ഒപ്പിന്റെ ഫോട്ടോ തിരഞ്ഞെടുക്കുക | |
| `sigResult` | Ready: {kb} KB, {w}×{h} px | തയ്യാർ: {kb} KB, {w}×{h} px | |
| `sigFail` | Couldn't get the file between {min} and {max} KB. Try a sharper photo. | ഫയൽ {min}-നും {max} KB-ക്കും ഇടയിൽ ആക്കാൻ കഴിഞ്ഞില്ല. കൂടുതൽ വ്യക്തമായ ഒരു ഫോട്ടോ ഉപയോഗിച്ച് നോക്കുക. | |
| `sigDownload` | Download JPEG | JPEG ഡൗൺലോഡ് | |
| `photoTitle` | Your photograph | നിങ്ങളുടെ ഫോട്ടോ | |
| `photoRule` | SSC no longer takes an uploaded photo: the application form captures it live with your camera, and photographing an old photo gets the form rejected. Use good light, a plain background, camera at eye level, no cap, mask or glasses. | SSC ഇപ്പോൾ അപ്‌ലോഡ് ചെയ്ത ഫോട്ടോ സ്വീകരിക്കുന്നില്ല: അപേക്ഷാ ഫോം തന്നെ നിങ്ങളുടെ ക്യാമറ ഉപയോഗിച്ച് ലൈവ് ആയി ഫോട്ടോ എടുക്കും; പഴയ ഫോട്ടോയുടെ ഫോട്ടോ എടുത്താൽ ഫോം റിജക്ട് ആകും. നല്ല വെളിച്ചം, പ്ലെയിൻ ബാക്ക്ഗ്രൗണ്ട്, കണ്ണിന്റെ ഉയരത്തിൽ ക്യാമറ; തൊപ്പി, മാസ്ക്, കണ്ണട പാടില്ല. | |
| `photoSource` | Source: Notice para 9.4-9.5 (CHSL/CGL 2026) | ഉറവിടം: നോട്ടിഫിക്കേഷൻ ഖണ്ഡിക 9.4-9.5 (CHSL/CGL 2026) | |
| `ageTitle` | Age calculator | ഏജ് കാൽക്കുലേറ്റർ | |
| `ageOn` | Age on | ഈ തീയതിയിലെ പ്രായം | |
| `ageResult` | {y} years, {m} months, {d} days | {y} വർഷം, {m} മാസം, {d} ദിവസം | |
| `ageInvalid` | Date of birth must be before that date. | ജനനത്തീയതി ആ തീയതിക്ക് മുമ്പായിരിക്കണം. | |
| `docsTitle` | Certificate formats | സർട്ടിഫിക്കറ്റ് ഫോർമാറ്റുകൾ | |
| `docsNote` | SSC's formats for category, disability and ex-serviceman certificates. | വിഭാഗം (കാറ്റഗറി), ഭിന്നശേഷി, വിമുക്തഭട സർട്ടിഫിക്കറ്റുകൾക്കുള്ള SSC ഫോർമാറ്റുകൾ. | |
| `savedEmpty` | Nothing saved yet. Tap Save on any notice. | ഇതുവരെ ഒന്നും സേവ് ചെയ്തിട്ടില്ല. ഏത് അറിയിപ്പിലും സേവ് ടാപ്പ് ചെയ്യുക. | |
| `followedExams` | Exams you follow | നിങ്ങൾ ഫോളോ ചെയ്യുന്ന പരീക്ഷകൾ | |
| `chooseLanguage` | Choose your language | നിങ്ങളുടെ ഭാഷ തിരഞ്ഞെടുക്കുക | |
| `titlesNote` | Notice titles stay in the language the recruiter publishes them in (mostly English or Hindi). | അറിയിപ്പുകളുടെ തലക്കെട്ടുകൾ റിക്രൂട്ടിംഗ് ഏജൻസി പ്രസിദ്ധീകരിക്കുന്ന ഭാഷയിൽ തന്നെയായിരിക്കും (കൂടുതലും ഇംഗ്ലീഷ് അല്ലെങ്കിൽ ഹിന്ദി). | |
| `qaLatest` | Latest jobs | പുതിയ ജോലികൾ | |
| `qaResults` | Results | റിസൾട്ട് | |
| `qaAdmit` | Admit cards | അഡ്മിറ്റ് കാർഡ് | |
| `qaAnswer` | Answer keys | ആൻസർ കീ | |
| `qaEligibility` | Check eligibility | യോഗ്യത നോക്കാം | |
| `qaRoll` | Result by roll no. | റോൾ നമ്പർ റിസൾട്ട് | |
| `homeSectors` | Jobs by sector | മേഖല തിരിച്ച് ജോലികൾ | |
| `homeStates` | Jobs by state | സംസ്ഥാനം തിരിച്ച് ജോലികൾ | |
| `homeClosing` | Closing soon | ഉടൻ അവസാനിക്കുന്നവ | |
| `homeChannels` | Get every update on WhatsApp or Telegram | എല്ലാ അപ്ഡേറ്റുകളും WhatsApp-ലോ Telegram-ലോ നേടൂ | |
| `homeChannelsNote` | Free. One message per update, with the official link. | സൗജന്യം. ഓരോ അപ്ഡേറ്റിനും ഒരു മെസേജ്, ഔദ്യോഗിക ലിങ്ക് സഹിതം. | |
| `whatsappJoin` | WhatsApp | WhatsApp | |
| `shareWhatsapp` | WhatsApp | WhatsApp | |
| `trustLine` | Unofficial · Every update links to the official website | അനൗദ്യോഗികം · ഓരോ അപ്ഡേറ്റിലും ഔദ്യോഗിക വെബ്സൈറ്റിന്റെ ലിങ്ക് | |
| `sector_ssc` | SSC | SSC | |
| `sector_police` | Police | പോലീസ് | |
| `sector_defence` | Defence & CAPF | ഡിഫൻസ് & CAPF | |
| `sector_railway` | Railway | റെയിൽവേ | |
| `sector_bank` | Bank | ബാങ്ക് | |
| `sector_psu` | PSU | PSU | |
| `sector_science` | Science & space | ശാസ്ത്രം & ബഹിരാകാശം | |
| `sector_state` | State govt | സംസ്ഥാന സർക്കാർ | |
| `sector_teaching` | Teaching | അധ്യാപനം | |
| `state_all` | All India | അഖിലേന്ത്യ | |
| `state_UP` | Uttar Pradesh | ഉത്തർപ്രദേശ് | |
| `state_BR` | Bihar | ബിഹാർ | |
| `state_HR` | Haryana | ഹരിയാന | |
| `anySector` | All sectors | എല്ലാ മേഖലകളും | |
| `anyState` | All states | എല്ലാ സംസ്ഥാനങ്ങളും | |
| `jobsSector` | {name} jobs | {name} ജോലികൾ | |
| `jobsState` | Government jobs in {name} | {name}-ലെ സർക്കാർ ജോലികൾ | |
| `jobsKind` | {name}: latest | {name}: പുതിയവ | |
| `notCovered` | Nothing from this sector yet. We add a recruiter only after checking its official website, so this list will grow. | ഈ മേഖലയിൽ നിന്ന് ഇതുവരെ ഒന്നുമില്ല. ഒരു റിക്രൂട്ടിംഗ് ഏജൻസിയുടെ ഔദ്യോഗിക വെബ്സൈറ്റ് പരിശോധിച്ച ശേഷമേ ഞങ്ങൾ അത് ചേർക്കൂ, അതിനാൽ ഈ ലിസ്റ്റ് വളരും. | |
| `centralJobs` | Central govt (all India) | കേന്ദ്ര സർക്കാർ (അഖിലേന്ത്യ) | |
| `recruitersTitle` | Recruiters we follow | ഞങ്ങൾ ഫോളോ ചെയ്യുന്ന റിക്രൂട്ടിംഗ് ഏജൻസികൾ | |
| `recruitersNote` | Updates are read from these official websites. Tap one to open it. | ഈ ഔദ്യോഗിക വെബ്സൈറ്റുകളിൽ നിന്നാണ് അപ്ഡേറ്റുകൾ എടുക്കുന്നത്. തുറക്കാൻ ഒന്നിൽ ടാപ്പ് ചെയ്യുക. | |
| `moreUpdates` | See all updates | എല്ലാ അപ്ഡേറ്റുകളും കാണുക | |
| `r_age_below_min` | Too young: you must be at least {n} on {date}. | പ്രായം കുറവാണ്: {date}-ന് നിങ്ങൾക്ക് കുറഞ്ഞത് {n} വയസ്സ് ഉണ്ടായിരിക്കണം. | |
| `r_age_above_max` | Over the age limit: you must be under {n} on {date} (relaxation included). | പ്രായപരിധി കഴിഞ്ഞു: {date}-ന് നിങ്ങളുടെ പ്രായം {n}-ൽ താഴെയായിരിക്കണം (ഇളവ് ഉൾപ്പെടെ). | |
| `r_age_other_relaxation` | Over the general age limit. Ex-serviceman / Govt employee / widow relaxation may cover it: see Notice {ref}. | പൊതു പ്രായപരിധി കഴിഞ്ഞു. വിമുക്തഭടൻ / സർക്കാർ ജീവനക്കാരൻ / വിധവ എന്നിവർക്കുള്ള ഇളവ് ബാധകമായേക്കാം: നോട്ടിഫിക്കേഷൻ {ref} കാണുക. | |
| `r_education_graduate` | Needs a bachelor's degree. | ബിരുദം വേണം. | |
| `r_education_12` | Needs Class 12 pass. | പ്ലസ് ടു പാസ്സ് വേണം. | |
| `r_education_10` | Needs Class 10 pass. | പത്താം ക്ലാസ് പാസ്സ് വേണം. | |
| `r_result_needed_by` | Your result must be out by {date}. | {date}-നകം നിങ്ങളുടെ റിസൾട്ട് വന്നിരിക്കണം. | |
| `r_appearing_not_stated` | The Notice doesn't say candidates waiting for results may apply; you must have the qualification by {date}. | റിസൾട്ട് കാത്തിരിക്കുന്നവർക്ക് അപേക്ഷിക്കാമെന്ന് നോട്ടിഫിക്കേഷനിൽ പറയുന്നില്ല; {date}-നകം നിങ്ങൾക്ക് യോഗ്യത ഉണ്ടായിരിക്കണം. | |
| `r_pwbd_not_in_notice` | This Notice gives no PwBD age relaxation: check the Notice. | ഈ നോട്ടിഫിക്കേഷനിൽ PwBD പ്രായ ഇളവ് നൽകുന്നില്ല: നോട്ടിഫിക്കേഷൻ പരിശോധിക്കുക. | |
| `r_gender_male` | Only for male candidates. | പുരുഷ ഉദ്യോഗാർഥികൾക്ക് മാത്രം. | |
| `r_gender_female` | Only for female candidates. | വനിതാ ഉദ്യോഗാർഥികൾക്ക് മാത്രം. | |
| `r_gender_other` | Only for {g} candidates. | {g} ഉദ്യോഗാർഥികൾക്ക് മാത്രം. | |
| `r_state_language` | Needs the State's official/regional language at Secondary School level. | സംസ്ഥാനത്തിന്റെ ഔദ്യോഗിക/പ്രാദേശിക ഭാഷ സെക്കൻഡറി സ്കൂൾ തലത്തിൽ പഠിച്ചിരിക്കണം. | |
| `r_jso_maths_or_statistics` | Needs 60% in Mathematics in Class 12, or Statistics as a subject in your degree. | പ്ലസ് ടുവിൽ മാത്തമാറ്റിക്സിന് 60% വേണം, അല്ലെങ്കിൽ ബിരുദത്തിൽ സ്റ്റാറ്റിസ്റ്റിക്സ് ഒരു വിഷയമായിരിക്കണം. | |
| `r_sig2_degree_subject` | Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI. | ബിരുദം സ്റ്റാറ്റിസ്റ്റിക്സ്, മാത്തമാറ്റിക്സ്, ഇക്കണോമിക്സ്, ഡെമോഗ്രഫി, പോപ്പുലേഷൻ സ്റ്റഡീസ്, ഓപ്പറേഷൻസ് റിസർച്ച്, IT, കമ്പ്യൂട്ടർ സയൻസ്/എഞ്ചിനീയറിംഗ്/ആപ്ലിക്കേഷൻസ്, ഡാറ്റാ സയൻസ് അല്ലെങ്കിൽ AI എന്നിവയിൽ ഒന്നിലായിരിക്കണം. | |
| `r_driving_licence_lmv` | Needs a valid LMV (motorcycle and car) driving licence on the day of the physical test. | ഫിസിക്കൽ ടെസ്റ്റ് ദിവസം സാധുവായ LMV (മോട്ടോർസൈക്കിളും കാറും) ഡ്രൈവിംഗ് ലൈസൻസ് വേണം. | |
| `r_si_fire_qualification` | Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch. | ഫിസിക്സ്, കെമിസ്ട്രി, മാത്തമാറ്റിക്സ് എന്നിവയോടെ B.Sc., അല്ലെങ്കിൽ പത്താം ക്ലാസും മെക്കാനിക്കൽ, സിവിൽ, ഇലക്ട്രിക്കൽ അല്ലെങ്കിൽ ലിസ്റ്റിലുള്ള ഒരു എഞ്ചിനീയറിംഗ് ബ്രാഞ്ചിൽ 3 വർഷ ഡിപ്ലോമയും വേണം. | |
| `note_physical_standards` | Physical and medical standards apply (see the Notice). | ശാരീരിക, മെഡിക്കൽ മാനദണ്ഡങ്ങൾ ബാധകമാണ് (നോട്ടിഫിക്കേഷൻ കാണുക). | |
| `note_bro_male_only` | Border Roads Organisation vacancies: men only, physical standards apply. | Border Roads Organisation ഒഴിവുകൾ: പുരുഷന്മാർക്ക് മാത്രം, ശാരീരിക മാനദണ്ഡങ്ങൾ ബാധകം. | |
| `note_deo_science_maths_some` | In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics. | ചില ഓഫീസുകളിൽ (ഉപഭോക്തൃകാര്യ-ഭക്ഷ്യ വകുപ്പ്, SSC, സാംസ്കാരിക വകുപ്പ്) ഈ തസ്തികയ്ക്ക് മാത്തമാറ്റിക്സ് ഉൾപ്പെടുന്ന പ്ലസ് ടു സയൻസ് വേണം. | |
| `note_stenography_skill_test` | A stenography skill test is part of the selection. | സെലക്ഷന്റെ ഭാഗമായി ഒരു സ്റ്റെനോഗ്രഫി സ്കിൽ ടെസ്റ്റ് ഉണ്ട്. | |
| `listWord` | Lists: {n} | ലിസ്റ്റുകൾ: {n} | |
