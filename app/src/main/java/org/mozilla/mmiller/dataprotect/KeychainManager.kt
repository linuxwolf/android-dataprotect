package org.mozilla.mmiller.dataprotect

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val B64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.URL_SAFE
private val CIPHER_SPEC = "AES/GCM/NoPadding"
private val KEYCHAIN_KEY = "keychain"


class KeychainManager(prefs: SharedPreferences) {
    private val prefs = prefs
    private val encKey: SecretKey

    init {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        var keyItem = ks.getEntry(KEYCHAIN_KEY, null) as KeyStore.SecretKeyEntry?
        if (keyItem != null) {
            encKey = keyItem.secretKey
        } else {
            val spec = KeyGenParameterSpec.Builder(KEYCHAIN_KEY, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
            gen.init(spec)
            encKey = gen.generateKey()
        }
    }

    fun getItem(name: String): String? {
        val pkey = "keychain:$name"
        var result = prefs.getString(pkey, null)
        if (result != null) {
            // parse it and decrypt
            val encrypted = Base64.decode(result, B64_FLAGS)

            // this is a form of what's called a "5116-style" structure:
            // <inputs to cipher> || <ciphertext>
            // <inputs to cipher> in this case is also:
            // <version (1 octect)> || <GCM nonce (12 octets)>

            val ver: Int = encrypted[0].toInt()
            val iv = encrypted.sliceArray(1..12)
            val cdata = encrypted.sliceArray(13..encrypted.size-1)

            if (1 != ver) {
                throw IllegalArgumentException("unsupported version $ver")
            }
            val spec = GCMParameterSpec(128, iv)
            val cipher = Cipher.getInstance(CIPHER_SPEC)
            cipher.init(Cipher.DECRYPT_MODE, encKey, spec)
            val pdata = cipher.doFinal(cdata)
            result = String(pdata, StandardCharsets.UTF_8)
        }

        return result
    }
    fun setItem(name: String, value: String): Boolean {
        val pkey = "keychain:$name"

        val pdata = value.toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER_SPEC)

        val rng = SecureRandom()
        val ver = byteArrayOf(1)
        val iv = ByteArray(12)
        rng.nextBytes(iv)

        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encKey, spec)
        val cdata = cipher.doFinal(pdata)

        // this is a form of what's called a "5116-style" structure:
        // <inputs to cipher> || <ciphertext>
        // <inputs to cipher> in this case is also:
        // <version (1 octect)> || <GCM nonce (12 octets)>
        val encrypted = byteArrayOf(*ver, *iv, *cdata)

        val result = Base64.encodeToString(encrypted, B64_FLAGS)
        return prefs.edit()
                .putString(pkey, result)
                .commit()
    }
    fun removeItem(name: String) {
        val pkey = "keychain:$name"
        prefs.edit().remove(pkey).commit()
    }
}