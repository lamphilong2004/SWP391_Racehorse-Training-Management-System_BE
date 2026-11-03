package com.racehorse.horse.photo;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"dev", "test"})
public class HorsePhotoStorageConfiguration implements WebMvcConfigurer {

    private final LocalHorsePhotoStorage storage;
    private final HorsePhotoService photoService;

    public HorsePhotoStorageConfiguration(LocalHorsePhotoStorage storage, HorsePhotoService photoService) {
        this.storage = storage;
        this.photoService = photoService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(storage.resourceHandlerPattern())
                .addResourceLocations(storage.rootDirectory().toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HorsePhotoResourceAuthorizationInterceptor(storage, photoService))
                .addPathPatterns(storage.resourceHandlerPattern());
    }
}
