// The only inline script on the site. It must run before first paint, so it cannot be an external module.
// Keep it byte-stable: its SHA-256 is pinned in the Content-Security-Policy (backend/firebase/firebase.json),
// and scripts/check-dist.mjs fails the build if the hash in the built HTML and the CSP disagree.
//  - html[data-js]: JS runs, so reveal-on-scroll may hide content until it scrolls into view
//  - theme: ?theme=light|dark, else the footer toggle's sessionStorage value (no cookies)
//  - ?rm=1: force reduced motion (QA aid)
//  - A/B (home only, html[data-variant] present): random per page load, no storage (F-WEB-01); ?variant=A|B pins it
//  - html[data-pl]: platform for PlatformText wording: ?platform=ios|android|both (waitlist confirmation), else
//    android for Android user agents, else ios. Set here, not in a module, so the text never reflows after paint.
export const THEME_BOOT =
  "(function(d,q){d.setAttribute('data-js','');var t=q.get('theme');try{t=t||sessionStorage.getItem('dp-theme')}catch(e){}" +
  "if(t==='light'||t==='dark')d.setAttribute('data-theme',t);if(q.get('rm')==='1')d.setAttribute('data-rm','true');" +
  "if(d.hasAttribute('data-variant')){var v=q.get('variant');d.setAttribute('data-variant',v==='A'||v==='B'?v:Math.random()<.5?'A':'B')}" +
  "var p=q.get('platform');d.setAttribute('data-pl',p==='ios'||p==='android'||p==='both'?p:/android/i.test(navigator.userAgent)?'android':'ios')" +
  "})(document.documentElement,new URLSearchParams(location.search))";
