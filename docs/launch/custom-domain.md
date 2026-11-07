# driveperf.com and www on Firebase Hosting (DRIVE-79)

**Who does what:** Keval edits the DNS at Namecheap and clicks through the Firebase console. The agent never changes DNS and never asks for Namecheap credentials. The agent fills in the values Keval copies from the console, then verifies propagation, HTTPS, the redirect and HSTS. **Related:** [ADR-009](../architecture/adr/ADR-009-web-stack.md), [firebase-setup.md](firebase-setup.md), [email.md](email.md).

**Redirect choice (ADR-009): `www.driveperf.com` → 301 → `https://driveperf.com`** (the apex is canonical; every page's `<link rel="canonical">` and the sitemap use the apex). Firebase sets up this redirect in the **console** when `www` is added. `firebase.json` redirects match paths only, not host names, so ADR-009's "redirect in `firebase.json`" is done in the console instead.

## 0. Before anything is removed: the current Namecheap records

Namecheap → **Domain List** → driveperf.com → **Manage** → **Advanced DNS**. Send the agent a screenshot or a list of every record under *Host Records* and *Mail Settings*.

What public DNS shows today (agent, `dig`, 7 Nov 2026). It can't see the URL-redirect records or any unused ones, so the screenshot is still needed:

| Type | Host | Value | What it is | During this runbook |
|---|---|---|---|---|
| A | `@` | `162.255.119.193` | Namecheap URL forward (`http://driveperf.com` → 302 → `http://www.driveperf.com/`) | **remove** (with its URL Redirect record) |
| CNAME | `www` | `parkingpage.namecheap.com.` | parking page | **remove** |
| MX | `@` | `eforward1…5.registrar-servers.com` (10/10/10/15/20) | Namecheap email forwarding | **keep** (replaced later by Zoho's MX in [email.md](email.md)) |
| TXT | `@` | `v=spf1 include:spf.efwd.registrar-servers.com ~all` | SPF for Namecheap forwarding | **keep** (replaced by Zoho's SPF in [email.md](email.md)) |
| NS | | `dns1/dns2.registrar-servers.com` | Namecheap BasicDNS | keep (DNS stays at Namecheap) |
| — | `_dmarc`, DKIM, CAA, AAAA | none | | |

Keval's screenshot of *Advanced DNS*: _(to add)_

**Keep every MX, SPF (`v=spf1 …` TXT), DKIM (`…._domainkey` TXT) and DMARC (`_dmarc` TXT) record.** They carry email ([email.md](email.md)). Only the parking records go.

## 1. Add the domains in Firebase (console)

Firebase console → project **DrivePerf** (`driveperf-prod`) → *Build* → **Hosting** → **Add custom domain**:

1. Domain **`driveperf.com`**. Leave "Redirect … to an existing website" **unticked** → Continue.
2. Firebase shows the records to add. Copy **every** value exactly as shown into the table in §2 and send it to the agent. It is usually one **TXT** at `@` for ownership and one or more **A** records at `@`, and sometimes an `_acme-challenge` TXT or **AAAA** records. Firebase may also say whether existing records conflict.
3. **Add custom domain** again: domain **`www.driveperf.com`**, and **tick "Redirect www.driveperf.com to an existing website"** → choose **`driveperf.com`**. Copy the records it shows: a **CNAME** for `www`, or A records, plus any TXT.

## 2. Namecheap changes (Advanced DNS)

Do the removals and additions in one sitting. Values in `«…»` come from the console in §1; don't guess them.

**Remove (parking page):**

| Type | Host | Value (typical Namecheap default) |
|---|---|---|
| CNAME | `www` | `parkingpage.namecheap.com.` |
| URL Redirect | `@` | `http://www.driveperf.com/` |
| any other A / CNAME / URL Redirect at `@` or `www` that §0 shows and Firebase doesn't list | | |

**Add (Firebase Hosting):**

| Type | Host | Value | TTL |
|---|---|---|---|
| TXT | `@` | `«hosting-site=… value from the console»` (**keep permanently**: Hosting needs it to renew certificates) | Automatic |
| A | `@` | `«IP 1 from the console»` | Automatic |
| A | `@` | `«IP 2, if shown»` | Automatic |
| AAAA | `@` | `«only if the console shows one»` | Automatic |
| TXT | `_acme-challenge` | `«only if the console shows one»` | Automatic |
| CNAME | `www` | `«target from the console for www»` | Automatic |

Namecheap notes: write the host as `@` for the apex, and as `www` or `_acme-challenge` (without `.driveperf.com`) for subdomains. Namecheap allows several TXT records at `@`, so the Firebase TXT sits alongside the SPF TXT from [email.md](email.md). Don't merge them; only SPF must stay a single record. No CAA records are needed unless the console asks for them.

## 3. Verify (agent)

Propagation can take up to 24 h, but usually minutes. The certificate is provisioned automatically after the records resolve (usually within a few hours, up to 24 h).

```bash
dig +short driveperf.com A                 # the Firebase IPs from §1 and nothing else (no parking IP)
dig +short driveperf.com TXT               # the hosting-site TXT (plus the SPF/verification TXTs from email.md)
dig +short www.driveperf.com               # the CNAME target / IPs from §1
dig +short driveperf.com MX                # unchanged by this runbook
curl -sI http://driveperf.com | head -3              # 301 → https://driveperf.com/
curl -sI https://www.driveperf.com | head -3         # 301 → https://driveperf.com/
curl -sI https://driveperf.com | grep -i -E 'HTTP/|strict-transport|content-security'   # 200, HSTS max-age=31536000; includeSubDomains
openssl s_client -connect driveperf.com:443 -servername driveperf.com </dev/null 2>/dev/null | openssl x509 -noout -issuer -subject -dates
```

The console status for both domains must read **Connected**. Results:

| Check | Result | When |
|---|---|---|
| `driveperf.com` A / TXT | | |
| `www` → 301 → apex | | |
| HTTPS certificate (issuer, expiry) | | |
| HSTS header | | |
| Console status | | |

HSTS is sent without `preload` for now. Adding the domain to the browser preload list is a separate, hard-to-undo decision for after launch.
