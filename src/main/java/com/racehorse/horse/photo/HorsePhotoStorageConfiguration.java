package com.racehorse.horse.photo;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"dev", "test"})
public class HorsePhotoStorageConfiguration implements WebMvcConfigurer {

    private final LocalHorsePhotoStorage storage;

    public HorsePhotoStorageConfiguration(LocalHorsePhotoStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(storage.resourceHandlerPattern())
                .addResourceLocations(storage.rootDirectory().toUri().toString());
    }
}
