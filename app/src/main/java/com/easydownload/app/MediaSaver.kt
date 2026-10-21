package com.easydownload.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Copies a finished download into shared storage so it shows up in the Gallery (videos)
 * or the Music app (songs), inside an "Easy Download" folder. Uses MediaStore, so no
 * storage permission is needed on Android 10+.
 */
object MediaSaver {

    const val FOLDER = "Easy Download"

    private val AUDIO_EXT = setOf("mp3", "m4a", "opus", "ogg", "aac", "wav", "flac")

    data class Saved(val uri: Uri, val displayName: String, val mime: String) {
        val isAudio: Boolean get() = mime.startsWith("audio/")
    }

    fun save(ctx: Context, src: File): Saved {
        val ext = src.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext in AUDIO_EXT) "audio/mpeg" else "video/mp4"
        val isAudio = mime.startsWith("audio/")

        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/" + FOLDER

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Couldn't create the file in storage")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file for writing")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, src.name, mime)
    }
}
