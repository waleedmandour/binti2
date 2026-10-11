// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Ported from DiPlay (https://github.com/shihabal3amri/DiPlay) with gratitude.

package com.binti.dilink.dilink.adb

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * A lazily-opened ADB shell on the head unit for background reads.
 *
 * Ported from DiPlay's `BydAdbShell.kt`. Never asks for approval, so the
 * car's "Allow debugging?" dialog cannot appear while driving. A refused
 * or missing adbd is retried every 30 seconds.
 *
 * **Usage:**
 * ```kotlin
 * val shell = BydAdbShell("Binti-Battery")
 * val output = shell.run(context, "service call autoservice 7 i32 1014 i32 1246777400")
 * // output = "Result: Parcel(00000000 00000002 '........')" or null
 * ```
 *
 * @author Dr. Waleed Mandour (ported from DiPlay by shihabal3amri)
 */
internal class BydAdbShell(private val tag: String) {

    private var adb: LocalAdb? = null
    private var retryAtMillis = 0L
    private var unavailableLogged = false

    /** The result of the latest connection attempt; READY with a null run means the link failed. */
    @Volatile
    var lastAccess: LocalAdb.Access? = null
        private set

    /**
     * Run a shell command and return its output. Returns null if ADB is
     * unavailable or the command failed. Retries are throttled to once
     * per 30 seconds after a failure.
     */
    @Synchronized
    fun run(context: Context, command: String): String? = run(context, command, null)

    /** Same as [run], with a larger bounded read window for a slow command. */
    @Synchronized
    fun run(context: Context, command: String, readTimeoutMillis: Int?): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < retryAtMillis) return null

        val client = adb ?: LocalAdb(AdbKeys.load(context)).also { adb = it }
        val access = client.connect(mayAsk = false)
        lastAccess = access

        if (access != LocalAdb.Access.READY) {
            retryAtMillis = now + RETRY_MILLIS
            if (!unavailableLogged) {
                unavailableLogged = true
                Log.w(tag, "ADB access $access — retrying in ${RETRY_MILLIS / 1000}s")
            }
            return null
        }

        unavailableLogged = false
        return if (readTimeoutMillis == null) {
            client.shell(command)
        } else {
            client.shell(command, readTimeoutMillis)
        }
    }

    @Synchronized
    fun close() {
        adb?.close()
        adb = null
    }

    private companion object {
        const val RETRY_MILLIS = 30_000L
    }
}

/**
 * Parses `service call` replies such as `Result: Parcel(00000000 00000002 '........')`.
 *
 * The first hex word is the exception code (0 = success). The second word
 * is the return value (for GET_INT transactions) or the start of a string
 * reply (for other transactions).
 *
 * Ported from DiPlay's `BydParcel` object.
 */
internal object BydParcel {

    /** The value after a zero exception code, as its 32 raw bits. */
    fun value(output: String?): Int? {
        val words = words(output)
        if (words.size < 2 || words[0] != 0L) return null
        return words[1].toInt()
    }

    /** All hex words in the Parcel body, as Longs. */
    fun words(output: String?): List<Long> {
        val body = output
            ?.substringAfter("Parcel(", "")
            ?.substringBefore('\'')
            ?: return emptyList()
        return body.trim().split(Regex("\\s+")).mapNotNull { word ->
            word.takeIf { it.length == 8 }?.toLongOrNull(16)
        }
    }
}
