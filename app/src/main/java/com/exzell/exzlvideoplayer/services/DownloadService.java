package com.exzell.exzlvideoplayer.services;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.SearchActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class DownloadService extends IntentService {

    public static final String DOWNLOAD = "download";
    public static final String UPDATE = "update";

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public DownloadService(String name) {
        super(name);
    }

    public DownloadService() {
        super("Down");
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        String link = intent.getStringExtra(DOWNLOAD);
        ResultReceiver rec = intent.getParcelableExtra(SearchActivity.RECEIVER);

        InputStream st = null;
        FileOutputStream fileOutputStream = null;
        HttpURLConnection http = null;

        try {
            URL ur = new URL(link);
            String extForm = ur.toExternalForm();
            URI uri = ur.toURI();

            http = (HttpURLConnection) ur.openConnection();
            http.setDoInput(true);


            long downloadLength = http.getContentLengthLong();
            String namae = ur.getFile().split("/")[2];

            Bundle name = new Bundle(1);
            name.putString("name", namae);
            name.putLong("size", downloadLength);
            rec.send(1, name);


            http.connect();
            st = new BufferedInputStream(http.getInputStream());
            byte[] bit = new byte[1024];
            File newFile = new File(getExternalFilesDir("downloads"), namae);
            if (!newFile.exists()) {
//                newFile.mkdirs();
                newFile.createNewFile();
            }


            Toast.makeText(DownloadService.this, newFile.getPath(), Toast.LENGTH_SHORT).show();
            fileOutputStream = new FileOutputStream(newFile);
            int count;
            long total = 0;

            while ((count = st.read(bit)) != -1) {
                total += count;
                Bundle result = new Bundle(1);

                result.putFloat(UPDATE, ((float) total / (float) downloadLength));

                rec.send(0, result);

                fileOutputStream.write(bit, 0, count);
            }

            if(total >= downloadLength){
                Bundle b = new Bundle(1);
                b.putString("file_path", newFile.getPath());
                rec.send(3, b);
            }
        } catch (IOException | URISyntaxException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                st.close();
                fileOutputStream.flush();
                fileOutputStream.close();
                http.disconnect();


            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

        }
    }
}
