package app.luma.torch.dictation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import com.k2fsa.sherpa.onnx.*;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only keeps audio in memory. Native inference and AudioRecord belong to one worker. */
public final class SpeechEngine {
    public interface Observer { void onState(String state); }
    public interface Session { void onText(String text); void onFinished(); void onError(String message); }
    private static SpeechEngine instance;
    public static synchronized SpeechEngine get(Context context) {
        if (instance == null) instance = new SpeechEngine(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<Observer> observers = new CopyOnWriteArraySet<>();
    private OnlineRecognizer recognizer;
    private volatile boolean configuredBeam;
    // Latest user intent survives queued native release/load work.
    private volatile boolean wantLoaded, requestedBeam;
    private volatile String state = "off";
    private volatile String lastError = "";
    private volatile boolean listening;
    private Session session;
    private volatile int generation;
    private volatile int transcriptRevision;
    private SpeechEngine(Context context) { this.context=context; }
    public String state() { return state; }
    /** סיבת הכשל האחרון בטעינת המודל (ריק אם אין), לתצוגה במסך ההכתבה. */
    public String lastError() { return lastError; }
    private static String describe(Throwable error) {
        StringBuilder sb=new StringBuilder(); int depth=0;
        for(Throwable t=error;t!=null&&depth<5;t=t.getCause(),depth++) {
            if(sb.length()>0) sb.append("\n<- ");
            sb.append(t.getClass().getSimpleName());
            if(t.getMessage()!=null) sb.append(": ").append(t.getMessage());
        }
        return sb.toString();
    }
    public boolean isListening() { return listening; }
    public void observe(Observer observer) { observers.add(observer); observer.onState(state); }
    public void remove(Observer observer) { observers.remove(observer); }
    private void state(String value) {
        if(value.equals(state)) return;
        state=value;
        main.post(() -> { for (Observer observer:observers) observer.onState(value); });
    }
    public void warm() {
        requestedBeam=RecognitionHints.snapshot(context).beam;
        wantLoaded=true;
        if(state.equals("off")||state.equals("error")) state("loading");
        worker.execute(this::reconcile);
    }
    public void reconfigure() {
        boolean beam=RecognitionHints.snapshot(context).beam;
        requestedBeam=beam;
        if(state.equals("ready")&&configuredBeam==beam)return;
        cancel();
        if(wantLoaded) state("loading");
        worker.execute(this::reconcile);
    }
    /** Native worker only: settle to the latest requested state, even if it changed mid-load. */
    private void reconcile() {
        if(listening) return;
        while(true) {
            if(!wantLoaded) {
                if(recognizer!=null) recognizer.release();
                recognizer=null; state("off"); return;
            }
            if(recognizer!=null&&configuredBeam==requestedBeam) { state("ready"); return; }
            state("loading");
            try {
                if(recognizer!=null)recognizer.release();recognizer=null;
                boolean beam=requestedBeam;
                recognizer=RecognizerFactory.create(context,beam);configuredBeam=beam;lastError="";
            } catch(Throwable error) {
                android.util.Log.e("LumaDictation","model load failed",error);
                // שומרים את הכשל הראשון: ניסיונות חוזרים מחזירים לרוב רק "NoClassDefFoundError" ומסתירים את הסיבה האמיתית
                if(lastError.isEmpty()) lastError=describe(error)+"\nABI: "+String.join(",",Build.SUPPORTED_ABIS);
                state(wantLoaded?"error":"off"); return;
            }
        }
    }
    public boolean start(Session listener) {
        if (!wantLoaded || !state.equals("ready") || listening) return false;
        session=listener; int token=++generation; listening=true; state("listening");
        worker.execute(() -> capture(token)); return true;
    }
    public void stop() { if (listening) { listening=false; state("finishing"); } }
    public void cancel() { generation++; session=null; stop(); }
    public void close() {
        wantLoaded=false; cancel();
        worker.execute(this::reconcile);
    }
    /** Keep AudioRecord running, but discard hypotheses from before a manual edit. */
    public void resetTranscript() { transcriptRevision++; }
    private void text(int token,int revision,String text) {
        main.post(() -> { if(token==generation && revision==transcriptRevision && session!=null) session.onText(text); });
    }
    private static String join(String a,String b) { return a.isEmpty()?b:b.isEmpty()?a:a+" "+b; }
    private void captureFailed(int token) {
        main.post(() -> { if(token==generation && session!=null) { Session failed=session; session=null;
            failed.onError("המיקרופון לא זמין. בדוק את ההרשאה ונסה שוב."); } });
    }
    private void capture(int token) {
        AudioRecord audio=null; OnlineStream stream=null; String committed="", previous="";
        int revision=transcriptRevision;
        try {
            if(token!=generation) return;
            if(!Platform.hasPermission(context,Manifest.permission.RECORD_AUDIO))
                throw new SecurityException("Microphone permission revoked");
            int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(size<=0) throw new IllegalStateException("Unsupported microphone");
            audio=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,Math.max(size*2,64000));
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
            stream=recognizer.createStream(configuredBeam?RecognitionHints.snapshot(context).hotwords:""); audio.startRecording(); short[] pcm=new short[2560];
            float[] full=new float[pcm.length];
            while(listening && token==generation) {
                int count=Build.VERSION.SDK_INT>=23?audio.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING):audio.read(pcm,0,pcm.length);
                if(count<0) throw new IllegalStateException("Microphone read failed");
                if(count==0) continue;
                if(revision!=transcriptRevision) {
                    revision=transcriptRevision;
                    stream.release(); stream=null;
                    stream=recognizer.createStream(configuredBeam?RecognitionHints.snapshot(context).hotwords:"");
                    committed=""; previous="";
                    // Drop the straddling buffer and any queued audio from before the edit.
                    if(Build.VERSION.SDK_INT>=23) {
                        // Bounded nonblocking work; keep the microphone open on modern Android.
                        for(int drained=0;drained<32;drained++) {
                            int stale=audio.read(pcm,0,pcm.length,AudioRecord.READ_NON_BLOCKING);
                            if(stale<=0)break;
                        }
                    } else {
                        // Before API 23 there is no nonblocking read. Restart this AudioRecord
                        // to flush buffered audio without waiting for another blocking read.
                        audio.stop(); audio.startRecording();
                    }
                    continue;
                }
                // a blocking read nearly always fills the buffer; only a short read needs its own array
                float[] samples=count==full.length?full:new float[count]; for(int i=0;i<count;i++) samples[i]=pcm[i]/32768f;
                stream.acceptWaveform(samples,16000); while(recognizer.isReady(stream)) recognizer.decode(stream);
                String current=recognizer.getResult(stream).getText().trim();
                if(recognizer.isEndpoint(stream)) { committed=join(committed,current); recognizer.reset(stream); current=""; }
                String all=join(committed,current);
                if(!all.equals(previous)) { previous=all; text(token,revision,all); }
            }
            audio.stop(); stream.acceptWaveform(new float[24000],16000); stream.inputFinished();
            while(recognizer.isReady(stream)) recognizer.decode(stream);
            text(token,revision,join(committed,recognizer.getResult(stream).getText().trim()));
            main.post(() -> { if(token==generation && session!=null) { Session done=session; session=null; done.onFinished(); } });
        } catch(SecurityException denied) {
            // Permission may be revoked after the initial self-UID check.
            captureFailed(token);
        } catch(Throwable error) {
            captureFailed(token);
        } finally {
            if(token==generation) listening=false; // לא מכבים הקלטה חדשה שהתחילה בזמן שזו הסתיימה
            if(audio!=null) { try { audio.stop(); } catch(Exception ignored) {} audio.release(); }
            if(stream!=null) stream.release(); reconcile();
        }
    }
}
