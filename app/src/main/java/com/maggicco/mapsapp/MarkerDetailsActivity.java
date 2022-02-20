package com.maggicco.mapsapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class MarkerDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marker_details);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }
}