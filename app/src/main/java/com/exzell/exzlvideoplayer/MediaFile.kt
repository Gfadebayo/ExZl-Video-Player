package com.exzell.exzlvideoplayer

import android.os.Parcel
import android.os.Parcelable

import java.io.File

data class MediaFile(var path: String,
                var size: Long = 0,
                val dateAdded: Int = 0,
                var id: Int = 0,
                val type: Type = Type.DIRECTORY) : Parcelable {

    var displayName: String

    var thumbnailPath: String? = null

    var isSelected = false

    @Deprecated(level = DeprecationLevel.WARNING, message = "Use the File class to determine instead")
    val isDir: Boolean = false

    var duration: Long = 0

    var position: Long = 0

    var parentPath: String

    constructor(path: String): this(path, 0)

    init {
        displayName = File(path).name
        parentPath = File(path).parent!!
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MediaFile) return false

        val challenger = other as MediaFile?

        return path == challenger!!.path && displayName == challenger.displayName
    }

    override fun hashCode() = path.chars().sum()

    override fun toString() = displayName + " located at " + this.path

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        with(dest!!){
            writeString(path)
            writeLong(size)
            writeInt(dateAdded)
            writeInt(id)
            writeString(type.name)
            writeString(displayName)
            writeString(thumbnailPath)
            writeString(isSelected.toString())
            writeLong(duration)
            writeString(parentPath)
        }
    }

    override fun describeContents() = 0

    companion object CREATOR: Parcelable.Creator<MediaFile> {
        override fun createFromParcel(source: Parcel?): MediaFile {
            with(source!!){
                return MediaFile(readString()!!, readLong(), readInt(), readInt(), Type.valueOf(readString()!!)).apply {
                    displayName = readString()!!
                    thumbnailPath = readString()!!
                    isSelected = readString()?.toBoolean()!!
                    duration = readLong()
                    parentPath = readString()!!
                }
            }
        }

        override fun newArray(size: Int): Array<MediaFile> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    enum class Sort {
        NAME,
        SIZE,
        DATE,
        DURATION
    }

    enum class Type{
        AUDIO,
        VIDEO,
        AUDIOVIDEO,
        DIRECTORY
    }

}
