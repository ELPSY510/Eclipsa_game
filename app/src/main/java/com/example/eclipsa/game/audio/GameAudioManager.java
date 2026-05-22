package com.example.eclipsa.game.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.example.eclipsa.R;

/**
 * 这个类负责游戏内的打击音效（Tap/Hold/Drag/Flick 命中音效）和游戏内的背景音乐（BGM）
 * 它使用 HandlerThread 实现独立音频线程，避免阻塞主线程和游戏循环
 */
public class GameAudioManager {
    private float bgmVolume = 1.0f;
    private float seVolume = 1.0f;
    private boolean seEnabled = true;

    private SoundPool soundPool;//短音效播放器
    private int tapSoundId, holdSoundId, dragSoundId, flickSoundId;//SoundPool.load 返回的音效 ID。
    private HandlerThread audioThread;//独立线程
    private Handler audioHandler;//关联audio的heandler，用于发送任务
    private boolean ready = false;//标记soundpool是否加载完毕

    // 背景音乐
    private MediaPlayer bgmPlayer;
    private boolean bgmLoaded = false;
    //回调接口
    private OnBgmReadyListener bgmReadyListener;

    public interface OnBgmReadyListener {
        void onBgmReady();
    }

    /**
     * 创建 HandlerThread 并启动，名字为 "GameAudioThread"。
     * 创建 Handler 绑定到该线程的 Looper。
     * 通过 audioHandler.post 将 initSoundPool 任务投递到音频线程执行（异步初始化 SoundPool）。
     * @param context 实例
     */
    public GameAudioManager(Context context) {
        audioThread = new HandlerThread("GameAudioThread");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
        audioHandler.post(() -> initSoundPool(context));
    }

    public void setOnBgmReadyListener(OnBgmReadyListener listener) {
        this.bgmReadyListener = listener;
    }

    private void initSoundPool(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//不同版本的 SoundPool 构造方式不同，需要分别处理。
            AudioAttributes aa = new AudioAttributes.Builder()//创建 AudioAttributes 对象，用于描述音频的用途和类型。
                    .setUsage(AudioAttributes.USAGE_GAME)//表明该音频用于游戏（系统会针对游戏场景优化，例如低延迟）。
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)//表明内容是短促的音效
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(aa).build();
            //setMaxStreams(5)：设置最多同时播放 5 个音效流。超过时系统会自动停止优先级最低的流。
            //setAudioAttributes(aa)：应用上面定义的 AudioAttributes。
        } else {//对于 Android 5.0 以下版本，使用旧的构造函数：
            //第一个参数 5：最大并发流数。
            //第二个参数 STREAM_MUSIC：音频流类型（音乐流）。
            //第三个参数 0：采样率转换质量（0 表示默认）
            soundPool = new SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0);
        }
        // 加载四种键型的音效（文件名需与资源一致）
        tapSoundId   = soundPool.load(context, R.raw.tap, 1);
        holdSoundId  = soundPool.load(context, R.raw.hold, 1);
        dragSoundId  = soundPool.load(context, R.raw.drag, 1);
        flickSoundId = soundPool.load(context, R.raw.flick, 1);
        ready = true;//初始化完成标记
    }

    /**
     * 加载bgm
     * @param context 实例
     * @param chartFileName 文件名
     */
    public void loadBgm(Context context, String chartFileName) {
        //将整个加载过程放到音频线程 (audioHandler 关联的线程) 中执行，避免解码 MP3 等耗时操作阻塞主线程（UI 线程）或游戏循环线程
        audioHandler.post(() -> {
            //根据谱面文件名（例如 "tutorial.json"）构造对应的背景音乐资源名。
            String baseName = chartFileName.replace(".json", "");
            String resName = baseName + "_bgm";
            //如果存在 tutorial_bgm.mp3 等文件，返回其资源 ID；否则返回 0。
            int resId = context.getResources().getIdentifier(resName, "raw", context.getPackageName());

            if (resId == 0) {
                resId = R.raw.default_bgm;//如果找不到歌曲专属背景音乐，则回退到默认背景音乐 default_bgm。
            }
            if (resId == 0) {
                android.util.Log.e("GameAudioManager", "No bgm resource found for " + chartFileName);
                return;//如果连默认背景音乐也不存在（资源缺失），记录错误日志并直接返回，避免后续空指针异常。
            }
            if (bgmPlayer != null) {
                bgmPlayer.release();//释放旧的 MediaPlayer 对象，防止内存泄露
            }
            bgmPlayer = MediaPlayer.create(context, resId);//根据资源 ID 创建新的 MediaPlayer 实例。
            if (bgmPlayer != null) {
                bgmPlayer.setLooping(true);//设置背景音乐循环播放（通常音游背景音乐需要无限循环）。
                bgmLoaded = true;//标记背景音乐已加载完成，供 playBgm() 等方法判断是否可以播放。
                if (bgmReadyListener != null) {
                    //如果外部注册了监听器（bgmReadyListener），则通过主线程的 Handler 将回调发送到主线程执行。
                    new Handler(android.os.Looper.getMainLooper()).post(() -> bgmReadyListener.onBgmReady());
                }
            }
        });
    }

    /**
     * 启动bgm
     */
    public void playBgm() {
        audioHandler.post(() -> {
            if (bgmLoaded && bgmPlayer != null && !bgmPlayer.isPlaying()) {
                bgmPlayer.start();
            }
        });
    }

    /**
     * 暂停bgm
     */
    public void pauseBgm() {
        audioHandler.post(() -> {
            if (bgmLoaded && bgmPlayer != null && bgmPlayer.isPlaying()) {
                bgmPlayer.pause();
            }
        });
    }

    /**
     * 停止bgm，释放资源
     */
    public void stopBgm() {
        audioHandler.post(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.stop();
                bgmPlayer.release();
                bgmPlayer = null;
                bgmLoaded = false;
            }
        });
    }

    // 四种键型音效播放方法
    public void playTapSound()   { playSound(tapSoundId); }
    public void playHoldSound()  { playSound(holdSoundId); }
    public void playDragSound()  { playSound(dragSoundId); }
    public void playFlickSound() { playSound(flickSoundId); }

    /**
     * 根据id播放音效
     * @param soundId 音效id
     */
    private void playSound(int soundId) {
        if (!seEnabled) return;
        if (!ready) return;
        audioHandler.post(() -> {
            if (soundPool != null && soundId != 0) {
                soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        });
    }
    public void setBgmVolume(float volume) {
        bgmVolume = volume;
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(volume, volume);
        }
    }

    public void setSeVolume(float volume) {
        seVolume = volume;
        // 音效池播放时需单独设置，但因 SoundPool 每次 play 可指定音量，此处暂不实现动态调节，保存后下次生效
    }

    public void setSeEnabled(boolean enabled) {
        seEnabled = enabled;
    }
    /**
     * 释放资源
     */
    public void release() {
        audioHandler.post(() -> {
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
            if (bgmPlayer != null) {
                bgmPlayer.release();
                bgmPlayer = null;
            }
        });
        audioThread.quitSafely();
    }
}