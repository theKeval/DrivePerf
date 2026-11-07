// Prints a one-line summary per Lighthouse CI run, plus the elements behind any layout shift, so a CI failure
// can be diagnosed from the job log (the full reports are also uploaded as the `lighthouse` artifact).
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const dir = process.argv[2] ?? '.lighthouseci';
for (const file of readdirSync(dir).filter((f) => f.startsWith('lhr-') && f.endsWith('.json'))) {
  const r = JSON.parse(readFileSync(join(dir, file), 'utf8'));
  const a = r.audits;
  const ms = (id) => Math.round(a[id]?.numericValue ?? NaN);
  console.log(`${r.finalDisplayedUrl}  perf ${r.categories.performance.score}  CLS ${a['cumulative-layout-shift'].numericValue.toFixed(3)}  LCP ${ms('largest-contentful-paint')} ms  TBT ${ms('total-blocking-time')} ms  (${r.environment?.hostUserAgent ?? ''})`);
  for (const i of a['layout-shifts']?.details?.items ?? []) {
    console.log(`  shift ${i.score.toFixed(3)}  ${i.node?.selector ?? '?'}  ${(i.node?.snippet ?? '').slice(0, 160)}`);
    for (const s of i.subItems?.items ?? []) console.log(`    cause: ${JSON.stringify(s).slice(0, 200)}`);
  }
  const lcp = a['largest-contentful-paint-element']?.details?.items?.[0]?.items?.[0]?.node;
  if (lcp) console.log(`  LCP element: ${lcp.selector}  ${(lcp.snippet ?? '').slice(0, 120)}`);
}
