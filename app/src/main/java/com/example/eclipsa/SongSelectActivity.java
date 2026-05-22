package com.example.eclipsa;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.eclipsa.game.audio.UIAudioManager;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 选歌界面
 * 扫描 assets/charts/ 下的 JSON 谱面，解析歌曲名、BPM、难度，并显示在列表中。
 * 支持按歌名/难度排序，点击后跳转到游戏界面。
 */
public class SongSelectActivity extends AppCompatActivity {

    private ListView listView;
    private List<SongInfo> songInfos = new ArrayList<>();

    /**
     * 歌曲信息内部类
     */
    private static class SongInfo {
        String songName;   // 显示用歌曲名
        String fileName;   // 实际文件名（如 tutorial.json）
        int bpm;
        int difficulty;    // 1-30
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_select);

        listView = findViewById(R.id.list_songs);
        loadSongInfos(); // 从 assets 加载并解析谱面

        // 创建自定义适配器
        SongAdapter adapter = new SongAdapter(songInfos);
        listView.setAdapter(adapter);

        // 点击列表项：传递文件名到 GameActivity
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SongInfo info = songInfos.get(position);
            UIAudioManager.getInstance(this).playClick(); // 音效
            Intent intent = new Intent(SongSelectActivity.this, GameActivity.class);
            intent.putExtra("chartFileName", info.fileName);
            startActivity(intent);
        });

        // 返回主菜单
        Button btnBack = findViewById(R.id.btn_back_main);
        btnBack.setOnClickListener(v -> finish());

        // 显示歌曲总数
        TextView tvSongCount = findViewById(R.id.tv_song_count);
        tvSongCount.setText("歌曲总数: " + songInfos.size());

        // 按歌名排序
        Button btnSort = findViewById(R.id.btn_sort);
        btnSort.setOnClickListener(v -> {
            songInfos.sort((a, b) -> a.songName.compareToIgnoreCase(b.songName));
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "已按名称排序", Toast.LENGTH_SHORT).show();
        });

        // 按难度排序（如果布局中有该按钮）
        Button btnSortDifficulty = findViewById(R.id.btn_sort_difficulty);
        if (btnSortDifficulty != null) {
            btnSortDifficulty.setOnClickListener(v -> {
                songInfos.sort((a, b) -> Integer.compare(a.difficulty, b.difficulty));
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "已按难度排序", Toast.LENGTH_SHORT).show();
            });
        }

        // 帮助按钮
        Button btnHelp = findViewById(R.id.btn_help_select);
        btnHelp.setOnClickListener(v ->
                Toast.makeText(this, "点击歌曲开始游戏", Toast.LENGTH_SHORT).show());

        // 列表入场动画
        listView.setAlpha(0f);
        listView.animate().alpha(1f).setDuration(500).start();
    }

    /**
     * 自定义适配器，将 SongInfo 对象绑定到列表项布局
     */
    private class SongAdapter extends ArrayAdapter<SongInfo> {
        public SongAdapter(List<SongInfo> items) {
            super(SongSelectActivity.this, R.layout.list_item_song, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.list_item_song, parent, false);
            }
            SongInfo info = getItem(position);
            TextView tvName = convertView.findViewById(R.id.tv_song_name);
            TextView tvBpm = convertView.findViewById(R.id.tv_song_bpm);
            TextView tvDifficulty = convertView.findViewById(R.id.tv_difficulty);

            tvName.setText(info.songName);          // 显示歌曲名
            tvBpm.setText("BPM: " + info.bpm);     // 显示 BPM
            if (tvDifficulty != null) {
                tvDifficulty.setText("难度: " + info.difficulty);
            }
            return convertView;
        }
    }

    // ------------------ 数据加载与解析 ------------------

    /**
     * 扫描 assets/charts/ 下所有 .json 文件，解析每个文件的歌曲信息
     */
    private void loadSongInfos() {
        try {
            String[] files = getAssets().list("charts");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".json")) {
                        String json = loadJsonString(file);
                        SongInfo info = new SongInfo();
                        info.fileName = file;
                        // 提取歌曲名，若无则用文件名
                        info.songName = extractSongName(json);
                        if (info.songName == null) {
                            info.songName = file.replace(".json", "");
                        }
                        info.bpm = extractBpm(json);
                        info.difficulty = extractDifficulty(json);
                        songInfos.add(info);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从 assets 读取 JSON 文件内容
     */
    private String loadJsonString(String fileName) throws IOException {
        InputStream is = getAssets().open("charts/" + fileName);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }

    /**
     * 提取 songName 字段
     */
    private String extractSongName(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.getString("songName");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取 bpm 字段，默认 120
     */
    private int extractBpm(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.getInt("bpm");
        } catch (Exception e) {
            return 120;
        }
    }

    /**
     * 提取 difficulty 字段（1-30），默认 1
     */
    private int extractDifficulty(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optInt("difficulty", 1);
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIAudioManager.getInstance(this).startBgm();
    }

    @Override
    protected void onPause() {
        super.onPause();
        UIAudioManager.getInstance(this).stopBgm();
    }
}