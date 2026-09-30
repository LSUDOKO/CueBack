# Design system

CueBack has one look, called **Ember**: a near-black ground lit by orange light, the same light the owl's eyes and cloak give off. The app is dark-only, because both the palette and the mascot artwork are made for a dark ground.

The code lives in `app/src/main/java/com/cueback/app/ui/theme` and `ui/components`.

## Colour

| Token | Hex | Used for |
|---|---|---|
| `Ember.Coal` | `#080503` | The ground every screen sits on |
| `Ember.Flame` | `#FF6A1A` | Primary actions, selected states |
| `Ember.Glow` | `#FFA24A` | Accents, links, the "Next" ribbon |
| `Ember.Core` | `#FFD699` | The hottest highlights only |
| `Ember.Rust` / `Ember.Lava` | `#B8360A` / `#8E1A08` | Depth in the backdrop light |
| `Ember.Cream` | `#FFF3E8` | Text |
| `Ember.Ash` | `#CDB8A9` | Secondary text |
| `Ember.Ink` | `#1B0900` | Text and icons on flame-coloured surfaces |
| `Ember.Glass` | `#1A0B04` at 56% | Panels ("smoked glass") |

Text on flame is always `Ink`, never white: white on orange fails contrast.

## Type

One family, **Inter Tight** (variable, SIL Open Font License, bundled in `res/font`). Large statements are set light (300) with tight tracking; labels and buttons are medium to semibold. The scale is defined once in `Theme.kt`.

## Backdrop

Every screen sits on `EmberBackdrop`, which has three lighting set-ups:

| Variant | Light | Screens |
|---|---|---|
| `Dawn` | Orange pouring from the top | Home |
| `Night` | Black, with ember light at the top and both edges | Welcome back, onboarding, paywall |
| `Dusk` | Night turned down | Reading screens: settings, library, details, capture |

A tiled film grain sits over the gradients so they don't band. The light drifts slowly.

## Components

| Component | What it is |
|---|---|
| `Modifier.glass` / `GlassCard` | Translucent panel with a hairline that catches light along the top edge |
| `EmberButton` | The one primary action on a screen: a flame pill with dark ink |
| `GlassButton` | Secondary action |
| `OrbButton` | The glowing round button on home |
| `EmberChip` | A choice pill; flame when selected |
| `GlassTextField` | Text input |
| `RibbonNext` | The bookmark ribbon that marks the exact next action |
| `FactBlock` | A fact laid out as a line in a conversation: what you said on the right in flame, what Cue detected on the left next to the owl. A tag still says which, in words |
| `EmberScaffold` | Frame for every screen below home: backdrop, glass back button, title |

## Cue, the mascot

Cue is a hooded owl with a padlock emblem: it keeps your place, and keeps it private. The artwork is seven renders of one character (`res/drawable-nodpi/mascot_*.webp`), cut out from their backgrounds.

| Pose | Where it appears |
|---|---|
| Hero (floating, cloak flying) | First onboarding page, paywall |
| Front | Coach lines (`MascotSays`) |
| Turn, Side | Looking toward something: setup, app picker, a missing context |
| Wave | Welcome back, the re-entry result, a suggestion on home |
| Think | Loading, capture, choosing a use case, empty search |
| Head | Avatars: the home header and Cue's chat lines |

`Mascot` brings a pose to life without a 3D model:

- it hovers and breathes, and its halo pulses;
- the light it casts on the ground tightens and dims as it rises;
- now and then it glances aside, by turning through the three-quarter render;
- it leans with the way the phone is held, and its halo slides the other way, which reads as depth;
- tapped, it hops and waves.

## Motion

- Screens rise slightly as they fade in (one transition, app-wide).
- The welcome-back card reveals its sections in order, once.
- The only celebration is on the re-entry result: a ring and a scatter of sparks leave the owl once.
- Buttons compress slightly when pressed.
- With the system's animations turned off, everything is still: no idle motion, no transitions.

## Rules

1. One primary (`EmberButton`) action per screen.
2. Never signal provenance by colour or position alone; the tag says it in words.
3. Cue speaks in the first person and in one short sentence.
4. New surfaces are glass on the backdrop, not opaque cards.
