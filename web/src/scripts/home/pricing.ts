// Pricing region toggle (US / CA). Default: ?region=, else CA for an en-CA browser, else US.
type Prices = Record<'US' | 'CA', Record<'monthly' | 'annual' | 'founding' | 'annualPerMonth', string>>;
const sec = document.getElementById('pricing');
if (sec) {
  const P: Prices = JSON.parse(sec.dataset.prices || '{}');
  const alt = document.getElementById('price-alt');
  const set = (rg: 'US' | 'CA') => {
    sec.querySelectorAll<HTMLElement>('[data-region]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.region === rg)));
    const p = P[rg];
    sec.querySelectorAll<HTMLElement>('[data-price]').forEach((el) => {
      const k = el.dataset.price as keyof Prices['US'] | 'free';
      el.textContent = k === 'free' ? (rg === 'CA' ? 'CA$0' : 'US$0') : p[k];
    });
    const o = P[rg === 'CA' ? 'US' : 'CA'];
    if (alt) alt.textContent = `${rg === 'CA' ? 'In the US' : 'In Canada'}: ${o.monthly} a month or ${o.annual} a year, with a founding first year at ${o.founding}.`;
  };
  sec.querySelectorAll<HTMLElement>('[data-region]').forEach((b) => b.addEventListener('click', () => set(b.dataset.region === 'CA' ? 'CA' : 'US')));
  const q = new URLSearchParams(location.search).get('region');
  set(q === 'CA' || q === 'US' ? q : navigator.language === 'en-CA' ? 'CA' : 'US');
}
