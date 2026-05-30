package com.pafez.melodia.rhythm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RhythmChartParser {

	private RhythmChartParser() {
	}

	public static RhythmChart parse(String sourceName, String chartText) {
		if (chartText == null || chartText.trim().isEmpty()) {
			throw new IllegalArgumentException("Chart text cannot be empty.");
		}

		try (BufferedReader reader = new BufferedReader(new StringReader(chartText))) {
			String headerLine = nextContentLine(reader);
			if (headerLine == null) {
				throw new IllegalArgumentException("Chart text does not contain a header line.");
			}

			String[] header = headerLine.trim().split("\\s+");
			if (header.length < 3) {
				throw new IllegalArgumentException("Chart header must contain note count, duration, and bpm.");
			}

			int expectedNotes = Integer.parseInt(header[0]);
			long durationMs = Long.parseLong(header[1]);
			double bpm = parseBpm(header[2]);
			int lowerPitchBound;
			int upperPitchBound;
			if (header.length >= 5) {
				lowerPitchBound = Integer.parseInt(header[3]);
				upperPitchBound = Integer.parseInt(header[4]);
			} else {
				lowerPitchBound = Integer.MIN_VALUE;
				upperPitchBound = Integer.MAX_VALUE;
			}

			List<RhythmNote> notes = new ArrayList<>(Math.max(1, expectedNotes));
			String line;
			int startOrder = 0;
			while ((line = nextContentLine(reader)) != null) {
				String[] parts = line.trim().split("\\s+");
				if (parts.length < 3) {
					throw new IllegalArgumentException("Invalid note line: " + line);
				}
				int midiPitch = Integer.parseInt(parts[0]);
				long startTimeMs = Long.parseLong(parts[1]);
				long endTimeMs = Long.parseLong(parts[2]);
				notes.add(new RhythmNote(midiPitch, startTimeMs, endTimeMs, startOrder));
				startOrder++;
			}

			if (notes.size() != expectedNotes) {
				throw new IllegalArgumentException(String.format(Locale.ROOT,
						"Chart header expects %d notes but parsed %d notes.", expectedNotes, notes.size()));
			}

			if (header.length < 5) {
				int actualLowerBound = notes.stream().mapToInt(RhythmNote::getMidiPitch).min().orElse(lowerPitchBound);
				int actualUpperBound = notes.stream().mapToInt(RhythmNote::getMidiPitch).max().orElse(upperPitchBound);
				lowerPitchBound = actualLowerBound;
				upperPitchBound = actualUpperBound;
			}

			return new RhythmChart(sourceName, durationMs, bpm, lowerPitchBound, upperPitchBound, notes);
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected I/O while parsing chart text.", e);
		}
	}

	private static String nextContentLine(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (!line.trim().isEmpty()) {
				return line;
			}
		}
		return null;
	}

	private static double parseBpm(String value) {
		if ("-1".equals(value)) {
			return -1.0;
		}
		return Double.parseDouble(value);
	}
}