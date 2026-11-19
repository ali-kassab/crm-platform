package com.crmplatform.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** SMTP adapter; sends one message per recipient so results are reported individually. */
@Component
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from:no-reply@crm-platform.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public List<SendResult> send(EmailMessage message) {
        List<SendResult> results = new ArrayList<>();
        for (String recipient : message.to()) {
            try {
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(recipient);
                helper.setSubject(message.subject());
                helper.setText(message.text() == null ? "" : message.text(), message.html());
                mailSender.send(mime);
                results.add(new SendResult(recipient, true, mime.getMessageID(), null));
            } catch (MessagingException | MailException e) {
                log.warn("Email delivery failed for one recipient: {}", e.getMessage());
                results.add(new SendResult(recipient, false, null, "DELIVERY_FAILED"));
            }
        }
        return results;
    }
}
