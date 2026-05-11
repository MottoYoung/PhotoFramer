package com.photoframer.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageSaver {
    private const val TAG = "ImageSaver"
    private const val SUCCESS_MESSAGE = "已保存到相册"
    private const val FAILURE_MESSAGE = "保存失败"

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
                    val image = File(imagesDir, filename)
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
                Toast.makeText(context, if (result) SUCCESS_MESSAGE else FAILURE_MESSAGE, Toast.LENGTH_SHORT).show()
            }
            result
        }
    }

    suspend fun saveImageFileToGallery(context: Context, sourceFile: File, title: String): Boolean {
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
                    val image = File(imagesDir, filename)
                    fos = java.io.FileOutputStream(image)
                }

                if (fos != null) {
                    FileInputStream(sourceFile).use { input ->
                        fos.use { output ->
                            input.copyTo(output)
                            output.flush()
                            result = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save file failed", e)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (result) SUCCESS_MESSAGE else FAILURE_MESSAGE, Toast.LENGTH_SHORT).show()
            }
            result
        }
    }
}
