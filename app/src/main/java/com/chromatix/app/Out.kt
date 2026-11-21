package com.chromatix.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Salva os exports na pasta pública Documents/Chromatix. */
object Out {
    const val DIR = "Documents/Chromatix"

    fun save(c: Context, name: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
            }
            val u = c.contentResolver.insert(MediaStore.Files.getContentUri("external"), v)
                ?: throw IllegalStateException("não consegui criar o arquivo")
            c.contentResolver.openOutputStream(u)!!.use { it.write(bytes) }
        } else {
            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Chromatix")
            d.mkdirs()
            File(d, name).writeBytes(bytes)
        }
        return "$DIR/$name"
    }
}
