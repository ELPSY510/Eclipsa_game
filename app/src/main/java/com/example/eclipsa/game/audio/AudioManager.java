package com.example.eclipsa.game.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.example.eclipsa.R;

public class AudioManager {
    private static AudioManager instance;
    private Context context;

    private MediaPlayer bgmPlayer;
    private SoundPool soundPool;
    private int uiClickId;
    private boolean bgmLoaded = false;

    private HandlerThread audioThread;
    private Handler audioHandler;
    private Handler mainHandler;

    private AudioManager(Context context) {
        this.context = context.getApplicationContext();
        mainHandler = new Handler(Looper.getMainLooper());
        audioThread = new HandlerThread("AudioThread");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        // 异步初始化背景音乐和音效
        audioHandler.post(() -> {
            initBgm();
            initSoundPool();
        });
    }

    public static synchronized AudioManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioManager(context);
        }
        return instance;
    }

    private void initBgm() {
        try {
            bgmPlayer = MediaPlayer.create(context, R.raw.bgm_main);
            if (bgmPlayer != null) {
                bgmPlayer.setLooping(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(aa).build();
        } else {
            soundPool = new SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0);
        }
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                // 加载完成
            }
        });
        uiClickId = soundPool.load(context, R.raw.ui_click, 1);
    }

    public void startBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null && !bgmPlayer.isPlaying()) {
                try {
                    bgmPlayer.start();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void stopBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null) {
                try {
                    bgmPlayer.stop();
                    bgmPlayer.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                bgmPlayer = null;
            }
        });
    }

    public void playUiClick() {
        audioHandler.post(() -> {
            if (soundPool != null && uiClickId != 0) {
                soundPool.play(uiClickId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        });
    }

    public void release() {
        audioHandler.post(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.release();
                bgmPlayer = null;
            }
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        });
        audioThread.quitSafely();
    }
}