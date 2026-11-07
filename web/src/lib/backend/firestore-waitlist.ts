// Data adapter: waitlist on Firestore (Spark plan, no Cloud Functions). Firestore Lite Web SDK, loaded lazily.
// Rules (backend/firebase/firestore.rules) allow create only; a repeat sign-up hits the same document ID, which the
// rules see as an update and deny, so PERMISSION_DENIED after client-side validation means "already on the list".
import { SITE } from '../../config/site';
import { BackendUnavailableError, buildWaitlistRecord, waitlistDocId, type JoinResult, type WaitlistInput } from './waitlist';

type Lite = typeof import('firebase/firestore/lite');
let ready: Promise<{ lite: Lite; db: import('firebase/firestore/lite').Firestore }> | null = null;

/** Starts loading the SDK (call on first interaction with the form so submit is fast). */
export function warmUp() {
  if (!SITE.firebase.apiKey || !SITE.firebase.appId) return null;
  ready ??= (async () => {
    const [{ initializeApp }, lite] = await Promise.all([import('firebase/app'), import('firebase/firestore/lite')]);
    const app = initializeApp(SITE.firebase, 'driveperf-web');
    const db = lite.getFirestore(app);
    if (SITE.firestoreEmulator) lite.connectFirestoreEmulator(db, SITE.firestoreEmulator.host, SITE.firestoreEmulator.port);
    return { lite, db };
  })();
  ready.catch(() => { ready = null; });
  return ready;
}

export async function joinWaitlistFirestore(input: WaitlistInput): Promise<JoinResult> {
  const r = warmUp();
  if (!r) throw new BackendUnavailableError('Firebase web app is not configured for this build');
  let lite: Lite, db: import('firebase/firestore/lite').Firestore;
  try { ({ lite, db } = await r); } catch (e) { throw new BackendUnavailableError('Could not load the Firestore SDK', e); }
  const id = await waitlistDocId(input.email);
  try {
    await lite.setDoc(lite.doc(db, 'waitlist', id), {
      ...buildWaitlistRecord(input),
      createdAt: lite.serverTimestamp(),
      consentAt: lite.serverTimestamp(),
    });
    return 'created';
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === 'permission-denied') return 'duplicate';
    throw new BackendUnavailableError(`Waitlist write failed (${code ?? 'unknown'})`, e);
  }
}
