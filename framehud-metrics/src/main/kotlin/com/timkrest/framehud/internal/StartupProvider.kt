package com.timkrest.framehud.internal

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.timkrest.framehud.InternalFrameHudApi

@InternalFrameHudApi
public abstract class StartupProvider : ContentProvider() {

    protected abstract fun onStartup(application: Application)

    final override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        if (isMainProcess(application)) onStartup(application)
        return true
    }

    final override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    final override fun getType(uri: Uri): String? = null

    final override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    final override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
