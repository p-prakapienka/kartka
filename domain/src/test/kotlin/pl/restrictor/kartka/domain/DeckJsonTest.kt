package pl.restrictor.kartka.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckJsonTest {
    private val now = Instant.parse("2026-09-26T16:00:00Z")
    private val nowMs = now.toEpochMilli()

    private val topic = TopicSnapshot(
        uid = "topic-1",
        name = "English → Polish",
        frontLabel = "English",
        backLabel = "Polish",
        color = "blue",
        collections = listOf(
            CollectionSnapshot(
                uid = "col-1",
                name = "Food",
                cards = listOf(
                    CardSnapshot(
                        uid = "card-1",
                        front = "apple",
                        back = "jabłko",
                        note = null,
                        schedule = ScheduleState(2.5, 16, 4, 1, nowMs, nowMs),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun topicRoundTripKeepsTextAndSchedule() {
        val parsed = DeckCodec.parse(DeckCodec.exportTopic(topic, now), nowMs) as ParseResult.Ok
        val file = parsed.file as DeckFile.Topic
        assertEquals(topic, file.topic)
        assertEquals(now.toString(), file.exportedAt)
    }

    @Test
    fun collectionRoundTripKeepsParentLabels() {
        val json = DeckCodec.exportCollection(
            topicName = "English → Polish",
            frontLabel = "English",
            backLabel = "Polish",
            collection = topic.collections.first(),
            exportedAt = now,
        )
        val parsed = DeckCodec.parse(json, nowMs) as ParseResult.Ok
        val file = parsed.file as DeckFile.Collection
        assertEquals("Food", file.collection.name)
        assertEquals("English", file.frontLabel)
        assertEquals("Polish", file.backLabel)
        assertEquals(topic.collections.first(), file.collection)
    }

    @Test
    fun missingScheduleBecomesANewCard() {
        val json = """
            {
              "format": "kartka",
              "version": 1,
              "kind": "collection",
              "collection": {
                "uid": "col-1",
                "name": "Food",
                "topicName": "English → Polish",
                "frontLabel": "English",
                "backLabel": "Polish",
                "cards": [{ "uid": "card-9", "front": "bread", "back": "chleb", "extra": true }]
              }
            }
        """.trimIndent()
        val file = (DeckCodec.parse(json, nowMs) as ParseResult.Ok).file as DeckFile.Collection
        assertEquals(ScheduleState.fresh(nowMs), file.collection.cards.single().schedule)
    }

    @Test
    fun unknownVersionAndBadFormatImportNothing() {
        val wrongFormat = DeckCodec.parse("""{"format":"anki","version":1,"kind":"topic"}""", nowMs)
        assertTrue((wrongFormat as ParseResult.Err).message.contains("not a Kartka deck"))

        val wrongVersion = DeckCodec.parse(
            """{"format":"kartka","version":2,"kind":"topic","topic":{"uid":"t","name":"N","frontLabel":"A","backLabel":"B"}}""",
            nowMs,
        )
        assertTrue((wrongVersion as ParseResult.Err).message.contains("version 2"))
    }

    @Test
    fun emptySideIsRejected() {
        val json = """
            {"format":"kartka","version":1,"kind":"collection","collection":{
              "uid":"col-1","name":"Food","topicName":"T","frontLabel":"A","backLabel":"B",
              "cards":[{"uid":"card-1","front":"  ","back":"x"}]
            }}
        """.trimIndent()
        val result = DeckCodec.parse(json, nowMs)
        assertTrue(result is ParseResult.Err)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val json = """
            {"format":"kartka","version":1,"kind":"collection","collection":{
              "uid":"same","name":"Food","topicName":"T","frontLabel":"A","backLabel":"B",
              "cards":[{"uid":"same","front":"a","back":"b"}]
            }}
        """.trimIndent()
        assertTrue(DeckCodec.parse(json, nowMs) is ParseResult.Err)
    }

    @Test
    fun updateKeepsIdsAndCopyMintsNewOnes() {
        val file = (DeckCodec.parse(DeckCodec.exportTopic(topic, now), nowMs) as ParseResult.Ok).file
        val known = KnownIds(topics = setOf("topic-1"), collections = setOf("col-1"), cards = setOf("card-1"))
        val update = ImportPlanner.plan(file, known, ImportMode.UPDATE, null, null, null) { error("no") }
        assertEquals("topic-1", update.topics.single().uid)
        assertEquals("card-1", update.cards.single().uid)
        assertEquals(16, update.cards.single().schedule.intervalDays)
        assertEquals(3, update.preview.collidingCount)

        var n = 0
        val copy = ImportPlanner.plan(file, known, ImportMode.COPY, null, null, null) { "new-${n++}" }
        assertEquals("new-0", copy.topics.single().uid)
        assertEquals("new-1", copy.collections.single().uid)
        assertEquals("new-2", copy.cards.single().uid)
        assertEquals("jabłko", copy.cards.single().back)
        assertNull(copy.cards.single().note)
    }

    @Test
    fun collectionImportLandsInTheChosenTopicAndWarnsOnLabels() {
        val file = DeckFile.Collection(
            exportedAt = null,
            topicName = "Russian → Belarusian",
            frontLabel = "Russian",
            backLabel = "Belarusian",
            collection = CollectionSnapshot(
                uid = "food",
                name = "Food",
                cards = listOf(
                    CardSnapshot("milk", "milk", "малако", null, ScheduleState.fresh(nowMs)),
                ),
            ),
        )
        val plan = ImportPlanner.plan(
            file = file,
            known = KnownIds(),
            mode = ImportMode.UPDATE,
            destinationTopicUid = "topic-pl",
            destinationFrontLabel = "English",
            destinationBackLabel = "Polish",
            newUid = { error("no") },
        )
        assertEquals("topic-pl", plan.collections.single().topicUid)
        assertTrue(plan.collections.single().enforceParent)
        assertEquals(1, plan.warnings.size)

        val existing = ImportPlanner.plan(
            file = file,
            known = KnownIds(collections = setOf("food")),
            mode = ImportMode.UPDATE,
            destinationTopicUid = null,
            destinationFrontLabel = null,
            destinationBackLabel = null,
            newUid = { error("no") },
        )
        assertEquals(false, existing.collections.single().enforceParent)
        assertNull(existing.collections.single().topicUid)
    }
}
