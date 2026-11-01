package app.luma.torch.dictation;

import android.content.Context;
import android.content.res.AssetManager;
import com.k2fsa.sherpa.onnx.*;
import java.util.List;

/** בונה את המזהה. המודל נטען מקבצים בקושחה (ModelLocator.DIR), לא מתוך נכסי ה-APK. */
final class RecognizerFactory {
    static OnlineRecognizer create(Context context,boolean beam) {
        NativeLoader.ensure(); // לפני כל נגיעה במחלקות של sherpa-onnx
        List<String> missing=ModelLocator.missing();
        if(!missing.isEmpty()) throw new IllegalStateException("המודל חסר בקושחה: "+missing+" (נתיב: "+ModelLocator.DIR+")");
        OnlineTransducerModelConfig t=new OnlineTransducerModelConfig();
        t.setEncoder(ModelLocator.path("encoder.int8.onnx"));
        t.setDecoder(ModelLocator.path("decoder.int8.onnx"));
        t.setJoiner(ModelLocator.path("joiner.int8.onnx"));
        OnlineModelConfig m=new OnlineModelConfig();m.setTransducer(t);m.setTokens(ModelLocator.path("tokens.txt"));
        m.setModelType("zipformer2");m.setProvider("cpu");m.setNumThreads(2);
        m.setModelingUnit("bpe");m.setBpeVocab(ModelLocator.path("bpe.vocab"));
        OnlineRecognizerConfig c=new OnlineRecognizerConfig();c.setModelConfig(m);c.setFeatConfig(new FeatureConfig());
        c.setEnableEndpoint(true);c.setDecodingMethod(beam?"modified_beam_search":"greedy_search");
        c.setMaxActivePaths(2);c.setHotwordsScore(.18f);c.setBlankPenalty(0f);
        // assetManager=null => sherpa-onnx קורא את הנתיבים כקבצים רגילים בדיסק
        return new OnlineRecognizer((AssetManager) null,c);
    }
}
