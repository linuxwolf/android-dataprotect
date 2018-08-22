@file:Suppress("DEPRECATION")

package org.mozilla.mmiller.dataprotect

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import javax.crypto.Cipher

class FingerprintDialogFragment : DialogFragment() {
    private var listener : BiometricManager.OnActionListener? = null
    private var canceller : CancellationSignal? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = activity!!.layoutInflater
        val dlg = AlertDialog.Builder(activity)
                .setPositiveButton(R.string.print_pin_btn, { _, _ -> doPin() })
                .setNegativeButton(R.string.print_cancel_btn, { _, _ -> doCancel() })
                .setTitle(R.string.print_title)
                .setView(inflater.inflate(R.layout.dlg_fingerprint, null))
                .create()

        return dlg
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        listener = context as BiometricManager.OnActionListener
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 12345) {
            when (resultCode) {
                Activity.RESULT_OK -> listener?.onFound()
                Activity.RESULT_CANCELED -> doCancel()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun doPin() {
        listener?.onFallback()
        val c = canceller
        c?.cancel()
        canceller = null
    }

    private fun doCancel() {
        listener?.onCanceled()
        val c = canceller
        canceller = null
        c?.cancel()
    }

    fun start(context : Context, cipher : Cipher) : Boolean {
        val printman = context.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        if (!printman.isHardwareDetected()) {
            Log.d("printdialog", "no fingerprint hardware detected!")
            return false
        }
        if (!printman.hasEnrolledFingerprints()) {
            Log.d("printdialog", "no fingerprints enrolled!")
            return false
        }

        // assume just fingerprints for now ...
        val crypto = FingerprintManager.CryptoObject(cipher)
        canceller = CancellationSignal()
        printman.authenticate(crypto, canceller, 0, Handler(), null)

        show((context as AppCompatActivity?)?.supportFragmentManager, "fingerprint")

        return true
    }

    private inner class Handler : FingerprintManager.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            if (canceller == null) {
                Log.d("printdialog", "assume already cancelled")
            } else {
                Log.w("printdialog", "auth failed ($errorCode: $errString)")
                when (errorCode) {
                    FingerprintManager.FINGERPRINT_ERROR_HW_NOT_PRESENT -> doCancel()
                    FingerprintManager.FINGERPRINT_ERROR_NO_FINGERPRINTS -> doCancel()
                    FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED -> doCancel()
                    else -> {
                        canceller?.cancel()
                        listener?.onError(errorCode)
                    }
                }
            }
            this@FingerprintDialogFragment.dismiss()
        }
        override fun onAuthenticationFailed() {
            Log.w("printdialog", "bad fingerprint")
            // TODO: show bad print icon ...
        }

        override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
            Log.i("printdialog", "good print!")
            this@FingerprintDialogFragment.dismiss()
            listener?.onFound()
        }
    }
}