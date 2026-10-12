package com.cyberspectraa.cyberserver.voice;

/**
 * Stateful 48 kHz -> 16 kHz streaming PCM decimator for Opus microphone audio.
 *
 * A 49-tap Blackman-windowed low-pass filter attenuates frequencies above the
 * 16 kHz output Nyquist frequency instead of merely averaging groups of three.
 * State carries across 20 ms microphone frames so boundary samples are not
 * corrupted by resetting the filter for each packet.
 *
 * Each instance is owned by exactly one speech-recognition worker/session.
 */
final class VoicePcmResampler {
    private static final int DECIMATION = 3;
    private static final int TAPS = 49;
    private static final double CUTOFF_HZ = 7000.0D;
    private static final double INPUT_RATE_HZ = 48000.0D;
    private static final double[] COEFFICIENTS = makeFilter();

    private final short[] history = new short[TAPS];
    private int cursor;
    private int phase;

    short[] convert(short[] input) {
        if (input == null || input.length == 0) return new short[0];
        short[] output = new short[(input.length + DECIMATION - 1) / DECIMATION + 1];
        int written = 0;
        for (short sample : input) {
            history[cursor] = sample;
            cursor = (cursor + 1) % TAPS;
            if (phase == 0) {
                double sum = 0.0D;
                int pos = cursor;
                for (double coefficient : COEFFICIENTS) {
                    pos = (pos + TAPS - 1) % TAPS;
                    sum += history[pos] * coefficient;
                }
                int value = (int) Math.round(sum);
                output[written++] = (short) Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, value));
            }
            phase = (phase + 1) % DECIMATION;
        }
        return java.util.Arrays.copyOf(output, written);
    }

    private static double[] makeFilter() {
        double[] weights = new double[TAPS];
        double fc = CUTOFF_HZ / INPUT_RATE_HZ;
        int middle = (TAPS - 1) / 2;
        double total = 0.0D;
        for (int i = 0; i < TAPS; i++) {
            int offset = i - middle;
            double sinc = offset == 0
                    ? 2.0D * fc
                    : Math.sin(2.0D * Math.PI * fc * offset)
                    / (Math.PI * offset);
            double window = 0.42D
                    - 0.5D * Math.cos(2.0D * Math.PI * i / (TAPS - 1))
                    + 0.08D * Math.cos(4.0D * Math.PI * i / (TAPS - 1));
            weights[i] = sinc * window;
            total += weights[i];
        }
        for (int i = 0; i < TAPS; i++) weights[i] /= total;
        return weights;
    }
}
