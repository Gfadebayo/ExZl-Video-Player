package com.exzell.exzlvideoplayer

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.exzell.exzlvideoplayer.utils.MediaUtils
import com.exzell.exzlvideoplayer.utils.thumbnailPath
import com.shawnlin.numberpicker.NumberPicker
import java.util.stream.IntStream

class PopupDialogFragment : DialogFragment() {

    val mAction: Int by lazy { arguments!!.getInt("action") }
    val mMediaFile: MediaFile by lazy { arguments!!.getParcelable<MediaFile>("media")!! }
    lateinit var onthumbChange: OnThumbnailChangeListener

    fun setThumbnailListener(list: OnThumbnailChangeListener) {
        onthumbChange = list
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return when (mAction) {
            ACTION_RENAME -> createRenameDialog()
            ACTION_DELETE -> createDeleteDialog()
            else -> createThumbnailDialog()
        }
    }

    private fun createRenameDialog() =
            MaterialDialog(requireContext())
                    .cornerRadius(8f)
                    .title(R.string.new_name)
                    .negativeButton(R.string.cancel)
                    .positiveButton(R.string.rename)
                    .show {

                        //Calling input transforms it into a customView dialog the we can pply the required functions
                        input(waitForPositiveButton = true, prefill = mMediaFile.displayName) { dialog, seq ->
                            val done = MediaUtils.renameFile(requireContext(), mMediaFile, seq.toString())

                            Toast.makeText(requireContext(), if (done) R.string.rename_done else R.string.rename_fail, Toast.LENGTH_SHORT).show()
                        }.apply {
                            val inp = getInputField()

                            inp.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                                if (hasFocus) {
                                    val extStart = mMediaFile.displayName.lastIndexOf(".")
                                    inp.setSelection(0, extStart)
                                }
                            }
                        }
                    }

    private fun createDeleteDialog() = MaterialDialog(requireContext())
            .title(R.string.delete)
            .message(text = "Are you sure you want to delete this File \nThis operation cannot be undone")
            .negativeButton(R.string.cancel)
            .positiveButton(R.string.delete) {
                MediaUtils.deleteFile(requireContext(), mMediaFile)
            }

    private fun createThumbnailDialog(): MaterialDialog {
        return MaterialDialog(requireContext())
                .title(text = "Enter the time")
                .negativeButton(res = R.string.cancel)
                .customView(view = thumbnailPickers())
                .positiveButton(text = "Change") {
                    it.getCustomView().performClick()
                }
    }

    fun thumbnailPickers(): View =
            with(layoutInflater.inflate(R.layout.thumbnail_pickers, null, false) as LinearLayout) {

                var index = 0
                val times = MediaUtils.microUsToTime(mMediaFile.duration)
                times.forEach {

                    val pick: NumberPicker = this.getChildAt(index * 2) as NumberPicker

                    if (it == 0 && index != 2) {
                        pick.visibility = View.GONE
                        getChildAt((index * 2) + 1).visibility = View.GONE
                    } else pick.maxValue = if (index != 0 && times[index - 1] != 0) 59 else it


//                    if(index != 2)
//                    pick.setOnValueChangedListener{ picker, oldVal, newVal ->
//                        val pos = this.indexOfChild(pick)+2
//
//                        with(getChildAt(pos) as NumberPicker){
//                            maxValue = if(newVal != it) 59 else times[index+1]
//                        }
//
//                        //Picker is for hours so we do this twice for seconds
//                        if(index == 0)
//                            with(getChildAt(pos+2) as NumberPicker){
//                                maxValue = if(newVal != it) 59 else times[index+2]
//                            }
//                    }

                    index++
                }



                this.setOnClickListener {
                    val build = StringBuilder()
                    (it as LinearLayout).run {
                        IntStream.of(0, 2, 4).boxed()
                                .map { this.getChildAt(it) as NumberPicker }
                                .forEach {
                                    if (it.visibility == View.GONE) Unit

                                    build.append(it.value)
                                    build.append(':')
                                }
                    }


                    if (MediaUtils.timeInUs(build.toString()) > mMediaFile.duration) Toast.makeText(requireContext(), "Invalid Time", Toast.LENGTH_SHORT).show()
                    else MediaUtils.loadThumbIntoCache(requireContext().thumbnailPath(), mMediaFile.path, build.toString())
                    onthumbChange.onThumbnailChanged()
                }

                return this
            }

    companion object {

        const val ACTION_THUMBNAIL = 0
        const val ACTION_RENAME = 1
        const val ACTION_DELETE = 2

        @JvmStatic
        fun getInstance(action: Int, reference: MediaFile): PopupDialogFragment {
            val diag = PopupDialogFragment()

            val bund = Bundle(2)
            bund.putInt("action", action)
            bund.putParcelable("media", reference)

            diag.arguments = bund

            return diag
        }
    }

    interface OnThumbnailChangeListener {
        fun onThumbnailChanged()
    }
}