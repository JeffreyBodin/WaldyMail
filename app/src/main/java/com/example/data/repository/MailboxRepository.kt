package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Contact
import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MailboxRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = FirebaseAuth.getInstance(db.app),
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun emailsCollection(uid: String) =
    db.collection("users").document(uid).collection("emails")

  private fun contactsCollection(uid: String) =
    db.collection("users").document(uid).collection("contacts")

  fun observeEmails(userId: String): Flow<List<EmailMessage>> = flow {
    val colRef = emailsCollection(userId)
    emitAll(
      colRef
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              EmailMessage::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.sentAtMillis }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
          }
          throw error
        }
    )
  }

  fun observeContacts(userId: String): Flow<List<Contact>> = flow {
    val colRef = contactsCollection(userId)
    emitAll(
      colRef
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              Contact::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedBy { it.name.lowercase() }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
          }
          throw error
        }
    )
  }

  suspend fun ensureInitialMailboxSeeded(
    userId: String,
    userEmail: String,
    displayName: String,
  ): Result<Unit> {
    val userDocRef = db.collection("users").document(userId)
    val safeEmail = userEmail.ifBlank { "me@postbox.app" }
    val safeName = displayName.ifBlank { safeEmail.substringBefore("@") }

    return try {
      val snapshot = userDocRef.get().await()
      val existingProfile = snapshot.toObject(UserProfile::class.java)
      if (existingProfile != null && existingProfile.mailboxSeeded) {
        return Result.success(Unit)
      }

      val now = System.currentTimeMillis()
      val batch = db.batch()

      if (existingProfile == null) {
        val profilePayload =
          mapOf(
            "userId" to userId,
            "email" to safeEmail,
            "displayName" to safeName,
            "mailboxSeeded" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        batch.set(userDocRef, profilePayload)
      } else {
        batch.update(
          userDocRef,
          mapOf(
            "email" to safeEmail,
            "displayName" to safeName,
            "mailboxSeeded" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val existingEmails = emailsCollection(userId).limit(1).get().await()
      if (existingEmails.isEmpty) {
        val starterEmails =
          listOf(
            mapOf(
              "userId" to userId,
              "senderName" to "Clara Vance",
              "senderEmail" to "clara.vance@northstar.design",
              "recipientEmail" to safeEmail,
              "subject" to "Q4 Editorial Calendar & Print Proofs",
              "body" to
                "Hi $safeName,\n\nI've wrapped up the final typography pass for the November issue. Could you review the cover proof and confirm the press schedule before Thursday afternoon?\n\nBest,\nClara",
              "folder" to MailFolder.INBOX.id,
              "read" to false,
              "starred" to true,
              "sentAtMillis" to (now - 18 * 60_000L),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            mapOf(
              "userId" to userId,
              "senderName" to "Marcus Chen",
              "senderEmail" to "marcus@atelierlabs.io",
              "recipientEmail" to safeEmail,
              "subject" to "Sync notes: Search indexing & latency benchmarks",
              "body" to
                "Hey $safeName,\n\nHere are the benchmark numbers from yesterday's sync:\n• Subject & sender lookup: < 4ms\n• Full body keyword scan: < 12ms\n\nLet me know if you want to pair on the filter chips tomorrow morning.\n\nCheers,\nMarcus",
              "folder" to MailFolder.INBOX.id,
              "read" to false,
              "starred" to false,
              "sentAtMillis" to (now - 2 * 3_600_000L),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            mapOf(
              "userId" to userId,
              "senderName" to "Elena Rostova",
              "senderEmail" to "elena@vervepress.co",
              "recipientEmail" to safeEmail,
              "subject" to "Updated invoice & studio reservation",
              "body" to
                "Hello $safeName,\n\nAttached is the revised reservation confirmation for Studio B on November 20. Tea service and projector setup are both included.\n\nWarmly,\nElena",
              "folder" to MailFolder.INBOX.id,
              "read" to true,
              "starred" to false,
              "sentAtMillis" to (now - 26 * 3_600_000L),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            mapOf(
              "userId" to userId,
              "senderName" to "Postbox Team",
              "senderEmail" to "welcome@postbox.mail",
              "recipientEmail" to safeEmail,
              "subject" to "Welcome to Postbox — calm, searchable correspondence",
              "body" to
                "Welcome aboard, $safeName.\n\nHere are a few ways to get started:\n1. Search your inbox instantly by keyword in the subject, body, or sender.\n2. Manage your address book in the Contacts tab to add, edit, or email contacts in one tap.\n3. Star important threads or archive completed conversations to keep your inbox clear.\n\nHappy writing!",
              "folder" to MailFolder.INBOX.id,
              "read" to true,
              "starred" to true,
              "sentAtMillis" to (now - 48 * 3_600_000L),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        starterEmails.forEach { payload ->
          batch.set(emailsCollection(userId).document(), payload)
        }
      }

      val existingContacts = contactsCollection(userId).limit(1).get().await()
      if (existingContacts.isEmpty) {
        val starterContacts =
          listOf(
            mapOf(
              "userId" to userId,
              "name" to "Clara Vance",
              "email" to "clara.vance@northstar.design",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            mapOf(
              "userId" to userId,
              "name" to "Marcus Chen",
              "email" to "marcus@atelierlabs.io",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            mapOf(
              "userId" to userId,
              "name" to "Elena Rostova",
              "email" to "elena@vervepress.co",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        starterContacts.forEach { payload ->
          batch.set(contactsCollection(userId).document(), payload)
        }
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userDocRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun getEmailById(emailId: String): Result<EmailMessage?> {
    val uid = requireUserId()
    val docRef = emailsCollection(uid).document(emailId)
    return try {
      val doc = docRef.get().await()
      Result.success(
        doc.toObject(EmailMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun sendEmail(
    recipientEmail: String,
    subject: String,
    body: String,
    existingDraftId: String? = null,
    senderName: String? = null,
    senderEmail: String? = null,
  ): Result<String> {
    val uid = requireUserId()
    val user = auth.currentUser
    val resolvedSenderEmail =
      (senderEmail?.takeIf { it.isNotBlank() } ?: user?.email ?: "me@postbox.app").trim()
    val resolvedSenderName =
      (senderName?.takeIf { it.isNotBlank() }
          ?: user?.displayName?.takeIf { it.isNotBlank() }
          ?: resolvedSenderEmail.substringBefore("@"))
        .trim()
    val cleanRecipient = recipientEmail.trim()
    val now = System.currentTimeMillis()

    return try {
      val sentRef =
        if (!existingDraftId.isNullOrBlank()) {
          emailsCollection(uid).document(existingDraftId)
        } else {
          emailsCollection(uid).document()
        }

      if (!existingDraftId.isNullOrBlank()) {
        val existingDoc = sentRef.get().await()
        if (existingDoc.exists()) {
          sentRef
            .update(
              mapOf(
                "senderName" to resolvedSenderName,
                "senderEmail" to resolvedSenderEmail,
                "recipientEmail" to cleanRecipient,
                "subject" to subject.trim(),
                "body" to body.trim(),
                "folder" to MailFolder.SENT.id,
                "read" to true,
                "sentAtMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          sentRef
            .set(
              buildEmailPayload(
                uid = uid,
                senderName = resolvedSenderName,
                senderEmail = resolvedSenderEmail,
                recipientEmail = cleanRecipient,
                subject = subject.trim(),
                body = body.trim(),
                folder = MailFolder.SENT.id,
                read = true,
                starred = false,
                sentAtMillis = now,
              )
            )
            .await()
        }
      } else {
        sentRef
          .set(
            buildEmailPayload(
              uid = uid,
              senderName = resolvedSenderName,
              senderEmail = resolvedSenderEmail,
              recipientEmail = cleanRecipient,
              subject = subject.trim(),
              body = body.trim(),
              folder = MailFolder.SENT.id,
              read = true,
              starred = false,
              sentAtMillis = now,
            )
          )
          .await()
      }

      // If addressed to the user's own email, deliver a copy into Inbox too.
      if (cleanRecipient.equals(resolvedSenderEmail, ignoreCase = true)) {
        emailsCollection(uid)
          .document()
          .set(
            buildEmailPayload(
              uid = uid,
              senderName = resolvedSenderName,
              senderEmail = resolvedSenderEmail,
              recipientEmail = cleanRecipient,
              subject = subject.trim(),
              body = body.trim(),
              folder = MailFolder.INBOX.id,
              read = false,
              starred = false,
              sentAtMillis = now,
            )
          )
          .await()
      }

      Result.success(sentRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, emailsCollection(uid).path, auth)
      Result.failure(e)
    }
  }

  suspend fun saveDraft(
    recipientEmail: String,
    subject: String,
    body: String,
    existingDraftId: String? = null,
    senderName: String? = null,
    senderEmail: String? = null,
  ): Result<String> {
    val uid = requireUserId()
    val user = auth.currentUser
    val resolvedSenderEmail =
      (senderEmail?.takeIf { it.isNotBlank() } ?: user?.email ?: "me@postbox.app").trim()
    val resolvedSenderName =
      (senderName?.takeIf { it.isNotBlank() }
          ?: user?.displayName?.takeIf { it.isNotBlank() }
          ?: resolvedSenderEmail.substringBefore("@"))
        .trim()
    val now = System.currentTimeMillis()
    val docRef =
      if (!existingDraftId.isNullOrBlank()) {
        emailsCollection(uid).document(existingDraftId)
      } else {
        emailsCollection(uid).document()
      }

    return try {
      if (!existingDraftId.isNullOrBlank() && docRef.get().await().exists()) {
        docRef
          .update(
            mapOf(
              "senderName" to resolvedSenderName,
              "senderEmail" to resolvedSenderEmail,
              "recipientEmail" to recipientEmail.trim(),
              "subject" to subject.trim(),
              "body" to body.trim(),
              "folder" to MailFolder.DRAFTS.id,
              "read" to true,
              "sentAtMillis" to now,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            buildEmailPayload(
              uid = uid,
              senderName = resolvedSenderName,
              senderEmail = resolvedSenderEmail,
              recipientEmail = recipientEmail.trim(),
              subject = subject.trim(),
              body = body.trim(),
              folder = MailFolder.DRAFTS.id,
              read = true,
              starred = false,
              sentAtMillis = now,
            )
          )
          .await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun markRead(emailId: String, read: Boolean): Result<Unit> {
    val uid = requireUserId()
    val docRef = emailsCollection(uid).document(emailId)
    return try {
      docRef.update(mapOf("read" to read, "updatedAt" to FieldValue.serverTimestamp())).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun toggleStarred(emailId: String, starred: Boolean): Result<Unit> {
    val uid = requireUserId()
    val docRef = emailsCollection(uid).document(emailId)
    return try {
      docRef.update(mapOf("starred" to starred, "updatedAt" to FieldValue.serverTimestamp())).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun moveToFolder(emailId: String, folder: MailFolder): Result<Unit> {
    val uid = requireUserId()
    val docRef = emailsCollection(uid).document(emailId)
    return try {
      docRef
        .update(mapOf("folder" to folder.id, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteEmailPermanently(emailId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = emailsCollection(uid).document(emailId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun addContact(name: String, email: String): Result<String> {
    val uid = requireUserId()
    val docRef = contactsCollection(uid).document()
    val payload =
      mapOf(
        "userId" to uid,
        "name" to name.trim(),
        "email" to email.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateContact(contact: Contact, name: String, email: String): Result<Unit> {
    val uid = requireUserId()
    require(contact.id.isNotBlank()) { "Contact ID must not be blank for update" }
    val docRef = contactsCollection(uid).document(contact.id)
    return try {
      docRef
        .update(
          mapOf(
            "name" to name.trim(),
            "email" to email.trim(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteContact(contactId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = contactsCollection(uid).document(contactId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun getContactById(contactId: String): Result<Contact?> {
    val uid = requireUserId()
    val docRef = contactsCollection(uid).document(contactId)
    return try {
      val doc = docRef.get().await()
      Result.success(
        doc.toObject(Contact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  private fun buildEmailPayload(
    uid: String,
    senderName: String,
    senderEmail: String,
    recipientEmail: String,
    subject: String,
    body: String,
    folder: String,
    read: Boolean,
    starred: Boolean,
    sentAtMillis: Long,
  ): Map<String, Any> =
    mapOf(
      "userId" to uid,
      "senderName" to senderName,
      "senderEmail" to senderEmail,
      "recipientEmail" to recipientEmail,
      "subject" to subject,
      "body" to body,
      "folder" to folder,
      "read" to read,
      "starred" to starred,
      "sentAtMillis" to sentAtMillis,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
}
