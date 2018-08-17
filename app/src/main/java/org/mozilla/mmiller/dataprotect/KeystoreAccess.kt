package org.mozilla.mmiller.dataprotect

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val KEYSTORE_PROVIDER = "AndroidKeyStore"
private val KEYCHAIN_KEY = "keychain"
private val B64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.URL_SAFE
private val CIPHER_SPEC = "AES/GCM/NoPadding"

class KeystoreAccess {
    enum class LockState {
        LOCKED,
        UNLOCKED
    }

    private val keystore : KeyStore
    private val cache : MutableMap<String, SecretKey>
    private var lockstate : LockState = LockState.LOCKED

    init {
        keystore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keystore.load(null)

        val initCache = HashMap<String, SecretKey>()
        for (a in keystore.aliases()) {
            try {
                val key = keystore.getKey(a, null) as SecretKey
                initCache[a] = key
                Log.i("KeychainAccess", "keystore alias $a := $key")
            } catch (ex : Exception) {
                Log.d("KeychainAccess", "invalid entry $a")
            }
        }
        cache = initCache
    }

    val locked : Boolean get() = lockstate == LockState.LOCKED
    fun lock() {
        lockstate = LockState.LOCKED
    }
    fun unlock() {
        lockstate = LockState.UNLOCKED
    }

    fun available(label: String) = cache.contains(label)
    fun generate(label: String) {
        if (cache.contains(label)) {
            return
        }

        val spec = KeyGenParameterSpec.Builder(label, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        val keygen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keygen.init(spec)
        val key = keygen.generateKey()
        cache[label] = key
    }

    fun createEncryptCipher(label : String) : Cipher {
        val key = cache[label] ?: throw IllegalArgumentException("unknonwn label: $label")

        val cipher = Cipher.getInstance(CIPHER_SPEC)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        return cipher
    }
    fun createDecryptCipher(label : String, iv : ByteArray) : Cipher {
        val key = cache[label] ?: throw IllegalArgumentException("unknonwn label: $label")

        val cipher = Cipher.getInstance(CIPHER_SPEC)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher;
    }

    fun encryptBytes(label : String, pdata : ByteArray) : ByteArray {
        // outputs what is called a "5116-style" crypto object:
        //    <cipher inputs> || <cipher text + tag>
        // The <cipher inputs> include the following here:
        //    <version = 0x01> || <iv/nonce (12 bytes)>
        val cipher = createEncryptCipher(label)
        val cdata = cipher.doFinal(pdata)

        val ver = 0x01
        val iv = cipher.iv

        return byteArrayOf(ver.toByte(), *iv, *cdata)
    }
    fun decryptBytes(label : String, encrypted : ByteArray) : ByteArray {
        // expects a "5116-style" crypto object from above
        val ver = encrypted[0].toInt()
        if (ver != 0x01) {
            throw IllegalArgumentException("unsupported version: $ver")
        }

        val iv = encrypted.sliceArray(1..12)
        val cdata = encrypted.sliceArray(13..encrypted.size-1)

        val cipher = createDecryptCipher(label, iv)
        val pdata = cipher.doFinal(cdata)

        return pdata
    }
}
