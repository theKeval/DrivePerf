// S-WEB-05 FAQ content (DRIVE-179), ported from prototypes/web/pages/support.html. Answers are HTML (links).
// Rendered by FaqList.astro and emitted as FAQPage JSON-LD on /support.
export interface FaqItem { q: string; a: string; open?: boolean }
export interface FaqGroup { id: string; title: string; items: FaqItem[] }

export const FAQ: FaqGroup[] = [
  {
    "id": "capture",
    "title": "Recording and capture",
    "items": [
      {
        "q": "How does automatic recording work?",
        "a": "Turn on automatic trips and DrivePerf notices when you start driving, records the drive and ends it a few minutes after you park. Between drives it waits for low-power signals from your phone instead of keeping GPS on. <a href=\"/how-it-records\">How DrivePerf records</a> explains it step by step for iPhone and Android.",
        "open": true
      },
      {
        "q": "Why is the start of some drives dashed and marked \"estimated\"?",
        "a": "Phones take a moment to wake up an app when you start driving, so the first few hundred metres of an automatic drive may be missing. DrivePerf fills that stretch from where you parked and your phone's motion history, draws it dashed and labels it \"estimated\". It never shows event pins there."
      },
      {
        "q": "What happens if I force-quit (or force-stop) the app?",
        "a": "Force-quitting on iPhone, or force-stopping on Android, pauses automatic detection until you open DrivePerf again. You don't need to keep the app open; just leave it in the background. The Health Check in Settings shows you if anything needs fixing."
      },
      {
        "q": "DrivePerf missed a drive. What can I do?",
        "a": "Open the Health Check in Settings: it checks location, motion access, Low Power Mode or battery settings, and notifications, with a one-tap fix for each. On some Android phones the maker's battery saver puts apps to sleep; the Health Check helps you exempt DrivePerf. If a drive is still missed, the app may ask \"Did you drive around 5 pm?\" so we can tune detection."
      },
      {
        "q": "Can I record drives manually instead?",
        "a": "Yes. Start a drive with one tap before you set off: from the app, the Control Center control or widget on iPhone, or the Quick Settings tile or widget on Android. Every feature, including Pro, works in manual mode."
      }
    ]
  },
  {
    "id": "battery",
    "title": "Battery",
    "items": [
      {
        "q": "How much battery does it use?",
        "a": "About 2–4% of battery per hour of driving with the screen off. That's our estimate; we'll publish measured numbers from the beta. If the screen is on for navigation or music, your phone will use more for that, not for DrivePerf. When your phone gets hot or low, DrivePerf records less often to protect it."
      },
      {
        "q": "Does it drain my battery when I'm not driving?",
        "a": "Barely. Between drives DrivePerf doesn't run GPS. It relies on your phone's motion signals and a small geofence around where you parked."
      }
    ]
  },
  {
    "id": "passenger",
    "title": "Passenger trips",
    "items": [
      {
        "q": "I was a passenger. Will that count against my score?",
        "a": "No. Tap \"I was a passenger\" on the Story and the trip is removed from your scores, records and recaps. If you know before you leave, a passenger can start the drive as a passenger trip. Bus and train rides are usually recognised; when we're not sure, the Story asks."
      }
    ]
  },
  {
    "id": "scores",
    "title": "Scores and tips",
    "items": [
      {
        "q": "What is Smoothness?",
        "a": "A score from 0 to 100 for how gently you braked, accelerated and turned, compared with your own usual on the same route or the same kind of road. Your first three drives set your baseline. A full, gentle stop scores as well as not stopping, so rolling stops are never rewarded."
      },
      {
        "q": "Someone cut me off and I had to brake hard. Is that fair?",
        "a": "Tap the stop and answer \"Yes, it was needed\". It's left out of your score straight away, with no further questions."
      },
      {
        "q": "Will DrivePerf show me my score while I drive?",
        "a": "No, never. While a drive is in progress the app shows only a calm recording screen. No score, no speed, no alerts. Your Story is ready once you're parked."
      }
    ]
  },
  {
    "id": "data",
    "title": "Your data and privacy",
    "items": [
      {
        "q": "Do I need an account?",
        "a": "No. Everything in Free and the Pro Preview works without one, and your trips stay on your phone. Sign in only if you want Pro cloud backup or to use a second device."
      },
      {
        "q": "Is this an insurance app?",
        "a": "No. DrivePerf has no insurance features, and your data is never sold and never shared with insurers."
      },
      {
        "q": "How do I download or delete my data?",
        "a": "Open Profile, then Privacy Centre. \"Download all my data\" creates a ZIP in an open format, free on every plan. You can delete a trip, a date range, everything, or your whole account. No app any more? Use the <a href=\"/delete-account\">account deletion page</a>."
      },
      {
        "q": "What do share cards show?",
        "a": "By default, the shape of a route with the start and end trimmed, its Smoothness, your car and the part of day. Never a map unless you choose it each time, never where you start or stop, never speed."
      }
    ]
  },
  {
    "id": "pro",
    "title": "Pro and the Pro Preview",
    "items": [
      {
        "q": "What do I get for free?",
        "a": "Recording, every Drive Story, Smoothness and tips, trip summaries forever, Replay and Analysis for the last 30 days, the weekly recap, Drive Wrapped, sharing, 2 routes, 1 car and a full data download. See <a href=\"/#pricing\">Free vs Pro</a>."
      },
      {
        "q": "How does the Pro Preview work?",
        "a": "Every new driver gets 14 days of Pro with no card. On day 15 you'll see \"Your first two weeks, decoded\" and choose: keep Pro, or continue with Free. Nothing is ever charged without you choosing a plan."
      },
      {
        "q": "How much is Pro?",
        "a": "US$6.99 a month or US$39.99 a year in the US; CA$8.99 a month or CA$49.99 a year in Canada. Founding Drivers get their first year for US$29.99 or CA$39.99."
      },
      {
        "q": "What happens to my trips if Pro ends?",
        "a": "They stay on your phone. Replay and Analysis older than 30 days are locked, not lost: subscribe again and they're back. Cloud backup stops, and the cloud copy is kept for 30 days, with reminders, then deleted. During those 30 days you can still restore it to a new phone or download it."
      }
    ]
  },
  {
    "id": "cancel",
    "title": "Cancelling and refunds",
    "items": [
      {
        "q": "How do I cancel?",
        "a": "On iPhone: Settings, your name, Subscriptions, DrivePerf. On Android: Google Play, Profile, Payments and subscriptions, Subscriptions. Pro stays on until the end of the period you paid for."
      },
      {
        "q": "Can I get a refund?",
        "a": "Refunds are handled by Apple or Google. Request one from your App Store or Google Play purchase history."
      }
    ]
  }
];
