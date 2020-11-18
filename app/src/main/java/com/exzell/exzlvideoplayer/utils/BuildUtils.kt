package com.exzell.exzlvideoplayer.utils

import android.content.Context
import com.exzell.exzlvideoplayer.BuildConfig.*
import java.io.File


fun Context.thumbnailPath(): File{
    if(DEBUG) return this.getExternalFilesDir(Constants.THUMBAIL_PATH)!!
    else return this.getDir(Constants.THUMBAIL_PATH, Context.MODE_PRIVATE)
}

object Constants{
    val THUMBAIL_PATH = "thumbnails"
}