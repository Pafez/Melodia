package com.pafez.melodia.rhythm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RhythmChart {

	private final String sourceName;
	private final long durationMs;
	private final double bpm;
	private final List<RhythmNote> notes;
	private final List<Integer> uniquePitches;

	public RhythmChart(String sourceName, long durationMs, double bpm, List<RhythmNote> notes) {
		if (notes == null || notes.isEmpty()) {
			throw new IllegalArgumentException("A rhythm chart must contain at least one note.");
		}
		this.sourceName = sourceName == null ? "unknown" : sourceName;
		this.durationMs = durationMs;
		this.bpm = bpm;
		this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
		this.uniquePitches = buildUniquePitches(notes);
	}

	public String getSourceName() {
		return sourceName;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public double getBpm() {
		return bpm;
	}

	public List<RhythmNote> getNotes() {
		return notes;
	}

	public List<Integer> getUniquePitches() {
		return uniquePitches;
	}

	private static List<Integer> buildUniquePitches(List<RhythmNote> notes) {
		List<Integer> values = new ArrayList<>();
		for (RhythmNote note : notes) {
			if (!values.contains(note.getMidiPitch())) {
				values.add(note.getMidiPitch());
			}
		}
		Collections.sort(values);
		return Collections.unmodifiableList(values);
	}
}