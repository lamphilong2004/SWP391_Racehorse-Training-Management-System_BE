package com.racehorse.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUser user) {
}
