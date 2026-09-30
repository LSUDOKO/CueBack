import React, {useEffect, useState} from "react";
import {
  AbsoluteFill,
  Img,
  OffthreadVideo,
  continueRender,
  delayRender,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

/** The Ember palette, the same tokens the app uses. */
export const C = {
  coal: "#080503",
  soot: "#160B06",
  flame: "#FF6A1A",
  glow: "#FFA24A",
  core: "#FFD699",
  rust: "#B8360A",
  lava: "#8E1A08",
  cream: "#FFF3E8",
  ash: "#CDB8A9",
  ink: "#1B0900",
  glass: "rgba(26,11,4,0.56)",
  glassStrong: "rgba(20,9,3,0.78)",
};

export const FONT = "InterTight";

/** Loads the app's own typeface once per render process. */
export const useFont = () => {
  const [handle] = useState(() => delayRender("font"));
  useEffect(() => {
    const face = new FontFace(FONT, `url(${staticFile("fonts/inter_tight.ttf")})`);
    face
      .load()
      .then((f) => {
        document.fonts.add(f);
        continueRender(handle);
      })
      .catch(() => continueRender(handle));
  }, [handle]);
};

const GRAIN =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(
    `<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'><filter id='g'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1  0 0 0 0 0.95  0 0 0 0 0.9  0 0 0 0.16 0'/></filter><rect width='100%' height='100%' filter='url(#g)'/></svg>`,
  );

/** The lit ground every scene sits on. Dawn pours orange from the top; Night keeps it to the edges. */
export const Backdrop: React.FC<{variant?: "dawn" | "night" | "dusk"; children?: React.ReactNode}> = ({
  variant = "night",
  children,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const d = 0.5 + 0.5 * Math.sin((frame / fps) * 0.35);
  const strength = variant === "dusk" ? 0.55 : 1;
  const layers =
    variant === "dawn"
      ? [
          `linear-gradient(180deg, #E96F12 0%, #D8590C 15%, #9A3307 34%, #3D1505 52%, ${C.coal} 70%)`,
          `radial-gradient(60% 55% at ${60 + 24 * d}% ${3 + 5 * d}%, rgba(255,194,122,0.55), rgba(255,194,122,0))`,
          `radial-gradient(70% 60% at 50% 112%, rgba(179,32,14,0.42), rgba(179,32,14,0))`,
        ]
      : [
          `radial-gradient(75% 55% at ${50 + 14 * (d - 0.5)}% -7%, rgba(240,120,15,${0.95 * strength}), rgba(184,54,10,${0.62 * strength}) 45%, rgba(184,54,10,0) 100%)`,
          `radial-gradient(55% 60% at -22% ${40 + 7 * d}%, rgba(232,89,12,${0.52 * strength}), rgba(232,89,12,0))`,
          `radial-gradient(55% 60% at 122% ${52 - 7 * d}%, rgba(232,89,12,${0.52 * strength}), rgba(232,89,12,0))`,
          `radial-gradient(70% 60% at 50% 115%, rgba(179,32,14,${0.34 * strength}), rgba(179,32,14,0))`,
        ];
  return (
    <AbsoluteFill style={{backgroundColor: C.coal}}>
      <AbsoluteFill style={{backgroundImage: layers.join(","), backgroundColor: C.coal}} />
      <AbsoluteFill style={{backgroundImage: `url("${GRAIN}")`, opacity: 0.5, mixBlendMode: "screen"}} />
      {children}
    </AbsoluteFill>
  );
};

/** Ease used for every entrance: quick, then settles. */
export const enter = (frame: number, fps: number, delay = 0, durationInFrames = 22) =>
  spring({frame: frame - delay, fps, config: {damping: 18, stiffness: 120, mass: 0.8}, durationInFrames});

/** A phone with real footage inside. The clip is cut with `from`/`to` in seconds of the source file. */
export const Phone: React.FC<{
  src: string;
  from?: number;
  to?: number;
  x?: number;
  y?: number;
  height?: number;
  blur?: number;
  playbackRate?: number;
  appear?: number;
  tilt?: number;
  volume?: number;
}> = ({src, from = 0, to, x = 960, y = 540, height = 960, blur = 0, playbackRate = 1, appear = 0, tilt = 0}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(frame, fps, appear, 26);
  const width = height * (720 / 1600);
  const radius = height * 0.045;
  return (
    <div
      style={{
        position: "absolute",
        left: x - width / 2,
        top: y - height / 2,
        width,
        height,
        borderRadius: radius,
        overflow: "hidden",
        background: "#000",
        boxShadow: `0 40px 120px rgba(0,0,0,0.6), 0 0 0 3px rgba(255,243,232,0.12), 0 0 90px rgba(255,106,26,0.18)`,
        transform: `translateY(${(1 - s) * 60}px) scale(${0.94 + 0.06 * s}) rotate(${tilt}deg)`,
        opacity: Math.min(1, s * 1.4),
        filter: blur ? `blur(${blur}px)` : undefined,
      }}
    >
      <OffthreadVideo
        src={staticFile(src)}
        startFrom={Math.round(from * fps)}
        endAt={to === undefined ? undefined : Math.round(to * fps)}
        playbackRate={playbackRate}
        muted
        style={{width: "100%", height: "100%", objectFit: "cover"}}
      />
    </div>
  );
};

/** Large statement type, light weight, tight tracking. Lines arrive one after another. */
export const Headline: React.FC<{
  lines: string[];
  x?: number;
  y?: number;
  size?: number;
  width?: number;
  align?: "left" | "center";
  delay?: number;
  color?: string;
  weight?: number;
  hold?: number;
}> = ({lines, x = 120, y = 300, size = 64, width = 700, align = "left", delay = 0, color = C.cream, weight = 300, hold}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const out = hold === undefined ? 1 : interpolate(frame, [hold, hold + 12], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return (
    <div style={{position: "absolute", left: x, top: y, width, textAlign: align, opacity: out}}>
      {lines.map((line, i) => {
        const s = enter(frame, fps, delay + i * 5);
        return (
          <div
            key={i}
            style={{
              fontFamily: FONT,
              fontWeight: weight,
              fontSize: size,
              lineHeight: 1.08,
              letterSpacing: -size * 0.024,
              color,
              opacity: s,
              transform: `translateY(${(1 - s) * 28}px)`,
            }}
          >
            {line}
          </div>
        );
      })}
    </div>
  );
};

/** Secondary copy under a headline. */
export const Sub: React.FC<{text: string; x?: number; y?: number; width?: number; delay?: number; size?: number; align?: "left" | "center"; color?: string; hold?: number}> = ({
  text,
  x = 120,
  y = 480,
  width = 680,
  delay = 8,
  size = 30,
  align = "left",
  color = C.ash,
  hold,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(frame, fps, delay);
  const out = hold === undefined ? 1 : interpolate(frame, [hold, hold + 12], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        width,
        textAlign: align,
        fontFamily: FONT,
        fontWeight: 400,
        fontSize: size,
        lineHeight: 1.35,
        color,
        opacity: s * out,
        transform: `translateY(${(1 - s) * 18}px)`,
      }}
    >
      {text}
    </div>
  );
};

/** A small glass label, used to name what's on screen. */
export const Chip: React.FC<{text: string; x: number; y: number; delay?: number; flame?: boolean; hold?: number}> = ({text, x, y, delay = 0, flame = false, hold}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(frame, fps, delay, 18);
  const out = hold === undefined ? 1 : interpolate(frame, [hold, hold + 10], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        padding: "12px 22px",
        borderRadius: 999,
        background: flame ? `linear-gradient(180deg, ${C.glow}, ${C.flame})` : C.glass,
        border: `1px solid ${flame ? "rgba(255,232,196,0.5)" : "rgba(255,224,194,0.22)"}`,
        color: flame ? C.ink : C.cream,
        fontFamily: FONT,
        fontWeight: 500,
        fontSize: 24,
        letterSpacing: -0.2,
        opacity: s * out,
        transform: `scale(${0.9 + 0.1 * s})`,
        whiteSpace: "nowrap",
      }}
    >
      {text}
    </div>
  );
};

/** Cue, alive: hovering, breathing, with a pulsing halo and the light pooled beneath. */
export const Owl: React.FC<{pose?: string; x: number; y: number; size?: number; delay?: number; flip?: boolean}> = ({
  pose = "hero",
  x,
  y,
  size = 300,
  delay = 0,
  flip = false,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(frame, fps, delay, 30);
  const t = frame / fps;
  const bob = Math.sin(t * 2.4);
  const pulse = 0.5 + 0.5 * Math.sin(t * 3.3);
  return (
    <div style={{position: "absolute", left: x - size / 2, top: y - size / 2, width: size, height: size, opacity: s}}>
      <div
        style={{
          position: "absolute",
          inset: -size * 0.3,
          borderRadius: "50%",
          background: `radial-gradient(circle, rgba(255,106,26,${0.42 + 0.16 * pulse}) 0%, rgba(184,54,10,0.18) 50%, rgba(184,54,10,0) 82%)`,
        }}
      />
      <div
        style={{
          position: "absolute",
          left: size * 0.1,
          right: size * 0.1,
          top: size * 0.9,
          height: size * 0.16,
          borderRadius: "50%",
          background: `radial-gradient(ellipse, rgba(255,162,74,${0.5 - 0.14 * bob}) 0%, rgba(255,162,74,0) 70%)`,
          transform: `scaleX(${1 - 0.1 * bob})`,
        }}
      />
      <Img
        src={staticFile(`owl/mascot_${pose}.webp`)}
        style={{
          position: "absolute",
          inset: 0,
          width: "100%",
          height: "100%",
          transform: `translateY(${bob * size * 0.03 + (1 - s) * 40}px) rotate(${bob * 1.3}deg) scale(${(1 + 0.014 * pulse) * (0.9 + 0.1 * s)}) scaleX(${flip ? -1 : 1})`,
        }}
      />
    </div>
  );
};

/** A row of sparks leaving a point once: the app's only celebration, reused here. */
export const Sparks: React.FC<{x: number; y: number; at: number; radius?: number}> = ({x, y, at, radius = 220}) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [at, at + 34], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  if (p <= 0 || p >= 1) return null;
  return (
    <div style={{position: "absolute", left: x, top: y}}>
      {Array.from({length: 16}).map((_, i) => {
        const a = (i / 16) * Math.PI * 2 + 0.3;
        const r = radius * (0.4 + 0.8 * p) * (i % 2 ? 0.82 : 1);
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: Math.cos(a) * r,
              top: Math.sin(a) * r,
              width: 9 * (1 - p) + 2,
              height: 9 * (1 - p) + 2,
              borderRadius: "50%",
              background: i % 3 ? C.glow : C.core,
              opacity: 1 - p,
            }}
          />
        );
      })}
      <div
        style={{
          position: "absolute",
          left: -radius * (0.45 + 0.65 * p),
          top: -radius * (0.45 + 0.65 * p),
          width: radius * 2 * (0.45 + 0.65 * p),
          height: radius * 2 * (0.45 + 0.65 * p),
          borderRadius: "50%",
          border: `2px solid rgba(255,106,26,${0.7 * (1 - p)})`,
        }}
      />
    </div>
  );
};

/** Glass panel for dashboard shots and logos. */
export const Glass: React.FC<{x: number; y: number; width: number; height: number; delay?: number; children?: React.ReactNode; radius?: number; hold?: number}> = ({
  x,
  y,
  width,
  height,
  delay = 0,
  children,
  radius = 28,
  hold,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const s = enter(frame, fps, delay, 26);
  const out = hold === undefined ? 1 : interpolate(frame, [hold, hold + 12], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        width,
        height,
        borderRadius: radius,
        background: C.glassStrong,
        border: "1px solid rgba(255,224,194,0.22)",
        boxShadow: "0 30px 80px rgba(0,0,0,0.5)",
        overflow: "hidden",
        opacity: s * out,
        transform: `translateY(${(1 - s) * 40}px)`,
      }}
    >
      {children}
    </div>
  );
};

/** Fade the whole scene in and out at its edges. */
export const SceneFade: React.FC<{children: React.ReactNode; inFrames?: number; outFrames?: number}> = ({children, inFrames = 10, outFrames = 12}) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const o = Math.min(
    interpolate(frame, [0, inFrames], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
    interpolate(frame, [durationInFrames - outFrames, durationInFrames], [1, 0], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}),
  );
  return <AbsoluteFill style={{opacity: o}}>{children}</AbsoluteFill>;
};
