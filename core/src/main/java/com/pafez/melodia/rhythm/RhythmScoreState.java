package com.pafez.melodia.rhythm;

public final class RhythmScoreState {

	private int score;
	private int combo;
	private int maxCombo;
	private int perfectCount;
	private int greatCount;
	private int goodCount;
	private int missCount;
	private RhythmJudgment lastJudgment = RhythmJudgment.NONE;
	private long lastJudgmentTimeMs = -1L;

	public void registerJudgment(RhythmJudgment judgment, long timeMs) {
		if (judgment == null || judgment == RhythmJudgment.NONE) {
			return;
		}

		lastJudgment = judgment;
		lastJudgmentTimeMs = timeMs;

		if (judgment == RhythmJudgment.MISS) {
			missCount++;
			combo = 0;
			return;
		}

		combo++;
		if (combo > maxCombo) {
			maxCombo = combo;
		}

		int points = Math.round(judgment.getBaseScore() * getMultiplier());
		score += points;
		switch (judgment) {
			case PERFECT:
				perfectCount++;
				break;
			case GREAT:
				greatCount++;
				break;
			case GOOD:
				goodCount++;
				break;
			default:
				break;
		}
	}

	public int getScore() {
		return score;
	}

	public int getCombo() {
		return combo;
	}

	public int getMaxCombo() {
		return maxCombo;
	}

	public int getPerfectCount() {
		return perfectCount;
	}

	public int getGreatCount() {
		return greatCount;
	}

	public int getGoodCount() {
		return goodCount;
	}

	public int getMissCount() {
		return missCount;
	}

	public RhythmJudgment getLastJudgment() {
		return lastJudgment;
	}

	public long getLastJudgmentTimeMs() {
		return lastJudgmentTimeMs;
	}

	public float getMultiplier() {
		return Math.min(5f, 1f + (combo / 30f) * 4f);
	}

	public float getAccuracyPercent() {
		int judgedCount = perfectCount + greatCount + goodCount + missCount;
		if (judgedCount == 0) {
			return 0f;
		}
		float weightedScore = perfectCount * 1f + greatCount * 0.8f + goodCount * 0.5f;
		return (weightedScore / judgedCount) * 100f;
	}
}