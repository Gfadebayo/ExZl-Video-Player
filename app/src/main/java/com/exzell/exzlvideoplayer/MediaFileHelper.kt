package com.exzell.exzlvideoplayer

import android.app.Activity
import java.io.File


fun MediaFile.getParentPath(): String? {
    val file = File(path!!)
    return file.parent
}