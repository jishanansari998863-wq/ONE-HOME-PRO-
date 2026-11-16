package com.example.data.remote

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            cont.resume(task.result as T)
        } else {
            val ex = task.exception ?: IllegalStateException("Firebase task failed or was cancelled")
            cont.resumeWithException(ex)
        }
    }
}
