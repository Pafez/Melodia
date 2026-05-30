package com.pafez.melodia;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MidiToTextConverter {

    private static final int META_TEMPO = 0x51;
    private static final int NOTE_ON = 0x90;
    private static final int NOTE_OFF = 0x80;
    private static final int DEFAULT_TEMPO_US_PER_QN = 500_000;

    private MidiToTextConverter() {
    }

    public static void main(String[] args) throws IOException, InvalidMidiDataException {
        Path inputDir = args.length > 0 ? Paths.get(args[0]) : Paths.get("assets", "midiUp");
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : Paths.get("assets", "charts");
        double manualBpm = -1.0;
        if (args.length > 2) {
            try {
                manualBpm = Double.parseDouble(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }

        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            throw new IOException("Input MIDI directory does not exist: " + inputDir.toAbsolutePath());
        }

        Files.createDirectories(outputDir);

        try (java.util.stream.Stream<Path> files = Files.list(inputDir)) {
            List<Path> midiFiles = files
                    .filter(Files::isRegularFile)
                    .filter(MidiToTextConverter::isMidiFile)
                    .sorted()
                .collect(Collectors.toList());

            for (Path midiFile : midiFiles) {
                Path outputFile = outputDir.resolve(replaceExtension(midiFile.getFileName().toString(), ".txt"));
                convertSingleFile(midiFile, outputFile, manualBpm);
            }
        }
    }

    public static void convertSingleFile(Path midiFile, Path outputFile, double manualBpm)
            throws IOException, InvalidMidiDataException {
        Sequence sequence = MidiSystem.getSequence(midiFile.toFile());
        Track[] tracks = sequence.getTracks();
        // Select the first track that actually contains note events (ignore empty tracks).
        List<Track> nonEmpty = new ArrayList<>();
        for (Track t : tracks) {
            for (int i = 0; i < t.size(); i++) {
                MidiEvent e = t.get(i);
                if (e.getMessage() instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) e.getMessage();
                    int cmd = sm.getCommand();
                    if (cmd == NOTE_ON || cmd == NOTE_OFF) {
                        nonEmpty.add(t);
                        break;
                    }
                }
            }
        }

        if (nonEmpty.size() == 0) {
            throw new IllegalArgumentException("MIDI contains no note events: " + midiFile);
        }
        if (nonEmpty.size() > 1) {
            throw new IllegalArgumentException("MIDI must contain exactly one non-empty track: " + midiFile);
        }

        Track track = nonEmpty.get(0);
        TickTimeConverter tickTimeConverter;
        double extractedBpm = -1.0;
        if (manualBpm > 0.0) {
            int microsPerQuarter = (int) Math.round(60_000_000.0 / manualBpm);
            tickTimeConverter = TickTimeConverter.fixed(sequence.getResolution(), microsPerQuarter);
        } else {
            tickTimeConverter = TickTimeConverter.fromTrack(sequence, track);
            extractedBpm = 60_000_000.0 / tickTimeConverter.getInitialMicrosPerQuarter();
        }
        List<NoteRecord> notes = extractNotes(track, tickTimeConverter);

        long sequenceDurationMs = Math.round(sequence.getMicrosecondLength() / 1000.0);
        long maxNoteEndMs = notes.stream().mapToLong(NoteRecord::getEndMs).max().orElse(0L);
        long totalDurationMs = Math.max(sequenceDurationMs, maxNoteEndMs);
        int lowerPitch = notes.stream().mapToInt(NoteRecord::getPitch).min().orElseThrow(() ->
            new IllegalArgumentException("MIDI contains no note events: " + midiFile));
        int upperPitch = notes.stream().mapToInt(NoteRecord::getPitch).max().orElseThrow(() ->
            new IllegalArgumentException("MIDI contains no note events: " + midiFile));

        Files.createDirectories(outputFile.getParent());
        double bpmToWrite = manualBpm > 0.0 ? manualBpm : extractedBpm;
        String bpmStr = "-1";
        if (bpmToWrite > 0.0) {
            if (Math.abs(bpmToWrite - Math.round(bpmToWrite)) < 1e-9) {
                bpmStr = String.valueOf((long) Math.round(bpmToWrite));
            } else {
                bpmStr = String.format(java.util.Locale.US, "%.3f", bpmToWrite);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(notes.size() + " " + totalDurationMs + " " + bpmStr + " " + lowerPitch + " " + upperPitch);
            writer.newLine();
            for (NoteRecord note : notes) {
                writer.write(note.getPitch() + " " + note.getStartMs() + " " + note.getEndMs());
                writer.newLine();
            }
        }
    }

    private static List<NoteRecord> extractNotes(Track track, TickTimeConverter tickTimeConverter) {
        Map<Integer, Deque<PendingNote>> activeNotesByPitch = new HashMap<>();
        List<NoteRecord> completed = new ArrayList<>();
        int noteStartOrder = 0;

        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            if (!(event.getMessage() instanceof ShortMessage)) {
                continue;
            }
            ShortMessage shortMessage = (ShortMessage) event.getMessage();

            int command = shortMessage.getCommand();
            int pitch = shortMessage.getData1();
            int velocity = shortMessage.getData2();
            long tick = event.getTick();
            long timeMs = tickTimeConverter.tickToMillis(tick);

            if (command == NOTE_ON && velocity > 0) {
                activeNotesByPitch
                        .computeIfAbsent(pitch, ignored -> new ArrayDeque<>())
                        .addLast(new PendingNote(pitch, timeMs, noteStartOrder));
                noteStartOrder++;
            } else if (command == NOTE_OFF || (command == NOTE_ON && velocity == 0)) {
                Deque<PendingNote> pending = activeNotesByPitch.get(pitch);
                if (pending == null || pending.isEmpty()) {
                    continue;
                }
                PendingNote started = pending.removeFirst();
                completed.add(new NoteRecord(started.getPitch(), started.getStartMs(), timeMs, started.getStartOrder()));
            }
        }

        completed.sort(
                Comparator.comparingLong(NoteRecord::getStartMs)
                        .thenComparingInt(NoteRecord::getStartOrder)
        );
        return completed;
    }

    private static boolean isMidiFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mid") || name.endsWith(".midi");
    }

    private static String replaceExtension(String fileName, String newExtension) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return fileName + newExtension;
        }
        return fileName.substring(0, dotIndex) + newExtension;
    }

    private static final class PendingNote {
        private final int pitch;
        private final long startMs;
        private final int startOrder;

        private PendingNote(int pitch, long startMs, int startOrder) {
            this.pitch = pitch;
            this.startMs = startMs;
            this.startOrder = startOrder;
        }

        private int getPitch() {
            return pitch;
        }

        private long getStartMs() {
            return startMs;
        }

        private int getStartOrder() {
            return startOrder;
        }
    }

    private static final class NoteRecord {
        private final int pitch;
        private final long startMs;
        private final long endMs;
        private final int startOrder;

        private NoteRecord(int pitch, long startMs, long endMs, int startOrder) {
            this.pitch = pitch;
            this.startMs = startMs;
            this.endMs = endMs;
            this.startOrder = startOrder;
        }

        private int getPitch() {
            return pitch;
        }

        private long getStartMs() {
            return startMs;
        }

        private long getEndMs() {
            return endMs;
        }

        private int getStartOrder() {
            return startOrder;
        }
    }

    private static final class TempoPoint {
        private final long tick;
        private final int microsPerQuarter;

        private TempoPoint(long tick, int microsPerQuarter) {
            this.tick = tick;
            this.microsPerQuarter = microsPerQuarter;
        }

        private long getTick() {
            return tick;
        }

        private int getMicrosPerQuarter() {
            return microsPerQuarter;
        }
    }

    private static final class TickTimeConverter {
        private final int resolution;
        private final List<TempoPoint> tempoPoints;
        private final List<Long> cumulativeMicrosAtTempoPoint;

        private TickTimeConverter(int resolution, List<TempoPoint> tempoPoints,
                                  List<Long> cumulativeMicrosAtTempoPoint) {
            this.resolution = resolution;
            this.tempoPoints = tempoPoints;
            this.cumulativeMicrosAtTempoPoint = cumulativeMicrosAtTempoPoint;
        }

        static TickTimeConverter fromTrack(Sequence sequence, Track track) {
            if (sequence.getDivisionType() != Sequence.PPQ) {
                throw new IllegalArgumentException("Only PPQ MIDI division type is supported.");
            }

            List<TempoPoint> points = new ArrayList<>();
            points.add(new TempoPoint(0L, DEFAULT_TEMPO_US_PER_QN));

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (!(event.getMessage() instanceof MetaMessage)) {
                    continue;
                }
                MetaMessage metaMessage = (MetaMessage) event.getMessage();
                if (metaMessage.getType() != META_TEMPO) {
                    continue;
                }

                byte[] data = metaMessage.getData();
                if (data.length < 3) {
                    continue;
                }
                int microsPerQuarter = ((data[0] & 0xFF) << 16)
                        | ((data[1] & 0xFF) << 8)
                        | (data[2] & 0xFF);
                long tick = event.getTick();

                TempoPoint last = points.get(points.size() - 1);
                if (last.getTick() == tick) {
                    points.set(points.size() - 1, new TempoPoint(tick, microsPerQuarter));
                } else {
                    points.add(new TempoPoint(tick, microsPerQuarter));
                }
            }

            List<Long> cumulativeMicros = new ArrayList<>(points.size());
            long accumulated = 0L;
            cumulativeMicros.add(accumulated);

            for (int i = 1; i < points.size(); i++) {
                TempoPoint previous = points.get(i - 1);
                TempoPoint current = points.get(i);
                long deltaTicks = current.getTick() - previous.getTick();
                accumulated += ticksToMicros(deltaTicks, previous.getMicrosPerQuarter(), sequence.getResolution());
                cumulativeMicros.add(accumulated);
            }

            return new TickTimeConverter(sequence.getResolution(), points, cumulativeMicros);
        }

        static TickTimeConverter fixed(int resolution, int microsPerQuarter) {
            List<TempoPoint> points = new ArrayList<>();
            points.add(new TempoPoint(0L, microsPerQuarter));
            List<Long> cumulative = new ArrayList<>();
            cumulative.add(0L);
            return new TickTimeConverter(resolution, points, cumulative);
        }

        int getInitialMicrosPerQuarter() {
            return tempoPoints.get(0).getMicrosPerQuarter();
        }

        long tickToMillis(long tick) {
            int tempoIndex = findTempoIndex(tick);
            TempoPoint point = tempoPoints.get(tempoIndex);
            long baseMicros = cumulativeMicrosAtTempoPoint.get(tempoIndex);
            long deltaTicks = tick - point.getTick();
            long micros = baseMicros + ticksToMicros(deltaTicks, point.getMicrosPerQuarter(), resolution);
            return Math.round(micros / 1000.0);
        }

        private int findTempoIndex(long tick) {
            int low = 0;
            int high = tempoPoints.size() - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                long midTick = tempoPoints.get(mid).getTick();
                if (midTick == tick) {
                    return mid;
                }
                if (midTick < tick) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return Math.max(0, high);
        }

        private static long ticksToMicros(long ticks, int microsPerQuarter, int resolution) {
            if (ticks <= 0) {
                return 0L;
            }
            return Math.round((ticks * (double) microsPerQuarter) / resolution);
        }
    }
}