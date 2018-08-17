package org.mozilla.mmiller.dataprotect

import android.app.AlertDialog
import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.util.Log

class FingerprintDialogFragment : DialogFragment() {
    interface FingerprintActionListener {
        fun onFingerprintCanceled(dlg: FingerprintDialogFragment)
        fun onFingerprintFound(dlg: FingerprintDialogFragment)
    }

    private var listener : FingerprintActionListener? = null
    private var canceller : CancellationSignal? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val res = resources
        val inflater = activity!!.layoutInflater
        val dlg = AlertDialog.Builder(activity)
                .setNegativeButton(R.string.print_cancel_btn, { _, _ -> doCancel() })
                .setTitle(R.string.print_title)
                .setView(inflater.inflate(R.layout.dlg_fingerprint, null))
                .create()

        return dlg
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        listener = context as FingerprintActionListener
    }

    private fun doCancel() {
        canceller?.cancel()
        listener?.onFingerprintCanceled(this)
    }

    fun start(parent : AppCompatActivity, keychain : KeystoreAccess) : Boolean {
        val keyguard = parent.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val printman = parent.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        if (!printman.isHardwareDetected()) {
            Log.d("printdialog", "no fingerprint hardware detected!")
            return false
        }
        if (!printman.hasEnrolledFingerprints()) {
            Log.d("printdialog", "no fingerprints enrolled!")
            return false
        }
        if (!keyguard.isDeviceSecure()) {
            Log.d("printdialog", "device is not secured with Pattern/PIN/Password")
            return false
        }

        // assume just fingerprints for now ...
        keychain.generate("fingerprint")
        val crypto = FingerprintManager.CryptoObject(keychain.createEncryptCipher("fingerprint"))
        canceller = CancellationSignal()
        printman.authenticate(crypto, canceller, 0, Handler(), null)

        show(parent.supportFragmentManager, "fingerprint")

        return true
    }

    private inner class Handler : FingerprintManager.AuthenticationCallback() {
        override fun onAuthenticationFailed() {
            Log.w("printdialog", "bad fingerprint")
        }

        override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
            Log.i("printdialog", "good print!")
            listener?.onFingerprintFound(this@FingerprintDialogFragment)
            this@FingerprintDialogFragment.dismiss()
        }
    }
}