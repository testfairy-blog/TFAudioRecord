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
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;

public class TestFairyAudioRecord {

	/***************** SDK Constants *****************/

	static private String TAG = "TFAudioRecorder";
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
						}, "TFAudioRecorder Thread");
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
							flushOutputStreamToTestFairy(os);
							os.close();
							os = new ByteArrayOutputStream(IN_MEMORY_FILE_SIZE_IN_BYTES);
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
				flushStopWatch.stop();
				stopRecording();
			}
		}

		try {
			flushStopWatch.stop();
			flushOutputStreamToTestFairy(os);
			os.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private void flushOutputStreamToTestFairy(@NonNull ByteArrayOutputStream os) {
		Log.d(TAG, "Flushing audio to TestFairy");

		try {
			AudioSample sample = new AudioSample(RECORDER_SAMPLERATE, SHORTS_PER_ELEMENT * 8, 1);
			byte[] wavFile = sample.toWavFile(os.toByteArray());

			// TODO : send wavFile to TestFairy

		} catch (IOException e) {
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
	}

	public static class AudioSample {

		// Header template to copy into each Wave file, will be edited after copy.
		static private final byte[] header = {
				'R', 'I', 'F', 'F',
				'0', '0', '0', '0',     // chunk size
				'W', 'A', 'V', 'E',
				'f', 'm', 't', ' ',
				0x10, 0x00, 0x00, 0x00, // subchunk size (16 bytes)
				0x01, 0x00,             // audio format pcm
				0x01, 0x00,             // total channels
				0x00, 0x00, 0x00, 0x00, // sample rate
				0x00, 0x00, 0x00, 0x00, // byte rate
				0x04, 0x00,             // block align
				0x10, 0x00,             // bits per sample
				'd', 'a', 't', 'a',
				0x00, 0x00, 0x00, 0x00, // total sample data size
		};

		private int sampleRate;
		private int bitsPerSample;
		private int channels;

		public AudioSample(int sampleRate, int bitsPerSample, int channels) {
			this.sampleRate = sampleRate;
			this.bitsPerSample = bitsPerSample;
			this.channels = channels;
		}

		/**
		 * Join all audio chunks into one playable RIFF wave file
		 *
		 * @return byte[]
		 * @throws IOException
		 */
		public byte[] toWavFile(@NonNull byte[] audioData) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(header);
			baos.write(audioData, 0, audioData.length);

			byte[] out = baos.toByteArray();

			int byteRate = (sampleRate * bitsPerSample * channels) / 8;
			int fileSize = out.length + header.length - 4;

			out[0x04] = (byte) (fileSize & 0xff);
			out[0x05] = (byte) ((fileSize >> 8) & 0xff);
			out[0x06] = (byte) ((fileSize >> 16) & 0xff);
			out[0x07] = (byte) ((fileSize >> 24) & 0xff);

			out[0x16] = (byte) (channels & 0xff);

			out[0x18] = (byte) (sampleRate & 0xff);
			out[0x19] = (byte) ((sampleRate >> 8) & 0xff);
			out[0x1a] = (byte) ((sampleRate >> 16) & 0xff);
			out[0x1b] = (byte) ((sampleRate >> 24) & 0xff);

			out[0x1c] = (byte) (byteRate & 0xff);
			out[0x1d] = (byte) ((byteRate >> 8) & 0xff);
			out[0x1e] = (byte) ((byteRate >> 16) & 0xff);
			out[0x1f] = (byte) ((byteRate >> 24) & 0xff);

			out[0x22] = (byte) (bitsPerSample & 0xff);
			out[0x23] = (byte) ((bitsPerSample >> 8) & 0xff);

			out[0x28] = (byte) (out.length & 0xff);
			out[0x29] = (byte) ((out.length >> 8) & 0xff);
			out[0x2a] = (byte) ((out.length >> 16) & 0xff);
			out[0x2b] = (byte) ((out.length >> 24) & 0xff);

			return out;
		}
	}


	/***************** Exceptions *****************/

	static class AudioRecorderNotInitializedException extends RuntimeException {
		public AudioRecorderNotInitializedException() {
			super("Audio recorder cannot perform lifecycle. Did you forget to call TestFairyAudioRecord.onCreate() ?");
		}
	}


}
