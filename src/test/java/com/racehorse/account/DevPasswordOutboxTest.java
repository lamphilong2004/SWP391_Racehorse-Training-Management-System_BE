package com.racehorse.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DevPasswordOutboxTest {
    @TempDir Path temporary;
    @Test void onlyDevProfileEnablesLocalDelivery() {
        for(String profile:List.of("test","staging","prod","production")) {
            MockEnvironment env=new MockEnvironment();env.setActiveProfiles(profile);
            DevPasswordOutbox outbox=new DevPasswordOutbox(env,temporary.resolve(profile).toString());
            assertThat(outbox.enabled()).isFalse();outbox.deliver("fixture@example.test","fixture-token");
            assertThat(Files.exists(temporary.resolve(profile))).isFalse();
        }
        MockEnvironment mixed=new MockEnvironment();mixed.setActiveProfiles("dev","staging");
        assertThat(new DevPasswordOutbox(mixed,temporary.toString()).enabled()).isFalse();
    }
    @Test void devDeliveryIsOwnerPrivateAndHasNoPublicEndpoint() throws Exception {
        MockEnvironment env=new MockEnvironment();env.setActiveProfiles("dev");
        Path directory=temporary.resolve("outbox");
        new DevPasswordOutbox(env,directory.toString()).deliver("fixture@example.test","fixture-token");
        Path file;
        try(var files=Files.list(directory)) {file=files.findFirst().orElseThrow();}
        assertThat(Files.readString(file)).contains("NOT PRODUCTION-READY","fixture-token");
        PosixFileAttributeView posix=Files.getFileAttributeView(file,PosixFileAttributeView.class);
        if(posix!=null) assertThat(posix.readAttributes().permissions()).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        else {
            AclFileAttributeView acl=Files.getFileAttributeView(file,AclFileAttributeView.class);
            assertThat(acl).isNotNull();
            UserPrincipal owner=acl.getOwner();
            assertThat(acl.getAcl()).allMatch(entry->entry.type()==AclEntryType.ALLOW&&entry.principal().equals(owner));
        }
    }
}