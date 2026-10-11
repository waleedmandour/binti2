// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Ported from DiPlay (https://github.com/shihabal3amri/DiPlay) with gratitude.
// The ADB protocol implementation is a pure-Java port of AOSP's adb/protocol.txt.

package com.binti.dilink.dilink.adb

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One ADB message: a 24-byte little-endian header followed by its payload.
 *
 * Ported from DiPlay's `AdbPacket.kt`. The ADB wire protocol uses a 24-byte
 * header (command, arg0, arg1, payload_length, payload_sum, command_inverse)
 * followed by the payload bytes. The `command_inverse` field is a simple
 * integrity check — it must equal `command.inv()`.
 *
 * @author Dr. Waleed Mandour (ported from DiPlay by shihabal3amri)
 */
internal class AdbPacket(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {

    fun encode(): ByteArray = ByteBuffer
        .allocate(HEADER_BYTES + payload.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(payload.size)
            putInt(payload.sumOf { it.toInt() and 0xff }) // adbd before protocol 0x01000001 checks this sum
            putInt(command.inv())
            put(payload)
        }.array()

    companion object {
        // ADB message commands (4-byte ASCII magic numbers, little-endian)
        const val CNXN = 0x4e584e43  // "CNXN" — connect
        const val AUTH = 0x48545541  // "AUTH" — authenticate
        const val OPEN = 0x4e45504f  // "OPEN" — open stream
        const val OKAY = 0x59414b4f  // "OKAY" — ready / acknowledge
        const val CLSE = 0x45534c43  // "CLSE" — close stream
        const val WRTE = 0x45545257  // "WRTE" — write data
        const val STLS = 0x534c5453  // "STLS" — TLS (Android 11+ wireless debugging, unsupported)

        // ADB protocol constants
        const val VERSION = 0x01000000
        const val MAX_PAYLOAD = 256 * 1024

        // AUTH sub-commands (arg0 field)
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_PUBLIC_KEY = 3

        private const val HEADER_BYTES = 24

        /**
         * Read one ADB packet from the input stream. Blocks until the full
         * header and payload are available.
         */
        fun read(input: InputStream): AdbPacket {
            val header = ByteBuffer.wrap(readFully(input, HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN)
            val command = header.int
            val arg0 = header.int
            val arg1 = header.int
            val length = header.int
            header.int // payload sum — adbd checks this on older protocols
            if (header.int != command.inv() || length !in 0..MAX_PAYLOAD) {
                throw IOException("bad ADB header")
            }
            return AdbPacket(command, arg0, arg1, readFully(input, length))
        }

        private fun readFully(input: InputStream, count: Int): ByteArray {
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(bytes, offset, count - offset)
                if (read < 0) throw EOFException("ADB stream ended")
                offset += read
            }
            return bytes
        }
    }
}
