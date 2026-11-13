package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.ContactEntity
import com.example.data.local.EmailEntity
import com.example.data.local.MailDao
import com.example.data.local.PostboxDatabase
import com.example.data.model.Contact
import com.example.data.model.EmailMessage
import com.example.data.model.LocalAccountProfile
import com.example.data.model.MailFolder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MailboxRepository(
  private val mailDao: MailDao,
  private val prefs: SharedPreferences? = null,
) {
  constructor(
    context: Context
  ) : this(
    mailDao = PostboxDatabase.getInstance(context.applicationContext).mailDao(),
    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  )

  fun observeEmails(): Flow<List<EmailMessage>> =
    mailDao.observeAllEmails().map { entities -> entities.map { it.toDomain() } }

  fun observeContacts(): Flow<List<Contact>> =
    mailDao.observeAllContacts().map { entities -> entities.map { it.toDomain() } }

  fun getLocalAccountProfile(): LocalAccountProfile {
    val name = prefs?.getString(KEY_DISPLAY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    val email = prefs?.getString(KEY_EMAIL, DEFAULT_EMAIL) ?: DEFAULT_EMAIL
    return LocalAccountProfile(displayName = name, email = email)
  }

  fun updateLocalAccountProfile(displayName: String, email: String): LocalAccountProfile {
    val cleanName = displayName.trim().ifBlank { DEFAULT_NAME }
    val cleanEmail = email.trim().ifBlank { DEFAULT_EMAIL }
    prefs?.edit()?.putString(KEY_DISPLAY_NAME, cleanName)?.putString(KEY_EMAIL, cleanEmail)?.apply()
    return LocalAccountProfile(displayName = cleanName, email = cleanEmail)
  }

  suspend fun ensureInitialMailboxSeeded() {
    if (prefs?.getBoolean(KEY_SEEDED, false) == true) return

    val profile = getLocalAccountProfile()
    val now = System.currentTimeMillis()

    if (mailDao.countEmails() == 0) {
      val starterEmails =
        listOf(
          EmailEntity(
            id = UUID.randomUUID().toString(),
            senderName = "Clara Vance",
            senderEmail = "clara.vance@northstar.design",
            recipientEmail = profile.email,
            subject = "Q4 Editorial Calendar & Print Proofs",
            body =
              "Hi ${profile.displayName},\n\nI've wrapped up the final typography pass for the November issue. Could you review the cover proof and confirm the press schedule before Thursday afternoon?\n\nBest,\nClara",
            folder = MailFolder.INBOX.id,
            read = false,
            starred = true,
            sentAtMillis = now - 18 * 60_000L,
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
          EmailEntity(
            id = UUID.randomUUID().toString(),
            senderName = "Marcus Chen",
            senderEmail = "marcus@atelierlabs.io",
            recipientEmail = profile.email,
            subject = "Sync notes: Search indexing & latency benchmarks",
            body =
              "Hey ${profile.displayName},\n\nHere are the benchmark numbers from yesterday's sync:\n• Subject & sender lookup: < 4ms\n• Full body keyword scan: < 12ms\n\nLet me know if you want to pair on the filter chips tomorrow morning.\n\nCheers,\nMarcus",
            folder = MailFolder.INBOX.id,
            read = false,
            starred = false,
            sentAtMillis = now - 2 * 3_600_000L,
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
          EmailEntity(
            id = UUID.randomUUID().toString(),
            senderName = "Elena Rostova",
            senderEmail = "elena@vervepress.co",
            recipientEmail = profile.email,
            subject = "Updated invoice & studio reservation",
            body =
              "Hello ${profile.displayName},\n\nAttached is the revised reservation confirmation for Studio B on November 20. Tea service and projector setup are both included.\n\nWarmly,\nElena",
            folder = MailFolder.INBOX.id,
            read = true,
            starred = false,
            sentAtMillis = now - 26 * 3_600_000L,
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
          EmailEntity(
            id = UUID.randomUUID().toString(),
            senderName = "Postbox Guide",
            senderEmail = "guide@postbox.local",
            recipientEmail = profile.email,
            subject = "Welcome to Postbox — 100% offline correspondence",
            body =
              "Welcome aboard, ${profile.displayName}.\n\nPostbox runs completely on-device using a local Room database with zero cloud dependencies:\n1. Search your inbox instantly by keyword in the subject, body, or sender.\n2. Manage your address book in the Contacts tab to add, edit, or email contacts in one tap.\n3. Address an email to ${profile.email} to test instant inbox delivery locally.\n\nHappy writing!",
            folder = MailFolder.INBOX.id,
            read = true,
            starred = true,
            sentAtMillis = now - 48 * 3_600_000L,
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
        )
      mailDao.insertEmails(starterEmails)
    }

    if (mailDao.countContacts() == 0) {
      val starterContacts =
        listOf(
          ContactEntity(
            id = UUID.randomUUID().toString(),
            name = "Clara Vance",
            email = "clara.vance@northstar.design",
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
          ContactEntity(
            id = UUID.randomUUID().toString(),
            name = "Marcus Chen",
            email = "marcus@atelierlabs.io",
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
          ContactEntity(
            id = UUID.randomUUID().toString(),
            name = "Elena Rostova",
            email = "elena@vervepress.co",
            createdAtMillis = now,
            updatedAtMillis = now,
          ),
        )
      mailDao.insertContacts(starterContacts)
    }

    prefs?.edit()?.putBoolean(KEY_SEEDED, true)?.apply()
  }

  suspend fun getEmailById(emailId: String): EmailMessage? = mailDao.getEmailById(emailId)?.toDomain()

  suspend fun sendEmail(
    recipientEmail: String,
    subject: String,
    body: String,
    existingDraftId: String? = null,
    senderName: String,
    senderEmail: String,
  ): Result<String> {
    return runCatching {
      val now = System.currentTimeMillis()
      val cleanRecipient = recipientEmail.trim()
      val cleanSenderName = senderName.trim().ifBlank { DEFAULT_NAME }
      val cleanSenderEmail = senderEmail.trim().ifBlank { DEFAULT_EMAIL }
      val existing = existingDraftId?.takeIf { it.isNotBlank() }?.let { mailDao.getEmailById(it) }
      val sentId = existing?.id ?: UUID.randomUUID().toString()

      val sentEntity =
        EmailEntity(
          id = sentId,
          senderName = cleanSenderName,
          senderEmail = cleanSenderEmail,
          recipientEmail = cleanRecipient,
          subject = subject.trim(),
          body = body.trim(),
          folder = MailFolder.SENT.id,
          read = true,
          starred = existing?.starred ?: false,
          sentAtMillis = now,
          createdAtMillis = existing?.createdAtMillis ?: now,
          updatedAtMillis = now,
        )
      mailDao.insertEmail(sentEntity)

      // Deliver an unread copy into the local Inbox when addressed to the local identity.
      if (cleanRecipient.equals(cleanSenderEmail, ignoreCase = true)) {
        val inboxEntity =
          EmailEntity(
            id = UUID.randomUUID().toString(),
            senderName = cleanSenderName,
            senderEmail = cleanSenderEmail,
            recipientEmail = cleanRecipient,
            subject = subject.trim(),
            body = body.trim(),
            folder = MailFolder.INBOX.id,
            read = false,
            starred = false,
            sentAtMillis = now,
            createdAtMillis = now,
            updatedAtMillis = now,
          )
        mailDao.insertEmail(inboxEntity)
      }

      sentId
    }
  }

  suspend fun saveDraft(
    recipientEmail: String,
    subject: String,
    body: String,
    existingDraftId: String? = null,
    senderName: String,
    senderEmail: String,
  ): Result<String> {
    return runCatching {
      val now = System.currentTimeMillis()
      val existing = existingDraftId?.takeIf { it.isNotBlank() }?.let { mailDao.getEmailById(it) }
      val draftId = existing?.id ?: UUID.randomUUID().toString()
      val draftEntity =
        EmailEntity(
          id = draftId,
          senderName = senderName.trim().ifBlank { DEFAULT_NAME },
          senderEmail = senderEmail.trim().ifBlank { DEFAULT_EMAIL },
          recipientEmail = recipientEmail.trim(),
          subject = subject.trim(),
          body = body.trim(),
          folder = MailFolder.DRAFTS.id,
          read = true,
          starred = existing?.starred ?: false,
          sentAtMillis = now,
          createdAtMillis = existing?.createdAtMillis ?: now,
          updatedAtMillis = now,
        )
      mailDao.insertEmail(draftEntity)
      draftId
    }
  }

  suspend fun markRead(emailId: String, read: Boolean): Result<Unit> = runCatching {
    mailDao.updateRead(emailId, read, System.currentTimeMillis())
  }

  suspend fun toggleStarred(emailId: String, starred: Boolean): Result<Unit> = runCatching {
    mailDao.updateStarred(emailId, starred, System.currentTimeMillis())
  }

  suspend fun moveToFolder(emailId: String, folder: MailFolder): Result<Unit> = runCatching {
    mailDao.updateFolder(emailId, folder.id, System.currentTimeMillis())
  }

  suspend fun deleteEmailPermanently(emailId: String): Result<Unit> = runCatching {
    mailDao.deleteEmailById(emailId)
  }

  suspend fun addContact(name: String, email: String): Result<String> = runCatching {
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()
    mailDao.insertContact(
      ContactEntity(
        id = id,
        name = name.trim(),
        email = email.trim(),
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    )
    id
  }

  suspend fun updateContact(contact: Contact, name: String, email: String): Result<Unit> =
    runCatching {
      mailDao.updateContact(
        id = contact.id,
        name = name.trim(),
        email = email.trim(),
        updatedAtMillis = System.currentTimeMillis(),
      )
    }

  suspend fun deleteContact(contactId: String): Result<Unit> = runCatching {
    mailDao.deleteContactById(contactId)
  }

  suspend fun getContactById(contactId: String): Contact? =
    mailDao.getContactById(contactId)?.toDomain()

  private companion object {
    const val PREFS_NAME = "postbox_offline_prefs"
    const val KEY_SEEDED = "mailbox_seeded"
    const val KEY_DISPLAY_NAME = "local_display_name"
    const val KEY_EMAIL = "local_email"
    const val DEFAULT_NAME = "Postbox User"
    const val DEFAULT_EMAIL = "me@postbox.local"
  }
}
