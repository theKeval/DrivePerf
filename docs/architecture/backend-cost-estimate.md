# Backend monthly cost estimate at 1k and 5k DAU (DRIVE-76)

**Status:** estimate, US dollars per month, month 12 of steady use. It restates [ADR-004](adr/ADR-004-backend-v1-and-migration-seam.md) and [data-model.md §7](data-model.md) (same assumptions) as one table that includes the website, R2 and the fixed items, and adds what each plan phase costs. Prices are those verified on 6 Nov 2026 in those documents; **re-check the vendor pages on the day Keval upgrades to Blaze**.

## 1. What each phase costs

| Phase | Plan | What runs | Cost |
|---|---|---|---|
| Now to Phase 5 | **Spark** | Auth (50K MAU free), Firestore (waitlist, account roots), Hosting, App Check, Analytics, Remote Config | **US$0** (nothing is billable on Spark; a quota overrun stops the feature instead of charging) |
| Phase 6 start | **Blaze**, budget alerts at US$5 / 25 / 50 | + Cloud Run `api`, Cloud Scheduler (2 of 3 free jobs), Secret Manager, Artifact Registry, Cloudflare R2 | the tables below. A budget alert **does not cap spend**; the real limits are Cloud Run `max-instances 3`, App Check and the Pro-only upload check |
| Cloudflare | Free account with a card on file | R2 free tier: 10 GB-month, 1M writes, 10M reads | US$0 until 10 GB is stored |

Spark's tightest limit is Hosting transfer (360 MB/day): a launch-day spike on the website can exceed it, which is one more reason to be on Blaze before launch.

## 2. Assumptions (unchanged from ADR-004 and data-model §7.1)

MAU = 2.5 × DAU; 40 % of MAU signed in; **25 % of DAU back up** (Pro, Preview or Ride-along); 2 trips per backed-up user per day of about 25 minutes; only the R1 file is uploaded (about **0.25 MB per trip**, 0.6 MB per driving hour; R0 never leaves the phone); about 11 KB of Firestore data and 3 to 4 writes per trip; 8 API calls per day per backed-up user and 1 per other signed-in user; website 500 and 1,500 visits a day at 0.8 MB; Cloud Run and Firestore in `us-central1`; stock only grows (no churn purges), so month 12 is a ceiling for this load.

## 3. Estimate

| Line item | **1k DAU** | **5k DAU** | How it is computed |
|---|---|---|---|
| Users backing up / trips uploaded per month | 250 / 15,000 | 1,250 / 75,000 | 25 % of DAU × 2 trips × 30 days |
| **R2 storage** | 45 GB → **0.53** | 225 GB → **3.23** | trips/month × 0.25 MB × 12 months; (stock − 10 GB free) × US$0.015 |
| R2 operations and egress | 0 | 0 | about 3 requests per trip (prepare HEAD, PUT, commit HEAD): 45K and 225K a month, far inside 1M writes and 10M reads; egress is free |
| **Firestore** operations | 0 (inside 20K writes and 50K reads a day) | 0 | 1.4K and 7.1K writes a day |
| **Firestore** storage and PITR | 1.8 GiB → **0.23** | 9.2 GiB → **1.43** | storage beyond 1 GiB at about US$0.15/GiB, plus PITR overhead |
| **Cloud Run** API | 0 (65K requests) | 0 (320K requests) | inside 2M requests, 180K vCPU-s, 360K GiB-s |
| Cloud Scheduler | 0 | 0 | 2 jobs, 3 free |
| Auth, Analytics, App Check, Remote Config | 0 | 0 | 50K MAU free; 100K Remote Config fetches a day free (12 h minimum fetch interval) |
| **Hosting transfer** (website and API proxy) | 0.20 | 3.80 | beyond 360 MB/day at US$0.15/GB; the website is the largest line |
| Artifact Registry, Secret Manager, Logging | 0.50 | 0.50 | one image, 4 secrets, default log retention |
| Sentry (EU), RevenueCat, transactional email | not included | not included | Sentry Developer plan is free at launch; RevenueCat is free under US$2,500 monthly revenue; the email provider is chosen in Phase 6 |
| **Total** | **≈ US$1.5 to 2** | **≈ US$9** | data and API alone: ≈ US$1 and ≈ US$4 to 6 |

### Sensitivity (same method)

| Change | 1k DAU | 5k DAU |
|---|---|---|
| 100 % of DAU back up instead of 25 % | ≈ US$5 | ≈ US$27 |
| R1 at 50 Hz (file size doubles, A12 decides) | R2 line ×2: + 0.5 | R2 line ×2: + 3.2 |
| Backup users take 4 trips a day instead of 2 | R2 and Firestore lines ×2: ≈ + 1 | ≈ + 5 |
| One Firestore transaction retry storm / abuse | bounded by `max-instances 3` and App Check | same |

**Quota watch-points:** Play Integrity Standard allows 10,000 verdicts a day by default, so ask for an increase before about 10k Android DAU. If R2 passes about US$40 a month, compare Backblaze B2 (ADR-010 T2).

## 4. Why this stays small, and what would change it

Cost follows stored R1 volume (about 180 MB per backed-up user per year), not request volume. Levers if it ever matters: zstd (`formatVersion 2`, about 25 % smaller), moving R1 older than 12 months to a colder tier, and the churn purges. The VPS comparison and the triggers for leaving Firebase are in [ADR-010](adr/ADR-010-firebase-now-vs-vps.md): below about 20k DAU Firebase is cheaper than a VPS once about 6 to 10 hours a month of operations time are counted.
