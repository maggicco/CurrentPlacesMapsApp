package com.maggicco.mapsapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import java.io.Serializable;
import butterknife.BindView;
import butterknife.ButterKnife;


public class MarkerDetailsActivity extends AppCompatActivity implements Serializable {

    @BindView(R.id.titleDetail)
    TextView titleD;
    @BindView(R.id.snippetDetail)
    TextView snippetD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marker_details);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ButterKnife.bind(this);

        //For retrieve data when activity is open
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        //String sessionId = getIntent().getStringExtra("RESTAURANT_NAME");
        String titleDet = getIntent().getStringExtra("RESTAURANT_NAME");
        titleD.setText(titleDet);
        String snippetDet = getIntent().getStringExtra("RESTAURANT_ID");
        snippetD.setText(snippetDet);





    }
}