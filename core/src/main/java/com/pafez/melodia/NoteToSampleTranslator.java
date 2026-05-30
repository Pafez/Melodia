package com.pafez.melodia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NoteToSampleTranslator {

    private final List<Integer> availableNotes;

    public NoteToSampleTranslator(Set<Integer> availableNotes) {
        if (availableNotes == null || availableNotes.isEmpty()) {
            throw new IllegalArgumentException("Available notes cannot be empty.");
        }
        this.availableNotes = new ArrayList<>(new HashSet<>(availableNotes));
        this.availableNotes.sort(Comparator.naturalOrder());
    }

    public int translate(int midiNote) {
        int bestNote = availableNotes.get(0);
        int bestDistance = Math.abs(bestNote - midiNote);

        for (int candidate : availableNotes) {
            int distance = Math.abs(candidate - midiNote);
            if (distance < bestDistance || (distance == bestDistance && candidate < bestNote)) {
                bestNote = candidate;
                bestDistance = distance;
            }
        }

        return bestNote;
    }

    public boolean hasExactMatch(int midiNote) {
        return Collections.binarySearch(availableNotes, midiNote) >= 0;
    }

    public List<Integer> getAvailableNotes() {
        return Collections.unmodifiableList(availableNotes);
    }
}