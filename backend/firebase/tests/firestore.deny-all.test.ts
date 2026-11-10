// DRIVE-76 (ADR-004): clients never touch Firestore directly. Only the API's service account reads and writes user
// data (rules do not apply to it). These tests walk the whole document tree of data-model.md section 3.2 and prove
// that nobody gets in through the client SDK: not signed out, not signed in, not the owner of the data.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, type Firestore } from 'firebase/firestore';

let env: RulesTestEnvironment;

const OWNER = 'acct-owner';
const OTHER = 'acct-other';

// Every path of the document tree (data-model.md 3.2), plus names that do not exist yet.
const DOCUMENT_PATHS = [
  `users/${OWNER}`,
  `users/${OWNER}/consents/c1`,
  `users/${OWNER}/settings/profile`,
  `users/${OWNER}/devices/d1`,
  `users/${OWNER}/vehicles/v1`,
  `users/${OWNER}/places/p1`,
  `users/${OWNER}/privacyZones/z1`,
  `users/${OWNER}/routes/r1`,
  `users/${OWNER}/trips/t1`,
  `accounts/${OWNER}`,
  `accounts/${OWNER}/mutations/m1`,
  'rideAlongPasses/pass1',
  'deletionRequests/req1',
  'jobRuns/run1',
  'entitlementEvents/e1',
  'somethingNew/doc1',
];

const COLLECTIONS = ['users', 'accounts', 'rideAlongPasses', 'deletionRequests', 'jobRuns', 'waitlist', `users/${OWNER}/trips`];

type Client = { label: string; db: () => Firestore };
let clients: Client[];

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-driveperf',
    firestore: { rules: readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
  clients = [
    { label: 'signed out', db: () => env.unauthenticatedContext().firestore() as unknown as Firestore },
    { label: 'signed in as the owner', db: () => env.authenticatedContext(OWNER, { email_verified: true }).firestore() as unknown as Firestore },
    { label: 'signed in as someone else', db: () => env.authenticatedContext(OTHER).firestore() as unknown as Firestore },
    { label: 'signed in with an admin claim', db: () => env.authenticatedContext(OTHER, { admin: true }).firestore() as unknown as Firestore },
  ];
});
beforeEach(async () => {
  await env.clearFirestore();
  // Real data is present, so a pass cannot mean "nothing to read".
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore() as unknown as Firestore;
    for (const path of DOCUMENT_PATHS) await setDoc(doc(db, path), { seeded: true, deletedAt: null });
  });
});
afterAll(async () => { await env?.cleanup(); });

describe('Firestore is closed to every client (ADR-004)', () => {
  for (const path of DOCUMENT_PATHS) {
    it(`denies get, set, update and delete on ${path}`, async () => {
      for (const client of clients) {
        const ref = doc(client.db(), path);
        await assertFails(getDoc(ref));
        await assertFails(setDoc(ref, { seeded: false }));
        await assertFails(updateDoc(ref, { seeded: false }));
        await assertFails(deleteDoc(ref));
      }
    });
  }

  for (const path of COLLECTIONS) {
    it(`denies listing ${path}`, async () => {
      for (const client of clients) await assertFails(getDocs(collection(client.db(), path)));
    });
  }

  it('denies collection-group queries (including the tombstone queries the API runs with its service account)', async () => {
    for (const client of clients) {
      for (const group of ['trips', 'routes', 'vehicles', 'places', 'consents']) {
        await assertFails(getDocs(query(collectionGroup(client.db(), group), where('deletedAt', '==', null))));
      }
    }
  });

  it('lets the service account in: rules are bypassed, which is how the API works', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const snap = await getDoc(doc(ctx.firestore() as unknown as Firestore, `users/${OWNER}/trips/t1`));
      if (!snap.exists()) throw new Error('the seeded trip should be readable without rules');
    });
  });
});
