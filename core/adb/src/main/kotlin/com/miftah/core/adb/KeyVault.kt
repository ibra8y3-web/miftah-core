package com.miftah.core.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the ADB identity. The RSA private key is encrypted with an AES-GCM key that lives in the
 * Android Keystore (non-exportable); only the ciphertext touches the file system. Nothing is hard-coded.
 */
class KeyVault(context: Context) {
    private val keyFile = File(context.filesDir, "adb_key.enc")
    private val certFile = File(context.filesDir, "adb_cert.der")

    fun hasKeys(): Boolean = keyFile.isFile && certFile.isFile

    fun loadOrCreate(): Pair<PrivateKey, Certificate> {
        if (hasKeys()) {
            try {
                return load()
            } catch (e: GeneralSecurityException) {
                clear() // unreadable identity (e.g. Keystore key lost): discard it; re-pairing is required
            } catch (e: IOException) {
                clear()
            }
        }
        return generate()
    }

    fun clear() {
        keyFile.delete()
        certFile.delete()
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }

    private fun load(): Pair<PrivateKey, Certificate> {
        val plain = decrypt(keyFile.readBytes())
        try {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(plain))
            val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            return key to cert
        } finally {
            plain.fill(0)
        }
    }

    private fun generate(): Pair<PrivateKey, Certificate> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=MIFTAH Core")
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val holder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), Date(now - day), Date(now + 3650 * day), name, kp.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val encoded = kp.private.encoded
        try {
            keyFile.writeBytes(encrypt(encoded))
        } finally {
            encoded.fill(0)
        }
        certFile.writeBytes(cert.encoded)
        return kp.private to cert
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(data)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        if (blob.isEmpty()) throw IOException("empty key file")
        val ivLen = blob[0].toInt() and 0xFF
        if (blob.size <= 1 + ivLen) throw IOException("corrupt key file")
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "miftah_adb_wrap_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
