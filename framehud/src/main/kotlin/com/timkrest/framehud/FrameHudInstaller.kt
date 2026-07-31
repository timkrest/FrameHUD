package com.timkrest.framehud

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.timkrest.framehud.internal.isMainProcess

/**
 * Installs [FrameHud] before the first activity is created. Remove it from the merged manifest to
 * opt out and call [FrameHud.install] yourself.
 *
 * Apps name this class in their manifest, so its package is public contract even though the class
 * is not meant to be called.
 */
internal class FrameHudInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        if (isMainProcess(application)) FrameHud.install(application)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
