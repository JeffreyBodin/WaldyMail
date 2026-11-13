package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MailFolder
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun sendEmailAndObserveEmails_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = MailboxRepository(firestore, auth)

    val uniqueSubject = "Proposal ${UUID.randomUUID()}"
    val sendResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.sendEmail(
          recipientEmail = ALICE_EMAIL,
          subject = uniqueSubject,
          body = "Review attached schedule.",
          senderName = "Alice",
          senderEmail = ALICE_EMAIL,
        )
      }
    assertTrue(sendResult.isSuccess)

    val emails =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeEmails(uid).first { list ->
          list.any { it.subject == uniqueSubject && it.folder == MailFolder.INBOX.id }
        }
      }
    val inboxCopy = emails.first { it.subject == uniqueSubject && it.folder == MailFolder.INBOX.id }
    assertTrue(inboxCopy.id.isNotBlank())

    assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repo.markRead(inboxCopy.id, true).isSuccess })
    assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repo.toggleStarred(inboxCopy.id, true).isSuccess })
    assertTrue(
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.moveToFolder(inboxCopy.id, MailFolder.ARCHIVE).isSuccess
      }
    )

    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getEmailById(inboxCopy.id).getOrThrow() }
    assertNotNull(updated)
    assertEquals(true, updated?.read)
    assertEquals(true, updated?.starred)
    assertEquals(MailFolder.ARCHIVE.id, updated?.folder)
  }

  @Test
  fun contactCrud_authenticatedOwner_addEditDeleteSucceeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = MailboxRepository(firestore, auth)

    val uniqueEmail = "contact-${UUID.randomUUID()}@studio.io"
    val contactId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addContact("Jordan Blake", uniqueEmail).getOrThrow()
      }
    assertTrue(contactId.isNotBlank())

    val contacts =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeContacts(uid).first { list -> list.any { it.id == contactId } }
      }
    val created = contacts.first { it.id == contactId }
    assertEquals("Jordan Blake", created.name)
    assertEquals(uniqueEmail, created.email)

    assertTrue(
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateContact(created, "Jordan B. Blake", "jordan.b@studio.io").isSuccess
      }
    )
    val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getContactById(contactId).getOrThrow() }
    assertEquals("Jordan B. Blake", updated?.name)
    assertEquals("jordan.b@studio.io", updated?.email)

    assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteContact(contactId).isSuccess })
    val deleted = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getContactById(contactId).getOrThrow() }
    assertNull(deleted)
  }

  @Test
  fun observeEmails_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = MailboxRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.addContact("Private Contact", "secret@alice.io").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = MailboxRepository(firestore, auth)

    var thrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeEmails(aliceUid).first() }
    } catch (e: Exception) {
      thrown = e
    }
    val firestoreException = checkNotNull(thrown?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $thrown"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun observeContacts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = MailboxRepository(firestore, auth)

    var thrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeContacts(aliceUid).first() }
    } catch (e: Exception) {
      thrown = e
    }
    val firestoreException = checkNotNull(thrown?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $thrown"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
