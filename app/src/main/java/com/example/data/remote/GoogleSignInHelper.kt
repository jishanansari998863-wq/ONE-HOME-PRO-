package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object GoogleSignInHelper {
    private const val TAG = "OneHomeGoogleAuth"

    private fun ensureFirebaseInitialized(context: Context): Boolean = try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            true
        } else {
            val init = FirebaseApp.initializeApp(appCtx)
                ?: com.google.firebase.FirebaseOptions.fromResource(appCtx)
                    ?.let { FirebaseApp.initializeApp(appCtx, it) }
            init != null
        }
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
            val result = credentialManager.getCredential(context, request)
            signInWithGoogleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google auto sign-in unavailable: ${e.message}")
            false
        }
    }

    suspend fun signInInteractive(
        activityContext: Context,
        onCancelled: () -> Unit = {},
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
        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activityContext as? Activity ?: activityContext, request)
            val ok = signInWithGoogleCredential(result.credential)
            if (!ok) {
                onError("Unexpected credential type returned by Google Sign-In")
            }
            ok
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onCancelled()
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Google Sign-In failed")
            false
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

    @Suppress("DiscouragedApi")
    internal fun resolveWebClientId(context: Context): String? {
        runCatching {
            context.getString(com.example.R.string.default_web_client_id).takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }
        val candidates = linkedSetOf<String>()
        candidates.add(context.packageName)
        runCatching {
            candidates.add(context.resources.getResourcePackageName(com.example.R.string.firestore_database_id))
        }
        candidates.add("com.example")
        for (pkg in candidates) {
            val resolved = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", pkg)
                if (resId != 0) context.getString(resId).takeIf { it.isNotBlank() } else null
            } catch (_: Exception) {
                null
            }
            if (resolved != null) return resolved
        }
        return null
    }

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
