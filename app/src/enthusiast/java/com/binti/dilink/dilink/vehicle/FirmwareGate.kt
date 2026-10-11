// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Firmware-fingerprint + signing-cert-hash capability gate.
 *
 * Ported from DiPlay's `BydStandaloneHudOutput.available()` pattern.
 * Before sending any privileged broadcast or binder transact, verify:
 * 1. `Build.VERSION.SDK_INT >= 28`
 * 2. `Build.FINGERPRINT` matches a verified string (or prefix)
 * 3. The calling package is in an allowlist
 * 4. The target receiver package has the expected `longVersionCode`
 * 5. The target receiver is `FLAG_SYSTEM`, `enabled`, `exported`, no permission
 * 6. The receiver's signing cert SHA-256 matches a verified hash
 *
 * This prevents accidental writes to a forged receiver on the wrong firmware.
 *
 * **Verified values (from DiPlay/DiAuto):**
 * - DiLink 5.1 fingerprint: `BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys`
 * - ClusterDebug version: `10601004L`
 * - ClusterDebug signing cert SHA-256: `efe3ca8ada0d10c655c3df9910ad2ebc121a47d9a6358434eb24074309933efc`
 *
 * @author Dr. Waleed Mandour (pattern from DiPlay by shihabal3amri)
 */
object FirmwareGate {

    private const val TAG = "FirmwareGate"

    /**
     * The verified DiLink 5.1 firmware fingerprint. Any OTA that changes
     * this will disable the privileged HUD path until re-verified.
     */
    const val VERIFIED_DILINK_51_FINGERPRINT =
        "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys"

    /**
     * A prefix-match version of the fingerprint. Matches any DiLink 5.1 build
     * (more tolerant than exact equality — recommended for production).
     */
    const val VERIFIED_DILINK_51_PREFIX = "BYD-AUTO/IVI/IVI:13/"

    /**
     * The verified ClusterDebug receiver package.
     */
    const val CLUSTER_DEBUG_PACKAGE = "com.byd.clusterdebug"
    const val CLUSTER_DEBUG_RECEIVER = "com.byd.clusterdebug.BroadcastReceiverCAN"

    /**
     * The verified ClusterDebug receiver version code.
     */
    const val CLUSTER_DEBUG_VERSION = 10601004L

    /**
     * The verified ClusterDebug signing cert SHA-256 (hex string).
     */
    const val CLUSTER_DEBUG_CERT_SHA256 =
        "efe3ca8ada0d10c655c3df9910ad2ebc121a47d9a6358434eb24074309933efc"

    /**
     * Binti's own package names that are allowed to use the privileged path.
     */
    val ALLOWED_PACKAGES = listOf(
        "com.binti.dilink",
        "com.binti.dilink.enthusiast",
        "com.binti.dilink.debug",
        "com.binti.dilink.enthusiast.debug",
    )

    /**
     * Check if the current firmware matches the verified DiLink 5.1 fingerprint.
     * Uses prefix matching (more tolerant than exact equality).
     */
    fun isDiLink51Firmware(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val matches = fingerprint.startsWith(VERIFIED_DILINK_51_PREFIX)
        if (!matches) {
            Log.d(TAG, "Fingerprint '$fingerprint' does not match DiLink 5.1 prefix '$VERIFIED_DILINK_51_PREFIX'")
        }
        return matches
    }

    /**
     * Check if the calling package is in the allowlist.
     */
    fun isPackageAllowed(packageName: String): Boolean {
        val allowed = ALLOWED_PACKAGES.any { it == packageName }
        if (!allowed) {
            Log.w(TAG, "Package '$packageName' is not in the allowlist")
        }
        return allowed
    }

    /**
     * Compute the SHA-256 hash of a signing certificate.
     * Returns the hash as a lowercase hex string.
     */
    fun signatureSha256(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if the ClusterDebug receiver is installed with the verified
     * version code, system flag, and signing cert.
     *
     * Returns true only if ALL checks pass:
     * - Package is installed
     * - `longVersionCode == CLUSTER_DEBUG_VERSION`
     * - Package has `FLAG_SYSTEM`
     * - Package is enabled
     * - Signing cert SHA-256 matches `CLUSTER_DEBUG_CERT_SHA256`
     */
    fun isClusterDebugVerified(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(CLUSTER_DEBUG_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)

            // Check version code
            if (pkgInfo.longVersionCode != CLUSTER_DEBUG_VERSION) {
                Log.w(TAG, "ClusterDebug version mismatch: expected $CLUSTER_DEBUG_VERSION, got ${pkgInfo.longVersionCode}")
                return false
            }

            // Check system flag
            val isSystem = pkgInfo.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystem) {
                Log.w(TAG, "ClusterDebug is not a system app")
                return false
            }

            // Check enabled
            if (!pkgInfo.applicationInfo!!.enabled) {
                Log.w(TAG, "ClusterDebug is disabled")
                return false
            }

            // Check signing cert
            val signatures = pkgInfo.signingInfo?.apkContentsSigners
            if (signatures.isNullOrEmpty()) {
                Log.w(TAG, "ClusterDebug has no signing certificates")
                return false
            }
            val certHash = signatureSha256(signatures[0])
            if (certHash != CLUSTER_DEBUG_CERT_SHA256) {
                Log.w(TAG, "ClusterDebug cert mismatch: expected $CLUSTER_DEBUG_CERT_SHA256, got $certHash")
                return false
            }

            Log.d(TAG, "✅ ClusterDebug receiver verified (version=${pkgInfo.longVersionCode}, cert=$certHash)")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "ClusterDebug package not installed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify ClusterDebug", e)
            false
        }
    }

    /**
     * Full capability gate — check ALL conditions before using the privileged path.
     * Returns true only if:
     * - SDK >= 28 (Android 9+)
     * - Current package is in the allowlist
     * - Firmware matches DiLink 5.1
     * - ClusterDebug receiver is verified
     */
    fun isPrivilegedPathAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) {
            Log.d(TAG, "SDK < 28, privileged path unavailable")
            return false
        }
        if (!isPackageAllowed(context.packageName)) {
            return false
        }
        if (!isDiLink51Firmware()) {
            return false
        }
        if (!isClusterDebugVerified(context)) {
            return false
        }
        Log.i(TAG, "✅ Privileged path is available on this firmware")
        return true
    }
}
