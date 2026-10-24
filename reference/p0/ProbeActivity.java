package audio.apophenia;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phase-0 TEST PROBE only. Not the shipping app audio engine. */
public final class ProbeActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private TextView log;
    private Thread worker;
    private SpeechRecognizer recognizer;
    private static final int SAMPLE_RATE = 48000;
    private static final int RECORD_PERMISSION_REQUEST = 200;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff11151c);
        getWindow().setNavigationBarColor(0xff11151c);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff11151c);
        root.setPadding(22, 22, 22, 40);
        scroll.addView(root);
        TextView title = new TextView(this);
        title.setText("APOPHENIA  /  P0 PROBE");
        title.setTextColor(0xffebe4d5);
        title.setTextSize(23);
        root.addView(title);
        add(root, "1 · Device capabilities", () -> capabilities());
        add(root, "2 · Request mic permission", () -> requestMic());
        add(root, "3 · Test UNPROCESSED mic", () -> startTest(MediaRecorder.AudioSource.UNPROCESSED, false));
        add(root, "4 · Test VOICE_RECOGNITION mic", () -> startTest(MediaRecorder.AudioSource.VOICE_RECOGNITION, false));
        add(root, "5 · Test MIC source", () -> startTest(MediaRecorder.AudioSource.MIC, false));
        add(root, "6 · Test mic + output together", () -> startTest(MediaRecorder.AudioSource.VOICE_RECOGNITION, true));
        add(root, "STOP audio test", () -> stopTest());
        add(root, "7 · Native prebuilt loading check", () -> nativeCheck());
        add(root, "8 · Notation symbol rendering", () -> append("U+1D12A: \ud834\udd2a · if a replacement box, test font fallback"));
        add(root, "9 · On-device speech availability", () -> speechCheck());
        add(root, "9b · Try offline speech (speak one phrase)", () -> speechRun());
        add(root, "Share probe log", () -> shareLog());
        add(root, "10 · Permission revoke API (inspection only)", () -> append("API="+Build.VERSION.SDK_INT+"; revokeSelfPermissionOnKill exists from API 33. This probe does NOT revoke your permission."));
        log = new TextView(this);
        log.setTextColor(0xffb8d9da);
        log.setTextSize(14);
        log.setTextIsSelectable(true);
        log.setPadding(8, 30, 8, 30);
        root.addView(log);
        setContentView(scroll);
        append("Probe ready. Never starts the microphone without a button tap.");
    }
    private void add(LinearLayout root, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        root.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    private void append(String text) { runOnUiThread(() -> log.append("\n" + text)); }
    private void capabilities() {
        append("Device="+Build.MANUFACTURER+" "+Build.MODEL+" Android API="+Build.VERSION.SDK_INT);
        android.media.AudioManager am = (android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        append("OUTPUT_SAMPLE_RATE="+am.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        append("OUTPUT_FRAMES_PER_BUFFER="+am.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        append("UNPROCESSED_SUPPORTED="+am.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        append("SpeechRecognizer.isRecognitionAvailable="+SpeechRecognizer.isRecognitionAvailable(this));
    }
    private boolean allowed() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private void requestMic() { if (!allowed()) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_PERMISSION_REQUEST); else append("Mic permission already granted"); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        append("mic permission="+(allowed()?"granted":"denied"));
    }
    private void speechCheck() {
        if (Build.VERSION.SDK_INT >= 31) append("isOnDeviceRecognitionAvailable="+SpeechRecognizer.isOnDeviceRecognitionAvailable(this));
        else append("On-device recognizer factory unavailable (<API31)");
        append("Availability only; routing our own AudioRecord PCM through EXTRA_AUDIO_SOURCE (API33) remains a separate DEVICE test.");
    }
    private void shareLog() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Apophenia P0 probe log");
        send.putExtra(Intent.EXTRA_TEXT, log.getText().toString());
        startActivity(Intent.createChooser(send, "Share probe log"));
    }
    private void speechRun() {
        if (!allowed()) { append("Grant mic permission first"); requestMic(); return; }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            append("No on-device recognizer reported; not using a network speech service"); return;
        }
        if (recognizer != null) { append("Recognition already running"); return; }
        try {
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params){append("Speech ready; say a short phrase");}
                @Override public void onBeginningOfSpeech(){append("Speech started");}
                @Override public void onRmsChanged(float rmsdB){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){append("Speech ended");}
                @Override public void onError(int error){append("On-device speech error code="+error);finishSpeech();}
                @Override public void onResults(Bundle results) {
                    ArrayList<String> phrases=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    append("On-device result="+(phrases==null?"[]":phrases.toString()));finishSpeech();
                }
                @Override public void onPartialResults(Bundle results){}
                @Override public void onEvent(int eventType,Bundle params){}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            recognizer.startListening(intent);
            append("Started on-device recognition. Device service may still need locally installed language packs.");
        } catch(Exception e) {append("Cannot start on-device speech: "+e);finishSpeech();}
    }
    private void finishSpeech() {
        if(recognizer!=null){recognizer.destroy();recognizer=null;}
    }
    private void nativeCheck() {
        try { System.loadLibrary("apophenia_probe"); append("Loaded libapophenia_probe.so successfully"); }
        catch (UnsatisfiedLinkError ex) { append("No usable prebuilt libapophenia_probe.so: "+ex.getMessage()); }
    }
    private void startTest(int source, boolean speaker) {
        if (!allowed()) { append("Grant microphone permission first"); requestMic(); return; }
        if (running.get()) { append("A test is running; STOP first"); return; }
        running.set(true);
        worker = new Thread(() -> runCapture(source, speaker), "p0-audio-test");
        worker.start();
    }
    private void runCapture(int source, boolean speaker) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        final int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final int outMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec = null;
        AudioTrack out = null;
        try {
            if (min <= 0) throw new IllegalStateException("No 48k input: "+min);
            rec = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min*2, 8192));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord init failed");
            if (speaker) {
                if (outMin <= 0) throw new IllegalStateException("No 48k output: "+outMin);
                out = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(outMin*2,8192)).setTransferMode(AudioTrack.MODE_STREAM).build();
                out.play();
            }
            rec.startRecording();
            append("AudioRecord source="+source+" actual source="+rec.getAudioSource()+" rate="+rec.getSampleRate()+" simultaneous="+speaker);
            short[] in = new short[2048];
            short[] tone = new short[2048];
            double phase = 0.0, power = 0.0;
            long total=0, start=System.nanoTime();
            while (running.get() && (System.nanoTime()-start)<8_000_000_000L) {
                if (speaker) {
                    for(int i=0;i<tone.length;i++) { tone[i]=(short)(Math.sin(phase)*1300); phase+=2*Math.PI*440/SAMPLE_RATE; }
                    out.write(tone,0,tone.length,AudioTrack.WRITE_BLOCKING);
                }
                int n = rec.read(in,0,in.length,AudioRecord.READ_BLOCKING);
                if(n<0) throw new IllegalStateException("AudioRecord read returned "+n);
                for(int i=0;i<n;i++) power+=in[i]* (double)in[i];
                total+=n;
            }
            append("Captured "+total+" samples; RMS="+(total>0 ? Math.round(Math.sqrt(power/total)):0)+" (PCM units), recorded audio NOT retained");
            if (speaker) append("Important: output mic bleed is expected; this only tests concurrent I/O.");
        } catch (Exception e) { append("ERROR source="+source+": "+e); }
        finally {
            if(rec!=null) { try { rec.stop(); } catch(Exception ignore) {} rec.release(); }
            if(out!=null) { try { out.stop(); } catch(Exception ignore) {} out.release(); }
            running.set(false);
            append("Test stopped");
        }
    }
    private void stopTest() { running.set(false); append("Stop requested"); }
    @Override protected void onDestroy() { stopTest(); finishSpeech(); super.onDestroy(); }
}
