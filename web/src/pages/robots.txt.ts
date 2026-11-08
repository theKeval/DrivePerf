// robots.txt: prod allows crawling and points at the sitemap; preview/local builds disallow everything.
import type { APIRoute } from 'astro';
import { SITE } from '../config/site';

export const GET: APIRoute = () => new Response(
  SITE.env === 'prod'
    ? `User-agent: *\nAllow: /\n\nSitemap: ${SITE.origin}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n',
  { headers: { 'Content-Type': 'text/plain' } },
);
