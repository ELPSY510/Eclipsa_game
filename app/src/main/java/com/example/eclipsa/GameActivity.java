package com.example.eclipsa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.eclipsa.game.GameView;
import com.example.eclipsa.game.audio.GameAudioManager;
import com.example.eclipsa.game.audio.UIAudioManager;
import com.example.eclipsa.game.renderer.NoteRenderer;

/**
 * 游戏主界面（Activity）
 * 负责：初始化 GameView、音频、加载谱面、处理暂停/设置/重玩/退出
 */
public class GameActivity extends AppCompatActivity {

    // UI组件
    private GameView gameView;              // 自定义游戏视图
    private GameAudioManager gameAudio;     // 游戏内音频管理器
    private String currentChartFileName;    // 当前谱面文件名
    private int screenWidth;                // 屏幕宽度
    private int screenHeight;               // 屏幕高度

    // 设置存储
    private SharedPreferences settingsPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 初始化 SharedPreferences（用于保存设置）
        settingsPrefs = getSharedPreferences("game_settings", MODE_PRIVATE);

        // 获取屏幕尺寸
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        // 初始化 GameView 和音频管理器
        gameView = findViewById(R.id.gameView);
        gameAudio = new GameAudioManager(this);
        gameView.setGameAudio(gameAudio);

        // 停止主界面背景音乐（避免与游戏内音乐冲突）
        UIAudioManager.getInstance(this).stopBgm();

        // 获取从选歌界面传递的谱面文件名
        currentChartFileName = getIntent().getStringExtra("chartFileName");
        if (currentChartFileName == null) {
            currentChartFileName = "tutorial.json"; // 默认谱面
        }

        // 设置左下角歌曲名显示（去掉 .json 后缀）
        TextView tvSongName = findViewById(R.id.tv_song_name);
        tvSongName.setText(currentChartFileName.replace(".json", ""));

        // 暂停按钮点击事件
        ImageButton btnPause = findViewById(R.id.btn_pause);
        btnPause.setOnClickListener(v -> showPauseDialog());

        /**
         * 将加载谱面的任务发布到 UI 线程的消息队列中
         * 确保在 GameView 已经完成布局（onSizeChanged 被调用）之后再执行 loadChart。
         * 避免因屏幕尺寸未确定而出错。
         */
        final String finalFileName = currentChartFileName;
        gameView.post(() -> {
            gameView.loadChart(finalFileName);                 // 加载谱面
            String bgName = gameView.getBgImageResName();      // 获取背景资源名
            setBackgroundForSong(bgName);                      // 设置背景图片
        });

        /**
         * 背景音乐加载完成监听器
         * 加载完成后开始播放音乐并启动游戏（保证音画同步）
         * 异步加载避免 UI 卡顿
         */
        gameAudio.setOnBgmReadyListener(() -> {
            gameAudio.playBgm();                               // 播放背景音乐
            if (gameView != null && !gameView.isRunning()) {
                gameView.startGame();                          // 启动游戏循环
            }
        });
        // 开始异步加载背景音乐（加载完成后触发上述回调）
        gameAudio.loadBgm(this, currentChartFileName);

        // 应用保存的设置（音量等）
        applySettings();
    }

    /**
     * 应用用户保存的设置（背景音乐音量、打击音效音量）
     * 下落速度倍数本次不动态应用，仅保存，下次游戏生效
     */
    private void applySettings() {
        int bgmVolume = settingsPrefs.getInt("bgm_volume", 100);
        gameAudio.setBgmVolume(bgmVolume / 100f);               // 设置背景音乐音量（0.0~1.0）

        int seVolume = settingsPrefs.getInt("se_volume", 100);
        gameAudio.setSeVolume(seVolume / 100f);                 // 设置打击音效音量
        // 下落速度倍数在此处不处理，因为需要在谱面加载时重新计算 travelTime
    }

    /**
     * 显示暂停对话框（继续、设置、重玩、退出）
     * 暂停时冻结游戏逻辑和背景音乐
     */
    private void showPauseDialog() {
        // 暂停游戏逻辑（音符停止移动、时钟暂停）
        gameView.pauseGameLogic();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("游戏暂停");
        builder.setItems(new String[]{"继续", "设置", "重玩", "退出"}, (dialog, which) -> {
            switch (which) {
                case 0: // 继续
                    gameView.resumeGameLogic();
                    break;
                case 1: // 设置
                    showSettingsDialog();
                    break;
                case 2: // 重玩
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                    break;
                case 3: // 退出
                    finish();
                    break;
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> gameView.resumeGameLogic());
        builder.setOnDismissListener(dialog -> gameView.resumeGameLogic());
        builder.show();
    }

    /**
     * 显示设置弹窗（仅包含三项：背景音乐音量、打击音效音量、下落速度倍数）
     */
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载自定义布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(view);
        builder.setTitle("设置");

        // 获取控件
        SeekBar seekBgm = view.findViewById(R.id.seek_bgm_volume);
        SeekBar seekSe = view.findViewById(R.id.seek_se_volume);
        SeekBar seekSpeed = view.findViewById(R.id.seek_speed);

        // 加载当前保存的值
        seekBgm.setProgress(settingsPrefs.getInt("bgm_volume", 100));
        seekSe.setProgress(settingsPrefs.getInt("se_volume", 100));
        int speedProgress = settingsPrefs.getInt("speed_multiplier", 4); // 0~7 对应 0.8~1.5
        seekSpeed.setProgress(speedProgress);

        builder.setPositiveButton("保存", (dialog, which) -> {
            int bgmVol = seekBgm.getProgress();
            int seVol = seekSe.getProgress();
            int speedProg = seekSpeed.getProgress();

            // 保存到 SharedPreferences
            settingsPrefs.edit()
                    .putInt("bgm_volume", bgmVol)
                    .putInt("se_volume", seVol)
                    .putInt("speed_multiplier", speedProg)
                    .apply();

            // 立即应用音量和音效（速度需要重启游戏才能完全生效，此处提示）
            gameAudio.setBgmVolume(bgmVol / 100f);
            gameAudio.setSeVolume(seVol / 100f);
            Toast.makeText(this, "设置已保存，下落速度下次游戏生效", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 根据背景资源名加载并设置游戏背景图片（在子线程中解码，避免阻塞主线程）
     * @param bgResName 背景资源名（如 "bg_tutorial"），若为 null 则使用 default_bg
     */
    private void setBackgroundForSong(String bgResName) {
        if (bgResName == null) bgResName = "default_bg";
        int bgResId = getResources().getIdentifier(bgResName, "drawable", getPackageName());
        if (bgResId == 0) bgResId = R.drawable.default_bg;

        final int finalResId = bgResId;
        new Thread(() -> {
            Bitmap original = BitmapFactory.decodeResource(getResources(), finalResId);
            Bitmap scaled = Bitmap.createScaledBitmap(original, screenWidth, screenHeight, true);
            original.recycle();
            runOnUiThread(() -> {
                NoteRenderer renderer = gameView.getRenderer();
                if (renderer != null) {
                    renderer.setBackgroundBitmap(scaled);
                }
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复游戏（如果尚未运行）
        if (gameView != null && !gameView.isRunning()) {
            gameView.startGame();
        }
        // 恢复背景音乐播放
        if (gameAudio != null) {
            gameAudio.playBgm();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停游戏逻辑（释放资源）
        if (gameView != null) {
            gameView.pauseGame();
        }
        // 暂停背景音乐
        if (gameAudio != null) {
            gameAudio.pauseBgm();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放音频资源
        if (gameAudio != null) {
            gameAudio.stopBgm();
            gameAudio.release();
        }
    }
}