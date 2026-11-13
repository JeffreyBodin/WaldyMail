package com.example.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Contact

@Composable
fun ComposeEmailDialog(
  state: ComposeDraftState,
  senderEmail: String,
  contacts: List<Contact>,
  onToChange: (String) -> Unit,
  onSubjectChange: (String) -> Unit,
  onBodyChange: (String) -> Unit,
  onSaveDraft: () -> Unit,
  onSend: () -> Unit,
  onDismiss: () -> Unit,
) {
  val suggestedContacts =
    remember(state.toEmail, contacts) {
      val q = state.toEmail.trim()
      if (contacts.isEmpty()) emptyList()
      else if (q.isEmpty()) contacts.take(5)
      else
        contacts
          .filter {
            (it.name.contains(q, ignoreCase = true) ||
              it.email.contains(q, ignoreCase = true)) &&
              !it.email.equals(q, ignoreCase = true)
          }
          .take(5)
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.background,
      modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("compose_close_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close compose")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (state.draftId != null) "Edit Draft" else "New Message",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
              onClick = onSaveDraft,
              enabled = !state.isSending,
              modifier = Modifier.testTag("save_draft_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Draft")
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
              onClick = onSend,
              enabled = !state.isSending,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(48.dp).testTag("send_email_button"),
            ) {
              if (state.isSending) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Send,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send")
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
          modifier =
            Modifier
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
          if (state.validationError != null) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
              Text(
                text = state.validationError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              )
            }
          }

          Text(
            text = "From: $senderEmail",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
          )

          OutlinedTextField(
            value = state.toEmail,
            onValueChange = onToChange,
            label = { Text("To") },
            placeholder = { Text("recipient@example.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("compose_to_input"),
          )

          if (suggestedContacts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              suggestedContacts.forEach { contact ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier =
                    Modifier
                      .clip(RoundedCornerShape(12.dp))
                      .clickable { onToChange(contact.email) }
                      .testTag("compose_contact_chip_${contact.id}"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "${contact.name} (${contact.email})",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = state.subject,
            onValueChange = onSubjectChange,
            label = { Text("Subject") },
            placeholder = { Text("What is this about?") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("compose_subject_input"),
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = state.body,
            onValueChange = onBodyChange,
            label = { Text("Message") },
            placeholder = { Text("Write your message...") },
            minLines = 8,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
              ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("compose_body_input"),
          )
        }
      }
    }
  }
}
