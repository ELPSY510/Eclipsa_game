package com.example.eclipsa.game;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;//管理surfaceview
import android.view.SurfaceView;//可独立线程绘制的视图，适合游戏

import com.example.eclipsa.ResultActivity;
import com.example.eclipsa.game.audio.GameAudioManager;
import com.example.eclipsa.game.controller.GameController;
import com.example.eclipsa.game.renderer.NoteRenderer;
import com.example.eclipsa.chart.ChartLoader;
import com.example.eclipsa.chart.TimingCalculator;
import com.example.eclipsa.game.score.ScoreSystem;
/**
 * 继承 SurfaceView，实现 Runnable接口
 * */
public class GameView extends SurfaceView implements Runnable {

    //holder：管理画布（lockCanvas() / unlockCanvasAndPost()）。
    //
    //gameThread：游戏循环所在的线程。
    //
    //isRunning：控制游戏循环是否继续，volatile 确保多线程可见性。
    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean isRunning = false;

    //controller：游戏逻辑控制器（判定、分数、生成音符等）。
    //
    //renderer：绘制音符和特效。
    //
    //gameAudio：游戏内音频管理器。
    private GameController controller;
    private NoteRenderer renderer;
    private GameAudioManager gameAudio;

    //屏幕宽高，谱面加载标志，当前歌曲文件名（用于结算时传递），暂存的音频管理器。
    private int screenWidth, screenHeight;
    private boolean isChartLoaded = false;
    private String currentSongFileName = "tutorial.json";
    private GameAudioManager pendingGameAudio;

    // 多点触摸状态存储
    private SparseArray<Float> downXMap = new SparseArray<>();
    private SparseArray<Float> downYMap = new SparseArray<>();
    private SparseArray<Long> downTimeMap = new SparseArray<>();

    private String bgImageResName;
    //构造函数，获取surfaceholder
    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
    }
    //当视图大小确定时调用，记录屏幕宽高
    // 这里没有立即加载谱面，而是由 GameActivity 在合适的时机调用 loadChart。
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        screenHeight = h;
    }
    //让外部查询游戏是否运行
    public boolean isRunning() {
        return isRunning;
    }
    //将音频管理器传递给 GameView，
    // 如果控制器已经创建，则立即传递给控制器。
    public void setGameAudio(GameAudioManager gameAudio) {
        this.gameAudio = gameAudio;
        if (controller != null) {
            controller.setGameAudio(gameAudio);
        }
    }
    /**
     * 加载谱面
     * 参数String chartFileName
     * */
    public void loadChart(String chartFileName) {
        //如果屏幕尺寸还未确定（0），则通过 post 延迟到 UI 线程的下一帧再执行，确保尺寸有效。
        //
        //调用 pauseGame() 停止当前正在运行的游戏（如果有），清理旧状态。
        if (screenWidth == 0 || screenHeight == 0) {
            post(() -> loadChart(chartFileName));
            return;
        }
        pauseGame();
        //加载谱面数据，计算 travelTime（音符飞行时间）和判定线位置。
        //
        //GOOD_WINDOW 设为 500ms（较大，方便测试）。
        currentSongFileName = chartFileName;
        com.example.eclipsa.chart.ChartData chartData = ChartLoader.loadFromAssets(getContext(), chartFileName);
        if (chartData != null) {
            this.bgImageResName = chartData.bgImageResName;//背景设置
            TimingCalculator timingCalc = new TimingCalculator(chartData.bpm);
            float beatsToTravel = 4.0f;
            long travelTime = (long) ((beatsToTravel / (chartData.bpm / 60f)) * 1000);
            float judgeLineY = screenHeight * 0.75f;
            final long GOOD_WINDOW = 500;//判定最大窗口
        //创建控制器和渲染器，并将渲染器设置给控制器（用于特效添加）。
            controller = new GameController(chartData, travelTime, judgeLineY,
                    screenWidth, GOOD_WINDOW);
            renderer = new NoteRenderer(getContext(), screenWidth, screenHeight, judgeLineY, travelTime);
            renderer.setBpm(chartData.bpm);
            controller.setNoteRenderer(renderer);
        //将音频管理器传递给控制器（如果已有）
            if (gameAudio != null) {
                controller.setGameAudio(gameAudio);
            } else if (pendingGameAudio != null) {
                controller.setGameAudio(pendingGameAudio);
            }
        //日志
            android.util.Log.d("GameView", "谱面加载成功: " + chartFileName);
        } else {
            android.util.Log.e("GameView", "谱面加载失败: " + chartFileName);
        }

        isChartLoaded = true;
    }
    /**
     * 启动游戏
     * 设置游戏结束监听器
     * 启动游戏控制器
     * */
    public void startGame() {
        //如果控制器还没创建（谱面未加载），延迟 100ms 重试
        if (controller == null) {
            postDelayed(this::startGame, 100);
            return;
        }
        controller.setGameFinishListener(() -> {
            if (gameAudio != null) {
                gameAudio.stopBgm();
            }
            post(() -> {
                Intent intent = new Intent(getContext(), ResultActivity.class);
                ScoreSystem ss = controller.getScoreSystem();
                intent.putExtra("totalScore", ss.getTotalScore());
                intent.putExtra("perfectCount", ss.getPerfectCount());
                intent.putExtra("greatCount", ss.getGreatCount());
                intent.putExtra("goodCount", ss.getGoodCount());
                intent.putExtra("missCount", ss.getMissCount());
                intent.putExtra("maxCombo", ss.getMaxCombo());
                intent.putExtra("songFileName", currentSongFileName);
                pauseGame();
                getContext().startActivity(intent);
            });
        });
        controller.start();
        isRunning = true;
        //Thread 的构造函数可以接收一个 Runnable 参数。当线程启动时，它会调用该 Runnable 对象的 run() 方法。
        //
        //这里把 this（即 GameView 实例）传给 Thread，所以线程启动后会自动执行 GameView 中的 run() 方法，从而进入游戏循环。
        if (gameThread == null || !gameThread.isAlive()) {
            gameThread = new Thread(this);
            gameThread.start();
        }
    }
    /**
     * 停止循环标志，暂停控制器，等待线程结束。
     * */
    public void pauseGame() {
        isRunning = false;
        if (controller != null) controller.pause();
        if (gameThread != null) {
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            gameThread = null;
        }
    }
    /**
     * 暂停游戏逻辑（不停止线程，仅冻结时钟和音乐）
     */
    public void pauseGameLogic() {
        if (controller != null) controller.pause();
        if (gameAudio != null) gameAudio.pauseBgm();
    }

    /**
     * 恢复游戏逻辑
     */
    public void resumeGameLogic() {
        if (controller != null) controller.resume();
        if (gameAudio != null) gameAudio.playBgm();
    }
    /**
     * 每 16ms（约 60fps）执行一次更新和绘制
     * */
    @Override
    public void run() {
        while (isRunning) {
            update();
            draw();
            try { Thread.sleep(16); } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }
    /**
     * 调用控制器的update
     * */
    private void update() {
        if (controller != null) controller.update();
    }
    /**
     * 锁定画布
     * 调用渲染器绘制音符、判定线、特效等
     * 然后提交画布
     * */
    private void draw() {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        if (renderer != null && controller != null) {
            renderer.draw(canvas, controller.getActiveNotes(),
                    controller.getScoreSystem(),
                    controller.getCurrentTime());
        }
        holder.unlockCanvasAndPost(canvas);
    }
    /**
     * 获取当前连击
     * */
    public int getCurrentCombo() {
        if (controller != null) {
            return controller.getScoreSystem().getCombo();
        }
        return 0;
    }

    /**
     * 获取背景图片
     * @return image_bgName
     */
    public String getBgImageResName() {
        return bgImageResName;
    }

    /**
     *
     */
    public NoteRenderer getRenderer() {
        return renderer;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (controller == null) return true;
        long currentTime = controller.getCurrentTime();
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                float x = event.getX(index);
                float y = event.getY(index);
                downXMap.put(pointerId, x);
                downYMap.put(pointerId, y);
                downTimeMap.put(pointerId, currentTime);
                // 全屏判定（不限制Y区域）
                controller.onTouchDown(pointerId, x, y, currentTime);
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    float mx = event.getX(i);
                    float my = event.getY(i);
                    controller.onTouchMove(id, mx, my, currentTime);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                float upX = event.getX(index);
                float upY = event.getY(index);
                Long downTime = downTimeMap.get(pointerId);
                if (downTime != null) {
                    float downX = downXMap.get(pointerId);
                    float downY = downYMap.get(pointerId);
                    controller.onTouchUp(pointerId, downX, downY, upX, upY, downTime, currentTime);
                    downXMap.remove(pointerId);
                    downYMap.remove(pointerId);
                    downTimeMap.remove(pointerId);
                }
                break;
        }
        return true;
    }
}