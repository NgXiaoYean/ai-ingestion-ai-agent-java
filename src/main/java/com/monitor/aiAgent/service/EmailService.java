package com.monitor.aiAgent.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // Simple text email, but converts AI markdown to beautiful HTML
    public void sendAlert(String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setTo("xyng-wm24@student.tarc.edu.my");
            helper.setSubject(subject);

            // Format basic markdown logic to make it beautiful in the email client
            String htmlMessage = message
                    .replace("\n", "<br/>") // Line breaks
                    .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // Bold text
                    .replaceAll("(?m)^\\*(.*?)$", "&bull; $1") // Bullet points via *
                    .replaceAll("(?m)^-(.*?)$", "&bull; $1"); // Bullet points via -

            helper.setText(
                    "<html><body style='font-family: Arial, sans-serif; font-size: 14px; line-height: 1.6; color: #333;'>"
                            + htmlMessage + "</body></html>",
                    true); // true = HTML
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}