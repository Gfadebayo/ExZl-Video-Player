package com.exzell.exzlvideoplayer.utils;

import com.exzell.exzlvideoplayer.listeners.OnFileChangedListener;

public class ObserverUtils {

    private static ObserverUtils sUtils;
    private final OnFileChangedListener[] listeners = new OnFileChangedListener[2];

    private ObserverUtils() {
    }

    public static ObserverUtils getInstance() {
        if (sUtils == null) sUtils = new ObserverUtils();
        return sUtils;
    }

    public void setObserverListeners(OnFileChangedListener lis) {
        if (listeners[0] == null) listeners[0] = lis;
        else listeners[1] = lis;
    }

    public OnFileChangedListener[] getListeners() {
        return listeners;
    }
}
