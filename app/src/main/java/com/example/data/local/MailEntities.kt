package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Contact
import com.example.data.model.EmailMessage

@Entity(tableName = "emails")
data class EmailEntity(
  @PrimaryKey val id: String,
  val senderName: String,
  val senderEmail: String,
  val recipientEmail: String,
  val subject: String,
  val body: String,
  val folder: String,
  val read: Boolean,
  val starred: Boolean,
  val sentAtMillis: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
) {
  fun toDomain(): EmailMessage =
    EmailMessage(
      id = id,
      senderName = senderName,
      senderEmail = senderEmail,
      recipientEmail = recipientEmail,
      subject = subject,
      body = body,
      folder = folder,
      read = read,
      starred = starred,
      sentAtMillis = sentAtMillis,
    )
}

@Entity(tableName = "contacts")
data class ContactEntity(
  @PrimaryKey val id: String,
  val name: String,
  val email: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
) {
  fun toDomain(): Contact =
    Contact(
      id = id,
      name = name,
      email = email,
    )
}
