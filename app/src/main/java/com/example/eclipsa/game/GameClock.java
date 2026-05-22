package com.example.eclipsa.game;

public class GameClock {

    private long startTime;
    private long pauseDuration;
    private boolean isPaused;

    /**
     * 构造方法
     */
    public GameClock() {
        startTime = 0;
        pauseDuration = 0;
        isPaused = false;
    }

    public void start() {
        startTime = System.currentTimeMillis();
        pauseDuration = 0;
        isPaused = false;
    }

    public void pause() {
        if (!isPaused) {
            pauseDuration = getCurrentTime();
            isPaused = true;
        }
    }

    public void resume() {
        if (isPaused) {
            startTime = System.currentTimeMillis() - pauseDuration;
            isPaused = false;
        }
    }

    public long getCurrentTime() {
        if (isPaused) {
            return pauseDuration;
        }
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    public boolean isPaused() {
        return isPaused;
    }
}