// Table of contents: highlight the section in view (legal pages, support). Ported from pages.js.
const toc = document.querySelector('.toc');
if (toc && 'IntersectionObserver' in window) {
  const links = [...toc.querySelectorAll<HTMLAnchorElement>('a[href^="#"]')];
  const map = new Map(links.map((a) => [a.getAttribute('href')!.slice(1), a]));
  const io = new IntersectionObserver((en) => en.forEach((e) => {
    if (e.isIntersecting) { links.forEach((l) => l.classList.remove('is-active')); map.get(e.target.id)?.classList.add('is-active'); }
  }), { rootMargin: '-20% 0px -70% 0px' });
  map.forEach((_, id) => { const s = document.getElementById(id); if (s) io.observe(s); });
}
