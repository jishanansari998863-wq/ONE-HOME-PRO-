package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var appContext: Context
    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    open fun setUpFirebase() {
        appContext = ApplicationProvider.getApplicationContext<Context>()
        val resId = appContext.resources.getIdentifier("firestore_database_id", "string", appContext.packageName)
        databaseId = if (resId != 0) appContext.getString(resId) else "ai-studio-android-onehomep-b7e88f8b-dd6f-442a-8d76-7b31f8e0512f"

        val app = if (FirebaseApp.getApps(appContext).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.onehomepro.qtrvbx")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()

            val newAuth = FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            newApp
        } else {
            FirebaseApp.getInstance()
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId)
        auth = FirebaseAuth.getInstance(app)
    }

    @After
    open fun tearDownFirebase() {
        auth.signOut()
    }

    protected suspend fun signInTestUser(email: String, verifyEmailInEmulator: Boolean = true): String =
        withContext(Dispatchers.IO) {
            withTimeout(AUTH_TIMEOUT_MS) {
                val result = try {
                    auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
                } catch (_: FirebaseAuthInvalidUserException) {
                    auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
                }
                val user = checkNotNull(result.user) { "User auth failed for $email" }
                if (verifyEmailInEmulator && !user.isEmailVerified) {
                    markEmailVerifiedViaAuthEmulator(email, user.getIdToken(false).await().token!!)
                    user.reload().await()
                    user.getIdToken(true).await()
                }
                user.uid
            }
        }

    protected suspend fun clearFirestoreEmulatorData() = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val url = URL("http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 4000
                readTimeout = 4000
            }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun markEmailVerifiedViaAuthEmulator(email: String, idToken: String) {
        // Trigger verification oobCode via Identity Toolkit emulator and immediately consume latest oobLink
        val sendUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake-api-key")
        val sendConn = (sendUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        sendConn.outputStream.use { os ->
            os.write("""{"requestType":"VERIFY_EMAIL","idToken":"$idToken"}""".toByteArray())
        }
        sendConn.responseCode
        sendConn.disconnect()

        val listUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/oobCodes")
        val listJson = listUrl.openConnection().let { conn ->
            (conn as HttpURLConnection).inputStream.bufferedReader().readText().also { conn.disconnect() }
        }
        val arr = JSONObject(listJson).getJSONArray("oobCodes")
        for (i in arr.length() - 1 downTo 0) {
            val item = arr.getJSONObject(i)
            if (item.optString("email").equals(email, ignoreCase = true) &&
                item.optString("requestType") == "VERIFY_EMAIL"
            ) {
                val oobCode = item.getString("oobCode")
                val applyUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key")
                val applyConn = (applyUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                applyConn.outputStream.use { os ->
                    os.write("""{"oobCode":"$oobCode"}""".toByteArray())
                }
                applyConn.responseCode
                applyConn.disconnect()
                break
            }
        }
    }

    protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is FirebaseFirestoreException) return current
            current = current.cause
        }
        return null
    }

    protected companion object {
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 8000L
    }
}
