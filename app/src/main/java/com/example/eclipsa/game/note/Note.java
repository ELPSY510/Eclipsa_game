package com.example.eclipsa.game.note;

/**
 * 定义tap hold filck drag 以及其对应的行为
 * 由于drag flick 只有判定方式与tap不同，所以不用写子类
 */
public class Note {
    //定义四种音符类型的常量，方便在代码中识别音符种类（例如 note.type == Note.TYPE_TAP）。
    //
    //这些值在 Note 构造函数中通过参数传入，也会在 JudgementSystem 中用于过滤特定类型的音符
    public static final int TYPE_TAP = 0;
    public static final int TYPE_HOLD = 1;
    public static final int TYPE_DRAG = 2;
    public static final int TYPE_FLICK = 3;
    //定义四个滑动方向常量，用于 Flick 音符。谱面中的 "direction" 字段会映射到这些值，判定时检查玩家的滑动方向是否匹配
    public static final int DIR_UP = 0;
    public static final int DIR_DOWN = 1;
    public static final int DIR_LEFT = 2;
    public static final int DIR_RIGHT = 3;

    public int type;//音符类型
    public int track;//轨道
    public float x;//音符x坐标
    public float y;//音符y坐标
    public float startY;//起始y坐标
    public float endY;//终止y坐标
    public long judgeTime;//判定时间
    public long travelTime;//下落时间
    public boolean isJudged;//是否被判定
    public int direction;//方向
    //简化构造方法
    public Note(float x, float endY, long judgeTime, long travelTime, int track, int type) {
        this(x, endY, judgeTime, travelTime, track, type, DIR_UP);
    }
    //构造方法
    public Note(float x, float endY, long judgeTime, long travelTime, int track, int type, int direction) {
        this.x = x;
        this.startY = 0;
        this.endY = endY;
        this.y = startY;
        this.judgeTime = judgeTime;
        this.travelTime = travelTime;
        this.track = track;
        this.type = type;
        this.isJudged = false;
        this.direction = direction;
    }

    /**
     * ---------核心移动逻辑-------
     * 剩余时间=判定时间-当前时间
     * 下落进度=剩余时间/下落时间 (1代表未下落，0代表下落完)
     *  y=endy -进度*总长度
     * @param currentTime 游戏时间
     */
    public void updateY(long currentTime) {
        long remainingTime = judgeTime - currentTime;
        if (remainingTime <= 0) {
            y = endY;
        } else if (remainingTime >= travelTime) {
            y = startY;
        } else {
            float progress = (float) remainingTime / travelTime;
            y = endY - progress * (endY - startY);
        }
    }

    /**
     * ------------------超时判断------------------
     * 判断音符是否应该从活跃列表中移除
     * @param currentTime 游戏时间
     * @param goodWindow 判定窗口
     * @return currentTime>judgeTime +goodWindow?true :false
     */
    public boolean shouldRemove(long currentTime, long goodWindow) {
        if (isJudged) return true;
        return currentTime > judgeTime + goodWindow;
    }

    /**
     *-------------命中标记-----------------
     * 当玩家命中音符时调用，标记为已判定，并返回点击时间与判定时间的绝对差值
     * @param tapTime 点击时间
     * @return difftime 时间差，用于计算判定等级
     */
    public long hit(long tapTime) {
        this.isJudged = true;
        return Math.abs(tapTime - this.judgeTime);
    }
}