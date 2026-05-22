package com.example.eclipsa.chart;

import java.util.List;

/**
 * 数据结构，只存数据
 */
public class ChartData {
    public String songName;
    public int bpm;
    public int offset;
    public long endTimeMs;
    public String bgImageResName;   // 背景图片资源名（不含扩展名
    public int difficulty;      // 新增：难度 1-30
    public List<NoteData> notes;

    /**
     * 内部类，用来存note数据
     */
    public static class NoteData {
        public int tick;
        public int track;
        public String type;
        public Integer duration;
        public Integer direction;

        /**
         * 构造方法
         * @param tick 时间
         * @param track 轨道
         * @param type 键型
         * @param duration hold需要的持续时间
         * @param direction flick需要的滑向
         */
        public NoteData(int tick, int track, String type, Integer duration, Integer direction) {
            this.tick = tick;
            this.track = track;
            this.type = type;
            this.duration = duration;
            this.direction = direction;
        }
    }
}
