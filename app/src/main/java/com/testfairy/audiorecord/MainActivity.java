package com.testfairy.audiorecord;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

	private TextView audioRecordInfoText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		audioRecordInfoText = findViewById(R.id.audio_record_info_text);
		audioRecordInfoText.setText("Now recording...");
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		TestFairyAudioRecord.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
