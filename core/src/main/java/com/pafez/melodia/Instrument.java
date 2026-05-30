package com.pafez.melodia;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Instrument implements Disposable {

    private static final Pattern NOTE_FILE_PATTERN = Pattern.compile("^([A-Ga-g])([#b]?)(\\d+)$");
    private static final Map<String, Integer> NATURAL_SEMITONES;
    private static final Map<String, Integer> FLAT_SEMITONES;
    private static final Map<String, Integer> SHARP_SEMITONES;

    static {
        Map<String, Integer> natural = new HashMap<>();
        natural.put("C", 0);
        natural.put("D", 2);
        natural.put("E", 4);
        natural.put("F", 5);
        natural.put("G", 7);
        natural.put("A", 9);
        natural.put("B", 11);
        NATURAL_SEMITONES = Collections.unmodifiableMap(natural);

        Map<String, Integer> flat = new HashMap<>();
        flat.put("Ab", 8);
        flat.put("Bb", 10);
        flat.put("Db", 1);
        flat.put("Eb", 3);
        flat.put("Gb", 6);
        FLAT_SEMITONES = Collections.unmodifiableMap(flat);

        Map<String, Integer> sharp = new HashMap<>();
        sharp.put("C#", 1);
        sharp.put("D#", 3);
        sharp.put("F#", 6);
        sharp.put("G#", 8);
        sharp.put("A#", 10);
        SHARP_SEMITONES = Collections.unmodifiableMap(sharp);
    }

    private final String instrumentPath;
    private final Map<Integer, Sound> soundsByMidiNote;
    private final NoteToSampleTranslator noteTranslator;

    private Instrument(String instrumentPath, Map<Integer, Sound> soundsByMidiNote) {
        this.instrumentPath = instrumentPath;
        this.soundsByMidiNote = soundsByMidiNote;
        this.noteTranslator = new NoteToSampleTranslator(soundsByMidiNote.keySet());
    }

    public static Instrument load(String instrumentPath) {
        String normalizedPath = normalizeInstrumentPath(instrumentPath);
        FileHandle root = Gdx.files.internal(normalizedPath);
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException("Instrument folder not found: " + normalizedPath);
        }

        FileHandle[] files = root.list();
        Arrays.sort(files, Comparator.comparing(FileHandle::name));

        Map<Integer, Sound> sounds = new HashMap<>();
        for (FileHandle file : files) {
            if (file.isDirectory()) {
                continue;
            }
            Integer midiNote = parseMidiNoteFromFileName(file.nameWithoutExtension());
            if (midiNote == null) {
                continue;
            }
            sounds.put(midiNote, Gdx.audio.newSound(file));
        }

        if (sounds.isEmpty()) {
            throw new IllegalStateException("No playable note samples were found in: " + normalizedPath);
        }

        return new Instrument(normalizedPath, sounds);
    }

    public static Instrument loadPiano() {
        return load("audio/instruments/piano-mp3");
    }

    public String getInstrumentPath() {
        return instrumentPath;
    }

    public boolean hasNote(int midiNote) {
        return soundsByMidiNote.containsKey(midiNote);
    }

    public Set<Integer> getAvailableNotes() {
        return Collections.unmodifiableSet(new HashSet<>(soundsByMidiNote.keySet()));
    }

    public Sound getSound(int midiNote) {
        int resolvedNote = noteTranslator.translate(midiNote);
        Sound sound = soundsByMidiNote.get(resolvedNote);
        if (sound == null) {
            throw new IllegalArgumentException("No sample loaded for MIDI note " + midiNote + " in " + instrumentPath);
        }
        return sound;
    }

    public int getTranslatedNote(int midiNote) {
        return noteTranslator.translate(midiNote);
    }

    public long play(int midiNote) {
        return getSound(midiNote).play();
    }

    public long play(int midiNote, float volume) {
        return getSound(midiNote).play(volume);
    }

    public void stop(int midiNote) {
        getSound(midiNote).stop();
    }

    public void stopAll() {
        for (Sound sound : soundsByMidiNote.values()) {
            sound.stop();
        }
    }

    @Override
    public void dispose() {
        for (Sound sound : soundsByMidiNote.values()) {
            sound.dispose();
        }
        soundsByMidiNote.clear();
    }

    private static String normalizeInstrumentPath(String instrumentPath) {
        if (instrumentPath == null || instrumentPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Instrument path cannot be empty.");
        }

        String normalized = instrumentPath.replace('\\', '/').trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Integer parseMidiNoteFromFileName(String fileName) {
        Matcher matcher = NOTE_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }

        String noteName = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2);
        int octave = Integer.parseInt(matcher.group(3));
        Integer semitone = NATURAL_SEMITONES.get(noteName);
        if (semitone == null) {
            semitone = FLAT_SEMITONES.get(noteName);
        }
        if (semitone == null) {
            semitone = SHARP_SEMITONES.get(noteName);
        }
        if (semitone == null) {
            return null;
        }

        return (octave + 1) * 12 + semitone;
    }
}