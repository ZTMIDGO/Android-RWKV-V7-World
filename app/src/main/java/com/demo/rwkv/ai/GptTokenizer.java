package com.demo.rwkv.ai;

import java.util.List;

/**
 * Created by ZTMIDGO 2022/9/9
 */
public abstract interface GptTokenizer {
    List<Integer> encode(String text);
    String decode(List<Integer> tokens);
}
