package com.racehorse.integration;

/** Re-evaluates automatic Flow 1 activation after any mandatory onboarding item changes (DEC-009 §1). */
public interface ActivationHook {
    void evaluate(long horseId);
}
