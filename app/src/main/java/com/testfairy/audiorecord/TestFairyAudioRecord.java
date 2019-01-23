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

import com.testfairy.SessionStateListener;
import com.testfairy.TestFairy;
import com.testfairy.modules.audio.AudioSample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;

public class TestFairyAudioRecord {

	/***************** SDK Constants *****************/

	static private String TAG = "TFAudioRecorder";
	static private String THREAD_NAME = "testfairy-audiorecord";
	static private int REQUEST_AUDIO_PERMISSION_RESULT = 999876;


	/***************** Audio File Spec *****************/

	static private final int RECORDER_SAMPLERATE = 11025;
	static private final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	static private final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT ;
	static private final int SHORTS_PER_ELEMENT = 2; // 2 bytes in 16bit format, must be in sync with the encoding (above)
	static private final int IN_MEMORY_FILE_SIZE_IN_BYTES = 500 * 1024;
	static private final int AUDIO_FILE_MAX_DURATION_IN_SECONDS = 15;


	/***************** Recorder State *****************/

	WeakReference<Activity> activityWeakReference;
	private AudioRecord recorder = null;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private int bufferElements2Rec = -2;
	private StopWatch sessionStopwatch = new StopWatch(false);


	/***************** Singleton State *****************/

	static private TestFairyAudioRecord instance;


	/***************** Public Interface *****************/

	static public void onCreate(@NonNull Activity activity) {
		if (instance != null) {
			synchronized (instance) {
				instance = new TestFairyAudioRecord(activity);
			}
		} else {
			instance = new TestFairyAudioRecord(activity);
		}

		Log.d(TAG, "TestFairyAudioRecord initialized.");

		TestFairy.addSessionStateListener(new SessionStateListener() {
			@Override
			public void onSessionStarted(String s) {
				instance.sessionStopwatch.start();
			}
		});
	}

	static public void onResume() {
		Log.d(TAG, "TestFairyAudioRecord is in foreground.");

		if (instance != null) {
			synchronized (instance) {
				instance.startRecording();
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}

	static public void onPause() {
		Log.d(TAG, "TestFairyAudioRecord is in background.");

		if (instance != null) {
			synchronized (instance) {
				instance.stopRecording();
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}

	static public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		Log.d(TAG, "TestFairyAudioRecord received permission results.");

		if (instance != null) {
			synchronized (instance) {
				instance.retryWithRecievedPermissions(requestCode, permissions, grantResults);
			}
		} else {
			throw new AudioRecorderNotInitializedException();
		}
	}


	/***************** Implementation *****************/

	private TestFairyAudioRecord(@NonNull Activity activity) {
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
						if (recorder != null) {
							stopRecording();
						}

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
					if (recorder != null) {
						stopRecording();
					}
					recorder = findAndCreateAudioRecordFromSpecs();
				}

				if (recorder != null) {
					synchronized (recorder) {
						recorder.startRecording();
						isRecording = true;
						recordingThread = new Thread(new Runnable() {
							public void run() {
								pipeAudioDataToMemoryAndFlushPeriodicallyInBackgroundThread();
							}
						}, THREAD_NAME);
						recordingThread.start();

						Log.d(TAG, "Started recording.");
					}
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
						Log.d(TAG, "Audio record permission granted.");
						startRecording();
					} else {
						Log.w(TAG, "Audio record permission is denied.");

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
					synchronized (recorder) {
						isRecording = false;
						recorder.stop();
						recorder.release();
						recorder = null;
						recordingThread = null;

						Log.d(TAG, "Stopped recording.");
					}
				}
			}
		} else {
			if (recorder != null) {
				synchronized (recorder) {
					isRecording = false;
					recorder.stop();
					recorder.release();
					recorder = null;
					recordingThread = null;

					Log.d(TAG, "Stopped recording.");
				}
			}
		}
	}

	private AudioRecord findAndCreateAudioRecordFromSpecs() {
		try {
			Log.d(TAG, "Attempting rate " + RECORDER_SAMPLERATE + "Hz, bits: " + RECORDER_AUDIO_ENCODING + ", channel: "
					+ RECORDER_CHANNELS);

			bufferElements2Rec = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

			if (bufferElements2Rec != AudioRecord.ERROR_BAD_VALUE) {
				// check if we can instantiate and have a success
				AudioRecord recorder = new AudioRecord(
						MediaRecorder.AudioSource.MIC,
						RECORDER_SAMPLERATE,
						RECORDER_CHANNELS,
						RECORDER_AUDIO_ENCODING,
						bufferElements2Rec
				);

				if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
					return recorder;
				Log.d(TAG, "Failed to init: " + recorder.getState());
			}
		} catch (Throwable e) {
			Log.e(TAG, RECORDER_SAMPLERATE + "Exception, keep trying.",e);
		}

		return null;
	}

	private void pipeAudioDataToMemoryAndFlushPeriodicallyInBackgroundThread() {
		// Write the output audio in byte
		short soundBuffer[] = new short[SHORTS_PER_ELEMENT];

		float timeSinceSessionStarted = 0f;
		try {
			timeSinceSessionStarted = sessionStopwatch.getSecondsSinceStarted();
		} catch (IllegalStateException t) {
			timeSinceSessionStarted = 0f;
		}

		ByteArrayOutputStream os = new ByteArrayOutputStream(IN_MEMORY_FILE_SIZE_IN_BYTES);
		StopWatch flushStopWatch = new StopWatch(false);
		StopWatch logStopWatch = new StopWatch(true);

		while (isRecording) {
			// gets the voice output from microphone to byte format
			if (activityWeakReference.get() != null) {
				synchronized (activityWeakReference.get()) {
					flushStopWatch.startIfNotStarted();

					if (flushStopWatch.stopIfAboveTimeLimit(AUDIO_FILE_MAX_DURATION_IN_SECONDS)) {
						// Duration limit reached, flush!
						try {
							flushOutputStreamToTestFairy(os, timeSinceSessionStarted);
							os.close();
							os = new ByteArrayOutputStream(IN_MEMORY_FILE_SIZE_IN_BYTES);

							try {
								timeSinceSessionStarted = sessionStopwatch.getSecondsSinceStarted();
							} catch (IllegalStateException t) {
								timeSinceSessionStarted = 0f;
							}
						} catch (IOException e) {
							e.printStackTrace();
						}

						flushStopWatch.start();
					}

					if (recorder != null) {
						synchronized (recorder) {
							recorder.read(soundBuffer, 0, SHORTS_PER_ELEMENT);

							if (logStopWatch.stopIfAboveTimeLimit(1)) {
								Log.d(TAG, "Audio is being written to memory file" + soundBuffer.toString());
								logStopWatch.start();
							}

							try {
								// // writes the data to file from buffer
								// // stores the voice buffer
								byte bData[] = short2byte(soundBuffer);
								os.write(bData, 0, bData.length);
							} catch (Throwable e) {
								e.printStackTrace();
							}
						}
					}
				}
			} else {
				stopRecording();
			}
		}

		try {
			flushStopWatch.stop();
			flushOutputStreamToTestFairy(os, timeSinceSessionStarted);
			os.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private void flushOutputStreamToTestFairy(@NonNull ByteArrayOutputStream os, float timestamp) {
		Log.d(TAG, "Flushing audio to TestFairy: " + timestamp);

		AudioSample sample = new AudioSample(
				RECORDER_SAMPLERATE,
				SHORTS_PER_ELEMENT * 8,
				1,
				MediaRecorder.AudioSource.MIC,
				timestamp,
				os.toByteArray()
		);

		TestFairy.addAudioRecording(sample);
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

	static private class StopWatch {

		private long startTime = 0;

		public StopWatch(boolean autoStart) {
			if (autoStart) start();
		}

		public void start() {
			if (startTime != 0) throw new IllegalStateException("StopWatch already started before.");

			startTime = new Date().getTime();
		}

		public void stop() {
			startTime = 0;
		}

		public void startIfNotStarted() {
			if (startTime == 0) start();
		}

		public boolean stopIfAboveTimeLimit(int limitInSeconds) {
			if (startTime == 0) throw new IllegalStateException("Cannot stop a StopWatch before starting it first.");

			long currentTime = new Date().getTime();

			if (currentTime - startTime >= limitInSeconds * 1000) {
				startTime = 0;
				return true;
			} else {
				return false;
			}
		}

		public float getSecondsSinceStarted() {
			if (startTime == 0) throw new IllegalStateException("Cannot read time from a StopWatch before starting it first.");

			long diff = new Date().getTime() - startTime;

			return (float) (((double) diff) / 1000.0);
		}
	}


	/***************** Exceptions *****************/

	static class AudioRecorderNotInitializedException extends RuntimeException {
		public AudioRecorderNotInitializedException() {
			super("Audio recorder cannot perform lifecycle. Did you forget to call TestFairyAudioRecord.onCreate() ?");
		}
	}


}
