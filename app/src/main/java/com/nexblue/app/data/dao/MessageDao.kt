package com.nexblue.app.data.dao

import androidx.room.*
import com.nexblue.app.data.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity)


    @Query("SELECT * FROM messages WHERE chatUserId = :userId ORDER BY timestamp ASC")
    fun getMessagesForChat(userId: String): Flow<List<MessageEntity>>

}