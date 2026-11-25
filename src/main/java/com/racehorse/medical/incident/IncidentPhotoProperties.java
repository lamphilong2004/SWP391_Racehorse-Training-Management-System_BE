package com.racehorse.medical.incident;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Local folder for incident photos (Flow 3 step 3.1). Kept apart from horse photos; never served as static files. */
@Component
@ConfigurationProperties(prefix = "app.storage.incident-photos")
public class IncidentPhotoProperties {

    private Path directory = Path.of(System.getProperty("user.home"), ".racehorse", "incident-photos");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }
}
