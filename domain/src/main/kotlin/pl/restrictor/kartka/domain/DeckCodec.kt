package pl.restrictor.kartka.domain

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ParseResult {
    data class Ok(val file: DeckFile) : ParseResult
    data class Err(val message: String) : ParseResult
}

object DeckCodec {
    const val FORMAT = "kartka"
    const val VERSION = 1
    const val MAX_TEXT = 8_000
    const val MAX_NAME = 200
    const val MAX_LABEL = 80
    const val MAX_UID = 80

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }

    fun exportTopic(topic: TopicSnapshot, exportedAt: Instant = Instant.now()): String {
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(
                format = FORMAT,
                version = VERSION,
                kind = "topic",
                exportedAt = exportedAt.toString(),
                topic = topic.toPayload(),
            ),
        )
    }

    fun exportCollection(
        topicName: String,
        frontLabel: String,
        backLabel: String,
        collection: CollectionSnapshot,
        exportedAt: Instant = Instant.now(),
    ): String {
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(
                format = FORMAT,
                version = VERSION,
                kind = "collection",
                exportedAt = exportedAt.toString(),
                collection = CollectionFilePayload(
                    uid = collection.uid,
                    name = collection.name,
                    topicName = topicName,
                    frontLabel = frontLabel,
                    backLabel = backLabel,
                    cards = collection.cards.map { it.toPayload() },
                ),
            ),
        )
    }

    fun parse(text: String, nowEpochMs: Long): ParseResult {
        if (text.length > 20 * 1024 * 1024) {
            return ParseResult.Err("That file is larger than 20 MB.")
        }
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (_: SerializationException) {
            return ParseResult.Err("This file is not valid JSON.")
        } catch (_: IllegalArgumentException) {
            return ParseResult.Err("This file is not valid JSON.")
        }
        return try {
            ParseResult.Ok(read(envelope, nowEpochMs))
        } catch (error: DeckFormatException) {
            ParseResult.Err(error.message)
        }
    }

    private fun read(envelope: Envelope, nowEpochMs: Long): DeckFile {
        if (envelope.format != FORMAT) throw DeckFormatException("This file is not a Kartka deck.")
        if (envelope.version != VERSION) {
            throw DeckFormatException(
                "This file uses Kartka format version ${envelope.version}. This app reads version $VERSION.",
            )
        }
        if (envelope.exportedAt != null) parseInstant(envelope.exportedAt, "exportedAt")
        return when (envelope.kind) {
            "topic" -> {
                val topic = envelope.topic ?: throw DeckFormatException("The file is missing a topic.")
                DeckFile.Topic(envelope.exportedAt, topic.toSnapshot(nowEpochMs))
            }
            "collection" -> {
                val collection = envelope.collection
                    ?: throw DeckFormatException("The file is missing a collection.")
                val seen = HashSet<String>()
                fun claim(uid: String) {
                    if (!seen.add(uid)) throw DeckFormatException("This file contains the same id twice.")
                }
                val collectionUid = requiredUid(collection.uid)
                claim(collectionUid)
                DeckFile.Collection(
                    exportedAt = envelope.exportedAt,
                    topicName = requiredName(collection.topicName, "topic name"),
                    frontLabel = requiredLabel(collection.frontLabel, "front label"),
                    backLabel = requiredLabel(collection.backLabel, "back label"),
                    collection = CollectionSnapshot(
                        uid = collectionUid,
                        name = requiredName(collection.name, "collection name"),
                        cards = collection.cards.map { card ->
                            val snapshot = card.toSnapshot(nowEpochMs)
                            claim(snapshot.uid)
                            snapshot
                        },
                    ),
                )
            }
            else -> throw DeckFormatException("This file is not a Kartka topic or collection.")
        }
    }

    private fun TopicPayload.toSnapshot(nowEpochMs: Long): TopicSnapshot {
        val seen = HashSet<String>()
        fun claim(uid: String) {
            if (!seen.add(uid)) throw DeckFormatException("This file contains the same id twice.")
        }
        val topicUid = requiredUid(uid)
        claim(topicUid)
        return TopicSnapshot(
            uid = topicUid,
            name = requiredName(name, "topic name"),
            frontLabel = requiredLabel(frontLabel, "front label"),
            backLabel = requiredLabel(backLabel, "back label"),
            color = TopicColors.normalize(color),
            collections = collections.map { collection ->
                val collectionUid = requiredUid(collection.uid)
                claim(collectionUid)
                CollectionSnapshot(
                    uid = collectionUid,
                    name = requiredName(collection.name, "collection name"),
                    cards = collection.cards.map { card ->
                        val cardUid = requiredUid(card.uid)
                        claim(cardUid)
                        card.toSnapshot(nowEpochMs)
                    },
                )
            },
        )
    }

    private fun CardPayload.toSnapshot(nowEpochMs: Long): CardSnapshot {
        val scheduleMissing = ease == null &&
            intervalDays == null &&
            repetitions == null &&
            lapses == null &&
            dueAt == null &&
            lastReviewedAt == null
        val schedule = if (scheduleMissing) {
            ScheduleState.fresh(nowEpochMs)
        } else {
            if (ease != null && (ease.isNaN() || ease.isInfinite())) {
                throw DeckFormatException("A card has a review value that is not a number.")
            }
            val reps = repetitions ?: 0
            val lapseCount = lapses ?: 0
            val interval = intervalDays ?: 0
            if (reps < 0 || lapseCount < 0 || interval < 0) {
                throw DeckFormatException("A card has a negative review value.")
            }
            ScheduleState(
                ease = (ease ?: Scheduler.DEFAULT_EASE).coerceIn(Scheduler.MIN_EASE, Scheduler.MAX_EASE),
                intervalDays = interval.coerceAtMost(Scheduler.MAX_INTERVAL_DAYS),
                repetitions = reps,
                lapses = lapseCount,
                dueAtEpochMs = dueAt?.let { parseInstant(it, "due date").toEpochMilli() } ?: nowEpochMs,
                lastReviewedAtEpochMs = lastReviewedAt?.let { parseInstant(it, "review date").toEpochMilli() },
            )
        }
        return CardSnapshot(
            uid = requiredUid(uid),
            front = requiredText(front, "front"),
            back = requiredText(back, "back"),
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.also { requiredText(it, "note") },
            schedule = schedule,
        )
    }

    private fun parseInstant(value: String, label: String): Instant {
        try {
            return Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw DeckFormatException("A $label in the file is not valid.")
        }
    }

    private fun requiredUid(value: String): String {
        val uid = value.trim()
        if (!UID.matches(uid)) throw DeckFormatException("A card or deck id in the file is not valid.")
        return uid
    }

    private fun requiredName(value: String, label: String): String {
        val name = value.trim()
        if (name.isEmpty()) throw DeckFormatException("A $label is empty.")
        if (name.length > MAX_NAME) throw DeckFormatException("A $label is too long.")
        return name
    }

    private fun requiredLabel(value: String, label: String): String {
        val text = value.trim()
        if (text.isEmpty()) throw DeckFormatException("A $label is empty.")
        if (text.length > MAX_LABEL) throw DeckFormatException("A $label is too long.")
        return text
    }

    private fun requiredText(value: String, label: String): String {
        val text = value.trim()
        if (text.isEmpty()) throw DeckFormatException("A card is missing its $label.")
        if (text.length > MAX_TEXT) throw DeckFormatException("A card $label is too long.")
        return text
    }

    private val UID = Regex("^[A-Za-z0-9_.:-]{1,$MAX_UID}$")

    private fun TopicSnapshot.toPayload() = TopicPayload(
        uid = uid,
        name = name,
        frontLabel = frontLabel,
        backLabel = backLabel,
        color = color,
        collections = collections.map { collection ->
            CollectionPayload(
                uid = collection.uid,
                name = collection.name,
                cards = collection.cards.map { it.toPayload() },
            )
        },
    )

    private fun CardSnapshot.toPayload() = CardPayload(
        uid = uid,
        front = front,
        back = back,
        note = note,
        ease = schedule.ease,
        intervalDays = schedule.intervalDays,
        repetitions = schedule.repetitions,
        lapses = schedule.lapses,
        dueAt = Instant.ofEpochMilli(schedule.dueAtEpochMs).toString(),
        lastReviewedAt = schedule.lastReviewedAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
    )
}

private class DeckFormatException(override val message: String) : IllegalArgumentException(message)

@Serializable
private data class Envelope(
    val format: String,
    val version: Int,
    val kind: String,
    val exportedAt: String? = null,
    val topic: TopicPayload? = null,
    val collection: CollectionFilePayload? = null,
)

@Serializable
private data class TopicPayload(
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String = "teal",
    val collections: List<CollectionPayload> = emptyList(),
)

@Serializable
private data class CollectionPayload(
    val uid: String,
    val name: String,
    val cards: List<CardPayload> = emptyList(),
)

@Serializable
private data class CollectionFilePayload(
    val uid: String,
    val name: String,
    val topicName: String,
    val frontLabel: String,
    val backLabel: String,
    val cards: List<CardPayload> = emptyList(),
)

@Serializable
private data class CardPayload(
    val uid: String,
    val front: String,
    val back: String,
    val note: String? = null,
    val ease: Double? = null,
    val intervalDays: Int? = null,
    val repetitions: Int? = null,
    val lapses: Int? = null,
    val dueAt: String? = null,
    val lastReviewedAt: String? = null,
)
