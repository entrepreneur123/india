"""Guide pages: lottery tax (with a calculator), how to claim a prize, lottery scams.

Every fact here comes from an official text, read on 20-10-2026:
  * Income-tax Act, 2025 (Act 30 of 2025; in force from 1 April 2026): s.194(1) Table item 1 (30% on lottery
    winnings), s.94(4) (no expenses), s.393(3) Table item 1 (TDS at "rates in force", threshold "₹ 10000 in case of a
    single transaction").  Text as passed: prsindia.org (link below).
  * Finance Bill, 2026: First Schedule Part II (TDS 30% for residents; no surcharge for residents), clause 3(17)
    (no cess on TDS paid to residents), clause 3(12)(b) row 10 (surcharge 10/15/25% for s.202, the new regime)
    and 3(13) (marginal relief).
  * Nagaland: official claim form (valid from January 2026), instructions 1-11.
  * Sikkim: official result notification (e.g. DN191026, 19-10-2026) and claim form.
  * Kerala: Kerala Paper Lotteries (Regulation) Rules, 2005, rule 9; Kannur District Lottery Office page.
  * Scams: Kerala Police press release 261/PR/SPMC/PHQ/2026 (01-07-2026); RBI press releases of 06-02-2012 and
    30-07-2009; Lotteries (Regulation) Act, 1998, s.4(a).
Not stated here because no official text confirms it: the Kerala agent's commission on a prize, a fixed late-claim
limit for Kerala (the rules give 60 and 90 days in different places).
"""

SRC_ACT = ("Income-tax Act, 2025 (sections 94, 194, 393)",
           "https://prsindia.org/files/bills_acts/bills_parliament/2025/Bill_as_passed_by_LS_Income_Tax_(No.2)_Bill.pdf")
SRC_FB = ("Finance Bill, 2026 (rates for 2026-27)", "https://www.indiabudget.gov.in/doc/Finance_Bill.pdf")
SRC_NL = ("Nagaland State Lotteries – prize claim form", "https://www.nagalandlotteries.com/prize-claim-form.php")
SRC_SK = ("Sikkim State Lotteries – claim form", "https://www.sikkimlotteries.com/claim.html")
SRC_KL = ("Kerala Paper Lotteries (Regulation) Rules, 2005 – rule 9", "https://indiankanoon.org/doc/41875287/")
SRC_KLD = ("District Lottery Office, Kannur (Government of Kerala)", "https://kannur.nic.in/district-lottery-office/")
SRC_KP = ("Kerala Police – warning on fake online lottery (01.07.2026)", "https://keralapolice.gov.in/page/press-releases")
SRC_RBI = ("RBI – caution against fictitious lottery offers", "https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=25911")
SRC_LRA = ("Lotteries (Regulation) Act, 1998", "https://www.nagalandlotteries.com/the_lotteries_regulation_act_1998.pdf")

PATHS = {"tax": "/lottery-tax/", "claim": "/claim-prize/", "scams": "/lottery-scams/"}

# Each guide: title, desc, h1, intro, sections [(heading, [paragraphs])], sources. "calc" holds the calculator labels.
G = {
    "en": {
        "nav": "Guides", "sources": "Official sources", "checked": "Checked against these documents on 20.10.2026.",
        "names": {"tax": "Lottery tax calculator", "claim": "How to claim a prize", "scams": "Fake lottery scams"},
        "tax": {
            "title": "Lottery Tax Calculator India 2026 – Tax on Lottery Winnings and Payout",
            "desc": "How much tax is cut from a lottery prize in India: 30% above ₹10,000, then cess and surcharge when you file. Calculator for Dear and Kerala lottery prizes.",
            "h1": "Lottery tax calculator: tax on lottery winnings",
            "intro": "Type the prize amount to see how much tax is cut before you are paid and how much you keep. Rules for prizes received from 1 April 2026 (Income-tax Act, 2025).",
            "calc": {"amount": "Prize amount (₹)", "go": "Calculate", "prize": "Prize", "tds": "Tax cut before payment (30%)",
                     "paid": "Paid to you", "more": "More tax when you file your return", "total": "Total tax",
                     "keep": "You keep in the end",
                     "small": "No tax is cut from a prize of ₹10,000 or less. It is still taxed at 30% (plus 4% cess) when you file your return.",
                     "note": "Estimate for a resident of India, assuming the prize is your only income this tax year and the default (new) tax regime. Charges deducted by the lottery office are not included. Not tax advice: for a big prize, ask a tax professional."},
            "sections": [
                ("How lottery prizes are taxed", [
                    "Lottery winnings are taxed at a flat 30% (Income-tax Act, 2025, section 194). The basic exemption limit does not apply, and no expense, such as the ticket price, can be subtracted (section 94(4)).",
                    "If a single prize is more than ₹10,000, the payer cuts 30% of the whole prize before paying you (section 393(3), Table item 1). For residents of India exactly 30% is cut at this stage; cess and surcharge are not added.",
                ]),
                ("What you pay when you file your return", [
                    "A 4% Health and Education Cess is added to the 30%, so most winners pay 31.2% in total.",
                    "If your total income is above ₹50 lakh, a surcharge is added: 10% above ₹50 lakh, 15% above ₹1 crore and 25% above ₹2 crore in the new tax regime (in the old regime, 37% above ₹5 crore). Marginal relief limits the surcharge just above each limit.",
                    "Only 30% is cut when you are paid, so winners of big prizes usually still owe the cess and surcharge. Pay it as advance tax or self-assessment tax and show the prize in your return.",
                ]),
                ("Example: ₹1 crore prize", [
                    "30% cut before payment: ₹30,00,000, so ₹70,00,000 is paid to you. When you file: 10% surcharge ₹3,00,000 and 4% cess ₹1,32,000. Total tax ₹34,32,000; you keep ₹65,68,000.",
                ]),
            ],
            "src": [SRC_ACT, SRC_FB],
        },
        "claim": {
            "title": "How to Claim a Lottery Prize – Dear (Nagaland, Sikkim) and Kerala: Documents, Deadline",
            "desc": "Where to claim a Dear or Kerala lottery prize, the 30-day deadline and the documents to attach, from the official claim forms and rules.",
            "h1": "How to claim a lottery prize",
            "intro": "First check your ticket against the official result. Then follow the rules of the state that runs the lottery. Summary from the official claim forms and rules:",
            "sections": [
                ("Before you start", [
                    "Write your name, address and signature on the back of the winning ticket. Keep the original safe and flat: a laminated, torn or damaged ticket may not be paid, and photocopies are never paid.",
                    "Small prizes are usually paid through the seller: up to ₹10,000 for Dear lotteries and up to ₹5,000 for Kerala lotteries. Bigger prizes are claimed from the state lottery office as below.",
                    "Tax and charges are deducted from the prize itself. A real lottery office never asks you to pay money first.",
                ]),
                ("Nagaland State Lottery (Dear 1 PM and 8 PM)", [
                    "Prizes above ₹10,000 in one ticket: use the official claim form (the only valid form since January 2026) and the declaration on ₹20 stamp paper.",
                    "Deadline: within 30 days of the draw.",
                    "Where: in person at the Directorate of Nagaland State Lotteries, P. R. Hill Junction, Kohima, or at a Nodal Officer camp in Salt Lake (Kolkata), Mumbai, Ludhiana or Gangtok.",
                    "Attach: the original ticket, 3 passport photos, an affidavit of ownership, PAN card, Aadhaar card and a cancelled cheque, with copies attested by a First-Class Magistrate or Notary.",
                    "The prize is paid by RTGS or demand draft after tax and charges.",
                ]),
                ("Sikkim State Lottery (Dear 6 PM)", [
                    "Prizes above ₹10,000, in one ticket or together: send the ticket with the official claim form and 3 passport photos attested by a 1st Class Magistrate, Gazetted Officer, Oath Commissioner or Notary.",
                    "Deadline: no claim is accepted after 30 days from the draw (printed on every official result).",
                    "Where: in person or by post to the Principal Director, Sikkim State Lotteries, Deorali, Gangtok, or to the Inspectors at the Sikkim camp offices in Kolkata, Mumbai and Ludhiana. The form also asks for an affidavit, photo ID, PAN, a revenue stamp and a cancelled cheque.",
                ]),
                ("Kerala State Lottery", [
                    "Deadline: hand in the original ticket within 30 days of the draw (Kerala Paper Lotteries Rules, rule 9). A late claim is considered only on valid grounds.",
                    "Up to ₹5,000: a Kerala lottery agent can pay it. Up to ₹1 lakh: the District Lottery Office. Above ₹1 lakh: the Directorate of State Lotteries. You can also claim through a bank.",
                    "Attach (prizes above ₹5,000): a stamped receipt, a recent passport photo attested by a Gazetted Officer or bank, a self-attested copy of both sides of the ticket and a self-attested ID (passport, ration card, voter ID, driving licence or PAN). Winners from outside Kerala need these attested by a Notary.",
                ]),
            ],
            "src": [SRC_NL, SRC_SK, SRC_KL, SRC_KLD],
        },
        "scams": {
            "title": "Fake Lottery Scams in India – Lottery Winner Messages, Online Kerala Lottery, Guessing Numbers",
            "desc": "How fake lottery winner calls, WhatsApp 'guaranteed number' groups and online Kerala lottery sites cheat people, and how to report them.",
            "h1": "Fake lottery scams: how to spot them",
            "intro": "Lottery results attract cheats. These are the tricks police and the Reserve Bank of India warn about:",
            "sections": [
                ("“You have won, pay the tax first”", [
                    "A call, SMS or WhatsApp message says you won a lottery and must pay a registration fee, processing fee, GST, tax or a deposit to receive it. This is always fraud: a real prize has tax deducted from it, and no lottery office or bank asks you to pay first. The RBI warns that nobody from the RBI calls people about lottery winnings.",
                ]),
                ("Online Kerala lottery", [
                    "Kerala lotteries are paper lotteries, sold as printed tickets. Kerala Police warned in July 2026 that buying or selling Kerala lottery tickets online is banned, and that fake sites and WhatsApp groups use the government emblem, “mega bumper” offers and fake result checks to collect money.",
                ]),
                ("“Guaranteed number” groups", [
                    "No one can know a lottery number before the draw. The Lotteries (Regulation) Act, 1998 forbids prizes on a pre-announced number. Groups that sell a “sure”, “fixed” or “leak” number are cheating you.",
                ]),
                ("Fake result images", [
                    "A result image on WhatsApp can be edited. Check the number on the official result PDF or on our result page, which shows the numbers from the published sheet, before you pay or celebrate.",
                ]),
                ("Report it", [
                    "Call the cybercrime helpline 1930 or report at cybercrime.gov.in. Do not share OTPs, UPI PINs or bank details.",
                ]),
            ],
            "src": [SRC_KP, SRC_RBI, SRC_LRA],
        },
    },
    "bn": {
        "nav": "গাইড", "sources": "সরকারি সূত্র", "checked": "20.10.2026 তারিখে এই নথিগুলোর সঙ্গে মিলিয়ে দেখা হয়েছে।",
        "names": {"tax": "লটারি ট্যাক্স ক্যালকুলেটর", "claim": "পুরস্কার কীভাবে তুলবেন", "scams": "ভুয়ো লটারি প্রতারণা"},
        "tax": {
            "title": "ভারতে লটারির ট্যাক্স 2026 – লটারি পুরস্কারের ট্যাক্স ক্যালকুলেটর",
            "desc": "লটারির পুরস্কার থেকে কত ট্যাক্স কাটা হয়: ₹10,000-এর বেশি হলে 30%, তারপর রিটার্নের সময় সেস ও সারচার্জ। ডিয়ার ও কেরালা লটারির জন্য ক্যালকুলেটর।",
            "h1": "লটারি পুরস্কারের ট্যাক্স ক্যালকুলেটর",
            "intro": "পুরস্কারের টাকা লিখুন: টাকা পাওয়ার আগে কত ট্যাক্স কাটা হবে আর শেষে আপনার হাতে কত থাকবে দেখুন। 1 এপ্রিল 2026 থেকে পাওয়া পুরস্কারের নিয়ম (আয়কর আইন, 2025)।",
            "calc": {"amount": "পুরস্কারের টাকা (₹)", "go": "হিসাব করুন", "prize": "পুরস্কার", "tds": "টাকা দেওয়ার আগে কাটা ট্যাক্স (30%)",
                     "paid": "আপনাকে দেওয়া হবে", "more": "রিটার্ন জমার সময় আরও ট্যাক্স", "total": "মোট ট্যাক্স",
                     "keep": "শেষে আপনার হাতে থাকবে",
                     "small": "₹10,000 বা তার কম পুরস্কার থেকে ট্যাক্স কাটা হয় না। তবে রিটার্ন জমার সময় এর ওপরও 30% ট্যাক্স (সঙ্গে 4% সেস) দিতে হয়।",
                     "note": "ভারতের বাসিন্দার জন্য আনুমানিক হিসাব: ধরে নেওয়া হয়েছে এ বছর এই পুরস্কারই আপনার একমাত্র আয় এবং আপনি নতুন (ডিফল্ট) ট্যাক্স ব্যবস্থায় আছেন। লটারি দপ্তর যে চার্জ কাটে তা ধরা হয়নি। এটি ট্যাক্স পরামর্শ নয়: বড় পুরস্কার হলে একজন ট্যাক্স বিশেষজ্ঞের সঙ্গে কথা বলুন।"},
            "sections": [
                ("লটারির পুরস্কারে ট্যাক্স কীভাবে লাগে", [
                    "লটারির পুরস্কারে সরাসরি 30% ট্যাক্স লাগে (আয়কর আইন, 2025, ধারা 194)। এখানে বেসিক ছাড়ের সীমা খাটে না, আর টিকিটের দামের মতো কোনো খরচ বাদ দেওয়া যায় না (ধারা 94(4))।",
                    "একটি পুরস্কার ₹10,000-এর বেশি হলে, টাকা দেওয়ার আগে পুরো পুরস্কারের 30% কেটে নেওয়া হয় (ধারা 393(3), টেবিলের 1 নম্বর)। ভারতের বাসিন্দাদের ক্ষেত্রে এই সময় ঠিক 30%-ই কাটা হয়, সেস বা সারচার্জ যোগ হয় না।",
                ]),
                ("রিটার্ন জমার সময় কী দিতে হয়", [
                    "30%-এর ওপর 4% স্বাস্থ্য ও শিক্ষা সেস লাগে, ফলে বেশিরভাগ বিজয়ীর মোট ট্যাক্স হয় 31.2%।",
                    "মোট আয় ₹50 লাখের বেশি হলে সারচার্জ যোগ হয়: নতুন ব্যবস্থায় ₹50 লাখের বেশি হলে 10%, ₹1 কোটির বেশি হলে 15%, ₹2 কোটির বেশি হলে 25% (পুরোনো ব্যবস্থায় ₹5 কোটির বেশি হলে 37%)। প্রতিটি সীমার ঠিক ওপরে মার্জিনাল রিলিফ সারচার্জ কমিয়ে দেয়।",
                    "টাকা দেওয়ার সময় শুধু 30% কাটা হয়, তাই বড় পুরস্কারের বিজয়ীদের সাধারণত সেস আর সারচার্জ পরে দিতে হয়। অ্যাডভান্স ট্যাক্স বা সেল্ফ-অ্যাসেসমেন্ট ট্যাক্স হিসেবে জমা দিন এবং রিটার্নে পুরস্কারটি দেখান।",
                ]),
                ("উদাহরণ: ₹1 কোটির পুরস্কার", [
                    "টাকা দেওয়ার আগে 30% কাটা: ₹30,00,000, তাই আপনাকে দেওয়া হয় ₹70,00,000। রিটার্নের সময়: 10% সারচার্জ ₹3,00,000 আর 4% সেস ₹1,32,000। মোট ট্যাক্স ₹34,32,000, শেষে হাতে থাকে ₹65,68,000।",
                ]),
            ],
            "src": [SRC_ACT, SRC_FB],
        },
        "claim": {
            "title": "লটারির পুরস্কার কীভাবে তুলবেন – ডিয়ার (নাগাল্যান্ড, সিকিম) ও কেরালা: কাগজপত্র, সময়সীমা",
            "desc": "ডিয়ার বা কেরালা লটারির পুরস্কার কোথায় তুলবেন, 30 দিনের সময়সীমা আর কী কী কাগজ লাগবে: সরকারি ক্লেম ফর্ম ও নিয়ম থেকে।",
            "h1": "লটারির পুরস্কার কীভাবে তুলবেন",
            "intro": "আগে সরকারি রেজাল্টের সঙ্গে টিকিট মিলিয়ে নিন। তারপর যে রাজ্যের লটারি, সেই রাজ্যের নিয়ম মানুন। সরকারি ক্লেম ফর্ম ও নিয়মের সারাংশ:",
            "sections": [
                ("শুরু করার আগে", [
                    "জেতা টিকিটের পেছনে নাম, ঠিকানা আর সই লিখে দিন। আসল টিকিট ভাঁজ না করে যত্নে রাখুন: ল্যামিনেট করা, ছেঁড়া বা নষ্ট টিকিটের টাকা না-ও দেওয়া হতে পারে, আর ফটোকপির টাকা কখনো দেওয়া হয় না।",
                    "ছোট পুরস্কার সাধারণত বিক্রেতার মাধ্যমেই পাওয়া যায়: ডিয়ার লটারিতে ₹10,000 পর্যন্ত, কেরালা লটারিতে ₹5,000 পর্যন্ত। বড় পুরস্কার নিচের নিয়মে রাজ্যের লটারি দপ্তর থেকে তুলতে হয়।",
                    "ট্যাক্স আর চার্জ পুরস্কারের টাকা থেকেই কাটা হয়। আসল লটারি দপ্তর কখনো আগে টাকা চায় না।",
                ]),
                ("নাগাল্যান্ড স্টেট লটারি (ডিয়ার দুপুর 1টা ও রাত 8টা)", [
                    "এক টিকিটে ₹10,000-এর বেশি পুরস্কার: সরকারি ক্লেম ফর্ম (জানুয়ারি 2026 থেকে শুধু এটিই বৈধ) আর ₹20 স্ট্যাম্প পেপারে ঘোষণাপত্র।",
                    "সময়সীমা: ড্রয়ের 30 দিনের মধ্যে।",
                    "কোথায়: নিজে গিয়ে জমা দিন, ডিরেক্টরেট অফ নাগাল্যান্ড স্টেট লটারিজ, পি. আর. হিল জংশন, কোহিমা, অথবা সল্টলেক (কলকাতা), মুম্বই, লুধিয়ানা বা গ্যাংটকের নোডাল অফিসার ক্যাম্পে।",
                    "সঙ্গে দিন: আসল টিকিট, 3টি পাসপোর্ট ছবি, মালিকানার হলফনামা, প্যান কার্ড, আধার কার্ড আর একটি ক্যানসেল করা চেক; কপিগুলো ফার্স্ট-ক্লাস ম্যাজিস্ট্রেট বা নোটারিকে দিয়ে অ্যাটেস্ট করিয়ে নিন।",
                    "ট্যাক্স আর চার্জ কাটার পর পুরস্কার RTGS বা ডিমান্ড ড্রাফটে দেওয়া হয়।",
                ]),
                ("সিকিম স্টেট লটারি (ডিয়ার সন্ধ্যা 6টা)", [
                    "এক বা একাধিক টিকিট মিলিয়ে ₹10,000-এর বেশি পুরস্কার: সরকারি ক্লেম ফর্ম আর 3টি পাসপোর্ট ছবি (ফার্স্ট-ক্লাস ম্যাজিস্ট্রেট, গেজেটেড অফিসার, ওথ কমিশনার বা নোটারির অ্যাটেস্ট করা) সহ টিকিট পাঠান।",
                    "সময়সীমা: ড্রয়ের 30 দিন পরে আর কোনো ক্লেম নেওয়া হয় না (প্রতিটি সরকারি রেজাল্টে ছাপা থাকে)।",
                    "কোথায়: নিজে গিয়ে বা ডাকে, প্রিন্সিপাল ডিরেক্টর, সিকিম স্টেট লটারিজ, দেওরালি, গ্যাংটক, অথবা কলকাতা, মুম্বই ও লুধিয়ানার সিকিম ক্যাম্প অফিসের ইন্সপেক্টরের কাছে। ফর্মে হলফনামা, ছবিযুক্ত পরিচয়পত্র, প্যান, রেভিনিউ স্ট্যাম্প আর একটি ক্যানসেল করা চেকও লাগে।",
                ]),
                ("কেরালা স্টেট লটারি", [
                    "সময়সীমা: ড্রয়ের 30 দিনের মধ্যে আসল টিকিট জমা দিন (কেরালা পেপার লটারিজ রুলস, নিয়ম 9)। দেরিতে ক্লেম শুধু যুক্তিসঙ্গত কারণ থাকলে বিবেচনা করা হয়।",
                    "₹5,000 পর্যন্ত: কেরালার লটারি এজেন্ট দিতে পারেন। ₹1 লাখ পর্যন্ত: জেলা লটারি অফিস। ₹1 লাখের বেশি: ডিরেক্টরেট অফ স্টেট লটারিজ। ব্যাংকের মাধ্যমেও ক্লেম করা যায়।",
                    "সঙ্গে দিন (₹5,000-এর বেশি): স্ট্যাম্প দেওয়া রসিদ, গেজেটেড অফিসার বা ব্যাংকের অ্যাটেস্ট করা সাম্প্রতিক পাসপোর্ট ছবি, টিকিটের দুই পিঠের সেল্ফ-অ্যাটেস্টেড কপি আর সেল্ফ-অ্যাটেস্টেড পরিচয়পত্র (পাসপোর্ট, রেশন কার্ড, ভোটার কার্ড, ড্রাইভিং লাইসেন্স বা প্যান)। কেরালার বাইরের বিজয়ীদের এগুলো নোটারিকে দিয়ে অ্যাটেস্ট করাতে হয়।",
                ]),
            ],
            "src": [SRC_NL, SRC_SK, SRC_KL, SRC_KLD],
        },
        "scams": {
            "title": "ভুয়ো লটারি প্রতারণা – \"আপনি জিতেছেন\" মেসেজ, অনলাইন কেরালা লটারি, গেসিং নম্বর",
            "desc": "\"লটারি জিতেছেন\" বলে ভুয়ো ফোন, হোয়াটসঅ্যাপের \"নিশ্চিত নম্বর\" গ্রুপ আর অনলাইন কেরালা লটারি সাইট কীভাবে ঠকায়, আর কোথায় অভিযোগ করবেন।",
            "h1": "ভুয়ো লটারি প্রতারণা: কীভাবে চিনবেন",
            "intro": "লটারির রেজাল্ট ঘিরে প্রতারকরা সক্রিয়। পুলিশ আর রিজার্ভ ব্যাংক যে কৌশলগুলো নিয়ে সতর্ক করেছে:",
            "sections": [
                ("\"আপনি জিতেছেন, আগে ট্যাক্স দিন\"", [
                    "ফোন, এসএমএস বা হোয়াটসঅ্যাপে বলা হয় আপনি লটারি জিতেছেন, টাকা পেতে রেজিস্ট্রেশন ফি, প্রসেসিং ফি, জিএসটি, ট্যাক্স বা ডিপোজিট দিতে হবে। এটা সবসময় প্রতারণা: আসল পুরস্কার থেকে ট্যাক্স কেটে নেওয়া হয়, কোনো লটারি দপ্তর বা ব্যাংক আগে টাকা চায় না। রিজার্ভ ব্যাংক জানিয়েছে, লটারি জেতার ব্যাপারে আরবিআই থেকে কেউ কাউকে ফোন করে না।",
                ]),
                ("অনলাইন কেরালা লটারি", [
                    "কেরালা লটারি কাগজের লটারি, ছাপা টিকিটে বিক্রি হয়। কেরালা পুলিশ জুলাই 2026-এ সতর্ক করেছে যে অনলাইনে কেরালা লটারির টিকিট কেনা-বেচা নিষিদ্ধ, আর ভুয়ো সাইট ও হোয়াটসঅ্যাপ গ্রুপ সরকারি প্রতীক, \"মেগা বাম্পার\" অফার আর ভুয়ো রেজাল্ট চেক দেখিয়ে টাকা তোলে।",
                ]),
                ("\"নিশ্চিত নম্বর\" গ্রুপ", [
                    "ড্রয়ের আগে কেউ লটারির নম্বর জানতে পারে না। লটারিজ (রেগুলেশন) অ্যাক্ট, 1998 আগে থেকে ঘোষিত নম্বরে পুরস্কার নিষিদ্ধ করেছে। যারা \"শিওর\", \"ফিক্সড\" বা \"লিক\" নম্বর বিক্রি করে, তারা ঠকাচ্ছে।",
                ]),
                ("ভুয়ো রেজাল্টের ছবি", [
                    "হোয়াটসঅ্যাপে আসা রেজাল্টের ছবি এডিট করা হতে পারে। টাকা দেওয়া বা আনন্দ করার আগে সরকারি রেজাল্ট PDF বা আমাদের রেজাল্ট পেজে নম্বরটি মিলিয়ে নিন। আমাদের পেজে প্রকাশিত রেজাল্ট শিটের নম্বরই দেখানো হয়।",
                ]),
                ("অভিযোগ করুন", [
                    "সাইবার ক্রাইম হেল্পলাইন 1930-এ ফোন করুন বা cybercrime.gov.in-এ অভিযোগ জানান। OTP, UPI পিন বা ব্যাংকের তথ্য কাউকে দেবেন না।",
                ]),
            ],
            "src": [SRC_KP, SRC_RBI, SRC_LRA],
        },
    },
    "hi": {
        "nav": "गाइड", "sources": "सरकारी स्रोत", "checked": "20.10.2026 को इन दस्तावेज़ों से मिलाकर जाँचा गया।",
        "names": {"tax": "लॉटरी टैक्स कैलकुलेटर", "claim": "इनाम कैसे लें", "scams": "लॉटरी के नाम पर धोखाधड़ी"},
        "tax": {
            "title": "भारत में लॉटरी टैक्स 2026 – लॉटरी इनाम पर टैक्स कैलकुलेटर",
            "desc": "लॉटरी इनाम से कितना टैक्स कटता है: ₹10,000 से ज़्यादा पर 30%, फिर रिटर्न भरते समय सेस और सरचार्ज। डियर और केरल लॉटरी के लिए कैलकुलेटर।",
            "h1": "लॉटरी इनाम टैक्स कैलकुलेटर",
            "intro": "इनाम की रक़म लिखें और देखें कि पैसा मिलने से पहले कितना टैक्स कटेगा और आख़िर में आपके पास कितना बचेगा। ये नियम 1 अप्रैल 2026 या उसके बाद मिले इनाम पर लागू हैं (आयकर अधिनियम, 2025)।",
            "calc": {"amount": "इनाम की रक़म (₹)", "go": "हिसाब लगाएँ", "prize": "इनाम", "tds": "पैसा मिलने से पहले कटा टैक्स (30%)",
                     "paid": "आपको मिलेगा", "more": "रिटर्न भरते समय और टैक्स", "total": "कुल टैक्स",
                     "keep": "आख़िर में आपके पास",
                     "small": "₹10,000 या उससे कम के इनाम से टैक्स नहीं कटता। फिर भी रिटर्न भरते समय इस पर 30% टैक्स (और 4% सेस) देना होता है।",
                     "note": "भारत के निवासी के लिए अनुमान: मान लिया गया है कि इस साल आपकी कमाई सिर्फ़ यही इनाम है और आप नई (डिफ़ॉल्ट) टैक्स व्यवस्था में हैं। लॉटरी विभाग जो शुल्क काटता है, वह इसमें शामिल नहीं है। यह टैक्स सलाह नहीं है: बड़े इनाम पर किसी टैक्स विशेषज्ञ से बात करें।"},
            "sections": [
                ("लॉटरी इनाम पर टैक्स कैसे लगता है", [
                    "लॉटरी इनाम पर सीधा 30% टैक्स लगता है (आयकर अधिनियम, 2025, धारा 194)। यहाँ बेसिक छूट सीमा लागू नहीं होती और टिकट की कीमत जैसा कोई ख़र्च घटाया नहीं जा सकता (धारा 94(4))।",
                    "एक इनाम ₹10,000 से ज़्यादा हो तो भुगतान से पहले पूरे इनाम का 30% काट लिया जाता है (धारा 393(3), तालिका क्रम 1)। भारत के निवासियों के लिए इस समय ठीक 30% ही कटता है, सेस या सरचार्ज नहीं जुड़ता।",
                ]),
                ("रिटर्न भरते समय क्या देना होता है", [
                    "30% के ऊपर 4% स्वास्थ्य और शिक्षा सेस लगता है, इसलिए ज़्यादातर विजेताओं का कुल टैक्स 31.2% होता है।",
                    "कुल आय ₹50 लाख से ज़्यादा हो तो सरचार्ज जुड़ता है: नई व्यवस्था में ₹50 लाख से ऊपर 10%, ₹1 करोड़ से ऊपर 15% और ₹2 करोड़ से ऊपर 25% (पुरानी व्यवस्था में ₹5 करोड़ से ऊपर 37%)। हर सीमा के ठीक ऊपर मार्जिनल रिलीफ़ सरचार्ज कम कर देता है।",
                    "भुगतान के समय सिर्फ़ 30% कटता है, इसलिए बड़े इनाम के विजेताओं को आम तौर पर सेस और सरचार्ज बाद में देना पड़ता है। इसे एडवांस टैक्स या सेल्फ़-असेसमेंट टैक्स के रूप में जमा करें और रिटर्न में इनाम दिखाएँ।",
                ]),
                ("उदाहरण: ₹1 करोड़ का इनाम", [
                    "भुगतान से पहले 30% कटौती: ₹30,00,000, यानी आपको ₹70,00,000 मिलते हैं। रिटर्न के समय: 10% सरचार्ज ₹3,00,000 और 4% सेस ₹1,32,000। कुल टैक्स ₹34,32,000, आख़िर में आपके पास ₹65,68,000।",
                ]),
            ],
            "src": [SRC_ACT, SRC_FB],
        },
        "claim": {
            "title": "लॉटरी का इनाम कैसे लें – डियर (नागालैंड, सिक्किम) और केरल: दस्तावेज़, समय-सीमा",
            "desc": "डियर या केरल लॉटरी का इनाम कहाँ लें, 30 दिन की समय-सीमा और कौन-से दस्तावेज़ लगेंगे: सरकारी क्लेम फ़ॉर्म और नियमों से।",
            "h1": "लॉटरी का इनाम कैसे लें",
            "intro": "पहले सरकारी रिज़ल्ट से अपना टिकट मिलाएँ। फिर जिस राज्य की लॉटरी है, उसके नियम मानें। सरकारी क्लेम फ़ॉर्म और नियमों का सार:",
            "sections": [
                ("शुरू करने से पहले", [
                    "जीते हुए टिकट के पीछे अपना नाम और पता लिखें और साइन करें। असली टिकट को बिना मोड़े, संभालकर रखें: लैमिनेट किए हुए, फटे या ख़राब टिकट का पैसा शायद न मिले, और फ़ोटोकॉपी का पैसा कभी नहीं मिलता।",
                    "छोटे इनाम आम तौर पर टिकट बेचने वाले से ही मिल जाते हैं: डियर लॉटरी में ₹10,000 तक और केरल लॉटरी में ₹5,000 तक। बड़े इनाम नीचे दिए नियमों से राज्य के लॉटरी विभाग से लेने होते हैं।",
                    "टैक्स और शुल्क इनाम की रकम से ही काटे जाते हैं। असली लॉटरी विभाग कभी पहले पैसा नहीं माँगता।",
                ]),
                ("नागालैंड स्टेट लॉटरी (डियर 1 PM और 8 PM)", [
                    "एक टिकट में ₹10,000 से ज़्यादा का इनाम: सरकारी क्लेम फ़ॉर्म (जनवरी 2026 से सिर्फ़ यही मान्य) और ₹20 के स्टांप पेपर पर घोषणा-पत्र।",
                    "समय-सीमा: ड्रॉ के 30 दिन के अंदर।",
                    "कहाँ: ख़ुद जाकर जमा करें, डायरेक्टरेट ऑफ़ नागालैंड स्टेट लॉटरीज़, पी. आर. हिल जंक्शन, कोहिमा, या सॉल्ट लेक (कोलकाता), मुंबई, लुधियाना या गंगटोक के नोडल ऑफ़िसर कैंप में।",
                    "साथ लगाएँ: असली टिकट, 3 पासपोर्ट फ़ोटो, मालिकाना हक़ का हलफ़नामा, पैन कार्ड, आधार कार्ड और एक कैंसल चेक; कॉपियाँ फ़र्स्ट-क्लास मजिस्ट्रेट या नोटरी से अटेस्टेड हों।",
                    "टैक्स और शुल्क कटने के बाद इनाम RTGS या डिमांड ड्राफ़्ट से दिया जाता है।",
                ]),
                ("सिक्किम स्टेट लॉटरी (डियर 6 PM)", [
                    "एक या कई टिकट मिलाकर ₹10,000 से ज़्यादा का इनाम: सरकारी क्लेम फ़ॉर्म और 3 पासपोर्ट फ़ोटो (फ़र्स्ट-क्लास मजिस्ट्रेट, गज़ेटेड ऑफ़िसर, ओथ कमिश्नर या नोटरी से सत्यापित) के साथ टिकट भेजें।",
                    "समय-सीमा: ड्रॉ के 30 दिन बाद कोई क्लेम नहीं लिया जाता (यह हर सरकारी रिज़ल्ट पर छपा होता है)।",
                    "कहाँ: ख़ुद जाकर या डाक से, प्रिंसिपल डायरेक्टर, सिक्किम स्टेट लॉटरीज़, देवराली, गंगटोक, या कोलकाता, मुंबई और लुधियाना के सिक्किम कैंप ऑफ़िस के इंस्पेक्टर को। फ़ॉर्म में हलफ़नामा, फ़ोटो पहचान-पत्र, पैन, रेवेन्यू स्टांप और एक कैंसल चेक भी चाहिए।",
                ]),
                ("केरल स्टेट लॉटरी", [
                    "समय-सीमा: ड्रॉ के 30 दिन के अंदर असली टिकट जमा करें (केरल पेपर लॉटरीज़ नियम, नियम 9)। देर से किया गया क्लेम ठोस कारण होने पर ही माना जाता है।",
                    "₹5,000 तक: केरल का लॉटरी एजेंट दे सकता है। ₹1 लाख तक: ज़िला लॉटरी ऑफ़िस। ₹1 लाख से ज़्यादा: डायरेक्टरेट ऑफ़ स्टेट लॉटरीज़। बैंक के ज़रिए भी क्लेम किया जा सकता है।",
                    "साथ लगाएँ (₹5,000 से ज़्यादा): स्टांप लगी रसीद, गज़ेटेड ऑफ़िसर या बैंक से सत्यापित हाल की पासपोर्ट फ़ोटो, टिकट के दोनों तरफ़ की स्व-सत्यापित कॉपी और स्व-सत्यापित पहचान-पत्र (पासपोर्ट, राशन कार्ड, वोटर आईडी, ड्राइविंग लाइसेंस या पैन)। केरल से बाहर के विजेताओं को ये नोटरी से सत्यापित करवाने होते हैं।",
                ]),
            ],
            "src": [SRC_NL, SRC_SK, SRC_KL, SRC_KLD],
        },
        "scams": {
            "title": "लॉटरी फ़्रॉड – \"आप जीत गए\" मैसेज, ऑनलाइन केरल लॉटरी, गेसिंग नंबर",
            "desc": "लॉटरी जीतने की फ़र्ज़ी कॉल, व्हाट्सऐप के \"पक्का नंबर\" ग्रुप और ऑनलाइन केरल लॉटरी साइटें कैसे ठगती हैं, और शिकायत कहाँ करें।",
            "h1": "लॉटरी के नाम पर धोखाधड़ी: कैसे पहचानें",
            "intro": "लॉटरी रिज़ल्ट के आसपास ठग सक्रिय रहते हैं। पुलिस और रिज़र्व बैंक ने इन तरीक़ों से सावधान किया है:",
            "sections": [
                ("\"आप जीत गए, पहले टैक्स भरें\"", [
                    "कॉल, एसएमएस या व्हाट्सऐप पर कहा जाता है कि आपकी लॉटरी लगी है और इनाम पाने के लिए रजिस्ट्रेशन फ़ीस, प्रोसेसिंग फ़ीस, जीएसटी, टैक्स या डिपॉज़िट भरना होगा। यह हमेशा धोखा है: असली इनाम से टैक्स काटा जाता है, कोई लॉटरी विभाग या बैंक पहले पैसा नहीं माँगता। रिज़र्व बैंक ने कहा है कि लॉटरी जीतने के बारे में आरबीआई से कोई किसी को फ़ोन नहीं करता।",
                ]),
                ("ऑनलाइन केरल लॉटरी", [
                    "केरल लॉटरी काग़ज़ की लॉटरी है, छपे टिकट के रूप में बिकती है। केरल पुलिस ने जुलाई 2026 में चेतावनी दी कि केरल लॉटरी के टिकट ऑनलाइन ख़रीदना-बेचना प्रतिबंधित है, और फ़र्ज़ी साइटें और व्हाट्सऐप ग्रुप सरकारी लोगो, \"मेगा बंपर\" ऑफ़र और नक़ली रिज़ल्ट चेक दिखाकर पैसे ऐंठते हैं।",
                ]),
                ("\"पक्का नंबर\" ग्रुप", [
                    "ड्रॉ से पहले कोई भी लॉटरी नंबर नहीं जान सकता। लॉटरीज़ (रेगुलेशन) एक्ट, 1998 पहले से घोषित नंबर पर इनाम देने पर रोक लगाता है। जो लोग \"श्योर\", \"फ़िक्स\" या \"लीक\" नंबर बेचते हैं, वे ठग रहे हैं।",
                ]),
                ("नक़ली रिज़ल्ट की तस्वीरें", [
                    "व्हाट्सऐप पर आई रिज़ल्ट की तस्वीर एडिट की हुई हो सकती है। पैसे देने या जश्न मनाने से पहले सरकारी रिज़ल्ट PDF या हमारे रिज़ल्ट पेज पर नंबर मिलाएँ। हमारे पेज पर प्रकाशित रिज़ल्ट शीट के नंबर ही दिखाए जाते हैं।",
                ]),
                ("शिकायत करें", [
                    "साइबर क्राइम हेल्पलाइन 1930 पर कॉल करें या cybercrime.gov.in पर शिकायत करें। OTP, UPI पिन या बैंक की जानकारी किसी को न दें।",
                ]),
            ],
            "src": [SRC_KP, SRC_RBI, SRC_LRA],
        },
    },
}
