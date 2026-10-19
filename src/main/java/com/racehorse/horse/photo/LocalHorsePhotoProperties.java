package com.racehorse.horse.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.storage.local")
public class LocalHorsePhotoProperties {

    private Path directory = Path.of(System.getProperty("user.home"), ".racehorse", "horse-photos");
    private String publicBaseUrl = "/local-media/horse-photos";

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String publicPath() {
        URI uri = URI.create(publicBaseUrl);
        String path = uri.getPath();
        if (path == null || !path.startsWith("/") || path.contains("/../")
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getScheme() != null && !uri.getScheme().matches("https?"))) {
            throw new IllegalStateException("The local horse-photo URL configuration is invalid.");
        }
        String normalizedPath = path.replaceAll("/+$", "");
        if (normalizedPath.isBlank()) {
            throw new IllegalStateException("The local horse-photo URL configuration is invalid.");
        }
        return normalizedPath;
    }

    public String resourceHandlerPattern() {
        return publicPath() + "/**";
    }

    public URI photoUrl(String fileReference) {
        String base = publicBaseUrl.replaceAll("/+$", "");
        return URI.create(base + "/" + fileReference);
    }
}
