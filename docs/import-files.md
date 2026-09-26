# Creating a topic or collection file

Use this when an agent needs to produce a deck the user can import into Kartka. Write one UTF-8 JSON file. Do not invent fields. A file that fails any rule below is rejected as a whole.

Import from the topics screen for a topic file. Import from inside a topic for a collection file. A collection file is added to the topic that is open, not to whatever `topicName` says. If `frontLabel` and `backLabel` differ from that topic, Kartka warns and still imports.

## Envelope

Every file starts with:

| Field | Value |
| --- | --- |
| `format` | `"kartka"` |
| `version` | `1` |
| `kind` | `"topic"` or `"collection"` |
| `exportedAt` | Optional. If present, an ISO-8601 instant such as `"2026-09-26T20:00:00Z"`. |

`format` and `version` must be exact. Unknown keys are ignored, so do not rely on them.

## Ids

Every topic, collection, and card has a `uid`.

- 1 to 80 characters.
- Only `A-Z`, `a-z`, `0-9`, and `_ . : -`.
- A short id such as `food` is valid. A UUID is also valid. Either is fine.
- Each `uid` appears once in the file. The same id cannot be reused for a topic and a card.
- For a new deck, use ids that are not already on the phone, or tell the user to choose **Import as copy**. **Update** overwrites any row that already has that id, including its review progress.

## Text limits

| Field | Max length | Empty |
| --- | --- | --- |
| Topic name, collection name | 200 | Rejected |
| `frontLabel`, `backLabel` | 80 | Rejected |
| Card `front`, `back`, `note` | 8000 | Front and back rejected. Note may be `null` or omitted. |

Trim nothing yourself that the user asked to keep, but leading and trailing spaces are stripped on import, and a side that is only spaces is empty.

`frontLabel` is the language or side of the question. `backLabel` is the answer side. For a fact deck in one language, set both labels to that language, for example both `"Belarusian"`.

Topic `color` may be `teal`, `blue`, `violet`, `rose`, `amber`, or `green`. Anything else becomes `teal`. Omit it only on a collection file. On a topic file it defaults to `teal` when omitted.

## Cards

A new card needs only `uid`, `front`, and `back`. Omit `ease`, `intervalDays`, `repetitions`, `lapses`, `dueAt`, and `lastReviewedAt`. The card is then due immediately.

If any of those six fields is present, the card is not new. Missing ones among them default to ease 2.5, interval 0, repetitions 0, lapses 0, and due now. Do not set them unless the user asked to preserve review progress. `dueAt` and `lastReviewedAt` must be ISO-8601 instants. Negative review numbers are rejected. Ease is clamped to 1.3..3.0 and the interval to at most 180.

## Topic file

One topic, then its collections, then their cards. Use this when the user wants a new subject, or several collections together.

```json
{
  "format": "kartka",
  "version": 1,
  "kind": "topic",
  "exportedAt": "2026-09-26T20:00:00Z",
  "topic": {
    "uid": "en-pl",
    "name": "English → Polish",
    "frontLabel": "English",
    "backLabel": "Polish",
    "color": "teal",
    "collections": [
      {
        "uid": "en-pl-food",
        "name": "Food",
        "cards": [
          { "uid": "en-pl-apple", "front": "apple", "back": "jabłko" },
          { "uid": "en-pl-bread", "front": "bread", "back": "chleb", "note": "neuter" }
        ]
      }
    ]
  }
}
```

`collections` may be `[]`. A collection's `cards` may be `[]`.

## Collection file

One collection and its cards. Also include the parent labels so Kartka can warn if they do not match the open topic.

```json
{
  "format": "kartka",
  "version": 1,
  "kind": "collection",
  "exportedAt": "2026-09-26T20:00:00Z",
  "collection": {
    "uid": "be-facts-mind",
    "name": "Mind",
    "topicName": "Belarusian facts",
    "frontLabel": "Belarusian",
    "backLabel": "Belarusian",
    "cards": [
      {
        "uid": "be-facts-working-memory",
        "front": "Што такое рабочая памяць?",
        "back": "Гэта тое, што мы трымаем у галаве, пакуль выконваем задачу."
      }
    ]
  }
}
```

`topicName`, `frontLabel`, and `backLabel` are required on a collection file even though the collection is stored under the topic the user is viewing.

## Before handing the file over

Check all of the following:

- `format` is `kartka` and `version` is `1`.
- `kind` matches the object you included: `topic` or `collection`, not both.
- Every `uid` is unique and matches the character rule.
- No front or back is blank.
- New cards have no schedule fields.
- The file is UTF-8 and under 20 MB.

Save it with a `.json` name. The user imports it from Kartka with the system file picker.
