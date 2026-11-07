// Home FAQ teaser (S-WEB-01). Plain-text answers: also emitted as FAQPage JSON-LD.
export const HOME_FAQ: { q: string; a: string }[] = [
  { q: 'Will it drain my battery?', a: "We estimate about 2–4% an hour of driving with the screen off. Between drives, DrivePerf waits for the phone to tell it you're moving instead of keeping GPS on. We'll publish measured numbers from the beta." },
  { q: 'Is this an insurance app?', a: 'No, and it never will be. Your data is never sold and never shared with insurers. DrivePerf is for people who enjoy driving and want to get smoother.' },
  { q: 'Do I need an account?', a: 'No. Everything in Free and the Pro Preview works without one. You only sign in if you want Pro cloud backup or a second device.' },
  { q: 'What happens when the Pro Preview ends?', a: 'You choose. Continue with Free and keep every trip summary forever, or keep Pro. Replay and Analysis older than 30 days are locked on Free, not lost.' },
];
export const faqJsonLd = (items = HOME_FAQ) => ({
  '@context': 'https://schema.org', '@type': 'FAQPage',
  mainEntity: items.map(({ q, a }) => ({ '@type': 'Question', name: q, acceptedAnswer: { '@type': 'Answer', text: a } })),
});
