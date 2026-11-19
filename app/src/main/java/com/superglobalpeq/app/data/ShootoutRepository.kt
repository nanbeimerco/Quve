package com.superglobalpeq.app.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.superglobalpeq.app.model.ShootoutSessionRecord
import org.json.JSONObject
import java.io.File

/**
 * Stores each session in its own file, away from the preset preferences, so a growing vote log
 * never slows down ordinary preset saves. Call from a background thread.
 */
class ShootoutRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    fun loadAll(): List<ShootoutSessionRecord> {
        val files = directory.listFiles { file -> file.name.endsWith(FILE_SUFFIX) } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val text = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                ShootoutSessionRecord.fromJson(JSONObject(text))
            } catch (t: Throwable) {
                Log.w(TAG, "Skipping unreadable Shootout session ${file.name}", t)
                null
            }
        }.sortedByDescending { it.createdAtEpochMs }
    }

    fun save(session: ShootoutSessionRecord) {
        directory.mkdirs()
        val file = AtomicFile(fileFor(session.id))
        val stream = file.startWrite()
        try {
            stream.write(session.toJson().toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (t: Throwable) {
            file.failWrite(stream)
            throw t
        }
    }

    fun delete(sessionId: String) {
        AtomicFile(fileFor(sessionId)).delete()
    }

    private fun fileFor(sessionId: String): File {
        val safeId = sessionId.filter { it.isLetterOrDigit() || it == '-' }
        return File(directory, safeId + FILE_SUFFIX)
    }

    private companion object {
        const val TAG = "ShootoutRepository"
        const val DIRECTORY_NAME = "shootout"
        const val FILE_SUFFIX = ".json"
    }
}
