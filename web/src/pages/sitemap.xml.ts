// sitemap.xml (prod build lists every indexable page; non-prod builds are noindex and list nothing).
import type { APIRoute } from 'astro';
import { SITE } from '../config/site';
import { INDEXED_ROUTES } from '../lib/routes';

export const GET: APIRoute = () => {
  const urls = SITE.env === 'prod' ? INDEXED_ROUTES.map((p) => `  <url><loc>${SITE.origin}${p}</loc></url>`).join('\n') : '';
  const body = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
  return new Response(body, { headers: { 'Content-Type': 'application/xml' } });
};
