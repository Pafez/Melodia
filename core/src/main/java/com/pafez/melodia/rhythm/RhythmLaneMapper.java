package com.pafez.melodia.rhythm;

public final class RhythmLaneMapper {

	public static final int FIRST_COLUMN_MIDI_NOTE = 60;

	private final int laneCount;
	private final int lowerPitchBound;
	private final int upperPitchBound;
	private final int[] columnLowerBounds;
	private final int[] columnUpperBounds;

	private RhythmLaneMapper(int lowerPitchBound, int upperPitchBound, int laneCount) {
		if (laneCount <= 0) {
			throw new IllegalArgumentException("Lane count must be positive.");
		}
		if (lowerPitchBound > upperPitchBound) {
			throw new IllegalArgumentException("Lower pitch bound cannot be greater than the upper pitch bound.");
		}
		this.laneCount = laneCount;
		this.lowerPitchBound = lowerPitchBound;
		this.upperPitchBound = upperPitchBound;
		this.columnLowerBounds = new int[laneCount];
		this.columnUpperBounds = new int[laneCount];
		buildColumns();
	}

	public static RhythmLaneMapper fromChart(RhythmChart chart, int laneCount) {
		return new RhythmLaneMapper(chart.getLowerPitchBound(), chart.getUpperPitchBound(), laneCount);
	}

	public int getLaneCount() {
		return laneCount;
	}

	public int getLowerPitchBound() {
		return lowerPitchBound;
	}

	public int getUpperPitchBound() {
		return upperPitchBound;
	}

	public int getColumnLowerBound(int lane) {
		validateLane(lane);
		return columnLowerBounds[lane];
	}

	public int getColumnUpperBound(int lane) {
		validateLane(lane);
		return columnUpperBounds[lane];
	}

	public int getLaneForPitch(int midiPitch) {
		if (laneCount == 1) {
			return 0;
		}
		if (midiPitch <= columnLowerBounds[0]) {
			return 0;
		}
		if (midiPitch >= columnUpperBounds[laneCount - 1]) {
			return laneCount - 1;
		}

		for (int lane = 0; lane < laneCount; lane++) {
			if (columnUpperBounds[lane] < columnLowerBounds[lane]) {
				continue;
			}
			if (midiPitch >= columnLowerBounds[lane] && midiPitch <= columnUpperBounds[lane]) {
				return lane;
			}
		}

		for (int lane = 0; lane < laneCount - 1; lane++) {
			if (midiPitch < columnLowerBounds[lane + 1]) {
				return lane;
			}
		}
		return laneCount - 1;
	}

	private void buildColumns() {
		int rangeStart = FIRST_COLUMN_MIDI_NOTE;
		int rangeEnd = Math.max(FIRST_COLUMN_MIDI_NOTE, upperPitchBound);
		int rangeSize = rangeEnd - rangeStart + 1;
		int baseWidth = rangeSize / laneCount;
		int remainder = rangeSize % laneCount;
		int currentPitch = rangeStart;

		for (int lane = 0; lane < laneCount; lane++) {
			int width = baseWidth + (lane < remainder ? 1 : 0);
			if (width <= 0) {
				columnLowerBounds[lane] = currentPitch;
				columnUpperBounds[lane] = currentPitch - 1;
				continue;
			}
			columnLowerBounds[lane] = currentPitch;
			columnUpperBounds[lane] = currentPitch + width - 1;
			currentPitch += width;
		}

		columnLowerBounds[0] = FIRST_COLUMN_MIDI_NOTE;
		if (columnUpperBounds[0] < columnLowerBounds[0]) {
			columnUpperBounds[0] = columnLowerBounds[0];
		}
	}

	private void validateLane(int lane) {
		if (lane < 0 || lane >= laneCount) {
			throw new IllegalArgumentException("Lane index out of range: " + lane);
		}
	}
}