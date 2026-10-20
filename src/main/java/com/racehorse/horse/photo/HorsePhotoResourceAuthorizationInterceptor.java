package com.racehorse.horse.photo;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public final class HorsePhotoResourceAuthorizationInterceptor implements HandlerInterceptor {

    private final LocalHorsePhotoStorage storage;
    private final HorsePhotoService photoService;

    public HorsePhotoResourceAuthorizationInterceptor(
            LocalHorsePhotoStorage storage,
            HorsePhotoService photoService) {
        this.storage = storage;
        this.photoService = photoService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        String prefix = storage.resourceHandlerPattern();
        prefix = prefix.substring(0, prefix.length() - 2);
        if (!requestPath.startsWith(prefix)) {
            return true;
        }

        String fileReference = requestPath.substring(prefix.length());
        if (fileReference.isBlank() || fileReference.contains("/")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Horse photo not found.");
        }
        photoService.authorizeStoredPhoto(fileReference);
        return true;
    }
}
