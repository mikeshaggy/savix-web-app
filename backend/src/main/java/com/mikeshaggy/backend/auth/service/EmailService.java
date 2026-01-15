package com.mikeshaggy.backend.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${auth.reset-token.ttl-seconds}")
    private int resetTokenTtlSeconds;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        try {
            String resetLink = frontendUrl + "/auth/reset-password?token=" + resetToken;
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Reset Your Password - " + fromName);
            helper.setText(buildPasswordResetEmailBody(recipientEmail, resetLink), true);

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", recipientEmail);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", recipientEmail, e);
            throw new EmailSendException("Failed to send password reset email", e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to: {}", recipientEmail, e);
            throw new EmailSendException("Unexpected error sending email", e);
        }
    }

    private String buildPasswordResetEmailBody(String userEmail, String resetLink) {
        Context context = new Context();
        context.setVariable("appName", fromName);
        context.setVariable("userEmail", userEmail);
        context.setVariable("resetUrl", resetLink);
        context.setVariable("expiresIn", resetTokenTtlSeconds / 60);
        context.setVariable("dateTime", LocalDateTime.now().format(DTF));
        context.setVariable("year", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", fromEmail);
        
        return templateEngine.process("password-reset", context);
    }

    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
