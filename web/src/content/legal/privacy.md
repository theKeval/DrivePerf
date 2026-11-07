---
title: Privacy policy
metaTitle: Privacy policy · DrivePerf
description: "How DrivePerf handles your data: local-first, no account needed, never sold or shared with insurers, no cookies. Your rights in Canada, Quebec and the US."
ogTitle: DrivePerf privacy policy
ogDescription: Local-first. No account needed. Never sold, never shared with insurers.
lede: Your drives stay on your phone unless you turn on backup. Here is exactly what DrivePerf collects, when anything leaves your phone, and the rights you have.
version: "0.9"
date: 2026-11-07
dateLabel: 7 Nov 2026
dateLong: 7 November 2026
scope: Applies to the iPhone and Android apps and driveperf.com
banner: Draft for counsel review.
bannerText: This policy is a working draft written from the DrivePerf product requirements. It will be reviewed by a lawyer before launch, and items marked <span class="placeholder">like this</span> are still to be confirmed.
---

<h2 id="short">The short version</h2>
<div class="summary-cards">
  <div><span data-icon="phone"></span><p style="margin:0"><b>Local-first</b><span>Trips, routes, cars and scores are stored on your phone. Everything in Free works without an account.</span></p></div>
  <div><span data-icon="cloud"></span><p style="margin:0"><b>Backup is optional</b><span>Trip data leaves your phone only if you turn on Pro cloud backup, after you've seen what is uploaded and agreed.</span></p></div>
  <div><span data-icon="noentry"></span><p style="margin:0"><b>Never sold, never shared</b><span>We don't sell your data, share it for advertising, or give it to insurers. There are no ad or tracking SDKs.</span></p></div>
  <div><span data-icon="download"></span><p style="margin:0"><b>Yours to take or delete</b><span>Download all your data or delete anything, free, from the Privacy Centre in the app.</span></p></div>
</div>

<h2 id="who">Who we are</h2>

DrivePerf is an app for iPhone and Android that records your drives and helps you drive more smoothly. It is published as `com.driveperf.app` on the App Store and Google Play. It is made by Keval Langalia, an individual developer based in Ontario, Canada ("DrivePerf", "we", "us"). Keval is responsible for the personal information described here.

This policy covers the DrivePerf apps and driveperf.com. It is written to meet Canada's Personal Information Protection and Electronic Documents Act (PIPEDA), Quebec's Act respecting the protection of personal information in the private sector (Law 25), the California Online Privacy Protection Act (CalOPPA), and the comprehensive privacy laws of US states. Where these laws differ, we apply the stricter rule to everyone.

<h2 id="collect">What we collect, and when it leaves your phone</h2>

Most of what DrivePerf does happens on your phone. Processing on your device is not collection by us: we never see it unless you choose a feature that sends it.

<div class="table-scroll"><table>
  <thead><tr><th scope="col">Data</th><th scope="col">Where it lives</th><th scope="col">Leaves your phone only when</th></tr></thead>
  <tbody>
    <tr><td>Trips: route (precise location), time, speed, motion sensor readings, events, scores</td><td>Your phone</td><td>You turn on <strong>Pro cloud backup</strong> and agree on the consent screen</td></tr>
    <tr><td>Routes, cars, settings, privacy zones</td><td>Your phone</td><td>Cloud backup is on (privacy zones are included so a new phone keeps them)</td></tr>
    <tr><td>Email, name (optional), account ID</td><td>Our account service</td><td>You sign in with Apple, Google or an email link. Signing in is only needed for backup or a second device</td></tr>
    <tr><td>Purchase history</td><td>App Store or Google Play, and RevenueCat</td><td>You buy Pro or restore a purchase</td></tr>
    <tr><td>Usage statistics (for example, "a Story was opened")</td><td>Our analytics service, not linked to you</td><td>You say yes to usage statistics. Never coordinates, addresses, routes, car names, speeds or G-forces</td></tr>
    <tr><td>Crash reports and diagnostics</td><td>Our crash-reporting service, not linked to you</td><td>You say yes to crash reports. Never locations</td></tr>
    <tr><td>Advertising ID</td><td>—</td><td><strong>Never.</strong> We don't access it</td></tr>
  </tbody>
</table></div>

<h2 id="location">Location and motion data</h2>

DrivePerf uses your phone's location and motion sensors for one purpose: recording your drives.

- **Automatic trips are off until you turn them on.** When on, DrivePerf uses location "when the app is closed or not in use" to notice that a drive has started and record its route. On iPhone this needs "Always" location and Motion &amp; Fitness; on Android, "Allow all the time" location and Physical activity.
- **Manual trips** only need location while you use the app. Every Pro feature works in manual mode.
- Between drives, DrivePerf watches for movement using low-power signals from your phone and a geofence around where you parked. It does not track you continuously.
- Walks, runs, bike rides and transit are not recorded as drives. Rejected trip candidates are deleted within 24 hours.
- Share cards show the shape of a route with its start and end trimmed by a random 400–800 m, and never a map unless you choose the map style each time. Privacy zones remove more. A distinctive route can still be recognisable, so the Ring card, with no route at all, is one tap away.

<h2 id="use">How we use your data</h2>

- To record drives, score Smoothness against your own baseline, show your Story, Replay, routes, recaps and Wrapped. All of this is computed on your phone.
- To back up and restore your trips, if you turn on backup.
- To provide Pro, restore purchases and apply a Ride-along pass. A friend who uses your pass is reported to you only as "Your friend is set up", with nothing about their drives.
- With your permission, to understand how the app is used and fix crashes, in a form not linked to you.
- To email waitlist members about the beta and launch, only with their consent.

We will ask for separate consent before using your data for anything else, such as research.

<h2 id="never">What we never do</h2>

- **We don't sell your personal information**, and we don't "share" it for cross-context behavioural advertising or use it for targeted advertising.
- **We don't share your data with insurers**, and DrivePerf has no insurance features.
- We don't include advertising, attribution or data-broker SDKs, and we don't track you across other companies' apps or websites.
- We don't show your drives to anyone else. There are no feeds, friends lists or leaderboards.

<h2 id="providers">Service providers</h2>

We use a small number of providers that process data on our behalf, under contracts that limit their use of it to providing their service to us.

<div class="table-scroll"><table>
  <thead><tr><th scope="col">Provider</th><th scope="col">What for</th><th scope="col">When</th></tr></thead>
  <tbody>
    <tr><td>Google Firebase (Authentication, Cloud Firestore, Crashlytics, Analytics, Hosting)</td><td>Sign-in, cloud backup records, crash reports, usage statistics, serving this website, and storing the waitlist</td><td>Only for the features above that you use or allow; Hosting whenever you visit this website</td></tr>
    <tr><td>Cloudflare (R2 storage, Web Analytics)</td><td>Storing backed-up trip files; counting visits to this website without cookies</td><td>Cloud backup is on; you visit this website and your browser doesn't send Global Privacy Control or Do Not Track</td></tr>
    <tr><td>RevenueCat</td><td>Managing Pro subscriptions and Ride-along passes</td><td>When you buy, restore or redeem</td></tr>
    <tr><td>Apple App Store and Google Play</td><td>Payments. They process your payment details; we never see them</td><td>When you buy Pro</td></tr>
    <tr><td>Map providers: Apple MapKit on iPhone; <span class="placeholder">Google Maps SDK or MapLibre</span> on Android</td><td>Showing map tiles under your routes</td><td>When a map is on screen. Only tile requests are sent, never your trip</td></tr>
    <tr><td><span class="placeholder">[email provider]</span></td><td>Sending waitlist, beta and launch emails, and receiving mail at our @driveperf.com addresses</td><td>Waitlist members who agreed to emails; anyone who writes to us</td></tr>
  </tbody>
</table></div>

<h2 id="transfers">Where your data is stored</h2>

Data on your phone stays on your phone. Accounts, backup records and the waitlist are stored with Google Firebase in the **United States** (Cloud Firestore, region us-central1, Iowa), and backed-up trip files with Cloudflare R2 in **North America**, which means it is transferred outside Quebec and outside Canada. Before we launch cloud backup we complete a privacy impact assessment covering this transfer, as Quebec's Law 25 requires. Data stored in another country may be accessible to that country's authorities under its laws.

<h2 id="retention">How long we keep it</h2>

- **On your phone, on every plan:** until you delete it. You can delete a trip, trips older than 3, 6 or 12 months, or everything.
- **Pro cloud backup** (including during the Pro Preview or a Ride-along pass): until you delete it or delete your account. A trip you delete on your phone is deleted from backup at the next sync, within 24 hours.
- **When Pro ends:** backup stops and the cloud copy is kept for 30 days, with reminders when 30, 7 and 1 days are left, then deleted. During those 30 days you can still restore it to a new phone or download it (read-only, no new uploads). The data on your phone is untouched.
- **Your account:** Free users who sign in upload no trip data. The account record is kept until you delete your account, in the app or on our <a href="/delete-account">account deletion page</a>. Deletion is completed within 30 days, usually within 7.
- **Crash reports and usage statistics:** <span class="placeholder">[provider retention period]</span>, not linked to you.
- **Waitlist email:** until you unsubscribe, or <span class="placeholder">12 months after launch</span>, whichever comes first (proposed; to be confirmed by counsel).

<h2 id="rights">Your rights</h2>

Wherever you live, you can:

- **Access and take your data:** "Download all my data" in the Privacy Centre creates a ZIP in an open, documented format. It is free on every plan.
- **Correct it:** edit trips, cars and routes in the app, or ask us to correct account data.
- **Delete it:** in the app, or on the <a href="/delete-account">account deletion page</a> if you no longer have the app.
- **Withdraw consent** to backup, usage statistics, crash reports or emails at any time, without losing access to the rest of the app.
- **Opt out of sale, sharing and targeted advertising:** there is nothing to opt out of, because we don't do any of them. We still honour your request and Global Privacy Control.

### Canada and Quebec

Under PIPEDA you can access and correct your personal information and challenge our compliance. Under Quebec's Law 25 you also have rights to portability in a structured, commonly used format, to be informed of decisions based on automated processing (DrivePerf's scores and archetypes are computed on your phone and only describe your own driving), and to file a complaint with the Commission d'accès à l'information du Québec. You can complain to the Office of the Privacy Commissioner of Canada.

### United States

Depending on your state, you have the right to know, access, delete, correct and port your personal data, to limit the use of sensitive data such as precise geolocation, and to opt out of sale, sharing and targeted advertising. We treat precise geolocation as sensitive data in every state: automatic trips are opt-in, and location leaves your phone only with your consent to backup. We won't discriminate against you for using these rights. If your request is denied, you can appeal by replying to our response.

### How to make a request

Use the Privacy Centre in the app, or email [privacy@driveperf.com](mailto:privacy@driveperf.com). We may ask you to confirm your email address so we know the request is yours. We answer within 30 days in Canada and 45 days in the US.

<h2 id="gpc">Global Privacy Control and Do Not Track</h2>

driveperf.com honours Global Privacy Control and Do Not Track signals: when your browser sends either, the page-count script described below is not loaded at all, so not even a cookie-less page count is recorded. The apps never track you across other companies' apps or websites.

<h2 id="cookies">Cookies</h2>

driveperf.com sets **no cookies** and uses no browser storage for tracking. Page counts come from Cloudflare Web Analytics, which is cookie-less and aggregate. The only thing the site keeps in your browser is your Night or Day theme choice, in session storage for that tab; it is cleared when you close the tab. The waitlist's A/B test picks a headline at random on each visit without storing anything on your device. The waitlist form uses no captcha or any other check that sets cookies.

<h2 id="website">The website and waitlist</h2>

If you join the waitlist we collect your **email**, your **phone type** (iPhone or Android), your **country** (Canada, US or other), which headline you saw (the A/B variant), and a record of your consent: the time, the version of the wording you agreed to and the page. Nothing else. It is stored in Google Cloud Firestore in the United States (us-central1). We use it only to email you about the DrivePerf beta and launch, and keep it until you unsubscribe or <span class="placeholder">12 months after launch</span>. Every email says who it's from, includes our postal contact details and has a one-click unsubscribe, honoured immediately and always within 10 business days, as Canada's Anti-Spam Legislation (CASL) and CAN-SPAM require. To have your entry removed completely, email [privacy@driveperf.com](mailto:privacy@driveperf.com). Waitlist data is never combined with app data.

Like any website, our host (Firebase Hosting) receives your IP address and browser details to deliver the pages; we don't use them to identify you.

<h2 id="children">Age: 18 and over</h2>

DrivePerf is for drivers aged 18 and over and is not directed to children. If we learn that someone under 18 is using DrivePerf, we delete their account data and let them know.

<h2 id="security">Security</h2>

Data on your phone is protected by your phone's encryption. Data in transit uses TLS, and cloud data is encrypted at rest and protected by access rules so only you can read your backup. If a breach creates a real risk of significant harm, we will tell you and the regulators, as the law requires.

<h2 id="changes">Changes to this policy</h2>

Each version has a number and a date. When we make a material change we'll tell you in the app before it takes effect, and the app records which version you accepted. Previous versions are available on request.

<h2 id="contact">Contact and who is responsible</h2>

DrivePerf has no separate privacy office. The person responsible for privacy and personal information, including as the person in charge of the protection of personal information under Quebec's Law 25, is:

**Keval Langalia, responsible for privacy and personal information** · [privacy@driveperf.com](mailto:privacy@driveperf.com)

Write to that address for any question, request or complaint about your personal information. You can also contact the Office of the Privacy Commissioner of Canada or, in Quebec, the Commission d'accès à l'information.
