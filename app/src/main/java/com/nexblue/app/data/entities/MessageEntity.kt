package com.nexblue.app.data.entities


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["userId"],
        childColumns = ["chatUserId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatUserId: String,  // Relación con ChatEntity
    val sender: String,      // "me" o "them"
    val text: String,
    val timestamp: Long
)