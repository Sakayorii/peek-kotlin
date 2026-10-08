# peek-kotlin

Kotlin port of [peek-vanilla](https://github.com/Sakayorii/peek-vanilla) — itself a
verified 1:1 port of [Peek](https://github.com/doan-labs/peek) by Doan Labs (MIT).
A name in, a face out: deterministic avatars, no network, no database.

Part of the peek family: `peek` (original, React) · `peek-vanilla` (JS) ·
`peek-kotlin` (JVM) · `peek-rust` (Rust).

```
peek/
├── src/main/kotlin/com/sakayori/peek/
│   ├── Js.kt        — JavaScript semantics, exactly (number printing, tidy, JSON)
│   ├── Identity.kt  — tidy / FNV-1a / mulberry32 / identify  (port of identity.js)
│   ├── Tables.kt    — faces, colors, parts, expressions        (port of tables.js)
│   ├── Scene.kt     — node tree model + prune                 (port of draw.js helpers)
│   ├── Draw.kt      — the geometry: draw()                    (port of draw.js)
│   └── Svg.kt       — settle / serialize / toSvg              (port of svg.js)
├── src/test/kotlin/com/sakayori/peek/
│   ├── PeekGoldenTest.kt  — byte-identical golden tests (JUnit4)
│   └── CorpusData.kt      — generated golden corpus (do not edit)
├── gen-corpus.mjs   — regenerates CorpusData.kt from peek-vanilla
└── verify.sh        — compiles with kotlinc and runs the tests (no Gradle needed)
```

## The 100% rule

Every behavior of the original that affects output is reproduced, including the
boring parts: JS `String(number)` formatting, `Math.round` ties, NFC
normalization, locale-independent lowercasing, and `JSON.stringify` key order
for the deterministic SVG ids.

Proof is mechanical, not vibes: `./verify.sh` compiles the port and diffs
`toSvg()` against peek-vanilla's output for 445 cases (curated names with
unicode/emoji/whitespace edge cases, every expression, every part override,
sizes, frames, riso, titles, plus 300 seeded fuzz names). One byte off fails.

```bash
./verify.sh          # compile + run golden tests
./verify.sh regen    # regenerate corpus from peek-vanilla first, then test
```

Run tests: `./gradlew test` (regenerates the corpus automatically), or `./verify.sh`
for a Gradle-free run with a local kotlinc.

## API

```kotlin
import com.sakayori.peek.*

// name in, SVG string out — same bytes as peek-vanilla for the same input
val svg: String = toSvg("Sakayori")
val svg2: String = toSvg("Sakayori", PeekOptions(expression = "happy", square = false))

// identity without drawing
val who: Identity = identify("Sakayori") // face, color, eyes, brows, mouth, cheeks, trait, persona
```

`PeekOptions` mirrors peek-vanilla's `toSvg` props: `face`, `color`, `eyes`,
`brows`, `mouth`, `cheeks`, `trait` (axis overrides win over the hash),
`expression` (11 states), `gaze`, `size`, `frame` (`ink`/`bone`/`paper`/`none`),
`square`, `riso`, `id`, `title`/`hideTitle`, `version`.

## Notes for the app integration

- The core is pure Kotlin/JVM with zero dependencies — safe to call from anywhere.
- `toSvg()` for a 64px avatar takes well under a millisecond; regenerating per
  keystroke (the listen-together name field) is cheap.
- The animated rig (`animate.js` → Compose) is a separate phase; this module is
  the deterministic engine it will drive.
