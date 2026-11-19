package com.example.data.remote

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Resolves Firebase & Google Sign-In configuration bundled into the APK by the
 * Google Services Gradle plugin (`app/google-services.json`) and `firebase_applet_config.xml`.
 *
 * Uses compile-time `R.string.*` resource IDs directly first (immune to OEM/split/getIdentifier
 * package mismatches), with `FirebaseOptions.fromResource` and multi-package `getIdentifier`
 * fallbacks. Strictly enforces Safe Mode if real non-placeholder values are absent.
 */
object FirebaseConfigProvider {
    private const val TAG = "OneHomeFirebaseCfg"

    data class ResolvedFirebaseConfig(
        val googleAppId: String,
        val apiKey: String,
        val projectId: String,
        val gcmSenderId: String?,
        val storageBucket: String?,
        val defaultWebClientId: String?,
        val firestoreDatabaseId: String
    )

    @Suppress("DiscouragedApi")
    private fun resolveString(
        context: Context,
        directResId: Int,
        resourceName: String
    ): String? {
        runCatching {
            context.getString(directResId).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()?.let { return it }

        val candidates = linkedSetOf<String>()
        candidates.add(context.packageName)
        candidates.add(BuildConfig.APPLICATION_ID)
        runCatching {
            candidates.add(context.resources.getResourcePackageName(directResId))
        }
        runCatching {
            candidates.add(context.resources.getResourcePackageName(R.string.firestore_database_id))
        }
        candidates.add("com.example")

        for (pkg in candidates) {
            val resolved = try {
                val id = context.resources.getIdentifier(resourceName, "string", pkg)
                if (id != 0) context.getString(id).trim().takeIf { it.isNotEmpty() } else null
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
            if (resolved != null) return resolved
        }
        return null
    }

    fun resolveFirestoreDatabaseId(context: Context): String? {
        val appCtx = context.applicationContext ?: context
        return resolveString(appCtx, R.string.firestore_database_id, "firestore_database_id")
    }

    fun resolveWebClientId(context: Context): String? {
        val appCtx = context.applicationContext ?: context
        return resolveString(appCtx, R.string.default_web_client_id, "default_web_client_id")
    }

    private fun resolveBundledConfig(appCtx: Context): Pair<ResolvedFirebaseConfig?, String?> {
        val optionsFromRes = runCatching {
            FirebaseOptions.fromResource(appCtx)
        }.getOrNull()

        val googleAppId = resolveString(appCtx, R.string.google_app_id, "google_app_id")
            ?: optionsFromRes?.applicationId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null to buildDiagnosticReport(
                appCtx = appCtx,
                reason = "Firebase configuration missing: app/google-services.json was not bundled in this APK (google_app_id resource not found).",
                optionsFromResNull = optionsFromRes == null,
                googleAppId = null,
                apiKey = null,
                projectId = null,
                firestoreDbId = resolveFirestoreDatabaseId(appCtx)
            )

        if (googleAppId.startsWith("1:0000") || googleAppId.contains("placeholder", ignoreCase = true)) {
            return null to buildDiagnosticReport(
                appCtx = appCtx,
                reason = "Firebase configuration contains placeholder google_app_id ($googleAppId). Bundle the real app/google-services.json.",
                optionsFromResNull = optionsFromRes == null,
                googleAppId = googleAppId,
                apiKey = null,
                projectId = null,
                firestoreDbId = resolveFirestoreDatabaseId(appCtx)
            )
        }

        val apiKey = resolveString(appCtx, R.string.google_api_key, "google_api_key")
            ?: optionsFromRes?.apiKey?.trim()?.takeIf { it.isNotEmpty() }
        if (apiKey.isNullOrBlank() || apiKey.contains("placeholder", ignoreCase = true)) {
            return null to buildDiagnosticReport(
                appCtx = appCtx,
                reason = "Firebase API key missing or placeholder (google_api_key resource not found).",
                optionsFromResNull = optionsFromRes == null,
                googleAppId = googleAppId,
                apiKey = apiKey,
                projectId = null,
                firestoreDbId = resolveFirestoreDatabaseId(appCtx)
            )
        }

        val projectId = resolveString(appCtx, R.string.project_id, "project_id")
            ?: optionsFromRes?.projectId?.trim()?.takeIf { it.isNotEmpty() }
        if (projectId.isNullOrBlank() || projectId.contains("placeholder", ignoreCase = true)) {
            return null to buildDiagnosticReport(
                appCtx = appCtx,
                reason = "Firebase project_id missing or placeholder (project_id resource not found).",
                optionsFromResNull = optionsFromRes == null,
                googleAppId = googleAppId,
                apiKey = apiKey,
                projectId = projectId,
                firestoreDbId = resolveFirestoreDatabaseId(appCtx)
            )
        }

        val firestoreDbId = resolveFirestoreDatabaseId(appCtx)
        if (firestoreDbId.isNullOrBlank()) {
            return null to buildDiagnosticReport(
                appCtx = appCtx,
                reason = "Firestore database configuration missing: firestore_database_id string resource not found.",
                optionsFromResNull = optionsFromRes == null,
                googleAppId = googleAppId,
                apiKey = apiKey,
                projectId = projectId,
                firestoreDbId = null
            )
        }

        val gcmSenderId = resolveString(appCtx, R.string.gcm_defaultSenderId, "gcm_defaultSenderId")
            ?: optionsFromRes?.gcmSenderId?.trim()?.takeIf { it.isNotEmpty() }
        val storageBucket = resolveString(appCtx, R.string.google_storage_bucket, "google_storage_bucket")
            ?: optionsFromRes?.storageBucket?.trim()?.takeIf { it.isNotEmpty() }
        val webClientId = resolveWebClientId(appCtx)

        return ResolvedFirebaseConfig(
            googleAppId = googleAppId,
            apiKey = apiKey,
            projectId = projectId,
            gcmSenderId = gcmSenderId,
            storageBucket = storageBucket,
            defaultWebClientId = webClientId,
            firestoreDatabaseId = firestoreDbId
        ) to null
    }

    fun ensureFirebaseApp(context: Context): FirebaseApp? {
        val appCtx = context.applicationContext ?: context
        val (resolved, _) = resolveBundledConfig(appCtx)
        if (resolved == null) return null

        val existing = runCatching {
            val apps = FirebaseApp.getApps(appCtx)
            if (apps.isNotEmpty()) FirebaseApp.getInstance() else null
        }.getOrNull()
        if (existing != null) return existing

        val defaultInit = runCatching { FirebaseApp.initializeApp(appCtx) }.getOrNull()
        if (defaultInit != null) return defaultInit

        // Fallback when FirebaseOptions.fromResource(appCtx) returned null on an OEM/ROM
        // despite the compiled R.string resources being bundled in resources.arsc:
        val builder = FirebaseOptions.Builder()
            .setApplicationId(resolved.googleAppId)
            .setApiKey(resolved.apiKey)
            .setProjectId(resolved.projectId)
        resolved.gcmSenderId?.let { builder.setGcmSenderId(it) }
        resolved.storageBucket?.let { builder.setStorageBucket(it) }

        return try {
            FirebaseApp.initializeApp(appCtx, builder.build())
        } catch (e: IllegalStateException) {
            // Concurrent init race — return existing instance
            runCatching { FirebaseApp.getInstance() }.getOrNull()
        } catch (t: Throwable) {
            Log.e(TAG, "Explicit FirebaseApp.initializeApp(context, options) failed", t)
            null
        }
    }

    fun inspectDiagnostic(context: Context): String? {
        val appCtx = context.applicationContext ?: context
        val (resolved, err) = resolveBundledConfig(appCtx)
        if (err != null) return err
        if (resolved == null) {
            return "Firebase configuration missing: unable to read google-services.json resources."
        }
        return try {
            val app = ensureFirebaseApp(appCtx)
            if (app == null) {
                buildDiagnosticReport(
                    appCtx = appCtx,
                    reason = "FirebaseApp.initializeApp returned null despite compiled resources.",
                    optionsFromResNull = runCatching { FirebaseOptions.fromResource(appCtx) }.getOrNull() == null,
                    googleAppId = resolved.googleAppId,
                    apiKey = resolved.apiKey,
                    projectId = resolved.projectId,
                    firestoreDbId = resolved.firestoreDatabaseId
                )
            } else {
                null
            }
        } catch (t: Throwable) {
            "Firebase initialization error: ${t.message ?: t::class.java.simpleName}"
        }
    }

    private fun buildDiagnosticReport(
        appCtx: Context,
        reason: String,
        optionsFromResNull: Boolean,
        googleAppId: String?,
        apiKey: String?,
        projectId: String?,
        firestoreDbId: String?
    ): String {
        val appsCount = runCatching { FirebaseApp.getApps(appCtx).size }.getOrDefault(-1)
        val resPkg = runCatching { appCtx.resources.getResourcePackageName(R.string.google_app_id) }.getOrDefault("unavailable")
        val maskedAppId = googleAppId?.let {
            if (it.length > 16) "${it.take(10)}...${it.takeLast(6)}" else it
        } ?: "null"
        return buildString {
            append(reason)
            append("\nBuild: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • AppId: ${appCtx.packageName}")
            append("\nAndroid SDK: ${Build.VERSION.SDK_INT} • ResPkg: $resPkg • FirebaseApps: $appsCount")
            append("\nfromResourceNull: $optionsFromResNull • googleAppId: $maskedAppId • apiKeyPresent: ${!apiKey.isNullOrBlank()} • projectId: ${projectId ?: "null"} • dbIdPresent: ${!firestoreDbId.isNullOrBlank()}")
        }
    }
}
