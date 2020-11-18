package com.exzell.exzlvideoplayer.viewmodels

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner

class MediaViewModelFactory(owner: SavedStateRegistryOwner,
                            val application: Application,
                            val cols: Array<String>,
                            val uri: Uri): AbstractSavedStateViewModelFactory(owner, null) {


    override fun <T : ViewModel?> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return MainViewModel(application, handle, cols, uri) as T

    }
}