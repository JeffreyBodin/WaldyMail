package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PostboxDatabase
import com.example.data.model.MailFolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalMailboxRepositoryTest {

  private lateinit var db: PostboxDatabase
  private lateinit var repository: MailboxRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, PostboxDatabase::class.java).allowMainThreadQueries().build()
    val prefs = context.getSharedPreferences("test_postbox_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    repository = MailboxRepository(db.mailDao(), prefs)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun seedMailbox_populatesStarterEmailsAndContactsOnce() = runBlocking {
    repository.ensureInitialMailboxSeeded()
    val emails = repository.observeEmails().first()
    val contacts = repository.observeContacts().first()
    assertEquals(4, emails.size)
    assertEquals(3, contacts.size)
  }

  @Test
  fun sendEmailToSelf_createsSentAndUnreadInboxCopies() = runBlocking {
    val sendResult =
      repository.sendEmail(
        recipientEmail = "me@postbox.local",
        subject = "Offline test",
        body = "Stored locally in Room.",
        senderName = "Postbox User",
        senderEmail = "me@postbox.local",
      )
    assertTrue(sendResult.isSuccess)

    val all = repository.observeEmails().first()
    val sent = all.firstOrNull { it.folder == MailFolder.SENT.id && it.subject == "Offline test" }
    val inbox = all.firstOrNull { it.folder == MailFolder.INBOX.id && it.subject == "Offline test" }
    assertNotNull(sent)
    assertNotNull(inbox)
    assertEquals(false, inbox?.read)

    repository.markRead(inbox!!.id, true)
    repository.toggleStarred(inbox.id, true)
    repository.moveToFolder(inbox.id, MailFolder.ARCHIVE)

    val updated = repository.getEmailById(inbox.id)
    assertEquals(true, updated?.read)
    assertEquals(true, updated?.starred)
    assertEquals(MailFolder.ARCHIVE.id, updated?.folder)
  }

  @Test
  fun contactCrud_addEditDeleteWorksOffline() = runBlocking {
    val id = repository.addContact("Jordan Blake", "jordan@local.dev").getOrThrow()
    val contacts = repository.observeContacts().first()
    val created = contacts.first { it.id == id }
    assertEquals("Jordan Blake", created.name)
    assertEquals("jordan@local.dev", created.email)

    repository.updateContact(created, "Jordan B. Blake", "j.blake@local.dev")
    val updated = repository.getContactById(id)
    assertEquals("Jordan B. Blake", updated?.name)
    assertEquals("j.blake@local.dev", updated?.email)

    repository.deleteContact(id)
    assertNull(repository.getContactById(id))
  }
}
