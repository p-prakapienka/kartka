# AGENTS.md

Kartka is an offline Android flashcard app. Package `pl.restrictor.kartka`. A topic is a label pair (English → Polish, Russian → Belarusian, or Belarusian → Belarusian for facts). A topic holds collections. A collection holds cards. There is no account, no ads, and no network.

Read this file before changing behavior. Prefer a small correct change over a cleanup of untouched code.

## Layout

- `:domain` is plain Kotlin (JVM 17). Scheduler, due labels, JSON codec, and import planning live here. No Android imports. New rules for those belong here, with a JUnit test.
- `:app` is Jetpack Compose, Material 3, and Room. It stores data and draws screens. It calls `:domain`; it does not reimplement it.
- Versions are in `gradle/libs.versions.toml`. Do not bump them unless a task requires it.
- UI strings are English and live in `app/src/main/res/values/strings.xml`.

## Invariants

These are blocking. A change that breaks one is not done.

- Cards are never finished. Good and Easy only push `dueAt` out. The cap is 180 days.
- Again sets the interval to 0, increments lapses, drops ease by 0.2, and sets `dueAt` to now + 10 minutes. Ease stays inside 1.3..3.0. Default ease is 2.5.
- In a study session, Again moves that card to the end of the queue so every other due card is seen first. A single remaining card stays. Good and Easy remove the card from the session. Do not reinsert Again a fixed number of places ahead; that traps the session on the first three cards.
- Leaving a session does not delete cards. The stored `dueAt` is what brings them back.
- JSON format is `kartka`, version `1`. Kind is `topic` or `collection`. Unknown keys are ignored. A bad file imports nothing, not a partial deck.
- A `uid` is 1..80 characters matching `^[A-Za-z0-9_.:-]{1,80}$`. It is not required to be a UUID. The app mints UUIDs for rows it creates. Uids are unique inside one file and unique in the database across topics, collections, and cards.
- Import Update keeps colliding uids and replaces text and schedule. Copy mints new uids for the whole file. A collection import lands in the topic the user is viewing. Label mismatch is a warning, not a reject.
- Deleting a topic deletes its collections and cards. Deleting a collection deletes its cards. Foreign keys cascade.
- The manifest has no `INTERNET` permission. Do not add one, an account, analytics, or ads.
- `app/kartka-debug.keystore` is the sideload key (alias `kartkadebug`, password `android`). Every debug and release APK must use it. Do not generate a new keystore. A new certificate makes Android refuse to update an install.
- CI passes `-Pkartka.versionCode=${{ github.run_number }}`. Do not hardcode versionCode back to 1, and do not make a build that can install as a downgrade.
- `android:hasFragileUserData="true"` and the backup rules that include `kartka.db`, `kartka.db-wal`, and `kartka.db-shm` stay. Uninstall should be able to keep the deck, and an in-place update must keep it.
- User-facing limits: name 200, label 80, card text 8000. Empty front or back is rejected.

## Study and storage

Room database name is `kartka.db`. Schedule fields on a card are `ease`, `intervalDays`, `repetitions`, `lapses`, `dueAt`, `lastReviewedAt`. Time is epoch millis in the database and ISO-8601 instants in JSON. Omit schedule fields on import to create a new card due now.

Good intervals: first success 1 day, second 3 days, later `round(intervalDays * ease)` days. Easy intervals: first 3 days, second `round(3 * ease)` (at least 4), later `round(intervalDays * ease * 1.3)` and at least one day more than the current interval. Both cap at 180.

Topic colors are `teal`, `blue`, `violet`, `rose`, `amber`, `green`. Anything else becomes `teal`.

## Commands

From the repo root, with JDK 17 and Android SDK 36:

```bash
./gradlew :domain:test
./gradlew test assembleDebug
```

`:domain:test` is required for scheduler, queue, codec, or import changes. `assembleDebug` is required when `:app` or the manifest changes. The APK is `app/build/outputs/apk/debug/kartka-debug.apk`.

Do not commit `local.properties`, `.gradle/`, or `build/`.

## Out of scope unless asked

Accounts, sync, a backend, FSRS or extra ratings, widgets, and Play Store signing. The checked-in key is for sideload updates only. Do not add a second module or a new architecture layer for one call site.

## Review

Before finishing, check the diff against the invariants above. Report blocking misses first: a scheduler change without a test, a JSON change that breaks version 1 files, a new signing key, a network permission, or a study queue that can hide cards behind Again. Style nits are not blocking.
