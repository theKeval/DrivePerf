// S-WEB-05 Support: live FAQ filter. Filters questions, opens matches, announces the count, shows the empty state.
const input = document.getElementById('faq-search') as HTMLInputElement | null;
const count = document.getElementById('faq-count');
const empty = document.getElementById('faq-empty');
if (input && count && empty) {
  input.addEventListener('input', () => {
    const raw = input.value.trim(), q = raw.toLowerCase();
    let n = 0;
    document.querySelectorAll<HTMLDetailsElement>('.faq details').forEach((d) => {
      const hit = !q || (d.textContent || '').toLowerCase().includes(q);
      d.hidden = !hit;
      if (hit) n++;
      if (q && hit) d.open = true;
    });
    document.querySelectorAll<HTMLElement>('.faq-group').forEach((g) => (g.hidden = !g.querySelector('details:not([hidden])')));
    count.textContent = q ? `${n} ${n === 1 ? 'answer' : 'answers'} for “${raw}”` : '';
    empty.hidden = n > 0;
  });
}
