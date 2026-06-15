package com.healyn.auth.adapter;

import com.healyn.auth.config.OtpMailProperties;
import com.healyn.auth.domain.OtpChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpOtpSenderTest {

    private final OtpMailProperties props = new OtpMailProperties("no-reply@healyn.app", "Your code");

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender bean) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    @Test
    void sendsEmailWithCodeAndDeclaredChannel() {
        JavaMailSender mail = mock(JavaMailSender.class);
        SmtpOtpSender sender = new SmtpOtpSender(provider(mail), props);
        sender.verifyConfigured();

        assertThat(sender.channel()).isEqualTo(OtpChannel.EMAIL);
        sender.send("patient@example.com", "654321");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("patient@example.com");
        assertThat(msg.getFrom()).isEqualTo("no-reply@healyn.app");
        assertThat(msg.getSubject()).isEqualTo("Your code");
        assertThat(msg.getText()).contains("654321");
    }

    @Test
    void failsFastWhenSmtpNotConfigured() {
        SmtpOtpSender sender = new SmtpOtpSender(provider(null), props);
        assertThatThrownBy(sender::verifyConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP");
    }
}
