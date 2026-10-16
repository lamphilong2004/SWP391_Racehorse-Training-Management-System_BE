package com.racehorse.security;

import com.racehorse.auth.AccountStatus;
import com.racehorse.auth.AuthRepository;
import com.racehorse.auth.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class CurrentAccountStatusFilter extends OncePerRequestFilter {

    private final AuthRepository authRepository;
    private final JsonSecurityHandlers securityHandlers;

    public CurrentAccountStatusFilter(
            AuthRepository authRepository,
            JsonSecurityHandlers securityHandlers) {
        this.authRepository = authRepository;
        this.securityHandlers = securityHandlers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath()))
                || ("GET".equals(request.getMethod()) && "/api/health".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && authentication.isAuthenticated()) {
            long userId;
            try {
                userId = Long.parseLong(jwtAuthentication.getToken().getSubject());
            } catch (NumberFormatException exception) {
                securityHandlers.commence(
                        request,
                        response,
                        new BadCredentialsException("The token subject is invalid.", exception));
                return;
            }
            CurrentUser currentUser = authRepository.findCurrentUser(userId).orElse(null);
            if (currentUser == null) {
                securityHandlers.commence(
                        request,
                        response,
                        new BadCredentialsException("The token subject does not identify an account."));
                return;
            }
            if (currentUser.accountStatus() != AccountStatus.ACTIVE) {
                securityHandlers.accountDeactivated(response);
                return;
            }
            List<SimpleGrantedAuthority> authorities = currentUser.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(currentUser, null, authorities));
        }
        filterChain.doFilter(request, response);
    }
}
