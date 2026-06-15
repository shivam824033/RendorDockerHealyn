package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsOtpSenderTest {

    @Test
    void declaresSmsChannel() {
        assertThat(new SmsOtpSender().channel()).isEqualTo(OtpChannel.SMS);
    }

    @Test
    void failsLoudlyUntilProviderWired() {
        assertThatThrownBy(() -> new SmsOtpSender().send("+15555550123", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS OTP delivery is not configured");
    }
}
