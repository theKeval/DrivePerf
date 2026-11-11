// DRIVE-76: guards that keep the Firebase config in line with the decisions log. They read files only (no emulator).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const read = (rel: string) => readFileSync(fileURLToPath(new URL(`../${rel}`, import.meta.url)), 'utf8');
const json = (rel: string) => JSON.parse(read(rel)) as Record<string, any>;

describe('firestore.rules', () => {
  it('allows exactly one thing to clients: creating a waitlist entry (ADR-004)', () => {
    const rules = read('firestore.rules').replace(/\/\/.*$/gm, '');
    const allows = [...rules.matchAll(/allow\s+([a-z, ]+):\s*if\s+([^;]+);/g)].map((m) => ({ ops: m[1].trim(), cond: m[2].trim() }));
    const open = allows.filter((a) => a.cond !== 'false');
    expect(open).toHaveLength(1);
    expect(open[0].ops).toBe('create');
    expect(open[0].cond.startsWith('isValidWaitlistEntry(')).toBe(true);
    // The catch-all denies everything that is not matched above.
    expect(allows.some((a) => a.ops === 'read, write' && a.cond === 'false')).toBe(true);
  });
});

describe('firebase.json (the live Hosting config belongs to DRIVE-79 and is not changed here)', () => {
  const config = json('firebase.json');

  it('has no Storage or Functions section: trip files go to Cloudflare R2 and the server is the Ktor API (ADR-004, decisions log 6 Nov 2026)', () => {
    expect(config.storage).toBeUndefined();
    expect(config.functions).toBeUndefined();
  });

  it('keeps Firestore rules and indexes wired and the Auth emulator on', () => {
    expect(config.firestore).toEqual({ rules: 'firestore.rules', indexes: 'firestore.indexes.json' });
    expect(config.emulators.auth.port).toBe(9099);
    expect(config.emulators.firestore.port).toBe(8080);
  });
});

describe('api-hosting/firebase.json (api.driveperf.com, prepared, not deployed)', () => {
  const hosting = json('api-hosting/firebase.json').hosting;

  it('rewrites every path to the Cloud Run service "api" in us-central1 (ADR-004)', () => {
    expect(hosting.rewrites).toEqual([{ source: '**', run: { serviceId: 'api', region: 'us-central1' } }]);
  });

  it('is a separate Hosting target, so it can never replace the driveperf.com site', () => {
    expect(hosting.target).toBe('api');
    expect(hosting.public).toBe('static');
    expect(read('api-hosting/static/robots.txt')).toContain('Disallow: /');
  });
});

describe('firestore.indexes.json', () => {
  const { indexes, fieldOverrides } = json('firestore.indexes.json');

  it('holds the four composite indexes of the server jobs (data-model.md 3.5)', () => {
    const shape = indexes.map((i: any) => `${i.collectionGroup}:${i.fields.map((f: any) => f.fieldPath).join(',')}`);
    expect(shape).toEqual([
      'accounts:backup.state,backup.purgeAt',
      'accounts:backup.state,backup.nextReminderAt',
      'accounts:backup.state,backup.writeUntil',
      'accounts:status,deletionRequestedAt',
    ]);
    for (const index of indexes) expect(index.queryScope).toBe('COLLECTION');
  });

  it('exempts the bulky trip fields from indexing and keeps the tombstone collection-group indexes', () => {
    const exempt = fieldOverrides.filter((o: any) => o.collectionGroup === 'trips' && o.indexes.length === 0).map((o: any) => o.fieldPath);
    expect(exempt.sort()).toEqual(['bbox', 'events', 'eventFeedback', 'fieldClock', 'files', 'previewTrack', 'quality', 'score', 'stops'].sort());
    for (const group of ['trips', 'routes', 'vehicles', 'places']) {
      const o = fieldOverrides.find((x: any) => x.collectionGroup === group && x.fieldPath === 'deletedAt');
      expect(o.indexes.map((i: any) => i.queryScope).sort()).toEqual(['COLLECTION', 'COLLECTION_GROUP']);
    }
  });
});
