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

- **A drawn banner, not text.** `BigPictureStyle` shows a panel generated on the fly:
  dragon on the left, the message in a comic speech bubble on the right, over a painted
  scene on the same `NotifArt.Vibe` ladder as the widget — blue when it's just time,
  violet at ten minutes, magenta and rain at thirty, embers on crimson past two hours.
  The scene climbs with the wording because both read `Tier`. This is the single biggest
  reason Duo's reminders read as *a character talking to you* rather than a system
  message. See `reminder/NotifArt.kt`.
- **Nothing is ever cut off.** The banner is 1024 wide and grows in height to fit the
  message, up to the point Android starts cropping big pictures; only past that does the
  type step down a size. The earlier version was a fixed 1024×512 that silently clipped
  the last lines of the longer messages, which is exactly the ones that mattered.
- **The text sits in a white bubble, not on the art.** The scene runs from midnight blue
  to bright daylight depending on the tier, and no single ink colour is legible on both.
  The bubble stays white for exactly that reason, but takes a hairline border in the
  current vibe's colour so it isn't the same white rectangle at every hour of the day.
- **A prop in the dragon's hand**, chosen by the same vibe: a capsule while the dose is
  waiting, a sweat-drop once it's late, hearts when it's logged.
- **The dragon's face as the large icon**, cropped round, matching her current mood.
- **Colorized** notification tinted to the dragon's pink (plum in the serious tier).
- **It does not go away.** Ongoing, non-swipeable, with a `deleteIntent` that re-posts it.

What it deliberately does *not* do is take over the screen. An earlier version set
`showWhenLocked` + `turnScreenOn` on the activity and gave the reminder a full-screen
intent, so it could wake the lock screen. Two problems. `turnScreenOn` applies every time
the activity resumes, not just when a reminder fires -- so pressing the power button
while the app was open turned the screen straight back on, in a loop. And a
high-importance notification that bypasses Do Not Disturb and re-posts every ten minutes
is already impossible to ignore; seizing the display on top of that was hostile, not
persistent. It also meant shoving her into a Settings screen on first launch to grant
`USE_FULL_SCREEN_INTENT`. All of it is gone.
- **A new line every re-post**, seeded on the slot so a dismissal doesn't reshuffle it.

## The app icon changes with her mood

Android can't repaint a launcher icon at runtime, but it *can* swap which
`<activity-alias>` is the enabled launcher entry. Exactly one is enabled at a time, and
which one is chosen by `NotifArt.Vibe` -- **the same ladder that colours the widget and
the notification banner**. Icon, tile and reminder are always the same colour:

| Vibe | Alias | Icon |
|---|---|---|
| `REST_DAY` / `REST_NIGHT` / `WIN` | `.Calm` | green, eyes closed, sleeping |
| `DUE` / `NUDGE` | `.Waiting` | blue, wide eyes, holding a pill |
| `SULK` / `DRAMA` | `.Late` | magenta, frowning |
| `ANGRY` | `.Overdue` | terracotta, lowered brows |

The backgrounds are **drawings, not flat colours** (`ic_bg_*.xml`): a diagonal gradient, a
soft halo behind the dragon's head, three sparkles. A solid fill can only ever be a solid
fill, and it sat on her home screen all day looking like exactly that. Nothing finer than a
sparkle goes in — some launchers render the icon at 48dp, and any smaller detail turns to
grime at that size.

### The icon can't wear the outfit — the shortcut can

Worth stating plainly, because it looks like an oversight: Android only knows icons that
were declared at **compile time**. Swapping between a few prepared ones is possible, which
is what `IconSwitcher` does for mood. Handing the launcher an image generated at runtime is
not — there's no public API for it, and with 55 pieces you'd need a drawing per
combination, which runs to millions.

A **dynamic shortcut** does accept a bitmap. So `DragonShortcut` pushes one, drawn by the
same `Dragon` renderer as the app and the widget, wearing whatever she currently has on:
long-press the icon and there she is, hat and plush included. It opens the locker rather
than logging a dose — logging belongs in the app, where the confirmation, the celebration
and the chest are.

Four steps, not eight, because every swap costs a launcher redraw -- the icon tracks
*whether there's something to do and roughly how long it's been*, not every shade the
widget goes through. The icon backgrounds in `ic_colors.xml` are a notch lighter than
the matching `NotifArt.skin` values: an icon is a quarter the size of a tile, and the
darkest scene colours read as a black smudge at that scale.

There is deliberately **no** "just logged" icon, though `ic_launcher_happy` is still in
the tree. It was a four-second state on a surface she isn't looking at -- she's inside
the app -- and it cost two alias swaps in four seconds. See below for why that matters.

The backgrounds are picked for contrast as much as for mood. An earlier draft put the
overdue dragon on dark plum, which looked appropriately grim and also made a crimson
dragon almost invisible at launcher size; terracotta keeps her readable while still
reading as the alarming one of the four.

**The swap can kill the app.** `setComponentEnabledSetting` makes the launcher re-query
the package, and plenty of OEM launchers force-stop the app when that happens --
`DONT_KILL_APP` is a request, not a guarantee. Fire it while she's looking at the screen
and the app vanishes under her, which reads as the phone crashing.

So `apply()` is only ever called from `ReminderReceiver`. Nothing in the UI touches it.
When a dose is logged in the app, `Reminders` schedules an `ACTION_ICON` alarm a minute
out instead of repainting on the spot.

The minute is the point. Doing it in `onStop()` seemed right -- she's leaving, nothing on
screen -- but that's the exact moment the launcher is drawing itself, so the redraw landed
in full view and looked like the phone restarting. A minute later she's moved on and the
home screen is idle, where a brief icon flicker is unremarkable. The alarm is `RTC`, not
`RTC_WAKEUP`: waking a sleeping phone to repaint an icon would be absurd.

Lesser caveats: some launchers briefly drop the icon during a swap and a few reset its
home screen position, so `apply` bails out early if the desired alias is already enabled.
All state lives in Room and AlarmManager, so a restart is otherwise harmless.

## One drawing, three places

`ui/Dragon.kt` draws her against a plain `android.graphics.Canvas`. Compose renders it via
`drawIntoCanvas { it.nativeCanvas }`, the notification renders it to a `Bitmap`. The
on-screen dragon and the notification dragon are the same code and can't drift apart.
Nine moods, zero image assets — she's all vectors, so she scales to any density and adds
nothing to the APK.

**The five tiers now have five faces.** `Tier` used to map three of its five rungs onto the
same expression, so the words escalated and the face didn't, which escalates by half. The
arc is the one an actual sulk follows:

| Tier | Face |
|---|---|
| `PONCTUEL` | `Waiting` — neutral, holding the pill |
| `RELANCE` | `Pleading` — huge shining eyes, roof eyebrows |
| `BOUDERIE` | `Sulking` — half-lidded, looking away, storm cloud overhead |
| `DRAME` | `Sad` — crying |
| `SERIEUX` | `Overdue` — angry, done joking |

`Love` (heart eyes) and `Proud` (closed eyes, chin up, gold sparkles) sit outside the
ladder: `Love` while the locker's fitting room is open, `Proud` when nothing is due and the
streak is a week or better.

### Tears, not weather

The crying face used to scatter eight droplets between x=55 and x=165 — around the body,
touching neither the eyes nor the cheeks. Floating in space like that, it read as a dragon
caught in the rain rather than a dragon crying.

What makes a tear is **contact**. It now runs from the lower lid down the cheek and stops
at the jaw, with a single white highlight along it for wetness, and one detached droplet
below — after the streak, never instead of it, or you're back to weather.

### The clock has to be state, or the dragon freezes

`HomeScreen` derives her mood from the current time, and the current time is not something
Compose observes. A plain `System.currentTimeMillis()` at the top of the composable is read
once and then frozen, and since nothing recomposes until the database changes, her face
stopped updating: a dose came due, the lateness crossed an hour, and she kept whatever
expression she had when the screen was first composed — until the app was closed and
reopened, which built a fresh composition. That was the bug, and it looked exactly like
"the mood only updates on restart".

So `now` is `mutableStateOf`, ticked every 20 seconds inside
`repeatOnLifecycle(RESUMED)`. That scope matters twice over: nothing ticks while the
screen isn't in front of anyone, and the value is refreshed the instant it comes back, so
there's no stale dragon for the first twenty seconds after unlocking.

Everything time-dependent on that screen reads the same `now` — `todayAt`, `canLogNow`,
the date header. Passing it explicitly rather than letting each call default to
`System.currentTimeMillis()` means the whole screen agrees on what time it is, instead of
each part depending on the accident of when it was last recomposed.

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

## Streaks

Two different numbers, in two different places.

The **notification** that confirms a dose shows that medication's own run of consecutive
days. The **app** shows a card only when the last dose of the day lands -- counting whole
days where nothing at all was missed. That second number is the one worth being proud of,
and it's why it waits for the day to actually be finished: a 9pm pill still outstanding
means the day isn't done, however early it is.

Both walk backwards with `Calendar.add(DAY_OF_YEAR, -1)` rather than subtracting
86_400_000 ms. Slots are stored as local wall-clock times, and on the two DST changeovers
a day is 23 or 25 hours long -- millisecond arithmetic lands an hour off, finds no log,
and silently resets a streak of any length to one. Twice a year, invisibly.

### A day is only judged on the medications that existed then

Every day is scored against `meds.dueOn(dayStart)` -- the medications whose `createdAt`
falls on or before that day -- not against every currently-active medication.

Without that filter, **adding a second medication wiped the streak and emptied the week**.
The new one had no dose logged for yesterday, so yesterday stopped being a complete day,
so everything before it became unreachable; `weekStatus` turned every prior day to MISSED
for the same reason. Nothing about the UI could have explained why.

Two consequences worth knowing:

- `perfectDayStreak` stops at the first day where *nothing* was due. Without that, days
  before the first medication existed would be vacuously "complete" and the loop would
  walk its full ten-year bound and report a streak of 3650.
- Days before any medication existed render as `FUTURE`, not `MISSED`. It's the same
  barely-there dot, and it's the same thing to say: nothing was asked of that day.

`createdAt` defaults to `0` in `MIGRATION_3_4` -- "has always existed" -- so medications
already installed keep exactly the streak and week they had before the update. Setting it
to `now` would have reset everyone's history, which is the bug being fixed. `AddMedication`
preserves it through edits for the same reason, since that screen is both add and edit.
The day of creation *does* count, deliberately: judging by slot time instead would mean
that installing the app in the evening with a morning pill makes day one free, and day one
is the one you most want to count.

### A dose belongs to a day, not to a millisecond

`logForSlot` looks for a dose anywhere inside the slot's **calendar day** rather than at
the slot's exact timestamp. This is not a nicety.

A `DoseLog` stores `scheduledFor` -- the time the medication was set to *when the dose was
taken*. Every lookup recomputes that timestamp from the medication's *current*
`hourOfDay`/`minute`. So moving a reminder by five minutes used to make every past dose
for that medication unfindable: streak back to one, week dots emptied, drift chart blank,
with the rows still sitting untouched in the database. Editing the time is one of the
first things anyone does after installing, which made it a first-week bug.

The schema holds one time per medication, so "that medication, that day" always names
exactly one dose. `driftMinutes` still reads the stored `scheduledFor`, so how late you
actually were stays true even after the reminder moves.

### The pill you took at 00:30 last night

`Slots.loggableSlots(med, now)` returns the slots a tap can satisfy right now, best first:
today's, from two hours before its time onwards, and — for six hours after its time —
**yesterday's**.

The second one exists because a 21h pill taken at 00:30 could not be recorded at all.
`todayAt` resolves to the current calendar day and must keep doing so (see the note above
it: making it reach backwards once made a 9am dose read as nineteen hours overdue at 4am),
so at 00:30 the 21h slot meant *tonight*, twenty hours out, the window wasn't open, and
the button was greyed. The pill she was physically holding had nowhere to go, and
yesterday stayed missed.

Six hours is the line between the two cases: a 21h pill at 00:30 is obviously last
night's; a 9h pill "taken" at 1am the next day is a missed day being papered over. Today's
window still has no upper bound, so a morning dose is loggable all evening as before.

It's a pure function with no database, because three callers ask the same question with
different data in hand — the screen has `takenDays`, the rest have the DAO. What must not
diverge is the *rule*; each caller checks what's already logged for itself. And when the
tap is going to credit yesterday, the button says so — **"Je l'ai prise (hier soir)"**. A
button that records something other than what you think it records is worse than a greyed
one.

### Two message sets, and which is which

`FloMessages` holds two celebration pools, and getting them the wrong way round makes the
good one nearly unreachable.

`dayStreakLine(days)` is the **hand-written diary** -- one line per day, 1 through 32.
These read like a running conversation, so they belong to the *day* streak: "jour 17 des
cacahuètes" makes no sense if the number jumps three times a day. This is what shows on
the full-screen page, and in the notification when the day completes while she's away.

`celebration(streak, seed)` is the short generic confirmation for **one dose when others
are still owed** that day. Deliberately plain: a medication taken three times daily would
otherwise burn through all 32 hand-written lines in eleven days.

## Where the celebration lands

The last dose of the day gets a different treatment depending on where she is when it
happens, because the same news needs a different shape in each place.

**Logged in the app** -- a full-screen celebration: rotating rays, confetti, the number
rolling up from zero, the dragon cheering, a week of dots, and a **Continuer** button. It
is dismissed by hand rather than on a timer. An animation that vanishes on its own is
something that happened *at* you; one you close is something you finished.

**Logged with a tag while the phone is asleep** -- a notification, because there is no
screen to celebrate on and the news has to survive until she picks the phone up.

Telling those apart takes more than an `isResumed` flag: a tag held against a sleeping
phone *launches* the activity, which resumes behind the lock screen. So `watchingNow()`
also requires the display to be on and the keyguard down. When she is watching, no
notification is posted at all -- the cheering dragon on screen is already the message,
and a notification on top would be the same news twice.

## One notification per medication, all day

`notifId(tagId)` is a single id, and the head-up, the reminder and every re-post land on
it. They **replace** each other instead of stacking. In the drawer there is one line per
medication that changes tone through the day, which reads like someone talking; two lines
saying the same thing in different registers reads like software nagging.

This replaced two separate stacking bugs:

- The 15-minute head-up had its own id, so at the scheduled minute *"c'est bientôt
  l'heure"* and *"c'est l'heure"* sat one above the other -- a notification announcing
  there was a pill, directly above the notification of the pill.
- An evening countdown posted at 21h listing everything still owed. It could only ever
  appear **on top of** a reminder: it fired only when a dose was outstanding, and an
  outstanding dose always has its own ongoing reminder. So it was always a duplicate.
  Its channel is deleted in `ensureChannels` rather than merely abandoned, so it doesn't
  linger forever in the phone's notification settings under a name that means nothing.

## The countdown runs to the top of the ladder, not to midnight

The reminder carries a **live ticking clock counting down to `slot + 2h`** -- the moment
`Tier.SERIEUX` begins and the dragon stops joking.

It used to count down to midnight, which was a deadline the app didn't keep: nothing in
particular happened at zero, and a clock grinding through six hours doesn't hurry anyone.
Two hours is a real deadline with a visible consequence, and it's short enough to feel.
Past it there's nothing left to count, so the chronometer is dropped entirely rather than
counting back up with a minus sign.

The ticking is the whole point, and it's why this uses `setUsesChronometer` +
`setChronometerCountDown` rather than writing the remaining time into the text. A sentence
saying "il reste 2 heures" is frozen at whatever it said when it was posted, and it's a
fact you read. A clock visibly counting down is handed to the system, stays accurate
without the app waking up once, and reads as pressure. That's the Duolingo trick.

The 15-minute head-up gets the same treatment, counting down to the scheduled minute.

## The first thirty seconds

Three pages, shown once, skippable from the first: **who the dragon is**, **what's asked
of you**, **what you get**. Then it's gone for good.

Three and not seven, on purpose. A tutorial with an itinerary, on an app whose whole job is
one pill a day, is the surest way never to be opened a second time. Each page has one
picture, one line of title, and two sentences.

Every page **shows** the thing rather than describing it — the real dragon, the real week
dots, the real locker with Bernadette sitting beside her. It's the same code the app runs
on, so an intro screen can't drift out of date the way a mocked-up screenshot would.

Bernadette, the green frog, is the only piece in the catalogue with a name rather than a
description. That's what lifts her out of the accessory list.

The "seen it" flag lives in `SharedPreferences`, not in Room: it isn't data about her, it's
a display detail, and restoring a backup should not make her sit through the introduction
again.

## Cosmetics, chests and the locker

One piece per **complete day**, never twice the same, permanent once earned. Fifty-five
exist, across five slots — 14 head, 14 body, 10 feet, 9 wings, 8 companions. The catalogue is `Cosmetics.ALL`; adding another is one entry
there plus one drawing function in `Dragon.kt` keyed on the same id. Nothing else needs
touching — `LockerScreen` iterates `Slot.entries`, so a new slot appears on its own.

Two of the five slots don't behave like clothing:

- **`WINGS`** replaces a body part rather than sitting on top of one. The dragon already
  has wings, so `Dragon.wing()` takes the piece as a parameter; drawing the cosmetic
  afterwards like a hat would leave two pairs overlapping. Nine variants share four
  silhouettes — bat, feathered, butterfly, dragonfly — with the colour and detail doing
  the rest.
- **`FRIEND`** isn't worn at all. It sits on the ground to her right, drawn *after* the
  breathing bob is restored: a plush that rises and falls in time with the dragon reads as
  levitating. Right rather than left because the tail sweeps left and the ground shadow
  ends around x=164, which makes that corner the only reliably empty space whatever else
  she's wearing. The six animals share `plushBody` and `plushFace` and differ by
  **silhouette** — a horn, round ears versus pointed ones, eyes on top of the head, a tail
  fin. Six identical balls in different colours would be indistinguishable side by side in
  the locker, and a 60dp thumbnail leaves no other way to tell them apart.

Two body pieces break the "clip it inside the torso" rule, and both had to:

- **The cape** is drawn *before* the body (`capeBehind`), because a cape clipped to the
  front of the torso isn't a cape, it's an apron. Only the golden clasp is drawn in front.
- **The dress, swimsuit and apron** have no sleeves. `sleeve()` returns null for them, so
  the arms stay bare — giving a summer dress a matching sleeve would produce a garment
  nobody owns.

None of the wings use a `Shader`, and that's deliberate rather than stylistic: a shader
overrides the paint colour, which would bypass the locker's silhouette tint and show a
locked piece in full colour. The ember and rainbow fades are bands of flat colour clipped
to the wing outline instead. The tattered Halloween pair cuts its holes out of the path
with `Path.Op.DIFFERENCE` rather than painting over them, since there's no background
colour to paint with.

Adding a medication is its own full screen rather than a bottom sheet: the keyboard eats
half a modal sheet, leaving three fields crammed into two centimetres.

**The chest** appears after the streak page, and only after the Continuer button has been
pressed **four times**. Each press fills the button further and vibrates a little harder.
That serves no function whatsoever, which is the point: the chest is waiting behind it,
and three seconds of pointless effort turn a screen transition into impatience. One press
would make the reward free.

Then the chest shakes three times, escalating, before it opens. The satisfaction is
entirely in the wait -- a chest that opens instantly is a dialog box; a chest that rattles
for two seconds while you can't do anything is a gift.

The piece is written to the database **before** the chest animates, not when it's
dismissed. If the app dies mid-animation the gift is already banked; granting on close
would lose it at the single most infuriating moment possible.

**The locker** is a page to the left of home, reached by swiping, Clash Royale style. Two
small dots at the bottom of the screen are the only sign it exists -- no tab, because it
must never compete with the one thing that matters.

### The fitting room

Typing **"Je t'aime"** into the box at the very bottom of the locker opens the whole
catalogue for five minutes.

It is a *fitting room*, not a shortcut. Nothing that happens inside the window reaches the
database — not what she puts on, not what she takes off — so when it expires her real
outfit is still exactly where it was. Pieces are still earned one per complete day, which
is the only thing that gives them any value; a word that handed them over for good would
leave the locker with no reason to be opened tomorrow.

It sits at the **very bottom**, below the full collection, on purpose. You have to scroll
past everything still locked to reach it, so you've already seen what there is to want.
Found before that, it would replace the wanting; found after, it feeds it.

The comparison strips accents, apostrophes, spaces and case (`Cosmetics.isPreviewCode`).
The apostrophe in *t'aime* comes out curly on half the keyboards in existence, and a
password you have to type character-perfect is a password that doesn't work on the evening
you actually want it.

The widget keeps showing the real outfit throughout, since it reads the database. The
try-on only exists inside the app, which is the only place the countdown is visible.

Pieces are grouped by slot and **one is worn per slot**: equipping a hat silently removes
the hat already there, rather than showing an error. Nobody wants to read "please remove
your tuque first" for a problem the app can solve by itself.

Locked pieces show as a **two-tone silhouette** -- the dragon greys out, the unearned
piece stays near-black -- with the name replaced by `? ? ?`. Painting both the same colour
was the first attempt and produced a black dragon in a black hat, i.e. a blob, when the
shape of the piece is the one thing worth showing. You can see the outline and guess; you
don't know the colour or the detail. Revealing everything would make the locker a shopping
list, showing nothing would leave it no reason to be opened.

Storage is Room table `OwnedCosmetic`, added in schema **version 2** with a real migration
that creates the table. Emphatically not `fallbackToDestructiveMigration`, which would
erase months of dose history to make room for a hat.

## Streak freezes

One missed day used to reset everything to zero, which is exactly the clean break that
makes people quit. A freeze is **free, automatic and silent**: `useFreezeIfNeeded()` runs
on resume, and if yesterday lapsed while a streak was running, it spends the week's freeze
and the streak survives. One per seven days, no more, or the number stops meaning anything.

It only fires when there was something to protect. Freezing a day when the streak was
already dead would waste the only one available for nothing.

## Skipping a dose deliberately

Empty bottle, prescribed pause, sick day. Without a skip button the only way to silence
the dragon is to log a dose that was never swallowed -- and that's the chart you'd hand a
doctor. **Pas aujourd'hui** writes a normal `DoseLog` with `skipped = true`: reminders
stop, the streak holds, and the drift chart plots nothing, because there is nothing to
plot.

It takes two taps, like removal. The slot is consumed for the day and there is no undo, so
a stray thumb shouldn't be able to spend it.

## Reminders that actually fire

The real failure mode on Samsung -- the S25 included -- is One UI putting the app to sleep
and dropping its alarms without a word. The symptom is the worst possible one for a
medication app: nothing arrives, and nobody notices.

This lives on the **settings page**, swiped to from the right, with a one-tap fix and the
exact One UI path (Batterie → Limites d'utilisation en arrière-plan → Applications jamais
mises en veille), because the system dialog alone isn't enough on Samsung.

It started on the home screen and was moved off it. That warning matters twice a year and
is clutter the other 363 days, and the home screen has to answer exactly one question --
have I taken my pill -- with everything that doesn't answer it pushed to one side or the
other. Three pages now: locker left, home centre, settings right.

### Proving a reminder actually fired

`ReminderHealth` detects that the battery is *restricted* — a risk. `silentMisses`
detects a **failure**: a slot came and went, no reminder was posted, and no dose was
logged.

Every time `post` actually hands a notification to the system, it writes a `ReminderPost`
row for that `(tagId, slot)`. On the day One UI puts the app to sleep and the alarm never
fires, that row is missing, and the app can say so instead of failing in silence. This is
the failure mode the whole file worries about, and until now nothing could observe it —
which is the worst possible property for a medication app, because the person has stopped
checking for herself.

All three conditions matter, and each rules out a false positive:

- **No dose logged.** Taking a dose early cancels the reminder before it ever posts. Without
  this check, every early dose would be reported as a breakage.
- **The medication existed.** `dueOn` again, so a medication added on Tuesday isn't blamed
  for Monday.
- **Completed days only.** Today's slot may have passed a minute ago with its alarm mid-flight.
  Counting it would be a race against the clock, and a warning that appears and clears itself
  means nothing.

It shows on the **home screen**, which the battery warning deliberately isn't allowed to
do. The difference is that the battery warning is permanent and becomes furniture; this
one appears only on the day a reminder genuinely didn't arrive, and on that day it's the
most important thing on the screen. Logging the missing dose clears it on its own.

## The Sunday recap

Every other notification in this app starts from a problem: it's time, it's late, it's very
late. An app that only ever speaks to demand something becomes a chore no matter how well
the lines are written. `postRecap` fires Sunday at 19h and does nothing but report the week
— days completed out of seven, and the streak.

**It only posts if nothing is still owed.** Congratulating someone while a reminder is
still on screen would be two notifications for one day, one of them celebrating too early
— exactly what the last-call countdown was removed for. That condition also means the
recap only ever arrives once Sunday is genuinely finished.

The tone tracks what actually happened but none of the versions scold: it's Sunday evening,
the week is over, there is nothing left to recover, and blame at that moment buys nothing.
Low-importance channel, no vibration, and its own id so next Sunday's replaces this one.

Being an inexact alarm, it survives neither reboot nor update, so it's re-armed in
`onCreate` and in `rescheduleAll`. Arming it twice replaces it rather than stacking.

### The nagging goes quiet before she does

`Reminders.Alert` decides whether a post is allowed to make noise:

| | Sound? |
|---|---|
| `DUE` — the scheduled minute | always, whatever time it is |
| `NAG` — a repeat | only below `SERIEUX`, and only outside 22h–08h |
| `SILENT` — re-post after a swipe | never |

This used to be one boolean, true for the scheduled minute *and* every repeat. A dose
missed at 21h therefore rang at alarm volume every ten minutes until midnight, on a
channel with `setBypassDnd(true)` and `USAGE_ALARM`. The first ring is the whole point of
a medication reminder and still goes through unconditionally; the twelfth at 3am wakes
nobody usefully and only teaches you to turn the app's notifications off, which ends
everything.

The repeat interval backs off too: at `SERIEUX` the next nag is armed for **just after
midnight** instead of ten minutes out. It cannot simply stop, and this is the trap --
that alarm is what clears the stale reminder once the day turns over and arms the next
day's, via the early return in `onAlarm`. Dropping it would leave yesterday's "you're
late" pinned to the screen and, far worse, no reminders at all on the following days until
the app was opened by hand.

## Backup

On the settings page. Local JSON to a file she picks: medications, every log, cosmetics,
freezes. No account, no
cloud, no server to keep alive in five years -- a file she can drop in Drive and forget is
the only backup format that outlives whoever wrote the app.

Restore is **additive**. Nothing is deleted, everything is merged. A restore that starts by
emptying the database is a restore that destroys what it was meant to save when the file
turns out to be wrong.

## The widget

Three bands, like a Duolingo tile: a status bar (streak flame left, the week's seven dots
right), **the hand-written line of the day set large**, and the dragon with the current
state along the bottom.

The middle band is the point of the whole widget. `dayStreakLine` is the diary -- a
different sentence every day -- and it's on `layout_weight` so it takes every pixel the
other two bands don't need. `autoSizeTextType="uniform"` between 10sp and 26sp does the
rest: "jamais deux sans toi" renders huge, the Life Is A Highway verse steps down until
it fits, and neither one ever ends in an ellipsis. That range is the only way to show
messages running from twenty to a hundred and ten characters on two cells without
clipping the end of a joke.

The dragon gave up size for that text, which is the right trade here -- the drawing is
the same all day, the sentence changes every morning. The tile is resizable, so making
it bigger grows both. Below the dragon, in small type, is the one thing that's actually
actionable: the medication owed right now, or `FloMessages.widgetLine` in three words.

The streak sits in a dark translucent pill with a flame, and stays put at zero with the
flame greyed out. A badge that appears and disappears makes the tile jump from one day
to the next; and it is deliberately *not* a red numbered circle in the top corner, which
is the universal sign of a pending chore rather than a reward.

**Both counters carry their own contrast.** The pill went from 35% to 65% black, and the
week dots gained a dark pill of their own behind them. That's the change that matters:
seven small white circles drawn straight onto the scene vanished the moment the background
went bright, and the faintest states — missed and still-to-come — disappeared outright.
On a background guaranteed dark, all five states read on every one of the eight vibes.
Today's dot is now a gold ring rather than merely a larger white circle, so it's found by
colour instead of by comparing diameters.

Both are also drawn at roughly triple their on-screen size (96px pill, 320×48 week) and
scaled down, because a 24dp pill rasterised at 56px is visibly soft on a 3x screen. The
whole `RemoteViews` payload is still about 660 KB, well inside the 1 MB Binder ceiling.

### The background escalates, and it knows what time it is

`NotifArt.Vibe` is the colour ladder, shared by the widget and the notification banner:

| Vibe | When | Look |
|---|---|---|
| `REST_DAY` | nothing owed, 06h–20h | bright sky, **sun**, two clouds |
| `REST_NIGHT` | nothing owed, 20h–06h | indigo, **crescent moon**, stars |
| `WIN` | dose just logged | mint, confetti |
| `DUE` | 0–10 min late | blue |
| `NUDGE` | 10–30 | indigo/violet |
| `SULK` | 30–60 | magenta, light rain |
| `DRAMA` | 60–120 | red, rain |
| `ANGRY` | 120+ | deep crimson, embers, heat glow |

This is Duo's actual mechanism: being late doesn't just change the words, it changes the
tile's *temperature*. You can read it from across the room without reading anything.

The thresholds come from `Tier.forLateness` rather than a second list, so the colour and
the wording can never escalate at different minutes. And the rest state splits on the
clock, because a crescent moon at three in the afternoon is the one detail that tells you
instantly the drawing isn't looking at the time. The middle tiers carry stars at night
and clouds by day for the same reason, and no sun or moon at all -- the colour is already
saying the thing.

The same `Vibe` also picks the banner's small prop -- a raspberry-and-white capsule while
the dose is waiting, a manga sweat-drop once it's late, two hearts when it's logged, and
nothing at all while the dragon is asleep. Three props, not a sticker catalogue: past
that, the character disappears behind its own accessories.

Widgets only self-refresh every 30 minutes, which is Android's floor for
`updatePeriodMillis`, so the tile would otherwise still be blue while the reminder had
already reached crimson. `Reminders.post` refreshes it on every nag -- the device is
already awake at that point, so it costs nothing -- and `resolve` does too, so a dose
logged by NFC with the app closed drops the tile back to calm at once.

Earlier it also had a button that logged the dose without opening anything. It was removed
for the reason at the top of `DragonWidget`: logging from the home screen saves two seconds
and skips the confirmation, the celebration and the chest, which are the whole reason to
come back tomorrow. The machinery that backed it (`logFromOutside`) went with it in the
release tidy-up rather than sitting there uncalled.

## Editing a medication

Same screen as adding, with `existing` filled in. The key is preserved: it's what ties a
medication to its whole history, and losing that to fix a typo would be absurd.

## The week, in seven dots

Monday leftmost, Sunday rightmost, always -- on the home screen, on the celebration, and
in the widget. A rolling "last seven days" would shift every morning and force a re-read
each time; a fixed week is recognised at a glance, like a calendar.

Five states, five colours: done (mint), frozen (teal, white ring), today (framboise, with
a halo and a slow pulse), missed (pale blush), still to come (barely there). Today is the
only dot that asks for anything, so it's the only one that moves.

On the widget the same five states are drawn in white and transparency instead. The
painted scene behind them changes five times a day, and a fixed palette eventually lands
on a background of its own hue and vanishes.

A 47-day streak says nothing about *this* week. These dots are the only view that answers
"am I doing alright right now" honestly.

## The head-up, fifteen minutes early

Every other tier on the ladder reacts to lateness, which means the first word of the day
was always a small accusation. `ACTION_SOON` fires fifteen minutes *before* the dose, in
its own quiet channel with vibration off, and says nothing more than "it's nearly time".

It carries a chronometer counting down to the scheduled minute, so the quarter of an hour
visibly melts instead of being asserted in words that go stale a minute later.

It posts on `notifId(tagId)` -- the reminder's own id -- so at the scheduled minute the
real reminder *replaces* it rather than landing underneath it. It used to have its own id
and expire via `setTimeoutAfter`, which left a window where both were on screen: a
notification saying there was a pill, sitting directly above the notification of the pill.

The alarm is inexact and `RTC` rather than `RTC_WAKEUP`: landing to the second doesn't
matter a quarter of an hour ahead, and it doesn't justify pulling the phone out of doze.

## Removing a medication

Two deliberate taps. **Retirer** replaces the row's footer with a confirmation panel, and
**Oui, retirer** sits somewhere the first button wasn't -- so a double tap can't delete
anything. Only one row can be armed at a time; opening a second confirmation closes the
first, because two live delete buttons is how you tap the wrong one.

It removes the medication *and* its history, and it cancels the pending alarms before
touching the database, so nothing fires into a void afterwards.

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

## Tests

```bash
gradle :app:testDebugUnitTest
```

They run on the JVM with no emulator and no device, and CI runs them before the APK is
built — a failing streak calculation fails the build instead of shipping a Release that
loses a two-hundred-day streak.

That's possible because everything that counts days is written against the `MedDao`
**interface** rather than against Room, so `TestDao` — a few mutable lists — stands in for
the database. A suite that needs an emulator is a suite you stop running.

Every test corresponds to a bug that actually happened, or to a risk written down in a
comment and never once executed:

| Test | Guards |
|---|---|
| `deuxieme jour compte deux` | day two showing "day 1" |
| `changer l heure du rappel garde l historique` | editing the reminder time erasing all history |
| `ajouter un medicament ne casse pas la serie` | a second medication wiping the streak |
| `la serie s arrete avant le premier medicament` | the vacuous-days loop reporting 3650 |
| `la serie survit au changement d heure du printemps` / `de l automne` | the DST hazard `slotDaysAgo` warns about |
| `la dose du soir se note encore apres minuit` | the 21h pill that couldn't be logged at 00:30 |
| `une dose du matin ne se rattrape pas la nuit suivante` | that grace window swallowing a genuinely missed day |
| `les jours d avant la creation ne sont pas manques` | a new medication emptying the week |
| `un rappel jamais parti est signale` | the Samsung silent failure going unnoticed |
| `une dose prise en avance n est pas une panne` | early doses being reported as breakage |
| `une dose oubliee apres un rappel n est pas une panne` | the app confessing to someone else's forgetting |

`TimeZone` is pinned to `America/Montreal` in `@Before`, otherwise the DST tests would
pass or fail depending on which machine ran them. `T.at(2025, 6, 3, 21, 0)` builds the
dates so a test reads out loud — one you can't read is one you can't fix the day it
breaks. `perfectDayStreak`, `weekStatus` and `currentStreak` all take an optional `now`
purely so tests can choose what day it is; every caller keeps the default.

## Still to add

- Multiple daily slots per medication (the schema holds one time per med today)
- Linking a sticker to a medication that was created without one
- Editing or deleting a medication
- More cosmetics (the catalogue is built to take them)
- CSV export for appointments
- A snooze button — deliberately absent, since it undoes the whole idea

---

Personal adherence tracker, not a medical device. If a dose is genuinely critical, keep a
backup reminder that doesn't depend on one phone's battery.
