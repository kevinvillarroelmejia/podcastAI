package com.podcastai.tts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class Mp3Test {

    @Test
    void silenceHasRequestedDuration() {
        byte[] audio = Mp3.silence(2000);
        assertThat(Mp3.durationMs(audio)).isBetween(1970L, 2030L);
    }

    @Test
    void concatAddsDurationsAndSkipsId3() {
        byte[] id3 = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 2, 0, 0};
        byte[] a = Mp3.silence(1000);
        byte[] withTag = new byte[id3.length + a.length];
        System.arraycopy(id3, 0, withTag, 0, id3.length);
        System.arraycopy(a, 0, withTag, id3.length, a.length);
        byte[] joined = Mp3.concat(List.of(withTag, Mp3.silence(500)));
        assertThat(Mp3.durationMs(joined)).isBetween(1450L, 1550L);
    }

    @Test
    void garbageGivesZero() {
        assertThat(Mp3.durationMs(new byte[] {1, 2, 3, 4, 5})).isZero();
    }
}
