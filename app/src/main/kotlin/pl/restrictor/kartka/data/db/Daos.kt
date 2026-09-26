package pl.restrictor.kartka.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Query(
        """
        SELECT t.id, t.uid, t.name, t.frontLabel, t.backLabel, t.color,
          (SELECT COUNT(*) FROM cards c
            INNER JOIN collections col ON col.id = c.collectionId
            WHERE col.topicId = t.id) AS cardCount,
          (SELECT COUNT(*) FROM cards c
            INNER JOIN collections col ON col.id = c.collectionId
            WHERE col.topicId = t.id AND c.dueAt <= :now) AS dueCount
        FROM topics t
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(now: Long): Flow<List<TopicSummary>>

    @Query("SELECT * FROM topics ORDER BY name COLLATE NOCASE")
    suspend fun list(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun get(id: Long): TopicEntity?

    @Query("SELECT * FROM topics WHERE uid = :uid")
    suspend fun findByUid(uid: String): TopicEntity?

    @Query("SELECT uid FROM topics")
    suspend fun uids(): List<String>

    @Insert
    suspend fun insert(entity: TopicEntity): Long

    @Update
    suspend fun update(entity: TopicEntity)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CollectionDao {
    @Query(
        """
        SELECT c.id, c.uid, c.name,
          (SELECT COUNT(*) FROM cards WHERE collectionId = c.id) AS cardCount,
          (SELECT COUNT(*) FROM cards WHERE collectionId = c.id AND dueAt <= :now) AS dueCount
        FROM collections c
        WHERE c.topicId = :topicId
        ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(topicId: Long, now: Long): Flow<List<CollectionSummary>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun get(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE topicId = :topicId ORDER BY name COLLATE NOCASE")
    suspend fun listForTopic(topicId: Long): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE uid = :uid")
    suspend fun findByUid(uid: String): CollectionEntity?

    @Query("SELECT uid FROM collections")
    suspend fun uids(): List<String>

    @Insert
    suspend fun insert(entity: CollectionEntity): Long

    @Update
    suspend fun update(entity: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE collectionId = :collectionId ORDER BY front COLLATE NOCASE")
    fun observeInCollection(collectionId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE uid = :uid")
    suspend fun findByUid(uid: String): CardEntity?

    @Query("SELECT uid FROM cards")
    suspend fun uids(): List<String>

    @Query(
        """
        SELECT * FROM cards
        WHERE collectionId = :collectionId AND dueAt <= :now
        ORDER BY dueAt ASC, id ASC
        """,
    )
    suspend fun dueInCollection(collectionId: Long, now: Long): List<CardEntity>

    @Query(
        """
        SELECT * FROM cards
        WHERE collectionId = :collectionId AND dueAt > :now
        ORDER BY dueAt ASC, id ASC
        """,
    )
    suspend fun upcomingInCollection(collectionId: Long, now: Long): List<CardEntity>

    @Query(
        """
        SELECT dueAt FROM cards
        WHERE collectionId = :collectionId AND dueAt > :now
        ORDER BY dueAt ASC
        LIMIT 1
        """,
    )
    suspend fun nextDueInCollection(collectionId: Long, now: Long): Long?

    @Query("SELECT COUNT(*) FROM cards WHERE collectionId = :collectionId")
    suspend fun countInCollection(collectionId: Long): Int

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN collections ON collections.id = cards.collectionId
        WHERE collections.topicId = :topicId AND cards.dueAt <= :now
        ORDER BY cards.dueAt ASC, cards.id ASC
        """,
    )
    suspend fun dueForTopic(topicId: Long, now: Long): List<CardEntity>

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN collections ON collections.id = cards.collectionId
        WHERE collections.topicId = :topicId AND cards.dueAt > :now
        ORDER BY cards.dueAt ASC, cards.id ASC
        """,
    )
    suspend fun upcomingForTopic(topicId: Long, now: Long): List<CardEntity>

    @Query(
        """
        SELECT cards.dueAt FROM cards
        INNER JOIN collections ON collections.id = cards.collectionId
        WHERE collections.topicId = :topicId AND cards.dueAt > :now
        ORDER BY cards.dueAt ASC
        LIMIT 1
        """,
    )
    suspend fun nextDueForTopic(topicId: Long, now: Long): Long?

    @Query(
        """
        SELECT COUNT(*) FROM cards
        INNER JOIN collections ON collections.id = cards.collectionId
        WHERE collections.topicId = :topicId
        """,
    )
    suspend fun countForTopic(topicId: Long): Int

    @Query("SELECT * FROM cards WHERE collectionId = :collectionId ORDER BY id ASC")
    suspend fun listInCollection(collectionId: Long): List<CardEntity>

    @Insert
    suspend fun insert(entity: CardEntity): Long

    @Update
    suspend fun update(entity: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)
}
