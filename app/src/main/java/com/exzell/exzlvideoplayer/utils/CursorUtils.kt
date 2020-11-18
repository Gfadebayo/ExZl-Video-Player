package com.exzell.exzlvideoplayer.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

import kotlin.collections.ArrayList

fun Context.queryMediaStore(queriedUri: Uri, selection: String?, selectionArgs: Array<String>?, sortOrder: String?, vararg columnsToBeSelected: String): ArrayList<Bundle>? {
        val resultBundle = arrayListOf<Bundle>()

            val query = getContentResolver().query(queriedUri, columnsToBeSelected, selection, selectionArgs, sortOrder)

            if (query == null) return null

            while (query.moveToNext()) {
                val bund = Bundle(columnsToBeSelected.size)

                for (i in columnsToBeSelected.indices) {
                    //since the column type may not be known
                    //it is better to get it ourselves
                    when (query.getType(i)) {
                        Cursor.FIELD_TYPE_STRING -> bund.putString(columnsToBeSelected[i], query.getString(i))
                        Cursor.FIELD_TYPE_INTEGER -> bund.putInt(columnsToBeSelected[i], query.getInt(i))
                        else -> bund.putFloat(columnsToBeSelected[i], query.getFloat(i))
                    }
                }

                resultBundle.add(bund)
            }

            query.close()

        return resultBundle
    }
