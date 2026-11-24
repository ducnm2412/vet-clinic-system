package com.vetclinic.chat.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Lấy IP khách cho hạn mức vãng lai từ X-Forwarded-For (app.chat.rate-limit.trusted-proxy-hops). */
class ClientIpTest {

    @ParameterizedTest(name = "[{index}] xff=''{0}'' remote={1} hops={2} -> {3}")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            // hops = 0: phần tử đầu — khách tự đặt được
            "9.9.9.9, 10.0.0.1|172.18.0.5|0|9.9.9.9",
            "NULL|172.18.0.5|0|172.18.0.5",
            "'   '|172.18.0.5|0|172.18.0.5",
            // hops = 1 (mặc định): api-gateway nối IP nó thấy → lấy phần tử cuối, giá trị giả ở đầu bị bỏ qua
            "1.1.1.1, 2.2.2.2, 9.9.9.9|172.18.0.5|1|9.9.9.9",
            "9.9.9.9|172.18.0.5|1|9.9.9.9",
            // trình duyệt → Next.js (/api rewrite, không ghi IP) → gateway: phần tử cuối là IP container frontend
            "6.6.6.6, 172.18.0.7|172.18.0.5|1|172.18.0.7",
            // hops = 2: phần tử thứ 2 từ phải
            "6.6.6.6, 9.9.9.9, 172.18.0.7|172.18.0.5|2|9.9.9.9",
            // ít phần tử hơn hops (không qua đủ proxy): lấy phần tử đầu
            "9.9.9.9|172.18.0.5|3|9.9.9.9",
            // header bẩn: không được làm lỗi 500
            "','|172.18.0.5|1|172.18.0.5",
            "' , '|172.18.0.5|1|172.18.0.5",
            // peer nội bộ khác: loopback (IPv4/IPv6), 10/8, 192.168/16
            "6.6.6.6, 9.9.9.9|127.0.0.1|1|9.9.9.9",
            "6.6.6.6, 9.9.9.9|0:0:0:0:0:0:0:1|1|9.9.9.9",
            "6.6.6.6, 9.9.9.9|10.1.2.3|1|9.9.9.9",
            "6.6.6.6, 9.9.9.9|192.168.1.20|1|9.9.9.9"
    })
    void derivesClientIpFromTrustedHop(String forwarded, String remoteAddr, int hops, String expected) {
        assertThat(ChatController.clientIp(forwarded, remoteAddr, hops)).isEqualTo(expected);
    }

    /** Gọi thẳng cổng 8092 từ Internet (không qua gateway): header do khách tự đặt, không tin. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void headerFromPublicPeerIsIgnored(int hops) {
        assertThat(ChatController.clientIp("1.1.1.1, 2.2.2.2", "203.0.113.9", hops)).isEqualTo("203.0.113.9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.9", "8.8.8.8", "2001:db8::1", "172.32.0.1", "", "localhost", "deadbeef",
            "fe80::1%eth0"})
    void nonInternalOrNonLiteralPeerIsNotTrusted(String remoteAddr) {
        assertThat(ChatController.isInternalPeer(remoteAddr)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "10.0.0.1", "172.16.0.1", "172.31.255.254",
            "192.168.0.10", "::ffff:172.18.0.5"})
    void internalPeerIsTrusted(String remoteAddr) {
        assertThat(ChatController.isInternalPeer(remoteAddr)).isTrue();
    }

    @Test
    void nullPeerIsNotTrusted() {
        assertThat(ChatController.isInternalPeer(null)).isFalse();
        assertThat(ChatController.clientIp("9.9.9.9", null, 1)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0", "1"})
    void overlongValueIsTruncated(int hops) {
        String longValue = "x".repeat(200);
        assertThat(ChatController.clientIp(longValue, "10.0.0.1", hops)).hasSize(45);
    }
}
