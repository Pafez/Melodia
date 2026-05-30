package com.pafez.melodia.rhythm;

public enum RhythmJudgment {
	PERFECT("Perfect", 300),
	GREAT("Great", 200),
	GOOD("Good", 100),
	MISS("Miss", 0),
	NONE("", 0);

	private final String label;
	private final int baseScore;

	RhythmJudgment(String label, int baseScore) {
		this.label = label;
		this.baseScore = baseScore;
	}

	public String getLabel() {
		return label;
	}

	public int getBaseScore() {
		return baseScore;
	}

	public static RhythmJudgment judge(long absoluteOffsetMs, RhythmGameRules rules) {
		if (absoluteOffsetMs <= rules.getPerfectWindowMs()) {
			return PERFECT;
		}
		if (absoluteOffsetMs <= rules.getGreatWindowMs()) {
			return GREAT;
		}
		if (absoluteOffsetMs <= rules.getGoodWindowMs()) {
			return GOOD;
		}
		if (absoluteOffsetMs <= rules.getMissWindowMs()) {
			return MISS;
		}
		return NONE;
	}
}