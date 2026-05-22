package com.example.eclipsa.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 全屏彩虹扫描渐变背景
 * 使用 SweepGradient 围绕中心点旋转，产生炫彩效果
 */
public class StreamerView extends View {
    private Paint paint;
    private int width, height;
    private float angle = 0f;          // 当前旋转角度
    private ValueAnimator animator;
    private SweepGradient gradient;

    private final int[] rainbowColors = {
            Color.RED, Color.parseColor("#FF7F00"), Color.YELLOW,
            Color.GREEN, Color.BLUE, Color.parseColor("#4B0082"),
            Color.parseColor("#9400D3"), Color.RED  // 首尾相同，无缝衔接
    };
    private final float[] positions = {
            0f, 0.16f, 0.33f, 0.5f, 0.66f, 0.83f, 1f
    };

    public StreamerView(Context context) {
        this(context, null);
    }

    public StreamerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        startAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        updateGradient();
    }

    private void updateGradient() {
        if (width == 0 || height == 0) return;
        float centerX = width / 2f;
        float centerY = height / 2f;
        // 创建一个旋转了 angle 的 SweepGradient
        // 由于 SweepGradient 本身不支持旋转，但我们可以通过旋转画布实现，或者直接使用 SweepGradient 默认方向并旋转画布。
        // 更简单：在 onDraw 中旋转 Canvas，但那样会影响整个绘制。为了只旋转渐变，可以借助 Matrix，但较复杂。
        // 简单方案：直接使用 SweepGradient，不旋转，配合颜色数组循环实现颜色“流动”。但 SweepGradient 本身不支持动画，需要旋转画布。
        // 为了性能，我们直接绘制时旋转 Canvas，这样整个视图的绘制会旋转，但因为它是背景，旋转无伤大雅。
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (width == 0 || height == 0) return;
        float centerX = width / 2f;
        float centerY = height / 2f;
        canvas.save();
        canvas.rotate(angle, centerX, centerY);
        // 创建 SweepGradient，起始角度受旋转影响，但角度参数是固定的，实际上 SweepGradient 是从0度开始顺时针扫描。
        // 通过旋转画布，实现了渐变的旋转效果。
        SweepGradient sweepGradient = new SweepGradient(centerX, centerY, rainbowColors, positions);
        paint.setShader(sweepGradient);
        canvas.drawRect(0, 0, width, height, paint);
        canvas.restore();
    }

    private void startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(10000);  // 10秒旋转一圈
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}