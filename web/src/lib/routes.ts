// Indexable pages (sitemap.xml). Pages with noindex (waitlist confirmation, email preferences, ride-along: pass URLs
// are personal, DRIVE-181 §9; 404) are left out.
export const INDEXED_ROUTES = ['/', '/how-it-records', '/support', '/privacy', '/terms', '/delete-account', '/press'] as const;
