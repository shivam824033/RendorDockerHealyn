package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Production {@link OtpSender}: routes each OTP to the channel-specific {@link ChannelOtpSender}.
 * Replaces the dev-only {@link LoggingOtpSender} outside local/test (audit §11 item 5).
 */
@Component
@Profile("!local & !test")
public class CompositeOtpSender implements OtpSender {

    private final Map<OtpChannel, ChannelOtpSender> byChannel;

    CompositeOtpSender(List<ChannelOtpSender> senders) {
        Map<OtpChannel, ChannelOtpSender> map = new EnumMap<>(OtpChannel.class);
        for (ChannelOtpSender sender : senders) {
            map.put(sender.channel(), sender);
        }
        this.byChannel = map;
    }

    @Override
    public void send(String target, OtpChannel channel, String code) {
        ChannelOtpSender sender = byChannel.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No OTP sender configured for channel " + channel);
        }
        sender.send(target, code);
    }
}
