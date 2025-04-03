package com.demo.rwkv.ai;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.rwkv.ai.databinding.FragmentAnswerBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by ZTMIDGO 2023/6/20
 */
public class TalkFragment extends Fragment {
    private final ExecutorService exec = Executors.newCachedThreadPool();

    public static TalkFragment newInstance() {

        Bundle args = new Bundle();

        TalkFragment fragment = new TalkFragment();
        fragment.setArguments(args);
        return fragment;
    }
    private LinearLayoutManager mLayoutManager;

    private Handler uiHandler;
    private ProgressDialog dialog;
    private RWKVModel model;
    private MyAdapter mAdapter;
    private FragmentAnswerBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiHandler = new Handler();
        dialog = new ProgressDialog(getActivity());
        dialog.setCancelable(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
        if (model != null) model.close();
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAnswerBinding.inflate(inflater, container, false);

        mLayoutManager = new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false);
        mAdapter = new MyAdapter(getActivity(), inflater, new ArrayList<>());
        binding.recyclerView.setLayoutManager(mLayoutManager);
        mLayoutManager.setStackFromEnd(true);
        binding.recyclerView.setAdapter(mAdapter);

        binding.header.topK.setText(String.valueOf(PreferencesManager.getTopK()));
        binding.header.len.setText(String.valueOf(PreferencesManager.getLen()));
        binding.header.p1.setText(String.valueOf(PreferencesManager.getP1()));
        binding.header.p2.setText(String.valueOf(PreferencesManager.getP2()));
        binding.header.temp.setText(String.valueOf(PreferencesManager.getTemp()));
        binding.header.topP.setText(String.valueOf(PreferencesManager.getTopp()));


        File modePath = new File(RWKVModel.getModePath(getActivity()));
        File tokenPath = new File(WorldTokenizerImp.getTokenPath(getActivity()));
        binding.divCopy.setVisibility(modePath.exists() && tokenPath.exists() ? GONE : VISIBLE);

        binding.copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileUtils.copyAssets(getActivity().getAssets(), "model", getActivity().getFilesDir().getAbsoluteFile());
                        }catch (Exception e){
                            e.printStackTrace();
                        }finally {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    binding.divCopy.setVisibility(modePath.exists() && tokenPath.exists() ? GONE : VISIBLE);
                                    dialog.dismiss();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String topKStr = binding.header.topK.getText().toString();
                String lenStr = binding.header.len.getText().toString();
                String p1Str = binding.header.p1.getText().toString();
                String p2Str = binding.header.p2.getText().toString();
                String text = binding.edit.getText().toString();
                String tempStr = binding.header.temp.getText().toString();
                String toppStr = binding.header.topP.getText().toString();

                final int topK = TextUtils.isEmpty(topKStr) ? PreferencesManager.getTopK() : Integer.parseInt(topKStr);
                final int len = TextUtils.isEmpty(lenStr) ? PreferencesManager.getLen() : Integer.parseInt(lenStr);
                final float p1 = TextUtils.isEmpty(p1Str) ? PreferencesManager.getP1() : Float.parseFloat(p1Str);
                final float p2 = TextUtils.isEmpty(p2Str) ? PreferencesManager.getP2() : Float.parseFloat(p2Str);
                final float temp = TextUtils.isEmpty(tempStr) ? PreferencesManager.getTemp() : Float.parseFloat(tempStr);
                final float topp = TextUtils.isEmpty(toppStr) ? PreferencesManager.getTopp() : Float.parseFloat(toppStr);

                PreferencesUtils.setProperty(Atts.LEN, (int)len);
                PreferencesUtils.setProperty(Atts.TOP_K, (int)topK);
                PreferencesUtils.setProperty(Atts.P1, p1 * 1f);
                PreferencesUtils.setProperty(Atts.P2, p2 * 1f);
                PreferencesUtils.setProperty(Atts.TEMP, temp * 1f);
                PreferencesUtils.setProperty(Atts.TOP_P, topp * 1f);

                if (model != null && model.isRunning()) return;

                if (model == null){
                    dialog.show();
                    exec.execute(new MyRunnable() {
                        @Override
                        public void run() {
                            model = new RWKVModel();
                            model.init(getActivity());
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    working(text, temp, topp, topK, len, p1, p2);
                                }
                            });
                        }
                    });
                }else {
                    working(text, temp, topp, topK, len, p1, p2);
                }
            }
        });

        binding.clean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (model == null || model.isRunning()) return;
                
                mAdapter.clean();
                model.clean();
            }
        });

        return binding.getRoot();
    }

    private void working(String text, float temp, float topp, int topK, int len, float p1, float p2){
        dialog.dismiss();

        if (TextUtils.isEmpty(text)) return;

        mAdapter.add(new Talk(Talk.TYPE_QUESTION, text));
        final Talk answer = new Talk(Talk.TYPE_ANSWER, "");
        mAdapter.add(answer);
        mLayoutManager.scrollToPositionWithOffset(mAdapter.getItemCount() - 1, Integer.MIN_VALUE);

        binding.edit.setText("");
        exec.execute(new Runnable() {
            @Override
            public void run() {
                model.generate(text, len, p1, p2, temp, topp, topK, new RWKVModel.Callback() {
                    @Override
                    public void onResult(String text) {
                        answer.setText((answer.getText() + text).replaceFirst("(\n\n)$", ""));
                        uiHandler.post(new MyRunnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyItemChanged(mAdapter.getItemCount() - 1);
                                mLayoutManager.scrollToPositionWithOffset(mAdapter.getItemCount() - 1, Integer.MIN_VALUE);
                            }
                        });
                    }
                });
            }
        });
    }
}
