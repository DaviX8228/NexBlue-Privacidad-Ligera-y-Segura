package com.nexblue.app.data.dao

import androidx.room.*
import com.nexblue.app.data.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY lastTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE userId = :userId LIMIT 1")
    suspend fun getChatByUserId(userId: String): ChatEntity?

    @Query("DELETE FROM chats WHERE userId = :userId")
    suspend fun deleteChat(userId: String)

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastTimestamp = :timestamp WHERE userId = :userId")
    suspend fun updateLastMessage(userId: String, lastMessage: String, timestamp: Long)

}