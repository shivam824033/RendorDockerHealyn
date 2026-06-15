package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;

/** Delivers an OTP code over a single channel. Composed by {@link CompositeOtpSender}. */
public interface ChannelOtpSender {

    OtpChannel channel();

    void send(String target, String code);
}
