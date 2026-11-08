// Waitlist domain (F-WEB-02): entry shape, validation and the stored record. Pure: no Firebase, no DOM.
// The same constraints are enforced by backend/firebase/firestore.rules; the rules tests import
// buildWaitlistRecord() so the exact payload the site sends is proven to pass the rules.

export const PLATFORMS = ['ios', 'android', 'both'] as const;
export const COUNTRIES = ['CA', 'US', 'other'] as const;
export const VARIANTS = ['A', 'B'] as const;
export type Platform = (typeof PLATFORMS)[number];
export type Country = (typeof COUNTRIES)[number];
export type Variant = (typeof VARIANTS)[number];

export const EMAIL_MAX = 254;
/** Same pattern as the rules (and the prototype's EMAIL_RE): something@something.tld, no spaces. */
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export interface WaitlistInput {
  email: string;
  platform: string;
  country: string;
  consent: boolean;
  variant: string;
  sourcePage: string;
  consentTextVersion: string;
}

export type WaitlistField = 'email' | 'platform' | 'country' | 'consent';
export type WaitlistErrors = Partial<Record<WaitlistField, string>>;

/** Copy from docs/design/DRIVE-171/web/design-spec.md §4. */
export function validateWaitlist(i: Pick<WaitlistInput, 'email' | 'platform' | 'country' | 'consent'>): WaitlistErrors {
  const e: WaitlistErrors = {};
  const v = i.email.trim();
  if (!v) e.email = 'Enter your email address.';
  else if (!EMAIL_RE.test(v) || v.length > EMAIL_MAX) e.email = 'That email doesn’t look right. Check for a typo, like a missing “.com”.';
  if (!(PLATFORMS as readonly string[]).includes(i.platform)) e.platform = 'Choose iPhone or Android, so we invite you to the right beta.';
  if (!(COUNTRIES as readonly string[]).includes(i.country)) e.country = 'Choose your country.';
  if (i.consent !== true) e.consent = 'Tick the box so we can email you. That’s the only thing we use your email for.';
  return e;
}

export const normaliseEmail = (email: string) => email.trim().toLowerCase();

/** Document ID: SHA-256 (hex) of the normalised email, so a repeat sign-up targets the same document. */
export async function waitlistDocId(email: string): Promise<string> {
  const bytes = new TextEncoder().encode(normaliseEmail(email));
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Stored fields, minus the two server timestamps (createdAt, consentAt) the adapter adds. CASL consent record:
 *  consent + consentTextVersion + consentAt + sourcePage. */
export function buildWaitlistRecord(i: WaitlistInput) {
  return {
    email: normaliseEmail(i.email),
    platform: i.platform as Platform,
    country: i.country as Country,
    consent: true as const,
    consentTextVersion: i.consentTextVersion,
    sourcePage: i.sourcePage,
    variant: i.variant as Variant,
  };
}

export type JoinResult = 'created' | 'duplicate';
export class BackendUnavailableError extends Error {
  constructor(message: string, readonly cause?: unknown) { super(message); this.name = 'BackendUnavailableError'; }
}
