package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MailFolder(val id: String, val label: String) {
  INBOX("inbox", "Inbox"),
  STARRED("starred", "Starred"),
  SENT("sent", "Sent"),
  DRAFTS("drafts", "Drafts"),
  ARCHIVE("archive", "Archive"),
  TRASH("trash", "Trash"),
}

@IgnoreExtraProperties
data class EmailMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val senderName: String = "",
  val senderEmail: String = "",
  val recipientEmail: String = "",
  val subject: String = "",
  val body: String = "",
  val folder: String = MailFolder.INBOX.id,
  val read: Boolean = false,
  val starred: Boolean = false,
  val sentAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Contact(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val email: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val mailboxSeeded: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
