package com.demo.rwkv.ai;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RWKVModel {
    static {
        System.loadLibrary("ai");
    }

    private WorldTokenizerImp tokenizer;
    private long reference;
    private boolean isRunning;

    public static String getModePath(Context context){
        return PathManager.getModelPath(context)+"/rwkv-model-1.5B-Q.bin";
    }

    public void init(Context context){
        reference = nativeInit(getModePath(context));
        tokenizer = new WorldTokenizerImp(context);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void close(){
        nativeClose(reference);
    }

    public void generate(String text, int maxLength, float presence, float frequency, float temp, float topp, int topk, Callback callback){
        isRunning = true;
        List<Integer> list = tokenizer.encode(text);
        list.addAll(0, Arrays.asList(24281, 59, 33));
        list.addAll(Arrays.asList(261, 5585, 41693, 59));
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) array[i] = list.get(i);
        Map<Integer, Float> occurrence = new HashMap<>();
        for (int i = 0; i < maxLength; i++){
            float[] outputLogits = nativeForward(reference, array);
            for (Map.Entry<Integer, Float> entry : occurrence.entrySet()){
                int x = entry.getKey();
                if (x < 0) continue;
                outputLogits[x] = outputLogits[x] - (presence + entry.getValue() * frequency);
            }
            int nextToken = sampleLogits(outputLogits, outputLogits.length, temp, topk, topp);

            if (!occurrence.containsKey(nextToken)) occurrence.put(nextToken, 0f);
            occurrence.put(nextToken, occurrence.get(nextToken) + 1f);

            array = new int[]{nextToken};
            String result = tokenizer.decode(Arrays.asList(nextToken));
            if (callback != null) callback.onResult(result);
            if ((nextToken == 60807 || nextToken == 23692 || nextToken == 33161 || nextToken == 82 || nextToken == 24281 || nextToken == 53648 || nextToken == 40301 || nextToken == 0)) break;
        }
        isRunning = false;
    }

    public void clean(){
        nativeClean(reference);
    }

    public native long nativeInit(String filePath);

    public native float[] nativeForward(long reference, int[] tonkens);

    public native int sampleLogits(float[] logits, int size, float temperature, int top_k, float top_p);

    public native void nativeClose(long reference);

    public native void nativeClean(long reference);

    public interface Callback{
        void onResult(String text);
    }
}
