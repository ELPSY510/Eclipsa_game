package com.example.eclipsa.game.score;

public class ScoreSystem {

    public static final int PERFECT = 0;
    public static final int GREAT = 1;
    public static final int GOOD = 2;
    public static final int MISS = 3;

    private int totalScore;
    private int combo;
    private int maxCombo;
    private int perfectCount, greatCount, goodCount, missCount;

    public ScoreSystem() {
        reset();
    }

    public void reset() {
        totalScore = 0;
        combo = 0;
        maxCombo = 0;
        perfectCount = 0;
        greatCount = 0;
        goodCount = 0;
        missCount = 0;
    }

    /**
     *
     * @param judge 评级
     * @return 得分
     */
    public int onHit(int judge) {
        int baseScore;
        switch (judge) {
            case PERFECT: baseScore = 100; perfectCount++; break;
            case GREAT:   baseScore = 70;  greatCount++;   break;
            case GOOD:    baseScore = 40;  goodCount++;    break;
            default: return 0;
        }
        int bonus = 1 + combo / 10;
        int gained = baseScore * bonus;
        totalScore += gained;
        combo++;
        if (combo > maxCombo) maxCombo = combo;
        return gained;
    }

    /**
     * drag得分处理，绕开onhit
     * @return 50分
     */
    public int addDragHit() {
        totalScore += 50;
        combo++;
        perfectCount++;
        if (combo > maxCombo) maxCombo = combo;
        return 50;
    }

    /**
     * 处理miss
     */
    public void onMiss() {
        combo = 0;
        missCount++;
    }

    /**
     * 获得判定
     * @param timeDiff 时间差，在judgementsystem中计算获得
     * @return judge评级
     */
    public static int getJudge(long timeDiff) {
        if (timeDiff <= 200) return PERFECT;
        if (timeDiff <= 300) return GREAT;
        if (timeDiff <= 500) return GOOD;
        return MISS;
    }
    //访问器
    public int getTotalScore() { return totalScore; }
    public int getCombo() { return combo; }
    public int getMaxCombo() { return maxCombo; }
    public int getPerfectCount() { return perfectCount; }
    public int getGreatCount() { return greatCount; }
    public int getGoodCount() { return goodCount; }
    public int getMissCount() { return missCount; }
}