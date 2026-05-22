package com.example.eclipsa.chart;

/**
 *
 * 提供tick转换为ms的方法
 */
public class TimingCalculator {
    private int bpm;
    private float msPerTick;
    //构造函数
    public TimingCalculator(int bpm) {
        this.bpm = bpm;
        this.msPerTick = (60000f / bpm) / 480f;
    }

    public long tickToMs(int tick) {
        return (long)(tick * msPerTick);
    }
    //提供设置bpm的方法
    public void setBpm(int bpm) {
        this.bpm = bpm;
        this.msPerTick = (60000f / bpm) / 480f;
    }
}