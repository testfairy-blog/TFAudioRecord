package com.testfairy.audiorecord;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

	private TextView audioRecordInfoText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		audioRecordInfoText = findViewById(R.id.audio_record_info_text);
		audioRecordInfoText.setText("Now recording...");
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		TestFairyAudioRecord.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
