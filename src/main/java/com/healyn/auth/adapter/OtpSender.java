package com.healyn.auth.adapter;

import com.healyn.auth.domain.OtpChannel;

public interface OtpSender {
    void send(String target, OtpChannel channel, String code);
}
