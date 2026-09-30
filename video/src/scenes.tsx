import React from "react";
import {AbsoluteFill, Img, Sequence, interpolate, staticFile, useCurrentFrame, useVideoConfig} from "remotion";
import {Backdrop, C, Chip, FONT, Glass, Headline, Owl, Phone, SceneFade, Sparks, Sub, enter} from "./ui";

const fade = (f: number, a: number, b: number, c: number, d: number) =>
  Math.min(
    interpolate(f, [a, b], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
    interpolate(f, [c, d], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
  );

/* ------------------------------------------------------------------ 1. The problem */

const NOTES = [
  {title: "Class group", body: "12 new messages", x: 1080, y: 170, at: 70},
  {title: "Incoming call", body: "Mom", x: 1210, y: 330, at: 92},
  {title: "Reels", body: "Just one more?", x: 1030, y: 500, at: 112},
  {title: "Class group", body: "anyone have the notes??", x: 1190, y: 660, at: 128},
  {title: "Game", body: "Your energy is full", x: 1060, y: 820, at: 142},
];

export const Problem: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const blur = interpolate(f, [150, 210], [0, 14], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  const dim = interpolate(f, [150, 210], [1, 0.25], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  const shake = f > 70 && f < 160 ? Math.sin(f * 1.7) * 3 : 0;
  return (
    <SceneFade>
      <Backdrop variant="dusk">
        <div style={{position: "absolute", left: 610, top: 90, width: 405, height: 900, opacity: dim, filter: `blur(${blur}px)`, transform: `translateX(${shake}px)`}}>
          <Img
            src={staticFile("shots/problem_page_clean.png")}
            style={{width: "100%", height: "100%", objectFit: "cover", borderRadius: 40, boxShadow: "0 40px 120px rgba(0,0,0,0.6), 0 0 0 3px rgba(255,243,232,0.12)"}}
          />
        </div>
        {NOTES.map((n, i) => {
          const s = enter(f, fps, n.at, 16);
          const out = interpolate(f, [150, 200], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
          return (
            <div
              key={i}
              style={{
                position: "absolute",
                left: n.x,
                top: n.y,
                width: 460,
                padding: "20px 24px",
                borderRadius: 26,
                background: "rgba(30,30,34,0.92)",
                border: "1px solid rgba(255,255,255,0.1)",
                boxShadow: "0 20px 50px rgba(0,0,0,0.5)",
                fontFamily: FONT,
                color: "#fff",
                opacity: s * out,
                transform: `translateX(${(1 - s) * 180}px) rotate(${(i % 2 ? 1.5 : -1.2) * (1 - s)}deg)`,
              }}
            >
              <div style={{fontSize: 20, opacity: 0.6, fontWeight: 500}}>{n.title}</div>
              <div style={{fontSize: 27, marginTop: 4}}>{n.body}</div>
            </div>
          );
        })}
        <Sequence durationInFrames={150}>
          <Headline lines={["9:14 pm.", "Test tomorrow."]} x={120} y={330} size={72} width={480} />
          <Sub text="Problem 7 is wrong, and you know why: the units." x={120} y={520} width={440} delay={30} />
        </Sequence>
        <Sequence from={196}>
          <Headline lines={["You didn't forget the goal."]} x={0} y={390} width={1920} size={78} align="center" />
          <Headline lines={["You forgot where you were."]} x={0} y={500} width={1920} size={78} align="center" delay={26} color={C.glow} />
        </Sequence>
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ 2. Title */

export const Title: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(f, fps, 20, 30);
  return (
    <SceneFade>
      <Backdrop variant="night">
        <Owl pose="hero" x={960} y={430} size={420} />
        <Sparks x={960} y={430} at={10} radius={300} />
        <div
          style={{
            position: "absolute",
            top: 690,
            width: "100%",
            textAlign: "center",
            fontFamily: FONT,
            fontWeight: 300,
            fontSize: 132,
            letterSpacing: -4,
            color: C.cream,
            opacity: s,
            transform: `translateY(${(1 - s) * 30}px)`,
          }}
        >
          CueBack
        </div>
        <Sub text="Don't save the task. Save your place." x={0} y={855} width={1920} align="center" delay={34} size={36} color={C.glow} />
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ Generic footage scene */

export type Caption = {at: number; until: number; lines: string[]; sub?: string};
/** A push-in on part of the phone: `y` is the point to zoom to, as a fraction of the phone's height. */
export type Focus = {at: number; until: number; scale: number; y: number};
export type Tag = {at: number; until: number; text: string; x: number; y: number; flame?: boolean};

/**
 * Phone footage on the left, captions on the right. `clip` is a file in public/footage;
 * `from`/`to` are seconds in that file; `rate` speeds it up.
 */
export const Footage: React.FC<{
  clip: string;
  from: number;
  to: number;
  rate?: number;
  captions: Caption[];
  tags?: Tag[];
  step?: string;
  variant?: "dawn" | "night" | "dusk";
  phoneX?: number;
  focus?: Focus[];
  /** Blur the phone below a fraction of its height, to hide third-party content. Windows end at `until` (scene frames). */
  mask?: {until: number; below: number; sidesFrom?: number}[];
}> = ({clip, from, to, rate = 1, captions, tags = [], step, variant = "night", phoneX = 1320, focus = [], mask = []}) => {
  const activeMask = mask.find((m) => useCurrentFrame() < m.until);
  const maskBelow = activeMask?.below;
  const f = useCurrentFrame();
  // Eased push-in and pull-out around each focus window.
  let scale = 1;
  let originY = 0.5;
  for (const z of focus) {
    const k = Math.min(
      interpolate(f, [z.at, z.at + 18], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
      interpolate(f, [z.until - 18, z.until], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
    );
    const eased = k * k * (3 - 2 * k);
    if (eased > 0) {
      scale = 1 + (z.scale - 1) * eased;
      originY = z.y;
    }
  }
  return (
    <SceneFade>
      <Backdrop variant={variant}>
        <AbsoluteFill style={{transform: `scale(${scale})`, transformOrigin: `${phoneX}px ${60 + originY * 980}px`}}>
          <Phone src={`footage/${clip}.mp4`} from={from} to={to} playbackRate={rate} x={phoneX} y={540} height={980} />
          {maskBelow !== undefined ? (
            <div
              style={{
                position: "absolute",
                left: phoneX - 220,
                width: 441,
                top: 50 + maskBelow * 980,
                height: (1 - maskBelow) * 980,
                borderRadius: "0 0 44px 44px",
                backdropFilter: "blur(22px) brightness(0.55)",
                WebkitBackdropFilter: "blur(22px) brightness(0.55)",
                background: "rgba(8,5,3,0.35)",
              }}
            />
          ) : null}
          {activeMask?.sidesFrom !== undefined
            ? [0, 1].map((side) => (
                <div
                  key={side}
                  style={{
                    position: "absolute",
                    left: side === 0 ? phoneX - 220 : phoneX + 220 - 44,
                    width: 44,
                    top: 50 + activeMask.sidesFrom! * 980,
                    height: (activeMask.below - activeMask.sidesFrom!) * 980,
                    backdropFilter: "blur(16px) brightness(0.5)",
                    WebkitBackdropFilter: "blur(16px) brightness(0.5)",
                  }}
                />
              ))
            : null}
        </AbsoluteFill>
        {step ? (
          <div style={{position: "absolute", left: 120, top: 150, fontFamily: FONT, fontWeight: 500, fontSize: 24, color: C.glow, opacity: interpolate(f, [0, 12], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"})}}>
            {step}
          </div>
        ) : null}
        {captions.map((c, i) => (
          <Sequence key={i} from={c.at} durationInFrames={c.until - c.at}>
            <Headline lines={c.lines} x={120} y={330} size={64} width={720} hold={c.until - c.at - 12} />
            {c.sub ? <Sub text={c.sub} x={120} y={330 + c.lines.length * 72 + 30} width={640} hold={c.until - c.at - 12} /> : null}
          </Sequence>
        ))}
        {tags.map((t, i) => (
          <Sequence key={`t${i}`} from={t.at} durationInFrames={t.until - t.at}>
            <Chip text={t.text} x={t.x} y={t.y} flame={t.flame} hold={t.until - t.at - 10} />
          </Sequence>
        ))}
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ Time passes */

export const TimePasses: React.FC<{label: string; sub?: string}> = ({label, sub}) => {
  const f = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const angle = interpolate(f, [0, durationInFrames], [0, 540]);
  return (
    <SceneFade>
      <Backdrop variant="dusk">
        <div style={{position: "absolute", left: 960 - 170, top: 250, width: 340, height: 340, borderRadius: "50%", border: "2px solid rgba(255,224,194,0.25)"}}>
          <div
            style={{
              position: "absolute",
              left: 168,
              top: 30,
              width: 4,
              height: 140,
              background: `linear-gradient(${C.core}, ${C.flame})`,
              transformOrigin: "2px 140px",
              transform: `rotate(${angle}deg)`,
              borderRadius: 2,
              boxShadow: `0 0 18px ${C.flame}`,
            }}
          />
          <div style={{position: "absolute", left: 160, top: 160, width: 20, height: 20, borderRadius: "50%", background: C.glow}} />
        </div>
        <Headline lines={[label]} x={0} y={650} width={1920} size={64} align="center" />
        {sub ? <Sub text={sub} x={0} y={740} width={1920} align="center" /> : null}
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ How it works */

const STEPS = [
  {k: "Watch", t: "Only the apps you choose", d: "Which one is in front, and when the screen locks. Nothing on screen."},
  {k: "Notice", t: "A pause score", d: "Locking the phone, switching away and time idle add up. Past the line, the session closes."},
  {k: "Keep", t: "Your place", d: "Next step, what you had, the blocker, links. Every fact says where it came from."},
  {k: "Match", t: "The return", d: "Opening the same app again is matched to the place you left."},
  {k: "Give back", t: "Just enough", d: "A one-line cue after a short break. The full picture after a long one."},
];

export const HowItWorks: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  return (
    <SceneFade>
      <Backdrop variant="night">
        <Headline lines={["How CueBack works"]} x={120} y={110} size={60} width={1200} />
        <Sub text="All of it runs on the phone. There is no CueBack server." x={120} y={190} width={1200} delay={6} />
        {STEPS.map((s, i) => {
          const at = 20 + i * 26;
          const e = enter(f, fps, at, 22);
          const x = 120 + i * 340;
          return (
            <React.Fragment key={s.k}>
              {i > 0 ? (
                <div
                  style={{
                    position: "absolute",
                    left: x - 34,
                    top: 540,
                    width: 30 * e,
                    height: 3,
                    background: `linear-gradient(90deg, ${C.flame}, ${C.glow})`,
                    borderRadius: 2,
                  }}
                />
              ) : null}
              <div
                style={{
                  position: "absolute",
                  left: x,
                  top: 360,
                  width: 300,
                  height: 360,
                  borderRadius: 30,
                  background: C.glass,
                  border: "1px solid rgba(255,224,194,0.22)",
                  padding: 28,
                  boxSizing: "border-box",
                  opacity: e,
                  transform: `translateY(${(1 - e) * 40}px)`,
                }}
              >
                <div style={{fontFamily: FONT, fontSize: 22, fontWeight: 600, color: C.glow}}>{s.k}</div>
                <div style={{fontFamily: FONT, fontSize: 34, fontWeight: 400, color: C.cream, marginTop: 14, lineHeight: 1.1, letterSpacing: -0.6}}>{s.t}</div>
                <div style={{fontFamily: FONT, fontSize: 21, color: C.ash, marginTop: 16, lineHeight: 1.4}}>{s.d}</div>
              </div>
            </React.Fragment>
          );
        })}
        <Sequence from={170}>
          {["Detected", "Inferred", "You said"].map((t, i) => (
            <Chip key={t} text={t} x={120 + i * 200} y={800} delay={i * 6} flame={t === "You said"} />
          ))}
          <Sub text="CueBack never presents a guess as a fact." x={760} y={806} width={800} delay={20} color={C.cream} />
        </Sequence>
        <Owl pose="think" x={1690} y={150} size={170} delay={10} />
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ RevenueCat */

export const RevenueCat: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const kb1 = interpolate(f, [60, 330], [1.02, 1.12], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  const kb2 = interpolate(f, [330, 540], [1.0, 1.1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  const logo = enter(f, fps, 0, 26);
  return (
    <SceneFade>
      <Backdrop variant="night">
        <div style={{position: "absolute", left: 120, top: 110, opacity: logo, transform: `translateY(${(1 - logo) * 20}px)`}}>
          <Img src={staticFile("brand/revenuecat-logo-light.svg")} style={{height: 58}} />
        </div>
        <Headline lines={["Monetization that", "just works."]} x={120} y={230} size={64} width={640} delay={8} />
        <Sub
          text="Plans come from the RevenueCat offering. Buying unlocks the cueback_pro entitlement, which survives restarts and can be restored."
          x={120}
          y={420}
          width={600}
          delay={16}
        />
        <Sequence from={40} durationInFrames={300}>
          <Glass x={760} y={200} width={1040} height={312} delay={0} hold={288}>
            <Img src={staticFile("shots/rc_metrics.png")} style={{width: "100%", height: "100%", objectFit: "cover", transform: `scale(${kb1})`, transformOrigin: "20% 60%"}} />
          </Glass>
          <Sub text="Revenue, subscriptions and customers, live in the RevenueCat dashboard." x={760} y={540} width={1040} delay={14} size={26} color={C.cream} hold={288} />
        </Sequence>
        <Sequence from={320}>
          <Glass x={760} y={160} width={1040} height={590} delay={0}>
            <Img src={staticFile("shots/rc_tx.png")} style={{width: "100%", height: "100%", objectFit: "cover", objectPosition: "50% 0%", transform: `scale(${kb2})`, transformOrigin: "50% 10%"}} />
          </Glass>
        </Sequence>
        <Sequence from={60}>
          <Chip text="Free: 3 places, 1 app" x={120} y={600} delay={0} />
          <Chip text="Pro: unlimited, full picture" x={120} y={670} delay={8} flame />
          <Chip text="Monthly · Annual · Lifetime" x={120} y={740} delay={16} />
        </Sequence>
        <Sub text="Test Store sandbox data from our own testing, not real revenue." x={760} y={790} width={1040} delay={60} size={22} />
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ OneSignal + privacy + tech */

export const Stack: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const items = [
    ["Kotlin + Jetpack Compose", "Native Android, Material 3, the Ember design system"],
    ["On-device engine", "Pure Kotlin: segmentation, capsules, matching, recovery depth"],
    ["Room + WorkManager", "Local storage; a foreground service for returns in seconds"],
    ["RevenueCat", "Offerings, paywall, purchase, restore, entitlements"],
    ["OneSignal", "Push with privacy-safe tags: counts and times, never task content"],
    ["65 automated tests", "Engine, data, privacy and real UI flows; CI on every push"],
  ];
  return (
    <SceneFade>
      <Backdrop variant="dusk">
        <Headline lines={["Built to be trusted."]} x={120} y={110} size={60} width={1200} />
        <Sub text="Private by design: only apps you pick, nothing on screen, everything on your phone. Export or delete it all in Settings." x={120} y={195} width={1100} delay={6} />
        {items.map(([t, d], i) => {
          const e = enter(f, fps, 16 + i * 8, 20);
          const col = i % 2;
          const row = Math.floor(i / 2);
          return (
            <div
              key={t}
              style={{
                position: "absolute",
                left: 120 + col * 820,
                top: 360 + row * 170,
                width: 780,
                height: 140,
                borderRadius: 26,
                background: C.glass,
                border: "1px solid rgba(255,224,194,0.22)",
                padding: "24px 30px",
                boxSizing: "border-box",
                opacity: e,
                transform: `translateY(${(1 - e) * 30}px)`,
              }}
            >
              <div style={{fontFamily: FONT, fontSize: 32, color: C.cream, letterSpacing: -0.4}}>{t}</div>
              <div style={{fontFamily: FONT, fontSize: 22, color: C.ash, marginTop: 8}}>{d}</div>
            </div>
          );
        })}
        <Img src={staticFile("brand/revenuecat-logo-light.svg")} style={{position: "absolute", left: 1260, top: 124, height: 40, opacity: enter(f, fps, 40)}} />
        <Chip text="OneSignal" x={1640} y={116} delay={46} />
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ Use cases */

export type UseCase = {app: string; place: string; next: string; clip?: string; from?: number; to?: number};

export const UseCases: React.FC<{cases: UseCase[]}> = ({cases}) => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const per = 90;
  const i = Math.min(cases.length - 1, Math.floor(f / per));
  const local = f - i * per;
  const c = cases[i];
  return (
    <SceneFade>
      <Backdrop variant="night">
        <Headline lines={["Not just study."]} x={120} y={110} size={60} width={900} />
        <Sub text="Any app where your real work happens. Share the page, say the next step, come back to it." x={120} y={190} width={760} delay={6} />
        <Sequence from={i * per} durationInFrames={per} key={i}>
          <div style={{position: "absolute", left: 120, top: 420}}>
            <div style={{fontFamily: FONT, fontSize: 24, fontWeight: 600, color: C.glow, opacity: enter(local, fps, 0)}}>{c.app}</div>
            <div style={{fontFamily: FONT, fontSize: 52, fontWeight: 300, color: C.cream, width: 760, letterSpacing: -1, marginTop: 10, opacity: enter(local, fps, 4), lineHeight: 1.1}}>{c.place}</div>
            <div
              style={{
                marginTop: 34,
                width: 680,
                borderRadius: 26,
                background: C.glassStrong,
                border: "1px solid rgba(255,224,194,0.22)",
                overflow: "hidden",
                display: "flex",
                opacity: enter(local, fps, 10),
                transform: `translateY(${(1 - enter(local, fps, 10)) * 24}px)`,
              }}
            >
              <div style={{width: 6, background: `linear-gradient(${C.core}, ${C.flame})`}} />
              <div style={{padding: "20px 24px"}}>
                <div style={{fontFamily: FONT, fontSize: 22, fontWeight: 600, color: C.glow}}>Next</div>
                <div style={{fontFamily: FONT, fontSize: 34, color: C.cream, marginTop: 6}}>{c.next}</div>
              </div>
            </div>
          </div>
          {c.clip ? <Phone src={`footage/${c.clip}.mp4`} from={c.from} to={c.to} x={1400} y={560} height={900} /> : null}
        </Sequence>
        <div style={{position: "absolute", left: 120, bottom: 110, display: "flex", gap: 12}}>
          {cases.map((_, k) => (
            <div key={k} style={{width: k === i ? 46 : 14, height: 8, borderRadius: 4, background: k === i ? C.flame : "rgba(255,243,232,0.25)"}} />
          ))}
        </div>
      </Backdrop>
    </SceneFade>
  );
};

/* ------------------------------------------------------------------ End */

export const End: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  return (
    <SceneFade outFrames={30}>
      <Backdrop variant="night">
        <Owl pose="wave" x={960} y={360} size={360} />
        <Headline lines={["CueBack"]} x={0} y={580} width={1920} size={110} align="center" />
        <Sub text="Save your place. Come back to it." x={0} y={715} width={1920} align="center" delay={10} size={38} color={C.glow} />
        <Sub text="github.com/LSUDOKO/CueBack  ·  Open source (Apache 2.0)" x={0} y={800} width={1920} align="center" delay={20} size={28} />
        <div style={{position: "absolute", bottom: 90, width: "100%", textAlign: "center", opacity: enter(f, fps, 30)}}>
          <Img src={staticFile("brand/revenuecat-logo-light.svg")} style={{height: 34, opacity: 0.85}} />
          <div style={{fontFamily: FONT, fontSize: 22, color: C.ash, marginTop: 14}}>Built for RevenueCat Shipaton 2026 · Next Gen</div>
        </div>
      </Backdrop>
    </SceneFade>
  );
};
