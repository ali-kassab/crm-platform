package com.crmplatform.support;

import com.crmplatform.shared.mail.EmailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures outgoing emails in memory so tests can assert on them. */
public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public List<SendResult> send(EmailMessage message) {
        sent.add(message);
        return message.to().stream().map(to -> new SendResult(to, true, "test-" + sent.size(), null)).toList();
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }

    public Optional<EmailMessage> lastTo(String recipient) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).to().contains(recipient)) {
                return Optional.of(sent.get(i));
            }
        }
        return Optional.empty();
    }

    public void clear() {
        sent.clear();
    }
}
