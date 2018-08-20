package org.mozilla.mmiller.dataprotect

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.mozilla.mmiller.dataprotect.FingerprintDialogFragment
import java.nio.charset.StandardCharsets

class KeyValAdapter(
        private val prefs: SharedPreferences,
        private val keychain: KeystoreAccess,
        private val itemKeys: List<String>
) : RecyclerView.Adapter<KeyValAdapter.ViewHolder>() {
    override fun getItemCount() = itemKeys.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.keyval_item, parent,  false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val key = itemKeys[pos]
        var value = prefs.getString(key, "")

        if (!keychain.locked) {
            var bvalue = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            bvalue = keychain.decryptBytes("keychain", bvalue)
            value = String(bvalue, StandardCharsets.UTF_8)
        }

        holder.keyView.text = key
        holder.valView.text = value
    }

    class ViewHolder(val view : View) : RecyclerView.ViewHolder(view) {
        var keyView : TextView
        var valView : TextView

        init {
            keyView = view.findViewById(R.id.key_view)
            valView = view.findViewById(R.id.val_view)
        }
    }
}

class MainActivity : AppCompatActivity(), BiometricManager.OnActionListener {
    private lateinit var keychain : KeystoreAccess
    private val itemKeys: List<String>

    init {
        itemKeys = ArrayList<String>().apply {
            for (idx in 1..5) {
                add("key $idx")
            }
        }
    }

    private lateinit var keyvalView : RecyclerView
    private lateinit var keyvalAdapter : KeyValAdapter
    private lateinit var keyvalManager : RecyclerView.LayoutManager
    private lateinit var btnToggleLocked : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keychain = KeystoreAccess()
        keychain.generate("keychain")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this).apply {
            val editor = this.edit()
            for (kname in itemKeys) {
                if (!this.contains(kname)) {
                    val pvalue = "value for $kname"
                    val encrypted = keychain.encryptBytes("keychain", pvalue.toByteArray(StandardCharsets.UTF_8))
                    val kvalue = Base64.encodeToString(encrypted, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
                    editor.putString(kname, kvalue)
                    Log.d("Main", "created pref '$kname'")
                }
            }
            editor.commit()
        }

        keyvalAdapter = KeyValAdapter(prefs, keychain, itemKeys)
        keyvalManager = LinearLayoutManager(this)

        keyvalView = findViewById<RecyclerView>(R.id.keyval_list).apply {
            setHasFixedSize(true)

            layoutManager = keyvalManager
            adapter = keyvalAdapter
        }

        btnToggleLocked = findViewById(R.id.btn_toggle_locked)
        btnToggleLocked.setOnClickListener { onToggleLockedClicked() }
    }

    private fun onToggleLockedClicked() {
        if (!keychain.locked) {
            keychain.lock()
            doLockUpdated()
        } else {
            startUnlock()
        }
    }

    private fun startUnlock() {
        val biometrics = BiometricManager()
        biometrics.start(this, keychain)
    }

    private fun doLockUpdated() {
        btnToggleLocked.setText(if (keychain.locked) R.string.btn_locked_unlock else R.string.btn_locked_lock)
        keyvalAdapter.notifyDataSetChanged()
    }

    override fun onCanceled() {
        Log.d("Main", "fingerprint unlock canceled")
        doLockUpdated()
    }

    override fun onError(code: Int) {
        Log.d("Main", "fingerprint unlock failed! ($code)")
    }

    override fun onFound() {
        Log.d("Main", "fingerprint unlock success!")
        keychain.unlock()
        doLockUpdated()
    }
}
