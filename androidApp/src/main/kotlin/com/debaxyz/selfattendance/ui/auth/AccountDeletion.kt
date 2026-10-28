package com.debaxyz.selfattendance.ui.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.debaxyz.selfattendance.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/* ============================================================
 *  ACCOUNT DELETION HELPERS
 *  Firebase requires a recent sign-in before deleting an account,
 *  so we always re-verify the user first.
 * ============================================================ */

fun FirebaseUser.usesGoogle(): Boolean =
    providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

/** Re-verify identity with Google (shows the Google account sheet). */
suspend fun reauthenticateWithGoogle(activity: Activity): Result<Unit> = try {
    val user = Firebase.auth.currentUser ?: error("You are not signed in.")
    val clientId = activity.getString(R.string.default_web_client_id)
    val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        Result.success(Unit)
    } else {
        Result.failure(IllegalStateException("Unexpected credential type returned"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Re-verify identity with the account password (email/password users). */
suspend fun reauthenticateWithPassword(password: String): Result<Unit> = try {
    val user = Firebase.auth.currentUser ?: error("You are not signed in.")
    val email = user.email ?: error("This account has no email address.")
    user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    Result.success(Unit)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun deleteAccountErrorMessage(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is GetCredentialCancellationException -> "Verification was cancelled. Nothing was deleted."
        e is FirebaseAuthInvalidCredentialsException -> "Incorrect password. Nothing was deleted."
        e is FirebaseAuthInvalidUserException || msg.contains("mismatch", ignoreCase = true) ->
            "Please verify with the same account you are signed in with."
        e is FirebaseAuthRecentLoginRequiredException -> "Please try again to confirm it's you."
        e is FirebaseNetworkException || msg.contains("network", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ->
            "No internet connection. Connect and try again. Nothing was deleted."
        else -> e.localizedMessage ?: "Could not delete the account. Please try again."
    }
}
