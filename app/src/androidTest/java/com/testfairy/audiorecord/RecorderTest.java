package com.testfairy.audiorecord;

import android.Manifest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.testfairy.modules.audio.AudioSample;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RecorderTest {

	static private String TEST_TAG = "TFAudioRecorderTest";

	@Rule
	public ActivityTestRule<MainActivity> activityActivityTestRule = new ActivityTestRule<>(MainActivity.class);

	@Rule
	public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

	@Before
	public void setup() throws InterruptedException {
		waitForRecorderThread();
	}

	@Test
	public void lifecycleTest() throws InterruptedException {
		final List<AudioSample> samples = new ArrayList<>();

		TestFairyAudioRecord.setAudioSampleListener(new TestFairyAudioRecord.AudioSampleListener() {
			@Override
			public void onNewSample(AudioSample audioSample) {
				Log.d(TEST_TAG, "Added sample");
				samples.add(audioSample);
			}
		});

		Thread.sleep(5000);
		TestFairyAudioRecord.onPause();
		Thread.sleep(5000);
		TestFairyAudioRecord.onResume();
		Thread.sleep(5000);
		TestFairyAudioRecord.onPause();
		Thread.sleep(5000);

		assertEquals("Pausing should flush the recording.", 2, samples.size());
	}

	@Test
	public void timeLimitTest() throws InterruptedException {
		final List<AudioSampleHolder> samples = new ArrayList<>();

		TestFairyAudioRecord.setAudioSampleListener(new TestFairyAudioRecord.AudioSampleListener() {
			@Override
			public void onNewSample(AudioSample audioSample) {
				samples.add(new AudioSampleHolder(audioSample));
			}
		});

		Thread.sleep(40000);
		TestFairyAudioRecord.setAudioSampleListener(null);

		assertEquals(samples.size(), 2);

		long timeDiff = samples.get(1).getTime() - samples.get(0).getTime();
		assertTrue(
				"Consecutive recordings should be 15 seconds apart.",
				timeDiff > 14000 && timeDiff < 16000
		);
	}

	@Test
	public void audioSampleFileSizeTest() throws InterruptedException {
		final List<AudioSampleHolder> samples = new ArrayList<>();

		TestFairyAudioRecord.setAudioSampleListener(new TestFairyAudioRecord.AudioSampleListener() {
			@Override
			public void onNewSample(AudioSample audioSample) {
				samples.add(new AudioSampleHolder(audioSample));
			}
		});

		Thread.sleep(20000);
		TestFairyAudioRecord.setAudioSampleListener(null);

		assertEquals(samples.size(), 1);

		assertTrue(
				"File size is less than 600kb.",
				samples.get(0).getSample().toWavFile().length < 1024 * 600
		);
	}

	private void waitForRecorderThread() throws InterruptedException {
		long end = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < end) {

			int count = countRecorderAliveThreads();
			if (count == 1) {
				// matched expected number of threads
				return;
			}

			Thread.sleep(1000);
		}

		List<Thread> testfairyAliveThreads = getRecorderAliveThreads();

		assertEquals("Number of live TestFairy recorder threads should match expectations.", 1, testfairyAliveThreads.size());
	}

	private int countRecorderAliveThreads() {
		List<String> stillAlive = new ArrayList<>();

		List<Thread> threads = getRecorderAliveThreads();
		for (Thread thread : threads) {

			stillAlive.add(thread.getName());
		}

		if (stillAlive.size() > 0) {
			Log.d(TEST_TAG, " TestFairy Audio Threads: " + stillAlive.toString());
		}

		Log.d(TEST_TAG, "countRecorderAliveThreads returns = " + stillAlive.size());
		return stillAlive.size();
	}

	private List<Thread> getRecorderAliveThreads() {
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Thread[] allThreads = threadSet.toArray(new Thread[threadSet.size()]);

		ArrayList<Thread> threads = new ArrayList<>();
		for (Thread thread : allThreads) {
			if (thread.getName().contains("testfairy-audiorecord") && thread.isAlive()) {
				threads.add(thread);
			}
		}

		return threads;
	}

	private static class AudioSampleHolder {
		private long time;
		private AudioSample sample;

		public AudioSampleHolder(AudioSample sample) {
			this.time = System.currentTimeMillis();
			this.sample = sample;
		}

		public long getTime() {
			return time;
		}

		public AudioSample getSample() {
			return sample;
		}
	}

}
