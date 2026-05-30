package com.pafez.melodia.rhythm;

public final class RhythmGameRules {

	private final int laneCount;
	private final int leadInMs;
	private final int travelTimeMs;
	private final int perfectWindowMs;
	private final int greatWindowMs;
	private final int goodWindowMs;
	private final int missWindowMs;

	public RhythmGameRules(int laneCount, int leadInMs, int travelTimeMs,
						  int perfectWindowMs, int greatWindowMs, int goodWindowMs, int missWindowMs) {
		if (laneCount <= 0) {
			throw new IllegalArgumentException("Lane count must be positive.");
		}
		if (leadInMs <= 0 || travelTimeMs <= 0) {
			throw new IllegalArgumentException("Lead-in and travel time must be positive.");
		}
		if (!(perfectWindowMs > 0 && perfectWindowMs <= greatWindowMs && greatWindowMs <= goodWindowMs
				&& goodWindowMs <= missWindowMs)) {
			throw new IllegalArgumentException("Timing windows must be ordered from tightest to widest.");
		}
		this.laneCount = laneCount;
		this.leadInMs = leadInMs;
		this.travelTimeMs = travelTimeMs;
		this.perfectWindowMs = perfectWindowMs;
		this.greatWindowMs = greatWindowMs;
		this.goodWindowMs = goodWindowMs;
		this.missWindowMs = missWindowMs;
	}

	public static RhythmGameRules defaultRules() {
		return new RhythmGameRules(5, 1800, 1800, 30, 70, 110, 160);
	}

	public int getLaneCount() {
		return laneCount;
	}

	public int getLeadInMs() {
		return leadInMs;
	}

	public int getTravelTimeMs() {
		return travelTimeMs;
	}

	public int getPerfectWindowMs() {
		return perfectWindowMs;
	}

	public int getGreatWindowMs() {
		return greatWindowMs;
	}

	public int getGoodWindowMs() {
		return goodWindowMs;
	}

	public int getMissWindowMs() {
		return missWindowMs;
	}
}