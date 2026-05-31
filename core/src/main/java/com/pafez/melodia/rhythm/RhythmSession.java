package com.pafez.melodia.rhythm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;

public final class RhythmSession {

	private static final class ActiveHoldNote {
		private final int noteIndex;
		private final RhythmJudgment initialJudgment;

		private ActiveHoldNote(int noteIndex, RhythmJudgment initialJudgment) {
			this.noteIndex = noteIndex;
			this.initialJudgment = initialJudgment;
		}
	}

	private final RhythmChart chart;
	private final RhythmGameRules rules;
	private final RhythmLaneMapper laneMapper;
	private final List<Deque<Integer>> laneQueues;
	private final BitSet resolvedNotes;
	private final ActiveHoldNote[] activeHoldNotes;
	private final boolean[] lanePressed;
	private final RhythmScoreState scoreState = new RhythmScoreState();

	private int resolvedCount;

	public RhythmSession(RhythmChart chart, RhythmLaneMapper laneMapper, RhythmGameRules rules) {
		if (chart == null) {
			throw new IllegalArgumentException("Chart cannot be null.");
		}
		if (laneMapper == null) {
			throw new IllegalArgumentException("Lane mapper cannot be null.");
		}
		if (rules == null) {
			throw new IllegalArgumentException("Rules cannot be null.");
		}
		this.chart = chart;
		this.rules = rules;
		this.laneMapper = laneMapper;
		this.resolvedNotes = new BitSet(chart.getNotes().size());
		this.laneQueues = new ArrayList<>(laneMapper.getLaneCount());
		this.activeHoldNotes = new ActiveHoldNote[laneMapper.getLaneCount()];
		this.lanePressed = new boolean[laneMapper.getLaneCount()];
		for (int lane = 0; lane < laneMapper.getLaneCount(); lane++) {
			laneQueues.add(new ArrayDeque<Integer>());
		}
		buildLaneQueues();
	}

	public RhythmChart getChart() {
		return chart;
	}

	public RhythmGameRules getRules() {
		return rules;
	}

	public RhythmLaneMapper getLaneMapper() {
		return laneMapper;
	}

	public RhythmScoreState getScoreState() {
		return scoreState;
	}

	public boolean isResolved(int noteIndex) {
		return resolvedNotes.get(noteIndex);
	}

	public boolean isHoldNoteActive(int noteIndex) {
		for (ActiveHoldNote activeHoldNote : activeHoldNotes) {
			if (activeHoldNote != null && activeHoldNote.noteIndex == noteIndex) {
				return true;
			}
		}
		return false;
	}

	public int getResolvedCount() {
		return resolvedCount;
	}

	public boolean isFinished() {
		return resolvedCount >= chart.getNotes().size();
	}

	public void update(long songTimeMs) {
		for (Deque<Integer> laneQueue : laneQueues) {
			pruneResolvedHeads(laneQueue);
			while (!laneQueue.isEmpty()) {
				int noteIndex = laneQueue.peekFirst();
				RhythmNote note = chart.getNotes().get(noteIndex);
				if (songTimeMs <= note.getStartTimeMs() + rules.getMissWindowMs()) {
					break;
				}
				resolveMiss(laneQueue.removeFirst(), songTimeMs);
				pruneResolvedHeads(laneQueue);
			}
		}
		for (int lane = 0; lane < activeHoldNotes.length; lane++) {
			ActiveHoldNote activeHoldNote = activeHoldNotes[lane];
			if (activeHoldNote == null) {
				continue;
			}
			if (resolvedNotes.get(activeHoldNote.noteIndex)) {
				activeHoldNotes[lane] = null;
				continue;
			}
			RhythmNote note = chart.getNotes().get(activeHoldNote.noteIndex);
			if (songTimeMs < note.getEndTimeMs()) {
				continue;
			}
			if (lanePressed[lane]) {
				resolveHit(activeHoldNote.noteIndex, activeHoldNote.initialJudgment, songTimeMs);
			} else {
				resolveMiss(activeHoldNote.noteIndex, songTimeMs);
			}
			activeHoldNotes[lane] = null;
		}
	}

	public RhythmHitResult hitLane(int lane, long songTimeMs) {
		return pressLane(lane, songTimeMs);
	}

	public RhythmHitResult pressLane(int lane, long songTimeMs) {
		if (lane < 0 || lane >= laneQueues.size()) {
			return RhythmHitResult.ignored();
		}

		update(songTimeMs);
		if (lanePressed[lane]) {
			return RhythmHitResult.ignored();
		}
		lanePressed[lane] = true;
		Deque<Integer> laneQueue = laneQueues.get(lane);
		pruneResolvedHeads(laneQueue);
		if (laneQueue.isEmpty()) {
			return RhythmHitResult.ignored();
		}

		int noteIndex = laneQueue.peekFirst();
		RhythmNote note = chart.getNotes().get(noteIndex);
		long absoluteOffsetMs = Math.abs(songTimeMs - note.getStartTimeMs());
		RhythmJudgment judgment = RhythmJudgment.judge(absoluteOffsetMs, rules);
		if (judgment == RhythmJudgment.NONE) {
			return RhythmHitResult.ignored();
		}

		int resolvedNoteIndex = laneQueue.removeFirst();
		if (note.isHoldNote()) {
			activeHoldNotes[lane] = new ActiveHoldNote(resolvedNoteIndex, judgment);
			return new RhythmHitResult(judgment, note, true);
		}

		resolveHit(resolvedNoteIndex, judgment, songTimeMs);
		return new RhythmHitResult(judgment, note, true);
	}

	public RhythmHitResult releaseLane(int lane, long songTimeMs) {
		if (lane < 0 || lane >= laneQueues.size()) {
			return RhythmHitResult.ignored();
		}

		update(songTimeMs);
		if (!lanePressed[lane]) {
			return RhythmHitResult.ignored();
		}
		lanePressed[lane] = false;

		ActiveHoldNote activeHoldNote = activeHoldNotes[lane];
		if (activeHoldNote == null || resolvedNotes.get(activeHoldNote.noteIndex)) {
			activeHoldNotes[lane] = null;
			return RhythmHitResult.ignored();
		}

		RhythmNote note = chart.getNotes().get(activeHoldNote.noteIndex);
		if (songTimeMs >= note.getEndTimeMs()) {
			activeHoldNotes[lane] = null;
			return RhythmHitResult.ignored();
		}

		activeHoldNotes[lane] = null;
		resolveMiss(activeHoldNote.noteIndex, songTimeMs);
		return new RhythmHitResult(RhythmJudgment.MISS, note, true);
	}

	private void buildLaneQueues() {
		for (int noteIndex = 0; noteIndex < chart.getNotes().size(); noteIndex++) {
			RhythmNote note = chart.getNotes().get(noteIndex);
			int lane = laneMapper.getLaneForPitch(note.getMidiPitch());
			laneQueues.get(lane).addLast(noteIndex);
		}
	}

	private void pruneResolvedHeads(Deque<Integer> laneQueue) {
		while (!laneQueue.isEmpty() && resolvedNotes.get(laneQueue.peekFirst())) {
			laneQueue.removeFirst();
		}
	}

	private void resolveHit(int noteIndex, RhythmJudgment judgment, long songTimeMs) {
		if (resolvedNotes.get(noteIndex)) {
			return;
		}
		resolvedNotes.set(noteIndex);
		resolvedCount++;
		scoreState.registerJudgment(judgment, songTimeMs);
	}

	private void resolveMiss(int noteIndex, long songTimeMs) {
		if (resolvedNotes.get(noteIndex)) {
			return;
		}
		resolvedNotes.set(noteIndex);
		resolvedCount++;
		scoreState.registerJudgment(RhythmJudgment.MISS, songTimeMs);
	}
}