package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String target, OtpChannel channel, String code) {
        log.warn("[DEV OTP] channel={} target={} code={}", channel, target, code);
    }
}
