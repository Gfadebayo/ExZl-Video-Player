package com.exzell.exzlvideoplayer.fragments;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.MimeTypeFilter;

import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.services.DownloadService;
//import com.exzell.exzlvideoplayer.utils.FileUtils;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;

import static com.exzell.exzlvideoplayer.SearchActivity.RECEIVER;

public class DownloadActivity extends AppCompatActivity {
    private MaterialTextView mLength;
    private ProgressBar mBar;
    private MaterialTextView mName;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        mBar = findViewById(R.id.progress_bar);
        mName = findViewById(R.id.text_download);
        mLength = findViewById(R.id.text_download_size);


        Intent downloadIntent = new Intent(this, DownloadService.class);
        downloadIntent.putExtra(DownloadService.DOWNLOAD, getIntent().getStringExtra("Download"));
        downloadIntent.putExtra(RECEIVER, new DownloadReceiver(new Handler()));
        startService(downloadIntent);

    }

    private class DownloadReceiver extends ResultReceiver {

        /**
         * Create a new ResultReceive to receive results.  Your
         * {@link #onReceiveResult} method will be called from the thread running
         * <var>handler</var> if given, or from an arbitrary thread if null.
         *
         * @param handler
         */
        DownloadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {

            if (resultCode == 1) {
                String name = resultData.getString("name");
                long size = resultData.getLong("size");

                mName.setText(name);
//                mLength.setText(FileUtils.INSTANCE.sizeInMb(size));
            } else if(resultCode == 0) {

                float prog = resultData.getFloat(DownloadService.UPDATE);
                Log.i("Down", "Progress called at " + prog);
                mBar.setProgress((int) (prog * mBar.getMax()));
            }else {
                String path = resultData.getString("file_path");
                final Uri file = Uri.fromFile(new File(path));
                String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                final String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);

                Snackbar.make(findViewById(R.id.constraint_download), "Download Completed", Snackbar.LENGTH_LONG).setAction("Open File", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent in = new Intent(Intent.ACTION_VIEW);
                        in.setType(mimeType);

                        startActivity(in);
                    }
                }).show();
            }
        }
    }
}
