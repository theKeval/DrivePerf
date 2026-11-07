// The website's only backend seam (ADR-004/ADR-010). Pages call these functions and nothing else; swapping
// Firestore for the API (POST https://api.driveperf.com/v1/waitlist) or a VPS endpoint changes this file only.
import { joinWaitlistFirestore, warmUp } from './firestore-waitlist';
import type { JoinResult, WaitlistInput } from './waitlist';

export * from './waitlist';

export const backend = {
  /** Resolves 'created' or 'duplicate' (idempotent); rejects with BackendUnavailableError on any failure. */
  joinWaitlist: (input: WaitlistInput): Promise<JoinResult> => joinWaitlistFirestore(input),
  /** Optional: begin loading the client SDK ahead of submit. */
  warmUp: () => { void warmUp(); },
};
