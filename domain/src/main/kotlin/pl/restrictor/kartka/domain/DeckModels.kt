package pl.restrictor.kartka.domain

data class CardSnapshot(
    val uid: String,
    val front: String,
    val back: String,
    val note: String?,
    val schedule: ScheduleState,
)

data class CollectionSnapshot(
    val uid: String,
    val name: String,
    val cards: List<CardSnapshot>,
)

data class TopicSnapshot(
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String,
    val collections: List<CollectionSnapshot>,
)

sealed interface DeckFile {
    val exportedAt: String?

    data class Topic(
        override val exportedAt: String?,
        val topic: TopicSnapshot,
    ) : DeckFile

    data class Collection(
        override val exportedAt: String?,
        val topicName: String,
        val frontLabel: String,
        val backLabel: String,
        val collection: CollectionSnapshot,
    ) : DeckFile
}

object TopicColors {
    val keys = listOf("teal", "blue", "violet", "rose", "amber", "green")

    fun normalize(value: String): String {
        val key = value.trim().lowercase()
        return if (key in keys) key else "teal"
    }
}
