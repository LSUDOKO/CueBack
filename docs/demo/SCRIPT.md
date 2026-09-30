# CueBack demo video: script

**Length:** 3:54. The core story (problem, solution, the full save-and-return loop, and the RevenueCat purchase) is told in the first 2 minutes, because judges aren't required to watch further. After that come other uses, the RevenueCat dashboard, setup, and how it works.

**Format:** 1920×1080, 30 fps, H.264 with AAC audio. Real screen recordings from a Moto g34 (Android 15) inside a phone frame, on the app's own Ember backdrop, with Inter Tight captions and camera push-ins on key moments.

**Sound:** a narrated voiceover over an original ambient score. The narration is 24 lines, timed to the scenes, spoken by Kokoro's "Heart" voice (an open-source neural text-to-speech model, Apache 2.0, run locally). The exact words and timings are in `video/voiceover/lines.json`; `video/voiceover/generate.py` makes the clips and `video/voiceover/mix.py` lowers the music under the voice. The score comes from `video/scripts/score.py`. To use your own voice instead, record the lines at the listed times and replace the clips before mixing.

**Everything on the phone is real:** the pause and return were detected by the live app, the purchase is a real RevenueCat Test Store transaction, and "Back in 21 seconds" is what the app measured.

**Rebuild:** in `video/`, run `npx remotion render src/index.ts CueBackDemo out/cueback-demo.mp4`. The phone footage (`public/footage/`) and score (`public/audio/`) aren't in git because of their size.

---

| Time | Scene | On screen | Narration (summary; exact words in lines.json) |
|---|---|---|---|
| 0:00 | **The problem** (animated): a problem set; notifications fly in; the phone blurs | "9:14 pm. Test tomorrow." / "You didn't forget the goal. You forgot where you were." | "It's the night before a test. Problem seven is wrong, and you know why. Then your phone lights up, and when you come back, you've lost your place." |
| 0:10 | **Title:** Cue rises with a spark burst | "CueBack. Don't save the task. Save your place." | "This is CueBack." |
| 0:15 | **Studying** (footage, 3×) | "You're on Problem 7." CueBack watches just one app you chose: Chrome. | "CueBack watches only the apps you pick." |
| 0:25 | **Save your place** (footage): share to CueBack, type the next step, Save | "Save your place in one tap." Tag: *You said: Redo 7b, convert km to m first* | "Share the page and say the next step in your own words." |
| 0:37 | **Life happens** (footage): home screen, the phone locks | "Then the phone pulls you away." | "Then life happens." |
| 0:43 | **Time passes** (animated clock) | "Some time later" | |
| 0:47 | **You're back** (footage, push-in on the notification) | "You open Chrome again." / "CueBack notices." | "Open Chrome again, and CueBack notices, with your next step right in the notification." |
| 0:59 | **Welcome-back card** (footage, push-in on Next) | "Welcome back." / "Your next step, first." | "It leads with your next step, marked 'You said', because CueBack never passes off a guess as a fact." |
| 1:11 | **Resume** (footage): the same page reopens | "One tap, right where you were." | "One tap takes you back." |
| 1:19 | **Back to work** (footage, 5×) | "Back to work." | |
| 1:23 | **Re-entry time** (footage): "Back in 21 seconds", then Home | "Back in 21 seconds." / "Your place, kept." | "CueBack measured it: back to real work in twenty-one seconds." |
| 1:36 | **Pro paywall** (footage): plans loaded live from RevenueCat | "Upgrade when it's earned." | "Pro, powered by RevenueCat: annual, monthly or lifetime." |
| 1:48 | **Purchase** (footage): RevenueCat Test Store, "You have CueBack Pro" | "A real purchase, end to end." Tag: *cueback_pro: active* | "A real purchase through RevenueCat unlocks the cueback_pro entitlement." |
| 2:02 | **A lecture video** (YouTube, Khan Academy): share at the minute | "Share it at the minute." | "It isn't just for studying. A lecture, at the minute you left." |
| 2:09 | **A code review** (GitHub) | "The file you were checking." | "A code review, on the same file." |
| 2:17 | **Research for an essay** (Wikipedia in Chrome) | "The article you were citing." | "Research, on the same article." |
| 2:27 | **Your library** (footage): Home, Library, open a place, Resume | "Every place, one tap away." / "Search it all." | "Every place is in your library, searchable." |
| 2:41 | **RevenueCat** (animated): the official logo, the dashboard metrics and the transaction list | "Monetization that just works." Labelled as Test Store sandbox data | "Revenue, subscriptions and customers, all in RevenueCat." |
| 2:57 | **Setting it up** (onboarding footage) | "Meet Cue." / "Private by design." / "Tuned to your work." / "Three switches. That's it." | "Setup is three switches. CueBack sees which app is open, never what's on screen." |
| 3:24 | **How it works** (animated): Watch → Notice → Keep → Match → Give back | "CueBack never presents a guess as a fact." | "Everything runs on your phone. There's no CueBack server." |
| 3:36 | **Built to be trusted** (animated): the tech stack, RevenueCat and OneSignal | 65 automated tests, CI on every push | |
| 3:46 | **End card:** Cue waves | "CueBack. Save your place. Come back to it." github.com/LSUDOKO/CueBack · Built for RevenueCat Shipaton 2026 · Next Gen | "CueBack. Save your place, and come back to it." |

---

## Credits and rights

- Cue the owl, the app and the music are our own work.
- RevenueCat logo from RevenueCat's press kit. OneSignal is named in text only.
- Khan Academy video shown briefly in the YouTube app (Creative Commons BY-NC-SA). Other apps' recommendations on that screen are blurred.
- Wikipedia article text is CC BY-SA.
- The RevenueCat dashboard shows Test Store sandbox data from our own testing, not real revenue.
