package com.example.eclipsa.game.generator;

import com.example.eclipsa.game.note.Note;
import com.example.eclipsa.game.note.HoldNote;
import com.example.eclipsa.chart.ChartData;
import com.example.eclipsa.chart.TimingCalculator;

import java.util.ArrayList;
import java.util.List;

public class NoteGenerator {

    private ChartData chartData;//解析后面的谱面数据
    private TimingCalculator timingCalculator;//时间计算器，根基bpm将ticks转换为ms
    private long travelTime;
    private float judgeLineY;
    private int screenWidth;
    private int nextNoteIndex = 0;

    /**
     * 构造函数
     * @param chartData 谱面数据
     * @param travelTime 音符运动时间
     * @param judgeLineY 判定线坐标
     * @param screenWidth 屏幕宽度
     */
    public NoteGenerator(ChartData chartData, long travelTime, float judgeLineY, int screenWidth) {
        this.chartData = chartData;
        this.timingCalculator = new TimingCalculator(chartData.bpm);
        this.travelTime = travelTime;
        this.judgeLineY = judgeLineY;
        this.screenWidth = screenWidth;
    }

    /**
     *关键方法
     * @param currentTime 当前游戏时间
     * @return 一个 List<Note>，包含本次调用新生成的音符（可能为空）
     */
    public List<Note> generate(long currentTime) {
        List<Note> newNotes = new ArrayList<>();
        if (chartData == null) return newNotes;
        if (nextNoteIndex >= chartData.notes.size()) return newNotes;

        ChartData.NoteData noteData = chartData.notes.get(nextNoteIndex);//从谱面音符列表中取出当前索引对应的 NoteData 对象
        long judgeTime = timingCalculator.tickToMs(noteData.tick) + chartData.offset;//计算判定时间
        long createTime = judgeTime - travelTime;//计算创建时间

        if (currentTime >= createTime) {
            float trackX = getTrackX(noteData.track);
            Note note = createNoteFromData(noteData, trackX, judgeTime);
            if (note != null) {
                newNotes.add(note);
            }
            nextNoteIndex++;
        }
        return newNotes;
    }

    /**
     * 计算轨道中心坐标
     * @param track 轨道
     * @return track中心x坐标
     */
    private float getTrackX(int track) {
        float trackWidth = screenWidth / 4f;
        return track * trackWidth + trackWidth / 2;
    }

    /**
     * 根据音符类型创建不同的 Note 子类实例。
     * Tap/Drag/Flick 使用基类 Note，只需传入类型常量。
     * Hold 需要额外计算 durationMs（Hold 持续毫秒数），并创建 HoldNote 对象。
     * 所有音符共享相同的 judgeLineY（判定线 Y）和 travelTime（飞行时间）。
     * @param data 谱面数据
     * @param x 轨道中心x坐标
     * @param judgeTime 判定时间
     * @return 一个note或者hold note
     */
    private Note createNoteFromData(ChartData.NoteData data, float x, long judgeTime) {
        switch (data.type) {
            case "tap":
                return new Note(x, judgeLineY, judgeTime, travelTime, data.track, Note.TYPE_TAP);
            case "hold":
                long durationMs = timingCalculator.tickToMs(data.duration);
                return new HoldNote(x, judgeLineY, judgeTime, travelTime, data.track, durationMs);
            case "drag":
                return new Note(x, judgeLineY, judgeTime, travelTime, data.track, Note.TYPE_DRAG);
            case "flick":
                return new Note(x, judgeLineY, judgeTime, travelTime, data.track, Note.TYPE_FLICK, data.direction);
            default:
                return null;
        }
    }
}