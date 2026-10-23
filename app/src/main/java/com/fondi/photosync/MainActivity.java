package com.fondi.photosync;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText("Fondi Photo Sync\n\nPhase 0 baseline build.");
        textView.setTextSize(18f);
        textView.setPadding(48, 48, 48, 48);

        setContentView(textView);
    }
}
