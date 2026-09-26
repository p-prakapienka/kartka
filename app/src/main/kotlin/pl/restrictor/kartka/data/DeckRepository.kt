package pl.restrictor.kartka.data

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import pl.restrictor.kartka.data.db.AppDatabase
import pl.restrictor.kartka.data.db.CardEntity
import pl.restrictor.kartka.data.db.CollectionEntity
import pl.restrictor.kartka.data.db.CollectionSummary
import pl.restrictor.kartka.data.db.TopicEntity
import pl.restrictor.kartka.data.db.TopicSummary
import pl.restrictor.kartka.domain.CardSnapshot
import pl.restrictor.kartka.domain.CollectionSnapshot
import pl.restrictor.kartka.domain.DeckCodec
import pl.restrictor.kartka.domain.ImportPlan
import pl.restrictor.kartka.domain.KnownIds
import pl.restrictor.kartka.domain.Rating
import pl.restrictor.kartka.domain.ScheduleState
import pl.restrictor.kartka.domain.Scheduler
import pl.restrictor.kartka.domain.TopicSnapshot
import kotlinx.coroutines.flow.Flow

data class StudyCard(
    val id: Long,
    val front: String,
    val back: String,
    val note: String?,
)

data class StudyLoad(
    val title: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String,
    val cards: List<StudyCard>,
    val nextDueAt: Long?,
    val hasAnyCards: Boolean,
)

sealed interface StudyTarget {
    data class Topic(val id: Long) : StudyTarget
    data class Collection(val id: Long) : StudyTarget
}

data class ExportedDeck(
    val fileName: String,
    val json: String,
)

class DeckRepository(private val db: AppDatabase) {
    fun observeTopics(now: Long): Flow<List<TopicSummary>> = db.topics().observeSummaries(now)

    fun observeCollections(topicId: Long, now: Long): Flow<List<CollectionSummary>> =
        db.collections().observeSummaries(topicId, now)

    fun observeCards(collectionId: Long): Flow<List<CardEntity>> = db.cards().observeInCollection(collectionId)

    suspend fun topic(id: Long): TopicEntity? = db.topics().get(id)

    suspend fun collection(id: Long): CollectionEntity? = db.collections().get(id)

    suspend fun topics(): List<TopicEntity> = db.topics().list()

    suspend fun knownIds(): KnownIds = KnownIds(
        topics = db.topics().uids().toSet(),
        collections = db.collections().uids().toSet(),
        cards = db.cards().uids().toSet(),
    )

    suspend fun createTopic(name: String, frontLabel: String, backLabel: String, color: String, now: Long): TopicEntity {
        val entity = TopicEntity(
            uid = newUid(),
            name = name.trim(),
            frontLabel = frontLabel.trim(),
            backLabel = backLabel.trim(),
            color = color,
            createdAt = now,
            updatedAt = now,
        )
        val id = db.topics().insert(entity)
        return entity.copy(id = id)
    }

    suspend fun updateTopic(id: Long, name: String, frontLabel: String, backLabel: String, color: String, now: Long) {
        val existing = db.topics().get(id) ?: return
        db.topics().update(
            existing.copy(
                name = name.trim(),
                frontLabel = frontLabel.trim(),
                backLabel = backLabel.trim(),
                color = color,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteTopic(id: Long) = db.topics().delete(id)

    suspend fun createCollection(topicId: Long, name: String, now: Long): CollectionEntity {
        val entity = CollectionEntity(
            uid = newUid(),
            topicId = topicId,
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
        )
        val id = db.collections().insert(entity)
        return entity.copy(id = id)
    }

    suspend fun updateCollection(id: Long, name: String, now: Long) {
        val existing = db.collections().get(id) ?: return
        db.collections().update(existing.copy(name = name.trim(), updatedAt = now))
    }

    suspend fun deleteCollection(id: Long) = db.collections().delete(id)

    suspend fun createCard(collectionId: Long, front: String, back: String, note: String?, now: Long) {
        val schedule = ScheduleState.fresh(now)
        db.cards().insert(
            CardEntity(
                uid = newUid(),
                collectionId = collectionId,
                front = front.trim(),
                back = back.trim(),
                note = note?.trim()?.ifEmpty { null },
                ease = schedule.ease,
                intervalDays = schedule.intervalDays,
                repetitions = schedule.repetitions,
                lapses = schedule.lapses,
                dueAt = schedule.dueAtEpochMs,
                lastReviewedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateCard(id: Long, front: String, back: String, note: String?, now: Long) {
        val existing = db.cards().get(id) ?: return
        db.cards().update(
            existing.copy(
                front = front.trim(),
                back = back.trim(),
                note = note?.trim()?.ifEmpty { null },
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteCard(id: Long) = db.cards().delete(id)

    suspend fun review(cardId: Long, rating: Rating, now: Long) {
        val card = db.cards().get(cardId) ?: return
        val next = Scheduler.review(card.toSchedule(), rating, now)
        db.cards().update(
            card.copy(
                ease = next.ease,
                intervalDays = next.intervalDays,
                repetitions = next.repetitions,
                lapses = next.lapses,
                dueAt = next.dueAtEpochMs,
                lastReviewedAt = next.lastReviewedAtEpochMs,
                updatedAt = now,
            ),
        )
    }

    suspend fun loadStudy(target: StudyTarget, now: Long, early: Boolean): StudyLoad? {
        return when (target) {
            is StudyTarget.Topic -> {
                val topic = db.topics().get(target.id) ?: return null
                val cards = if (early) db.cards().upcomingForTopic(topic.id, now) else db.cards().dueForTopic(topic.id, now)
                StudyLoad(
                    title = topic.name,
                    frontLabel = topic.frontLabel,
                    backLabel = topic.backLabel,
                    color = topic.color,
                    cards = cards.map { it.toStudyCard() },
                    nextDueAt = if (early) null else db.cards().nextDueForTopic(topic.id, now),
                    hasAnyCards = db.cards().countForTopic(topic.id) > 0,
                )
            }
            is StudyTarget.Collection -> {
                val collection = db.collections().get(target.id) ?: return null
                val topic = db.topics().get(collection.topicId) ?: return null
                val cards = if (early) {
                    db.cards().upcomingInCollection(collection.id, now)
                } else {
                    db.cards().dueInCollection(collection.id, now)
                }
                StudyLoad(
                    title = collection.name,
                    frontLabel = topic.frontLabel,
                    backLabel = topic.backLabel,
                    color = topic.color,
                    cards = cards.map { it.toStudyCard() },
                    nextDueAt = if (early) null else db.cards().nextDueInCollection(collection.id, now),
                    hasAnyCards = db.cards().countInCollection(collection.id) > 0,
                )
            }
        }
    }

    suspend fun nextDue(target: StudyTarget, now: Long): Long? = when (target) {
        is StudyTarget.Topic -> db.cards().nextDueForTopic(target.id, now)
        is StudyTarget.Collection -> db.cards().nextDueInCollection(target.id, now)
    }

    suspend fun exportTopic(id: Long): ExportedDeck? {
        val topic = db.topics().get(id) ?: return null
        val collections = db.collections().listForTopic(id).map { collection ->
            CollectionSnapshot(
                uid = collection.uid,
                name = collection.name,
                cards = db.cards().listInCollection(collection.id).map { it.toSnapshot() },
            )
        }
        val snapshot = TopicSnapshot(
            uid = topic.uid,
            name = topic.name,
            frontLabel = topic.frontLabel,
            backLabel = topic.backLabel,
            color = topic.color,
            collections = collections,
        )
        return ExportedDeck(exportFileName(topic.name), DeckCodec.exportTopic(snapshot, Instant.now()))
    }

    suspend fun exportCollection(id: Long): ExportedDeck? {
        val collection = db.collections().get(id) ?: return null
        val topic = db.topics().get(collection.topicId) ?: return null
        val snapshot = CollectionSnapshot(
            uid = collection.uid,
            name = collection.name,
            cards = db.cards().listInCollection(collection.id).map { it.toSnapshot() },
        )
        return ExportedDeck(
            exportFileName(collection.name),
            DeckCodec.exportCollection(topic.name, topic.frontLabel, topic.backLabel, snapshot, Instant.now()),
        )
    }

    suspend fun apply(plan: ImportPlan, now: Long) {
        db.withTransaction {
            val topicIds = HashMap<String, Long>()
            for (topic in plan.topics) {
                val existing = db.topics().findByUid(topic.uid)
                topicIds[topic.uid] = if (existing == null) {
                    db.topics().insert(
                        TopicEntity(
                            uid = topic.uid,
                            name = topic.name,
                            frontLabel = topic.frontLabel,
                            backLabel = topic.backLabel,
                            color = topic.color,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else {
                    db.topics().update(
                        existing.copy(
                            name = topic.name,
                            frontLabel = topic.frontLabel,
                            backLabel = topic.backLabel,
                            color = topic.color,
                            updatedAt = now,
                        ),
                    )
                    existing.id
                }
            }
            val collectionIds = HashMap<String, Long>()
            for (collection in plan.collections) {
                val existing = db.collections().findByUid(collection.uid)
                collectionIds[collection.uid] = if (existing == null) {
                    val topicUid = collection.topicUid ?: error("Import is missing a topic.")
                    val topicId = topicIds[topicUid] ?: db.topics().findByUid(topicUid)?.id
                        ?: error("Import is missing a topic.")
                    db.collections().insert(
                        CollectionEntity(
                            uid = collection.uid,
                            topicId = topicId,
                            name = collection.name,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else {
                    val parentUid = collection.topicUid
                    val topicId = if (collection.enforceParent && parentUid != null) {
                        topicIds[parentUid] ?: db.topics().findByUid(parentUid)?.id ?: existing.topicId
                    } else {
                        existing.topicId
                    }
                    db.collections().update(existing.copy(name = collection.name, topicId = topicId, updatedAt = now))
                    existing.id
                }
            }
            for (card in plan.cards) {
                val collectionId = collectionIds[card.collectionUid]
                    ?: db.collections().findByUid(card.collectionUid)?.id
                    ?: error("Import is missing a collection.")
                val existing = db.cards().findByUid(card.uid)
                if (existing == null) {
                    db.cards().insert(
                        CardEntity(
                            uid = card.uid,
                            collectionId = collectionId,
                            front = card.front,
                            back = card.back,
                            note = card.note,
                            ease = card.schedule.ease,
                            intervalDays = card.schedule.intervalDays,
                            repetitions = card.schedule.repetitions,
                            lapses = card.schedule.lapses,
                            dueAt = card.schedule.dueAtEpochMs,
                            lastReviewedAt = card.schedule.lastReviewedAtEpochMs,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else {
                    db.cards().update(
                        existing.copy(
                            collectionId = collectionId,
                            front = card.front,
                            back = card.back,
                            note = card.note,
                            ease = card.schedule.ease,
                            intervalDays = card.schedule.intervalDays,
                            repetitions = card.schedule.repetitions,
                            lapses = card.schedule.lapses,
                            dueAt = card.schedule.dueAtEpochMs,
                            lastReviewedAt = card.schedule.lastReviewedAtEpochMs,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
    }

    private fun CardEntity.toSnapshot() = CardSnapshot(
        uid = uid,
        front = front,
        back = back,
        note = note,
        schedule = toSchedule(),
    )

    private fun CardEntity.toSchedule() = ScheduleState(
        ease = ease,
        intervalDays = intervalDays,
        repetitions = repetitions,
        lapses = lapses,
        dueAtEpochMs = dueAt,
        lastReviewedAtEpochMs = lastReviewedAt,
    )

    private fun CardEntity.toStudyCard() = StudyCard(id = id, front = front, back = back, note = note)

    private fun newUid(): String = UUID.randomUUID().toString()
}

fun exportFileName(name: String): String {
    val clean = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "-").take(60).ifBlank { "kartka" }
    return "$clean.kartka.json"
}
