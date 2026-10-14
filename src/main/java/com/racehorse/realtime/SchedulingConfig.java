package com.racehorse.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the CRITICAL-alert escalation sweep (RealtimeService#escalate). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
