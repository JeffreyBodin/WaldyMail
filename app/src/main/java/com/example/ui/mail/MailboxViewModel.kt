package com.example.ui.mail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Contact
import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import com.example.data.repository.MailboxRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class MainTab(val label: String) {
  MAIL("Mail"),
  CONTACTS("Contacts"),
}

data class ComposeDraftState(
  val isOpen: Boolean = false,
  val draftId: String? = null,
  val toEmail: String = "",
  val subject: String = "",
  val body: String = "",
  val isSending: Boolean = false,
  val validationError: String? = null,
)

data class ContactEditorState(
  val isOpen: Boolean = false,
  val editingContact: Contact? = null,
  val name: String = "",
  val email: String = "",
  val isSaving: Boolean = false,
  val validationError: String? = null,
)

data class MailSearchFilters(
  val query: String = "",
  val fieldFilter: SearchFieldFilter = SearchFieldFilter.ALL,
  val scope: SearchScope = SearchScope.INBOX,
  val unreadOnly: Boolean = false,
  val starredOnly: Boolean = false,
)

class MailboxViewModel(
  private val repository: MailboxRepository,
  private val currentUserId: String,
  val currentUserEmail: String,
  val currentUserDisplayName: String,
) : ViewModel() {

  private val _selectedTab = MutableStateFlow(MainTab.MAIL)
  val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

  private val _selectedFolder = MutableStateFlow(MailFolder.INBOX)
  val selectedFolder: StateFlow<MailFolder> = _selectedFolder.asStateFlow()

  private val _searchFilters = MutableStateFlow(MailSearchFilters())
  val searchFilters: StateFlow<MailSearchFilters> = _searchFilters.asStateFlow()

  private val _contactsQuery = MutableStateFlow("")
  val contactsQuery: StateFlow<String> = _contactsQuery.asStateFlow()

  private val _selectedEmailId = MutableStateFlow<String?>(null)
  val selectedEmailId: StateFlow<String?> = _selectedEmailId.asStateFlow()

  private val _composeState = MutableStateFlow(ComposeDraftState())
  val composeState: StateFlow<ComposeDraftState> = _composeState.asStateFlow()

  private val _contactEditorState = MutableStateFlow(ContactEditorState())
  val contactEditorState: StateFlow<ContactEditorState> = _contactEditorState.asStateFlow()

  private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

  val allEmailsState: StateFlow<UiState<List<EmailMessage>>> =
    repository
      .observeEmails(currentUserId)
      .map<List<EmailMessage>, UiState<List<EmailMessage>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing emails", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load mailbox"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val visibleEmailsState: StateFlow<UiState<List<EmailDisplayItem>>> =
    combine(allEmailsState, _selectedFolder, _searchFilters) { emailsState, folder, filters ->
        when (emailsState) {
          is UiState.Loading -> UiState.Loading
          is UiState.Error -> emailsState
          is UiState.Success ->
            UiState.Success(
              MailSearchEngine.filterEmails(
                emails = emailsState.data,
                selectedFolder = folder,
                query = filters.query,
                fieldFilter = filters.fieldFilter,
                searchScope = filters.scope,
                unreadOnly = filters.unreadOnly,
                starredOnly = filters.starredOnly,
              )
            )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val unreadInboxCount: StateFlow<Int> =
    allEmailsState
      .map { state ->
        (state as? UiState.Success)?.data?.count {
          it.folder == MailFolder.INBOX.id && !it.read
        } ?: 0
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = 0,
      )

  val selectedEmail: StateFlow<EmailMessage?> =
    combine(allEmailsState, _selectedEmailId) { state, selectedId ->
        val emails = (state as? UiState.Success)?.data ?: return@combine null
        emails.firstOrNull { it.id == selectedId }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val allContactsState: StateFlow<UiState<List<Contact>>> =
    repository
      .observeContacts(currentUserId)
      .map<List<Contact>, UiState<List<Contact>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing contacts", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load contacts"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val filteredContactsState: StateFlow<UiState<List<Contact>>> =
    combine(allContactsState, _contactsQuery) { contactsState, query ->
        when (contactsState) {
          is UiState.Loading -> UiState.Loading
          is UiState.Error -> contactsState
          is UiState.Success -> {
            val q = query.trim()
            if (q.isEmpty()) {
              contactsState
            } else {
              UiState.Success(
                contactsState.data.filter {
                  it.name.contains(q, ignoreCase = true) ||
                    it.email.contains(q, ignoreCase = true)
                }
              )
            }
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      repository.ensureInitialMailboxSeeded(
        userId = currentUserId,
        userEmail = currentUserEmail,
        displayName = currentUserDisplayName,
      )
    }
  }

  fun selectTab(tab: MainTab) {
    _selectedTab.value = tab
  }

  fun selectFolder(folder: MailFolder) {
    _selectedFolder.value = folder
    _selectedEmailId.value = null
  }

  fun updateSearchQuery(query: String) {
    _searchFilters.update { it.copy(query = query) }
  }

  fun updateSearchFieldFilter(filter: SearchFieldFilter) {
    _searchFilters.update { it.copy(fieldFilter = filter) }
  }

  fun updateSearchScope(scope: SearchScope) {
    _searchFilters.update { it.copy(scope = scope) }
  }

  fun toggleUnreadOnly() {
    _searchFilters.update { it.copy(unreadOnly = !it.unreadOnly) }
  }

  fun toggleStarredOnly() {
    _searchFilters.update { it.copy(starredOnly = !it.starredOnly) }
  }

  fun clearSearch() {
    _searchFilters.value = MailSearchFilters()
  }

  fun updateContactsQuery(query: String) {
    _contactsQuery.value = query
  }

  fun openEmail(email: EmailMessage) {
    if (email.folder == MailFolder.DRAFTS.id) {
      openDraft(email)
      return
    }
    _selectedEmailId.value = email.id
    if (!email.read) {
      viewModelScope.launch { repository.markRead(email.id, true) }
    }
  }

  fun closeEmailDetail() {
    _selectedEmailId.value = null
  }

  fun toggleRead(email: EmailMessage) {
    viewModelScope.launch {
      repository.markRead(email.id, !email.read).onFailure {
        _snackbarMessages.tryEmit("Couldn't update read status")
      }
    }
  }

  fun toggleStarred(email: EmailMessage) {
    viewModelScope.launch {
      repository.toggleStarred(email.id, !email.starred).onFailure {
        _snackbarMessages.tryEmit("Couldn't update star")
      }
    }
  }

  fun archiveEmail(email: EmailMessage) {
    viewModelScope.launch {
      val target =
        if (email.folder == MailFolder.ARCHIVE.id) MailFolder.INBOX else MailFolder.ARCHIVE
      repository
        .moveToFolder(email.id, target)
        .onSuccess {
          if (_selectedEmailId.value == email.id) _selectedEmailId.value = null
          _snackbarMessages.tryEmit(
            if (target == MailFolder.ARCHIVE) "Moved to Archive" else "Moved back to Inbox"
          )
        }
        .onFailure { _snackbarMessages.tryEmit("Couldn't move message") }
    }
  }

  fun moveToTrash(email: EmailMessage) {
    viewModelScope.launch {
      if (email.folder == MailFolder.TRASH.id) {
        repository
          .deleteEmailPermanently(email.id)
          .onSuccess {
            if (_selectedEmailId.value == email.id) _selectedEmailId.value = null
            _snackbarMessages.tryEmit("Deleted permanently")
          }
          .onFailure { _snackbarMessages.tryEmit("Couldn't delete email") }
      } else {
        repository
          .moveToFolder(email.id, MailFolder.TRASH)
          .onSuccess {
            if (_selectedEmailId.value == email.id) _selectedEmailId.value = null
            _snackbarMessages.tryEmit("Moved to Trash")
          }
          .onFailure { _snackbarMessages.tryEmit("Couldn't move to Trash") }
      }
    }
  }

  fun restoreFromTrash(email: EmailMessage) {
    viewModelScope.launch {
      repository
        .moveToFolder(email.id, MailFolder.INBOX)
        .onSuccess {
          if (_selectedEmailId.value == email.id) _selectedEmailId.value = null
          _snackbarMessages.tryEmit("Restored to Inbox")
        }
        .onFailure { _snackbarMessages.tryEmit("Couldn't restore email") }
    }
  }

  // --- Compose & Drafts ---

  fun openCompose(recipientEmail: String = "", subject: String = "", body: String = "") {
    _composeState.value =
      ComposeDraftState(
        isOpen = true,
        toEmail = recipientEmail,
        subject = subject,
        body = body,
      )
  }

  fun openReply(email: EmailMessage) {
    val replyTo =
      if (email.folder == MailFolder.SENT.id) email.recipientEmail else email.senderEmail
    val replySubject =
      if (email.subject.startsWith("Re:", ignoreCase = true)) email.subject
      else "Re: ${email.subject}"
    val quoted = "\n\n---\nOn previous message, ${email.senderName} wrote:\n${email.body}"
    openCompose(recipientEmail = replyTo, subject = replySubject, body = quoted)
  }

  fun openForward(email: EmailMessage) {
    val fwdSubject =
      if (email.subject.startsWith("Fwd:", ignoreCase = true)) email.subject
      else "Fwd: ${email.subject}"
    val fwdBody =
      "\n\n--- Forwarded message ---\nFrom: ${email.senderName} <${email.senderEmail}>\nSubject: ${email.subject}\n\n${email.body}"
    openCompose(recipientEmail = "", subject = fwdSubject, body = fwdBody)
  }

  private fun openDraft(draft: EmailMessage) {
    _composeState.value =
      ComposeDraftState(
        isOpen = true,
        draftId = draft.id,
        toEmail = draft.recipientEmail,
        subject = draft.subject,
        body = draft.body,
      )
  }

  fun updateComposeTo(to: String) {
    _composeState.update { it.copy(toEmail = to, validationError = null) }
  }

  fun updateComposeSubject(subject: String) {
    _composeState.update { it.copy(subject = subject, validationError = null) }
  }

  fun updateComposeBody(body: String) {
    _composeState.update { it.copy(body = body, validationError = null) }
  }

  fun closeCompose() {
    _composeState.value = ComposeDraftState()
  }

  fun saveComposeAsDraft() {
    val draft = _composeState.value
    if (draft.toEmail.isBlank() && draft.subject.isBlank() && draft.body.isBlank()) {
      closeCompose()
      return
    }
    _composeState.update { it.copy(isSending = true, validationError = null) }
    viewModelScope.launch {
      repository
        .saveDraft(
          recipientEmail = draft.toEmail,
          subject = draft.subject,
          body = draft.body,
          existingDraftId = draft.draftId,
          senderName = currentUserDisplayName,
          senderEmail = currentUserEmail,
        )
        .onSuccess {
          _composeState.value = ComposeDraftState()
          _snackbarMessages.tryEmit("Draft saved")
        }
        .onFailure { err ->
          _composeState.update {
            it.copy(isSending = false, validationError = err.message ?: "Failed to save draft")
          }
        }
    }
  }

  fun sendComposeEmail() {
    val draft = _composeState.value
    val recipient = draft.toEmail.trim()
    if (!EMAIL_REGEX.matches(recipient)) {
      _composeState.update { it.copy(validationError = "Enter a valid recipient email address.") }
      return
    }
    if (draft.subject.isBlank() && draft.body.isBlank()) {
      _composeState.update { it.copy(validationError = "Add a subject or message body before sending.") }
      return
    }

    _composeState.update { it.copy(isSending = true, validationError = null) }
    viewModelScope.launch {
      repository
        .sendEmail(
          recipientEmail = recipient,
          subject = draft.subject.ifBlank { "(No subject)" },
          body = draft.body,
          existingDraftId = draft.draftId,
          senderName = currentUserDisplayName,
          senderEmail = currentUserEmail,
        )
        .onSuccess {
          _composeState.value = ComposeDraftState()
          val selfNote =
            if (recipient.equals(currentUserEmail, ignoreCase = true)) {
              "Email sent & delivered to Inbox"
            } else {
              "Email sent to $recipient"
            }
          _snackbarMessages.tryEmit(selfNote)
        }
        .onFailure { err ->
          _composeState.update {
            it.copy(isSending = false, validationError = err.message ?: "Failed to send email")
          }
        }
    }
  }

  // --- Contacts CRUD ---

  fun openAddContact(prefillName: String = "", prefillEmail: String = "") {
    _contactEditorState.value =
      ContactEditorState(
        isOpen = true,
        editingContact = null,
        name = prefillName,
        email = prefillEmail,
      )
  }

  fun openEditContact(contact: Contact) {
    _contactEditorState.value =
      ContactEditorState(
        isOpen = true,
        editingContact = contact,
        name = contact.name,
        email = contact.email,
      )
  }

  fun updateContactName(name: String) {
    _contactEditorState.update { it.copy(name = name, validationError = null) }
  }

  fun updateContactEmail(email: String) {
    _contactEditorState.update { it.copy(email = email, validationError = null) }
  }

  fun closeContactEditor() {
    _contactEditorState.value = ContactEditorState()
  }

  fun saveContact() {
    val editor = _contactEditorState.value
    val name = editor.name.trim()
    val email = editor.email.trim()

    if (name.isEmpty()) {
      _contactEditorState.update { it.copy(validationError = "Contact name is required.") }
      return
    }
    if (!EMAIL_REGEX.matches(email)) {
      _contactEditorState.update { it.copy(validationError = "Enter a valid email address.") }
      return
    }

    _contactEditorState.update { it.copy(isSaving = true, validationError = null) }
    viewModelScope.launch {
      val result =
        if (editor.editingContact == null) {
          repository.addContact(name = name, email = email).map { Unit }
        } else {
          repository.updateContact(editor.editingContact, name = name, email = email)
        }

      result
        .onSuccess {
          val wasEdit = editor.editingContact != null
          _contactEditorState.value = ContactEditorState()
          _snackbarMessages.tryEmit(if (wasEdit) "Contact updated" else "Contact added")
        }
        .onFailure { err ->
          _contactEditorState.update {
            it.copy(isSaving = false, validationError = err.message ?: "Couldn't save contact")
          }
        }
    }
  }

  fun deleteContact(contact: Contact) {
    viewModelScope.launch {
      repository
        .deleteContact(contact.id)
        .onSuccess { _snackbarMessages.tryEmit("Deleted ${contact.name}") }
        .onFailure { _snackbarMessages.tryEmit("Couldn't delete contact") }
    }
  }

  private companion object {
    const val TAG = "MailboxVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }
}
