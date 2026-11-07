// Firestore rules tests (Emulator). Run: `npm test` in backend/firebase (starts the Firestore emulator).
// The valid payload comes from the website's own builder (web/src/lib/backend/waitlist.ts), so these tests prove
// that what driveperf.com sends is accepted, and that everything else is rejected.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, type Firestore } from 'firebase/firestore';
import { buildWaitlistRecord, waitlistDocId, type WaitlistInput } from '../../../web/src/lib/backend/waitlist';

let env: RulesTestEnvironment;
let db: Firestore;

const input = (over: Partial<WaitlistInput> = {}): WaitlistInput => ({
  email: 'Marcus.Chen@Example.com ',
  platform: 'ios',
  country: 'CA',
  consent: true,
  variant: 'A',
  sourcePage: 'S-WEB-01',
  consentTextVersion: 'wl-consent-v1 (6 Nov 2026)',
  ...over,
});
const record = (over: Partial<WaitlistInput> = {}) => ({ ...buildWaitlistRecord(input(over)), createdAt: serverTimestamp(), consentAt: serverTimestamp() });
const entryRef = async (email = input().email) => doc(db, 'waitlist', await waitlistDocId(email));

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-driveperf',
    firestore: { rules: readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
beforeEach(async () => {
  await env.clearFirestore();
  db = env.unauthenticatedContext().firestore() as unknown as Firestore;
});
afterAll(async () => { await env?.cleanup(); });

describe('waitlist (F-WEB-02)', () => {
  it('accepts the exact record the website builds', async () => {
    await assertSucceeds(setDoc(await entryRef(), record()));
  });

  it('accepts every allowed platform, country and variant', async () => {
    for (const [platform, country, variant] of [['android', 'US', 'B'], ['both', 'other', 'A']] as const) {
      const email = `${platform}.${country}@example.com`;
      await assertSucceeds(setDoc(await entryRef(email), record({ email, platform, country, variant })));
    }
  });

  it('rejects a repeat sign-up for the same email (idempotent duplicate: the page shows "already on the list")', async () => {
    await assertSucceeds(setDoc(await entryRef(), record()));
    await assertFails(setDoc(await entryRef(), record()));
    await assertFails(setDoc(await entryRef('marcus.chen@example.com'), record({ email: 'MARCUS.CHEN@example.com' })));
  });

  it('allows no reads, updates or deletes', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const id = await waitlistDocId(input().email);
      await setDoc(doc(ctx.firestore() as unknown as Firestore, 'waitlist', id), { ...buildWaitlistRecord(input()), createdAt: Timestamp.now(), consentAt: Timestamp.now() });
    });
    const ref = await entryRef();
    await assertFails(getDoc(ref));
    await assertFails(getDocs(collection(db, 'waitlist')));
    await assertFails(updateDoc(ref, { platform: 'android' }));
    await assertFails(deleteDoc(ref));
  });

  it('rejects a document ID that is not the SHA-256 of the email', async () => {
    await assertFails(setDoc(doc(db, 'waitlist', 'random-id'), record()));
    await assertFails(setDoc(await entryRef('someone.else@example.com'), record()));
  });

  it('rejects extra and missing fields', async () => {
    const ref = await entryRef();
    await assertFails(setDoc(ref, { ...record(), name: 'Marcus' }));
    const { country: _country, ...missing } = record();
    await assertFails(setDoc(ref, missing));
  });

  it('rejects bad emails', async () => {
    for (const email of ['not-an-email', 'a@b', 'a b@example.com', `${'x'.repeat(250)}@example.com`]) {
      await assertFails(setDoc(await entryRef(email), { ...record(), email }));
    }
    // Not lowercased (the website always lowercases)
    const upper = 'Upper@Example.com';
    await assertFails(setDoc(doc(db, 'waitlist', await waitlistDocId(upper)), { ...record(), email: upper }));
  });

  it('rejects values outside the allowed sets', async () => {
    const ref = await entryRef();
    await assertFails(setDoc(ref, { ...record(), platform: 'iphone' }));
    await assertFails(setDoc(ref, { ...record(), country: 'FR' }));
    await assertFails(setDoc(ref, { ...record(), variant: 'a' }));
    await assertFails(setDoc(ref, { ...record(), consent: false }));
    await assertFails(setDoc(ref, { ...record(), consent: 'yes' }));
    await assertFails(setDoc(ref, { ...record(), sourcePage: '/index.html' }));
    await assertFails(setDoc(ref, { ...record(), consentTextVersion: '' }));
    await assertFails(setDoc(ref, { ...record(), consentTextVersion: 'v'.repeat(41) }));
  });

  it('rejects client-supplied timestamps', async () => {
    const ref = await entryRef();
    await assertFails(setDoc(ref, { ...record(), createdAt: Timestamp.fromDate(new Date('2026-01-01')) }));
    await assertFails(setDoc(ref, { ...record(), consentAt: Timestamp.now() }));
  });
});

describe('everything else', () => {
  it('denies reads and writes to any other collection', async () => {
    for (const path of ['accounts/u1', 'deletion_requests/r1', 'deletionRequests/r1', 'rideAlongPasses/p1', 'waitlist_export/x']) {
      await assertFails(setDoc(doc(db, path), { a: 1 }));
      await assertFails(getDoc(doc(db, path)));
    }
  });
});
