package com.racehorse.core026;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.racehorse.core026.Contracts.CheckResult;
import static com.racehorse.core026.IncidentService.*;

/**
 * CORE-024 calls inside its start transaction, retaining the session lock
 * through its state update. PASS covers only this gate. CORE-024 must still
 * enforce medical restrictions and every other start rule.
 */
@Service
public class PreSessionStartGate {
    public enum Outcome {
        PASS, PRE_CHECK_MISSING, PRE_CHECK_ABNORMAL
    }

    private final Core026Store store;

    public PreSessionStartGate(Core026Store store) {
        this.store = store;
    }

    @Transactional
    public Outcome evaluate(long sessionId) {
        if (sessionId < 1)
            throw invalid("Positive session ID required.");
        store.session(sessionId, true).orElseThrow(() -> missing("Session not found."));
        return store.checkResult(sessionId)
                .map(result -> result == CheckResult.ALL_NORMAL ? Outcome.PASS : Outcome.PRE_CHECK_ABNORMAL)
                .orElse(Outcome.PRE_CHECK_MISSING);
    }

    @Transactional
    public void requirePass(long sessionId) {
        Outcome result = evaluate(sessionId);
        if (result != Outcome.PASS)
            throw conflict(result.name());
    }
}
