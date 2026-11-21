package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
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

object GoogleSignInHelper {
    private const val TAG = "Auth"

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
        if (!ensureFirebaseInitialized(activityContext)) {
            onError("Firebase configuration (google-services.json) is missing in this build.")
            return false
        }
        val clientId = resolveWebClientId(activityContext)
        if (clientId == null) {
            onError("Google Sign-In configuration missing: default_web_client_id not found")
            return false
        }
        val foregroundActivity = activityContext.findActivity()
        if (foregroundActivity == null) {
            onError("Google Sign-In requires an active foreground screen.")
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
                onError("Unexpected credential type returned by Google Sign-In")
            }
            ok
        } catch (e: NoCredentialException) {
            Log.w(TAG, "GetSignInWithGoogleOption returned NoCredentialException; trying all-accounts GoogleId fallback: ${e.message}")
            tryGoogleIdBottomSheetFallback(credentialManager, uiContext, clientId, onCancelled, onError)
        } catch (e: GetCredentialUnsupportedException) {
            Log.w(TAG, "GetSignInWithGoogleOption unsupported; trying GoogleId fallback: ${e.message}")
            tryGoogleIdBottomSheetFallback(credentialManager, uiContext, clientId, onCancelled, onError)
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "Google Credential Manager provider unavailable", e)
            onError("Google Play Services / Credential Manager is unavailable or outdated on this device.")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onCancelled(formatCancellationDetail(e.message))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Google Sign-In failed")
            false
        }
    }

    private suspend fun tryGoogleIdBottomSheetFallback(
        credentialManager: CredentialManager,
        uiContext: Context,
        clientId: String,
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
                onError("Unexpected credential type returned by Google Sign-In")
            }
            ok
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account credential available on device: ${e.message}", e)
            onError("No Google account is signed in on this device. Add your Google account in Android Settings > Accounts and try again.")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onCancelled(formatCancellationDetail(e.message))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In fallback failed", e)
            onError(e.localizedMessage ?: "Google Sign-In failed")
            false
        }
    }

    private fun formatCancellationDetail(rawMessage: String?): String? {
        val clean = rawMessage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            clean.contains("blocked", ignoreCase = true) ->
                "Google Play Services temporarily paused sign-in prompts after repeated cancellations ($clean). Wait a moment or add your Google account in Android Settings and tap Continue with Google Account again."
            clean.contains("16:") || clean.contains("Developer", ignoreCase = true) || clean.contains("reauth", ignoreCase = true) ->
                "Google Play Services rejected or cancelled the prompt ($clean). Ensure you installed the official debug-signed APK and have a signed-in Google account on this device."
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
