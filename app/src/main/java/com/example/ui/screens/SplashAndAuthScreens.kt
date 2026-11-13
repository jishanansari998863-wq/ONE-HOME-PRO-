package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.util.QatarFormatters
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.AuthScreenStep
import com.example.ui.OneHomeUiState
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
            modifier = Modifier.fillMaxWidth(),
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
    }
}

@Composable
fun WelcomeSetupScreen(
    uiState: OneHomeUiState,
    onChangeLanguage: (AppLanguage) -> Unit,
    onSetupOwner: (String, String, String, String, String, Boolean, Boolean) -> Unit,
    onDismissBanner: () -> Unit
) {
    val strings = LocalAppStrings.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var businessName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("Doha HQ") }
    var enableMfa by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("welcome_setup_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
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
            modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = strings.createOwnerAccount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Clean workspace: 0 sample records. Configure your real Owner credentials and company branch in Qatar (QAR / Asia/Qatar).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(strings.fullName) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_full_name_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.email) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("${strings.password} (min 8 chars)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password visibility")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_password_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text(strings.businessName) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_business_name_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text(strings.branchName) },
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_branch_name_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        modifier = Modifier.testTag("setup_remember_me_checkbox")
                    )
                    Text(strings.rememberMe, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = enableMfa,
                        onCheckedChange = { enableMfa = it },
                        modifier = Modifier.testTag("setup_mfa_checkbox")
                    )
                    Text("Enable 2-Step MFA at setup", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSetupOwner(fullName, email, password, businessName, branchName, enableMfa, rememberMe)
                    },
                    enabled = !uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("setup_submit_button")
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(strings.createOwnerAccount)
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
    onDismissBanner: () -> Unit
) {
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
                TextButton(
                    onClick = onGoForgotPassword,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("forgot_password_button")
                ) {
                    Text(strings.forgotPassword)
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
                        text = "Unrecognized device detected for ${challenge.email}. Enter local verification code: ${challenge.verificationCode}",
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
                        StatusBadge(ctx.userType)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("logout_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
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
