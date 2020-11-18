package com.exzell.exzlvideoplayer.utils

import android.annotation.SuppressLint

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.io.File
import java.util.concurrent.ExecutorService


    @SuppressLint("DefaultLocale")
    fun sizeInMb(size: Long, exe: ExecutorService): String? {
        var siz: String? = null

        try {
            siz = exe.submit(Callable {
                val integer = size.toFloat()
                //size in kb
                val mbSize = integer / 1000

                if (mbSize < 1024)
                    String.format("%3dKB", mbSize.toInt())
                else if (mbSize >= 1024 && mbSize < 1000 * 1000)
                    String.format("%.2fMB", mbSize / 1024)
                else
                    String.format("%.2fGB", mbSize / (1024 * 1024))
            }).get()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        return siz
    }

    fun updateThumbnailPath(oldPath: String, newName: String): String{
        val out = File(oldPath)
        val inn = File(out.parent, newName)

        out.renameTo(inn)

        return out.path
    }
