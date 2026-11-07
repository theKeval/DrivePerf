# ADR-010: Firebase now vs a VPS from day one

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** [ADR-004](ADR-004-backend-v1-and-migration-seam.md) (API seam, portability rules, Firebase cost model), [ADR-003](ADR-003-local-storage-trip-files-retention.md) (retention), [data-model.md](../data-model.md) (A11: Firestore and Postgres schemas, sync, migration outline)

## Context and problem statement

Keval's plan: start on a new Firebase project (Spark, then Blaze when needed); after launch, once user numbers justify it, move **the entire server side** to a self-managed VPS such as Hostinger KVM 2. His question: *"If you think we should use the VPS from the start, tell me and I'll buy it; but if we can do something migration-feasible we'd prefer that."*

Hostinger KVM 2 today (6 Nov 2026, hostinger.com): 2 vCPU, 8 GB RAM, 100 GB NVMe, 8 TB bandwidth, **US$8.79/mo on a 24-month prepay (US$210.96 up front), renewing at US$14.99/mo**; weekly backups and Wanguard DDoS filtering included; 10 data centres (Boston named; no Canadian location confirmed). KVM 4 (4 vCPU, 16 GB, 200 GB): US$12.99/mo intro, US$28.99 renewal.

## Decision drivers

Launch in January 2027 with one developer · ops burden and security risk · cost at 1k / 5k / 20k DAU · migration feasibility without a client rewrite · privacy and compliance obligations (PIPEDA, Law 25, US state laws).

## Considered options

1. **Firebase now, behind our own API; migrate to a VPS when triggers fire** (ADR-004 seam).
2. **VPS from day one** (Postgres + S3-compatible storage + Ktor API on Hostinger), Firebase only for Analytics/Crashlytics/Remote Config/App Check.
3. **Hybrid from day one:** Firebase for everything except trip files on Cloudflare R2 or Backblaze B2.

## Decision outcome

**Recommendation: option 1. Do not buy the VPS now.** At launch-scale traffic Firebase costs ≈ US$2–10/month and almost no operations time, while a VPS costs about the same or more in money and 6–10 hours a month of patching, backups, monitoring and on-call — time that should go into capture reliability and the January launch. Because clients only know `https://api.driveperf.com/v1` (ADR-004), moving later is a **server-side project of about 2–4 weeks** with no app release, so waiting costs us nothing in lock-in.

What would change this recommendation: v1 needing SQL across users, long-running jobs or stateful connections (it does not), or a Firebase limit blocking the launch (none found).

### Cost model at month 12 (US$/month; [Estimate], same usage assumptions as ADR-004)

| | 1k DAU | 5k DAU | 20k DAU |
|---|---|---|---|
| **Firebase (option 1)** — see ADR-004 breakdown (≈ US$16 of the 20k figure is website traffic) | **≈ 2** | **≈ 10** | **≈ 45–55** |
| VPS server | KVM 2: 8.79 intro / 14.99 renewal | KVM 2: 8.79 / 14.99 | KVM 4 for headroom: 12.99 / 28.99 |
| Object storage for R1 trip files (Backblaze B2 ≈ US$6.95/TB-mo; 45 / 225 / 900 GB) | ≈ 0.3 | ≈ 1.6 | ≈ 6.3 |
| Off-box DB backups, uptime monitor (free tiers), domain TLS (Caddy) | ≈ 0.5 | ≈ 0.5 | ≈ 1 |
| Firebase pieces kept (Analytics, Crashlytics, Remote Config, App Check, Auth ≤ 50K MAU) | 0 | 0 | 0 |
| **VPS total (option 2)** | **≈ 10–16** | **≈ 11–17** | **≈ 20–36** |
| Ops time, Firebase vs VPS | ~1 h vs 6–10 h | ~1 h vs 6–10 h | ~2 h vs 8–12 h |
| One-off exit egress from Cloud Storage if migrating at this size (≈ US$0.12/GB after 100 GB/month free) | ≈ 0 | ≈ 15 | ≈ 95 |

Reading: a VPS is **more expensive** at 1k–5k DAU. At 20k DAU it saves ≈ US$10–30/month (mostly website traffic, which can move on its own), far less than the value of the extra 6–10 hours. Trip-file storage (retained until the user deletes it) is the line that grows without bound; it can also be moved on its own (trigger T2 below).

### Ops burden for a solo, AI-assisted developer

| Concern | Firebase + Cloud Run (managed) | Self-managed VPS | AI assistance helps by… |
|---|---|---|---|
| OS and runtime patching | none | `unattended-upgrades`, monthly kernel reboots, Docker image and Postgres minor updates | writing Ansible/cloud-init and update scripts; **you still own the reboot window** |
| Backups and restore | Firestore PITR/exports and Storage durability are Google's | WAL-G/pgBackRest to off-box storage, retention policy, **monthly restore drill** | scripting drills; cannot make an untested backup safe |
| TLS | automatic (Hosting) | Caddy automatic certificates (easy) | — |
| Monitoring and alerting | Cloud Monitoring, Crashlytics, budget alerts | off-box uptime checks, disk/CPU/Postgres alerts, log shipping, alert routing to your phone | dashboards as code |
| Auth security | Google-run Auth, token signing keys, abuse protection | if self-hosted: IdP CVEs, key rotation, brute-force and email-link abuse | keep Firebase Auth even after migration (recommended) |
| Uptime | multi-zone, no single box | **one VPS is a single point of failure**; host maintenance and disk failure are yours to recover | runbooks; a warm standby doubles cost |
| Abuse/DDoS | Google front end, App Check, `max-instances` | Wanguard filtering, firewall, rate limits in Caddy/API | — |
| Compliance evidence | Google's SOC 2/ISO reports cover the platform | you document hardening, access and incident response yourself | drafting documents |
| Incident response | rare, platform-level | you are on call for every outage, including holidays and Wrapped season | — |

### Triggers to start the migration project

Review quarterly (first review at the v1.1 release). Start when **any one** holds for **two consecutive months**:

- **T1 Cost:** the Firebase/GCP invoice exceeds **US$150/month**, or the 6-month forecast exceeds **US$250/month**, **and** the modelled VPS total (including object storage and 8 h/month of ops at a rate Keval sets) is ≥ 40 % cheaper.
- **T2 Trip-file storage (partial move first):** the Cloud Storage line exceeds **US$40/month** or holds more than **1.5 TB**. Move only the blob backend to S3-compatible storage (B2 or R2) by swapping the server's `BlobSigner` adapter; nothing else changes. Doing this early keeps the one-off egress bill small.
- **T3 Scale:** sustained **> 25k DAU**; Firestore writes above **5×** the free daily quota; or Cloud Run needing `min-instances > 0` to meet latency targets.
- **T4 Features:** a planned feature needs cross-user SQL aggregates (for example v1.1 club recaps), a web dashboard with complex queries, server-side spatial work (PostGIS route matching), jobs longer than 60 minutes, or WebSockets/live features.
- **T5 Compliance or contract:** counsel, a Law 25 PIA or a partner contract requires a processor other than Google, or Canadian residency that GCP's Montréal/Toronto regions cannot satisfy. (Residency alone is **not** a VPS trigger.)
- **T6 Vendor risk:** a Firebase price or policy change that at least doubles projected cost, or deprecates a service we rely on.

**Preconditions before cut-over (all required):** Postgres and S3 adapters pass the same contract suite as the Firestore adapters; a full rehearsal including rollback on a staging project and staging VPS; off-box backups with a successful restore drill; monitoring and paging live; privacy policy and PIA updated for the new processors (Hostinger, object-storage provider) and published before the switch; Keval available for two weeks; not in December (Wrapped season) and not within four weeks of a major app release.

### Target VPS architecture (when triggered)

Ubuntu LTS, SSH keys only, `ufw`, `fail2ban`, unattended security upgrades; Docker Compose with **Caddy** (TLS, reverse proxy, rate limits), the **same `backend/api` image** that runs on Cloud Run (configured with Postgres and S3 adapters), **Postgres** (current major; native `uuid`, `timestamptz`, `jsonb`); trip files on **external S3-compatible storage** (B2 or R2), not a self-hosted store on the 100 GB local disk, which the 5k-DAU stock outgrows in about five months (and MinIO's community edition stopped publishing binaries in Dec 2025, data-model.md §3.7); scheduled jobs as systemd timers calling the same `/v1/jobs/*` endpoints; WAL-G to off-box storage plus Hostinger weekly backups; external uptime checks. **Firebase Auth, Analytics, Crashlytics, Remote Config, App Check and FCM stay** (free, device-side or identity-critical); moving Auth is a separate, optional step (M6).

### Migration runbook outline (zero-downtime)

- **M0 Prepare (1–2 weeks):** write Postgres adapters (`UserStore`, `SyncStore`, `EntitlementStore`) and the S3 `BlobSigner`; run the contract suite on both; provision the VPS from code; lower the `api.driveperf.com` DNS TTL to 60 s 48 h ahead; rehearse M1–M5 on staging.
- **M1 Dual-write (Firebase primary):** the Cloud Run API writes Firestore in the request and appends each change to an outbox; a relay applies it to Postgres idempotently by `(id, rev)`. New trip files are copied to S3 after commit, and each `blobs` record stores its backend.
- **M2 Backfill:** page through Firestore by ID (or a managed export), transform with the A11 mapping, upsert into Postgres only where the incoming `rev` is newer (re-runnable). Copy existing objects GCS → S3 with checksum verification (rclone).
- **M3 Verify:** per-user counts and a hash over sorted `(id, rev)` pairs must match; shadow reads compare both stores for 7 days with zero unexplained differences.
- **M4 Cut-over (canary, no DNS change yet):** deploy a Cloud Run revision in *proxy mode* that forwards to the VPS origin; shift traffic 5 % → 25 % → 100 % with Cloud Run traffic splitting. The VPS is now primary and reverse-writes changes to Firestore through its own outbox, keeping Firestore warm for rollback. After 7 stable days, point `api.driveperf.com` DNS at the VPS (Caddy certificate pre-issued with a DNS-01 challenge); stale DNS answers still reach the proxy, so nothing breaks.
- **M5 Rollback (any time in the 30-day window):** shift Cloud Run traffic back to the native revision (seconds) or restore DNS; Firestore is current thanks to the reverse writes; blob records know where each object lives.
- **M6 Auth (optional, separate, last):** recommended to keep Firebase Auth. If moving: stand up an OIDC IdP federating Apple and Google with the **same** Apple Services ID/Team and Google OAuth clients so provider subjects match; import `firebase auth:export` users into it, mapping `(issuer, subject)` to existing account IDs (no password hashes exist: sign-in is Apple, Google and email link only); the API trusts both issuers; ship an app version whose `AuthGateway` does a one-time silent exchange of a Firebase ID token for an IdP session; keep the Firebase issuer trusted until `min_supported_build` retires old versions (≥ 6 months).
- **M7 Everything else:** RevenueCat webhook URL is unchanged (same domain); Cloud Scheduler → systemd timers; the static website can move to Caddy by changing two A records, with Firebase Hosting kept as a hot standby.
- **M8 Decommission (after 30 stable days):** stop reverse writes, run a final diff, delete the Firestore data and Cloud Storage objects (privacy: no stray copies), keep the Firebase project for the SDK-direct services, and record the change in `decisions.md`.

### Consequences

- Good: no ops work before launch; launch-scale cost near zero; a clear, measurable point at which to move.
- Good: the seam (ADR-004) keeps the move server-only and reversible.
- Bad: the API layer is extra code we would not need if we stayed on Firebase forever; it is also what makes the VPS option cheap.
- Bad: Cloud Storage exit egress grows with time; trigger T2 limits it.

### Confirmation

A quarterly cost/trigger review is added to the operations checklist (A17); the Firestore and Postgres adapter contract suite exists from Phase 6, so the Postgres adapter can be written and tested at any time.

## Pros and cons of the other options

- **VPS from day one:** predictable bill and full control; costs more than Firebase until well past 5k DAU, adds 6–10 h/month of ops and a single point of failure during the launch window, and still needs Firebase for analytics, crashes, config and App Check.
- **Hybrid with R2/B2 for trip files from day one:** removes the future blob migration and its egress bill (R2 has no egress fees); adds a second vendor, account and privacy-policy processor before launch, against the "all on Firebase for now" direction. Kept as an open question for Keval; trigger T2 achieves most of the benefit later.

## More information

Sources (accessed 6 Nov 2026): Hostinger VPS plans and prices (https://www.hostinger.com/vps-hosting); Firebase pricing (https://firebase.google.com/pricing); Cloud Storage pricing and North America internet egress ≈ US$0.12/GB for the first TB (https://cloud.google.com/storage/pricing; secondary 2026 sources); Backblaze B2 ≈ US$6.95/TB-month and Cloudflare R2 US$0.015/GB-month with free egress (secondary 2026 comparisons, e.g. https://mecanik.dev/en/posts/cloudflare-r2-pricing-explained-real-costs-vs-s3-and-backblaze/ — confirm on vendor pages before buying); Firebase Auth export/import (https://firebase.google.com/docs/cli/auth); Supabase guide to migrating from Firebase Auth (https://supabase.com/kb/guides/migrate-from-firebase-auth/).
