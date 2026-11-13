package com.example.ui.mail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.Contact
import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import com.example.data.repository.MailboxRepository
import com.example.ui.contacts.ContactEditorDialog
import com.example.ui.contacts.ContactsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostboxAppScreen(modifier: Modifier = Modifier) {
  val viewModel: MailboxViewModel =
    viewModel(
      factory = viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY])
          MailboxViewModel(repository = MailboxRepository(app))
        }
      }
    )

  val localProfile by viewModel.localProfile.collectAsStateWithLifecycle()
  val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
  val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
  val searchFilters by viewModel.searchFilters.collectAsStateWithLifecycle()
  val visibleEmailsState by viewModel.visibleEmailsState.collectAsStateWithLifecycle()
  val unreadInboxCount by viewModel.unreadInboxCount.collectAsStateWithLifecycle()
  val selectedEmail by viewModel.selectedEmail.collectAsStateWithLifecycle()
  val allContactsState by viewModel.allContactsState.collectAsStateWithLifecycle()
  val filteredContactsState by viewModel.filteredContactsState.collectAsStateWithLifecycle()
  val contactsQuery by viewModel.contactsQuery.collectAsStateWithLifecycle()
  val composeState by viewModel.composeState.collectAsStateWithLifecycle()
  val contactEditorState by viewModel.contactEditorState.collectAsStateWithLifecycle()
  val profileEditorState by viewModel.profileEditorState.collectAsStateWithLifecycle()

  val allContacts: List<Contact> = (allContactsState as? UiState.Success)?.data ?: emptyList()

  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.snackbarMessages.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      FolderDrawerContent(
        selectedFolder = selectedFolder,
        unreadInboxCount = unreadInboxCount,
        userDisplayName = localProfile.displayName,
        userEmail = localProfile.email,
        onSelectFolder = { folder ->
          viewModel.selectTab(MainTab.MAIL)
          viewModel.selectFolder(folder)
          scope.launch { drawerState.close() }
        },
        onEditLocalIdentity = {
          scope.launch { drawerState.close() }
          viewModel.openProfileEditor()
        },
      )
    },
    modifier = modifier,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isWideLayout = maxWidth >= 700.dp

      if (!isWideLayout && selectedTab == MainTab.MAIL && selectedEmail != null) {
        BackHandler { viewModel.closeEmailDetail() }
      } else if (selectedTab == MainTab.CONTACTS) {
        BackHandler { viewModel.selectTab(MainTab.MAIL) }
      }

      Row(modifier = Modifier.fillMaxSize()) {
        if (isWideLayout) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            Spacer(modifier = Modifier.height(12.dp))
            NavigationRailItem(
              selected = selectedTab == MainTab.MAIL,
              onClick = { viewModel.selectTab(MainTab.MAIL) },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadInboxCount > 0) {
                      Badge { Text(unreadInboxCount.toString()) }
                    }
                  }
                ) {
                  Icon(
                    imageVector =
                      if (selectedTab == MainTab.MAIL) Icons.Rounded.Mail
                      else Icons.Outlined.Mail,
                    contentDescription = stringResource(R.string.nav_mail),
                  )
                }
              },
              label = { Text(stringResource(R.string.nav_mail)) },
              modifier = Modifier.testTag("rail_mail"),
            )
            NavigationRailItem(
              selected = selectedTab == MainTab.CONTACTS,
              onClick = { viewModel.selectTab(MainTab.CONTACTS) },
              icon = {
                Icon(
                  imageVector =
                    if (selectedTab == MainTab.CONTACTS) Icons.Rounded.Contacts
                    else Icons.Outlined.Contacts,
                  contentDescription = stringResource(R.string.nav_contacts),
                )
              },
              label = { Text(stringResource(R.string.nav_contacts)) },
              modifier = Modifier.testTag("rail_contacts"),
            )
          }
          VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            if (isWideLayout || selectedTab == MainTab.CONTACTS || selectedEmail == null) {
              TopAppBar(
                colors =
                  TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                  ),
                navigationIcon = {
                  IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier =
                      Modifier.minimumInteractiveComponentSize().testTag("open_drawer_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Menu,
                      contentDescription = "Open folders menu",
                    )
                  }
                },
                title = {
                  val titleText =
                    when {
                      selectedTab == MainTab.CONTACTS -> stringResource(R.string.nav_contacts)
                      searchFilters.query.isNotBlank() -> "Search · ${searchFilters.scope.label}"
                      else -> selectedFolder.label
                    }
                  Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                actions = {
                  Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 8.dp),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                      )
                    }
                  }
                  MonogramAvatar(
                    name = localProfile.displayName,
                    size = 34.dp,
                    modifier = Modifier.padding(end = 14.dp),
                  )
                },
              )
            }
          },
          bottomBar = {
            if (!isWideLayout && (selectedTab == MainTab.CONTACTS || selectedEmail == null)) {
              NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars,
              ) {
                NavigationBarItem(
                  selected = selectedTab == MainTab.MAIL,
                  onClick = { viewModel.selectTab(MainTab.MAIL) },
                  icon = {
                    BadgedBox(
                      badge = {
                        if (unreadInboxCount > 0) {
                          Badge { Text(unreadInboxCount.toString()) }
                        }
                      }
                    ) {
                      Icon(
                        imageVector =
                          if (selectedTab == MainTab.MAIL) Icons.Rounded.Mail
                          else Icons.Outlined.Mail,
                        contentDescription = stringResource(R.string.nav_mail),
                      )
                    }
                  },
                  label = { Text(stringResource(R.string.nav_mail)) },
                  modifier = Modifier.testTag("bottom_nav_mail"),
                )
                NavigationBarItem(
                  selected = selectedTab == MainTab.CONTACTS,
                  onClick = { viewModel.selectTab(MainTab.CONTACTS) },
                  icon = {
                    Icon(
                      imageVector =
                        if (selectedTab == MainTab.CONTACTS) Icons.Rounded.Contacts
                        else Icons.Outlined.Contacts,
                      contentDescription = stringResource(R.string.nav_contacts),
                    )
                  },
                  label = { Text(stringResource(R.string.nav_contacts)) },
                  modifier = Modifier.testTag("bottom_nav_contacts"),
                )
              }
            }
          },
          floatingActionButton = {
            if (selectedTab == MainTab.MAIL && (isWideLayout || selectedEmail == null)) {
              ExtendedFloatingActionButton(
                onClick = { viewModel.openCompose() },
                icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.compose)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("compose_fab"),
              )
            } else if (selectedTab == MainTab.CONTACTS) {
              ExtendedFloatingActionButton(
                onClick = { viewModel.openAddContact() },
                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.add_contact)) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_contact_fab"),
              )
            }
          },
        ) { innerPadding ->
          Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
              MainTab.CONTACTS -> {
                ContactsScreen(
                  contactsState = filteredContactsState,
                  query = contactsQuery,
                  onQueryChange = viewModel::updateContactsQuery,
                  onAddContactClick = { viewModel.openAddContact() },
                  onEditContactClick = viewModel::openEditContact,
                  onDeleteContact = viewModel::deleteContact,
                  onComposeToContact = { contact ->
                    viewModel.selectTab(MainTab.MAIL)
                    viewModel.openCompose(recipientEmail = contact.email)
                  },
                )
              }
              MainTab.MAIL -> {
                if (isWideLayout) {
                  Row(modifier = Modifier.fillMaxSize()) {
                    MailListPane(
                      selectedFolder = selectedFolder,
                      searchFilters = searchFilters,
                      visibleEmailsState = visibleEmailsState,
                      selectedEmailId = selectedEmail?.id,
                      onQueryChange = viewModel::updateSearchQuery,
                      onFieldFilterChange = viewModel::updateSearchFieldFilter,
                      onScopeChange = viewModel::updateSearchScope,
                      onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                      onToggleStarredOnly = viewModel::toggleStarredOnly,
                      onClearSearch = viewModel::clearSearch,
                      onEmailClick = viewModel::openEmail,
                      onToggleStar = viewModel::toggleStarred,
                      modifier = Modifier.weight(0.46f).fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(modifier = Modifier.weight(0.54f).fillMaxHeight()) {
                      val detail = selectedEmail
                      if (detail == null) {
                        EmptyMailboxState(
                          icon = Icons.Rounded.Inbox,
                          title = "Select an email to read",
                          subtitle = "Pick any message from the list or compose a new one.",
                        )
                      } else {
                        val inContacts =
                          allContacts.any {
                            it.email.equals(detail.senderEmail, ignoreCase = true)
                          }
                        EmailDetailPane(
                          email = detail,
                          isSenderInContacts = inContacts,
                          showBackButton = false,
                          onBack = viewModel::closeEmailDetail,
                          onReply = { viewModel.openReply(detail) },
                          onForward = { viewModel.openForward(detail) },
                          onToggleStar = { viewModel.toggleStarred(detail) },
                          onToggleUnread = {
                            viewModel.toggleRead(detail)
                            viewModel.closeEmailDetail()
                          },
                          onArchive = { viewModel.archiveEmail(detail) },
                          onTrashOrDelete = { viewModel.moveToTrash(detail) },
                          onRestoreFromTrash = { viewModel.restoreFromTrash(detail) },
                          onSaveSenderToContacts = {
                            viewModel.openAddContact(
                              prefillName = detail.senderName,
                              prefillEmail = detail.senderEmail,
                            )
                          },
                        )
                      }
                    }
                  }
                } else {
                  val detail = selectedEmail
                  if (detail != null) {
                    val inContacts =
                      allContacts.any {
                        it.email.equals(detail.senderEmail, ignoreCase = true)
                      }
                    EmailDetailPane(
                      email = detail,
                      isSenderInContacts = inContacts,
                      showBackButton = true,
                      onBack = viewModel::closeEmailDetail,
                      onReply = { viewModel.openReply(detail) },
                      onForward = { viewModel.openForward(detail) },
                      onToggleStar = { viewModel.toggleStarred(detail) },
                      onToggleUnread = {
                        viewModel.toggleRead(detail)
                        viewModel.closeEmailDetail()
                      },
                      onArchive = { viewModel.archiveEmail(detail) },
                      onTrashOrDelete = { viewModel.moveToTrash(detail) },
                      onRestoreFromTrash = { viewModel.restoreFromTrash(detail) },
                      onSaveSenderToContacts = {
                        viewModel.openAddContact(
                          prefillName = detail.senderName,
                          prefillEmail = detail.senderEmail,
                        )
                      },
                    )
                  } else {
                    MailListPane(
                      selectedFolder = selectedFolder,
                      searchFilters = searchFilters,
                      visibleEmailsState = visibleEmailsState,
                      selectedEmailId = null,
                      onQueryChange = viewModel::updateSearchQuery,
                      onFieldFilterChange = viewModel::updateSearchFieldFilter,
                      onScopeChange = viewModel::updateSearchScope,
                      onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                      onToggleStarredOnly = viewModel::toggleStarredOnly,
                      onClearSearch = viewModel::clearSearch,
                      onEmailClick = viewModel::openEmail,
                      onToggleStar = viewModel::toggleStarred,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (composeState.isOpen) {
    ComposeEmailDialog(
      state = composeState,
      senderEmail = localProfile.email,
      contacts = allContacts,
      onToChange = viewModel::updateComposeTo,
      onSubjectChange = viewModel::updateComposeSubject,
      onBodyChange = viewModel::updateComposeBody,
      onSaveDraft = viewModel::saveComposeAsDraft,
      onSend = viewModel::sendComposeEmail,
      onDismiss = viewModel::closeCompose,
    )
  }

  if (contactEditorState.isOpen) {
    ContactEditorDialog(
      state = contactEditorState,
      onNameChange = viewModel::updateContactName,
      onEmailChange = viewModel::updateContactEmail,
      onSave = viewModel::saveContact,
      onDismiss = viewModel::closeContactEditor,
    )
  }

  if (profileEditorState.isOpen) {
    LocalProfileEditorDialog(
      state = profileEditorState,
      onNameChange = viewModel::updateProfileDisplayName,
      onEmailChange = viewModel::updateProfileEmail,
      onSave = viewModel::saveProfile,
      onDismiss = viewModel::closeProfileEditor,
    )
  }
}

@Composable
private fun MailListPane(
  selectedFolder: MailFolder,
  searchFilters: MailSearchFilters,
  visibleEmailsState: UiState<List<EmailDisplayItem>>,
  selectedEmailId: String?,
  onQueryChange: (String) -> Unit,
  onFieldFilterChange: (SearchFieldFilter) -> Unit,
  onScopeChange: (SearchScope) -> Unit,
  onToggleUnreadOnly: () -> Unit,
  onToggleStarredOnly: () -> Unit,
  onClearSearch: () -> Unit,
  onEmailClick: (EmailMessage) -> Unit,
  onToggleStar: (EmailMessage) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    EmailSearchBarAndFilters(
      filters = searchFilters,
      onQueryChange = onQueryChange,
      onFieldFilterChange = onFieldFilterChange,
      onScopeChange = onScopeChange,
      onToggleUnreadOnly = onToggleUnreadOnly,
      onToggleStarredOnly = onToggleStarredOnly,
      onClearSearch = onClearSearch,
      modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )

    when (visibleEmailsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        EmptyMailboxState(
          icon = Icons.Rounded.Inbox,
          title = "Couldn't load emails",
          subtitle = visibleEmailsState.message,
        )
      }
      is UiState.Success -> {
        val items = visibleEmailsState.data
        if (items.isEmpty()) {
          val isSearchActive =
            searchFilters.query.isNotBlank() ||
              searchFilters.unreadOnly ||
              searchFilters.starredOnly
          EmptyMailboxState(
            icon = if (isSearchActive) Icons.Rounded.Search else Icons.Rounded.Inbox,
            title =
              if (isSearchActive) stringResource(R.string.no_search_results_title)
              else stringResource(R.string.no_emails_title),
            subtitle =
              if (isSearchActive) {
                "Try another keyword, switch field filter, or search All Mail."
              } else {
                "Your ${selectedFolder.label} folder is empty."
              },
            actionLabel = if (isSearchActive) "Clear search" else null,
            onAction = if (isSearchActive) onClearSearch else null,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("email_list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
          ) {
            items(items, key = { it.email.id }) { displayItem ->
              EmailListItemCard(
                item = displayItem,
                isSelected = displayItem.email.id == selectedEmailId,
                onClick = { onEmailClick(displayItem.email) },
                onToggleStar = { onToggleStar(displayItem.email) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FolderDrawerContent(
  selectedFolder: MailFolder,
  unreadInboxCount: Int,
  userDisplayName: String,
  userEmail: String,
  onSelectFolder: (MailFolder) -> Unit,
  onEditLocalIdentity: () -> Unit,
) {
  ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.width(290.dp),
  ) {
    Column(modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp)) {
      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = userDisplayName,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = userEmail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Device-local storage · No network",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
      )

      val folderItems: List<Pair<MailFolder, ImageVector>> =
        listOf(
          MailFolder.INBOX to Icons.Outlined.Inbox,
          MailFolder.STARRED to Icons.Outlined.StarBorder,
          MailFolder.SENT to Icons.AutoMirrored.Rounded.Send,
          MailFolder.DRAFTS to Icons.Outlined.Drafts,
          MailFolder.ARCHIVE to Icons.Outlined.Archive,
          MailFolder.TRASH to Icons.Outlined.Delete,
        )

      folderItems.forEach { (folder, icon) ->
        NavigationDrawerItem(
          selected = selectedFolder == folder,
          onClick = { onSelectFolder(folder) },
          icon = { Icon(imageVector = icon, contentDescription = null) },
          label = { Text(folder.label, style = MaterialTheme.typography.labelLarge) },
          badge = {
            if (folder == MailFolder.INBOX && unreadInboxCount > 0) {
              Text(
                text = unreadInboxCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          },
          colors =
            NavigationDrawerItemDefaults.colors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
              selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          modifier =
            Modifier
              .padding(NavigationDrawerItemDefaults.ItemPadding)
              .testTag("drawer_folder_${folder.id}"),
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
      )

      NavigationDrawerItem(
        selected = false,
        onClick = onEditLocalIdentity,
        icon = {
          Icon(
            imageVector = Icons.Rounded.ManageAccounts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        label = {
          Text(
            text = stringResource(R.string.edit_local_identity),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
          )
        },
        modifier =
          Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .testTag("edit_identity_button"),
      )
    }
  }
}

@Composable
private fun LocalProfileEditorDialog(
  state: ProfileEditorState,
  onNameChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.edit_local_identity),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column {
        if (state.validationError != null) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          ) {
            Text(
              text = state.validationError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
        OutlinedTextField(
          value = state.displayName,
          onValueChange = onNameChange,
          label = { Text("Display name") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = state.email,
          onValueChange = onEmailChange,
          label = { Text("Local email address") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("profile_email_input"),
        )
      }
    },
    confirmButton = {
      Button(onClick = onSave, modifier = Modifier.testTag("save_profile_button")) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
