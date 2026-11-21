package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

object GoogleSignInHelper {
    private const val TAG = "OneHomeAuth"
    const val EXPECTED_FIREBASE_DEBUG_SHA1 = "05:35:6B:A3:33:4F:EB:7A:32:DA:59:57:F6:94:51:F9:94:00:9F:E1"

    data class SignInDiagnostics(
        val packageName: String,
        val versionLabel: String,
        val isDebugBuild: Boolean,
        val installedApkSha1: String?,
        val expectedDebugSha1: String,
        val debugSha1MatchesFirebase: Boolean?,
        val webClientId: String?,
        val googleAppId: String?,
        val projectId: String?,
        val playServicesStatus: String,
        val lastAttemptTrace: String?
    ) {
        fun toReportText(): String = buildString {
            appendLine("ONE HOME PRO — Sign-In Runtime Diagnostics")
            appendLine("Package: $packageName ($versionLabel)")
            appendLine("Build Type: ${if (isDebugBuild) "DEBUG" else "RELEASE"}")
            appendLine("Installed APK SHA-1: ${installedApkSha1 ?: "Unavailable (test/robolectric)"}")
            if (isDebugBuild) {
                val matchLabel = when (debugSha1MatchesFirebase) {
                    true -> "MATCHES Firebase"
                    false -> "MISMATCH! Reinstall official ONE_HOME_PRO_FINAL.apk"
                    null -> "Unknown"
                }
                appendLine("Expected Firebase SHA-1: $expectedDebugSha1 ($matchLabel)")
            }
            appendLine("Web OAuth Client ID: ${webClientId ?: "MISSING"}")
            appendLine("Firebase App ID: ${googleAppId ?: "MISSING"}")
            appendLine("Firebase Project ID: ${projectId ?: "MISSING"}")
            appendLine("Google Play Services: $playServicesStatus")
            if (!lastAttemptTrace.isNullOrBlank()) {
                appendLine("Last Credential Manager Trace:")
                appendLine(lastAttemptTrace)
            }
        }.trim()
    }

    @Volatile
    private var lastAttemptTrace: String? = null

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun ensureFirebaseInitialized(context: Context): Boolean = try {
        FirebaseConfigProvider.ensureFirebaseApp(context) != null
    } catch (e: Exception) {
        Log.e(TAG, "FirebaseApp initialization check failed", e)
        false
    }

    internal fun isTechnicalCancellationForFallback(rawMessage: String?): Boolean {
        val clean = rawMessage?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (clean.equals("activity is cancelled by the user.", ignoreCase = true) ||
            clean.equals("activity is cancelled by the user", ignoreCase = true) ||
            clean.contains("blocked", ignoreCase = true)
        ) {
            return false
        }
        return clean.contains("16:") ||
            clean.contains("[16]") ||
            clean.contains("reauth", ignoreCase = true) ||
            clean.contains("developer", ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun readSigningSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkgName = context.packageName
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            val info: PackageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
            info.signatures ?: emptyArray()
        }
        val firstCert = certs.firstOrNull() ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-1").digest(firstCert.toByteArray())
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    private fun readPlayServicesStatus(context: Context): String = runCatching {
        val pm = context.packageManager
        val gmsInfo = pm.getPackageInfo("com.google.android.gms", 0)
        val appEnabled = runCatching { pm.getApplicationInfo("com.google.android.gms", 0).enabled }.getOrDefault(true)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            gmsInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            gmsInfo.versionCode.toLong()
        }
        val state = if (appEnabled) "Enabled" else "DISABLED"
        "$state • v${gmsInfo.versionName ?: "?"} ($versionCode)"
    }.getOrElse { "Not found or inaccessible (${it::class.java.simpleName})" }

    private fun readStringResourceSafe(context: Context, resId: Int, fallbackName: String): String? =
        runCatching { context.getString(resId).trim().takeIf { it.isNotEmpty() } }.getOrNull()
            ?: runCatching {
                @Suppress("DiscouragedApi")
                val id = context.resources.getIdentifier(fallbackName, "string", context.packageName)
                if (id != 0) context.getString(id).trim().takeIf { it.isNotEmpty() } else null
            }.getOrNull()

    fun collectRuntimeDiagnostics(context: Context): SignInDiagnostics {
        val appCtx = context.applicationContext ?: context
        val sha1 = readSigningSha1(appCtx)
        val matchesFirebase = if (BuildConfig.DEBUG && sha1 != null) {
            sha1.equals(EXPECTED_FIREBASE_DEBUG_SHA1, ignoreCase = true)
        } else {
            null
        }
        return SignInDiagnostics(
            packageName = appCtx.packageName,
            versionLabel = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            isDebugBuild = BuildConfig.DEBUG,
            installedApkSha1 = sha1,
            expectedDebugSha1 = EXPECTED_FIREBASE_DEBUG_SHA1,
            debugSha1MatchesFirebase = matchesFirebase,
            webClientId = resolveWebClientId(appCtx),
            googleAppId = readStringResourceSafe(appCtx, R.string.google_app_id, "google_app_id"),
            projectId = readStringResourceSafe(appCtx, R.string.project_id, "project_id"),
            playServicesStatus = readPlayServicesStatus(appCtx),
            lastAttemptTrace = lastAttemptTrace
        )
    }

    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (!ensureFirebaseInitialized(context)) return false
        if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) return true
        val clientId = resolveWebClientId(context) ?: return false
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val hostContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(hostContext, request)
            signInWithGoogleCredential(result.credential)
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credentials saved on device: ${e.message}")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google auto sign-in unavailable: ${e.message}")
            false
        }
    }

    suspend fun signInInteractive(
        activityContext: Context,
        onCancelled: (String?) -> Unit = {},
        onError: (String) -> Unit
    ): Boolean {
        val diag = collectRuntimeDiagnostics(activityContext)
        Log.i(
            TAG,
            "Starting interactive Google Sign-In | pkg=${diag.packageName} | sha1=${diag.installedApkSha1} | sha1Match=${diag.debugSha1MatchesFirebase} | webClient=${diag.webClientId} | gms=${diag.playServicesStatus}"
        )

        if (!ensureFirebaseInitialized(activityContext)) {
            val msg = "Firebase configuration (google-services.json) is missing in this build."
            lastAttemptTrace = msg
            onError(msg)
            return false
        }
        val clientId = diag.webClientId
        if (clientId == null) {
            val msg = "Google Sign-In configuration missing: default_web_client_id not found"
            lastAttemptTrace = msg
            onError(msg)
            return false
        }
        if (diag.isDebugBuild && diag.debugSha1MatchesFirebase == false) {
            val msg = "Installed APK signing SHA-1 (${diag.installedApkSha1}) does not match Firebase (${diag.expectedDebugSha1}). Uninstall this APK and install ONE_HOME_PRO_FINAL.apk."
            lastAttemptTrace = msg
            Log.e(TAG, msg)
            onError(msg)
            return false
        }

        val foregroundActivity = activityContext.findActivity()
        if (foregroundActivity == null) {
            val msg = "Google Sign-In requires an active foreground screen."
            lastAttemptTrace = msg
            onError(msg)
            return false
        }
        val uiContext: Context = foregroundActivity
        val credentialManager = CredentialManager.create(foregroundActivity)

        // Request 1: Explicit interactive Sign in with Google button flow (1 option per request for API 36)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(uiContext, primaryRequest)
            val ok = signInWithGoogleCredential(result.credential)
            if (!ok) {
                val msg = "Unexpected credential type returned by Google Sign-In"
                lastAttemptTrace = msg
                onError(msg)
            } else {
                lastAttemptTrace = "SUCCESS via GetSignInWithGoogleOption (${diag.packageName})"
            }
            ok
        } catch (e: NoCredentialException) {
            Log.w(TAG, "GetSignInWithGoogleOption -> NoCredentialException (${e.type}): ${e.message}")
            tryGoogleIdBottomSheetFallback(
                credentialManager = credentialManager,
                uiContext = uiContext,
                clientId = clientId,
                diag = diag,
                primaryFailure = "GetSignInWithGoogleOption -> NoCredentialException: ${e.message}",
                onCancelled = onCancelled,
                onError = onError
            )
        } catch (e: GetCredentialUnsupportedException) {
            Log.w(TAG, "GetSignInWithGoogleOption -> GetCredentialUnsupportedException (${e.type}): ${e.message}")
            tryGoogleIdBottomSheetFallback(
                credentialManager = credentialManager,
                uiContext = uiContext,
                clientId = clientId,
                diag = diag,
                primaryFailure = "GetSignInWithGoogleOption -> Unsupported: ${e.message}",
                onCancelled = onCancelled,
                onError = onError
            )
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "Google Credential Manager provider unavailable", e)
            val msg = "Google Play Services / Credential Manager is unavailable or outdated on this device (${e.message ?: e.type})."
            lastAttemptTrace = "GetCredentialProviderConfigurationException (${e.type}): ${e.message}"
            onError(msg)
            false
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "GetSignInWithGoogleOption -> Cancellation (${e.type}): ${e.message}", e)
            if (isTechnicalCancellationForFallback(e.message)) {
                Log.w(TAG, "Technical Play Services rejection on button flow; attempting Credential Manager bottom sheet once.")
                tryGoogleIdBottomSheetFallback(
                    credentialManager = credentialManager,
                    uiContext = uiContext,
                    clientId = clientId,
                    diag = diag,
                    primaryFailure = "GetSignInWithGoogleOption -> ${e.type}: ${e.message}",
                    onCancelled = onCancelled,
                    onError = onError
                )
            } else {
                lastAttemptTrace = "GetSignInWithGoogleOption -> Cancelled (${e.type}): ${e.message}"
                onCancelled(formatCancellationDetail(e.message, diag, null))
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In GetCredentialException (${e.type}): ${e.message}", e)
            lastAttemptTrace = "GetSignInWithGoogleOption -> ${e::class.java.simpleName} (${e.type}): ${e.message}"
            onError("${e::class.java.simpleName}: ${e.message ?: e.type}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            lastAttemptTrace = "GetSignInWithGoogleOption -> ${e::class.java.simpleName}: ${e.message}"
            onError(e.localizedMessage ?: "Google Sign-In failed")
            false
        }
    }

    private suspend fun tryGoogleIdBottomSheetFallback(
        credentialManager: CredentialManager,
        uiContext: Context,
        clientId: String,
        diag: SignInDiagnostics,
        primaryFailure: String,
        onCancelled: (String?) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val fallbackRequest = GetCredentialRequest.Builder()
            .addCredentialOption(fallbackOption)
            .build()
        return try {
            val result = credentialManager.getCredential(uiContext, fallbackRequest)
            val ok = signInWithGoogleCredential(result.credential)
            if (!ok) {
                val msg = "Unexpected credential type returned by Google Sign-In"
                lastAttemptTrace = "$primaryFailure\nFallback -> $msg"
                onError(msg)
            } else {
                lastAttemptTrace = "SUCCESS via GetGoogleIdOption fallback (after: $primaryFailure)"
            }
            ok
        } catch (e: NoCredentialException) {
            Log.w(TAG, "GetGoogleIdOption fallback -> NoCredentialException (${e.type}): ${e.message}", e)
            lastAttemptTrace = "$primaryFailure\nFallback GetGoogleIdOption -> NoCredentialException (${e.type}): ${e.message}"
            onError("No Google account is signed in or authorized on this device (${e.message ?: "NoCredentialException"}). Add/re-authenticate your Google account in Android Settings and ensure OAuth Consent Screen allows this account.")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "GetGoogleIdOption fallback -> Cancellation (${e.type}): ${e.message}", e)
            lastAttemptTrace = "$primaryFailure\nFallback GetGoogleIdOption -> Cancelled (${e.type}): ${e.message}"
            onCancelled(formatCancellationDetail(e.message, diag, primaryFailure))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In fallback GetCredentialException (${e.type}): ${e.message}", e)
            lastAttemptTrace = "$primaryFailure\nFallback GetGoogleIdOption -> ${e::class.java.simpleName} (${e.type}): ${e.message}"
            onError("${e::class.java.simpleName}: ${e.message ?: e.type}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In fallback failed", e)
            lastAttemptTrace = "$primaryFailure\nFallback GetGoogleIdOption -> ${e::class.java.simpleName}: ${e.message}"
            onError(e.localizedMessage ?: "Google Sign-In failed")
            false
        }
    }

    private fun formatCancellationDetail(
        rawMessage: String?,
        diag: SignInDiagnostics,
        primaryFailure: String?
    ): String? {
        val clean = rawMessage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            clean.contains("blocked", ignoreCase = true) ->
                "Google Play Services temporarily paused sign-in prompts after repeated cancellations ($clean). Wait a moment or check 'Verify APK & Sign-In Diagnostics' below."
            clean.contains("16:") || clean.contains("[16]") || clean.contains("Developer", ignoreCase = true) || clean.contains("reauth", ignoreCase = true) -> {
                val shaSummary = if (diag.debugSha1MatchesFirebase == false) {
                    "Installed APK SHA-1 (${diag.installedApkSha1}) does NOT match Firebase (${diag.expectedDebugSha1})."
                } else {
                    "APK SHA-1 (${diag.installedApkSha1 ?: "verified"}) & Web Client ID match Firebase. In Google Cloud Console (${diag.projectId ?: "gen-lang-client-0874428284"}) > OAuth Consent Screen > Audience, add your Gmail under Test Users (or Publish app) and confirm your device Google account needs no re-auth."
                }
                val fallbackNote = if (!primaryFailure.isNullOrBlank()) " Both button & bottom-sheet prompts failed." else ""
                "Google Play Services rejected or cancelled the prompt ($clean).$fallbackNote $shaSummary"
            }
            clean.equals("activity is cancelled by the user.", ignoreCase = true) ||
                clean.equals("activity is cancelled by the user", ignoreCase = true) ->
                null
            else -> clean
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(1_500L) {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                if (ensureFirebaseInitialized(context)) {
                    runCatching { Firebase.auth.signOut() }
                }
            }
        }
    }

    internal fun resolveWebClientId(context: Context): String? =
        FirebaseConfigProvider.resolveWebClientId(context)

    private suspend fun signInWithGoogleCredential(credential: androidx.credentials.Credential): Boolean {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(authCredential).awaitResult()
            return Firebase.auth.currentUser != null
        }
        return false
    }
}
