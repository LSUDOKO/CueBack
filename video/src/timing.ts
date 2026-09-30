import type {Caption, Focus, Tag} from "./scenes";

const s = (sec: number) => Math.round(sec * 30);

/** Cuts from the real return take (seconds in public/footage/return.mp4). */
export const RETURN: {key: string; from: number; to: number; rate: number; step: string; captions: Caption[]; tags?: Tag[]; focus?: Focus[]}[] = [
  {
    key: "notify",
    from: 3,
    to: 22.4,
    rate: 1.6,
    step: "Coming back",
    captions: [
      {at: 0, until: s(6), lines: ["You open", "Chrome again."], sub: "Three minutes later, back to the same page."},
      {at: s(6), until: s(12.1), lines: ["CueBack", "notices."], sub: "\"You're back in Chrome\", with your next step right in the notification."},
    ],
    focus: [{at: s(4.6), until: s(9.2), scale: 1.55, y: 0.07}],
  },
  {
    key: "card",
    from: 22.4,
    to: 34,
    rate: 1,
    step: "The welcome-back card",
    captions: [
      {at: 0, until: s(5.5), lines: ["Welcome back."], sub: "Cue found your place: which task, and how long you were gone."},
      {at: s(5.5), until: s(11.6), lines: ["Your next step,", "first."], sub: "In your own words. Tagged \"You said\", because CueBack never passes off a guess as a fact."},
    ],
    tags: [{at: s(5.5), until: s(11.6), text: "Next: Redo 7b, convert km to m first", x: 120, y: 720, flame: true}],
    focus: [{at: s(5.2), until: s(11.6), scale: 1.45, y: 0.46}],
  },
  {
    key: "resume",
    from: 38,
    to: 46,
    rate: 1,
    step: "Resume",
    captions: [{at: 0, until: s(8), lines: ["One tap,", "right where you were."], sub: "The exact page you saved reopens."}],
  },
  {
    key: "dwell",
    from: 46,
    to: 66,
    rate: 5,
    step: "Resume",
    captions: [{at: 0, until: s(4), lines: ["Back to work."], sub: "CueBack quietly times how long it took you to get going again."}],
  },
  {
    key: "result",
    from: 66,
    to: 82.6,
    rate: 1.2,
    step: "Re-entry time",
    captions: [
      {at: 0, until: s(8), lines: ["Back in", "21 seconds."], sub: "From the welcome-back card to real work. Yours to keep, not a score."},
      {at: s(8), until: s(13.8), lines: ["Your place,", "kept."], sub: "Home shows what's open and how fast you usually get back."},
    ],
  },
];
