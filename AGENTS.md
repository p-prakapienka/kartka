# AGENTS.md

Kartka is an offline Android flashcard app. Package `pl.restrictor.kartka`. A topic is a label pair (English → Polish, Russian → Belarusian, or Belarusian → Belarusian for facts). A topic holds collections. A collection holds cards. There is no account, no ads, and no network.

Blocking rules and review checks are in this file. [docs/implementation-plan.md](docs/implementation-plan.md) is the initial design. Read it for context. Do not edit it when implementing a later change. To write a topic or collection the user can import, follow [docs/import-files.md](docs/import-files.md). Prefer a small correct change over a cleanup of untouched code.

## Layout

- `:domain` is plain Kotlin (JVM 17). Scheduler, due labels, JSON codec, and import planning live here. No Android imports. New rules for those belong here, with a JUnit test.
- `:app` is Jetpack Compose, Material 3, and Room. It stores data and draws screens. It calls `:domain`; it does not reimplement it.
- Versions are in `gradle/libs.versions.toml`. Do not bump them unless a task requires it.
- UI strings are English and live in `app/src/main/res/values/strings.xml`.

## Invariants

These are blocking. A change that breaks one is not done.

- Cards are never finished. Bad, Medium, and Good only set `dueAt`. Nothing is removed from the deck.
- The wait is not an increasing formula. Bad, Medium, and Good each wait a fixed time. Defaults are 1 hour, 1 day, and 7 days. The user changes those times in the app (Repeat times on the topics screen). Allowed range is 1 minute to 365 days. A grade uses the times saved at that moment. It does not rewrite cards already scheduled.
- A graded card leaves the study session. Do not put it back in the queue. One card graded Bad is not shown again until it is due.
- Ease is kept on the card for older files, but a grade does not change it. Bad resets repetitions to 0 and adds a lapse. Medium and Good add a repetition.
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

Room database name is `kartka.db`. Schedule fields on a card are `ease`, `intervalDays`, `repetitions`, `lapses`, `dueAt`, `lastReviewedAt`. Time is epoch millis in the database and ISO-8601 instants in JSON. Omit schedule fields on import to create a new card due now. Repeat times are not in the database. They live in the `kartka-repeat` preferences: `bad_ms`, `medium_ms`, `good_ms`.

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

Before finishing, check the diff against the invariants above. Report blocking misses first: a grade that shows the same card again immediately, repeat times that cannot be changed in the app, a scheduler change without a test, a JSON change that breaks version 1 files, a new signing key, or a network permission. Style nits are not blocking.
