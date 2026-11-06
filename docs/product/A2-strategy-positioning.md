# DrivePerf v1: strategy, positioning, pricing and growth loops (A2)

**Author:** agent A2 (product strategy and brand) · **Date:** 6 November 2026 · **Phase:** 1, Discovery · **Status:** proposal for the Gate 1 review

**Inputs:** [kickoff plan §2](../planning/DrivePerf-v1-kickoff-plan.md), [Android market research](../research/2026-09/01-android-market-research.md), [next-phase research plan](../research/2026-09/02-next-phase-research-plan.md), the Sep 2026 evidence appendices, and targeted web research on 6 Nov 2026 (sources in §9). The parallel A1 market refresh was not available when this was written. Re-check §5 against A1's iOS price table when it lands.

**What this document is not:** it is not interview evidence. No users were interviewed. Personas, retention targets and price points are **hypotheses to validate** in the discovery interviews and the beta, and they are labelled that way. Vendor claims are marked *(vendor claim)*. Benchmarks that could not be verified are flagged, with my reasoning given instead.

---

## 0. One-page summary

**The bet.** Speed-led driving apps already lead the iOS store listings: Open Road's subtitle is "CarPlay Speedometer, Top Speed", SpeedRank's is "#1 Speedometer & Driving Stats", and Enroad (6.7K ratings) is praised in reviews for "top speed + corner speed" (App Store, observed 6 Nov 2026). DrivePerf should own the opposite pole: **smoothness as a skill you can see and improve**, presented with Strava/Spotify-grade recaps. That position is defensible. It is safe to gamify and acceptable to the stores (Apple guideline 1.4.4 bans encouraging "excessive speed"), and it is the one claim the speed apps cannot copy without abandoning their own pitch.

| Decision | Recommendation | Runner-up |
|---|---|---|
| Hero user | **The daily-driven enthusiast**: someone who loves their car *and* drives it most days. This narrows the plan's "everyday enthusiast" by **behaviour**, not identity. | The plan's broader everyday enthusiast |
| Hero metric | **Smoothness**, always shown **relative to your own baseline and your routes**. There is no live score and no reward for skipping a necessary stop. | A branded "Flow" score (same maths, more ownable, weaker for search) |
| Positioning | **"Smooth is a skill."** DrivePerf measures it and helps you master it. Supporting line: *Every drive, decoded.* | Recap-led: "Strava-grade recaps for your daily drive" |
| App Store subtitle (≤30) | `Smoothness score & drive recap` (30) | `Smooth driving, decoded` (23) |
| Play short description (≤80) | `Auto-record every drive. See your smoothness, weekly recaps and route records.` (78) | `Every drive, decoded: auto trip recording, smoothness score and weekly recaps.` (78) |
| Price, US | **US$6.99/mo · US$39.99/yr** | The plan's US$4.99 / US$34.99 |
| Price, Canada | **CA$8.99/mo · CA$49.99/yr** | CA$6.99 / CA$44.99 |
| Trial | **14-day "Pro Preview" (reverse trial, no card)**, then a paywall with a founding-member intro price | A 7-day store trial on the annual plan, offered at the first Route comparison |
| Paywall moment | The **"Your first two weeks, decoded"** recap on day 15, plus contextual locks (comparing with your best, older analysis) | A paywall after the 3rd recorded drive |
| Free vs Pro | Free is generous on **capture, the Story, scores, the weekly recap, Wrapped and sharing**. Pro sells **depth over time**: full-history analysis, Route comparisons, trends, exports, cloud backup and unlimited cars. History is **"locked, not lost."** | The plan's split, which puts Wrapped in Pro and keeps 30 days of history |
| Growth loop | Privacy-safe **route-glyph share cards** (no basemap by default) plus a **Ride-along pass** referral (30 days of Pro for both sides, paid out on the friend's activation) | Club codes for car clubs |
| North star | **Weekly Decoded Drivers**: users with ≥2 good-quality recorded trips **and** ≥1 Story, recap or analysis viewed that week | The plan's "≥3 trips a week" |
| Activation | **Always-on capture granted + first Story viewed within 72 h** | The plan's "first trip + Story within 48 h" |
| Retention targets | Engaged retention (classic day N): **D1 35% · D7 20% · D30 12%**. Capture retention W4 ≥ 30%. | — |
| Launch window | **Spring 2027 "Season Opener"**, when Canadian and northern-US enthusiasts take summer cars out of storage | A quiet soft launch in Feb 2027 with a marketing push in spring |

**Where I disagree with the kickoff plan** (detail in each section):
1. **Hero user.** I agree enthusiasts are the brand and acquisition hero, but retention comes from *repeat drives*. The persona to design for is the enthusiast who commutes in the car they love. The "data tinkerer" sub-type will pull the roadmap toward timing and track features, and v1 must resist that.
2. **Hero metric.** Smoothness is right, but an absolute 0–100 score saturates: good drivers sit at 90+ within a week and have nowhere to go. Make it personal-relative. The scoring spec (A12) must also not reward rolling stops or avoiding a necessary hard brake.
3. **Pricing.** US$34.99 per year undercuts the cluster of directly comparable apps at US$39.99 for no clear gain. US$4.99 per month makes the monthly plan too attractive against the annual one.
4. **Wrapped belongs in Free.** It is the biggest share moment. Spotify reports 500M+ Wrapped shares in the first day of the 2025 campaign (press coverage of company statements). Putting it behind the paywall turns off the main growth loop.
5. **"30-day history" should be "locked, not lost."** Hiding a user's own local data creates resentment. Keep every trip summary free forever and gate the deep analysis of older drives.
6. **North star and activation.** "≥3 trips a week" rewards driving *more*, which goes against the brand and is out of the user's control (remote work, winter car storage). It also counts passive logging that nobody looks at. Count value delivered instead.
7. **No streaks of any kind tied to driving.** This is a Duolingo instinct to resist: a daily-drive streak rewards unnecessary driving, and a "smooth streak" pressures people to avoid needed braking.

**Gate 1 decisions for the product owner** are listed in §8.

---

## 1. Personas

These are hypotheses built from the Sep 2026 research audiences and competitor review themes. Validate them in the 16–20 interviews proposed in the [next-phase plan](../research/2026-09/02-next-phase-research-plan.md). Names and details are illustrative. Each persona keeps its own disagreements rather than being averaged into a generic user, as that plan asks.

### Priority order
1. **E1 The Daily-Driven Enthusiast**: the hero. Design every default for them.
2. **I1 The EV Efficiency Optimiser**: the bridge persona, enthusiast-adjacent and fastest to pay.
3. **E2 The Data Tinkerer**: the credibility persona. Win their respect, but don't build the roadmap for them.
4. **I2 The New Adult Driver**: the secondary coach view. Handle with care.

---

### E1 · The Daily-Driven Enthusiast (hero)
*"Marcus", 31, Mississauga ON. 2023 GTI, manual. 35-minute commute, a weekend back-road drive once a month. Posts in a model subreddit and a local Discord. Uses Strava for running.*

| | |
|---|---|
| **JTBD** | *When* I drive the car I chose because I love driving, *I want* proof that I drive it well and something good-looking to show for it, *so that* my everyday commute feels like part of the hobby and not dead time. |
| **Triggers to install** | A new car or a new mod. Seeing a share card in a car subreddit or IG story. Spring "un-storage" season. A friend's Story. Getting bored of a tracker app that looks dated. |
| **Anxieties** | "Will it kill my battery?" "Is this an insurance app in disguise?" "Will it nag me like a driving instructor?" "Will it miss half my drives?" Capture reliability was the #1 complaint across competitor reviews in the Sep 2026 research. |
| **Moment of delight** | Parked after the first commute, he opens the Story and sees the **G-dot replay his on-ramp**, with the line he took visible in the friction circle. Then: *"Smoothness 84: your baseline. Cleanest stretch: 6 km of Lakeshore with no abrupt inputs."* He screenshots it. |
| **Pays for today** | Car parts, detailing, a track day once a year. Strava subscription. Expect low tolerance for "just another subscription". |
| **Pro trigger** | Day 10: the commute becomes a Route. He wants to **overlay today against his best** and see where he gained. |
| **What makes him uninstall** | Missed trips in week 1. Noticeable battery drain. Tips that scold normal driving ("You braked hard!" at a red light someone ran). A paywall before the first Story. Any hint of data going to insurers. A score that feels random from day to day on the same route. |

### E2 · The Data Tinkerer
*"Priya", 27, Seattle WA. ND Miata, autocross-curious. Uses RaceChrono at a single track day and exports to a spreadsheet. Reads the forums for everything.*

| | |
|---|---|
| **JTBD** | *When* I've changed something (tyres, alignment, my own technique), *I want* measured evidence from real drives, *so that* I know whether it made a difference and I'm not just imagining it. |
| **Triggers** | A mod or a new set of tyres. A forum thread about phone-based telemetry. Frustration that track apps are overkill for the road. |
| **Anxieties** | "Is phone IMU data garbage?" "What is the sample rate?" "Is the score a black box?" "Can I get my raw data out?" False precision (decimal places that are not accuracy) is a known complaint pattern in the Sep research. |
| **Moment of delight** | The **data-quality badge** says "Good data · phone mounted · 50 Hz". The G-G plot matches what she felt in the sweeper. **CSV export** opens cleanly with timestamps, units and channel provenance (observed, derived, estimated). |
| **Pays for today** | RaceChrono Pro (one-time purchase, US$18.99 on Play per the Sep evidence), Harry's LapTimer and similar tools. Prefers one-time payment. Will pay a subscription for real depth. |
| **Pro trigger** | Export, full-history analysis, segment comparison. |
| **What makes her uninstall** | An unexplained score. Export locked or lossy. Precision she can show is fake. No lap or segment timing *ever* (v1 must say honestly that track mode is a roadmap item). |
| **Strategic warning** | She is loud on forums, and her approval brings credibility. Her feature requests (0–60 timing, lap timers, max-G) pull toward speed, though. **Serve her with transparency and export, not with timing.** |

### I1 · The EV Efficiency Optimiser (bridge)
*"Jordan", 38, Ottawa ON. Ioniq 5, plus a family crossover. Tracks kWh/100 km from the car's display. Winter range anxiety. Reads EV subreddits.*

| | |
|---|---|
| **JTBD** | *When* I drive my EV, especially in winter, *I want* to know which habits cost me range, *so that* I can drive in a way that gets me further without thinking about it. |
| **Triggers** | The first winter with the EV. A road trip. Seeing efficiency stats in another EV owner's post. |
| **Anxieties** | "Will its efficiency score contradict my car's kWh display?" (It may: v1 measures **behaviour proxies, not energy**, as the research makes clear.) Battery drain on the phone. Location privacy. |
| **Moment of delight** | Weekly recap: *"Smoothest week yet. 41% fewer abrupt stops than in October."* The stop-start pattern on the school run is visibly smoother, and their own kWh number has gone down at the same time. |
| **Pays for today** | Charging network memberships, an EV-specific app or two. Comfortable with subscriptions. |
| **Pro trigger** | Trends over months, per-car comparison in the Garage, a second car. |
| **What makes them uninstall** | The app claims fuel or energy savings it cannot measure. Efficiency tips that are generic ("drive slower"). Battery cost on long trips. |
| **Copy rule** | Never "you saved 12% energy". Say "smoother habits like these usually help range. Check your car's display to see your number." |

### I2 · The New Adult Driver
*"Sam", 22, Calgary AB. Fully licensed eight months ago. Drives a 2014 Corolla handed down from a parent. Nervous on highways. Uses Duolingo.*

| | |
|---|---|
| **JTBD** | *When* I'm still building confidence, *I want* calm, private feedback that I'm getting better, *so that* I feel ready for highways, night driving and passengers. |
| **Triggers** | The first highway trips. A near-miss. Advice from a parent or instructor to "watch your braking". Moving to a new city. |
| **Anxieties** | Being judged. Being *monitored* (by parents or insurers). A low score making them more nervous behind the wheel. Doing the wrong thing with the phone while driving. |
| **Moment of delight** | After their first solo highway drive: *"Calm merge onto Deerfoot. Smoothness 88, your best yet on a highway."* There is no "but". |
| **Pays for today** | Very price-sensitive. Probably stays on Free, which is fine: they are retention and word-of-mouth, not revenue. |
| **Pro trigger** | Rarely. Possibly a gift, or a "Ride-along pass" from a friend. |
| **What makes them uninstall** | Being scolded. Anything that looks like surveillance or a parent-sharing feature. Notifications that arrive while they're driving. |
| **Guardrails** | **Adults only (18+) in v1.** Teen and parent monitoring is a different product with consent and privacy work behind it (the research says the same). The coach view shows **one** focus and always leads with what went well. |

### Persona implications for the PRD (A4)
- **Defaults follow E1:** automatic capture, a fast Story, Routes.
- **E2 gets transparency:** a data-quality badge, provenance labels, and export that cannot be taken away. **I1 gets the Garage and trends. I2 gets the coach view and a gentle tone.**
- **What is out of v1 must be said publicly** ("no timing, no leaderboards, no insurer, ever on public roads"). This turns E2's disappointment into respect.

---

## 2. Positioning

### Market frame (observed 6 Nov 2026)
| App (iOS US) | Subtitle on the store | Lead |
|---|---|---|
| Open Road: Drive Tracker GPS | "CarPlay Speedometer, Top Speed" | Speed, social, convoy |
| SpeedRank - Trip Tracker | "#1 Speedometer & Driving Stats" | Speed, global leaderboards |
| Enroad – Drive Tracker | "#1 Drive Tracker" | Stats; reviews praise top and corner speed |

Sources: App Store listings (see §9). Speed is the default way the category sells itself. **Smoothness and story is open ground**, and it is also the safest place to stand legally and with the stores.

### Three candidate value propositions

**A. Recap-led: "Strava-grade recaps for your daily drive."**
*Tagline:* **Every drive, decoded.**
Every trip becomes a short, beautiful Story, and your weeks and years become recaps worth sharing.
- *For:* it is instantly understood (people know "Strava for X") and visually led. It suits IG and Reddit.
- *Against:* it is a **format**, not a reason. Competitors already ship replays and share cards (Open Road advertises share cards and streaks). "X, decoded" is a common headline construction, which makes the line hard to own.

**B. Mastery-led: "Smooth is a skill. DrivePerf measures it and helps you master it."** ← **recommended**
*Tagline:* **Smooth is a skill.** · *Descriptor:* Every drive, decoded.
Positioning statement: *For people who love driving and do it every day, DrivePerf is the drive tracker that turns every trip into a short story and a smoothness score you can actually improve, with no speed contests, no extra hardware and nothing sold to insurers.*
- *For:* it gives a **reason to come back** (getting better), not just something to look at. It speaks to enthusiasts as skill ("car control"), to EV drivers as range habits and to new drivers as confidence, through one idea. It contrasts directly with the speed apps. It is safe for the stores (Apple guideline 1.4.4).
- *Against:* "smooth" can read as tame to some enthusiasts. Counter that with craft language (inputs, line, composure) and with visuals that are *more* exciting than the speed apps', such as the G-dot replay.

**C. Trust-led: "The private drive journal."**
*Tagline:* **Your drives. Your data. Beautifully kept.**
- *For:* it is a real differentiator, given how muddled competitor privacy wording is (see the DrivingTracker and Open Road policy notes in the Sep research), and it is a strong reassurance line.
- *Against:* privacy is a **reason to trust, not a reason to install**. It works as the third proof point under B, not as the headline.

**Recommendation: B, with A as the supporting descriptor and C as the third proof point.** The landing hero reads: *Smooth is a skill.* / *Every drive, decoded: automatic recording, a smoothness score you can improve, and recaps worth sharing. Private by design.*

**Runner-up: A.** Choose it if interviews show that enthusiasts hear "smooth" as "nanny app". **Test both lines in the A9 landing-page waitlist** (two variants, sign-up rate per variant) before store copy is locked.

Clearance flag: check "Smooth is a skill" for trademark conflicts before A6 builds identity around it.

### Store metadata
| Field | Limit | Recommended | Runner-up |
|---|---|---|---|
| App name (both stores) | 30 | `DrivePerf: Drive Tracker` (24) | `DrivePerf – Smooth Driving` (26) |
| App Store subtitle | 30 | `Smoothness score & drive recap` (30) | `Smooth driving, decoded` (23) |
| Play short description | 80 | `Auto-record every drive. See your smoothness, weekly recaps and route records.` (78) | `Every drive, decoded: auto trip recording, smoothness score and weekly recaps.` (78) |

Notes: limits are confirmed in the [Play Console help](https://support.google.com/googleplay/android-developer/answer/9859152) (name 30, short description 80) and in consistent secondary sources for the Apple 30-character subtitle (§9). "Drive tracker" is the category search term and goes in the name. "Score" and "recap" go in the subtitle. Avoid "best", "#1" and "top" in metadata: they are ranking words (Google Play's metadata policy restricts ranking claims, so check its current wording, and they fight the brand). That is why it says "route records", not "personal bests". **"Auto-record" is a promise**, so keep it only if A3 confirms reliable automatic capture on both platforms.

---

## 3. Signature experiences (storyboards)

Global motion and safety rules that apply to all four:
- **Nothing plays, animates or asks for input while a trip is active.** Stories are generated at trip end and opened only when the phone is no longer in an automotive state. The Live Activity and Android Live Update show **recording status only: no live score, no speed and no "on pace for a best"**.
- There is a reduced-motion variant for every frame (cross-fades replace draw-ons). Every frame has a VoiceOver/TalkBack label carrying its copy. Colour is never the only signal.
- First meaningful paint takes under 3 s (from the plan). Frames auto-advance, a tap advances, and a swipe down exits.
- The data-quality state is honest on every surface: *observed / derived / estimated*.

### 3.1 Drive Story (every trip, about 15 s)

| # | Time | What animates | Copy (example) | Notes |
|---|---|---|---|---|
| 0 | — | Post-trip notification, delivered only once parked | **"Drive decoded."** Home → Work · Smoothness 84 | One notification per drive. Quiet hours respected. |
| 1 | 0–1.5 s | The car's chip slides in. The background tints to the car's Garage colour. | **Tuesday evening · Home → Work** · GTI | Part of day, not a timestamp (this carries over to share cards). One-tap "I was a passenger" chip. |
| 2 | 1.5–4 s | The route **draws on** from start to end. The first and last few hundred metres are faded. The camera eases to fit. | **24.6 km · 31 min** | No top speed, here or on any frame. |
| 3 | 4–7 s | **G-dot replay**: a dot travels the friction circle, compressed, with a fading trail. A tick on the route map marks where you are. | **Your inputs, replayed.** Every brake and turn in 3 seconds. | The signature visual. It replaces speed as the exciting element. |
| 4 | 7–10 s | The **smoothness ring** fills with soft haptic ticks and the number counts up. A comparison chip pops in. | **Smoothness 84** · +3 vs your usual on this route | First drive: **"Your baseline. Every drive from here is compared to you, not to anyone else."** |
| 5 | 10–13 s | One **segment of the route glows** and the rest dims. | **Cleanest stretch:** 6.1 km on Lakeshore with no abrupt inputs. | Lead with what went well, always. |
| 6 | 13–16 s | A small trace sparkline appears under the tip. The relevant spot pulses on the map. | **One thing for next time:** ease off earlier before the Bathurst light. That stop cost the most smoothness. | Exactly one tip, backed by evidence (tap to see the trace). Never a tip about a stop the user marked as necessary. |
| 7 | end | The buttons rise in. The data-quality badge is shown. | **Explore the drive** · **Share** · **Done** · *Good data · phone mounted* | Pro users go to the full Analysis. Free users get Analysis for the last 30 days. |

**Edge-case frames:**
- *Degraded data:* "Your phone moved around for about 3 minutes, so we left that part out of the score."
- *Unscorable:* "We couldn't score this one fairly (phone in a bag). Here's the route anyway."
- *Necessary braking:* "Hard stop at King & Spadina. Was it needed? **Yes, it was needed**" → the stop is excluded from the score, with no further questions.

### 3.2 Routes and route records

| # | Trigger | What animates | Copy | Notes |
|---|---|---|---|---|
| R1 | The 2nd similar trip | In the Story, frame 2, **two route lines slide together and merge** into one glowing line. | **Looks like a regular.** Name this route? `Commute` `Gym` `Custom…` | User-named. The default name is never a street address. |
| R2 | Route page | The route glyph is the hero. A dot strip shows every run, with the best one in gold. | **The Commute** · 12 drives · Route record **91** (14 Oct) | The record is **smoothness only, never duration.** |
| R3 | A new record | In Story frame 4, the ring **sweeps past the old record marker**, which flips to gold. A double haptic. One shimmer and no confetti. | **New route record.** 91 on The Commute, beating 89 from 14 Oct. | Quiet pride, not a casino. |
| R4 | Compare (Pro) | A **ghost line** of the record run overlays today's. Segments shade by smoothness gained or lost. | **Where you gained most:** the 403 on-ramp. You braked earlier and lighter. | Duration is shown neutrally in the table, never as a delta or a win. No "x minutes faster". |
| R5 | End of month | The month's runs stack as thin lines and the smoothest one lifts out. | **Smoothest commute in October:** Thursday the 23rd. | Feeds the weekly recap and Wrapped. |

**Safety flag (for A12, the scoring spec):** a route record creates an incentive to protect it. (1) There is never live feedback on record pace. (2) **A complete, gentle stop must score as well as not stopping.** Score jerk and abruptness, not the presence of a stop, so that a rolling stop is never rewarded. (3) Stops marked as necessary are excluded. (4) Make records hard to "game" by driving slowly: report that a trip's typical pace was unusual and decline to set a record on it, rather than rewarding crawling. This needs design work with A12.

### 3.3 Weekly recap (Sunday 18:00 local, about 20 s)

| # | What animates | Copy | Notes |
|---|---|---|---|
| W0 | Push notification | **Your week, decoded.** 9 drives · Smoothness 82 (+3) | Sent only if the phone is not in a trip. Otherwise it is held until parked. |
| W1 | A 7-day strip. One dot per drive, sized by distance, landing day by day. | **9 drives · 212 km · 4 h 40 min** | |
| W2 | A 4-week trend line. This week's point drops in and the line redraws. | **Week average 82, up 3.** | If it went down: "Down 2. Thursday's drive pulled it down. Here's why →". Explain, never blame. |
| W3 | The best drive's card **flips over**. | **Drive of the week:** Wednesday morning, The Commute, 89. | |
| W4 | Focus habit: a small 3-week meter fills. | **Your focus: braking into stops.** Gentler in 7 of 9 drives this week. | One habit at a time, chosen from the evidence. |
| W5 | The share card assembles from the frames. | **Share your week** · **See you next Sunday** | |
| Empty week | A parked-car illustration. | **No drives this week. Your car got a rest.** | No guilt and no "come back". |

### 3.4 Drive Wrapped (a monthly mini-Wrapped, and the annual one from 1 December)

Monthly: five frames (W1–W4 plus an archetype), on the 1st of each month. This gives habit and share moments before the first December. The **annual** Wrapped:

| # | What animates | Copy (template) | Notes |
|---|---|---|---|
| 1 | A road line crosses a black screen. The year assembles from the user's top-route glyphs. | **2027, decoded.** | |
| 2 | An odometer roll. | **You drove {14,286} km.** That's about {n}× {a comparison from a vetted list}. | Comparisons are pre-checked facts. Neutral about volume: no "wow, so much driving". |
| 3 | A clock face fills by hour. | **{312} hours behind the wheel.** Your most-driven hour: **{5 pm}**. | |
| 4 | **Archetype card**: an illustration flips in. This is the share hero. | **You're The Glider.** Few abrupt inputs, all year. | Archetypes: Glider, Metronome (consistency), Climber (most improved), Night Owl, Road-Tripper, Urbanist (stop-start mastery). **Never** "Speed Demon", "Hooligan" or "Send-It". |
| 5 | The smoothest drive redraws. | **Smoothest drive of the year:** {12 Sep}, {The Lake Road}, **96**. | User-named routes only. |
| 6 | A before/after ring. | **Most improved:** The Commute, **74 → 88** (Feb → Nov). | |
| 7 | Garage bars. | **The GTI did 71% of the driving.** The Corolla did the grocery runs. | Multi-car is Pro. One car gets a single-car frame. |
| 8 | A habit meter. | **Habit mastered:** gentler stops. {38}% fewer abrupt stops than in March. | Only if the change is statistically real. Otherwise skip the frame. |
| 9 | Three generated cards fan out. | **Share your year.** | The route-glyph style is the default (§4). |

Comparison to other users ("top 5% smoothest") is **out of v1**. It is leaderboard-adjacent and needs a large, fair baseline. Revisit for Wrapped 2028.

**Wrapped is free** (Pro adds extra chapters: per-route deep dives and card styles). See §5 for why.

---

## 4. Growth loops

### 4.1 Share cards (privacy-safe by default)

| Card | Content | Default privacy |
|---|---|---|
| **Route glyph** (default) | The route **shape only, with no basemap and no place names**. Smoothness ring, car name and part of day. | Start and end trimmed by a **randomised 400–800 m**. No timestamp. Glyph is not geo-referenced. |
| **Ring** | Smoothness, a route-record badge, a car silhouette. No route at all. | Maximum privacy, for people who share often. |
| **Week strip** | The weekly recap strip and trend. | No locations. |
| **Archetype** | The Wrapped archetype illustration and two stats. | No locations. |
| **Map** (opt-in) | A stylised basemap, trimmed ends, place labels off. | **Explicit toggle each time** with a "this shows where you drove" note. |

Rules:
- **Never on a card:** speed, max G, 0–60, duration as an achievement, exact time and date, or start and end points.
- **No real-time location leakage:** cards say "Tuesday evening", never "just now". Image metadata (EXIF and geotags) is stripped.
- **Share is only available once the trip has ended.** There are no share prompts during a trip.
- **There is always a small DrivePerf mark** (the loop). Pro gets a more minimal mark, not none.
- Formats: IG Story 9:16, a Reddit- and Discord-friendly 4:5, and an alt-text string copied with the image.
- Precedent for the risk: in January 2018 Strava's aggregated heatmap exposed military sites through users' activity ([Wareable](https://www.wareable.com/sport/strava-heat-maps-military-bases-5682), [TechCrunch](https://techcrunch.com/?p=1592280)). Distinctive route shapes can still be recognisable. Explain this in the privacy centre, and make the Ring card one tap away.

### 4.2 Referral: the "Ride-along pass" (recommended)
- Any user can send a link from a share card or from Settings. **The friend gets 30 days of Pro** (instead of the standard 14-day preview).
- **The sender gets 1 month of Pro when the friend activates** (always-on capture plus 3 Stories viewed), not on install. Capped at 6 months a year.
- Delivered through RevenueCat promotional entitlements or store offer codes. Apple supports one-time and custom offer codes for subscriptions ([App Store Connect help](https://developer.apple.com/help/app-store-connect/manage-subscriptions/set-up-introductory-offers-for-auto-renewable-subscriptions); Apple has no referral-specific rule that I could confirm, so check App Review notes before launch).
- **Never** reward distance, number of drives, ratings or reviews (both stores prohibit incentivised ratings).
- **Runner-up: Club codes.** A car-club organiser gets a code that gives members 30 days of Pro. A "club recap" (aggregate and anonymous) is a v1.1 idea. **Flag:** anything that ranks club members against each other is a leaderboard, which is out of v1.

### 4.3 Community seeding (car communities)

Principles: **founder-led, always disclose that you built it, ask moderators first, follow each community's rules, no astroturfing and no bought votes.** Reddit's own guidance and individual subreddit rules on self-promotion vary and change, and some subreddits ban it outright (secondary sources in §9). Read each sidebar before posting.

| Channel | Where (examples to verify) | Play |
|---|---|---|
| **Reddit, model-specific** | Owner subreddits for the GTI, Miata, WRX/BRZ, Civic Si/Type R, Mustang, Ioniq 5/EV6 and Model 3/Y, plus general EV and car subreddits | Participate for real for 4–6 weeks first. Then one **"I built this, roast it"** post per community, where allowed, linking to the beta. Model-specific content such as "What smooth looks like on a G-G plot, with a ND Miata example". The founder answers every comment. |
| **Reddit, general** | Large general car subreddits | Only through allowed formats (weekly threads or mod approval). Lead with an educational post (how phone motion data works and what it *can't* tell you). Credibility over clicks. |
| **Forums** | Model forums (for example VWVortex, Miata.net, NASIOC, CivicX, Tesla Motors Club, Mach-E forums) | A long-form build-log thread: "Building a smoothness-first drive tracker". Monthly updates. Ask the forum's Data Tinkerers to test export. |
| **Discord** | Our own **"DrivePerf Garage"** server (#feedback, #route-records, #my-car, #beta-bugs), plus model and city meet servers with mod permission | Beta home. Weekly "Story of the week" (opt-in, privacy-safe cards only). Moderation rule: remove anything showing speed or phone use while driving. |
| **Instagram** | Small car photographers and daily-driver creators (5–20k followers), Cars & Coffee pages in the GTA, Vancouver and Calgary, and US metros | A "Daily Driven" series: the creator's car, their Week-strip card, filmed parked. Gift Pro, not cash, at first. Disclose partnerships. |
| **IRL** | Cars & Coffee meets in the spring | QR stickers for "Get your first Drive Story". A founder at the meet. |

**Recruit 200–300 "Founding Drivers"** for the TestFlight and Play closed beta, balanced across E1, E2, I1 and I2 and across iOS and Android. They get a founding badge and the founding-member price (§5).

### 4.4 Launch-day plan ("Season Opener", target spring 2027)
Why spring: many Canadian and northern-US enthusiasts store summer cars over the winter. Taking the car out of storage is an emotional "first drive of the season" moment, which suits the "First Story" hook. *(This is reasoning, not a measured statistic; confirm the behaviour in interviews.)* A rough sum of the plan's phase durations also lands in about Mar–Apr 2027.

| When | Action |
|---|---|
| T-8 wks | Waitlist live (A9/A18), with A/B positioning test B vs A. App Store pre-order and **Play pre-registration** pages up. |
| T-6 wks | Founding Drivers beta at full size. Press kit: Story GIFs, *parked* car footage, the privacy one-pager. |
| T-3 wks | Fix-only freeze. Capture-health and battery dashboards green on the beta (see the §7 guardrails). |
| T-1 wk | Embargoed briefings: auto-tech writers in the US and Canada. Founder AMA scheduled in one allowed subreddit. |
| **Launch (Tue)** | Both stores, same day. Email the waitlist (founding-member intro price for 30 days). Product Hunt (secondary audience). Discord launch event. Founding Drivers' Stories as social proof. |
| L+1 to L+7 | Staggered community posts (one community a day, never all at once). Reply to **every** review within 24 h. A daily "capture health" check (missed-trip reports). |
| L+14 | The first weekly recaps land for launch cohorts, the second share wave. A "Season Opener" mini-Wrapped for the first 2 weeks. |

**Launch video rule:** the phone is mounted and untouched while the car moves. All interaction happens parked. No speedometer, no sound of launching hard, no "watch this".

### 4.5 Unsafe-driving flags (summary)

| Feature or idea | Risk | Mitigation | Owner |
|---|---|---|---|
| Route records | Pressure to avoid a needed stop, or rolling stops | Score jerk, not stops. Necessary-stop exclusion. No live record pace. | A12, A4 |
| Any live score in a Live Activity, widget or watch | Glancing at the screen while driving, chasing the score | Recording status only while driving | A4, A15/16 |
| Streaks ("drive every day", "smooth streak") | More driving, avoiding braking | **No driving-behaviour streaks in v1.** At most a "recap-watching" streak. | A4 |
| Max G, top speed, 0–60 on cards or in Wrapped | Glorifying speed and hard cornering. Apple 1.4.4; see Lemmon v. Snap below. | Never on cards or Wrapped. Shown neutrally in Analysis only. | A6, A4 |
| Leaderboards and club rankings | Competitive driving on public roads | Out of v1 | A4 |
| Community user-generated content | Reposting speed or phone-in-hand clips | Moderation policy. Never repost. | Growth |
| Notifications | Distraction while driving | Held until parked. Max 1 per drive. | A4, A15/16 |
| Duration comparisons on Routes | "Beat your time" racing on the commute | Duration is never a record and never a highlighted delta | A4, A6 |

Legal precedent: in *Lemmon v. Snap* (9th Cir., 4 May 2021), the court held that Section 230 did not shield Snap from a negligent-design claim over its Speed Filter, which was tied to a fatal high-speed crash. Snap removed the filter that year ([ABA Journal](https://www.abajournal.com/news/article/snapchat-can-be-sued-for-speed-filter-used-by-youths-before-fatal-accident-9th-circuit-rules), [Harvard JOLT](https://jolt.law.harvard.edu/digest/lemmon-v-snap-inc-ninth-circuit-chips-away-at-tech-companies-section-230-immunity)). The design of a feature can create liability. Get counsel to review this. It is not legal advice.

---

## 5. Monetization

### 5.1 Comparable public pricing (observed 6 Nov 2026 unless stated)

| App | Model | US | Canada | Source and confidence |
|---|---|---|---|---|
| **Open Road: Drive Tracker** (closest iOS peer) | Freemium + Pro | $9.99/mo · $39.99/yr · $2.99/wk | CA$12.99/mo · CA$49.99/yr · CA$3.99/wk | App Store US and CA listings · high |
| **SpeedRank** | Freemium + Pro | $8.99/mo · $39.99/yr | — | App Store US · high (small app, 9 ratings) |
| **Enroad – Drive Tracker** | Freemium + Pro | $6.99/wk. Several monthly ($9.99–13.99) and yearly ($11.99–57.99) prices are listed but unlabelled. | — | App Store US · medium (tiers unclear) |
| **DOWNFORCE** (Android) | Two tiers | Pro €9/mo or €79/yr; Full €19/mo or €159/yr; 7-day trial | — | Sep 2026 evidence, Play listing · medium (EUR) |
| **PACE** | Ads + Pro | €3.99/mo · €29.99/yr | — | Sep 2026 evidence, vendor site · medium |
| **Speedometer: Driving Tracker** (iOS) | Sub + lifetime | Reported $4.99/mo, $19.99–29.99/yr, $39.99 lifetime | — | Third-party deal posts · **low** |
| **Strava** (adjacent benchmark) | Freemium + sub | $11.99/mo · $79.99/yr | CA$14.99/mo · CA$99.99/yr | App Store US; strava.com/pricing (CA) · high |
| **Oura** membership (adjacent) | Hardware + sub | $5.99/mo · $69.99/yr | — | Secondary sources citing Oura · medium |
| **Life360 Gold** (driving reports) | Family bundle | — | CA$15.99/mo | Sep 2026 evidence · medium |
| Market medians (all categories) | — | $12.99/mo · $38.42/yr (2025 global medians) | — | Adapty, State of In-App Subscriptions 2026 · medium |

What this shows: **directly comparable driving apps cluster at US$39.99 a year and US$9–10 a month** (CA$49.99 and CA$12.99 for Open Road). Canadian prices are set *below* FX parity. Open Road and Strava both price Canada at about 1.25× USD, while USD/CAD was about 1.42 in October 2026 (third-party rate sites; see §9). Canadian buyers anchor on the round CA$ number, not the converted one.

### 5.2 Recommended prices

| | US | Canada | Logic |
|---|---|---|---|
| **Annual (hero)** | **US$39.99** | **CA$49.99** | Parity with the peer cluster and a better product. US$34.99 saves the buyer $5 but signals "budget", and leaves revenue on the table. |
| **Monthly** | **US$6.99** | **CA$8.99** | Under the peers ($8.99–9.99), so it isn't a deterrent. Annual is still 52% cheaper than 12 months (US$83.88), which steers people to annual. Health & Fitness is the one category where annual plans hold most revenue (60.6%, Adapty 2026). |
| **Founding-member intro offer** | **US$29.99 for the first year** | **CA$39.99 for the first year** | Waitlist, beta and launch month only. Uses the store's introductory "pay up front" offer, so it is one per subscription group per Apple's rules. Rewards early believers without lowering the list price. |
| Weekly plan | **None** | None | Weekly plans hold 55.5% of app revenue across Adapty's dataset, but they read as predatory to enthusiasts and clash with the trust brand. |
| Lifetime | **Not at launch** | — | Revisit at 6 months as a capped "founders lifetime" around US$119.99 if forums push back on the subscription. E2 likes one-time purchases. |

**Runner-up:** the plan's US$4.99/mo and US$34.99/yr (CA$6.99 / CA$44.99). Choose it only if beta willingness-to-pay interviews show the US$39.99 annual price blocks conversion for E1.

Lesson from adjacent apps: Strava's monthly price rise of more than 25% drew a public backlash ([BikeRadar](https://www.bikeradar.com/news/strava-hikes-monthly-subscription-cost-by-more-than-25-per-cent/)). **Launch at a price you can hold for 2+ years.** Raising prices in an enthusiast community is costly.

### 5.3 Free vs Pro

| Capability | Free | Pro |
|---|---|---|
| Recording (automatic + manual), unlimited | ✅ | ✅ |
| Drive Story for every trip | ✅ | ✅ |
| Smoothness, efficiency-proxy and event scores | ✅ | ✅ |
| Trip history | ✅ **All summaries, forever** ("locked, not lost") | ✅ |
| Analysis view (map, synced timeline, G-G) | ✅ **Drives from the last 30 days** | ✅ **Full history**, plus advanced channels and segment tools |
| Routes and route records | ✅ Detection and records for **2 routes** | ✅ Unlimited routes, **ghost comparison**, "where you gained" |
| Trends | Weekly | Monthly, yearly, per car, per route |
| Weekly recap | ✅ | ✅ |
| **Drive Wrapped** (monthly + annual) | ✅ **Core chapters** | ✅ Extra chapters and card styles |
| Share cards | ✅ All privacy-safe styles | ✅ Plus the minimal mark and extra styles |
| Garage | 1 vehicle | Unlimited vehicles |
| Export | ✅ **"Download all my data"** in the privacy centre (account archive) | ✅ Per-trip GPX/CSV with sensor channels and provenance |
| Cloud backup and multi-device restore | Local + OS device backup | ✅ Cloud backup (it costs us Firebase Blaze storage) |
| Privacy centre, delete anything | ✅ | ✅ |

**Why this split.** Free is generous on everything that drives **habit and sharing**: capture, the Story, the weekly recap and Wrapped. Pro sells **depth over time**, the value that grows with every drive. That mirrors Strava, which keeps recording and sharing free and gates Matched Activities, Best Efforts, routes and advanced analysis ([Strava support](https://support.strava.com/hc/en-us/articles/216917657)). Privacy basics (delete and full data download) are **never** paywalled: that is the trust promise.

### 5.4 Trial strategy

**Recommended: a 14-day "Pro Preview" reverse trial.** Every new user gets Pro from install without a card. On day 15 they move to Free.
- *Why not a card-up-front store trial at install:* the user hasn't driven yet. RevenueCat reports that 55.4% of 3-day-trial cancellations happen on day 0 ([RevenueCat 2026](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026)). A driving app's value builds up over repeated drives: a Route needs at least 2 runs and a recap needs a week.
- *Why 14 days:* that covers two weekly recaps and normally several runs of the commute, which is enough to form a Route and feel ghost comparison. RevenueCat also reports that longer trials (17–32 days) convert at a 42.5% median vs 25.5% for trials under 4 days. *(Those figures are for card-on-file store trials; a reverse trial is a different mechanism, so treat this as directional.)*
- *Risk:* reverse trials usually convert fewer users than card-required trials, and RevenueCat's freemium medians are low (2.1% day-35 conversion vs 10.7% for hard paywalls). **A/B test in the beta and after launch** (RevenueCat Experiments): Preview vs the runner-up.

**Runner-up:** a 7-day store free trial on the annual plan, offered at the first Route comparison moment.

### 5.5 Paywall moments
1. **Primary, day 15: "Your first two weeks, decoded."** A mini-Wrapped of the Preview period. It ends on: *"Pro showed you 3 routes and 11 comparisons. Keep it for US$39.99/yr: your first year is US$29.99."* The annual plan is pre-selected, monthly is visible, and **"Continue with Free"** has equal visual weight.
2. **Contextual locks** after the Preview: tapping *Compare with your record*, an Analysis older than 30 days, a 3rd route, a 2nd car, or per-trip export. Each lock **shows a blurred preview of the user's own data**, not a stock screenshot.
3. **Never:** during onboarding (only "Pro Preview included"), in a push notification, right after a *worse-than-usual* drive, or while a trip is active.

---

## 6. Brand voice guide

**Character: the calm co-pilot.** It has a driving instructor's eye, a friend's warmth and an engineer's honesty. It is quietly proud, never loud. It explains and does not judge.

| Dial | Setting |
|---|---|
| Calm ↔ Hype | Calm (8/10) |
| Expert ↔ Casual | Expert in substance, casual in phrasing |
| Warm ↔ Clinical | Warm, with dry wit allowed once per screen at most |
| Directive ↔ Suggestive | Suggestive ("next time, try…") |

**Words we use:** smooth, composed, clean, flow, line, rhythm, inputs, gentle, earlier, steady, record (route record), decoded.
**Words we avoid:** aggressive, crush, dominate, beast, send it, launch, fastest, top speed, beat your time, bad driver, risky driver, violation, "you saved X% fuel".

### 10 do/don't pairs

| # | Context | ✅ Do | ❌ Don't |
|---|---|---|---|
| 1 | Score reveal | "Smoothness 84: 3 above your usual on this route." | "Smoothness 84/100. Room for improvement!" |
| 2 | Hard brake | "Hard stop at King & Spadina. Was it needed?" | "Harsh braking detected! −8 points." |
| 3 | Tip | "Next time, ease off a little earlier before the Bathurst light." | "You brake too late. Fix it." |
| 4 | Efficiency | "Smoother stops like these usually help range." | "You saved 12% energy this week!" |
| 5 | Record | "New route record. 91 on The Commute." | "🔥 CRUSHED IT! Fastest commute ever!" |
| 6 | Data quality | "Your phone moved around for about 3 minutes, so we left that part out." | "Error: invalid sensor data." |
| 7 | Empty week | "No drives this week. Your car got a rest." | "We miss you! Don't lose your progress!" |
| 8 | Privacy | "Your trips stay on your phone unless you turn on backup." | "We take your privacy seriously." |
| 9 | Paywall | "Keep comparing every run of The Commute with Pro." | "Unlock your full potential! Limited time!" |
| 10 | Enthusiast pride | "Clean line through the on-ramp. Composed all the way." | "That corner was 0.9 g, you animal." |

### Notification copy (all held until the phone is out of a trip; quiet hours 22:00–07:00 by default)

| Type | Copy |
|---|---|
| After a drive | **Drive decoded.** Home → Work · Smoothness 84 |
| After the first drive | **Your first Drive Story is ready.** Two minutes well spent. |
| Route discovered | **Looks like a regular.** Name the route you've now driven 3 times? |
| Route record | **New route record on The Commute.** 91, beating 89 from 14 Oct. |
| Weekly recap | **Your week, decoded.** 9 drives · average 82 (+3) |
| Monthly Wrapped | **October, wrapped.** You're The Metronome this month. |
| Capture problem (most important) | **Recording is paused.** Location access changed to "While Using". Two taps to fix it. |
| Missed-trip check | **Did you drive around 5 pm?** We may have missed it. Tell us and we'll tune detection. |
| Preview ending (day 13) | **Your Pro Preview ends in 2 days.** Your trips stay yours either way. |
| Lapsed 21 days (once only) | **Recording is still on.** We'll tell you when the next drive is decoded. *(No guilt, sent once.)* |

Rules: at most 1 notification per drive and 1 recap a week. No "we miss you" sequences. Every type can be switched off individually in Settings.

---

## 7. Metrics

### 7.1 North star: **Weekly Decoded Drivers (WDD)**
*Users who, in a calendar week, had **≥2 recorded trips of good or usable quality** and **viewed ≥1 Story, weekly recap or Analysis**.*

- **Why it differs from the plan's "≥3 trips a week":** trip count measures how much someone *drove*, which DrivePerf should never encourage and cannot influence (hybrid work, holidays, winter car storage). It also counts logging that nobody looks at, where we capture but deliver no value. WDD needs both **capture working** (our hardest technical problem) and **insight consumed** (our value). ≥2 rather than ≥3 includes lighter drivers such as I2 and weekend E1s.
- **Runner-up:** the plan's weekly active recorders (≥3 trips).

**Input metrics (the WDD tree):** auto-capture permission rate · trip capture completeness (detected ÷ actual, from missed-trip reports and beta diaries) · usable-quality share · Story open rate per trip · recap open rate · share rate per Story · Pro conversion.

### 7.2 Activation
**Recommended:** *always-on capture granted (iOS "Always" plus Motion & Fitness; Android background location plus activity recognition) **and** the first Story viewed within 72 h of install.*
- 72 h rather than 48 h, because people who don't drive every day would fail a 48 h window for reasons that have nothing to do with the product.
- Requiring always-on capture matters because manual-only users depend on remembering to record, and forgetting to record is a recurring theme in the Sep research. The **habit signal** to watch alongside it is **3 Stories viewed in the first 7 days**.
- Target: **≥45% of installs activated** (a hypothesis; set the real target from beta data). Background location consent is the biggest single drop-off, and A3 must report realistic grant rates per platform.

### 7.3 Retention targets

**Benchmarks (all flagged by confidence):**
| Source | D1 | D7 | D30 | Notes |
|---|---|---|---|---|
| Phiture compilation, cross-industry 2025–26 | ~25–26% | ~11–13% | ~5–7% | Compiled from Adjust, AppsFlyer, Statista and Business of Apps. Updated Mar 2026. **Cells are not attributed to individual sources**, so medium-low confidence. |
| Phiture compilation, Health & Fitness | ~20% | ~8% | ~3–4% | Same caveat |
| Phiture compilation, Travel | ~16% | ~7% | ~3–4% | Navigation and travel are the nearest category to driving |
| Other secondary sources for H&F | 27–45% | 13–25% | 8–15% | Vendor blogs that disagree by about 2× at D30. Treat as **unverified**. |

**DrivePerf targets (engaged retention = the app opened in the foreground on calendar day N, the classic definition for comparability):**

| | Launch target | Stretch | Beta kill/fix line |
|---|---|---|---|
| **D1** | **35%** | 45% | < 25% |
| **D7** | **20%** | 25% | < 12% |
| **D30** | **12%** | 15% | < 7% |
| **Capture retention W4** (still recording trips in week 4) | **≥30%** | 40% | < 20% |
| Pro conversion by day 35 (of installs) | **3%** | 5% | < 1.5% |

**Rationale (reasoning, since there is no verified driving-app benchmark):**
1. A passive-capture app makes its own reasons to return: **every drive produces a notification and a Story**, which content and fitness apps don't get for free. That justifies targets **1.5–3× the Health & Fitness compilation**, roughly top-quartile behaviour.
2. Classic day-N retention punishes apps whose usage follows driving days. Report **weekly retention (W1, W4)** alongside it, and judge the product mainly on W4.
3. **Capture retention** is tracked separately because a user who stays installed and recording, but never opens the app, still feeds Wrapped and could still convert. A large gap between capture retention and engaged retention means the Story isn't pulling people back.
4. The 3% Pro target is about 1.4× RevenueCat's 2.1% freemium median ([RevenueCat 2026](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026)). It is justified only if E1 and I1 are the core of acquisition. Adapty reports a 35.0% trial-to-paid rate for Health & Fitness card trials, which is the benchmark for the runner-up trial design.
5. Expect a hard annual renewal cliff. RevenueCat reports that about 72% of annual subscribers cancelled within year 1 in its 2026 data, and Adapty puts H&F first-renewal retention at 30.3%. Wrapped in December is the **renewal-season value proof** for annual plans bought in spring.

### 7.4 Guardrails (as important as growth)
- **Battery:** ≤ 4–6% per hour of recording (from the plan). Track the rate of reviews and tickets that mention battery.
- **Missed trips:** a missed-trip report rate per 100 trips. A trust killer if it rises.
- **Notification opt-out rate** under 10% in the first 30 days.
- **Safety:** zero share cards or marketing assets showing speed or max G. Store-review feedback logged.
- **Paywall regret:** refund rate and "Continue with Free" share. Don't optimise conversion against trust.

---

## 8. Decisions for the product owner at Gate 1

1. **Hero user:** confirm the *daily-driven enthusiast* (enthusiast identity plus daily driving) as the design default, with the EV optimiser as the bridge persona.
2. **Positioning:** approve **"Smooth is a skill."** as the lead and "Every drive, decoded." as the descriptor, *or* keep the plan's line as the lead. Either way, run the A/B test on the A9 waitlist.
3. **Prices:** **US$6.99 / US$39.99** and **CA$8.99 / CA$49.99**, with a founding-member first year at US$29.99 / CA$39.99, *or* the plan's US$4.99 / US$34.99.
4. **Free/Pro split:** approve **Wrapped in Free**, **"locked, not lost" history**, a free 30-day Analysis window, and a free full-data download.
5. **Trial:** a 14-day reverse "Pro Preview" (recommended) vs a 7-day card trial on the annual plan.
6. **North star and activation:** adopt **WDD** and **always-on capture plus a Story within 72 h**, replacing the plan's definitions.
7. **Safety policy (binding on A4, A6 and A12):** no driving-behaviour streaks, no live score while driving, no speed or max G on any card or in Wrapped, and route records based on smoothness only, with necessary-stop exclusion and no reward for rolling stops.
8. **Launch window:** "Season Opener" in spring 2027 vs the earliest possible date.
9. **Quebec:** the plan says an English-only Canada launch. **Get legal advice on French-language obligations for distributing and marketing in Quebec** before launch. The options are FR-CA localisation at launch or no Quebec-targeted marketing until it's done. (Flagged, not assessed here.)
10. **Referral:** approve the Ride-along pass (30 days for the friend, 1 month for the sender on the friend's activation, capped at 6 a year).

---

## 9. Sources (all accessed 6 November 2026 unless stated)

**Repository inputs**
- [Kickoff plan §2](../planning/DrivePerf-v1-kickoff-plan.md), [Android market research, 17 Sep 2026](../research/2026-09/01-android-market-research.md), [next-phase research plan](../research/2026-09/02-next-phase-research-plan.md), [indie-app evidence](../research/2026-09/indie-driving-apps-expanded-evidence.md) (DOWNFORCE, PACE prices), [enthusiast evidence](../research/2026-09/enthusiast-track-and-pdr-evidence.md) (RaceChrono, Harry's prices), [original competitor evidence](../research/2026-09/android-competitors-evidence.md) (Life360 CA pricing).

**Competitor listings (prices are store listings and can vary by region and over time)**
- Open Road: Drive Tracker GPS, App Store US: https://apps.apple.com/us/app/6755834573 and CA: https://apps.apple.com/ca/app/6755834573
- SpeedRank - Trip Tracker, App Store US: https://apps.apple.com/us/app/id6761128659
- Enroad – Drive Tracker, App Store US: https://apps.apple.com/us/app/id6757245082 (the ID previously appeared as "TripRank - Speed Tracker" in search results)
- Speedometer: Driving Tracker prices (third-party, low confidence): https://www.hotukdeals.com/deals/speedometer-driving-tracker-ios-app-free-lifetime-premium-carplay-gps-obd2-4964612 ; https://www.mydealz.de/deals/iosipados-speedometer-driving-tracker-lifetime-premium-kostenlos-statt-4499-eur-carplay-gps-obd2-2829155

**Adjacent apps**
- Strava, App Store US (IAP list): https://apps.apple.com/us/app/strava-run-bike-walk/id426826309
- Strava pricing, Canada view: https://www.strava.com/pricing
- Strava subscription features: https://support.strava.com/hc/en-us/articles/216917657
- Strava price-rise backlash: https://www.bikeradar.com/news/strava-hikes-monthly-subscription-cost-by-more-than-25-per-cent/
- Oura membership price (secondary, citing Oura support): https://dupple.com/blog/whoop-vs-oura-2026
- Spotify Wrapped 2025, 200M engaged users and 500M+ shares on day one (press coverage of Spotify statements): https://techcrunch.com/2025/12/04/spotify-says-wrapped-2025-is-its-biggest-yet-with-200m-users-in-its-first-day ; https://www.musicbusinessworldwide.com/spotify-wrapped-campaign-hit-200m-engaged-users-in-24-hours-a-19-yoy-increase
- Strava heatmap incident, Jan 2018: https://www.wareable.com/sport/strava-heat-maps-military-bases-5682 ; https://techcrunch.com/?p=1592280

**Benchmarks**
- RevenueCat, *State of Subscription Apps 2026* summary (published 19 Mar 2026, updated 22 Apr 2026; 115k+ apps): https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026 *(vendor research)*
- Adapty, *State of In-App Subscriptions 2026* (16k apps): https://adapty.io/state-of-in-app-subscriptions-2026 *(vendor research)*
- Phiture, retention benchmarks compilation (updated Mar 2026): https://phiture.com/mobilegrowthstack/managing-retention-rate-benchmarks-and-expectations/
- Adjust retention page (not retrievable, HTTP 429; cited only through Phiture): https://www.adjust.com/blog/what-makes-a-good-retention-rate/
- USD/CAD rate, October 2026 (third-party; not checked against the Bank of Canada): https://www.exchange-rates.org/exchange-rate-history/cad-usd-2026-10-02

**Policy and legal**
- Apple App Review Guidelines 1.4.4 and 1.4.5: https://developer.apple.com/app-store/review/guidelines/
- Apple introductory offers and eligibility: https://developer.apple.com/help/app-store-connect/manage-subscriptions/set-up-introductory-offers-for-auto-renewable-subscriptions
- Google Play store listing limits: https://support.google.com/googleplay/android-developer/answer/9859152
- App Store subtitle limit of 30 characters (secondary): https://appdrift.co/blog/app-store-character-limits-complete-reference
- Reddit self-promotion norms (secondary, vendor blog): https://redship.io/blog/reddit-self-promotion-rules
- *Lemmon v. Snap* (9th Cir. 2021): https://www.abajournal.com/news/article/snapchat-can-be-sued-for-speed-filter-used-by-youths-before-fatal-accident-9th-circuit-rules ; https://jolt.law.harvard.edu/digest/lemmon-v-snap-inc-ninth-circuit-chips-away-at-tech-companies-section-230-immunity

**Unverified or not found:** a driving-app-specific retention benchmark, Canadian prices for SpeedRank and Enroad, Health & Fitness median prices from RevenueCat (the charts were not readable), Duolingo Super official pricing (no official price list; Duolingo tests prices), and the current self-promotion rules of specific car subreddits.
