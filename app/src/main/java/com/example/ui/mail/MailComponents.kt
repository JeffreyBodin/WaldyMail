package com.example.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EmailMessage
import com.example.data.model.MailFolder
import com.example.ui.theme.AmberStar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private val AvatarBgPalette =
  listOf(
    Color(0xFFD6E3FF),
    Color(0xFFFFDAD3),
    Color(0xFFC6EFE3),
    Color(0xFFF3E2C7),
    Color(0xFFE3D8F5),
    Color(0xFFD5E8D4),
  )

private val AvatarFgPalette =
  listOf(
    Color(0xFF174B87),
    Color(0xFF9E331D),
    Color(0xFF14594B),
    Color(0xFF7A4F0B),
    Color(0xFF4C337A),
    Color(0xFF28592A),
  )

@Composable
fun MonogramAvatar(
  name: String,
  size: Dp = 42.dp,
  modifier: Modifier = Modifier,
) {
  val trimmed = name.trim()
  val initial = trimmed.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
  val idx = (trimmed.hashCode().absoluteValue) % AvatarBgPalette.size

  Box(
    modifier = modifier.size(size).clip(CircleShape).background(AvatarBgPalette[idx]),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initial,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = AvatarFgPalette[idx],
    )
  }
}

fun formatEmailTimestamp(epochMillis: Long): String {
  if (epochMillis <= 0L) return ""
  val nowCal = Calendar.getInstance()
  val msgCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
  return when {
    nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
      nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) ->
      SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
      nowCal.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
    nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) ->
      SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
  }
}

@Composable
fun EmailSearchBarAndFilters(
  filters: MailSearchFilters,
  onQueryChange: (String) -> Unit,
  onFieldFilterChange: (SearchFieldFilter) -> Unit,
  onScopeChange: (SearchScope) -> Unit,
  onToggleUnreadOnly: () -> Unit,
  onToggleStarredOnly: () -> Unit,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = "Search emails",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (filters.query.isEmpty()) {
            Text(
              text = stringResource(R.string.search_inbox_placeholder),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = filters.query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("inbox_search_input"),
          )
        }
        if (filters.query.isNotEmpty() || filters.unreadOnly || filters.starredOnly) {
          IconButton(
            onClick = onClearSearch,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Clear,
              contentDescription = "Clear search and filters",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (filters.query.isNotBlank()) {
        SearchFieldFilter.entries.forEach { field ->
          val selected = filters.fieldFilter == field
          FilterChip(
            selected = selected,
            onClick = { onFieldFilterChange(field) },
            label = { Text(field.label, style = MaterialTheme.typography.labelMedium) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier = Modifier.testTag("search_field_${field.name.lowercase()}"),
          )
        }
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
          modifier =
            Modifier
              .clip(RoundedCornerShape(12.dp))
              .clickable {
                onScopeChange(
                  if (filters.scope == SearchScope.INBOX) SearchScope.ALL_MAIL
                  else SearchScope.INBOX
                )
              }
              .testTag("search_scope_toggle"),
        ) {
          Text(
            text = "Scope: ${filters.scope.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          )
        }
      } else {
        FilterChip(
          selected = filters.unreadOnly,
          onClick = onToggleUnreadOnly,
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.MarkEmailUnread,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text("Unread", style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("filter_unread_chip"),
        )
        FilterChip(
          selected = filters.starredOnly,
          onClick = onToggleStarredOnly,
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Star,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text("Starred", style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("filter_starred_chip"),
        )
      }
    }
  }
}

@Composable
fun EmailListItemCard(
  item: EmailDisplayItem,
  isSelected: Boolean,
  onClick: () -> Unit,
  onToggleStar: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val email = item.email
  val isUnread = !email.read
  val isSentOrDraft =
    email.folder == MailFolder.SENT.id || email.folder == MailFolder.DRAFTS.id
  val primaryParty =
    if (isSentOrDraft) {
      if (email.recipientEmail.isBlank()) "(No recipient)" else "To: ${email.recipientEmail}"
    } else {
      email.senderName.ifBlank { email.senderEmail }
    }

  val containerColor =
    when {
      isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
      isUnread -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
      else -> Color.Transparent
    }

  val border =
    if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    else null

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = containerColor,
    border = border,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("email_card_${email.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(contentAlignment = Alignment.TopEnd) {
        MonogramAvatar(name = if (isSentOrDraft) email.recipientEmail.ifBlank { "D" } else primaryParty)
        if (isUnread) {
          Box(
            modifier =
              Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = primaryParty,
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
              ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = formatEmailTimestamp(email.sentAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isUnread) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = email.subject.ifBlank { "(No subject)" },
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
            ),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = email.body.replace("\n", " ").trim().ifBlank { "No message body" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = onToggleStar,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("star_button_${email.id}"),
          ) {
            Icon(
              imageVector =
                if (email.starred) Icons.Rounded.Star else Icons.Outlined.StarBorder,
              contentDescription = if (email.starred) "Unstar email" else "Star email",
              tint = if (email.starred) AmberStar else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        if (item.matchedIn.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "Matched in ${item.matchedIn.joinToString(" · ")}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun EmptyMailboxState(
  icon: ImageVector = Icons.Rounded.Inbox,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier.size(68.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
          onClick = onAction,
          colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}
