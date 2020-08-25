package com.exzell.exzlvideoplayer.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.fragments.DownloadActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogUtils {

    public static void startDialog(Context co, DialogInterface.OnShowListener onShow) {

        AlertDialog dia = new MaterialAlertDialogBuilder(co)
                .setCancelable(false)
                .create();

        dia.setOnShowListener(onShow);
        dia.show();
        dia.setContentView(R.layout.thumbnail_dialog);


    }

    public static void downloadLinkDialog(final Context co, final View v) {

        final AlertDialog link = new MaterialAlertDialogBuilder(co)
                .setTitle("Link")
                .setView(v)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String link = ((EditText) v).getText().toString();

                        Intent in = new Intent(co, DownloadActivity.class);
                        in.putExtra("Download", link);
                        co.startActivity(in);
                    }
                })
                .create();

        link.show();
    }
}