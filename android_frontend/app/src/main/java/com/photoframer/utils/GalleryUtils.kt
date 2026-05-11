package com.photoframer.utils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size

object GalleryUtils {

    fun canOpenGallery(context: Context): Boolean {
        return buildGalleryIntents(context).any { intent ->
            intent.resolveActivity(context.packageManager) != null
        }
    }

    fun openGallery(context: Context): Boolean {
        val intent = buildGalleryIntents(context).firstOrNull { candidate ->
            candidate.resolveActivity(context.packageManager) != null
        } ?: return false

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun loadLatestThumbnail(
        context: Context,
        targetSizePx: Int = 220
    ): Bitmap? {
        val latestUri = getLatestPhotoUri(context) ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    latestUri,
                    Size(targetSizePx, targetSizePx),
                    null
                )
            } else {
                context.contentResolver.openInputStream(latestUri)?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
    }

    fun createGalleryIntent(context: Context): Intent {
        return buildGalleryIntents(context).first()
    }

    private fun buildGalleryIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val latestPhotoUri = getLatestPhotoUri(context)

        if (latestPhotoUri != null) {
            intents += Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(latestPhotoUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        intents += Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        intents += Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        intents += Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_GALLERY)
        }

        return intents
    }

    private fun getLatestPhotoUri(context: Context): Uri? {
        val latestAppPhoto = queryLatestPhotoUri(
            context = context,
            onlyPhotoFramerFolder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
        if (latestAppPhoto != null) {
            return latestAppPhoto
        }

        return queryLatestPhotoUri(
            context = context,
            onlyPhotoFramerFolder = false
        )
    }

    private fun queryLatestPhotoUri(
        context: Context,
        onlyPhotoFramerFolder: Boolean
    ): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val relativePath = "${Environment.DIRECTORY_PICTURES}/PhotoFramer/"

        val selection: String?
        val selectionArgs: Array<String>?
        if (onlyPhotoFramerFolder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(relativePath)
        } else {
            selection = null
            selectionArgs = null
        }

        val cursor = runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        }.getOrNull() ?: return null

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
    }
}
