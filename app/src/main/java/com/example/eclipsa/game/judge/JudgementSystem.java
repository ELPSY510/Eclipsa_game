package com.example.eclipsa.game.judge;

import com.example.eclipsa.game.audio.GameAudioManager;
import com.example.eclipsa.game.note.Note;
import com.example.eclipsa.game.note.HoldNote;
import com.example.eclipsa.game.renderer.NoteRenderer;
import com.example.eclipsa.game.score.ScoreSystem;
import java.util.List;

public class JudgementSystem {

    private ScoreSystem scoreSystem;
    private List<Note> activeNotes;
    private HoldHandler holdHandler;
    private long goodWindow;
    private GameAudioManager gameAudio;
    private NoteRenderer noteRenderer;

    /**
     * 构造方法 holdhander专门用来处理hold
     * @param scoreSystem
     * @param activeNotes
     * @param goodWindow
     */
    public JudgementSystem(ScoreSystem scoreSystem, List<Note> activeNotes, long goodWindow) {
        this.scoreSystem = scoreSystem;
        this.activeNotes = activeNotes;
        this.goodWindow = goodWindow;
        this.holdHandler = new HoldHandler(scoreSystem, goodWindow);
    }

    public void setGameAudio(GameAudioManager gameAudio) {
        this.gameAudio = gameAudio;
    }

    public void setNoteRenderer(NoteRenderer renderer) {
        this.noteRenderer = renderer;
    }

    private void addEffect(float x, float y) {
        if (noteRenderer != null) {
            noteRenderer.addEffect(x, y);
        }
    }

    /**
     * Hold 优先：如果该轨道存在一个尚未头判的 Hold 音符，且头判成功（时间差在窗口内），则播放 Hold 音效、添加特效，并直接返回（不继续检查 Tap）。
     * Tap 处理：如果没有 Hold 或头判失败，则查找最早的 Tap 音符。若时间差在窗口内，则：
     * 调用note的 hit() 标记为已判定。
     * 从 activeNotes 中移除。
     * 根据时间差得到 Perfect/Great/Good。
     * 调用 scoreSystem.onHit(judge) 加分、增加连击。
     * 播放 Tap 音效和特效。
     * 注意：Tap 命中后，该音符立即被移除，不会与后续触摸冲突。
     * @param track 轨道
     * @param tapTime 时间
     */
    public void handleTap(int track, long tapTime) {
        // Hold 头判
        HoldNote holdNote = findEarliestHoldNoteInTrack(track);
        if (holdNote != null && !holdNote.isHeadJudged) {
            if (holdHandler.onHeadJudge(holdNote, tapTime)) {
                if (gameAudio != null) gameAudio.playHoldSound();
                addEffect(holdNote.x, holdNote.y);
                return;
            }
        }

        // Tap 判定
        Note tapNote = findEarliestNoteInTrack(track, Note.TYPE_TAP);
        if (tapNote != null) {
            long timeDiff = Math.abs(tapTime - tapNote.judgeTime);
            if (timeDiff <= goodWindow) {
                tapNote.hit(tapTime);
                activeNotes.remove(tapNote);
                int judge = ScoreSystem.getJudge(timeDiff);
                scoreSystem.onHit(judge);
                if (gameAudio != null) gameAudio.playTapSound();
                addEffect(tapNote.x, tapNote.y);
            }
        }
    }

    /**
     * Drag 不判断时间差，只要手指划过该音符所在轨道，就立即命中（任何时刻）。
     * 固定加分（addDragHit），不经过等级判定。
     * 命中后移除音符，播放 Drag 音效和特效
     * @param track 轨道
     * @param currentTime 现在时间
     */
    public void handleDrag(int track, long currentTime) {
        Note dragNote = findEarliestNoteInTrack(track, Note.TYPE_DRAG);
        if (dragNote != null && !dragNote.isJudged) {
            dragNote.isJudged = true;
            activeNotes.remove(dragNote);
            scoreSystem.addDragHit();
            if (gameAudio != null) gameAudio.playDragSound();
            addEffect(dragNote.x, dragNote.y);
        }
    }

    /**
     * 先检查方向是否匹配（flickNote.direction == direction）。
     * 再判断时间差是否在窗口内。
     * 命中后与 Tap 类似：移除、加分（按等级）、播放音效和特效。
     * @param track 轨道
     * @param direction 方向
     * @param flickTime 判定时间
     */
    public void handleFlick(int track, int direction, long flickTime) {
        Note flickNote = findEarliestNoteInTrack(track, Note.TYPE_FLICK);
        if (flickNote != null && !flickNote.isJudged && flickNote.direction == direction) {
            long timeDiff = Math.abs(flickTime - flickNote.judgeTime);
            if (timeDiff <= goodWindow) {
                flickNote.hit(flickTime);
                activeNotes.remove(flickNote);
                int judge = ScoreSystem.getJudge(timeDiff);
                scoreSystem.onHit(judge);
                if (gameAudio != null) gameAudio.playFlickSound();
                addEffect(flickNote.x, flickNote.y);
            }
        }
    }

    /**
     * 委托 HoldHandler.onTailJudge() 处理尾判逻辑。
     * 如果成功，播放 Hold 音效和特效（注意：这里 Hold 音效与头判相同，项目中设计如此）。
     * @param releaseTime 尾判松手时间
     */
    public void handleHoldRelease(long releaseTime) {
        boolean success = holdHandler.onTailJudge(releaseTime);
        if (success) {
            HoldNote hold = holdHandler.getActiveHoldNote();
            if (hold != null) {
                if (gameAudio != null) gameAudio.playHoldSound();
                addEffect(hold.x, hold.y);
            }
        }
    }

    /**
     *用于 GameController 在 onTouchUp 时判断是否需要调用 handleHoldRelease。
     * @return
     */
    public boolean hasActiveHold() {
        return holdHandler.getActiveHoldNote() != null;
    }

    /**
     * 遍历 activeNotes，找到指定轨道上最早（judgeTime 最小）且未判定的音符。
     * 对于 Hold，还要求 isHeadJudged == false，避免重复头判。
     * 这是保证“先到先判”逻辑的关键。
     * @param track 轨道
     * @return
     */
    private HoldNote findEarliestHoldNoteInTrack(int track) {
        HoldNote earliest = null;
        for (Note note : activeNotes) {
            if (note.type == Note.TYPE_HOLD && !((HoldNote)note).isHeadJudged && note.track == track) {
                HoldNote hold = (HoldNote) note;
                if (earliest == null || hold.judgeTime < earliest.judgeTime) {
                    earliest = hold;
                }
            }
        }
        return earliest;
    }

    /**
     * @param track 轨道
     * @param type 类型
     * @return
     */
    private Note findEarliestNoteInTrack(int track, int type) {
        Note earliest = null;
        for (Note note : activeNotes) {
            if (note.type == type && !note.isJudged && note.track == track) {
                if (earliest == null || note.judgeTime < earliest.judgeTime) {
                    earliest = note;
                }
            }
        }
        return earliest;
    }
}