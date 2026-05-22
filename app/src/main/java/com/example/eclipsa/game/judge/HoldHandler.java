package com.example.eclipsa.game.judge;

import com.example.eclipsa.game.note.HoldNote;
import com.example.eclipsa.game.score.ScoreSystem;

public class HoldHandler {

    private ScoreSystem scoreSystem;
    private HoldNote activeHoldNote;//当前按住的hold
    private long goodWindow;

    public HoldHandler(ScoreSystem scoreSystem, long goodWindow) {
        this.scoreSystem = scoreSystem;
        this.goodWindow = goodWindow;
        this.activeHoldNote = null;
    }

    /**
     * 计算时间差，在窗口内则：
     * 标记 isHeadJudged = true、isPressing = true。
     * 记录当前按住的 Hold 到 activeHoldNote。
     * 计算判定等级，调用 scoreSystem.onHit(judge) 加分（头判得分）。
     * 返回 true。
     * @param hold hold
     * @param tapTime 点击时的当前时间
     * @return
     */
    public boolean onHeadJudge(HoldNote hold, long tapTime) {
        long timeDiff = Math.abs(tapTime - hold.judgeTime);
        if (timeDiff <= goodWindow) {
            hold.isHeadJudged = true;
            hold.isPressing = true;
            activeHoldNote = hold;
            int judge = ScoreSystem.getJudge(timeDiff);
            hold.headJudge = judge;
            scoreSystem.onHit(judge);
            return true;
        }
        return false;
    }

    /**
     * 尾判基于 tailJudgeTime（即 headJudgeTime + holdDuration），而不是动态缩短的长度。
     * 在窗口内：尾判成功，加分，标记完成，清除 activeHoldNote。
     * 否则：调用 scoreSystem.onMiss()（连击归零），标记失败。
     * 注意：头判和尾判是独立计分的，头判成功后不会因为尾判失败而撤销头判得分。
     * @param releaseTime 松手时当前时间
     * @return
     */
    public boolean onTailJudge(long releaseTime) {
        if (activeHoldNote == null) return false;
        HoldNote hold = activeHoldNote;
        long timeDiff = Math.abs(releaseTime - hold.tailJudgeTime);
        if (timeDiff <= goodWindow) {
            int judge = ScoreSystem.getJudge(timeDiff);
            hold.tailJudge = judge;
            hold.isTailJudged = true;
            hold.isJudged = true;
            hold.isPressing = false;
            scoreSystem.onHit(judge);
            activeHoldNote = null;
            return true;
        } else {
            scoreSystem.onMiss();
            hold.isJudged = true;
            activeHoldNote = null;
            return false;
        }
    }

    public HoldNote getActiveHoldNote() {
        return activeHoldNote;
    }

    public void clear() {
        activeHoldNote = null;
    }
}