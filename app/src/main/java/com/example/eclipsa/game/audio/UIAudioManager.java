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

public class UIAudioManager {
    private static volatile UIAudioManager instance;
    private Context appContext;
    private HandlerThread audioThread;
    private Handler audioHandler;
    private Handler mainHandler;

    private MediaPlayer bgmPlayer;
    private SoundPool soundPool;
    private int clickSoundId;
    private float bgmVolume = 1.0f;

    /**
     * 私有构造：确保只能通过 getInstance 创建，实现单例。
     * @param context 实例对象
     */
    //避免内存泄漏：如果直接持有 Activity 的 Context，
    // 当 Activity 被销毁后，UIAudioManager 是单例（一直存在），
    // 仍会持有 Activity 的引用，导致 Activity 无法被垃圾回收，造成内存泄漏。
    // 而 ApplicationContext 不会持有任何 Activity，是安全的。
    //UIAudioManager 需要在应用整个生命周期中提供服务（背景音乐在多个 Activity 间共享），使用 ApplicationContext 最合适。
    private UIAudioManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());//创建 mainHandler：用于主线程回调
        this.audioThread = new HandlerThread("UIAudioThread");//创建并启动 audioThread：名为 "UIAudioThread" 的后台线程，专门处理音频操作。
        this.audioThread.start();
        this.audioHandler = new Handler(audioThread.getLooper());//创建 audioHandler：绑定到 audioThread 的 Looper
        //投递任务
        audioHandler.post(this::initBgm);
        audioHandler.post(this::initSoundPool);
    }

    /**
     * 单例获取方法
     * 双重检查锁定：保证线程安全的同时提高性能（避免每次调用都加锁）。
     * 首次调用时传入 Context 创建实例，后续调用直接返回已有实例
     * @param context
     * @return
     */
    public static UIAudioManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UIAudioManager.class) {
                if (instance == null) {
                    instance = new UIAudioManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 初始化bgm
     */
    private void initBgm() {
        try {
            bgmPlayer = MediaPlayer.create(appContext, R.raw.bgm_main);
            if (bgmPlayer != null) {
                bgmPlayer.setLooping(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化音效池
     * 参考GameAudioManager
     */
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
        clickSoundId = soundPool.load(appContext, R.raw.ui_click, 1);
    }

    /**
     * 开启bgm
     */
    public void startBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null && !bgmPlayer.isPlaying()) {
                bgmPlayer.start();
            }
        });
    }

    /**
     * 暂停bgm
     */
    public void stopBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null) {
                if (bgmPlayer.isPlaying()) bgmPlayer.stop();
                bgmPlayer.release();
                bgmPlayer = null;
            }
        });
    }

    /**
     * 点击音效播放
     */
    public void playClick() {
        audioHandler.post(() -> {
            if (soundPool != null && clickSoundId != 0) {
                soundPool.play(clickSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        });
    }

    /**
     * 释放资源
     */
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

    /**
     * 设置bgm
     * @param volume 音量
     */
    public void setBgmVolume(float volume) {
        this.bgmVolume = volume;
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(volume, volume);
        }
    }
}