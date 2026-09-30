import React from "react";
import {AbsoluteFill, Audio, Composition, Series, interpolate, staticFile, useCurrentFrame} from "remotion";
import {End, Footage, HowItWorks, Problem, RevenueCat, Stack, TimePasses, Title, UseCases} from "./scenes";
import {useFont} from "./ui";
import {RETURN} from "./timing";

const FPS = 30;
const s = (sec: number) => Math.round(sec * FPS);

/** Scene list: [component, duration in frames]. The first two minutes carry the whole story. */
const SCENES: [React.ReactNode, number][] = [
  [<Problem />, s(10)],
  [<Title />, s(5)],
  [
    <Footage
      clip="study"
      from={6}
      to={36}
      rate={3}
      step="The night before a test"
      captions={[{at: 0, until: s(10), lines: ["You're on", "Problem 7."], sub: "CueBack watches just one app you chose: Chrome."}]}
    />,
    s(10),
  ],
  [
    <Footage
      clip="study"
      from={73}
      to={91}
      rate={1.5}
      step="Save your place"
      captions={[{at: 0, until: s(12), lines: ["Save your place", "in one tap."], sub: "Share the page to CueBack and write the next step in your own words."}]}
      tags={[{at: s(4), until: s(12), text: "You said: Redo 7b, convert km to m first", x: 120, y: 700, flame: true}]}
    />,
    s(12),
  ],
  [
    <Footage
      clip="study"
      from={90}
      to={96.4}
      rate={1}
      step="Then life happens"
      captions={[{at: 0, until: s(6.4), lines: ["Then the phone", "pulls you away."], sub: "You lock it. You go somewhere else. That's fine."}]}
    />,
    s(6.4),
  ],
  [<TimePasses label="Some time later" sub="CueBack noticed the pause and kept your place." />, s(4)],
  ...RETURN.map((r) => [<Footage key={r.key} clip="return" from={r.from} to={r.to} rate={r.rate} step={r.step} captions={r.captions} tags={r.tags} focus={r.focus} />, s((r.to - r.from) / r.rate)] as [React.ReactNode, number]),
  [
    <Footage
      clip="purchase"
      from={16}
      to={40}
      rate={2}
      step="Pro, powered by RevenueCat"
      captions={[{at: 0, until: s(12), lines: ["Upgrade when", "it's earned."], sub: "Plans load live from the RevenueCat offering: annual, monthly or lifetime."}]}
    />,
    s(12),
  ],
  [
    <Footage
      clip="purchase"
      from={40}
      to={60}
      rate={1.5}
      step="Pro, powered by RevenueCat"
      captions={[{at: 0, until: s(13.3), lines: ["A real purchase,", "end to end."], sub: "RevenueCat's Test Store runs the transaction; the cueback_pro entitlement unlocks Pro."}]}
      tags={[{at: s(7), until: s(13.3), text: "cueback_pro: active", x: 120, y: 700, flame: true}]}
    />,
    s(13.3),
  ],
  [
    <Footage
      clip="lecture"
      from={8.8}
      to={11.6}
      rate={0.8}
      step="Not just study · 1 of 3"
      mask={[{until: 99999, below: 0.55}]}
      captions={[{at: 0, until: s(3.5), lines: ["A lecture video."], sub: "A Khan Academy lesson on average velocity, three minutes in."}]}
    />,
    s(3.5),
  ],
  [
    <Footage
      clip="lecture"
      from={23}
      to={26.6}
      rate={0.9}
      step="Not just study · 1 of 3"
      mask={[{until: 99999, below: 0.735, sidesFrom: 0.28}]}
      captions={[{at: 0, until: s(4), lines: ["Share it at", "the minute."], sub: "\"Pause at 3:00 and try the example first.\""}]}
    />,
    s(4),
  ],
  [
    <Footage
      clip="code"
      from={15}
      to={27.8}
      rate={1.6}
      step="Not just study · 2 of 3"
      captions={[{at: 0, until: s(8), lines: ["A code review."], sub: "The file you were checking in GitHub: \"Check the recency weight before merging.\""}]}
    />,
    s(8),
  ],
  [
    <Footage
      clip="research"
      from={9}
      to={27.8}
      rate={1.88}
      step="Not just study · 3 of 3"
      captions={[{at: 0, until: s(10), lines: ["Research for", "an essay."], sub: "The article you were citing: \"Cite the warming figure in paragraph 2.\""}]}
    />,
    s(10),
  ],
  [
    <Footage
      clip="library"
      from={3}
      to={37}
      rate={2.5}
      step="Your library"
      variant="dawn"
      captions={[
        {at: 0, until: s(6), lines: ["Every place,", "one tap away."], sub: "Home shows what's open. Pro keeps as many as you need."},
        {at: s(6), until: s(13.6), lines: ["Search it all."], sub: "By task, link, file or app. Open one and pick up where you stopped."},
      ]}
    />,
    s(13.6),
  ],
  [<RevenueCat />, s(16)],
  [
    <Footage
      clip="onboarding"
      from={0}
      to={30}
      rate={2}
      step="Setting it up"
      variant="dusk"
      captions={[
        {at: 0, until: s(5), lines: ["Meet Cue."], sub: "The owl that keeps your place."},
        {at: s(5), until: s(10), lines: ["Private by", "design."], sub: "It sees which of your chosen apps is open. Never what's on screen."},
        {at: s(10), until: s(15), lines: ["Tuned to", "your work."], sub: "Study, coding, writing, research or design."},
      ]}
    />,
    s(15),
  ],
  [
    <Footage
      clip="onboarding"
      from={30}
      to={65}
      rate={3}
      step="Setting it up"
      variant="dusk"
      captions={[{at: 0, until: s(11.7), lines: ["Three switches.", "That's it."], sub: "Usage access, the apps to watch, notifications. Then CueBack does the remembering."}]}
    />,
    s(11.7),
  ],
  [<HowItWorks />, s(12)],
  [<Stack />, s(10)],
  [<End />, s(8)],
];

export const TOTAL = SCENES.reduce((a, [, d]) => a + d, 0);

const Demo: React.FC = () => {
  useFont();
  const f = useCurrentFrame();
  // Narration and music are pre-mixed (voiceover/mix.py), with the music ducked under the voice.
  const vol = interpolate(f, [0, s(1), TOTAL - s(3), TOTAL], [0, 1, 1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return (
    <AbsoluteFill style={{backgroundColor: "#080503"}}>
      <Audio src={staticFile("audio/mix.wav")} volume={vol} />
      <Series>
        {SCENES.map(([node, d], i) => (
          <Series.Sequence key={i} durationInFrames={d}>
            {node}
          </Series.Sequence>
        ))}
      </Series>
    </AbsoluteFill>
  );
};

export const Root: React.FC = () => (
  <Composition id="CueBackDemo" component={Demo} durationInFrames={TOTAL} fps={FPS} width={1920} height={1080} />
);
