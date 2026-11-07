// Site configuration: the one place for environment-specific, public values.
// Nothing here is secret: a Firebase web config identifies the project to the browser (access is enforced by
// Firestore rules), and a Cloudflare Web Analytics token is printed in every page that loads the beacon.
//
// Environment is chosen at build time with PUBLIC_DP_ENV:
//   local (default)  `npm run dev` / emulator tests: Firestore emulator, project "demo-driveperf", no analytics
//   dev              PR preview channels on driveperf-dev (ADR-008): dev Firestore, no analytics
//   prod             driveperf.com on driveperf-prod: prod Firestore, Cloudflare Web Analytics beacon

export type SiteEnv = 'local' | 'dev' | 'prod';

const env = (import.meta.env.PUBLIC_DP_ENV ?? 'local') as SiteEnv;
if (!['local', 'dev', 'prod'].includes(env)) throw new Error(`PUBLIC_DP_ENV must be local, dev or prod (got "${env}")`);

export interface FirebaseWebConfig { apiKey: string; authDomain: string; projectId: string; appId: string }

// Registered web apps ("DrivePerf Web") in each Firebase project. Values from Firebase console →
// Project settings → Your apps. Empty values mean the app is not registered yet: the waitlist then
// shows its network-error state (never a fake success).
const FIREBASE: Record<SiteEnv, FirebaseWebConfig> = {
  local: { apiKey: 'demo-key', authDomain: 'demo-driveperf.firebaseapp.com', projectId: 'demo-driveperf', appId: 'demo-app' },
  dev: { apiKey: '', authDomain: 'driveperf-dev.firebaseapp.com', projectId: 'driveperf-dev', appId: '' },
  prod: { apiKey: '', authDomain: 'driveperf-prod.firebaseapp.com', projectId: 'driveperf-prod', appId: '' },
};

export const SITE = {
  env,
  origin: 'https://driveperf.com',
  name: 'DrivePerf',
  firebase: FIREBASE[env],
  /** Firestore emulator for local builds (`firebase emulators:start` in backend/firebase). */
  firestoreEmulator: env === 'local' ? { host: '127.0.0.1', port: 8080 } : null,
  /** Cloudflare Web Analytics (ADR-007, Gate 4): public beacon token; prod only, and never under GPC/DNT. */
  cloudflareBeaconToken: env === 'prod' ? '' : '',
  emails: { privacy: 'privacy@driveperf.com', support: 'support@driveperf.com', hello: 'hello@driveperf.com' },
  /** Waitlist consent text version (DRIVE-171 spec §4); bump when the consent copy changes. */
  waitlistConsentVersion: 'wl-consent-v1 (6 Nov 2026)',
} as const;
