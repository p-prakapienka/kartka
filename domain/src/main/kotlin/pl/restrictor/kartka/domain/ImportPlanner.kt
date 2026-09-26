package pl.restrictor.kartka.domain

enum class DeckKind { TOPIC, COLLECTION }

enum class ImportMode { UPDATE, COPY }

data class KnownIds(
    val topics: Set<String> = emptySet(),
    val collections: Set<String> = emptySet(),
    val cards: Set<String> = emptySet(),
) {
    fun contains(uid: String): Boolean = uid in topics || uid in collections || uid in cards
}

data class ImportPreview(
    val kind: DeckKind,
    val title: String,
    val collectionCount: Int,
    val cardCount: Int,
    val collidingCount: Int,
    val frontLabel: String,
    val backLabel: String,
)

data class TopicUpsert(
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String,
)

data class CollectionUpsert(
    val uid: String,
    val name: String,
    val topicUid: String?,
    val enforceParent: Boolean,
)

data class CardUpsert(
    val uid: String,
    val collectionUid: String,
    val front: String,
    val back: String,
    val note: String?,
    val schedule: ScheduleState,
)

data class ImportPlan(
    val preview: ImportPreview,
    val topics: List<TopicUpsert>,
    val collections: List<CollectionUpsert>,
    val cards: List<CardUpsert>,
    val warnings: List<String>,
)

class ImportRejected(message: String) : IllegalArgumentException(message)

object ImportPlanner {
    fun preview(file: DeckFile, known: KnownIds): ImportPreview = when (file) {
        is DeckFile.Topic -> ImportPreview(
            kind = DeckKind.TOPIC,
            title = file.topic.name,
            collectionCount = file.topic.collections.size,
            cardCount = file.topic.collections.sumOf { it.cards.size },
            collidingCount = uids(file).count { known.contains(it) },
            frontLabel = file.topic.frontLabel,
            backLabel = file.topic.backLabel,
        )
        is DeckFile.Collection -> ImportPreview(
            kind = DeckKind.COLLECTION,
            title = file.collection.name,
            collectionCount = 1,
            cardCount = file.collection.cards.size,
            collidingCount = uids(file).count { known.contains(it) },
            frontLabel = file.frontLabel,
            backLabel = file.backLabel,
        )
    }

    fun plan(
        file: DeckFile,
        known: KnownIds,
        mode: ImportMode,
        destinationTopicUid: String?,
        destinationFrontLabel: String?,
        destinationBackLabel: String?,
        newUid: () -> String,
    ): ImportPlan {
        val source = if (mode == ImportMode.COPY) remap(file, newUid) else file
        val warnings = labelWarning(source, destinationFrontLabel, destinationBackLabel)
        val topics = ArrayList<TopicUpsert>()
        val collections = ArrayList<CollectionUpsert>()
        val cards = ArrayList<CardUpsert>()
        when (source) {
            is DeckFile.Topic -> {
                topics += TopicUpsert(
                    uid = source.topic.uid,
                    name = source.topic.name,
                    frontLabel = source.topic.frontLabel,
                    backLabel = source.topic.backLabel,
                    color = TopicColors.normalize(source.topic.color),
                )
                for (collection in source.topic.collections) {
                    collections += CollectionUpsert(
                        uid = collection.uid,
                        name = collection.name,
                        topicUid = source.topic.uid,
                        enforceParent = true,
                    )
                    cards += collection.cards.map { it.toUpsert(collection.uid) }
                }
            }
            is DeckFile.Collection -> {
                val exists = source.collection.uid in known.collections && mode == ImportMode.UPDATE
                if (!exists && destinationTopicUid.isNullOrBlank()) {
                    throw ImportRejected("Choose a topic first.")
                }
                collections += CollectionUpsert(
                    uid = source.collection.uid,
                    name = source.collection.name,
                    topicUid = if (exists) null else destinationTopicUid,
                    enforceParent = !exists,
                )
                cards += source.collection.cards.map { it.toUpsert(source.collection.uid) }
            }
        }
        return ImportPlan(
            preview = preview(file, known),
            topics = topics,
            collections = collections,
            cards = cards,
            warnings = warnings,
        )
    }

    private fun labelWarning(
        file: DeckFile,
        destinationFrontLabel: String?,
        destinationBackLabel: String?,
    ): List<String> {
        if (file !is DeckFile.Collection) return emptyList()
        if (destinationFrontLabel == null || destinationBackLabel == null) return emptyList()
        val sameFront = file.frontLabel.equals(destinationFrontLabel, ignoreCase = true)
        val sameBack = file.backLabel.equals(destinationBackLabel, ignoreCase = true)
        if (sameFront && sameBack) return emptyList()
        return listOf(
            "This file is labeled ${file.frontLabel} → ${file.backLabel}. " +
                "The topic you picked is $destinationFrontLabel → $destinationBackLabel.",
        )
    }

    private fun remap(file: DeckFile, newUid: () -> String): DeckFile = when (file) {
        is DeckFile.Topic -> file.copy(
            topic = file.topic.copy(
                uid = newUid(),
                collections = file.topic.collections.map { collection ->
                    collection.copy(
                        uid = newUid(),
                        cards = collection.cards.map { it.copy(uid = newUid()) },
                    )
                },
            ),
        )
        is DeckFile.Collection -> file.copy(
            collection = file.collection.copy(
                uid = newUid(),
                cards = file.collection.cards.map { it.copy(uid = newUid()) },
            ),
        )
    }

    private fun uids(file: DeckFile): List<String> = when (file) {
        is DeckFile.Topic -> buildList {
            add(file.topic.uid)
            file.topic.collections.forEach { collection ->
                add(collection.uid)
                collection.cards.forEach { add(it.uid) }
            }
        }
        is DeckFile.Collection -> buildList {
            add(file.collection.uid)
            file.collection.cards.forEach { add(it.uid) }
        }
    }

    private fun CardSnapshot.toUpsert(collectionUid: String) = CardUpsert(
        uid = uid,
        collectionUid = collectionUid,
        front = front,
        back = back,
        note = note,
        schedule = schedule,
    )
}
