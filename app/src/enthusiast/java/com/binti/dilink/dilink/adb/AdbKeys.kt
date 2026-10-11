// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Ported from DiPlay (https://github.com/shihabal3amri/DiPlay).
// Implements the AOSP android_pubkey wire format.

package com.binti.dilink.dilink.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Binti's own ADB key — adbd remembers it after the driver approves it once.
 *
 * Ported from DiPlay's `AdbKeys.kt`. Generates an RSA-2048 keypair on first
 * use, stores it in `noBackupFilesDir/adb/`, and provides:
 * - [sign] — signs the ADB auth token using NONEwithRSA with a prepended
 *   SHA-1 DigestInfo header (because adbd treats the token as a digest).
 * - [publicKeyMessage] — encodes the public key in AOSP's `android_pubkey`
 *   wire format (modulus size, n0inv, R^2 mod n, exponent).
 *
 * The key persists across app updates because it's stored in
 * `noBackupFilesDir` (not `filesDir`), which survives app reinstalls
 * but is excluded from ADB backups.
 *
 * @author Dr. Waleed Mandour (ported from DiPlay by shihabal3amri)
 */
object AdbKeys {

    private const val DIR = "adb"
    private const val PRIVATE = "adbkey.private"
    private const val PUBLIC = "adbkey.public"
    private const val KEY_BITS = 2048
    private const val NAME = " binti@diLink"

    /**
     * SHA-1 DigestInfo header — adbd expects the auth token to be signed
     * as if it were a SHA-1 digest. We prepend this header before the token
     * bytes, then sign with NONEwithRSA (raw RSA, no padding).
     */
    private val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    /**
     * Load the keypair from app-private storage, or generate one on first use.
     * Thread-safe (synchronized).
     */
    @Synchronized
    fun load(context: Context): KeyPair {
        val dir = File(context.noBackupFilesDir, DIR)
        val privateFile = File(dir, PRIVATE)
        val publicFile = File(dir, PUBLIC)

        // Try to load existing keys
        runCatching {
            val factory = KeyFactory.getInstance("RSA")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
            )
        }

        // Generate new keypair on first use
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        dir.mkdirs()
        privateFile.writeBytes(pair.private.encoded)
        publicFile.writeBytes(pair.public.encoded)
        return pair
    }

    /**
     * Sign the ADB auth token. Adbd expects a signature as if the token were
     * a SHA-1 digest, so we prepend the SHA-1 DigestInfo header before signing
     * with raw RSA (NONEwithRSA).
     */
    fun sign(token: ByteArray, key: PrivateKey): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(key)
            update(SHA1_DIGEST_INFO + token)
            sign()
        }

    /**
     * Encode the public key in AOSP's `android_pubkey` wire format.
     *
     * Format: modulus size in words (int32), n0inv = -1/n mod 2^32 (int32),
     * modulus as little-endian bytes, R^2 mod n as little-endian bytes,
     * exponent (int32). Base64-encoded, followed by a name and a NUL byte.
     */
    fun publicKeyMessage(key: PublicKey): ByteArray {
        val rsa = key as RSAPublicKey
        val n = rsa.modulus
        val words = KEY_BITS / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val rr = BigInteger.ONE.shiftLeft(2 * KEY_BITS).mod(n)

        val blob = ByteBuffer
            .allocate(8 + 2 * KEY_BITS / 8 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(words)
                putInt(n0inv.toInt())
                put(littleEndian(n, KEY_BITS / 8))
                put(littleEndian(rr, KEY_BITS / 8))
                putInt(rsa.publicExponent.toInt())
            }.array()

        return (Base64.encodeToString(blob, Base64.NO_WRAP) + NAME + "\u0000").toByteArray(Charsets.UTF_8)
    }

    /**
     * Convert a BigInteger to little-endian byte array of the given size.
     * Drops BigInteger's sign byte if present.
     */
    private fun littleEndian(value: BigInteger, size: Int): ByteArray {
        val bigEndian = value.toByteArray().takeLast(size)
        return ByteArray(size) { index -> bigEndian.getOrElse(bigEndian.size - 1 - index) { 0 } }
    }
}
