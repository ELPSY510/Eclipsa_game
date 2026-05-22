package com.example.eclipsa.game.updater;

import com.example.eclipsa.game.note.Note;
import java.util.Iterator;
import java.util.List;

public class NoteUpdater {

    private long goodWindow;

    public NoteUpdater(long goodWindow) {
        this.goodWindow = goodWindow;
    }

    /**
     *
     * @param activeNotes 屏幕上的音符
     * @param currentTime 游戏时间
     * @param onMissCallback 回调接口
     */
    public void update(List<Note> activeNotes, long currentTime, OnMissListener onMissCallback) {
        Iterator<Note> iterator = activeNotes.iterator();
        while (iterator.hasNext()) {//使用 Iterator 而不是 for-each 循环，因为需要在遍历过程中删除元素
            Note note = iterator.next();
            note.updateY(currentTime);
            if (note.shouldRemove(currentTime, goodWindow)) {
                if (!note.isJudged && onMissCallback != null) {
                    onMissCallback.onMiss(note);
                }
                iterator.remove();
            }
        }
    }

    /**
     * 函数式接口，用于回调 Miss 事件
     */
    public interface OnMissListener {
        void onMiss(Note note);
    }
}