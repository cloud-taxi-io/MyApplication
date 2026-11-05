package com.example.myapplication.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhoneAuthUiState(
    val user: FirebaseUser? = null,
    /** Non-null once the SMS has been sent: the UI then asks for the OTP code. */
    val verificationId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Phone number + SMS OTP sign-in with Firebase Authentication (Google Identity Platform).
 * Phone number must be in E.164 format, e.g. +213550123456.
 */
class PhoneAuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(PhoneAuthUiState(user = auth.currentUser))
    val state: StateFlow<PhoneAuthUiState> = _state.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        // Instant verification or SMS auto-retrieval: no code entry needed.
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signIn(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Verification failed") }
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken,
        ) {
            resendToken = token
            _state.update { it.copy(isLoading = false, verificationId = verificationId, error = null) }
        }
    }

    fun sendCode(activity: Activity, phoneNumber: String, resend: Boolean = false) {
        _state.update { it.copy(isLoading = true, error = null) }
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resend) resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyCode(code: String) {
        val id = _state.value.verificationId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        signIn(PhoneAuthProvider.getCredential(id, code.trim()))
    }

    /** Go back to the phone number step (wrong number, etc.). */
    fun changeNumber() {
        resendToken = null
        _state.update { it.copy(verificationId = null, error = null) }
    }

    fun signOut() {
        auth.signOut()
        resendToken = null
        _state.value = PhoneAuthUiState()
    }

    private fun signIn(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _state.value = PhoneAuthUiState(user = task.result?.user)
            } else {
                _state.update {
                    it.copy(isLoading = false, error = task.exception?.localizedMessage ?: "Invalid code")
                }
            }
        }
    }
}
