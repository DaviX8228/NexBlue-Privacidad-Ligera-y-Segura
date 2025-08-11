package com.nexblue.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val userId: String,
    val alias: String,
    val lastMessage: String,
    val lastTimestamp: Long
)