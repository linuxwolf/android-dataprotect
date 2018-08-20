package org.mozilla.mmiller.dataprotect

import android.annotation.TargetApi
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

    var listener : OnActionListener? = null

    fun start(context : Context, keychain : KeystoreAccess) {
        // prep a Cipher for the eventual CryptoObject
        keychain.generate("biometrics")
        val cipher = keychain.createEncryptCipher("biometrics")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startBiometricPrompt(context, cipher)
        } else {
            Log.e("biometrics", "OOPS!  NO SUPPORT")
        }
    }

    @TargetApi(28)
    private fun startBiometricPrompt(context: Context, cipher: Cipher) {
        val signal = CancellationSignal()
        val res = context.resources
        val runner = context.mainExecutor
        val clickHandler = object : DialogInterface.OnClickListener {
            override fun onClick(dlg: DialogInterface?, type: Int) { doCancel() }
        }
        val prompt = BiometricPrompt.Builder(context)
                .setTitle(res.getString(R.string.print_title))
                .setDescription(res.getString(R.string.print_instructions))
                .setNegativeButton("use pin",
                        runner,
                        clickHandler)
                .build()

        val crypto = BiometricPrompt.CryptoObject(cipher)
        prompt.authenticate(crypto, signal, runner, BiometricHandler())
    }

    private fun doCancel() {
        listener?.onCanceled()
    }

    @TargetApi(28)
    private inner class BiometricHandler : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            Log.d("biometrics", "auth error $errorCode: $errString")
            when (errorCode) {
                BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT -> doCancel()
                BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS -> doCancel()
                BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED -> doCancel()
                else -> listener?.onError(errorCode)
            }
        }
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            listener?.onFound()
        }
    }
}