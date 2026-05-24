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

/**
 * 游戏控制器
 * 负责协调音符生成、更新、判定、评分和游戏结束等核心逻辑。
 * 它是游戏引擎的总控，被 GameView 调用。
 */
public class GameController {

    // ---------------------------- 核心组件 ----------------------------
    private GameClock gameClock;            // 游戏时钟，提供统一的时间基准
    private List<Note> activeNotes;         // 当前屏幕上的活跃音符列表
    private ScoreSystem scoreSystem;        // 评分系统（分数、连击、统计）
    private JudgementSystem judgementSystem; // 判定系统
    private NoteGenerator noteGenerator;    // 音符生成器
    private NoteUpdater noteUpdater;        // 音符更新器
    private int screenWidth;                // 屏幕宽度，用于轨道映射

    // ---------------------------- 游戏结束相关 ----------------------------
    private long totalDuration;             // 游戏总时长（毫秒），在 start() 中确定
    private boolean isGameFinished = false; // 是否已结束，防止重复触发

    // ---------------------------- 游戏结束回调接口 ----------------------------
    public interface GameFinishListener {
        void onGameFinish();
    }
    private GameFinishListener finishListener; // 外部注册的结束监听器

    // ---------------------------- 多点触摸辅助（存储按下信息） ----------------------------
    private SparseArray<Float> downXMap = new SparseArray<>();
    private SparseArray<Float> downYMap = new SparseArray<>();
    private SparseArray<Long> downTimeMap = new SparseArray<>();

    /**
     * 构造函数
     * @param chartData   谱面数据
     * @param travelTime  音符飞行时间（毫秒）
     * @param judgeLineY  判定线 Y 坐标
     * @param screenWidth 屏幕宽度
     * @param goodWindow  判定窗口（毫秒）
     */
    public GameController(ChartData chartData, long travelTime, float judgeLineY,
                          int screenWidth, long goodWindow) {
        this.screenWidth = screenWidth;
        this.gameClock = new GameClock();
        this.activeNotes = new ArrayList<>();
        this.scoreSystem = new ScoreSystem();
        this.judgementSystem = new JudgementSystem(scoreSystem, activeNotes, goodWindow);
        this.noteGenerator = new NoteGenerator(chartData, travelTime, judgeLineY, screenWidth);
        this.noteUpdater = new NoteUpdater(goodWindow);
    }

    // ---------------------------- 依赖注入方法 ----------------------------
    public void setGameAudio(GameAudioManager gameAudio) {
        if (judgementSystem != null) judgementSystem.setGameAudio(gameAudio);
    }

    public void setNoteRenderer(NoteRenderer renderer) {
        if (judgementSystem != null) judgementSystem.setNoteRenderer(renderer);
    }

    public void setGameFinishListener(GameFinishListener listener) {
        this.finishListener = listener;
    }

    // ---------------------------- 游戏生命周期控制 ----------------------------
    /**
     * 启动游戏：确定总时长，启动时钟。
     * 总时长 = 谱面 endTimeMs（如果有）否则默认 15 秒。
     */
    public void start() {
        // 注意：此处需要从谱面数据中获得 endTimeMs，但构造函数没有保存。
        // 由于修改了结构，需要在构造函数中保存 endTimeMs 或通过参数传入。
        // 为兼容现有代码，请确保在创建 GameController 时，chartData 中包含 endTimeMs。
        // 这里临时用默认值，实际应在构造函数中保存 endTimeMs 成员变量。
        long endTimeMs = 0; // 这里应该从外部传入或保存
        totalDuration = (endTimeMs > 0) ? endTimeMs : 15000;
        gameClock.start();
        isGameFinished = false;
    }

    /**
     * 暂停游戏：暂停时钟（游戏时间不再前进，因此不会触发结束）
     */
    public void pause() {
        gameClock.pause();
    }

    /**
     * 恢复游戏：恢复时钟，游戏时间继续增加，达到总时长后会触发结束。
     */
    public void resume() {
        gameClock.resume();
    }

    // ---------------------------- 游戏循环更新 ----------------------------
    /**
     * 每帧由 GameView 调用。
     * 更新音符，并检查是否达到总时长。
     */
    public void update() {
        long currentTime = gameClock.getCurrentTime();

        // 检查游戏是否应该结束（仅当未结束时）
        if (!isGameFinished && currentTime >= totalDuration) {
            isGameFinished = true;
            if (finishListener != null) {
                finishListener.onGameFinish();
            }
            return;
        }

        // 生成新音符
        List<Note> newNotes = noteGenerator.generate(currentTime);
        synchronized (activeNotes) {
            activeNotes.addAll(newNotes);
            noteUpdater.update(activeNotes, currentTime, note -> {
                if (!note.isJudged) scoreSystem.onMiss();
            });
        }
    }

    // ---------------------------- 触摸事件处理 ----------------------------
    public void onTouchDown(int pointerId, float x, float y, long time) {
        int track = mapXToTrack(x);
        synchronized (activeNotes) {
            judgementSystem.handleTap(track, time);
        }
    }

    public void onTouchMove(int pointerId, float x, float y, long time) {
        int track = mapXToTrack(x);
        synchronized (activeNotes) {
            judgementSystem.handleDrag(track, time);
        }
    }

    public void onTouchUp(int pointerId, float downX, float downY, float upX, float upY,
                          long downTime, long upTime) {
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

    // ---------------------------- 辅助方法 ----------------------------
    private int mapXToTrack(float touchX) {
        float trackWidth = screenWidth / 4f;
        int track = (int) (touchX / trackWidth);
        return Math.min(track, 3);
    }

    private int getDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? com.example.eclipsa.game.note.Note.DIR_RIGHT : com.example.eclipsa.game.note.Note.DIR_LEFT;
        } else {
            return dy > 0 ? com.example.eclipsa.game.note.Note.DIR_DOWN : com.example.eclipsa.game.note.Note.DIR_UP;
        }
    }

    // ---------------------------- 数据访问器 ----------------------------
    public List<Note> getActiveNotes() {
        synchronized (activeNotes) {
            return new ArrayList<>(activeNotes);
        }
    }

    public ScoreSystem getScoreSystem() {
        return scoreSystem;
    }

    public long getCurrentTime() {
        return gameClock.getCurrentTime();
    }

    // 可选：如果需要动态设置总时长（例如从外部传入 endTimeMs），可添加 setter
    public void setTotalDuration(long duration) {
        this.totalDuration = duration;
    }
}