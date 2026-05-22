package com.example.eclipsa.game.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;

import com.example.eclipsa.game.GameClock;
import com.example.eclipsa.game.generator.NoteGenerator;
import com.example.eclipsa.game.updater.NoteUpdater;
import com.example.eclipsa.game.judge.JudgementSystem;
import com.example.eclipsa.game.score.ScoreSystem;
import com.example.eclipsa.game.note.Note;
import com.example.eclipsa.game.audio.GameAudioManager;
import com.example.eclipsa.game.renderer.NoteRenderer;
import com.example.eclipsa.chart.ChartData;

import java.util.ArrayList;
import java.util.List;

public class GameController {

    private GameClock gameClock;
    private List<Note> activeNotes;//当前屏幕上的音符
    private ScoreSystem scoreSystem;
    private JudgementSystem judgementSystem;
    private NoteGenerator noteGenerator;
    private NoteUpdater noteUpdater;
    private int screenWidth;

    private long endTimeMs;//指定谱面结束时间
    //用于主线程延迟结束（当时间到后，通过 Handler 在主线程回调 GameFinishListener）
    private Handler handler;
    private Runnable finishRunnable;

    //游戏结束回调接口，在gameview中注册
    public interface GameFinishListener {
        void onGameFinish();
    }
    private GameFinishListener finishListener;

    // 多点触摸辅助
    private SparseArray<Float> downXMap = new SparseArray<>();
    private SparseArray<Float> downYMap = new SparseArray<>();
    private SparseArray<Long> downTimeMap = new SparseArray<>();
    /**
     * 初始化各个子模块，并将必要的引用传递下去（例如 activeNotes 列表传递给 JudgementSystem 和 NoteUpdater，因为它们需要操作该列表）。
     * goodWindow 是判定窗口（例如 150ms），从 GameView 传入。
     * travelTime 是音符从顶部飞到判定线的时间，由 BPM 计算得出。
     * endTimeMs 从谱面数据中获取，用于游戏结束条件。
     * 参数 (谱面数据,下落时间,判定线位置,屏幕宽度,判定框)
     * */
    public GameController(ChartData chartData, long travelTime, float judgeLineY,
                          int screenWidth, long goodWindow) {
        this.screenWidth = screenWidth;
        this.gameClock = new GameClock();
        this.activeNotes = new ArrayList<>();
        this.scoreSystem = new ScoreSystem();
        this.judgementSystem = new JudgementSystem(scoreSystem, activeNotes, goodWindow);
        this.noteGenerator = new NoteGenerator(chartData, travelTime, judgeLineY, screenWidth);
        this.noteUpdater = new NoteUpdater(goodWindow);
        this.endTimeMs = chartData.endTimeMs;
        this.handler = new Handler(Looper.getMainLooper());
    }
    //公共接口方法
    //设置依赖
    //音频管理器和渲染器传递给 JudgementSystem，以便在判定命中时播放音效和添加特效。
    public void setGameAudio(GameAudioManager gameAudio) {
        if (judgementSystem != null) {
            judgementSystem.setGameAudio(gameAudio);
        }
    }
    public void setNoteRenderer(NoteRenderer renderer) {
        if (judgementSystem != null) {
            judgementSystem.setNoteRenderer(renderer);
        }
    }
    //设置游戏结束监听器方法gameview中使用
    public void setGameFinishListener(GameFinishListener listener) {
        this.finishListener = listener;
    }

    /**
     * 开启游戏时钟
     * 计算结算时间
     * 延迟给主线程一个结束游戏的任务
     * */
    public void start() {
        gameClock.start();
        long duration = (endTimeMs > 0) ? endTimeMs : 15000;
        finishRunnable = () -> {
            if (finishListener != null) finishListener.onGameFinish();
        };
        handler.postDelayed(finishRunnable, duration);
    }

    /**
     * 暂停时钟，移除finishrunnable任务
     */
    public void pause() {
        gameClock.pause();
        if (handler != null && finishRunnable != null) {
            handler.removeCallbacks(finishRunnable);
        }
    }

    /**
     * 每帧（由 GameView 的游戏循环线程调用）执行。
     * 先调用 noteGenerator.generate() 生成应该出现的音符（基于当前时间）。
     * 关键线程安全：使用 synchronized (activeNotes) 锁保护对 activeNotes 的所有操作，避免与触摸事件并发修改导致崩溃。
     * 将新生成的音符添加到活跃列表，然后调用 noteUpdater.update() 更新所有音符位置并移除超时音符（Miss 回调）。
     */

    public void update() {
        long currentTime = gameClock.getCurrentTime();
        List<Note> newNotes = noteGenerator.generate(currentTime);
        synchronized (activeNotes) {
            activeNotes.addAll(newNotes);
            noteUpdater.update(activeNotes, currentTime, note -> {
                if (!note.isJudged) scoreSystem.onMiss();//注意这里创建了一个监听器实例
            });
        }
    }

    /**
     * 根据 X 坐标计算轨道（0-3）。
     * 在同步锁中调用 judgementSystem.handleTap()，处理 Tap 音符或 Hold 头判。
     * @param pointerId
     * @param x
     * @param y
     * @param time
     */
    public void onTouchDown(int pointerId, float x, float y, long time) {
        int track = mapXToTrack(x);
        synchronized (activeNotes) {
            judgementSystem.handleTap(track, time);
        }
    }

    /**
     * 移动事件：调用 handleDrag 用于 Drag 音符命中
     * @param pointerId
     * @param x
     * @param y
     * @param time
     */
    public void onTouchMove(int pointerId, float x, float y, long time) {
        int track = mapXToTrack(x);
        synchronized (activeNotes) {
            judgementSystem.handleDrag(track, time);
        }
    }

    /**
     *抬起事件：计算滑动距离，如果超过 50 像素，则视为 Flick 操作，判定方向并调用 handleFlick。
     * 无论是否滑动，如果有正在按住的 Hold 音符，调用 handleHoldRelease 处理尾判。
     * @param pointerId
     * @param downX
     * @param downY
     * @param upX
     * @param upY
     * @param downTime
     * @param upTime
     */
    public void onTouchUp(int pointerId, float downX, float downY, float upX, float upY, long downTime, long upTime) {
        float dx = upX - downX;
        float dy = upY - downY;
        if (Math.hypot(dx, dy) > 50) {
            int direction = getDirection(dx, dy);
            int track = mapXToTrack(upX);
            synchronized (activeNotes) {
                judgementSystem.handleFlick(track, direction, upTime);
            }
        }
        if (judgementSystem.hasActiveHold()) {
            synchronized (activeNotes) {
                judgementSystem.handleHoldRelease(upTime);
            }
        }
    }

    /**
     * 将屏幕 X 坐标映射到 0-3 轨道。
     * @param touchX
     * @return
     */
    private int mapXToTrack(float touchX) {
        float trackWidth = screenWidth / 4f;
        int track = (int) (touchX / trackWidth);
        return Math.min(track, 3);
    }

    /**
     * 根据滑动向量确定方向（上下左右）
     * @param dx
     * @param dy
     * @return
     */
    private int getDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? com.example.eclipsa.game.note.Note.DIR_RIGHT : com.example.eclipsa.game.note.Note.DIR_LEFT;
        } else {
            return dy > 0 ? com.example.eclipsa.game.note.Note.DIR_DOWN : com.example.eclipsa.game.note.Note.DIR_UP;
        }
    }
    public void resume() {
        gameClock.resume();
    }

    /**
     *成员方法/访问器
     * @return
     */
    public List<Note> getActiveNotes() {
        synchronized (activeNotes) {
            return new ArrayList<>(activeNotes);
        }
    }
    public ScoreSystem getScoreSystem() { return scoreSystem; }
    public long getCurrentTime() { return gameClock.getCurrentTime(); }
}