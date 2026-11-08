// Lighthouse CI (F-WEB-01.2, ADR-009): mobile emulation (the Lighthouse default), every page, launch targets.
// Runs against the prod-env static build in dist/ on LHCI's local server, so Hosting headers are not part of
// this audit; they are checked with curl -I on the preview channel (docs/launch/firebase-setup.md).
const indexed = ['/', '/privacy', '/terms', '/support', '/how-it-records', '/delete-account', '/press'];
const noindex = ['/waitlist/confirmed', '/email-preferences', '/ride-along', '/404'];
// The home hero is an A/B test picked at random per load; audit both variants explicitly so a shift in either is
// caught every run instead of on whichever variant the run happened to draw.
const url = (p) => (p === '/' ? ['http://localhost/?variant=A', 'http://localhost/?variant=B'] : [`http://localhost${p}.html`]);

const base = {
  'categories:performance': ['error', { minScore: 0.95 }],
  'categories:accessibility': ['error', { minScore: 1 }],
  'categories:best-practices': ['error', { minScore: 1 }],
  'cumulative-layout-shift': ['error', { maxNumericValue: 0 }],
};

module.exports = {
  ci: {
    // 3 runs per URL (the LHCI default; assertions use the best run). With 1 run, the first URL took the CI
    // runner's cold-Chrome start as ~1 s of unattributable main-thread work (TBT 765 ms vs 111 ms on the next URL).
    collect: { staticDistDir: './dist', url: [...indexed, ...noindex].flatMap(url), numberOfRuns: 3 },
    assert: {
      assertMatrix: [
        { matchingUrlPattern: '^(?!.*(waitlist/confirmed|email-preferences|ride-along|404)).*$', assertions: { ...base, 'categories:seo': ['error', { minScore: 1 }] } },
        // noindex pages can't score 100 on SEO ("Page is blocked from indexing") by design; the rest still applies.
        { matchingUrlPattern: '.*(waitlist/confirmed|email-preferences|ride-along|404).*', assertions: base },
      ],
    },
    upload: { target: 'filesystem', outputDir: './.lighthouseci' },
  },
};
