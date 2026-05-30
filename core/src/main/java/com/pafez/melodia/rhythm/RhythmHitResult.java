package com.pafez.melodia.rhythm;

public final class RhythmHitResult {

	private static final RhythmHitResult IGNORED = new RhythmHitResult(RhythmJudgment.NONE, null, false);

	private final RhythmJudgment judgment;
	private final RhythmNote note;
	private final boolean consumed;

	public RhythmHitResult(RhythmJudgment judgment, RhythmNote note, boolean consumed) {
		this.judgment = judgment;
		this.note = note;
		this.consumed = consumed;
	}

	public static RhythmHitResult ignored() {
		return IGNORED;
	}

	public RhythmJudgment getJudgment() {
		return judgment;
	}

	public RhythmNote getNote() {
		return note;
	}

	public boolean isConsumed() {
		return consumed;
	}

	public boolean isIgnored() {
		return judgment == RhythmJudgment.NONE && !consumed;
	}
}