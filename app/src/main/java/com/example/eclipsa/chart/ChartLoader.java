package com.example.eclipsa.chart;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 实现谱面加载
 */
public class ChartLoader {
    /**
     * 从 assets 目录加载指定文件，并解析为 ChartData。
     * @param context 实例
     * @param fileName 文件名
     * @return 解析后的文件
     */
    public static ChartData loadFromAssets(Context context, String fileName) {
        try {
            //context.getAssets().open("charts/" + fileName)：打开 assets/charts/ 下的文件，返回 InputStream。
            InputStream is = context.getAssets().open("charts/" + fileName);
            int size = is.available();//is.available()：获取文件大小（字节数）
            byte[] buffer = new byte[size];//创建与文件大小相同的字节数组 buffer。
            is.read(buffer);//is.read(buffer)：将文件内容读入字节数组。
            is.close();//is.close()：关闭流。
            String json = new String(buffer, StandardCharsets.UTF_8);// new String(buffer, StandardCharsets.UTF_8)：将字节数组转为 UTF-8 字符串。
            return parseJson(json);//调用 parseJson(json) 解析 JSON 字符串。
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ---------解析json谱面---------
     * endTime：歌曲结束时间（毫秒），如果没有则默认为 0（游戏会使用默认 15 秒结束）。
     * @param json json字符串 LoadFromAssets传入
     * @return note类
     * @throws Exception
     */
    private static ChartData parseJson(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        ChartData data = new ChartData();
        //读取字段
        data.songName = obj.getString("songName");
        data.bpm = obj.getInt("bpm");
        data.offset = obj.optInt("offset", 0);
        data.endTimeMs = obj.optLong("endTime", 0);
        data.bgImageResName = obj.optString("bgImage", "default_bg");
        data.difficulty = obj.optInt("difficulty", 1);

        JSONArray notesArray = obj.getJSONArray("notes");//获取json中的notes数组
        List<ChartData.NoteData> notes = new ArrayList<>();//用于储存解析后的notes数组
        //遍历notes数组，创建不同notedata对象存入notes数组
        for (int i = 0; i < notesArray.length(); i++) {
            JSONObject noteObj = notesArray.getJSONObject(i);
            int tick = noteObj.getInt("tick");
            int track = noteObj.getInt("track");
            String type = noteObj.getString("type");

            if (type.equals("tap")) {
                notes.add(new ChartData.NoteData(tick, track, type, null, null));
            } else if (type.equals("hold")) {
                int duration = noteObj.getInt("duration");
                notes.add(new ChartData.NoteData(tick, track, type, duration, null));
            } else if (type.equals("drag")) {
                notes.add(new ChartData.NoteData(tick, track, type, null, null));
            } else if (type.equals("flick")) {
                String dirStr = noteObj.getString("direction");
                int direction = convertDirection(dirStr);
                notes.add(new ChartData.NoteData(tick, track, type, null, direction));
            }
        }
        data.notes = notes;
        return data;
    }

    /**
     *  将谱面方向解析为可读的方向向量
     * @param dir 方向
     * @return 方向向量
     */
    private static int convertDirection(String dir) {
        switch (dir) {
            case "up": return com.example.eclipsa.game.note.Note.DIR_UP;
            case "down": return com.example.eclipsa.game.note.Note.DIR_DOWN;
            case "left": return com.example.eclipsa.game.note.Note.DIR_LEFT;
            case "right": return com.example.eclipsa.game.note.Note.DIR_RIGHT;
            default: return com.example.eclipsa.game.note.Note.DIR_UP;
        }
    }
}