package com.example.ui.mail

import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder

enum class SearchFieldFilter(val label: String) {
  ALL("All fields"),
  SUBJECT("Subject"),
  BODY("Body"),
  SENDER("Sender"),
}

enum class SearchScope(val label: String) {
  INBOX("Inbox"),
  ALL_MAIL("All Mail"),
}

data class EmailDisplayItem(
  val email: EmailMessage,
  val matchedIn: List<String> = emptyList(),
)

object MailSearchEngine {
  fun filterEmails(
    emails: List<EmailMessage>,
    selectedFolder: MailFolder,
    query: String,
    fieldFilter: SearchFieldFilter,
    searchScope: SearchScope,
    unreadOnly: Boolean,
    starredOnly: Boolean,
  ): List<EmailDisplayItem> {
    val trimmedQuery = query.trim()
    val isSearching = trimmedQuery.isNotEmpty()

    val baseList =
      if (isSearching) {
        when (searchScope) {
          SearchScope.INBOX -> emails.filter { it.folder == MailFolder.INBOX.id }
          SearchScope.ALL_MAIL -> emails.filter { it.folder != MailFolder.TRASH.id }
        }
      } else {
        when (selectedFolder) {
          MailFolder.STARRED -> emails.filter { it.starred && it.folder != MailFolder.TRASH.id }
          else -> emails.filter { it.folder == selectedFolder.id }
        }
      }

    val quickFiltered =
      baseList.filter { email ->
        val matchesUnread = !unreadOnly || !email.read
        val matchesStarred = !starredOnly || email.starred
        matchesUnread && matchesStarred
      }

    if (!isSearching) {
      return quickFiltered.map { EmailDisplayItem(email = it) }
    }

    return quickFiltered.mapNotNull { email ->
      val subjectHit = email.subject.contains(trimmedQuery, ignoreCase = true)
      val bodyHit = email.body.contains(trimmedQuery, ignoreCase = true)
      val senderHit =
        email.senderName.contains(trimmedQuery, ignoreCase = true) ||
          email.senderEmail.contains(trimmedQuery, ignoreCase = true)

      val matchedLabels = buildList {
        if (subjectHit && (fieldFilter == SearchFieldFilter.ALL || fieldFilter == SearchFieldFilter.SUBJECT)) {
          add("Subject")
        }
        if (bodyHit && (fieldFilter == SearchFieldFilter.ALL || fieldFilter == SearchFieldFilter.BODY)) {
          add("Body")
        }
        if (senderHit && (fieldFilter == SearchFieldFilter.ALL || fieldFilter == SearchFieldFilter.SENDER)) {
          add("Sender")
        }
      }

      val isMatch =
        when (fieldFilter) {
          SearchFieldFilter.ALL -> subjectHit || bodyHit || senderHit
          SearchFieldFilter.SUBJECT -> subjectHit
          SearchFieldFilter.BODY -> bodyHit
          SearchFieldFilter.SENDER -> senderHit
        }

      if (isMatch) EmailDisplayItem(email = email, matchedIn = matchedLabels) else null
    }
  }
}
