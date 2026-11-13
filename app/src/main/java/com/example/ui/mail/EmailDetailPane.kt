package com.example.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import com.example.ui.theme.AmberStar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmailDetailPane(
  email: EmailMessage,
  isSenderInContacts: Boolean,
  showBackButton: Boolean,
  onBack: () -> Unit,
  onReply: () -> Unit,
  onForward: () -> Unit,
  onToggleStar: () -> Unit,
  onToggleUnread: () -> Unit,
  onArchive: () -> Unit,
  onTrashOrDelete: () -> Unit,
  onRestoreFromTrash: () -> Unit,
  onSaveSenderToContacts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fullTimestamp =
    if (email.sentAtMillis > 0L) {
      SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.getDefault())
        .format(Date(email.sentAtMillis))
    } else ""

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBackButton) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Back to list",
            )
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onToggleStar,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_star_button"),
        ) {
          Icon(
            imageVector = if (email.starred) Icons.Rounded.Star else Icons.Outlined.StarBorder,
            contentDescription = if (email.starred) "Unstar" else "Star",
            tint = if (email.starred) AmberStar else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onToggleUnread,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_unread_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.MarkEmailUnread,
            contentDescription = "Mark as unread",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (email.folder == MailFolder.TRASH.id) {
          IconButton(
            onClick = onRestoreFromTrash,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_restore_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.RestoreFromTrash,
              contentDescription = "Restore to Inbox",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        } else {
          IconButton(
            onClick = onArchive,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_archive_button"),
          ) {
            Icon(
              imageVector =
                if (email.folder == MailFolder.ARCHIVE.id) Icons.Rounded.Unarchive
                else Icons.Outlined.Archive,
              contentDescription =
                if (email.folder == MailFolder.ARCHIVE.id) "Unarchive" else "Archive",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(
          onClick = onTrashOrDelete,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_delete_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription =
              if (email.folder == MailFolder.TRASH.id) "Delete permanently" else "Move to Trash",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(
      modifier =
        Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text =
              MailFolder.entries.firstOrNull { it.id == email.folder }?.label ?: "Inbox",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        if (fullTimestamp.isNotEmpty()) {
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = fullTimestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = email.subject.ifBlank { "(No subject)" },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )

      Spacer(modifier = Modifier.height(18.dp))

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MonogramAvatar(name = email.senderName.ifBlank { email.senderEmail }, size = 44.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = email.senderName.ifBlank { email.senderEmail },
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = email.senderEmail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "To: ${email.recipientEmail}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (!isSenderInContacts && email.senderEmail.isNotBlank()) {
            TextButton(
              onClick = onSaveSenderToContacts,
              modifier = Modifier.testTag("save_sender_to_contacts_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.PersonAdd,
                contentDescription = "Add sender to contacts",
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Text(
        text = email.body.ifBlank { "(No message body)" },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )

      Spacer(modifier = Modifier.height(28.dp))
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onReply,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("detail_reply_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Reply,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reply")
      }

      OutlinedButton(
        onClick = onForward,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("detail_forward_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Forward,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Forward")
      }
    }
  }
}
