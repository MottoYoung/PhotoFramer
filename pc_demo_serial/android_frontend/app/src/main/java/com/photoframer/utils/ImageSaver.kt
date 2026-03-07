package com.photoframer.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ImageSaver {
    private const val TAG = "ImageSaver"

    suspend fun saveImageToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        return withContext(Dispatchers.IO) {
            val filename = "${title}_${System.currentTimeMillis()}.jpg"
            var fos: OutputStream? = null
            var result = false

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoFramer")
                    }
                    val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { context.contentResolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val image = java.io.File(imagesDir, filename)
                    fos = java.io.FileOutputStream(image)
                }

                fos?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    result = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if(result) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
            }
            result
        }
    }
}
