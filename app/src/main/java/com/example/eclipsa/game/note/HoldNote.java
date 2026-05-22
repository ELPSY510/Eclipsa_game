package com.example.eclipsa.game.note;

/**
 * 作为note的子类
 * 定义hold属性以及对应行为
 */
public class HoldNote extends Note {

    public boolean isHeadJudged;    //是否头判
    public boolean isTailJudged;     //是否尾判
    public boolean isPressing;       //是否按住
    public int headJudge;            //头判等级
    public int tailJudge;            //尾判等级
    public long tailJudgeTime;       // 尾部判定时间
    public float fullLength;         // 头部到尾部的总长度（像素）

    /**
     * 构造方法
     * @param x 音符x坐标
     * @param endY 终止坐标
     * @param headJudgeTime 头判时间
     * @param travelTime    下落时间
     * @param track    轨道
     * @param holdDuration  应当按住的时间
     */
    public HoldNote(float x, float endY, long headJudgeTime, long travelTime,
                    int track, long holdDuration) {
        super(x, endY, headJudgeTime, travelTime, track, TYPE_HOLD);
        this.tailJudgeTime = headJudgeTime + holdDuration;
        this.isHeadJudged = false;
        this.isTailJudged = false;
        this.isPressing = false;
        this.fullLength = (endY - startY) * ((float) holdDuration / travelTime);
    }

    /**
     * 重写updatey方法
     * @param currentTime 游戏时间
     */
    @Override
    public void updateY(long currentTime) {
        if (!isHeadJudged) {
            // 阶段1：未按下，整体下落
            long remainingTime = judgeTime - currentTime;
            float progress = (remainingTime <= 0) ? 1.0f : 1.0f - (float) remainingTime / travelTime;
            y = startY + progress * (endY - startY);
        } else {
            // 阶段2：已按下，头部固定在判定线
            y = endY;
        }
    }

    /**
     *
     * @param currentTime 游戏时间
     * @return 尾部y坐标
     */
    public float getTailY(long currentTime) {
        if (!isHeadJudged) {
            // 未按下时，尾部跟随头部，距离头部 fullLength（上方）
            return y - fullLength;
        } else {
            // 按下后，尾部从 y - fullLength 向 y 匀速移动
            float progress;
            if (currentTime >= tailJudgeTime) {
                progress = 1.0f;
            } else if (currentTime <= judgeTime) {
                progress = 0f;
            } else {
                progress = (float)(currentTime - judgeTime) / (tailJudgeTime - judgeTime);
            }
            return y - fullLength * (1 - progress);
        }
    }

    @Override
    public boolean shouldRemove(long currentTime, long goodWindow) {
        if (isTailJudged) return true;
        // 头判未发生且超过判定窗口
        if (!isHeadJudged && currentTime > judgeTime + goodWindow) return true;
        // 头判已发生但尾判未发生且超过尾部判定窗口
        if (isHeadJudged && !isTailJudged && currentTime > tailJudgeTime + goodWindow) return true;
        return false;
    }
}