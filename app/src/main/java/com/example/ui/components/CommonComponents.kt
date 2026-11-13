package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.entities.BusinessEntity
import com.example.domain.security.AuthContext
import com.example.domain.util.QatarFormatters
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneHomeProTopBar(
    title: String,
    authContext: AuthContext?,
    businesses: List<BusinessEntity>,
    language: AppLanguage,
    darkTheme: Boolean,
    unreadNotifications: Int,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onSwitchBusiness: (String) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit
) {
    var langMenuOpen by remember { mutableStateOf(false) }
    var branchMenuOpen by remember { mutableStateOf(false) }
    val activeBiz = businesses.firstOrNull { it.id == authContext?.activeBusinessId }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("top_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = LocalAppStrings.current.back
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureWhite)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.one_home_pro_logo),
                        contentDescription = "ONE HOME PRO Logo",
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeBiz != null && authContext != null && !authContext.isCustomerPortalOnly()) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(enabled = businesses.size > 1) { branchMenuOpen = true }
                                .testTag("branch_switcher_row")
                        ) {
                            Text(
                                text = "${activeBiz.name} • ${activeBiz.branchName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (businesses.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch branch",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = branchMenuOpen,
                            onDismissRequest = { branchMenuOpen = false }
                        ) {
                            businesses.forEach { biz ->
                                DropdownMenuItem(
                                    text = { Text("${biz.name} (${biz.branchName})") },
                                    onClick = {
                                        branchMenuOpen = false
                                        onSwitchBusiness(biz.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            Box {
                IconButton(
                    onClick = { langMenuOpen = true },
                    modifier = Modifier.testTag("language_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Change Language (${language.code.uppercase()})"
                    )
                }
                DropdownMenu(
                    expanded = langMenuOpen,
                    onDismissRequest = { langMenuOpen = false }
                ) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName) },
                            leadingIcon = {
                                if (lang == language) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = {
                                langMenuOpen = false
                                onChangeLanguage(lang)
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { onToggleTheme(!darkTheme) },
                modifier = Modifier.testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Light or Dark Theme"
                )
            }

            if (authContext != null) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = LocalAppStrings.current.notifications
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSecurity,
                    modifier = Modifier.testTag("security_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Account & Security"
                    )
                }
            }
        }
    )
}

@Composable
fun FeedbackBanner(
    error: String?,
    success: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = error ?: success ?: return
    val isError = error != null
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer else EmeraldContainer
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer else EmeraldSuccess

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(if (isError) "error_banner" else "success_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = fg
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("dismiss_banner_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss notification", tint = fg)
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val normalized = status.uppercase()
    val (bg, fg) = when (normalized) {
        "ACTIVE", "PAID", "CONFIRMED", "CLEARED", "COMPLETED", "APPROVED", "VACANT", "PRESENT", "OPEN", "RECONCILED", "VERIFIED_RESTORE", "SETTLED" ->
            EmeraldContainer to EmeraldSuccess
        "DUE", "PARTIAL", "PENDING", "PENDING_CLEARANCE", "DEPOSITED", "RECEIVED", "RESERVED", "IN_PROGRESS", "HELD", "ASSIGNED", "AWAITING_APPROVAL", "AWAITING_CUSTOMER" ->
            AmberContainer to AmberWarning
        "OVERDUE", "BOUNCED", "REJECTED", "FAILED", "REVERSED", "MAINTENANCE", "SUSPENDED", "LOCKED", "CRITICAL", "DISCREPANCY", "CLOSED" ->
            CrimsonContainer to CrimsonError
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = normalized.replace("_", " "),
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = if (value.contains("QAR")) MonoFontFamily else MaterialTheme.typography.titleMedium.fontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QarAmountText(
    fils: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = QatarFormatters.formatQar(fils),
        style = style.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier
    )
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    testTag: String = "empty_state_card",
    onAction: (() -> Unit)? = null
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("${testTag}_cta")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedCard(modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("permission_denied_card"),
        colors = CardDefaults.outlinedCardColors(containerColor = CrimsonContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = CrimsonError)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(strings.permissionDeniedTitle, style = MaterialTheme.typography.titleMedium, color = CrimsonError)
                Text(strings.permissionDeniedBody, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionTag: String = "section_action_button",
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag(actionTag)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_selector"
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedItem?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(testTag)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun createTempCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val photoFile = File.createTempFile("capture_${System.currentTimeMillis()}_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}
