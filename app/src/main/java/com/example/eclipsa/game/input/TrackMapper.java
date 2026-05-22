package com.example.eclipsa.game.input;

public class TrackMapper {
    private int screenWidth;

    public TrackMapper(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int mapXToTrack(float touchX) {
        float trackWidth = screenWidth / 4f;
        int track = (int) (touchX / trackWidth);
        return Math.min(track, 3);
    }
}