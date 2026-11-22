package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.security.CryptoUtil
import com.example.ui.theme.ArchitecturalGoldContainer
import com.example.ui.theme.MonoFontFamily
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class QrMatrix(
    val dimension: Int,
    val darkModules: BooleanArray
) {
    fun isDark(x: Int, y: Int): Boolean = darkModules[y * dimension + x]
}

object QrCodeMatrixEncoder {
    fun encode(content: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val size = maxOf(width, height)
        val modules = BooleanArray(size * size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                modules[y * size + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(dimension = size, darkModules = modules)
    }
}

@Composable
fun ScannableQrCodeBox(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp
) {
    val qrMatrix = remember(content) {
        runCatching { QrCodeMatrixEncoder.encode(content) }.getOrNull()
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .size(size)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        if (qrMatrix != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                val n = qrMatrix.dimension
                if (n <= 0) return@Canvas
                val modulePx = this.size.width / n.toFloat()
                drawRect(color = Color.White, size = this.size)
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (qrMatrix.isDark(x, y)) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * modulePx, y * modulePx),
                                size = Size(modulePx + 0.5f, modulePx + 0.5f)
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "QR unavailable — use Manual Setup Key below",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun MfaSetupKitCard(
    accountEmail: String,
    totpSecret: String,
    recoveryCodes: List<String>,
    masterRecoveryKey: String?,
    title: String = "MFA Authenticator QR & Recovery Kit (Save Once)",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val base32Secret = remember(totpSecret) { CryptoUtil.toBase32TotpSecret(totpSecret) }
    val groupedSecret = remember(base32Secret) { CryptoUtil.formatTotpSecretGrouped(base32Secret) }
    val otpAuthUri = remember(accountEmail, base32Secret) {
        CryptoUtil.buildTotpOtpAuthUri(accountEmail = accountEmail, secret = base32Secret)
    }

    var copiedLabel by remember { mutableStateOf<String?>(null) }
    var launchNotice by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        copiedLabel = label
        launchNotice = null
    }

    val fullKitText = remember(accountEmail, base32Secret, recoveryCodes, masterRecoveryKey) {
        buildString {
            appendLine("ONE HOME PRO - Super Admin MFA & Recovery Kit")
            appendLine("Account: $accountEmail")
            appendLine("TOTP Setup Key (Base32): $base32Secret")
            appendLine("OTPAuth URI: $otpAuthUri")
            if (!masterRecoveryKey.isNullOrBlank()) {
                appendLine("Master Recovery Key: $masterRecoveryKey")
            }
            appendLine("Single-Use Recovery Codes:")
            recoveryCodes.forEachIndexed { index, code ->
                appendLine("  ${index + 1}. $code")
            }
        }
    }

    Surface(
        color = ArchitecturalGoldContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "1. Scan this QR Code in Google Authenticator / Microsoft Authenticator (or tap 'Open in Authenticator App' / Copy Setup Key).\n2. Enter the live 6-digit code shown in your Authenticator app below to finish login.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            ScannableQrCodeBox(
                content = otpAuthUri,
                modifier = Modifier.testTag("sa_mfa_qr_code")
            )

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(otpAuthUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        launchNotice = "Opened Authenticator app. Check the 6-digit code generated for ONE HOME PRO ($accountEmail)."
                    } catch (_: Exception) {
                        copyToClipboard("TOTP Setup Key", base32Secret)
                        launchNotice = "No Authenticator URI handler found — Setup Key copied! Paste it manually into Google Authenticator (Time-based / 6 digits)."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("sa_open_authenticator_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open in Authenticator App")
            }

            // Manual TOTP Secret Key card
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Manual Setup Key (Base32, SHA1, 6-digit, 30s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { copyToClipboard("TOTP Setup Key", base32Secret) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("sa_copy_totp_secret_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy TOTP Key", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Key")
                        }
                    }
                    Text(
                        text = groupedSecret,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("sa_totp_secret_text")
                    )
                }
            }

            if (!masterRecoveryKey.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Master Recovery Key", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { copyToClipboard("Master Recovery Key", masterRecoveryKey) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("sa_copy_master_key_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Master Recovery Key", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }
                        Text(
                            text = masterRecoveryKey,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("sa_master_recovery_key_text")
                        )
                    }
                }
            }

            if (recoveryCodes.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Single-Use Backup Recovery Codes (${recoveryCodes.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        recoveryCodes.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { code ->
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { copyToClipboard("Full MFA & Recovery Kit", fullKitText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("sa_copy_full_kit_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Full MFA & Recovery Kit")
            }

            if (copiedLabel != null || launchNotice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = launchNotice ?: "Copied $copiedLabel to clipboard — save in a safe password manager.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
