package com.racehorse.realtime;

import com.racehorse.training.SessionReadModel.SessionDetail;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes flow2-stomp.md frames AFTER the database transaction commits (REST stays the source of truth). */
@Component
public class SessionEvents {
    private final SimpMessagingTemplate broker;

    public SessionEvents(SimpMessagingTemplate broker) { this.broker = broker; }

    public void state(SessionDetail d, String previous) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("sessionId", d.summary().id());
        f.put("status", d.summary().status());
        f.put("previousStatus", previous);
        f.put("changedAt", Instant.now().toString());
        f.put("interruptionReason", d.interruptionReason());
        f.put("intensity", d.summary().intensity());
        f.put("blocked", d.summary().blocked());
        f.put("blockedReasons", d.summary().blockedReasons());
        send("/topic/sessions/" + d.summary().id() + "/state", f);
    }

    public void metrics(long sessionId, Map<String, Object> frame) { send("/topic/sessions/" + sessionId + "/metrics", frame); }

    public void alert(long sessionId, Map<String, Object> frame) { send("/topic/sessions/" + sessionId + "/alerts", frame); }

    private void send(String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { broker.convertAndSend(destination, payload); }
            });
        } else {
            broker.convertAndSend(destination, payload);
        }
    }
}
