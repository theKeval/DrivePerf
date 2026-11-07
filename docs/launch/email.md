# Email for privacy@, support@ and hello@driveperf.com (DRIVE-79)

The site publishes three addresses: **privacy@** (privacy policy, account deletion, email preferences), **support@** (support page, footer) and **hello@** (press kit, no-JS waitlist fallback). Firebase has no mailboxes. The recommendation is in [decisions.md](../product/decisions.md) (6 Nov 2026, "Email"). **Keval creates the account and does every console step**; the agent never handles credentials. The DNS records live at Namecheap, next to the Firebase Hosting A and TXT records ([custom-domain.md](custom-domain.md)).

## Choose one (Keval)

| Option | What | Cost | Send as @driveperf.com | Notes |
|---|---|---|---|---|
| **A** (recommended if the existing Zoho organisation is on a paid plan) | Add `driveperf.com` as a domain in the existing Zoho Mail organisation; privacy@, support@ and hello@ become **aliases** of the existing mailbox | none extra | yes | One inbox for everything |
| **B** (free) | New **Zoho Mail Forever Free** organisation for driveperf.com: one user (e.g. `keval@driveperf.com`) plus the three addresses as aliases (or **groups** if the free plan doesn't allow aliases) | $0 | yes | Up to 5 users, 5 GB each, web and mobile apps only, no IMAP |
| **C** (minimum) | Namecheap free **email forwarding** to Keval's inbox | $0 | **no** (receive only) | Can't reply from @driveperf.com, which is weak for privacy requests |

**Chosen: option A** (Keval, 7 Nov 2026): add driveperf.com to the existing paid Zoho Mail organisation, with privacy@, support@ and hello@ as aliases of Keval's mailbox.

Steps for A (Keval, in the Zoho Mail Admin Console, mailadmin.zoho.com): **Domains → Add Domain** → `driveperf.com` → verify with the TXT method (copy the TXT into the table below) → after verification, **Users → (your user) → Mail aliases → Add** `privacy@driveperf.com`, `support@driveperf.com` and `hello@driveperf.com` → **Domains → driveperf.com → Email configuration**, which shows the MX, SPF and DKIM values for your data centre (generate the DKIM key there with the selector Zoho suggests).

## Records to add at Namecheap (options A and B)

Zoho shows the exact values during domain setup (*Zoho Mail Admin Console → Domains → driveperf.com → DNS Mapping*). Use **Zoho's values for the account's data centre**. A `.com` account uses `mx.zoho.com`; other regions use, for example, `mx.zoho.eu` or `mx.zohocloud.ca`. Copy them into this table and send it to the agent before saving.

| Type | Host | Value | Priority | Purpose |
|---|---|---|---|---|
| TXT | `@` | `«zoho-verification=… from Zoho»` | — | domain ownership |
| MX | `@` | `«mx.zoho.com»` | 10 | mail |
| MX | `@` | `«mx2.zoho.com»` | 20 | mail |
| MX | `@` | `«mx3.zoho.com»` | 50 | mail |
| TXT | `@` | `v=spf1 include:zoho.com ~all` (use Zoho's include for the data centre) | — | SPF |
| TXT | `«zmail._domainkey»` | `«v=DKIM1; k=rsa; p=… from Zoho»` | — | DKIM |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:privacy@driveperf.com` | — | DMARC (monitor first; tighten to `quarantine` after a few clean weeks) |

Rules:
- **Today the domain uses Namecheap email forwarding** (MX `eforward1…5.registrar-servers.com`, SPF `v=spf1 include:spf.efwd.registrar-servers.com ~all`; seen with `dig` on 7 Nov 2026). For option A, switching **Mail Settings** from *Email Forwarding* to **Custom MX** removes the forwarding MX records. **Replace** the old SPF TXT with Zoho's, rather than adding a second one. Check first that no forwarding rule in Namecheap is still needed.
- **Exactly one SPF record.** If §0 of [custom-domain.md](custom-domain.md) shows an existing `v=spf1` TXT, merge the includes into it (for example `v=spf1 include:zoho.com include:… ~all`). Never add a second one.
- In Namecheap, set **Mail Settings → Custom MX** first; the MX rows then appear under Host Records.
- These records coexist with the Firebase Hosting **A** and **TXT** (`hosting-site=…`) records at `@`.

Option C instead: Namecheap → Domain → **Mail Settings → Email Forwarding**, then add `privacy`, `support` and `hello` → Keval's inbox. Namecheap adds its own MX and SPF records; don't add the Zoho rows.

## Verify (agent, then Keval)

```bash
dig +short MX driveperf.com        # the three Zoho MX hosts with priorities 10/20/50 (or Namecheap's for option C)
dig +short TXT driveperf.com       # one v=spf1 record, the Zoho verification TXT and the Firebase hosting-site TXT
dig +short TXT _dmarc.driveperf.com
dig +short TXT zmail._domainkey.driveperf.com   # selector as Zoho named it
```

Before launch, Keval sends a test email from an outside account to **each** of privacy@, support@ and hello@, and **replies from one of them** (options A and B). Check that the reply arrives with SPF/DKIM/DMARC "pass" in the recipient's "Show original" view.

| Check | Result | Date |
|---|---|---|
| MX / SPF / DKIM / DMARC resolve | | |
| privacy@ received | | |
| support@ received | | |
| hello@ received | | |
| Reply sent from @driveperf.com, authentication pass | | |
