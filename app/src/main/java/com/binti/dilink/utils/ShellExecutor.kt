package com.binti.dilink.utils

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Shell Command Executor
 *
 * Executes commands via system shell.
 * Used as a fallback/alternative to Accessibility Services for BYD DiLink.
 *
 * Note: Requires ADB enabled on the device or root access for certain commands.
 * On BYD DiLink, some system commands can be triggered via 'am' (Activity Manager)
 * even without root if the app is granted specific permissions.
 *
 * @author Dr. Waleed Mandour
 */
object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Execute a single shell command
     */
    fun execute(command: String): ShellResult {
        Log.d(TAG, "Executing: $command")
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val error = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            ShellResult(exitCode == 0, output.toString(), error.toString(), exitCode)
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed: ${e.message}")
            ShellResult(false, "", e.message ?: "Unknown error", -1)
        }
    }

    /**
     * Execute multiple commands as root (if available)
     */
    fun executeAsRoot(commands: List<String>): ShellResult {
        Log.d(TAG, "Executing ${commands.size} commands as root")
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            
            val exitCode = process.waitFor()
            ShellResult(exitCode == 0, "", "", exitCode)
        } catch (e: Exception) {
            Log.w(TAG, "Root execution failed (su not found?): ${e.message}")
            // Fallback to normal execution for each
            var allSuccess = true
            val outputs = StringBuilder()
            for (cmd in commands) {
                val res = execute(cmd)
                if (!res.success) allSuccess = false
                outputs.append(res.output)
            }
            ShellResult(allSuccess, outputs.toString(), "", if (allSuccess) 0 else -1)
        }
    }

    /**
     * DiLink Specific: Send a tap gesture via ADB
     */
    fun tap(x: Int, y: Int) {
        execute("input tap $x $y")
    }

    /**
     * DiLink Specific: Send a key event
     */
    fun keyevent(code: Int) {
        execute("input keyevent $code")
    }

    /**
     * DiLink Specific: Start an activity via AM
     */
    fun startActivity(packageName: String, activityName: String) {
        execute("am start -n $packageName/$activityName")
    }

    /**
     * DiLink Specific: Send a broadcast
     */
    fun sendBroadcast(action: String, extras: Map<String, String> = emptyMap()) {
        val sb = StringBuilder("am broadcast -a $action")
        for ((key, value) in extras) {
            sb.append(" --es $key \"$value\"")
        }
        execute(sb.toString())
    }
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
