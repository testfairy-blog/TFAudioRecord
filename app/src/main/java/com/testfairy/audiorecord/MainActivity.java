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

	static private String TAG = "TFAudioRecorder";
	static private int REQUEST_AUDIO_PERMISSION_RESULT = 999876;

	static private final int[] RECORDER_SAMPLERATES = new int[] { 11025 };
	static private final int[] RECORDER_CHANNELS = new int[] { AudioFormat.CHANNEL_IN_MONO };
	static private final int[] RECORDER_AUDIO_ENCODINGS = new int[] { AudioFormat.ENCODING_PCM_16BIT };
	static private final int SHORTS_PER_ELEMENT = 2; // 2 bytes in 16bit format, must be in sync with the encoding (above)
	static private final int IN_MEMORY_FILE_SIZE_IN_BYTES = 500 * 1024;

	private AudioRecord recorder = null;
	private Thread recordingThread = null;
	private boolean isRecording = false;

	private TextView audioRecordInfoText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		audioRecordInfoText = findViewById(R.id.audio_record_info_text);
		audioRecordInfoText.setText("Now recording...");
	}

	@Override
	protected void onResume() {
		super.onResume();

		startRecording();
	}

	@Override
	protected void onPause() {
		super.onPause();

		stopRecording();
	}

	int bufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024

	private void startRecording() {
//		recorder = new AudioRecord(
//				MediaRecorder.AudioSource.MIC,
//				RECORDER_SAMPLERATE, RECORDER_CHANNELS,
//				RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement
//		);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
					PackageManager.PERMISSION_GRANTED) {
				// put your code for Version>=Marshmallow
				recorder = findAudioRecord();
			} else {
				if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
					Toast.makeText(this,
							"App required access to audio", Toast.LENGTH_SHORT).show();
				}

				requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO
				}, REQUEST_AUDIO_PERMISSION_RESULT);
			}

		} else {
			recorder = findAudioRecord();
		}

		if (recorder != null) {
			recorder.startRecording();
			isRecording = true;
			recordingThread = new Thread(new Runnable() {
				public void run() {
					pipeAudioDataToMemoryAndFlushPeriodically();
				}
			}, "AudioRecorder Thread");
			recordingThread.start();

			Log.d(TAG, "Started recording.");
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == REQUEST_AUDIO_PERMISSION_RESULT) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startRecording();
			} else {
				Toast.makeText(getApplicationContext(),
						"Application will not have audio on record", Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void stopRecording() {
		// stops the recording activity
		if (recorder != null) {
			isRecording = false;
			recorder.stop();
			recorder.release();
			recorder = null;
			recordingThread = null;

			Log.d(TAG, "Stopped recording.");
		}
	}

	public AudioRecord findAudioRecord() {
		for (int rate : RECORDER_SAMPLERATES) {
			for (int audioFormat : RECORDER_AUDIO_ENCODINGS) {
				for (int channelConfig : RECORDER_CHANNELS) {
					try {
						Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
								+ channelConfig);

						bufferElements2Rec = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

						if (bufferElements2Rec != AudioRecord.ERROR_BAD_VALUE) {
							// check if we can instantiate and have a success
							AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferElements2Rec);

							if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
								return recorder;
							Log.d(TAG, "Failed to init: " + recorder.getState());
						}
					} catch (Exception e) {
						Log.e(TAG, rate + "Exception, keep trying.",e);
					}
				}
			}
		}

		return null;
	}

	private byte[] short2byte(short[] sData) {
		int shortArrsize = sData.length;
		byte[] bytes = new byte[shortArrsize * 2];
		for (int i = 0; i < shortArrsize; i++) {
			bytes[i * 2] = (byte) (sData[i] & 0x00FF);
			bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
			sData[i] = 0;
		}
		return bytes;
	}

	private void pipeAudioDataToMemoryAndFlushPeriodically() {
		// Write the output audio in byte
		short sData[] = new short[SHORTS_PER_ELEMENT];

		ByteArrayOutputStream os = new ByteArrayOutputStream(IN_MEMORY_FILE_SIZE_IN_BYTES);

		while (isRecording) {
			// gets the voice output from microphone to byte format

			recorder.read(sData, 0, SHORTS_PER_ELEMENT);
			Log.d(TAG,"Short wirting to file" + sData.toString());
			try {
				// // writes the data to file from buffer
				// // stores the voice buffer
				byte bData[] = short2byte(sData);
				os.write(bData, 0, bData.length);
			} catch (/*IO*/Exception e) {
				e.printStackTrace();
			}
		}
		try {
			os.close();
		} catch (/*IO*/Exception e) {
			e.printStackTrace();
		}
	}

}
