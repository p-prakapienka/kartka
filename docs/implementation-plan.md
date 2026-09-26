# Implementation plan

Kartka is a local Android flashcard app. This document is the initial design. Later work may read it for context. Do not update it to match later changes. Current rules for agents are in [AGENTS.md](../AGENTS.md).

## 1. Product

A person installs the APK and uses it. No registration.

- A **topic** is a pair of labels, such as English → Polish, Russian → Belarusian, or Belarusian → Belarusian for facts.
- A **collection** sits inside one topic, such as Food or Travel.
- A **card** has a front, a back, an optional note, and a review schedule.
- The user can add, edit, and delete each of those.
- Known cards come back later. Missed cards come back in about 10 minutes. Nothing is ever marked finished.
- A topic or a collection can be exported and imported as UTF-8 JSON through the system file picker.

## 2. Modules

Two Gradle modules:

| Module | Job |
| --- | --- |
| `:domain` | Kotlin JVM library. Scheduler, due labels, JSON codec, import planning. No Android imports. |
| `:app` | Compose UI, Room, and the file picker. Calls `:domain`. |

`DeckRepository` is the only writer of the database. Screens do not compute the next interval themselves. `ImportPlanner` decides Update versus Copy before Room runs a transaction.

Package name: `pl.restrictor.kartka`. App name: Kartka. `minSdk` 26, `compileSdk` / `targetSdk` 36, JDK 17.

## 3. Data

Room database `kartka.db`.

- `topics`: `uid`, name, `frontLabel`, `backLabel`, color, timestamps.
- `collections`: `uid`, `topicId`, name, timestamps. `onDelete` cascades from the topic.
- `cards`: `uid`, `collectionId`, front, back, note, `ease`, `intervalDays`, `repetitions`, `lapses`, `dueAt`, `lastReviewedAt`, timestamps. `onDelete` cascades from the collection. Index `dueAt`.

`uid` is unique across all three tables. The app mints a UUID when it creates a row. Imported ids only need to match `^[A-Za-z0-9_.:-]{1,80}$`.

Colors are `teal`, `blue`, `violet`, `rose`, `amber`, `green`. Other values normalize to `teal`.

Limits: name 200, label 80, card text 8000. Blank front or back is rejected.

## 4. Scheduler

`Scheduler.review(state, rating, now)` is a pure function. Ease stays in 1.3..3.0. Intervals cap at 180 days. Default ease is 2.5.

| Rating | Effect |
| --- | --- |
| Again | Ease − 0.2, interval 0, repetitions 0, lapses + 1, due in 10 minutes. |
| Good | First time 1 day, second time 3 days, then `round(intervalDays * ease)`. |
| Easy | Ease + 0.15. First time 3 days, second time `round(3 * ease)` and at least 4, then `round(intervalDays * ease * 1.3)` and at least one day longer than now. |

`DueLabel.of` turns a due time into Now, minutes, hours, Tomorrow, or days. The UI does not format that itself.

## 5. Study session

Load every card that is due now, oldest due first. The session queue is in memory. The database write happens on each rating.

- Again appends that card to the end of the queue. The rest of the deck is seen before it returns. One card left stays on screen.
- Good and Easy drop the card from the queue. Its stored `dueAt` brings it back later.
- An empty due queue offers review early, which loads cards that are not due yet. Leaving the screen does not delete anything.

Do not insert Again two places ahead. That loops the first three cards and hides the rest.

## 6. Import and export

`DeckCodec` reads and writes format `kartka`, version `1`.

- A topic file has `kind: "topic"` and a `topic` object with collections and cards.
- A collection file has `kind: "collection"` and a `collection` object plus `topicName`, `frontLabel`, and `backLabel`.
- Dates are ISO-8601 instants. Omit the schedule fields to import a card as new and due now.
- Unknown JSON keys are ignored. The wrong format, the wrong version, a duplicate uid, or an empty side rejects the whole file.

`DeckFiles` reads and writes through the Storage Access Framework. Cap is 20 MB. The bytes must be UTF-8.

`ImportPlanner`:

- Preview counts collections, cards, and uid collisions.
- Update overwrites rows that share a uid, including schedule.
- Copy remints every uid in the file.
- A collection import is applied inside the open topic. A front/back label that does not match that topic is a warning. The import still proceeds.

Apply the plan in one Room transaction.

## 7. Screens

Navigation, all arguments are Room ids:

- `topics` lists topics. Create, edit, delete, export, and import a topic.
- `topics/{topicId}` lists collections. Study the topic, import a collection into it, edit or delete the topic.
- `topics/{topicId}/collections/{collectionId}` lists cards. Study the collection, edit or delete cards and the collection.
- `study/topic/{topicId}` and `study/collection/{collectionId}` show one card. Tap to reveal, then Again, Good, or Easy.

Editors are modal sheets. Empty states say there is nothing to study, not a blank screen. Follow the system dark theme. No extra product chrome.

## 8. Files, backup, updates

- No `INTERNET` permission.
- `android:allowBackup="true"` and `android:hasFragileUserData="true"`.
- Backup and device-transfer rules include `kartka.db`, `kartka.db-wal`, and `kartka.db-shm`.
- Sign debug and release with `app/kartka-debug.keystore` (alias `kartkadebug`, password `android`). Replacing that key breaks updates.
- CI sets `versionCode` from `github.run_number` so a new APK can install over the previous one. An in-place update keeps the database. The first install after a certificate change cannot; the user exports, uninstalls once, then imports.

## 9. CI

`.github/workflows/android.yml` on pushes to `main` and on `v*` tags:

1. JDK 17, Android SDK 36, build-tools 35.0.0. Use `android-actions/setup-android@v4`. v3 fails because it still installs the removed `tools` package.
2. `./gradlew test assembleDebug -Pkartka.versionCode=${{ github.run_number }}`.
3. Upload `app/build/outputs/apk/debug/kartka-debug.apk` as `kartka-debug-apk`.
4. On a `v*` tag, attach `kartka-<tag>.apk` to the GitHub Release.

## 10. Tests

Domain tests are the contract. Add or update them when the rule changes.

- `SchedulerTest`: Good and Easy intervals, the 180-day cap, ease floor and ceiling, Again's 10-minute delay, and Again moving to the end of a queue longer than three cards.
- `DeckJsonTest`: topic and collection round-trip, missing schedule means a new card, bad format or version imports nothing, blank sides and duplicate uids fail, Update keeps ids, Copy mints new ones, a collection import stays in the chosen topic.
- `DueLabelTest`: the due-text buckets.

`./gradlew :domain:test` for those. `./gradlew test assembleDebug` when `:app`, the manifest, resources, or Gradle config change.

## 11. Not in this plan

Accounts, sync, a server, ads, extra ratings, FSRS, widgets, and a Play Store upload key.
