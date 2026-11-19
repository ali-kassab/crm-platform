package com.crmplatform.shared.mail;

import java.util.List;

/** Outbound email port (INTEGRATION_SPECIFICATION.md). Adapters: SMTP (Mailpit locally), SES later. */
public interface EmailSender {

    List<SendResult> send(EmailMessage message);

    record EmailMessage(List<String> to, String subject, String html, String text) {
        public EmailMessage {
            to = List.copyOf(to);
        }
    }

    record SendResult(String recipient, boolean success, String messageId, String error) {
    }
}
