package com.example.data.model

enum class MailFolder(val id: String, val label: String) {
  INBOX("inbox", "Inbox"),
  STARRED("starred", "Starred"),
  SENT("sent", "Sent"),
  DRAFTS("drafts", "Drafts"),
  ARCHIVE("archive", "Archive"),
  TRASH("trash", "Trash"),
}

data class EmailMessage(
  val id: String = "",
  val senderName: String = "",
  val senderEmail: String = "",
  val recipientEmail: String = "",
  val subject: String = "",
  val body: String = "",
  val folder: String = MailFolder.INBOX.id,
  val read: Boolean = false,
  val starred: Boolean = false,
  val sentAtMillis: Long = 0L,
)

data class Contact(
  val id: String = "",
  val name: String = "",
  val email: String = "",
)

data class LocalAccountProfile(
  val displayName: String = "Postbox User",
  val email: String = "me@postbox.local",
)
