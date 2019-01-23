package com.testfairy.audiorecord;

import android.app.Application;

import com.testfairy.TestFairy;

public class MainApplication extends Application {

	static public final String APP_TOKEN = "SDK-nwrmjkFK";

	@Override
	public void onCreate() {
		super.onCreate();

		TestFairy.begin(this, APP_TOKEN);
	}
}
