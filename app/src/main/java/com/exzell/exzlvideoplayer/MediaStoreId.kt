package com.exzell.exzlvideoplayer

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.stream.Collectors
import java.util.stream.Stream

fun writeToFile(ctx: Context, files: List<MediaFile>, name: String){

    Thread {
        val file = File(ctx.getExternalFilesDir("test"), "$name.txt")

        if(!file.exists()) file.createNewFile()

        BufferedWriter(FileWriter(file)).run {
            write("IDs \t\t ---> \t\t Name")
            newLine()
            files.forEach {
                val id = it.id
                val name = it.displayName

                write("$id \t\t ---> \t\t $name")
                newLine()
            }

            close()
        }
    }.start()
}

const val DEFAULT_VIDEO = "Store Ids before(Video)"
const val AFTER_VIDEO = "Store Ids Change(Video)"

const val AFTER_AUDIO = "Store Ids Change(Audio)"
const val DEFAULT_AUDIO = "Store Ids Before(Audio)"