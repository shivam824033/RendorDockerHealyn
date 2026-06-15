package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase-1 seam for SMS-channel OTP. No SMS provider is wired yet, so this fails loudly rather than
 * silently dropping a phone-channel OTP. Replace with a real provider adapter (e.g. MSG91 / Twilio)
 * to enable phone-based registration and password reset (audit §11 item 5).
 */
@Component
@Profile("!local & !test")
public class SmsOtpSender implements ChannelOtpSender {

    @Override
    public OtpChannel channel() {
        return OtpChannel.SMS;
    }

    @Override
    public void send(String target, String code) {
        throw new IllegalStateException(
                "SMS OTP delivery is not configured. Wire an SMS provider before enabling "
                        + "phone-based signup / password reset.");
    }
}
