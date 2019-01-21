package com.testfairy.audiorecord;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

	private TextView audioRecordInfoText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		audioRecordInfoText = findViewById(R.id.audio_record_info_text);
		audioRecordInfoText.setText("Now recording...");

		TestFairyAudioRecord.onCreate(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		TestFairyAudioRecord.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();

		TestFairyAudioRecord.onPause();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		TestFairyAudioRecord.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
