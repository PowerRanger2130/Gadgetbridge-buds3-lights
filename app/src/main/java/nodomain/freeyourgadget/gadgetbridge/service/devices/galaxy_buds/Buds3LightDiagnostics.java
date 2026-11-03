/* SPDX-License-Identifier: AGPL-3.0-or-later */
package nodomain.freeyourgadget.gadgetbridge.service.devices.galaxy_buds;

import static nodomain.freeyourgadget.gadgetbridge.util.CheckSums.crc16_ccitt;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Bounded, in-memory diagnostics. Device addresses are keys, never report contents. */
public final class Buds3LightDiagnostics {
    public static final String OPEN = "buds3_debug_open";
    public static final String RUN = "buds3_debug_run";
    public static final String CANCEL = "buds3_debug_cancel";
    public static final String PAYLOAD = "buds3_debug_payload";
    public static final String FLAGS = "buds3_debug_flags";
    public static final String REPEAT = "buds3_debug_repeat";
    public static final String SDK = "buds3_debug_sdk";
    public static final String STOP_ON = "buds3_debug_stop_on";
    public static final String DELAY = "buds3_debug_delay";
    public static final String EXPERIMENT = "buds3_debug_experiment";
    private static final LinkedHashMap<String, Buds3LightDiagnostics> SESSIONS = new LinkedHashMap<>();

    public static synchronized Buds3LightDiagnostics forDevice(final String address) {
        Buds3LightDiagnostics session = SESSIONS.get(address);
        if (session == null) {
            if (SESSIONS.size() >= 8) SESSIONS.remove(SESSIONS.keySet().iterator().next());
            session = new Buds3LightDiagnostics();
            SESSIONS.put(address, session);
        }
        return session;
    }

    private final ArrayDeque<String> entries = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private int characters, dropped, rxFrames, rxChunks, queued, invalid;
    private boolean capturing = true;
    private long revision, onReportCount;
    private String lastTx = "none", lastLight = "none", previousLight = "none", lastReply = "none";
    private byte[] lastLightPayload;

    public synchronized void setCapturing(final boolean enabled) {
        capturing = enabled;
        note(enabled ? "Capture resumed" : "Capture paused");
    }

    public synchronized boolean isCapturing() { return capturing; }
    public synchronized long revision() { return revision; }
    public synchronized long onReportCount() { return onReportCount; }
    public synchronized byte[] lastLightPayload() {
        return lastLightPayload == null ? null : lastLightPayload.clone();
    }

    public synchronized void clear() {
        entries.clear();
        characters = dropped = rxFrames = rxChunks = queued = invalid = 0;
        lastTx = lastLight = previousLight = lastReply = "none";
        lastLightPayload = null;
        note("Capture cleared. No command sent.");
    }

    public synchronized void note(final String message) { append("NOTE " + message); }

    public synchronized void rxChunk(final byte[] data) {
        if (!capturing) return;
        rxChunks++;
        append("RX chunk " + hex(data));
    }

    public synchronized void invalid(final String reason) {
        if (!capturing) return;
        invalid++;
        append("RX rejected " + reason);
    }

    public synchronized void queued(final byte[] frame, final String label) {
        queued++;
        lastTx = describe(frame);
        append("TX QUEUED " + label + " | " + lastTx);
    }

    public synchronized void received(final byte[] frame) {
        final String description = describe(frame);
        final int command = frame[3] & 255;
        final byte[] payload = Arrays.copyOfRange(frame, 4, frame.length - 3);
        if (command == 0x8e) {
            previousLight = lastLight;
            lastLight = description;
            lastLightPayload = payload;
            if (frame[2] == 0 && payload.length == 1 && payload[0] == 1) {
                onReportCount++;
            }
        }
        if ((frame[2] & 0x20) != 0 || command == 0x51 || command == 0x42) lastReply = description;
        if (capturing) {
            rxFrames++;
            append("RX FRAME " + description);
        }
    }

    private void append(final String text) {
        // User notes and device frames are bounded independently of input size.
        final String bounded = text.length() > 5000 ? text.substring(0, 5000) + " [truncated]" : text;
        final String line = clock.format(new Date()) + " " + bounded;
        entries.addLast(line);
        characters += line.length();
        while (entries.size() > 1200 || characters > 180000) {
            characters -= entries.removeFirst().length();
            dropped++;
        }
        revision++;
    }

    public synchronized String report() {
        return "Buds3 light protocol capture\n"
                + "Capture: " + (capturing ? "running" : "paused")
                + "; RX chunks=" + rxChunks + "; valid frames=" + rxFrames
                + "; rejected=" + invalid + "; TX queued=" + queued
                + "; older entries dropped=" + dropped + "\n"
                + "TX QUEUED means submitted to the Bluetooth queue, not confirmed illumination.\n"
                + "Response/ACK frames are not accepted as proof of LED state.\n"
                + "Last TX: " + lastTx + "\nLast lighting RX: " + lastLight
                + "\nPrevious lighting RX: " + previousLight
                + "\nLast response: " + lastReply + "\n\n" + String.join("\n", entries);
    }

    public static String hex(final byte[] data) {
        final StringBuilder out = new StringBuilder();
        for (byte value : data) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format(Locale.ROOT, "%02X", value & 255));
        }
        return out.toString();
    }

    public static byte[] parsePayload(final String text) {
        final String compact = text.trim().replaceAll("\\s+", "");
        if (compact.length() > 64 || (compact.length() & 1) != 0 || !compact.matches("[0-9a-fA-F]*")) {
            throw new IllegalArgumentException("Use up to 32 hexadecimal bytes, for example 01 or 01 00. Empty is allowed.");
        }
        final byte[] result = new byte[compact.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    public static byte[] frame(final int command, final byte[] payload, final int flags) {
        if (command != 0x8e && command != 0x90 && command != 0x24 && command != 0x28 && command != 0x88) {
            throw new IllegalArgumentException("Only lighting, touch settings and firmware-read commands are supported.");
        }
        if (flags != 0 && flags != 0x20) throw new IllegalArgumentException("Use request 00 or response 20 flags.");
        if ((command == 0x88 && (payload.length != 3 || payload[0] != 1 || (payload[1] != 1 && payload[1] != 2)))
                || payload.length > 32 || (command == 0x90 && payload.length != 10)
                || ((command == 0x24 || command == 0x28) && payload.length != 0)) {
            throw new IllegalArgumentException("Invalid payload length for this command.");
        }
        final byte[] frame = new byte[payload.length + 7];
        frame[0] = (byte) 0xfd;
        frame[1] = (byte) (payload.length + 3);
        frame[2] = (byte) flags;
        frame[3] = (byte) command;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        final int crc = crc16_ccitt(Arrays.copyOfRange(frame, 3, frame.length - 3));
        frame[frame.length - 3] = (byte) crc;
        frame[frame.length - 2] = (byte) (crc >> 8);
        frame[frame.length - 1] = (byte) 0xdd;
        return frame;
    }

    private static String describe(final byte[] frame) {
        final int id = frame[3] & 255;
        final byte[] payload = Arrays.copyOfRange(frame, 4, frame.length - 3);
        String detail = "";
        if (id == 0x8e && payload.length == 1) {
            final int state = payload[0] & 255;
            detail = frame[2] != 0 ? " response, meaning unverified" : state == 0 ? " lighting OFF"
                    : state == 1 ? " lighting ON report" : state == 2 || state == 3 ? " low-battery report" : " unknown lighting value";
        } else if (id == 0x51 && payload.length >= 2) {
            detail = String.format(Locale.ROOT, " RESP command=%02X result=%02X", payload[0] & 255, payload[1] & 255);
        } else if (id == 0x42 && payload.length >= 1) {
            detail = String.format(Locale.ROOT, " ACK command=%02X parameters=[%s]", payload[0] & 255,
                    hex(Arrays.copyOfRange(payload, 1, payload.length)));
        }
        return String.format(Locale.ROOT, "id=%02X flags=%02X payload=[%s]%s | %s", id, frame[2] & 255, hex(payload), detail, hex(frame));
    }

    /** RFCOMM is a stream: one read may contain half a frame or several frames. */
    public static final class Stream {
        private byte[] pending = new byte[0];
        public void clear() { pending = new byte[0]; }
        public List<byte[]> accept(final byte[] chunk, final Consumer<String> rejected) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(pending, 0, pending.length);
            bytes.write(chunk, 0, chunk.length);
            final byte[] data = bytes.toByteArray();
            final List<byte[]> frames = new ArrayList<>();
            int offset = 0;
            while (offset < data.length) {
                if (data[offset] != (byte) 0xfd) { offset++; continue; }
                if (data.length - offset < 4) break;
                final int size = (data[offset + 1] & 255) | ((data[offset + 2] & 3) << 8);
                if (size < 3) { rejected.accept("length below 3"); offset++; continue; }
                final int total = size + 4;
                if (data.length - offset < total) {
                    // A corrupt length must not hide later complete frames indefinitely.
                    int next = offset + 1;
                    for (; next + 7 <= data.length; next++) {
                        if (data[next] != (byte) 0xfd) continue;
                        final int nextSize = (data[next + 1] & 255) | ((data[next + 2] & 3) << 8);
                        final int end = next + nextSize + 4;
                        if (nextSize < 3 || end > data.length || data[end - 1] != (byte) 0xdd) continue;
                        final int nextCrc = crc16_ccitt(Arrays.copyOfRange(data, next + 3, end - 3)) & 0xffff;
                        if (nextCrc == ((data[end - 3] & 255) | ((data[end - 2] & 255) << 8))) break;
                    }
                    if (next + 7 > data.length) break;
                    rejected.accept("resynchronized after an incomplete or corrupt length");
                    offset = next;
                    continue;
                }
                final byte[] candidate = Arrays.copyOfRange(data, offset, offset + total);
                final int crc = crc16_ccitt(Arrays.copyOfRange(candidate, 3, total - 3)) & 0xffff;
                final int received = (candidate[total - 3] & 255) | ((candidate[total - 2] & 255) << 8);
                if (candidate[total - 1] != (byte) 0xdd || crc != received) {
                    rejected.accept("CRC or trailer: " + hex(candidate));
                    offset++;
                    continue;
                }
                frames.add(candidate);
                offset += total;
            }
            pending = Arrays.copyOfRange(data, offset, data.length);
            // A protocol frame is at most 1027 bytes. Incomplete junk cannot grow indefinitely.
            if (pending.length > 1027) { rejected.accept("receive buffer overflow"); pending = new byte[0]; }
            return frames;
        }
    }
}
