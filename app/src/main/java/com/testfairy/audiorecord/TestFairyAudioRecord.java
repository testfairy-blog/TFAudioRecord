package com.testfairy.audiorecord;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

public class TestFairyAudioRecord {

	/***************** SDK Constants *****************/

	static private String TAG = "TFAudioRecorder";
	static private int REQUEST_AUDIO_PERMISSION_RESULT = 999876;


	/***************** Audio File Spec *****************/

	static private final int[] RECORDER_SAMPLERATES = new int[] { 11025 };
	static private final int[] RECORDER_CHANNELS = new int[] { AudioFormat.CHANNEL_IN_MONO };
	static private final int[] RECORDER_AUDIO_ENCODINGS = new int[] { AudioFormat.ENCODING_PCM_16BIT };
	static private final int SHORTS_PER_ELEMENT = 2; // 2 bytes in 16bit format, must be in sync with the encoding (above)
	static private final int IN_MEMORY_FILE_SIZE_IN_BYTES = 500 * 1024;


	/***************** Recorder State *****************/

	WeakReference<Activity> activityWeakReference = new WeakReference<>(null);
	private AudioRecord recorder = null;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private int bufferElements2Rec = -2;


	/***************** Singleton State *****************/

	static private TestFairyAudioRecord instance;


	/***************** Public Interface *****************/

	static public void onCreate(Activity activity) {
		if (instance != null) {
			synchronized (instance) {
				instance = new TestFairyAudioRecord(activity);
			}
		} else {
			instance = new TestFairyAudioRecord(activity);
		}
	}

	static public void onResume() {
		if (instance != null) {
			synchronized (instance) {
				instance.startRecording();
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}

	static public void onPause() {
		if (instance != null) {
			synchronized (instance) {
				instance.stopRecording();
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}

	static public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (instance != null) {
			synchronized (instance) {
				instance.retryWithRecievedPermissions(requestCode, permissions, grantResults);
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}


	/***************** Implementation *****************/

	private TestFairyAudioRecord(Activity activity) {
		activityWeakReference = new WeakReference<>(activity);
	}

	private void startRecording() {
		if (activityWeakReference.get() != null) {
			synchronized (activityWeakReference.get()) {
				Activity activity = activityWeakReference.get();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
							PackageManager.PERMISSION_GRANTED) {
						// put your code for Version>=Marshmallow
						recorder = findAndCreateAudioRecordFromSpecs();
					} else {
						if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
							Toast.makeText(
									activity,
									"App required access to audio",
									Toast.LENGTH_SHORT
							).show();
						}

						activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO
						}, REQUEST_AUDIO_PERMISSION_RESULT);
					}

				} else {
					recorder = findAndCreateAudioRecordFromSpecs();
				}

				if (recorder != null) {
					recorder.startRecording();
					isRecording = true;
					recordingThread = new Thread(new Runnable() {
						public void run() {
							pipeAudioDataToMemoryAndFlushPeriodicallyInBackgroundThread();
						}
					}, "TFAudioRecorder Thread");
					recordingThread.start();

					Log.d(TAG, "Started recording.");
				}
			}
		}
	}

	private void retryWithRecievedPermissions(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (activityWeakReference.get() != null) {
			synchronized (activityWeakReference.get()) {
				Activity activity = activityWeakReference.get();

				if (requestCode == REQUEST_AUDIO_PERMISSION_RESULT) {
					if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
						startRecording();
					} else {
						Toast.makeText(
								activity,
								"Application will not have audio on record",
								Toast.LENGTH_SHORT
						).show();
					}
				}
			}
		}
	}

	private void stopRecording() {
		// stops the recording activity
		if (activityWeakReference.get() != null) {
			synchronized (activityWeakReference.get()) {
				if (recorder != null) {
					isRecording = false;
					recorder.stop();
					recorder.release();
					recorder = null;
					recordingThread = null;

					Log.d(TAG, "Stopped recording.");
				}
			}
		} else {
			if (recorder != null) {
				isRecording = false;
				recorder.stop();
				recorder.release();
				recorder = null;
				recordingThread = null;

				Log.d(TAG, "Stopped recording.");
			}
		}
	}

	private AudioRecord findAndCreateAudioRecordFromSpecs() {
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
					} catch (Throwable e) {
						Log.e(TAG, rate + "Exception, keep trying.",e);
					}
				}
			}
		}

		return null;
	}

	private void pipeAudioDataToMemoryAndFlushPeriodicallyInBackgroundThread() {
		// Write the output audio in byte
		short sData[] = new short[SHORTS_PER_ELEMENT];

		ByteArrayOutputStream os = new ByteArrayOutputStream(IN_MEMORY_FILE_SIZE_IN_BYTES);

		while (isRecording) {
			// gets the voice output from microphone to byte format
			if (activityWeakReference.get() != null) {
				synchronized (activityWeakReference.get()) {
					recorder.read(sData, 0, SHORTS_PER_ELEMENT);
					Log.d(TAG, "Short wirting to file" + sData.toString());
					try {
						// // writes the data to file from buffer
						// // stores the voice buffer
						byte bData[] = short2byte(sData);
						os.write(bData, 0, bData.length);
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			} else {
				stopRecording();
			}
		}
		try {
			os.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}


	/***************** Utilities *****************/

	static private byte[] short2byte(short[] sData) {
		int shortArrsize = sData.length;
		byte[] bytes = new byte[shortArrsize * 2];
		for (int i = 0; i < shortArrsize; i++) {
			bytes[i * 2] = (byte) (sData[i] & 0x00FF);
			bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
			sData[i] = 0;
		}
		return bytes;
	}


	/***************** Exceptions *****************/

	static class AudioRecorderNotInitializedException extends RuntimeException {
		public AudioRecorderNotInitializedException() {
			super("Audio recorder cannot perform lifecycle. Did you forget to call TestFairyAudioRecord.onCreate() ?");
		}
	}


}