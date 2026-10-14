package com.racehorse.demo;

import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** DEMO ONLY (profile "demo"): Club Manager can reset the dataset between rehearsals. Not in any contract. */
@RestController
@Profile("demo")
public class DemoController {
    private final DemoDataInitializer initializer;
    public DemoController(DemoDataInitializer initializer) { this.initializer = initializer; }

    @PostMapping("/api/demo/reset")
    public ResponseEntity<Void> reset() {
        Db.require(RoleCode.CLUB_MANAGER);
        initializer.reset();
        return ResponseEntity.noContent().build();
    }
}
