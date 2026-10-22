package com.racehorse.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Development delivery only: tokens never enter an HTTP response or application logger. */
@Component
public class DevPasswordOutbox {
    private final Environment environment;
    private final Path directory;
    public DevPasswordOutbox(Environment environment,
            @Value("${app.account.reset-outbox-directory:.local/password-reset-outbox}") String directory) {
        this.environment=environment;this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    public boolean enabled() { return environment.acceptsProfiles(Profiles.of("dev")) &&
        !environment.acceptsProfiles(Profiles.of("staging","prod","production")); }
    public void deliver(String email,String token) {
        if(!enabled()) return;
        try {
            Files.createDirectories(directory);
            if(Files.isSymbolicLink(directory)) throw new IOException("Unsafe outbox path.");
            privatePermissions(directory,true);
            Path file=Files.createTempFile(directory,"reset-",".local.txt");
            try {
                privatePermissions(file,false);
                Files.writeString(file,"Development only; NOT PRODUCTION-READY\nRecipient: "+email+
                    "\nReset path: /reset-password?token="+token+"\nExpires after 30 minutes.\n",StandardCharsets.UTF_8);
            } catch(IOException ex) { Files.deleteIfExists(file);throw ex; }
        } catch(IOException|UnsupportedOperationException ex) {
            // Never attach the exception: file-system messages must not leak token-bearing content.
            throw new IllegalStateException("The private development password outbox is unavailable.");
        }
    }
    private static void privatePermissions(Path path,boolean directory) throws IOException {
        PosixFileAttributeView posix=Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));return;
        }
        AclFileAttributeView acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl==null) throw new IOException("Owner-private ACL support is required.");
        AclEntry.Builder owner=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
            .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(directory) owner.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
        acl.setAcl(List.of(owner.build()));
    }
}