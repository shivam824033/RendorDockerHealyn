package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeOtpSenderTest {

    private static ChannelOtpSender stub(OtpChannel channel, AtomicReference<String> sink) {
        return new ChannelOtpSender() {
            @Override public OtpChannel channel() { return channel; }
            @Override public void send(String target, String code) { sink.set(channel + ":" + target + ":" + code); }
        };
    }

    @Test
    void routesToTheMatchingChannel() {
        AtomicReference<String> email = new AtomicReference<>();
        AtomicReference<String> sms = new AtomicReference<>();
        CompositeOtpSender composite = new CompositeOtpSender(
                List.of(stub(OtpChannel.EMAIL, email), stub(OtpChannel.SMS, sms)));

        composite.send("a@b.com", OtpChannel.EMAIL, "123456");

        assertThat(email.get()).isEqualTo("EMAIL:a@b.com:123456");
        assertThat(sms.get()).isNull();
    }

    @Test
    void throwsWhenNoSenderForChannel() {
        CompositeOtpSender composite = new CompositeOtpSender(
                List.of(stub(OtpChannel.EMAIL, new AtomicReference<>())));

        assertThatThrownBy(() -> composite.send("+15555550123", OtpChannel.SMS, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS");
    }
}
