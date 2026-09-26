# Kartka

Kartka is a flashcard app for Android. Topics hold collections, collections hold cards. Nothing leaves the phone: there is no account, no ads, and no network permission.

A topic is a pair of labels, such as English → Polish, Russian → Belarusian, or Belarusian → Belarusian for facts. A collection is a group inside that topic, such as Food or Travel.

Cards you know come back later. Cards you miss come back in about 10 minutes. Nothing is ever marked finished. The longest gap is 180 days.

## Install

The debug APK is signed with the debug key and can be installed directly.

1. Open the latest successful run of the **Android** workflow and download the `kartka-debug-apk` artifact, or open a GitHub Release created by a `v*` tag.
2. On the phone, allow installs from that source, then open the APK.

A tag such as `v1.0.0` also attaches `kartka-v1.0.0.apk` to a GitHub Release. Actions artifacts expire; the Release file does not.

## Study

Open a collection and tap Study, or study every due card in a topic. Tap the card to reveal the back, then choose Again, Good, or Easy.

## Import and export

Each topic and each collection can be saved as UTF-8 JSON through the system file picker. Import from the topics screen, or import a collection into the topic you are viewing. If the file's ids are already on the phone, choose Update or Import as copy. Update keeps those ids and replaces their text and review progress. Copy adds a second set. The whole file is applied, or nothing is.

```json
{
  "format": "kartka",
  "version": 1,
  "kind": "topic",
  "exportedAt": "2026-09-26T18:12:00Z",
  "topic": {
    "uid": "topic-1",
    "name": "English → Polish",
    "frontLabel": "English",
    "backLabel": "Polish",
    "color": "teal",
    "collections": [
      {
        "uid": "food",
        "name": "Food",
        "cards": [
          {
            "uid": "apple",
            "front": "apple",
            "back": "jabłko",
            "note": null,
            "ease": 2.5,
            "intervalDays": 0,
            "repetitions": 0,
            "lapses": 0,
            "dueAt": "2026-09-26T18:12:00Z",
            "lastReviewedAt": null
          }
        ]
      }
    ]
  }
}
```

A collection file uses `"kind": "collection"` and a `collection` object with `topicName`, `frontLabel`, `backLabel`, and `cards`. Omit the review fields to import the cards as new. `format` must be `kartka` and `version` must be `1`.

## Build

The project needs JDK 17 and Android SDK 36.

```bash
./gradlew test assembleDebug
```

The APK is `app/build/outputs/apk/debug/kartka-debug.apk`.
