package com.podcastai.tts;

import java.io.ByteArrayOutputStream;

/**
 * Utilidades mínimas de MP3: medir la duración recorriendo las tramas y unir fragmentos.
 */
public final class Mp3 {

    private static final int[][] BITRATES_KBPS = {
        // MPEG-1 Layer III
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1},
        // MPEG-2 / 2.5 Layer III
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1},
    };
    private static final int[][] SAMPLE_RATES = {
        {44100, 48000, 32000, -1}, // MPEG-1
        {22050, 24000, 16000, -1}, // MPEG-2
        {11025, 12000, 8000, -1},  // MPEG-2.5
    };

    private Mp3() {
    }

    /** Duración en milisegundos. Si el audio no se puede analizar, devuelve 0. */
    public static long durationMs(byte[] data) {
        int pos = skipId3(data);
        double seconds = 0;
        boolean valid = true;
        while (valid && pos + 4 <= data.length) {
            int frameLength = 0;
            int samples = 0;
            int sampleRate = 0;
            int b1 = data[pos] & 0xFF;
            int b2 = data[pos + 1] & 0xFF;
            int b3 = data[pos + 2] & 0xFF;
            boolean sync = b1 == 0xFF && (b2 & 0xE0) == 0xE0 && ((b2 >> 1) & 0x03) == 0x01;
            if (sync) {
                int versionBits = (b2 >> 3) & 0x03; // 3 = MPEG-1, 2 = MPEG-2, 0 = MPEG-2.5
                int versionRow = versionBits == 3 ? 0 : (versionBits == 2 ? 1 : 2);
                int bitrate = BITRATES_KBPS[versionRow == 0 ? 0 : 1][(b3 >> 4) & 0x0F];
                sampleRate = SAMPLE_RATES[versionRow][(b3 >> 2) & 0x03];
                int padding = (b3 >> 1) & 0x01;
                samples = versionRow == 0 ? 1152 : 576;
                if (bitrate > 0 && sampleRate > 0) {
                    frameLength = (samples / 8 * bitrate * 1000) / sampleRate + padding;
                }
            }
            if (frameLength > 0) {
                seconds += (double) samples / sampleRate;
                pos += frameLength;
            } else {
                valid = false;
            }
        }
        return Math.round(seconds * 1000);
    }

    /** Une varios MP3 del mismo formato quitando las etiquetas ID3 iniciales. */
    public static byte[] concat(Iterable<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            int start = skipId3(part);
            out.write(part, start, part.length - start);
        }
        return out.toByteArray();
    }

    static int skipId3(byte[] data) {
        int offset = 0;
        boolean hasId3 = data.length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3';
        if (hasId3) {
            int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            offset = Math.min(data.length, 10 + size);
        }
        return offset;
    }

    /** MP3 en silencio (MPEG-1, 32 kbps, 44,1 kHz, mono) de la duración pedida. */
    public static byte[] silence(long millis) {
        int frameBytes = 104; // 144 * 32000 / 44100
        long frames = Math.max(1, Math.round(millis / (1152.0 / 44100 * 1000)));
        byte[] out = new byte[(int) (frames * frameBytes)];
        for (long f = 0; f < frames; f++) {
            int at = (int) (f * frameBytes);
            out[at] = (byte) 0xFF;
            out[at + 1] = (byte) 0xFB;
            out[at + 2] = (byte) 0x10;
            out[at + 3] = (byte) 0xC0;
        }
        return out;
    }
}
