package com.pafez.melodia.rhythm;

public final class RhythmNote {

	private final int midiPitch;
	private final long startTimeMs;
	private final long endTimeMs;
	private final int startOrder;

	public RhythmNote(int midiPitch, long startTimeMs, long endTimeMs, int startOrder) {
		if (endTimeMs < startTimeMs) {
			throw new IllegalArgumentException("Note end time cannot be before the start time.");
		}
		this.midiPitch = midiPitch;
		this.startTimeMs = startTimeMs;
		this.endTimeMs = endTimeMs;
		this.startOrder = startOrder;
	}

	public int getMidiPitch() {
		return midiPitch;
	}

	public long getStartTimeMs() {
		return startTimeMs;
	}

	public long getEndTimeMs() {
		return endTimeMs;
	}

	public int getStartOrder() {
		return startOrder;
	}

	public boolean isHoldNote() {
		return endTimeMs > startTimeMs;
	}
}