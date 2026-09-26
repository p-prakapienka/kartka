package pl.restrictor.kartka.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "topics",
    indices = [Index(value = ["uid"], unique = true)],
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("topicId"), Index(value = ["uid"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val topicId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("dueAt"), Index(value = ["uid"], unique = true)],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val collectionId: Long,
    val front: String,
    val back: String,
    val note: String?,
    val ease: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TopicSummary(
    val id: Long,
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
    val color: String,
    val cardCount: Int,
    val dueCount: Int,
)

data class CollectionSummary(
    val id: Long,
    val uid: String,
    val name: String,
    val cardCount: Int,
    val dueCount: Int,
)
