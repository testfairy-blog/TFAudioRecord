# TFAudioRecord
TestFairy audio recording sample project.

## How to include in a project
1. Copy [this](/app/src/main/java/com/testfairy/audiorecord/TestFairyAudioRecord.java) to your project.
2. [Add TestFairy SDK to your project](https://docs.testfairy.com/Android/Integrating_Android_SDK.html).
3. Put the line below right next to `TestFairy.begin(context, token)`.
```java
 TestFairyAudioRecord.begin(context);
```
4. Put the lines below in your main activity.
```java
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    TestFairyAudioRecord.onRequestPermissionsResult(requestCode, permissions, grantResults);
}
```  
5. Profit!

## Extra Utilities
* Use `TestFairyAudioRecorder.setAudioSampleListener()` to capture each recorder audio sample. Given listeners will run in main thread.
* Use `TestFairyAudioRecorder.mute()` to pause recordings. Use `TestFairyAudioRecorder.unmute()` to resume.

## License
Apache License 2.0
