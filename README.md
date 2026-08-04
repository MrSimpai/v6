# Le dragon de Flo 🐉

A medication tracker for Flo. Two ways to log a dose, and the app is complete with
either one:

- **Tick it off** — a "Je l'ai prise" button on the row. Works out of the box, with no
  hardware at all, and doubles as the escape hatch for when she's away from the bottle.
- **Tap the bottle** — hold the phone to an NFC sticker. No "did I already press taken?"
  ambiguity, because you can't tap the tag without being at the bottle.

Adding a medication does *not* require a tag. The primary button in the sheet saves
immediately with a locally generated `MANUAL-…` key; linking a sticker is the secondary
option, and can be done later. Everything downstream — slots, reminders, streaks, charts,
the dragon's mood — is identical either way, so the app can be installed and lived with
before a single sticker exists. `uses-feature nfc` is `required="false"`, so it also
installs on a phone or emulator with no NFC radio.

A small raspberry dragon supervises. **The entire app is in French** — every screen,
every notification, `Locale.CANADA_FRENCH` for dates and times (`9h30`, `mardi 3 août`).
The register is everyday familiar French, not heavy joual: `tu` throughout, contractions
where they fall naturally, but no `pis` / `icitte` / `tsé` / `un boutte`. Strings are
inline in the Kotlin rather than in `strings.xml`, since there's only one language.

## Everything of hers is in one file

`Personal.kt` holds `object Her`: her name, her real name, the dragon's name, the pet
names, the time-of-day greeting, and the line at the bottom of the home screen. Nothing
else in the codebase knows who this is for — every screen and every notification reads
from there. Change those few lines and the whole app re-addresses itself.

The one exception is `strings.xml`, which carries only the launcher label: Android reads
it before any Kotlin runs.

## The dragon's escalation ladder

The Duolingo trick isn't the jokes — it's the escalation, and the fact that the mascot
eventually *stops* joking. Every 10 minutes the reminder re-posts with a new line, one
rung further up:

| Retard | Palier | Ton | Exemple |
|---|---|---|---|
| 0–10 min | `ALHEURE` | joyeux | *"Ding 🐉 — Framboise réclame son dû. Une (1) pilule."* |
| 10–30 | `RELANCE` | insistant | *"Hé. Hé Flo. Hé. — Une. Petite. Dose."* |
| 30–60 | `BOUDE` | passif-agressif | *"C'est correct. — Tout est correct. Je vais bien. La pilule va bien, toute seule, là-bas."* |
| 60–120 | `CULPABILITE` | peine d'amour théâtrale | *"Ça ne marche pas, ces rappels — Je blague, je n'arrêterai jamais. Prends ta dose, Flo."* |
| 120+ | `SERIEUX` | neutre, aucun surnom | *"Florie — ta médication. La dose d'aujourd'hui a plus de deux heures de retard…"* |

That last tier is the point of the first four. Nicknames, emoji and bits all disappear,
the banner goes dark plum, and the dragon uses her actual name — `Her.realName`, which
appears nowhere else in the app. If everything were equally silly, nothing would register
as urgent. Edit any of it in `FloMessages.kt` — it's just five lists of strings.

The register shift is doing as much work as the wording: the first four tiers are
familiar and contracted, `SERIEUX` is plain, unhurried standard French.

## What makes the notification feel like Duolingo's

- **A drawn banner, not text.** `BigPictureStyle` shows a 1024×512 panel generated on the
  fly: dragon on the left, message set in large type on the right, scale-pattern
  background. This is the single biggest reason Duo's reminders read as *a character
  talking to you* rather than a system message. See `reminder/NotifArt.kt`.
- **The dragon's face as the large icon**, cropped round, matching her current mood.
- **Colorized** notification tinted to the dragon's pink (plum in the serious tier).
- **It does not go away.** See below.
- **A new line every re-post**, seeded on the slot so a dismissal doesn't reshuffle it.

## The app icon changes with her mood

Android can't repaint a launcher icon at runtime, but it *can* swap which
`<activity-alias>` is the enabled launcher entry. Four aliases, exactly one enabled:

| State | Icon |
|---|---|
| nothing due | teal, eyes closed, sleeping |
| dose owed | apricot, wide eyes, holding a pill |
| 2h+ overdue | terracotta, lowered brows, frowning |
| just logged | mint, happy arcs, sparkles |

The backgrounds are picked for contrast as much as for mood. An earlier draft put the
overdue dragon on dark plum, which looked appropriately grim and also made a crimson
dragon almost invisible at launcher size; terracotta keeps her readable while still
reading as the alarming one of the four.

Caveats: some launchers briefly drop the icon during a swap and a few reset its home
screen position, so `IconSwitcher` bails out early if the desired alias is already
enabled. All state lives in Room and AlarmManager, so an alias-triggered restart is
harmless.

## One drawing, three places

`ui/Dragon.kt` draws her against a plain `android.graphics.Canvas`. Compose renders it via
`drawIntoCanvas { it.nativeCanvas }`, the notification renders it to a `Bitmap`. The
on-screen dragon and the notification dragon are the same code and can't drift apart.
Five moods: `Sleeping`, `Waiting`, `Overdue`, `Cheering`, `Sad`. Zero image assets — she's
all vectors, so she scales to any density and adds nothing to the APK.

**The current drawing follows the reference art.** Palette straight off it: `#C03765`
body, `#A21E50` horns and jaw frills, `#A83063` wing membrane, `#7A1B45` feet, `#F1BBCB`
belly. Four horns — a tall near-vertical pair and a wide-swept outer pair — plus three
frill spikes along each jaw, spread bat wings with three scallops and three finger bones,
a pear-shaped seated body, paws held together with claw grooves, and a thick tail
sweeping out to the left. The horns sit high and clearly separated from the head, so
nothing reads as ears.

The snout deliberately avoids creating a second face: it is the same crimson as the head
(a bump with a small warm patch and two nostril dots, not a contrasting pale muzzle), so
the eye can't parse it as its own oval-head-plus-features.

The launcher drawables in `res/drawable/ic_dragon_*.xml` are **generated from this same
geometry** rather than drawn by hand, which is why the home-screen icon and the on-screen
dragon can't drift apart.

---

## Getting the APK

There's no `gradlew` in this repo (I couldn't generate the wrapper JAR), so CI installs
Gradle directly. Two routes:

### Route A — GitHub builds it for you (no Android Studio)

1. Create a new GitHub repo, push these files to `main`.
2. Go to the **Actions** tab. The build runs automatically.
3. When it's green, go to the repo's **Releases** page (right-hand sidebar).
4. The latest release has exactly one asset: `MedTap-Flo.apk`.

That first build is a **debug** APK, which installs fine and is perfect for testing.

**Send Flo the Release link, not the Actions link.** Artifacts on the Actions tab
download as a `.zip`, which is close to unusable on a phone. A Release asset is a direct
`.apk` URL — she taps it and Android's installer opens. Same link every time, and
`make_latest` keeps it pointing at the newest build.

The workflow deliberately fails if the build produces more than one APK, rather than
picking one at random. ABI splits are disabled in `app/build.gradle.kts` (there's no
native code here, so a universal APK costs nothing), and only one variant is ever
assembled — release when a keystore secret exists, debug otherwise. One file, one name.

### Route B — signed release (do this before giving it to Flo properly)

Debug APKs are signed with a throwaway key, so you can't ship updates on top of one.
For a real build, generate a keystore once:

```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias medtap
base64 -w0 keystore.jks    # copy this whole string
```

Then in the repo: **Settings → Secrets and variables → Actions → New repository secret**,
add four:

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | the base64 blob above |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `medtap` |
| `KEY_PASSWORD` | the key password you chose |

Push again and the workflow produces a signed release APK. **Back up `keystore.jks`** —
lose it and Flo has to uninstall (losing her history) to take a future update.

### Installing it on her phone

Send her the Release link (or put the APK on Google Drive). Don't use WhatsApp or Gmail —
both block `.apk` attachments outright. She'll tap through "this file can harm your device" →
**Download anyway**, then **Allow from this source** in settings, then possibly a Play
Protect "**Install anyway**". Warn her those warnings are expected; they're alarming if
you don't know they're coming. (Play internal testing avoids all of that, but costs $25
and needs a review — worth it if this becomes permanent.)

## Hardware (optional)

Only needed if she wants the tap-the-bottle flow; the app is complete without it.

NTAG213 stickers, ~$0.15 each in packs of 50. Thin enough to sit under a bottle label.
Not on metal tins — the coil detunes; buy the ferrite-backed "on-metal" variant if you
must. No writing to tags needed: the app matches on each tag's permanent factory UID.

## The chart

The history view plots **when** she took each dose against when it was due. A count of
doses per week only says yes/no; the drift plot shows a 9am dose creeping toward 11am
over two weeks, which is the pattern that comes *before* a missed one. Days with no log
sit on the target line as hollow rings.

## Still to add

- Multiple daily slots per medication (the schema holds one time per med today)
- Linking a sticker to a medication that was created without one
- Editing or deleting a medication
- CSV export for appointments
- A snooze button — deliberately absent, since it undoes the whole idea

---

Personal adherence tracker, not a medical device. If a dose is genuinely critical, keep a
backup reminder that doesn't depend on one phone's battery.
