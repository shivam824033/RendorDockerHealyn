package com.healyn.auth.adapter;

import com.healyn.auth.config.OtpMailProperties;
import com.healyn.auth.domain.OtpChannel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real EMAIL-channel OTP delivery over SMTP (audit §11 item 5). Active outside local/test, where
 * {@link LoggingOtpSender} prints to the console instead. Boot only auto-configures a
 * {@link JavaMailSender} when {@code spring.mail.host} is set, so this fails fast at startup when
 * SMTP is unconfigured rather than silently dropping email OTPs (mirrors {@code JwtKeyProvider}).
 */
@Component
@Profile("!local & !test")
public class SmtpOtpSender implements ChannelOtpSender {

    private final JavaMailSender mailSender;
    private final OtpMailProperties props;

    SmtpOtpSender(ObjectProvider<JavaMailSender> mailSender, OtpMailProperties props) {
        this.mailSender = mailSender.getIfAvailable();
        this.props = props;
    }

    @PostConstruct
    void verifyConfigured() {
        if (mailSender == null) {
            throw new IllegalStateException(
                    "OTP email delivery requires SMTP configuration (set HEALYN_SMTP_HOST and credentials); "
                            + "refusing to start so email OTPs are not silently dropped.");
        }
        if (props.from() == null || props.from().isBlank()) {
            throw new IllegalStateException("healyn.otp.email.from must be set for OTP email delivery.");
        }
    }

    @Override
    public OtpChannel channel() {
        return OtpChannel.EMAIL;
    }

    @Override
    public void send(String target, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.from());
        message.setTo(target);
        message.setSubject(props.subject());
        message.setText("Your Healyn verification code is " + code
                + ".\n\nIt expires in 5 minutes. If you did not request this, you can ignore this email.");
        // Never log the code (Hard Rule #3).
        mailSender.send(message);
    }
}
