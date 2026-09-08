package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextpage.data.local.entity.DiscoverCacheEntity

/**
 * DAO for the isolated catalog cache. No method touches user_books,
 * outbox, or sync tables — isolation is structural, not conventional.
 */
@Dao
interface DiscoverCacheDao {
    @Query("SELECT * FROM discover_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): DiscoverCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: DiscoverCacheEntity)

    @Query("DELETE FROM discover_cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT COUNT(*) FROM discover_cache")
    suspend fun count(): Int
}
