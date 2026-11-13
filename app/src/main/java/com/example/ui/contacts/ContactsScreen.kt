package com.example.ui.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Contact
import com.example.ui.mail.ContactEditorState
import com.example.ui.mail.EmptyMailboxState
import com.example.ui.mail.MonogramAvatar
import com.example.ui.mail.UiState

@Composable
fun ContactsScreen(
  contactsState: UiState<List<Contact>>,
  query: String,
  onQueryChange: (String) -> Unit,
  onAddContactClick: () -> Unit,
  onEditContactClick: (Contact) -> Unit,
  onDeleteContact: (Contact) -> Unit,
  onComposeToContact: (Contact) -> Unit,
  modifier: Modifier = Modifier,
) {
  var pendingDeleteContact by remember { mutableStateOf<Contact?>(null) }

  Column(modifier = modifier.fillMaxSize()) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = "Search contacts",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (query.isEmpty()) {
            Text(
              text = stringResource(R.string.search_contacts_placeholder),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("contacts_search_input"),
          )
        }
        if (query.isNotEmpty()) {
          IconButton(
            onClick = { onQueryChange("") },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Clear,
              contentDescription = "Clear contact search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    when (contactsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        EmptyMailboxState(
          icon = Icons.Rounded.Contacts,
          title = "Couldn't load contacts",
          subtitle = contactsState.message,
        )
      }
      is UiState.Success -> {
        val contacts = contactsState.data
        if (contacts.isEmpty()) {
          EmptyMailboxState(
            icon = if (query.isBlank()) Icons.Rounded.PersonAdd else Icons.Rounded.Search,
            title =
              if (query.isBlank()) stringResource(R.string.no_contacts_title)
              else "No matching contacts",
            subtitle =
              if (query.isBlank()) {
                "Save people you correspond with to quickly compose emails."
              } else {
                "No contacts matched \"$query\"."
              },
            actionLabel = if (query.isBlank()) "Add contact" else "Clear search",
            onAction = if (query.isBlank()) onAddContactClick else { -> onQueryChange("") },
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(contacts, key = { it.id }) { contact ->
              ContactCard(
                contact = contact,
                onCompose = { onComposeToContact(contact) },
                onEdit = { onEditContactClick(contact) },
                onDelete = { pendingDeleteContact = contact },
              )
            }
          }
        }
      }
    }
  }

  pendingDeleteContact?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDeleteContact = null },
      title = { Text("Delete contact?") },
      text = {
        Text(
          "Remove ${target.name} (${target.email}) from your contacts? This won't delete past emails."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteContact(target)
            pendingDeleteContact = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          modifier = Modifier.testTag("confirm_delete_contact_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteContact = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun ContactCard(
  contact: Contact,
  onCompose: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .testTag("contact_card_${contact.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MonogramAvatar(name = contact.name, size = 44.dp)
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = contact.name,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = contact.email,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onCompose,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("compose_contact_${contact.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.MailOutline,
          contentDescription = "Email ${contact.name}",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(
        onClick = onEdit,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_contact_${contact.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit ${contact.name}",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onDelete,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("delete_contact_${contact.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Delete,
          contentDescription = "Delete ${contact.name}",
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }
}

@Composable
fun ContactEditorDialog(
  state: ContactEditorState,
  onNameChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          if (state.editingContact == null) stringResource(R.string.add_contact)
          else stringResource(R.string.edit_contact),
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
          value = state.name,
          onValueChange = onNameChange,
          label = { Text("Name") },
          placeholder = { Text("Clara Vance") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("contact_name_input"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = state.email,
          onValueChange = onEmailChange,
          label = { Text("Email address") },
          placeholder = { Text("clara@studio.io") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("contact_email_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onSave,
        enabled = !state.isSaving,
        modifier = Modifier.testTag("save_contact_button"),
      ) {
        if (state.isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Text("Save")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") }
    },
  )
}
