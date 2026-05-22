package com.example.eclipsa;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.eclipsa.game.audio.UIAudioManager;

/**
 * 主菜单界面
 * 功能：
 * - 大圆心跳动画（属性动画无限缩放）
 * - 点击大圆后向左平移，右侧弹出三个按钮（开始、设置、退出）
 * - 开始按钮 → 选歌界面
 * - 设置按钮 → 弹出设置对话框（调节音量、速度）
 * - 退出按钮 → 关闭应用
 * - 底部显示歌曲总数和最高分
 */
public class MainActivity extends AppCompatActivity {

    // ---------- UI 组件 ----------
    private ImageView circleLogo;          // 中央大圆
    private LinearLayout buttonContainer;  // 右侧按钮容器（开始、设置、退出）
    private TextView tvSongCount;          // 歌曲总数显示
    private TextView tvBestScore;          // 最高分显示
    private TextView tvVersion;            // 版本号显示（布局中需存在）

    private boolean isExpanded = false;    // 菜单是否已展开（控制平移/收起状态）
    private Animation heartbeatAnimation;  // 心跳动画（补间动画，已弃用，改用属性动画，但保留变量以备扩展）

    // 设置存储
    private SharedPreferences settingsPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 SharedPreferences（用于保存用户设置）
        settingsPrefs = getSharedPreferences("game_settings", MODE_PRIVATE);

        // 初始化视图组件
        circleLogo = findViewById(R.id.circle_logo);
        buttonContainer = findViewById(R.id.button_container);
        tvSongCount = findViewById(R.id.tv_song_count);
        tvBestScore = findViewById(R.id.tv_best_score);
        tvVersion = findViewById(R.id.tv_version);

        // 更新动态数据（歌曲总数、最高分）
        updateSongCount();
        updateBestScore();

        // 获取弹出按钮
        Button btnStart = findViewById(R.id.btn_start);
        Button btnSettings = findViewById(R.id.btn_settings);
        Button btnExit = findViewById(R.id.btn_exit);

        // 启动大圆心跳动画（使用属性动画，无限循环缩放）
        startHeartbeatAnimation();

        // 大圆点击事件：展开或收起菜单（动画不停止，仅平移）
        circleLogo.setOnClickListener(v -> {
            if (!isExpanded) {
                expandMenu();   // 展开：大圆左移，按钮淡入滑入
            } else {
                collapseMenu(); // 收起：大圆右移，按钮淡出滑出
            }
        });

        // 开始游戏按钮
        btnStart.setOnClickListener(v -> {
            UIAudioManager.getInstance(this).playClick(); // 播放点击音效
            Intent intent = new Intent(MainActivity.this, SongSelectActivity.class);
            startActivity(intent);
        });

        // 设置按钮：弹出设置对话框
        btnSettings.setOnClickListener(v -> {
            UIAudioManager.getInstance(this).playClick();
            showSettingsDialog(); // 显示设置弹窗
        });

        // 退出应用
        btnExit.setOnClickListener(v -> {
            UIAudioManager.getInstance(this).playClick();
            finish();  // 关闭主界面，应用退出
        });
    }

    /**
     * 启动大圆的心跳动画（属性动画：X轴和Y轴同时缩放，无限循环）
     * 使用 ObjectAnimator，比补间动画更流畅且与平移动画兼容。
     */
    private void startHeartbeatAnimation() {
        // X轴缩放动画（1.0 → 1.08 → 1.0，周期800ms）
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(circleLogo, "scaleX", 1.0f, 1.08f, 1.0f);
        scaleX.setDuration(800);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.RESTART);
        scaleX.start();

        // Y轴缩放动画（同上）
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(circleLogo, "scaleY", 1.0f, 1.08f, 1.0f);
        scaleY.setDuration(800);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.RESTART);
        scaleY.start();
    }

    /**
     * 展开菜单：大圆向左平移，右侧按钮淡入并滑入
     */
    private void expandMenu() {
        // 大圆平移动画（向左移动200像素）
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(circleLogo, "translationX", -200f);
        translateAnim.setDuration(300);
        translateAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        translateAnim.start();

        // 显示按钮容器，设置初始透明且偏移100像素（右侧）
        buttonContainer.setVisibility(View.VISIBLE);
        buttonContainer.setAlpha(0f);
        buttonContainer.setTranslationX(100f);

        // 按钮淡入并滑入（透明度1，偏移0）
        buttonContainer.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        isExpanded = true;
    }

    /**
     * 收起菜单：大圆移回原位，右侧按钮淡出并滑出
     */
    private void collapseMenu() {
        // 大圆移回原位（平移距离0）
        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(circleLogo, "translationX", 0f);
        translateAnim.setDuration(300);
        translateAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        translateAnim.start();

        // 按钮淡出并滑出（透明度0，偏移100），动画结束后隐藏容器
        buttonContainer.animate()
                .alpha(0f)
                .translationX(100f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> buttonContainer.setVisibility(View.INVISIBLE))
                .start();

        isExpanded = false;
    }

    /**
     * 显示设置弹窗（自定义布局 dialog_settings.xml）
     * 包含三个 SeekBar：
     * - 背景音乐音量（0-100）
     * - 打击音效音量（0-100）
     * - 下落速度倍数（0~7 对应 0.8~1.5）
     * 保存后立即应用背景音乐音量，其余提示下次游戏生效。
     */
    private void showSettingsDialog() {
        // 加载自定义布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        SeekBar seekBgm = view.findViewById(R.id.seek_bgm_volume);
        SeekBar seekSe = view.findViewById(R.id.seek_se_volume);
        SeekBar seekSpeed = view.findViewById(R.id.seek_speed);

        // 从 SharedPreferences 读取当前值（若无则使用默认值）
        seekBgm.setProgress(settingsPrefs.getInt("bgm_volume", 100));
        seekSe.setProgress(settingsPrefs.getInt("se_volume", 100));
        int speedProgress = settingsPrefs.getInt("speed_multiplier", 4); // 4对应1.2x
        seekSpeed.setProgress(speedProgress);

        // 构建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置");
        builder.setView(view);
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

            // 立即应用背景音乐音量（主界面）
            UIAudioManager.getInstance(this).setBgmVolume(bgmVol / 100f);

            // 提示：打击音效音量和下落速度将在下次游戏生效
            Toast.makeText(this, "设置已保存\n打击音效音量和下落速度下次游戏生效", Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 更新歌曲总数（从 assets/charts/ 目录统计 .json 文件数量）
     */
    private void updateSongCount() {
        try {
            String[] files = getAssets().list("charts");
            int count = (files == null) ? 0 : files.length;
            tvSongCount.setText("歌曲总数: " + count);
        } catch (Exception e) {
            tvSongCount.setText("歌曲总数: 0");
        }
    }

    /**
     * 更新最高分（从 SharedPreferences 读取，示例使用 tutorial.json 的最高分）
     */
    private void updateBestScore() {
        int best = getSharedPreferences("best_scores", MODE_PRIVATE)
                .getInt("tutorial.json", 0);
        tvBestScore.setText("最高分: " + best);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIAudioManager uiAudio = UIAudioManager.getInstance(this);
        uiAudio.startBgm(); // 开始播放主界面背景音乐

        // 应用保存的背景音乐音量
        int bgmVol = settingsPrefs.getInt("bgm_volume", 100);
        uiAudio.setBgmVolume(bgmVol / 100f);

        // 如果动画意外丢失且菜单未展开，重新启动心跳（属性动画不会丢失，但为防止异常保留此检查）
        if (circleLogo.getAnimation() == null && !isExpanded) {
            startHeartbeatAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停背景音乐（节省资源，避免与其他界面冲突）
        UIAudioManager.getInstance(this).stopBgm();
    }
}