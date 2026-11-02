# Yaari: legal/policy research (18+ random video/voice chat, voice rooms, friends, IAP)

Researched 2026-11-02. This is NOT legal advice. Items marked LAWYER need local counsel before launch.

Labels:
- `[confirmed: URL]` = the claim was read on that page (a full fetch, or the search tool's extract of that exact page). "(official)" means a government, regulator, Apple, Google, Meta or Twilio page. "(secondary)" means a law firm, news or vendor page.
- `[unverified]` = from memory, conflicting sources, or not found on a primary source.

---

## 0. TOP RISKS (read first)

1. **Apple App Store guideline 1.2 says that random/anonymous chat and "Chatroulette-style" apps "do not belong on the App Store and may be removed without notice."** This targets Yaari's core feature directly. [confirmed: https://developer.apple.com/app-store/review/guidelines/ (official)] Some random-video apps do exist on iOS, probably by positioning as social discovery with heavy moderation, verified profiles and interest matching. That is [unverified], so treat iOS approval as uncertain. Mitigations (still [unverified]): avoid "random/stranger" wording in metadata, require a verified profile, use interest-based matching, show a visible moderation and report flow, and launch Android first.
2. **Google Play's Age-Restricted Content policy requires apps that randomly connect strangers, anonymous chat apps and dating apps to block minors using the Play Console tools.** The Child Safety Standards policy explicitly covers random chat and anonymous chat apps. Both are required. [confirmed: https://support.google.com/googleplay/android-developer/answer/16302250 (official); https://support.google.com/googleplay/android-developer/answer/14747720 (official)]
3. **UAE: VoIP is a regulated telecom service.** In-app calling is legal only if the provider is licensed, partners with a licensee, or holds TDRA approval/exemption. Expect WebRTC calls to be blocked on UAE networks. [confirmed: https://tdra.gov.ae/en/FAQs (official, via search extract)]
4. **India: if Yaari ever passes 50 lakh (5M) registered Indian users, it becomes an SSMI.** That requires India-resident officers (CCO, Nodal Contact Person, RGO), which in practice needs a local entity or staff. Takedown windows were cut in Feb 2026 to 3h (govt/court orders) and 2h (non-consensual intimate imagery, NCII).
5. **SMS OTP to India from abroad:** without DLT registration, traffic goes on the international (ILDO) route and the sender ID is replaced with a random numeric one. DLT registration practically needs Indian KYC documents (PAN/GST).

---

## 1. SMS / OTP

### India – TRAI DLT (TCCCPR 2018, amended Feb 2025)
- **Who must register:** every Principal Entity (PE, the business) sending commercial SMS, including OTPs, on the domestic route. This covers entity, header (6-character sender ID) and content template (e.g. "Your OTP is {#var#}"). The PE gets a PE-ID. [confirmed: https://www.plivo.com/docs/sms/concepts/dlt-registration-process (secondary)] OTPs go on the transactional/service-implicit category. [unverified]
- **Timeline:** entity approval about 3–7 business days; header/template 1–3 days each. [secondary, vendor] Twilio's domestic alpha sender ID takes about 10 business days. [confirmed: https://www.twilio.com/en-us/guidelines/in/sms (official)]
- **Feb 2025 amendment:** the PE→telemarketer chain was capped to ensure traceability. [confirmed: https://cms.trai.gov.in/sites/default/files/2025-02/PR_No.11of2025.pdf (official, via search extract)] Senders can challenge operator action within 60 days. [confirmed: https://www.jsalaw.com/wp-content/uploads/2025/02/JSA-Prism-Telecommunications_February-2025_TCCCPR-amendments.Final_.pdf (secondary)] Reports of biometric/physical verification for new registrants: [unverified].
- **Foreign entity without an Indian company:**
  - The DLT portals ask for PAN/GST and similar Indian documents. Whether a foreign PE can register directly is [unverified] (no primary source found). LAWYER/vendor check.
  - Practical route per Twilio: the international route needs **no DLT**, but traffic goes via ILDO and the alpha sender ID is **overwritten with a random 5–9 digit numeric ID (5NNNN…)**. It bypasses DND and has no time-of-day limits. [confirmed: https://www.twilio.com/en-us/guidelines/in/sms (official)] Expect higher cost and lower trust or deliverability. [unverified]
- **Recommendation:** use WhatsApp authentication or Truecaller/other verification as the primary channel for India, and international-route SMS as fallback. Register DLT once an Indian entity exists.

### India – DoT SIM-binding (relevant to phone-number login apps)
- On 28–29 Nov 2025 DoT directed messaging apps (WhatsApp, Telegram, Signal, Snapchat, ShareChat, Josh, etc.) to keep continuous SIM-to-device binding. This was done under the Telecom Cyber Security Rules, amended to add "Telecommunication Identifier User Entities" (TIUE). [confirmed: https://www.internetfreedom.in/iff-calls-on-dot-to-recall-the-sim-binding-direction/ (secondary)]
- The deadline was extended to **31 Dec 2026**, and the 6-hour web logout was dropped. [confirmed: https://www.medianama.com/2026/04/223-dot-sim-binding-deadline-extended-dec-2026/ (secondary, via search extract)]
- Whether a random-chat or social app like Yaari is a "TIUE" covered by the direction: [unverified]. MediaNama reported that DoT might ease the rule where 1:1 messaging is not the core function. LAWYER, and monitor.

### WhatsApp authentication templates (Meta)
- OTP over WhatsApp is allowed using **AUTHENTICATION-category templates**. They must use an OTP button: copy-code, one-tap autofill (Android; iOS 26+ keyboard suggestion), or zero-tap (Android). [confirmed: https://developers.facebook.com/docs/whatsapp/business-management-api/authentication-templates (official)]
- The body text is preset and fixed. Optional extras are a security disclaimer and an expiry of 1–90 minutes. Codes are delivered only to the primary device; linked devices get a masked message (Cloud API). [confirmed: same URL]
- The code is at most 15 characters, and URLs, media and emojis are not allowed. [secondary: https://docs.ycloud.com/docs/message-templates-authentication] Country-specific "authentication-international" pricing (India, Indonesia, etc.) and business-verification requirements: [unverified]. Check Meta pricing docs.

### Sender ID registration (alphanumeric) – per Twilio country guidelines (official)
| Country | Rule | Lead time | Notes |
|---|---|---|---|
| UAE | Pre-registration required; unregistered IDs blocked; international registration supported | ~2 weeks | Promotional traffic for domestic entities only (prefix "AD-"); no URLs/phone numbers in body. [confirmed: https://www.twilio.com/en-us/guidelines/ae/sms] |
| Saudi Arabia | Registered alpha ID required; Twilio supports **international** registration only (cannot register Saudi/Qatar domestic brands) | ~2 weeks | Numeric IDs blocked; URLs must be allowlisted; brand-ownership documents if ID ≠ company name. [confirmed: https://www.twilio.com/en-us/guidelines/sa/sms] |
| Indonesia | International and domestic pre-registration required; non-registered traffic blocked on Telkomsel/Axiata/Smart since 21 Sep 2024 | ~4 weeks (intl) | IDs deprovisioned if <1,000 SMS per network per 3 months. [confirmed: https://www.twilio.com/en-us/guidelines/id/sms] |
| Vietnam | Sender ID **and templates** pre-registered; unregistered IDs may be blocked since 25 Aug 2025 | ~5 weeks | Brand/app name required in body; colour copy of business registration certificate needed; marketing not supported. [confirmed: https://www.twilio.com/en-us/guidelines/vn/sms] |
| Philippines | Unregistered IDs blocked since 7 Apr 2025 | ~2 weeks | IDs cannot contain TEST/SMS/MESSAGE. [confirmed: https://www.twilio.com/en-us/guidelines/ph/sms] |

### US – A2P 10DLC / toll-free
- Anyone sending application SMS from a 10DLC number to the US must register brand and campaign. Unregistered traffic incurs carrier fees and filtering. Standard brands need a tax ID (a non-US equivalent is accepted). [confirmed: https://www.twilio.com/docs/messaging/compliance/a2p-10dlc (official)]
- **OTP-only exception:** Twilio Verify using Twilio's pooled senders needs no A2P 10DLC registration. [confirmed: same URL]
- Toll-free numbers cannot send to the US or Canada until verified (statuses: Restricted/Pending/Verified/Blocked). [confirmed: https://www.twilio.com/docs/messaging/compliance/toll-free/console-onboarding (official)]

### SMS pumping fraud mitigation (Twilio)
- Verify Fraud Guard is on by default and auto-blocks prefixes with anomalous traffic; its level is tunable. Blocks show as error 60410. [confirmed: https://www.twilio.com/docs/verify/preventing-toll-fraud/sms-fraud-guard (official)]
- Geo Permissions: disable unused countries, and use "monitor" for risky countries you still serve. Safe-list your own numbers. [confirmed: https://www.twilio.com/docs/verify/preventing-toll-fraud/verify-geo-permissions (official)]
- On 19 Aug 2026 Twilio changed the default for Italy and Argentina to monitor-all after a pumping surge. [confirmed: https://www.twilio.com/en-us/changelog/Verify_Geo_Permission_Update (official, via search extract)]
- Twilio recommends migrating Programmable Messaging OTP traffic to Verify. [confirmed: https://www.twilio.com/docs/messaging/features/sms-pumping-protection-programmable-messaging (official, via search extract)]
- App-side measures: rate limit per IP, device and number; CAPTCHA or Play Integrity / App Attest before OTP; block premium-rate prefixes; exponential resend backoff; alert on conversion-rate drops. [unverified, industry practice] Vonage docs were not fetched. [unverified]

---

## 2. Identity / age verification

### Korea PASS (brief)
- PASS is the carriers' (SKT/KT/LG U+) mobile identity-verification brand. The legal basis is Network Act Art. 23-3 (designated 본인확인기관, i.e. identity verification agencies). In May 2026, 23 agencies were designated (e.g. NICE). [confirmed: https://www.itwiki.kr/w/정보통신망_이용촉진_및_정보보호_등에_관한_법률_제23조의3 (secondary); https://www.inews24.com/view/1348556 (secondary)]
- It works only for people with a Korean mobile subscription in their own name (Korean nationals, or registered foreigners with a Korean line). Businesses integrate through a contract with an agency (NICE, KCB, etc.) or a reseller. [unverified, matches general knowledge] **Not usable for the target markets**, so it is irrelevant unless Korea launches.

### India
- **Aadhaar online authentication (AUA/KUA):** the Aadhaar Authentication for Good Governance Amendment Rules 2025 (31 Jan 2025) let private entities apply through the relevant ministry. UIDAI examines the application and MeitY approves it. Purposes are limited (good governance, social welfare, innovation, knowledge) and use must be voluntary. [confirmed: https://www.khaitanco.com/thought-leadership/Aadhaar-authentication-for-private-entities (secondary, via search extract)] A foreign social or dating app getting AUA/KUA status is **unlikely**. [unverified] LAWYER.
- **Offline verification (OVSE):** UIDAI now runs a registered Offline Verification Seeking Entity regime. The Dec 2025 amendment added Reg. 13A. It supports Aadhaar-app integration (app-to-app intent on Android/iOS) and offline face match, and is open to "any other entity requiring Aadhaar offline verification" for a lawful purpose. Offline verification cannot be done on behalf of another entity. [confirmed: https://uidai.gov.in/en/ovse (official); https://www.mondaq.com/india/compliance/1721358/ (secondary)] Whether simple QR scanning needs OVSE registration: sources conflict [unverified]. Fees: [unverified].
- **DigiLocker:** the DPDP Rules let a parent's adult identity be checked with a "virtual token" from a provider such as DigiLocker (see section 3). [confirmed: https://www.legal500.com/intelligence/india/privacy/digital-personal-data-protection-rules-2025-highlights (secondary)] DigiLocker partner onboarding requirements for a foreign private app: [unverified].
- **Practical approach for an 18+ app (not verified):** self-declaration plus AI age estimation, plus selfie liveness, plus reporting of under-18 users. Aadhaar offline is optional and only after OVSE registration (needs an Indian entity in practice).

### United States
- **COPPA:** applies to under-13s. The amended rule took effect 23 Jun 2025, with compliance due **22 Apr 2026**. [confirmed: https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule (official)] In Feb 2026 the FTC issued a policy statement: no COPPA enforcement for collecting data solely to determine age via age-verification technology (conditions apply). [confirmed: https://www.ftc.gov/news-events/news/press-releases/2026/02/ftc-issues-coppa-policy-statement-incentivize-use-age-verification-technologies-protect-children (official, via search extract)] An 18+ app with actual knowledge of under-13 users must delete their data. [unverified]
- **App Store Accountability Acts** (status per Wiley, 8 Jun 2026): [confirmed: https://www.legal500.com/intelligence/united-states/privacy/key-developments-with-state-app-store-accountability-acts-as-texas-act-takes-effect (secondary)]
  - **Texas SB 2420:** enjoined Dec 2025. The Fifth Circuit stayed the injunction (28 May 2026) and on 5 Jun 2026 let the law stay in effect pending appeal, so it is **in effect** and enforced by the AG.
  - **Utah:** HB 498 pushed operational deadlines to **6 May 2027** and left a private right of action only.
  - **Louisiana:** HB 977 delayed it to **1 Jul 2027**.
  - **California AB 1043** (Digital Age Assurance Act): effective **1 Jan 2027** [unverified]; details not fetched.
  - **Developer duties:** consume the store's age-category signal (<13, 13–15, 16–17, 18+); obtain parental consent via the store for minors; notify the store of "significant changes"; give an accurate age rating; use age data only for compliance and delete it after use (Texas).
  - **Tools:** Google's Play Age Signals API returns signals for Texas accounts created after 28 May 2026 and launches globally per the Oct 29 2026 policy update. [confirmed: https://developer.android.com/google/play/age-signals/use-age-signals-api (official); https://support.google.com/googleplay/android-developer/answer/18100766 (official)] Apple's Declared Age Range API and the PermissionKit Significant Change API apply to new Texas Apple Accounts from 4 Jun 2026. [confirmed: https://developer.apple.com/news/?id=sg176nne (official, via search extract)]
- **State social-media age-verification laws (mostly minor-account laws, heavily litigated):**
  - Mississippi: enforcement allowed by SCOTUS in Aug 2025.
  - Arkansas (Act 900): blocked Apr 2026.
  - Louisiana social-media law: permanently enjoined.
  - Virginia SB 854: blocked Feb/Mar 2026.
  - Nebraska LB 383: preliminarily blocked 27 Jun 2026.
  - Florida HB3, Georgia, Tennessee: status [unverified].
  - [confirmed for the listed items: https://en.wikipedia.org/wiki/Social_media_age_verification_laws_in_the_United_States (secondary) and Bloomberg Law/Law360 headlines (secondary)]
  - An 18+-only app that blocks minors reduces exposure, but **these laws often require age verification of all account holders** (e.g. Tennessee). LAWYER for each state where Yaari is marketed.
- **TAKE IT DOWN Act:** covered platforms must offer an NCII removal process and remove within **48 hours** of a valid request, including known identical copies. The FTC has enforced it since **19 May 2026**, with penalties up to $53,088 per violation. [confirmed: https://www.ftc.gov/news-events/news/press-releases/2026/05/ftc-begins-enforcing-take-it-down-act (official, via search extract)]
- **CSAM:** US providers must report apparent CSAM to NCMEC (18 U.S.C. §2258A). [unverified, not fetched]

### Canada
- There is no federal age-verification law in force. **Bill C-34 (Safe Social Media Act)**, tabled 10 Jun 2026, would bar under-16 accounts on social media and require "adequate age-verification or age-estimation measures." It is still a bill. [confirmed: https://www.michaelgeist.ca/2026/06/everything-all-at-once-bill-c-34-... (secondary); https://blog.privacylawyer.ca/2026/06/online-harms-is-back.html (secondary)] OPC age-assurance guidance: not found [unverified].

### UAE – UAE PASS
- **Private entities can integrate.** They need a valid UAE trade licence and a signed Service Provider Agreement, then go through phases (initiation, development, assessment, go-live) via the developer portal. Credentials are issued per channel after use-case approval, and the flow must be user-initiated. [confirmed: https://docs.uaepass.ae/getting-onboarded-with-uae-pass/onboarding-process-for-uae-pass-service-providers/initiation-phase (official, via search extract)]
- This means a **UAE company (trade licence) is required**.

### Saudi – Nafath
- Nafath (operated by SDAIA, built by Elm) connects to more than 530 government and private platforms. Private relying parties integrate through Elm or the Nafath developer portal. [confirmed: https://en.wikipedia.org/wiki/Unified_national_access (secondary)] Eligibility criteria (Saudi CR required?) and review time: [unverified]. Likely needs a Saudi entity. LAWYER.

### Indonesia / SEA (brief)
- **Indonesia – PP 17/2025 ("PP Tunas"), child protection on electronic systems:**
  - Enforced from 28 Mar 2026. High-risk platforms are restricted for under-16s (lower-risk from 13); platforms must self-assess risk and use age-inference/assurance; sanctions are being issued (e.g. a written sanction to Google).
  - [confirmed: https://www.rajahtannasia.com/?p=198473 (secondary); https://www.nst.com.my/amp/world/region/2026/03/1405139/indonesia-enforces-social-media-ban-under-16s (secondary)]
  - A random video-chat app will almost certainly be "high-risk". [unverified]
- **Vietnam:** Decree 147/2024 requires social-network accounts to be verified by a Vietnamese mobile number (or national ID if the user has none). Only verified accounts may post, comment or livestream. [confirmed: https://www.tilleke.com/insights/vietnam-issues-new-decree-governing-internet-services-and-online-information/ (secondary)]
- **Malaysia:** the Online Safety Act 2025 has applied to licensed platforms since 1 Jan 2026. [confirmed: https://www.malaymail.com/news/malaysia/2026/01/01/... (secondary)] Malaysia under-16 social-media age rule: [unverified].
- **Philippines, Thailand:** no general age-verification statute found. [unverified]

---

## 3. Data protection

### India – DPDP Act 2023 + DPDP Rules 2025
- **Rules notified** 13 Nov 2025 (G.S.R. 846(E)), gazetted 13/14 Nov.
- **Phases:**
  - Immediately: definitions and the Board.
  - **13 Nov 2026:** Rule 4 (Consent Managers).
  - **13 May 2027:** Rules 3, 5–16, 22, 23 (notice, consent, security, breach, children, SDF, rights).
  - [confirmed: https://www.legal500.com/intelligence/india/privacy/digital-personal-data-protection-rules-2025-highlights (secondary); PIB: "eighteen-month period for phased compliance" https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf (official)]
  - MeitY proposed cutting this to 12 months in Jan 2026, but **no amendment had been notified** as of Sep/Oct 2026 sources. [confirmed: https://www.storyboard18.com/digital/meity-seeks-industry-views-on-fast-tracking-dpdp-act-rollout-proposes-12-month-compliance-timeline-88332.htm (secondary)] Re-check the Gazette.
- **Applies to foreign entities** processing data in connection with offering goods or services in India (Act s.3). [unverified, not fetched; well-known]
- **Children (<18):** verifiable consent from a parent or guardian is required. The fiduciary must confirm the parent is an identifiable adult using data it already holds, details the parent provides, or a virtual token (e.g. DigiLocker). [confirmed: legal500 URL above; PIB (official)] For an 18+ app: block minors and keep evidence of age-gating. [unverified]
- **Breach:** notify affected users without delay, notify the Board without delay, and send a **detailed report within 72 hours** (extendable). [confirmed: legal500 URL; PIB "without delay" (official)]
- **Rights requests:** respond within 90 days. Publish a DPO or contact person. [confirmed: PIB (official)]
- **Retention:**
  - Social-media intermediaries with ≥2 crore (20M) Indian users: erase after 3 years from the last interaction, with 48h notice before erasure.
  - Logs: keep at least 1 year.
  - [confirmed: legal500 URL]
- **Penalties:** up to ₹250 crore (security); ₹200 crore (breach notice or children's obligations); ₹50 crore (other). [confirmed: PIB (official)]
- **Consent Managers** must be Indian companies. [confirmed: PIB (official)]

### India – IT (Intermediary) Rules 2021 (as amended; latest amendment in force 20 Feb 2026)
- **All intermediaries:**
  - Grievance Officer: acknowledge within 24h. Resolve within 15 days originally; the 2026 amendment reportedly cut this to 7 days [unverified, sources conflict]. [confirmed baseline: https://www.tribuneindia.com/news/nation/social-media-cos-with-50l-users-to-be-accountable-under-new-it-rules-218388/amp (secondary)]
  - **Takedown on govt/court orders: 3 hours** (was 36h). **NCII: 2 hours** (was 24h).
  - Synthetic/AI content: labelling and user-declaration duties.
  - [confirmed: https://www.outlookbusiness.com/news/it-rules-amendment-2026-3-hour-takedown-rule-synthetic-content-defined-new-compliance-norms (secondary); https://www.barandbench.com/news/law-policy/centre-amends-it-rules-to-regulate-ai-generated-content-takedown-timelines-reduced (secondary, gives the dates)]
  - A draft Second Amendment 2026 is under consultation. [secondary]
- **SSMI (>50 lakh registered Indian users, notification of 25 Feb 2021):**
  - Appoint a **Chief Compliance Officer, a 24x7 Nodal Contact Person and a Resident Grievance Officer, all resident in India**.
  - Publish a monthly compliance report and keep a physical contact address in India.
  - [confirmed: https://www.millenniumpost.in/business/govt-sets-50l-users-limit-to-define-significant-social-media-intermediary-under-new-it-rules-432969 (secondary)]
  - Voluntary user verification and the first-originator traceability duty for messaging SSMIs: [unverified, from Rule 4 memory].

### UAE – PDPL (Federal Decree-Law 45/2021)
- The law exists, but **Executive Regulations had not been issued** as of Sep 2026 per most sources. The adequacy list is unpublished and the UAE Data Office's enforcement is still developing. [confirmed: https://www.kayrouzandassociates.com/insights/cross-border-data-transfers-under-uae-law-in-2026 (secondary); conflicting vendor claim of a 1 Jan 2027 deadline is [unverified]]
- Free zones (DIFC/ADGM) have separate regimes. Not relevant unless Yaari incorporates there. [unverified]

### Saudi – PDPL
- In force (enforceable since Sep 2024). The amended **Transfer Regulation** (in force 1 Sep 2024) adds SDAIA Standard Contractual Clauses and BCRs as safeguards, plus a risk assessment for sensitive or continuous transfers. [confirmed: https://insightplus.bakermckenzie.com/bm/data-technology/saudi-arabia-updates-data-transfer-regulations-and-introduces-the-first-set-of-standard-contractual-clauses (secondary)]
- **National Register:** registration is required for public entities, entities whose core activity is processing personal data, and entities processing sensitive data.
  - **Since Oct 2026, overseas controllers can register.** They need a valid commercial registration (home country), an authorised representative, and MOFA verification/digital identity. Fines go up to SAR 5M.
  - [confirmed: https://www.clydeco.com/en/insights/2026/10/sdaia-opens-controller-registration-for-overseas-o (secondary, dated 8 Oct 2026)]
  - Yaari's core activity is processing personal data, so registration is likely required. [unverified] LAWYER.

### Qatar – PDPPL (Law 13/2016), regulator NCSA
- Cross-border rules are described inconsistently (not prohibited vs. restricted). Breach notice applies for serious harm. [confirmed: https://practiceguides.chambers.com/practice-guides/data-protection-privacy-2026/qatar (secondary, via search extract)] Details: [unverified].

### Egypt – PDPL (Law 151/2020)
- Executive Regulations were issued 1 Nov 2025 (Ministerial Decree 816/2025), with a **1-year grace period, so roughly Nov 2026**.
- Most controllers and processors need a **PDPC licence/permit**. Cross-border transfers need a separate licence (with exceptions). A DPO must be registered first. Breach notice to the PDPC is due in 72h. The law applies extraterritorially.
- [confirmed: https://cms.law/en/are/legal-updates/egypt-s-pdpl-executive-regulations-issued-one-year-compliance-countdown-begins (secondary); https://www.clydeco.com/en/insights/2026/01/egypt-regulatory-update-on-data-privacy (secondary)]

### Turkey (optional)
- Law 5651 as amended by Law 7578 (Official Gazette 1 May 2026) takes effect **1 Nov 2026**.
- Foreign social network providers with **>1M daily accesses from Turkey** must appoint a local representative (a Turkish citizen resident in Turkey, or a joint-stock company branch) and store Turkish user data in Turkey. Fines go up to 3% of global turnover.
- [confirmed: https://www.mondaq.com/turkey/social-media/1798624/new-obligations-introduced-for-social-media-and-game-platforms (secondary)] KVKK transfer rules: [unverified].

### US
- **CCPA/CPRA** applies if the business has >$26.625M revenue (2025–26), OR buys/sells/shares personal information of ≥100k consumers, OR earns ≥50% of revenue from selling/sharing. [revenue confirmed: https://cppa.ca.gov/regulations/cpi_adjustment.html (official, via search extract); other prongs [unverified]]
- About 19–20 state comprehensive privacy laws are in force by 2026, mostly with 100k-consumer thresholds. [unverified]
- Washington My Health My Data and similar laws may cover selfie/biometric age estimation. Illinois BIPA covers face geometry. LAWYER if face-based age estimation or verification is used. [unverified]

### Canada
- **PIPEDA** still governs. **Bill C-36 (Protecting Privacy and Consumer Data Act)**, tabled 15 Jun 2026, would replace Part 1 of PIPEDA with fines up to C$10M or 3% of revenue. It is not yet law. [confirmed: https://www.dlapiper.com/insights/publications/2026/06/canada-tables-bill-c36-the-protecting-privacy-and-consumer-data-act (secondary)]
- Quebec Law 25 (PIA before transfers outside Quebec, privacy officer, privacy-by-default): [unverified, not fetched].

### Indonesia
- **PDP Law 27/2022** has been in force since Oct 2024. The implementing regulation **PP 33/2026** was enacted 16 Jul 2026 and takes effect **16 Jan 2027**. The PDP Agency is not yet operational (a Constitutional Court challenge was filed Jun 2026). Cross-border transfers need equivalent protection or safeguards.
- [confirmed: https://www.rsm.global/indonesia/en/node/439 (secondary); https://www.hbtlaw.com/insights/2026-08/indonesia-personal-data-protection-and-cybersecurity-quarterly-update-august-2026 (secondary)]
- **PSE registration (MR 5/2020):** every private-scope electronic system provider accessible in Indonesia, **domestic or foreign**, must register via OSS / the Komdigi portal before operating. A local contact person is needed in practice. Unregistered platforms get blocked (e.g. 25 platforms warned in Sep 2026).
- [confirmed: https://rri.co.id/en/national/2737064/ministry-calls-25-private-pses-to-complete-registration (secondary); https://globallawexperts.com/what-is-pse-in-indonesia/ (secondary)] Foreign PSE content-moderation and access duties (e.g. 4h urgent takedown): [unverified].

### Vietnam
- **Decree 147/2024** (effective 25 Dec 2024):
  - Foreign cross-border social-network providers are in scope if they lease storage in Vietnam or have ≥100k monthly visits from Vietnam for 6 consecutive months.
  - Accounts must be verified by a Vietnamese phone number (or ID), and only verified accounts may post.
  - Providers must store registration data (name, DOB, phone) and handle takedowns.
  - [confirmed: https://www.tilleke.com/insights/vietnam-issues-new-decree-governing-internet-services-and-online-information/ (secondary); https://vietnamlawmagazine.vn/social-network-user-accounts-to-be-verified-with-subscriber-phone-numbers-73028.html (secondary)]
- **Decree 13/2023 was replaced** on 1 Jan 2026 by the **PDP Law + Decree 356/2025**. Cross-border transfers (including offshore storage or cloud) need a **transfer impact assessment (CTIA) filed with the Ministry of Public Security, reportedly within 60 days**. The law reaches foreign parties.
- [confirmed: https://www.vietnam-briefing.com/news/vietnam-personal-data-protection-regulation-decree-356.html (secondary)] Decree 330/2026 sanctions (up to 5% revenue): [unverified]. Data localization under the Cybersecurity Law / Decree 53/2022 for foreign providers (on MPS request): [unverified].

### Philippines – Data Privacy Act 2012
- NPC Circular 2022-04 requires registration of the DPO and data processing systems if the entity has ≥250 employees, processes sensitive personal information of ≥1,000 individuals, or processes data likely to pose risk (profiling or automated decisions). [confirmed: https://insightplus.bakermckenzie.com/bm/data-technology/philippines-new-npc-circular-on-registration-of-data-protection-officers-and-data-processing-systems-takes-effect_1 (secondary)]
- Applicability to a foreign app with no PH presence is extraterritorial under DPA s.6. [unverified]

### Thailand – PDPA 2019
- Extraterritorial for offering goods or services to people in Thailand. **Foreign controllers must appoint a written representative in Thailand (s.37(5))**, with exemptions. DPO duty applies on large-scale processing. [confirmed: https://www.nortonrosefulbright.com/en/knowledge/publications/e29d223d/overview-of-thailand-personal-data-protection-act-be2562-2019 (secondary)]
- **Royal Decree on Digital Platform Services (2022, effective Aug 2023):** notify ETDA before operating. **Overseas platforms must appoint a Thai local coordinator** (not a "foreign business" under the Foreign Business Act). A local company is not required. Tiered thresholds (draft: >5,000 monthly Thai users or revenue >THB 50M) are [unverified against final text]. [confirmed: https://www.tilleke.com/insights/thailand-issues-royal-decree-on-digital-platforms/ (secondary)]

### Malaysia
- **Licensing:** from 1 Jan 2025, social media and internet messaging services with **≥8M Malaysian users** need an Applications Service Provider Class Licence (CASP). From 1 Jan 2026 they are deemed registered (CMA s.46A). [confirmed: https://www.thestar.com.my/news/nation/2025/01/01/malaysia-begins-social-media-licencing (secondary); https://www.malaymail.com/news/malaysia/2026/01/01/... (secondary)]
- **Online Safety Act 2025** (in force 1 Jan 2026) imposes duties on licensed platforms: online safety plan, harmful-content measures and complaint channels. [confirmed: same (secondary)] Whether a local company is required: **not found** [unverified].
- PDPA 2010 (amended 2024: mandatory DPO, breach notification, data portability): [unverified, not fetched].

---

## 4. VoIP / online calling (impact on WebRTC calling)

| Country | Status | Impact on Yaari |
|---|---|---|
| **UAE** | VoIP to subscribers is a regulated telecom activity. It is legal only if licensed, partnered with a licensee, or TDRA-approved/exempt. Approved list includes Teams, Zoom, Meet, BOTIM. WhatsApp/FaceTime calls are blocked. [confirmed: https://tdra.gov.ae/en/FAQs (official, via search extract); https://tdra.gov.ae/en/about/tdra-sectors/information-and-digital-government/policy-and-programs-department/internet-guidelines (official, via search extract)] | Expect WebRTC media (STUN/TURN/SRTP) to be blocked or degraded. To offer calls lawfully: apply for a TDRA VoIP exemption (VoIP@tdra.gov.ae per search extract) or partner with e&/du. Otherwise disable voice/video for UAE and keep text only. LAWYER. |
| **Saudi** | Ban partially lifted Sept 2017. Apps must meet CITC/CST conditions (data handling, removing unlawful content, cooperation). WhatsApp calls were initially still blocked. [confirmed: https://www.thenationalnews.com/business/technology/saudi-arabia-unblocks-internet-calling-apps-such-as-whatsapp-and-skype-1.630132 (secondary)] Current WhatsApp call status: [unverified]. | Probably works technically. Regulatory expectation of lawful-intercept cooperation and content removal. LAWYER. |
| **Qatar** | CRA says VoIP use isn't prohibited, but selling VoIP needs a licence (Ooredoo/Vodafone). Consumer app calls are intermittently blocked since 2017. [confirmed: https://www.gulf-times.com/story/562254/qatar-residents-face-problems-using-voip-calls (secondary); CRA statement (secondary)] | A free calling feature may work but be unreliable. Charging per call likely needs a licence. [unverified] |
| **Egypt** | No blanket ban. The NTRA periodically throttles or restricts unlicensed VoIP. [confirmed: https://www.deccanherald.com/amp/story/world%2Fexplained-which-countries-have-blocked-whatsapp-3680796 (secondary)] | Variable quality. LAWYER if monetizing calls. |
| **Oman** | Historically blocked unlicensed VoIP. Reports suggest WhatsApp calls working by 2026. [confirmed: https://www.omanobserver.om/article/1162945/oman/community/can-we-make-whatsapp-calls-without-vpn-in-oman (secondary)] | Uncertain. Test on-network. |
| **India** | The Telecommunications Act 2023 deliberately excluded OTT; per the minister, OTT stays under MeitY/IT Act. Telcos still push for licensing. [confirmed: https://www.medianama.com/2024/01/223-dot-conscious-decision-ott-exclusion-telecom-act/ (secondary)] | No VoIP licence needed today [unverified for 2026]. Watch the SIM-binding/TIUE regime and the Digital India Act. |
| US/Canada/SEA | No consumer VoIP licensing for free app-to-app calling known. [unverified] | Fine. Note: Vietnam/Indonesia may expect lawful-access cooperation. [unverified] |

---

## 5. App store policies

### Google Play
- **UGC policy:** users must accept ToS defining objectionable content before posting. Requirements: ongoing moderation proportionate to UGC type; in-app **reporting** of content and users; in-app **blocking** for 1:1 features; monetization safeguards (paid features must not incentivize objectionable content). Sexual content must be incidental, hidden behind filters needing two actions to disable, and age-screened. Apps known primarily for objectionable UGC are removed. [confirmed: https://support.google.com/googleplay/android-developer/answer/9876937 (official)]
- **Age-Restricted Content & Functionality:** apps whose core is **randomly connecting users to strangers**, **anonymous chat**, or **dating** must **block minors** using Play Console tools (Restrict Minor Access; see answer/9867159). Apps where dating is incidental need robust age-gating. [confirmed: https://support.google.com/googleplay/android-developer/answer/16302250 (official)]
- **Child Safety Standards:** covers anonymous chat, random chat, and Social/Dating apps, whatever their age-gating. Requirements:
  1. Published CSAE standards on a public web page naming the app or developer.
  2. In-app feedback/report mechanism.
  3. Action on CSAM per law (removal, reporting).
  4. Compliance with child-safety laws.
  5. A named **child-safety point of contact**.
  - Declare all of this in Play Console. [confirmed: https://support.google.com/googleplay/android-developer/answer/14747720 (official)]
- **Oct 29 2026 policy update:**
  - ≥30 days to comply.
  - Play Age Signals launching globally.
  - From **27 Jan 2027**, `READ_CALL_LOG` is no longer allowed for phone-call account verification; use the SMS User Consent API etc.
  - From **27 Jan 2027**, dating/social apps must use minimum location scope, with a declaration required for `ACCESS_FINE_LOCATION`.
  - Trader-status declaration from Nov 2026.
  - [confirmed: https://support.google.com/googleplay/android-developer/answer/18100766 (official)]
- Account deletion: Play requires an in-app and web account-deletion path plus a Data Safety form. [unverified, not fetched; well-known policy]

### Apple
- **Guideline 1.2:** UGC apps must have filtering, reporting with timely responses, blocking, and published contact info. **"Apps … used primarily for pornographic content, Chatroulette-style experiences, random or anonymous chat, objectification of real people … do not belong on the App Store and may be removed without notice."** [confirmed: https://developer.apple.com/app-store/review/guidelines/ (official)]
- **1.2.1(a):** creator-content apps need age restriction based on verified or declared age. [confirmed: same]
- **5.1.1(v):** if the app supports account creation, it must **offer account deletion within the app**. [confirmed: same]
- **Age tools:** Declared Age Range API; Texas SB 2420 applies to new Texas accounts from 4 Jun 2026. [confirmed: https://developer.apple.com/news/?id=sg176nne (official, via search extract)]

---

## 6. Digital goods payments

### Google Play
- **Payments policy:** in-app digital goods, features and subscriptions **must use Google Play Billing**. No steering to other payment methods (buttons, links, webviews) unless enrolled in an eligible program. In-app currency is usable only in the app where bought. [confirmed: https://support.google.com/googleplay/android-developer/answer/9858738 (official)]
- **User choice / alternative billing:**
  - Separate programs exist for **India**, Korea and the US. Indonesia is in the general user-choice program.
  - **India and Korea:** service fee reduced by 4% for alternative-billing transactions; India manual sunset date TBA.
  - **Indonesia:** 4% reduction (pending global fee update).
  - Gaming and non-gaming apps eligible in India, Korea and the US.
  - [confirmed: https://support.google.com/googleplay/android-developer/answer/13821247 (official)]
- **US after Epic v. Google:**
  - From **29 Oct 2025**, Google does not require Play Billing for US users. Developers may link out and use other payment methods (Ninth Circuit affirmed 12 Sep 2025).
  - A new settlement was reached 4 Mar 2026. US alternative billing and external content links programs carry **reporting and service-fee obligations** (dates listed: 22 Jul 2026; 1 Oct 2026; downloads reporting by 1 Feb 2027).
  - [confirmed: https://support.google.com/googleplay/android-developer/answer/15582165 (official)]
  - Exact US rates (reported as 10% for subscriptions / 20% for other items, plus a per-install fee) are [unverified] (secondary: Notebookcheck).

### Apple
- **3.1.1:** unlocking features or content (subscriptions, currencies) requires IAP. [confirmed: https://developer.apple.com/app-store/review/guidelines/ (official)]
- **US storefront:** apps may include **buttons, external links and other calls to action** to other purchase methods, with no entitlement needed (3.1.1(a), 3.1.3). [confirmed: same]
- **Court status:**
  - Apr 2025: contempt order with zero commission on link-outs.
  - Dec 2025: Ninth Circuit upheld contempt but allowed a cost-based fee, to be set on remand.
  - May 2026: stays denied.
  - Apple currently collects **no commission on US link-out purchases**; the future rate is pending. [confirmed: https://www.macrumors.com/2026/04/29/epic-games-wins-reversal-app-store-fee-battle/ (secondary)]
  - SCOTUS cert status: sources conflict [unverified].
- **3.1.3(b) Multiplatform:** users may access in-app content or subscriptions bought on the website or other platforms, **provided those items are also available as IAP in the app**. Outside the US, the app cannot point users to the web purchase (3.1.3 intro). [confirmed: same]
- **3.1.3(a) Reader apps:** limited to magazines, news, books, audio, music and video, so **Yaari does not qualify**. [confirmed: same]
- **Virtual gifts to creators/hosts** (voice rooms) must use IAP. [Apple 3.1.1 "tipping digital content providers" bullet: unverified, not quoted]

### India payments (RBI, GST)
- **Payment aggregators:** non-bank PAs need RBI authorisation (PA/PG guidelines 2020). Cross-border PAs (PA-CB) need authorisation under the circular of 31 Oct 2023, which replaced OPGSP. A foreign merchant takes Indian payments **through** an authorised PA/PA-CB and does not need its own licence. [confirmed: https://www.jsalaw.com/newsletters-and-updates/rbis-new-guidelines-to-govern-payment-aggregators-in-cross-border-transactions/ (secondary)] PA-CB per-transaction cap (₹25 lakh) and due diligence above ₹2.5 lakh: [unverified, sources differ].
- **E-mandate / recurring (RBI consolidated framework, Apr 2026):** first registration needs Additional Factor of Authentication (AFA). Recurring debits **up to ₹15,000 need no AFA**; above that, AFA each time. ₹1 lakh applies only to insurance, mutual funds and credit-card bills. No fee to the customer. [confirmed: https://www.businesstoday.in/personal-finance/news/story/rbi-caps-recurring-payments-at-rs15000-without-otp-under-new-e-mandate-framework-526759-2026-04-21 (secondary); RBI PDF https://rbidocs.rbi.org.in/rdocs/notification/PDFs/396MDD002E435ECA145509929FC3ACBCFD0E9.PDF (official, not opened)]
- **GST on OIDAR:** foreign suppliers of online services to Indian non-business users must register (simplified scheme, Rule 14, REG-10). There is no threshold. IGST is 18% and returns are monthly on GSTR-5A. The B2C exemption was removed from 1 Oct 2023 (Finance Act 2023 widened the OIDAR scope). [confirmed: https://amlegals.com/from-service-tax-to-gst-how-india-came-to-tax-overseas-oidar-suppliers-and-how-the-simplified-registration-scheme-works/ (secondary)]
  - App-store sales: whether Google or Apple remits GST as deemed supplier: [unverified]. LAWYER/accountant.
  - Direct web sales: Yaari is liable. [unverified]

### UAE / Saudi VAT on digital services
- **UAE:** 5% VAT. Non-resident B2C e-service suppliers must register with **no threshold** (FTA E-Commerce guide VATGEC1, via EmaraTax); B2B is reverse charge. [confirmed: https://www.vatcalc.com/uae/uae-value-added-tax-on-non-resident-digital-services/ (secondary)]
- **Saudi:** 15% VAT. Non-resident B2C e-services must register from the first supply (no threshold), directly or via a tax representative; B2B is reverse charge. [confirmed: https://payproglobal.com/saas-sales-tax/saudi-arabia/ (secondary)]
- Marketplace (app store) deemed-supplier treatment in UAE/KSA: [unverified].

---

## 7. Entity / local-presence requirements

| Requirement | Trigger | Local company needed? |
|---|---|---|
| India SSMI officers (CCO, Nodal, RGO) | >50 lakh registered Indian users | Officers must be **India-resident employees**, so a local entity is needed in practice [confirmed residency; entity = unverified] |
| India DLT (domestic SMS) | Domestic SMS route | Indian KYC documents (PAN/GST) needed in practice [unverified] |
| India Aadhaar AUA/KUA or OVSE | Optional | Effectively an Indian entity [unverified] |
| India DPDP Consent Manager | Only if *being* a consent manager | Indian company [confirmed PIB] |
| India GST OIDAR | Any B2C sale | No (simplified registration for overseas suppliers) [confirmed secondary] |
| Indonesia PSE registration | Accessible in Indonesia | Foreign registration allowed; local contact needed in practice [secondary] |
| Malaysia CASP licence + Online Safety Act | ≥8M Malaysian users | Not found [unverified] |
| Thailand digital-platform notification | Serving Thai users (thresholds) | No; Thai **local coordinator** required [confirmed secondary] |
| Thailand PDPA representative | Foreign controller targeting Thailand | Representative in Thailand (person or entity) [confirmed secondary] |
| Vietnam Decree 147 | ≥100k monthly VN visits ×6 months or local storage | Not mandatory for foreign cross-border providers, but local contact, verification and takedown duties apply [secondary]; localization on request [unverified] |
| Saudi PDPL register | Core data processing / sensitive data | Overseas registration allowed with authorised representative + MOFA verification [confirmed secondary] |
| UAE PASS | Optional | UAE trade licence [confirmed official] |
| UAE VoIP | Offering calls | TDRA licence/exemption or licensed-operator partner [confirmed official] |
| Egypt PDPL licence | Processing data of people in Egypt | PDPC licence + registered DPO; entity requirement [unverified] |
| Turkey 5651 representative | >1M daily accesses from Turkey | Turkish citizen resident or joint-stock company branch [confirmed secondary] |
| US/Canada | — | None for operating [unverified] |

---

## 8. Practical checklist (defaults chosen; validate with counsel)
1. Android-first launch. Set a mature rating and **Restrict Minor Access** in Play Console. Publish a CSAE standards page and a named child-safety contact. Build in-app report and block, ToS acceptance, moderation (automated nudity detection on video frames plus a human queue), and an NCII takedown path within **2h (India) / 48h (US)**.
2. iOS: design to avoid the "random/anonymous chat" framing, with verified profiles and identity shown. Expect rejection risk.
3. OTP:
   - India: WhatsApp authentication as primary, international SMS as fallback.
   - US: Twilio Verify (no 10DLC).
   - Pre-register sender IDs in UAE/KSA/ID/VN/PH.
   - Turn on Fraud Guard and restrict Geo Permissions to launch countries.
4. Age: 18+ self-declaration plus store age signals (Texas, global Play Age Signals) plus AI age estimation on suspicion. Delete under-18 accounts. Vietnam requires phone-number verification of accounts.
5. UAE: disable voice/video until a TDRA exemption or partner exists. Qatar/Egypt/Oman: allow, but monitor quality.
6. Payments: Play Billing / IAP everywhere except where link-outs are allowed (US). Register for UAE/KSA VAT and India OIDAR if selling direct on the web.
7. Data: DPDP readiness by **13 May 2027**. Egypt PDPL licence (grace period ends about Nov 2026). Vietnam CTIA filing if VN users' data is stored offshore. Saudi register check. Thailand coordinator and representative.
