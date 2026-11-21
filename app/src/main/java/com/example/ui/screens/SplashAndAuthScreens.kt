package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.security.SuperAdminConfig
import com.example.domain.util.QatarFormatters
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeUiState
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun BrandedSplashScreen() {
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 6.dp,
                color = PureWhite,
                modifier = Modifier.size(148.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.one_home_pro_logo),
                    contentDescription = "ONE HOME PRO Official Brand Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.editionSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color = ArchitecturalGold,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun AuthHeaderCard(
    subtitle: String,
    language: AppLanguage,
    onChangeLanguage: (AppLanguage) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End
        ) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = { onChangeLanguage(lang) },
                    label = { Text(lang.code.uppercase()) },
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .testTag("auth_lang_${lang.code}")
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PureWhite,
            shadowElevation = 4.dp,
            modifier = Modifier.size(96.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.one_home_pro_logo),
                contentDescription = "ONE HOME PRO Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "ONE HOME PRO",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            modifier = Modifier.testTag("auth_build_version_label")
        )
    }
}

@Composable
fun RestrictedWelcomeGateScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onGoogleSignIn: (Context) -> Unit,
    onOpenCredentialsLogin: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("restricted_gate_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = strings.editionSubtitle,
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(14.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Exclusive Super Admin Access Gate",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Server-Verified Entry Required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    color = AmberContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ONE HOME PRO operates under strict Super Admin authorization. Installing this APK or submitting documents never grants automatic business access until explicitly approved on the central server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Button(
                    onClick = { onGoogleSignIn(context) },
                    enabled = !uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("google_signin_gate_button")
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Continue with Google Account")
                    }
                }

                if (uiState.hasExistingUsers) {
                    OutlinedButton(
                        onClick = onOpenCredentialsLogin,
                        enabled = !uiState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("open_credentials_login_button")
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Existing Approved User Sign In")
                    }
                }

                var showDiagDialog by remember { mutableStateOf(false) }
                var copiedDiag by remember { mutableStateOf(false) }
                TextButton(
                    onClick = {
                        copiedDiag = false
                        showDiagDialog = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("open_signin_diagnostics_button")
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verify APK & Sign-In Diagnostics")
                }

                if (showDiagDialog) {
                    val diagnostics = remember(showDiagDialog, uiState.bannerError, uiState.isBusy) {
                        com.example.data.remote.GoogleSignInHelper.collectRuntimeDiagnostics(context)
                    }
                    val reportText = remember(diagnostics) { diagnostics.toReportText() }
                    AlertDialog(
                        onDismissRequest = { showDiagDialog = false },
                        title = { Text("APK & Sign-In Verification") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = when (diagnostics.debugSha1MatchesFirebase) {
                                        true -> EmeraldContainer.copy(alpha = 0.7f)
                                        false -> MaterialTheme.colorScheme.errorContainer
                                        null -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = when (diagnostics.debugSha1MatchesFirebase) {
                                            true -> "Signing SHA-1 MATCHES Firebase OAuth client."
                                            false -> "Signing SHA-1 MISMATCH! Uninstall this APK and install ONE_HOME_PRO_FINAL.apk."
                                            null -> "Signing SHA-1 verified by runtime PackageManager."
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Text(
                                    text = reportText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("signin_diagnostics_report_text")
                                )
                                if (copiedDiag) {
                                    Text(
                                        text = "Diagnostics copied to clipboard.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("OneHomeProSignInDiagnostics", reportText))
                                    copiedDiag = true
                                },
                                modifier = Modifier.testTag("copy_signin_diagnostics_button")
                            ) {
                                Text("Copy Report")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiagDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SuperAdminBootstrapScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onBootstrap: (String, String, String) -> Unit,
    onSignOut: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("super_admin_bootstrap_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = "First-Time Super Admin Authority Initialization",
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Lock Exclusive Super Admin Authority",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Verified Identity: ${uiState.centralIdentity?.email ?: SuperAdminConfig.SUPER_ADMIN_EMAIL}\nYour Super Admin account stays bound to your verified Google identity (never locked to a single phone). Mandatory TOTP MFA, recovery codes, and a Master Recovery Key will be generated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = { Text("Super Admin Master Password (min 10 chars)") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_bootstrap_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Master Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_bootstrap_confirm_input")
                )
                val mismatch = confirmPassword.isNotEmpty() && masterPassword != confirmPassword
                if (mismatch) {
                    Text("Passwords do not match", color = CrimsonError, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                Text(
                    text = "Dual-Loss Backup Recovery Question (Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = securityQuestion,
                    onValueChange = { securityQuestion = it },
                    label = { Text("Security Recovery Question (e.g., First office city?)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_bootstrap_question_input")
                )
                OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = { securityAnswer = it },
                    label = { Text("Security Recovery Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_bootstrap_answer_input")
                )
                Button(
                    onClick = { onBootstrap(masterPassword, securityQuestion, securityAnswer) },
                    enabled = !uiState.isBusy && masterPassword.length >= 10 && masterPassword == confirmPassword,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_bootstrap_submit_button")
                ) {
                    Text("Bootstrap & Generate MFA + Recovery Kit")
                }
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(strings.logout)
                }
            }
        }
    }
}

@Composable
fun SuperAdminVerifyScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onVerify: (String, String) -> Unit,
    onOpenRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    var masterPassword by remember { mutableStateOf("") }
    var totpOrRecovery by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("super_admin_verify_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = "Super Admin 2-Factor Verification",
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Super Admin Authority Challenge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Verified Account: ${uiState.centralIdentity?.email ?: SuperAdminConfig.SUPER_ADMIN_EMAIL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val kit = uiState.lastRecoveryKit
                val provisionPair = uiState.superAdminMfaProvision
                if (kit != null || provisionPair != null) {
                    val secret = kit?.totpSecret ?: provisionPair!!.first
                    val codes = kit?.recoveryCodes ?: provisionPair!!.second
                    Surface(
                        color = ArchitecturalGoldContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sa_mfa_provision_box")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "SAVE ONCE IN SAFE PLACE — EMERGENCY NEW-PHONE RECOVERY KIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text("TOTP Secret: $secret", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                            Text("Single-Use Recovery Codes: ${codes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                            if (kit != null && kit.masterRecoveryKey.isNotBlank()) {
                                Text("Master Recovery Key: ${kit.masterRecoveryKey}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = { Text("Master Password") },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_verify_password_input")
                )
                OutlinedTextField(
                    value = totpOrRecovery,
                    onValueChange = { totpOrRecovery = it },
                    label = { Text("6-Digit TOTP, Recovery Code, or Master Recovery Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_verify_code_input")
                )
                Button(
                    onClick = { onVerify(masterPassword, totpOrRecovery) },
                    enabled = !uiState.isBusy && masterPassword.isNotBlank() && totpOrRecovery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_verify_submit_button")
                ) {
                    Text("Verify & Enter Super Admin Console")
                }
                OutlinedButton(
                    onClick = onOpenRecovery,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_open_recovery_button")
                ) {
                    Icon(Icons.Default.PhonelinkErase, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lost Phone or Forgot Password? Recover Account")
                }
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(strings.logout)
                }
            }
        }
    }
}

@Composable
fun SuperAdminRecoveryScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onRecover: (String, String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onDismissBanner: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    var recoveryProof by remember { mutableStateOf("") }
    var newMasterPassword by remember { mutableStateOf("") }
    var confirmMasterPassword by remember { mutableStateOf("") }
    var newQuestion by remember { mutableStateOf("") }
    var newAnswer by remember { mutableStateOf("") }
    var revokeOtherDevices by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("super_admin_recovery_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = "Super Admin Account & Lost-Device Recovery",
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Recover Exclusive Super Admin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Verified Google Account: ${uiState.centralIdentity?.email ?: SuperAdminConfig.SUPER_ADMIN_EMAIL}\nUse this if you lost your old phone (with TOTP) OR lost both phone & Master Password. Rotating credentials immediately invalidates prior credentials and locks old lost devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                uiState.superAdminSecurityQuestion?.takeIf { it.isNotBlank() }?.let { question ->
                    Surface(
                        color = ArchitecturalGoldContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sa_recovery_question_box")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Configured Security Question:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                OutlinedTextField(
                    value = recoveryProof,
                    onValueChange = { recoveryProof = it },
                    label = { Text("Master Recovery Key, Single-Use Code, or Security Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_proof_input")
                )
                OutlinedTextField(
                    value = newMasterPassword,
                    onValueChange = { newMasterPassword = it },
                    label = { Text("New Master Password (min 10 chars)") },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_new_password_input")
                )
                OutlinedTextField(
                    value = confirmMasterPassword,
                    onValueChange = { confirmMasterPassword = it },
                    label = { Text("Confirm New Master Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_confirm_password_input")
                )
                if (confirmMasterPassword.isNotEmpty() && newMasterPassword != confirmMasterPassword) {
                    Text("Passwords do not match", color = CrimsonError, style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Immediately Lock Lost Phone & Revoke Other Sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Prevents the old lost device from entering even with valid password/OTP until explicitly re-authorized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = revokeOtherDevices,
                        onCheckedChange = { revokeOtherDevices = it },
                        modifier = Modifier.testTag("sa_recovery_revoke_others_switch")
                    )
                }

                OutlinedTextField(
                    value = newQuestion,
                    onValueChange = { newQuestion = it },
                    label = { Text("Update Security Question (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_new_question_input")
                )
                OutlinedTextField(
                    value = newAnswer,
                    onValueChange = { newAnswer = it },
                    label = { Text("Update Security Answer (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_new_answer_input")
                )

                Button(
                    onClick = {
                        onRecover(recoveryProof, newMasterPassword, newQuestion, newAnswer, revokeOtherDevices)
                    },
                    enabled = !uiState.isBusy &&
                        recoveryProof.isNotBlank() &&
                        newMasterPassword.length >= 10 &&
                        newMasterPassword == confirmMasterPassword,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_recovery_submit_button")
                ) {
                    Text("Complete Recovery & Lock Old Devices")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_recovery_back_button")
                    ) {
                        Text(strings.back)
                    }
                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(strings.logout)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRevokedLockedScreen(
    uiState: OneHomeUiState,
    onRefreshStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("device_revoked_locked_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = CrimsonContainer.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(56.dp))
                Text(
                    text = "Device Permanently Locked (Lost / Revoked)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonError
                )
                Text(
                    text = uiState.gateLockReason
                        ?: "This physical device was revoked as LOST or replaced during Super Admin recovery. Even a valid Master Password or OTP cannot unlock this device unless an active Super Admin device explicitly re-authorizes it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("device_locked_recheck_button")
                    ) {
                        Text("Recheck")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("device_locked_signout_button")
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
fun AccessRequestAndStatusScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onSubmitRequest: (String, String, String, String, String, String, String, String) -> Unit,
    onRefreshStatus: () -> Unit,
    onOpenCredentialsLogin: () -> Unit,
    onSignOut: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    val existingReq = uiState.myAccessRequest
    val existingGrant = uiState.myAccessGrant
    val statusLabel = existingGrant?.status ?: existingReq?.status ?: SuperAdminConfig.GRANT_PENDING

    var fullName by remember(existingReq?.uid) {
        mutableStateOf(existingReq?.fullName ?: uiState.centralIdentity?.displayName.orEmpty())
    }
    var phone by remember(existingReq?.uid) { mutableStateOf(existingReq?.phone.orEmpty()) }
    var requestedRole by remember(existingReq?.uid) {
        mutableStateOf(
            existingReq?.requestedRole?.takeIf { it in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES } ?: "OWNER"
        )
    }
    var requestedBusiness by remember(existingReq?.uid) { mutableStateOf(existingReq?.requestedBusinessName.orEmpty()) }
    var requestedBranch by remember(existingReq?.uid) {
        mutableStateOf(existingReq?.requestedBranchName?.ifBlank { "Doha HQ" } ?: "Doha HQ")
    }
    var qidNumber by remember(existingReq?.uid) { mutableStateOf(existingReq?.qidNumber.orEmpty()) }
    var passportNumber by remember(existingReq?.uid) { mutableStateOf(existingReq?.passportNumber.orEmpty()) }
    var idDocSummary by remember(existingReq?.uid) { mutableStateOf(existingReq?.identityDocSummary.orEmpty()) }

    val pickIdPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            idDocSummary = "Selected ID Photo (${uri.lastPathSegment ?: "document"})"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("access_request_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthHeaderCard(
            subtitle = "Central Super Admin Approval Portal",
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Authorization Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = uiState.centralIdentity?.email ?: "Pending account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(statusLabel, modifier = Modifier.testTag("access_status_badge"))
                }

                uiState.gateLockReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Surface(color = CrimsonContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(reason, color = CrimsonError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }

                if (existingReq != null) {
                    Text(
                        text = "Submitted for '${existingReq.requestedBusinessName}' (${existingReq.requestedRole}). Note: ${existingReq.reviewNote ?: "Awaiting Super Admin review"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val canEditSubmission = existingReq == null || existingReq.status == SuperAdminConfig.GRANT_PENDING
                val regOpen = uiState.platformStatus?.registrationsEnabled != false
                if (!regOpen && existingReq == null) {
                    Surface(color = CrimsonContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "New access requests are currently paused by the Super Admin.",
                            color = CrimsonError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (canEditSubmission && existingGrant == null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Submit Verification & Access Request", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("${strings.fullName}*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("req_full_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number (+974...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("req_phone_input")
                    )
                    DropdownSelector(
                        label = "Requested Role*",
                        items = SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES,
                        selectedItem = requestedRole,
                        itemLabel = { it },
                        onSelect = { requestedRole = it },
                        testTag = "req_role_selector"
                    )
                    OutlinedTextField(
                        value = requestedBusiness,
                        onValueChange = { requestedBusiness = it },
                        label = { Text("${strings.businessName}*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("req_business_input")
                    )
                    OutlinedTextField(
                        value = requestedBranch,
                        onValueChange = { requestedBranch = it },
                        label = { Text(strings.branchName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("req_branch_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qidNumber,
                            onValueChange = { qidNumber = it },
                            label = { Text("QID Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("req_qid_input")
                        )
                        OutlinedTextField(
                            value = passportNumber,
                            onValueChange = { passportNumber = it },
                            label = { Text("Passport Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("req_passport_input")
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            pickIdPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (idDocSummary.isBlank()) "Attach ID / Business Document (Optional)" else "$idDocSummary ✓")
                    }
                    Button(
                        onClick = {
                            onSubmitRequest(
                                fullName, phone, requestedRole, requestedBusiness,
                                requestedBranch, qidNumber, passportNumber, idDocSummary
                            )
                        },
                        enabled = !uiState.isBusy && fullName.trim().length >= 2 && requestedBusiness.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("req_submit_button")
                    ) {
                        Text(if (existingReq == null) "Submit Access Request" else "Update Pending Request")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = onRefreshStatus,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("req_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check Approval")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("req_signout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.logout)
                    }
                }
                if (uiState.hasExistingUsers) {
                    TextButton(
                        onClick = onOpenCredentialsLogin,
                        modifier = Modifier.align(Alignment.CenterHorizontally).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Portal Password Login")
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyLockedScreen(
    uiState: OneHomeUiState,
    onRefreshStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("emergency_locked_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = CrimsonContainer.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.GppMaybe, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(56.dp))
                Text(
                    text = "Global Emergency Lock Active",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonError
                )
                Text(
                    text = uiState.gateLockReason
                        ?: uiState.platformStatus?.lockMessage
                        ?: "All non-Super-Admin access has been temporarily suspended by the Super Admin.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("emergency_refresh_button")
                    ) {
                        Text("Recheck")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("emergency_signout_button")
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onSignIn: (String, String, Boolean) -> Unit,
    onGoForgotPassword: () -> Unit,
    onBackToGate: () -> Unit,
    onDismissBanner: () -> Unit
) {
    BackHandler { onBackToGate() }
    val strings = LocalAppStrings.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("login_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = strings.editionSubtitle,
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.signIn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Valid password alone never grants entry without active Super Admin server authorization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.email) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.password) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password visibility")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        modifier = Modifier.testTag("login_remember_me_checkbox")
                    )
                    Text(strings.rememberMe, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onSignIn(email, password, rememberMe) },
                    enabled = !uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("login_submit_button")
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(strings.signIn)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = onBackToGate,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("back_to_gate_button")
                    ) {
                        Text("Google Gate")
                    }
                    TextButton(
                        onClick = onGoForgotPassword,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("forgot_password_button")
                    ) {
                        Text(strings.forgotPassword)
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    uiState: OneHomeUiState,
    onRequestReset: (String) -> Unit,
    onCompleteReset: (String, String, String) -> Unit,
    onBackToLogin: () -> Unit,
    onDismissBanner: () -> Unit
) {
    BackHandler { onBackToLogin() }
    val strings = LocalAppStrings.current
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("forgot_password_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.forgotPassword, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.email) },
                    modifier = Modifier.fillMaxWidth().testTag("reset_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onRequestReset(email) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("request_otp_button")
                ) {
                    Text("Generate Single-Use Reset OTP")
                }
                uiState.pendingResetOtpNotice?.let { notice ->
                    Spacer(Modifier.height(8.dp))
                    Surface(color = AmberContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(notice, color = AmberWarning, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = { Text("6-Digit Verification OTP") },
                    modifier = Modifier.fillMaxWidth().testTag("reset_otp_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (min 8 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("reset_new_password_input")
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onCompleteReset(email, otp, newPassword) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("complete_reset_button")
                ) {
                    Text("Reset Password & Revoke All Sessions")
                }
                TextButton(
                    onClick = onBackToLogin,
                    modifier = Modifier.align(Alignment.CenterHorizontally).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(strings.back)
                }
            }
        }
    }
}

@Composable
fun DeviceChallengeScreen(
    uiState: OneHomeUiState,
    onVerifyDevice: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismissBanner: () -> Unit
) {
    BackHandler { onCancel() }
    val strings = LocalAppStrings.current
    val challenge = uiState.pendingDeviceChallenge
    var code by remember { mutableStateOf("") }
    var trustDevice by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("device_challenge_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.newDeviceVerification, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (challenge != null) {
                    Text(
                        text = "Unrecognized device detected for ${challenge.email}. Enter verification code: ${challenge.verificationCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Device Verification Code") },
                    modifier = Modifier.fillMaxWidth().testTag("device_code_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = trustDevice, onCheckedChange = { trustDevice = it })
                    Text("Trust this device for future logins")
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onVerifyDevice(code, trustDevice) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("verify_device_button")
                ) {
                    Text(strings.verifyAndContinue)
                }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(strings.cancel)
                }
            }
        }
    }
}

@Composable
fun MfaChallengeScreen(
    uiState: OneHomeUiState,
    onVerifyMfa: (String) -> Unit,
    onCancel: () -> Unit,
    onDismissBanner: () -> Unit
) {
    BackHandler { onCancel() }
    val strings = LocalAppStrings.current
    val challenge = uiState.pendingMfaChallenge
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("mfa_challenge_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FeedbackBanner(uiState.bannerError, uiState.bannerSuccess, onDismissBanner)
        ElevatedCard(modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.mfaVerification, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Enter your 6-digit TOTP authenticator code or single-use recovery code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (challenge != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Authenticator secret hint: ${challenge.demoHintInfo}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("TOTP or Recovery Code") },
                    modifier = Modifier.fillMaxWidth().testTag("mfa_code_input")
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onVerifyMfa(code) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("verify_mfa_button")
                ) {
                    Text(strings.verifyAndContinue)
                }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(strings.cancel)
                }
            }
        }
    }
}

@Composable
fun AccountSecurityScreen(
    uiState: OneHomeUiState,
    onEnableMfa: () -> Unit,
    onDisableMfa: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onLogout: () -> Unit,
    onLogoutAllDevices: () -> Unit,
    onSubmitClosureRequest: (String) -> Unit
) {
    val ctx = uiState.authContext ?: return
    val strings = LocalAppStrings.current
    val currentUser = uiState.data.users.firstOrNull { it.id == ctx.userId }
    val mfaEnabled = currentUser?.mfaEnabled == true
    var closureReason by remember { mutableStateOf("") }
    var showClosureDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("account_security_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ctx.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(ctx.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(if (ctx.isSuperAdmin) "SUPER ADMIN" else ctx.userType)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.logout)
                        }
                        Button(
                            onClick = onLogoutAllDevices,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("logout_all_button")
                        ) {
                            Text(strings.logoutAllDevices, maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Factor Authentication (TOTP + Recovery Codes)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (mfaEnabled) "MFA is active on this account." else "Protect your account with TOTP and single-use recovery codes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (mfaEnabled) {
                            OutlinedButton(onClick = onDisableMfa, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("disable_mfa_button")) {
                                Text("Disable")
                            }
                        } else {
                            Button(onClick = onEnableMfa, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("enable_mfa_button")) {
                                Text("Enable MFA")
                            }
                        }
                    }
                    uiState.mfaProvisionInfo?.let { (secret, codes) ->
                        Spacer(Modifier.height(10.dp))
                        Surface(color = ArchitecturalGoldContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("TOTP Secret: $secret", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily))
                                Spacer(Modifier.height(4.dp))
                                Text("Recovery Codes (save once): ${codes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Active & Remembered Sessions (${uiState.data.activeSessions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (uiState.data.activeSessions.isEmpty()) {
            item { EmptyStateCard("No Sessions", "Session list is empty.") }
        } else {
            items(uiState.data.activeSessions, key = { it.id }) { session ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.deviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "RememberMe=${session.rememberMe} • Expires ${QatarFormatters.formatDateTime(session.expiresAtEpoch)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.isRevoked) {
                            StatusBadge("REVOKED")
                        } else {
                            TextButton(onClick = { onRevokeSession(session.id) }, modifier = Modifier.testTag("revoke_session_${session.id}")) {
                                Text(if (session.id == ctx.sessionId) "Current" else "Revoke")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Trusted Devices (${uiState.data.userDevices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(uiState.data.userDevices, key = { it.id }) { dev ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                        Text("Last seen: ${QatarFormatters.formatDateTime(dev.lastSeenAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(if (dev.isTrusted) "TRUSTED" else "UNTRUSTED")
                }
            }
        }

        item {
            Text("Recent Login History (${uiState.data.loginHistory.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(uiState.data.loginHistory.take(10), key = { it.id }) { lh ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lh.deviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${lh.details} • ${QatarFormatters.formatDateTime(lh.occurredAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(lh.outcome)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showClosureDialog = true },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("request_closure_button")
            ) {
                Text("Request Account Closure / Deletion Review")
            }
        }
    }

    if (showClosureDialog) {
        AlertDialog(
            onDismissRequest = { showClosureDialog = false },
            title = { Text("Request Account Closure") },
            text = {
                OutlinedTextField(
                    value = closureReason,
                    onValueChange = { closureReason = it },
                    label = { Text("Reason for closure request") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitClosureRequest(closureReason)
                    showClosureDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showClosureDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun StartupErrorScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onRetry: () -> Unit,
    onRepairDatabase: (Context) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("startup_error_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeaderCard(
            subtitle = strings.editionSubtitle,
            language = uiState.language,
            onChangeLanguage = onChangeLanguage
        )
        Spacer(Modifier.height(16.dp))
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = uiState.startupErrorTitle ?: "Startup Initialization Error",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("startup_error_title")
                        )
                        Text(
                            text = "Protected Safe Mode • Dashboard Locked",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = uiState.startupErrorMessage
                        ?: "ONE HOME PRO could not complete startup initialization. Please review the details below and retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("startup_error_message")
                )

                val details = uiState.startupErrorDetails
                if (!details.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("startup_error_details")
                        )
                    }
                }

                Button(
                    onClick = onRetry,
                    enabled = !uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("startup_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Startup")
                }

                if (uiState.isDatabaseStartupError) {
                    OutlinedButton(
                        onClick = { onRepairDatabase(context) },
                        enabled = !uiState.isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("startup_repair_db_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Repair Local Cache & Restart")
                    }
                }
            }
        }
    }
}

