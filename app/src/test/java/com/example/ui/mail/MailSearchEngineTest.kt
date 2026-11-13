package com.example.ui.mail

import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class MailSearchEngineTest {

  private val sampleEmails =
    listOf(
      EmailMessage(
        id = "1",
        senderName = "Clara Vance",
        senderEmail = "clara@northstar.design",
        subject = "Q4 Editorial Calendar",
        body = "Please review the press proofs before Thursday.",
        folder = MailFolder.INBOX.id,
        read = false,
        starred = true,
      ),
      EmailMessage(
        id = "2",
        senderName = "Marcus Chen",
        senderEmail = "marcus@atelierlabs.io",
        subject = "Sync notes",
        body = "Search indexing benchmark latency was under 4ms.",
        folder = MailFolder.INBOX.id,
        read = true,
        starred = false,
      ),
      EmailMessage(
        id = "3",
        senderName = "Elena Rostova",
        senderEmail = "elena@vervepress.co",
        subject = "Archived invoice",
        body = "Studio reservation confirmed for November.",
        folder = MailFolder.ARCHIVE.id,
        read = true,
        starred = false,
      ),
    )

  @Test
  fun `search inbox matches keyword in subject`() {
    val results =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "Editorial",
        fieldFilter = SearchFieldFilter.SUBJECT,
        searchScope = SearchScope.INBOX,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(listOf("1"), results.map { it.email.id })
    assertEquals(listOf("Subject"), results.first().matchedIn)
  }

  @Test
  fun `search inbox matches keyword in body`() {
    val results =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "benchmark",
        fieldFilter = SearchFieldFilter.BODY,
        searchScope = SearchScope.INBOX,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(listOf("2"), results.map { it.email.id })
    assertEquals(listOf("Body"), results.first().matchedIn)
  }

  @Test
  fun `search inbox matches sender name or email`() {
    val byName =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "Clara",
        fieldFilter = SearchFieldFilter.SENDER,
        searchScope = SearchScope.INBOX,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(listOf("1"), byName.map { it.email.id })

    val byDomain =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "atelierlabs.io",
        fieldFilter = SearchFieldFilter.SENDER,
        searchScope = SearchScope.INBOX,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(listOf("2"), byDomain.map { it.email.id })
  }

  @Test
  fun `search scope distinguishes inbox from all mail`() {
    val inboxOnly =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "Elena",
        fieldFilter = SearchFieldFilter.ALL,
        searchScope = SearchScope.INBOX,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(emptyList<String>(), inboxOnly.map { it.email.id })

    val allMail =
      MailSearchEngine.filterEmails(
        emails = sampleEmails,
        selectedFolder = MailFolder.INBOX,
        query = "Elena",
        fieldFilter = SearchFieldFilter.ALL,
        searchScope = SearchScope.ALL_MAIL,
        unreadOnly = false,
        starredOnly = false,
      )
    assertEquals(listOf("3"), allMail.map { it.email.id })
  }
}
