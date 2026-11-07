// S-WEB-06: platform tabs (WAI-ARIA tablist: arrows move, aria-selected, hidden panels). Every PlatformText
// sentence follows html[data-pl], which the inline boot script set before first paint (Android user agents →
// android, else ios); selecting a tab updates it.
type Pl = 'ios' | 'android';
const tabs = [...document.querySelectorAll<HTMLButtonElement>('[role="tab"][data-pl]')];
function select(pl: Pl, focus = false): void {
  tabs.forEach((t) => {
    const sel = t.dataset.pl === pl;
    t.setAttribute('aria-selected', String(sel));
    t.tabIndex = sel ? 0 : -1;
    if (sel && focus) t.focus();
  });
  document.querySelectorAll<HTMLElement>('[role="tabpanel"][data-pl]').forEach((p) => (p.hidden = p.dataset.pl !== pl));
  document.documentElement.dataset.pl = pl;
}
tabs.forEach((t, i) => {
  t.addEventListener('click', () => select(t.dataset.pl as Pl));
  t.addEventListener('keydown', (e) => {
    let n: HTMLButtonElement | undefined;
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') n = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
    else if (e.key === 'Home') n = tabs[0];
    else if (e.key === 'End') n = tabs[tabs.length - 1];
    if (n) { e.preventDefault(); select(n.dataset.pl as Pl, true); }
  });
});
if (tabs.length) select(document.documentElement.dataset.pl === 'android' ? 'android' : 'ios');
