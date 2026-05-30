package com.pafez.melodia.rhythm;

import com.badlogic.gdx.Input;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RhythmKeyBindings {

	private final int[] laneKeyCodes;

	private RhythmKeyBindings(int[] laneKeyCodes) {
		if (laneKeyCodes == null) {
			throw new IllegalArgumentException("Lane key bindings cannot be null.");
		}
		if (laneKeyCodes.length < RhythmGameRules.MIN_LANE_COUNT || laneKeyCodes.length > RhythmGameRules.MAX_LANE_COUNT) {
			throw new IllegalArgumentException("Lane key bindings must define between " + RhythmGameRules.MIN_LANE_COUNT
					+ " and " + RhythmGameRules.MAX_LANE_COUNT + " keys.");
		}
		Set<Integer> unique = new LinkedHashSet<>();
		for (int keyCode : laneKeyCodes) {
			if (!unique.add(keyCode)) {
				throw new IllegalArgumentException("Lane key bindings must not contain duplicate key codes.");
			}
		}
		this.laneKeyCodes = Arrays.copyOf(laneKeyCodes, laneKeyCodes.length);
	}

	public static RhythmKeyBindings defaultNumberRow() {
		return of(
				Input.Keys.NUM_1,
				Input.Keys.NUM_2,
				Input.Keys.NUM_3,
				Input.Keys.NUM_4,
				Input.Keys.NUM_5,
				Input.Keys.NUM_6,
				Input.Keys.NUM_7,
				Input.Keys.NUM_8,
				Input.Keys.NUM_9
		);
	}

	public static RhythmKeyBindings of(int... laneKeyCodes) {
		return new RhythmKeyBindings(laneKeyCodes);
	}

	public int getLaneCount() {
		return laneKeyCodes.length;
	}

	public int getKeyCode(int lane) {
		validateLane(lane);
		return laneKeyCodes[lane];
	}

	public RhythmKeyBindings withKeyCode(int lane, int keyCode) {
		validateLane(lane);
		int[] copy = Arrays.copyOf(laneKeyCodes, laneKeyCodes.length);
		copy[lane] = keyCode;
		return new RhythmKeyBindings(copy);
	}

	public int findLaneForKeyCode(int keyCode, int laneCount) {
		int maxLane = Math.min(laneCount, laneKeyCodes.length);
		for (int lane = 0; lane < maxLane; lane++) {
			if (laneKeyCodes[lane] == keyCode) {
				return lane;
			}
		}
		return -1;
	}

	public String describe(int laneCount) {
		int maxLane = Math.min(laneCount, laneKeyCodes.length);
		StringBuilder builder = new StringBuilder();
		for (int lane = 0; lane < maxLane; lane++) {
			if (lane > 0) {
				builder.append(" | ");
			}
			builder.append(Input.Keys.toString(laneKeyCodes[lane]));
		}
		return builder.toString();
	}

	private void validateLane(int lane) {
		if (lane < 0 || lane >= laneKeyCodes.length) {
			throw new IllegalArgumentException("Lane index out of range: " + lane);
		}
	}
}