// Lighthouse CI (F-WEB-01.2, ADR-009): mobile emulation (the Lighthouse default), every page, launch targets.
// Runs against the prod-env static build in dist/ on LHCI's local server, so Hosting headers are not part of
// this audit; they are checked with curl -I on the preview channel (docs/launch/firebase-setup.md).
const indexed = ['/', '/privacy', '/terms', '/support', '/how-it-records', '/delete-account', '/press'];
const noindex = ['/waitlist/confirmed', '/email-preferences', '/ride-along', '/404'];
const url = (p) => `http://localhost${p === '/' ? '/' : p + '.html'}`;

const base = {
  'categories:performance': ['error', { minScore: 0.95 }],
  'categories:accessibility': ['error', { minScore: 1 }],
  'categories:best-practices': ['error', { minScore: 1 }],
  'cumulative-layout-shift': ['error', { maxNumericValue: 0 }],
};

module.exports = {
  ci: {
    collect: { staticDistDir: './dist', url: [...indexed, ...noindex].map(url), numberOfRuns: 1 },
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
