package org.mozilla.mmiller.dataprotect

import android.annotation.TargetApi
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import javax.crypto.Cipher


class BiometricManager {
    interface OnActionListener {
        fun onFound()
        fun onCanceled()
        fun onError(code: Int)
    }

    private var listener : OnActionListener? = null

    fun start(context : Context, keychain : KeystoreAccess) {
        if (context !is BiometricManager.OnActionListener) {
            throw IllegalArgumentException("context must implement OnActionListener")
        }
        listener = context

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure()) {
            Log.d("printdialog", "device is not secured with Pattern/PIN/Password")
            listener?.onFound()
        }

        // prep a Cipher for the eventual CryptoObject
        val cipher = keychain.createEncryptCipher("keychain")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startBiometricPrompt(context, cipher)
        } else {
            startFingerprintManager(context, cipher)
        }
    }

    private fun startFingerprintManager(context : Context, cipher : Cipher) {
        val frag = FingerprintDialogFragment()
        if (!frag.start(context, cipher)) {
            listener?.onCanceled()
        }
    }

    @TargetApi(28)
    private fun startBiometricPrompt(context: Context, cipher: Cipher) {
        val signal = CancellationSignal()
        val res = context.resources
        val runner = context.mainExecutor
        val clickHandler = object : DialogInterface.OnClickListener {
            override fun onClick(dlg: DialogInterface?, type: Int) { listener?.onCanceled() }
        }
        val prompt = BiometricPrompt.Builder(context)
                .setTitle(res.getString(R.string.print_title))
                .setDescription(res.getString(R.string.print_instructions_touch))
                .setNegativeButton(res.getString(R.string.print_cancel_btn),
                        runner,
                        clickHandler)
                .build()

        val crypto = BiometricPrompt.CryptoObject(cipher)
        prompt.authenticate(crypto, signal, runner, BiometricCallback())
    }

    @TargetApi(28)
    private inner class BiometricCallback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            Log.d("biometrics", "auth error $errorCode: $errString")
            when (errorCode) {
                BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
                BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS -> listener?.onFound()
                BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED -> listener?.onCanceled()
                else -> listener?.onError(errorCode)
            }
        }
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            listener?.onFound()
        }
    }
}