package com.example.eclipsa.game.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.example.eclipsa.R;
import com.example.eclipsa.game.note.Note;
import com.example.eclipsa.game.note.HoldNote;
import com.example.eclipsa.game.score.ScoreSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 音符渲染器
 * 负责绘制背景、特效、所有音符、判定线、UI信息（得分、连击、统计等）
 */
public class NoteRenderer {

    private Paint linePaint;
    private Paint debugPaint;
    private Paint uiPaint;          // 专门用于绘制UI文字的画笔
    private float judgeLineY;
    private int screenWidth;
    private int screenHeight;
    private long travelTime;

    // 四种键型的图片资源
    private Bitmap tapBitmap;
    private Bitmap dragBitmap;
    private Bitmap flickBitmap;
    private Bitmap holdBitmap;
    // 背景
    private Bitmap backgroundBitmap;

    // 特效相关（使用线程安全列表）
    private List<Effect> effects = Collections.synchronizedList(new ArrayList<>());
    // 线程安全的特效列表（因为 addEffect 可能被主线程调用，而 updateEffects 在游戏循环线程调用，需要同步）。
    private List<Bitmap> effectFrames = new ArrayList<>();
    // 存储特效序列帧（effect_0.png ~ effect_15.png）
    private long effectFrameDuration = 50; // 每帧显示时间（毫秒）
    private int effectWidth = 500;   // 特效宽度（像素）
    private int effectHeight = 500;  // 特效高度（像素）

    // 图片尺寸（可根据需要调整）
    private int noteWidth = 500;
    private int noteHeight = 200;
    private int holdWidth = 500;

    // 连击动画相关变量
    private long lastCombo = -1;
    private long comboAnimStartTime = 0;
    private static final long COMBO_ANIM_DURATION = 300; // 动画持续时间（毫秒）

    // 当前 BPM（从谱面动态获取）
    private int currentBpm = 120;

    /**
     * 记录屏幕参数。
     * 初始化画笔、加载音符图片、加载特效序列帧。
     * @param context 传入gameactivity实例
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param judgeLineY 判定线坐标
     * @param travelTime 下落时间
     */
    public NoteRenderer(Context context, int screenWidth, int screenHeight, float judgeLineY, long travelTime) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.judgeLineY = judgeLineY;
        this.travelTime = travelTime;
        initPaints();
        loadBitmaps(context);
        loadEffectFrames(context);
    }

    /**
     * 设置当前 BPM（用于显示）
     * @param bpm 节拍数
     */
    public void setBpm(int bpm) {
        this.currentBpm = bpm;
    }

    /**
     * 初始化画笔
     */
    private void initPaints() {
        linePaint = new Paint();
        debugPaint = new Paint();
        uiPaint = new Paint();           // 新增UI画笔
        linePaint.setAntiAlias(true);
        debugPaint.setAntiAlias(true);
        uiPaint.setAntiAlias(true);
    }

    /**添加音符图片
     *
     * 从 res/drawable/ 加载原始图片，然后缩放到指定尺寸（noteWidth x noteHeight）。
     * createScaledBitmap 创建新 Bitmap，原始图片立即 recycle() 释放，避免内存浪费。
     * Hold 图片先缩放到 holdWidth，高度按比例计算（保持原始宽高比），因为后续拉伸身体时会再次拉伸到目标高度，保持宽度不变可减少变形。
     * @param context 见构造方法
     */
    private void loadBitmaps(Context context) {
        try {
            Bitmap originalTap = BitmapFactory.decodeResource(context.getResources(), R.drawable.tap);
            if (originalTap != null) {
                tapBitmap = Bitmap.createScaledBitmap(originalTap, noteWidth, noteHeight, true);
                originalTap.recycle();
            }
            Bitmap originalDrag = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag);
            if (originalDrag != null) {
                dragBitmap = Bitmap.createScaledBitmap(originalDrag, noteWidth, noteHeight, true);
                originalDrag.recycle();
            }
            Bitmap originalFlick = BitmapFactory.decodeResource(context.getResources(), R.drawable.flick);
            if (originalFlick != null) {
                flickBitmap = Bitmap.createScaledBitmap(originalFlick, noteWidth, noteHeight, true);
                originalFlick.recycle();
            }
            Bitmap originalHold = BitmapFactory.decodeResource(context.getResources(), R.drawable.hold);
            if (originalHold != null) {
                int scaledHeight = (int) ((float) originalHold.getHeight() * holdWidth / originalHold.getWidth());
                holdBitmap = Bitmap.createScaledBitmap(originalHold, holdWidth, scaledHeight, true);
                originalHold.recycle();
            }
        } catch (Exception e) {
            Log.e("NoteRenderer", "Failed to load bitmaps", e);
        }
    }

    /**加载特效帧序列
     *
     * 动态构造资源 ID：effect_0, effect_1, …, effect_15。
     * 如果某个资源不存在（resId == 0），停止加载后续帧。
     * 所有帧按顺序存入 effectFrames
     * @param context 见构造方法
     */
    private void loadEffectFrames(Context context) {
        for (int i = 0; i <= 15; i++) {
            int resId = context.getResources().getIdentifier("effect_" + i, "drawable", context.getPackageName());
            if (resId == 0) break;
            try {
                Bitmap frame = BitmapFactory.decodeResource(context.getResources(), resId);
                if (frame != null) {
                    effectFrames.add(frame);
                }
            } catch (Exception e) {
                Log.e("NoteRenderer", "Failed to load effect frame " + i, e);
            }
        }
        if (effectFrames.isEmpty()) {
            Log.w("NoteRenderer", "No effect frames loaded, effect disabled");
        }
    }

    /**
     * 添加特效(触发机制)
     * @param x 命中音符位置
     * @param y 命中位置
     */
    public void addEffect(float x, float y) {
        if (effectFrames.isEmpty()) return;
        effects.add(new Effect(x, y));
    }

    /**
     * ---------------------------------主绘制方法-------------------------------
     *
     * @param canvas 画布
     * @param activeNotes 屏幕上的音符(由controller持有并传入)
     * @param scoreSystem scoresystem实例
     * @param currentTime 游戏时间
     */
    public void draw(Canvas canvas, List<Note> activeNotes, ScoreSystem scoreSystem, long currentTime) {
        // 1. 绘制背景（完全覆盖上一帧）
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        } else {
            // 如果没有背景，用黑色填充（避免透明导致残影）
            canvas.drawColor(Color.BLACK);
        }

        // 2. 先绘制特效（底层）
        updateEffects(canvas);

        // 3. 绘制所有音符
        for (Note note : activeNotes) {
            if (note.type == Note.TYPE_TAP) {
                drawTap(canvas, note);
            } else if (note.type == Note.TYPE_HOLD) {
                drawHold(canvas, (HoldNote) note, currentTime);
            } else if (note.type == Note.TYPE_DRAG) {
                drawDrag(canvas, note);
            } else if (note.type == Note.TYPE_FLICK) {
                drawFlick(canvas, note);
            }
        }

        // 4. 绘制判定线（最上层）
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(5);
        canvas.drawLine(0, judgeLineY, screenWidth, judgeLineY, linePaint);

        // ---------------------------- UI 信息绘制（优化后）----------------------------
        // 使用单独的 uiPaint 绘制，避免干扰 debugPaint

        // 4.1 左上角：得分（大号）和最高连击（小号）
        uiPaint.setColor(Color.parseColor("#FFD700")); // 金色
        uiPaint.setTextSize(56);
        uiPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("SCORE " + scoreSystem.getTotalScore(), 30, 80, uiPaint);

        uiPaint.setColor(Color.parseColor("#AAAAAA"));
        uiPaint.setTextSize(28);
        canvas.drawText("MAX " + scoreSystem.getMaxCombo(), 30, 125, uiPaint);

        // 4.2 右上角：判定统计（紧凑排列） + 连击（放在 Perfect 上方）
        uiPaint.setTextSize(24);
        uiPaint.setTextAlign(Paint.Align.RIGHT);
        int rightMargin = screenWidth - 30;
        int yOffset = 80;

        // 先绘制连击（大号，位于 Perfect 上方）
        int combo = scoreSystem.getCombo();
        long now = System.currentTimeMillis();

        // 检测连击增加，触发动画
        if (combo != lastCombo) {
            lastCombo = combo;
            comboAnimStartTime = now;
        }

        // 计算缩放比例
        float scale = 1.0f;
        if (comboAnimStartTime > 0 && now - comboAnimStartTime < COMBO_ANIM_DURATION) {
            float t = (float)(now - comboAnimStartTime) / COMBO_ANIM_DURATION;
            if (t <= 0.5f) {
                scale = 1.0f + 0.8f * (t / 0.5f);
            } else {
                scale = 1.8f - 0.8f * ((t - 0.5f) / 0.5f);
            }
        }

        int comboColor;
        if (combo >= 50) comboColor = Color.parseColor("#FFD700");
        else if (combo >= 20) comboColor = Color.parseColor("#FFA500");
        else comboColor = Color.WHITE;

        Paint comboPaint = new Paint();
        comboPaint.setAntiAlias(true);
        comboPaint.setColor(comboColor);
        comboPaint.setTextSize(52 * scale);      // 比统计数字稍大
        comboPaint.setTextAlign(Paint.Align.RIGHT);
        comboPaint.setShadowLayer(5, 0, 0, Color.BLACK);
        // 将连击放在 Perfect 统计上方（Y 偏移减少 40 像素）
        canvas.drawText(combo + "x", rightMargin, yOffset - 30, comboPaint);

        // 统计信息（Perfect, Great, Good, Miss）
        uiPaint.setTextSize(24);
        uiPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawText("PERFECT " + scoreSystem.getPerfectCount(), rightMargin, yOffset, uiPaint);
        uiPaint.setColor(Color.parseColor("#66BB6A"));
        canvas.drawText("GREAT " + scoreSystem.getGreatCount(), rightMargin, yOffset + 30, uiPaint);
        uiPaint.setColor(Color.parseColor("#FFA726"));
        canvas.drawText("GOOD " + scoreSystem.getGoodCount(), rightMargin, yOffset + 60, uiPaint);
        uiPaint.setColor(Color.parseColor("#EF5350"));
        canvas.drawText("MISS " + scoreSystem.getMissCount(), rightMargin, yOffset + 90, uiPaint);

        // 4.3 右下角：速度系数 / BPM
        uiPaint.setColor(Color.parseColor("#CCCCCC"));
        uiPaint.setTextSize(20);
        uiPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("SPEED 1.0x", rightMargin, screenHeight - 30, uiPaint);
        canvas.drawText("BPM " + currentBpm, rightMargin, screenHeight - 55, uiPaint);

        // 4.4 左下角：版本号
        uiPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("v1.0", 30, screenHeight - 30, uiPaint);

        // 可选：保留原有的调试信息（如不需要可注释）
        debugPaint.setColor(Color.GREEN);
        debugPaint.setTextSize(20);
        debugPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Notes: " + activeNotes.size(), 50, 210, debugPaint);
        canvas.drawText("Time: " + currentTime + "ms", 50, 240, debugPaint);
        canvas.drawText("TravelTime: " + travelTime + "ms", 50, 270, debugPaint);
    }

    /**
     * -----------更新和绘制特效------------
     *同步块：因为 effects 可能同时在 addEffect（主线程）和 updateEffects（游戏循环线程）中被修改，使用 synchronized 保证线程安全。
     * 每帧计算已播放时间，确定当前应显示哪一帧。
     * 超过总帧数则移除特效。
     * @param canvas
     */
    private void updateEffects(Canvas canvas) {
        long now = System.currentTimeMillis();
        synchronized (effects) {
            //遍历特效列表，使用iterator安全修改
            Iterator<Effect> it = effects.iterator();
            while (it.hasNext()) {
                Effect effect = it.next();//取出当前特效对象
                long elapsed = now - effect.startTime;//计算特效开始了多少ms
                int frameIndex = (int) (elapsed / effectFrameDuration);//根据播放时间计算该显示第几帧
                if (frameIndex >= effectFrames.size()) {
                    it.remove();//动画播放完毕移除
                    continue;
                }
                Bitmap frame = effectFrames.get(frameIndex);//获取第n帧
                if (frame != null) {
                    //计算矩形上边界和左边界
                    int left = (int) (effect.x - effectWidth / 2);
                    int top = (int) (effect.y - effectHeight / 2);
                    //目标区域矩形大小
                    Rect dst = new Rect(left, top, left + effectWidth, top + effectHeight);
                    canvas.drawBitmap(frame, null, dst, null);
                }
            }
        }
    }

    /**
     * 绘制tap
     * @param canvas 画布
     * @param note 音符
     */
    private void drawTap(Canvas canvas, Note note) {
        if (tapBitmap == null) {//降级绘制
            Paint p = new Paint();
            p.setColor(Color.RED);
            canvas.drawCircle(note.x, note.y, 40, p);
            return;
        }
        int left = (int) (note.x - noteWidth / 2);
        int top = (int) (note.y - noteHeight / 2);
        Rect dst = new Rect(left, top, left + noteWidth, top + noteHeight);
        canvas.drawBitmap(tapBitmap, null, dst, null);
    }

    /**
     * 绘制drag
     * @param canvas 画布
     * @param note 音符
     */
    private void drawDrag(Canvas canvas, Note note) {
        if (dragBitmap == null) {//降级绘制
            Paint p = new Paint();
            p.setColor(Color.YELLOW);
            canvas.drawCircle(note.x, note.y, 40, p);
            return;
        }
        int left = (int) (note.x - noteWidth / 2);
        int top = (int) (note.y - noteHeight / 2);
        Rect dst = new Rect(left, top, left + noteWidth, top + noteHeight);
        canvas.drawBitmap(dragBitmap, null, dst, null);
    }

    /**
     * 绘制flick
     * @param canvas 画布
     * @param note 音符
     */
    private void drawFlick(Canvas canvas, Note note) {
        if (flickBitmap == null) {//降级绘制
            Paint p = new Paint();
            p.setColor(Color.MAGENTA);
            canvas.drawCircle(note.x, note.y, 40, p);
            return;
        }
        int left = (int) (note.x - noteWidth / 2);
        int top = (int) (note.y - noteHeight / 2);
        Rect dst = new Rect(left, top, left + noteWidth, top + noteHeight);
        canvas.drawBitmap(flickBitmap, null, dst, null);
    }

    /**
     * 绘制hold
     * @param canvas 画布
     * @param hold holdnote对象
     * @param currentTime 游戏时间
     */
    private void drawHold(Canvas canvas, HoldNote hold, long currentTime) {
        float headY = hold.y;//头部y坐标
        float tailY = hold.getTailY(currentTime);//尾部y坐标
        float topY = tailY;//尾部在上
        float bottomY = headY;//头部在下

        if (holdBitmap != null && bottomY > topY) {
            int left = (int) (hold.x - holdWidth / 2);
            int right = (int) (hold.x + holdWidth / 2);
            int top = (int) topY;
            int bottom = (int) bottomY;
            if (top < 0) top = 0;//防止top超出屏幕
            if (bottom > screenHeight) bottom = screenHeight;//防止底部超出
            if (top < bottom) {
                Rect dstRect = new Rect(left, top, right, bottom);
                Rect srcRect = new Rect(0, 0, holdBitmap.getWidth(), holdBitmap.getHeight());
                canvas.drawBitmap(holdBitmap, srcRect, dstRect, null);
            }
        } else {
            // 降级绘制
            Paint p = new Paint();
            if (!hold.isHeadJudged) p.setColor(Color.CYAN);
            else p.setColor(Color.GREEN);
            canvas.drawCircle(hold.x, headY, 35, p);
            if (bottomY > topY) {
                p.setColor(Color.argb(200, 0, 150, 255));
                canvas.drawRect(hold.x - 15, topY, hold.x + 15, bottomY, p);
            }
            if (!hold.isTailJudged) {
                p.setColor(Color.YELLOW);
                canvas.drawCircle(hold.x, tailY, 25, p);
            }
        }
    }

    /**
     * 背景设置
     * @param bitmap 背景图片
     */
    public void setBackgroundBitmap(Bitmap bitmap) {
        if (this.backgroundBitmap != null && this.backgroundBitmap != bitmap) {
            this.backgroundBitmap.recycle(); // 释放旧图
        }
        this.backgroundBitmap = bitmap;
    }

    /**
     * 内部Effect类
     * 记录特效的中心坐标和创建时间（系统毫秒时间，非游戏时钟）
     */
    private static class Effect {
        float x, y;
        long startTime;
        Effect(float x, float y) {
            this.x = x;
            this.y = y;
            this.startTime = System.currentTimeMillis();
        }
    }
}