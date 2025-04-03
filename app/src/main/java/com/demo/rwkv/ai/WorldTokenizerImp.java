package com.demo.rwkv.ai;

import android.content.Context;

import com.google.gson.Gson;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ZTMIDGO 2022/9/15
 */
public class WorldTokenizerImp implements GptTokenizer {
    private final Map<String, Integer> encoder = new HashMap<>();
    private final Map<Integer, String> decoder = new HashMap<>();
    private final Set<String> tiesSet = new HashSet<>();
    private final Context context;

    public static String getTokenPath(Context context){
        return PathManager.getModelPath(context) + "/vocab.json";
    }

    public WorldTokenizerImp(Context context){
        this.context = context;
        fillDecoder();
        fillEncoder();
    }

    @Override
    public List<Integer> encode(String text) {
        List<Integer> result = new ArrayList<>();
        char[] chars = text.toCharArray();
        int position = 0;
        int start = 0;

        while (position <= chars.length){

            char[] copy = Arrays.copyOfRange(chars, start, position);
            if (!tiesSet.contains(HexUtils.charsToHex(copy)) || position == chars.length){
                while (position > start){
                    String word = new String(Arrays.copyOfRange(chars, start, position));
                    if (encoder.containsKey(word)) {
                        result.add(encoder.get(word));
                        start = position;
                        break;
                    }else {
                        if (-- position <= start){
                            start += 1;
                            position = start;
                            break;
                        }
                    }
                }
            }

            position ++;
        }
        return result;
    }

    @Override
    public String decode(List<Integer> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i : tokens){
            if (decoder.containsKey(i)) sb.append(decoder.get(i));
        }
        return sb.toString();
    }

    private void addTies(String word){
        char[] chars = word.toCharArray();

        for (int i = 1; i <= chars.length; i++) {
            tiesSet.add(HexUtils.charsToHex(Arrays.copyOf(chars, i)));
        }
    }

    private void fillEncoder(){
        try {
            for (Map.Entry<Integer, String> entry : decoder.entrySet()){
                encoder.put(entry.getValue(), entry.getKey());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void fillDecoder(){
        try {
            String path = getTokenPath(context);
            Map<String, String> map = new HashMap<>();
            map.putAll(new Gson().fromJson(new FileReader(path), decoder.getClass()));
            for (Map.Entry<String, String> entry : map.entrySet()){
                addTies(entry.getValue());
                decoder.put(Integer.parseInt(entry.getKey()), entry.getValue());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
