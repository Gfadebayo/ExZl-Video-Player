package com.exzell.exzlvideoplayer.listeners;

import java.util.List;

public interface OnFileChangedListener {

    void onFileChanged(List<String> changedFile, boolean validPath);
}
