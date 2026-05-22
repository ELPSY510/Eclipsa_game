package com.example.eclipsa.game.input;

import android.view.MotionEvent;
import com.example.eclipsa.game.judge.JudgementSystem;
import com.example.eclipsa.game.note.Note;

public class TouchInputHandler {

    private JudgementSystem judgementSystem;
    private int screenWidth;
    private float downX, downY;
    private long downTime;

    public TouchInputHandler(JudgementSystem judgementSystem, int screenWidth) {
        this.judgementSystem = judgementSystem;
        this.screenWidth = screenWidth;
    }

    public boolean onTouchEvent(MotionEvent event, long currentTime) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downTime = currentTime;
                int trackDown = mapXToTrack(x);
                judgementSystem.handleTap(trackDown, currentTime);
                break;

            case MotionEvent.ACTION_MOVE:
                int trackMove = mapXToTrack(x);
                judgementSystem.handleDrag(trackMove, currentTime);
                break;

            case MotionEvent.ACTION_UP:
                // 检测滑动（Flick）
                float dx = x - downX;
                float dy = y - downY;
                if (Math.hypot(dx, dy) > 50) {
                    int direction = getDirection(dx, dy);
                    int trackUp = mapXToTrack(x);
                    judgementSystem.handleFlick(trackUp, direction, currentTime);
                }
                // Hold 尾判
                if (judgementSystem.hasActiveHold()) {
                    judgementSystem.handleHoldRelease(currentTime);
                }
                break;
        }
        return true;
    }

    private int mapXToTrack(float touchX) {
        float trackWidth = screenWidth / 4f;
        int track = (int) (touchX / trackWidth);
        return Math.min(track, 3);
    }

    private int getDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Note.DIR_RIGHT : Note.DIR_LEFT;
        } else {
            return dy > 0 ? Note.DIR_DOWN : Note.DIR_UP;
        }
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }
}