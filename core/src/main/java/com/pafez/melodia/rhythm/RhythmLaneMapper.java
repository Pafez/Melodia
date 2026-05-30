package com.pafez.melodia.rhythm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RhythmLaneMapper {

	private final List<Integer> sortedPitches;
	private final int laneCount;

	private RhythmLaneMapper(List<Integer> sortedPitches, int laneCount) {
		if (laneCount <= 0) {
			throw new IllegalArgumentException("Lane count must be positive.");
		}
		if (sortedPitches == null || sortedPitches.isEmpty()) {
			throw new IllegalArgumentException("At least one pitch is required to build a lane map.");
		}
		this.sortedPitches = Collections.unmodifiableList(new ArrayList<>(sortedPitches));
		this.laneCount = laneCount;
	}

	public static RhythmLaneMapper fromChart(RhythmChart chart, int laneCount) {
		Set<Integer> unique = new HashSet<>(chart.getUniquePitches());
		List<Integer> sorted = new ArrayList<>(unique);
		Collections.sort(sorted);
		return new RhythmLaneMapper(sorted, laneCount);
	}

	public int getLaneCount() {
		return laneCount;
	}

	public int getLaneForPitch(int midiPitch) {
		int exactIndex = Collections.binarySearch(sortedPitches, midiPitch);
		int pitchIndex = exactIndex >= 0 ? exactIndex : findNearestPitchIndex(midiPitch);
		if (sortedPitches.size() <= laneCount) {
			return pitchIndex;
		}
		return Math.min(laneCount - 1, (int) Math.floor((pitchIndex * (double) laneCount) / sortedPitches.size()));
	}

	private int findNearestPitchIndex(int midiPitch) {
		int insertionPoint = -Collections.binarySearch(sortedPitches, midiPitch) - 1;
		if (insertionPoint <= 0) {
			return 0;
		}
		if (insertionPoint >= sortedPitches.size()) {
			return sortedPitches.size() - 1;
		}

		int lowerPitch = sortedPitches.get(insertionPoint - 1);
		int upperPitch = sortedPitches.get(insertionPoint);
		int lowerDistance = Math.abs(midiPitch - lowerPitch);
		int upperDistance = Math.abs(upperPitch - midiPitch);
		return upperDistance < lowerDistance ? insertionPoint : insertionPoint - 1;
	}
}