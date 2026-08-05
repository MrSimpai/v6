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
`<activity-alias>` is the enabled launcher entry. Four aliases, exactly one enabled:

| State | Icon |
|---|---|
| nothing due, or dose just logged | teal, eyes closed, sleeping |
| dose owed (incl. 1h late) | apricot, wide eyes, holding a pill |
| 2h+ overdue | terracotta, lowered brows, frowning |

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

## The evening countdown

At 21h, if anything is still unlogged, a notification appears with a **live ticking clock
counting down to midnight** -- the moment the day, and the streak, turns over.

The ticking is the whole point, and it's why this uses `setUsesChronometer` +
`setChronometerCountDown` rather than writing the remaining time into the text. A sentence
saying "il reste 2 heures" is frozen at whatever it said when it was posted, and it's a
fact you read. A clock visibly counting down is handed to the system, stays accurate
without the app waking up once, and reads as pressure. That's the Duolingo trick.

`setTimeoutAfter(midnight - now)` makes it delete itself exactly as the countdown hits
zero, so there's never a dead countdown sitting in the shade at 3am.

`refreshLastCall()` is called both by the 21h alarm and after any dose is logged, and it
decides for itself whether to post or clear. So logging the last pill at 22h makes the
countdown vanish on its own, and it re-arms tomorrow's alarm on the way out.

It uses its own channel (*Série en jeu*, `IMPORTANCE_DEFAULT`), so it can be muted
separately from the reminder itself without losing the thing that actually matters.

## Cosmetics, chests and the locker

One piece of clothing per **complete day**, never twice the same, permanent once earned.
Three exist so far -- a bobble tuque with holes for the horns, little red boots, and a
Christmas hoodie. The catalogue is `Cosmetics.ALL`; adding a fourth is one entry there
plus one drawing function in `Dragon.kt` keyed on the same id. Nothing else needs touching.

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

## Backup

On the settings page. Local JSON to a file she picks: medications, every log, cosmetics,
freezes. No account, no
cloud, no server to keep alive in five years -- a file she can drop in Drive and forget is
the only backup format that outlives whoever wrote the app.

Restore is **additive**. Nothing is deleted, everything is merged. A restore that starts by
emptying the database is a restore that destroys what it was meant to save when the file
turns out to be wrong.

## The widget

Dragon, current state, and a button that logs the dose without opening anything -- the
shortest possible path between remembering and having recorded it. It goes through
`Reminders.logFromOutside`, the same slot matching and streak counting as the in-app
button, so the two can never tell different stories.

## Editing a medication

Same screen as adding, with `existing` filled in. The key is preserved: it's what ties a
medication to its whole history, and losing that to fix a typo would be absurd.

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
