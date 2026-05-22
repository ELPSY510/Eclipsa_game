package com.example.eclipsa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    private TextView tvGrade, tvScore, tvBest, tvNewRecord;
    private TextView tvPerfect, tvGreat, tvGood, tvMiss, tvMaxCombo;
    private Button btnRetry, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        int totalScore = getIntent().getIntExtra("totalScore", 0);
        int perfectCount = getIntent().getIntExtra("perfectCount", 0);
        int greatCount = getIntent().getIntExtra("greatCount", 0);
        int goodCount = getIntent().getIntExtra("goodCount", 0);
        int missCount = getIntent().getIntExtra("missCount", 0);
        int maxCombo = getIntent().getIntExtra("maxCombo", 0);
        String songFileName = getIntent().getStringExtra("songFileName");
        if (songFileName == null) songFileName = "tutorial.json";
        final String finalSongFileName = songFileName;

        tvGrade = findViewById(R.id.tv_grade);
        tvScore = findViewById(R.id.tv_score);
        tvBest = findViewById(R.id.tv_best);
        tvNewRecord = findViewById(R.id.tv_new_record);
        tvPerfect = findViewById(R.id.stat_perfect);
        tvGreat = findViewById(R.id.stat_great);
        tvGood = findViewById(R.id.stat_good);
        tvMiss = findViewById(R.id.stat_miss);
        tvMaxCombo = findViewById(R.id.stat_max_combo);
        btnRetry = findViewById(R.id.btn_retry);
        btnBack = findViewById(R.id.btn_back);

        tvPerfect.setText("PERFECT  " + perfectCount);
        tvGreat.setText("GREAT    " + greatCount);
        tvGood.setText("GOOD     " + goodCount);
        tvMiss.setText("MISS     " + missCount);
        tvMaxCombo.setText("MAX COMBO  " + maxCombo);
        tvScore.setText(String.valueOf(totalScore));

        int totalNotes = perfectCount + greatCount + goodCount + missCount;
        float accuracy = 0f;
        if (totalNotes > 0) {
            accuracy = (perfectCount + greatCount + goodCount) * 100f / totalNotes;
        }
        String grade = calculateGrade(accuracy, missCount);
        tvGrade.setText(grade);

        SharedPreferences prefs = getSharedPreferences("best_scores", MODE_PRIVATE);
        int bestScore = prefs.getInt(finalSongFileName, 0);
        boolean isNewRecord = totalScore > bestScore;
        if (isNewRecord) {
            prefs.edit().putInt(finalSongFileName, totalScore).apply();
            tvBest.setText("最高分: " + totalScore);
            tvNewRecord.setVisibility(View.VISIBLE);
        } else {
            tvBest.setText("最高分: " + bestScore);
            tvNewRecord.setVisibility(View.GONE);
        }

        btnRetry.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, GameActivity.class);
            intent.putExtra("chartFileName", finalSongFileName);
            startActivity(intent);
            finish();
        });

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private String calculateGrade(float accuracy, int missCount) {
        if (accuracy >= 98f && missCount == 0) return "SS";
        if (accuracy >= 95f) return "S";
        if (accuracy >= 85f) return "A";
        if (accuracy >= 70f) return "B";
        return "C";
    }
}