package com.exzell.exzlvideoplayer;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textview.MaterialTextView;

public class SearchActivity extends AppCompatActivity {

    public static final String RECEIVER = "res_receiver";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Intent intent = getIntent();

        String stringExtra = intent.getStringExtra(SearchManager.QUERY);

        Toast.makeText(this, "Search Requested", Toast.LENGTH_SHORT).show();

    }
}
