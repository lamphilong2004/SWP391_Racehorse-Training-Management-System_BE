package com.racehorse.realtime;

import com.racehorse.auth.UserJwtAuthenticationConverter;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.identity.RoleCode;
import com.racehorse.common.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEMO IMPLEMENTATION of flow2-stomp.md: endpoint /ws, JWT in the STOMP CONNECT Authorization header, client SEND
 * rejected, subscription authorization checked on every SUBSCRIBE (role-feature-matrix MF-07 + assignment scope).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/sessions/(\\d+)/(metrics|alerts|state)$");

    private final JwtDecoder decoder;
    private final UserJwtAuthenticationConverter converter;
    private final JdbcTemplate jdbc;
    private final CorsProperties cors;

    public WebSocketConfig(JwtDecoder decoder, UserJwtAuthenticationConverter converter, JdbcTemplate jdbc, CorsProperties cors) {
        this.decoder = decoder;
        this.converter = converter;
        this.jdbc = jdbc;
        this.cors = cors;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return message;
                StompCommand cmd = acc.getCommand();
                if (cmd == StompCommand.CONNECT) {
                    String header = acc.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) throw new MessagingException("Missing bearer token");
                    try {
                        AbstractAuthenticationToken auth = converter.convert(decoder.decode(header.substring(7)));
                        acc.setUser(auth);
                    } catch (RuntimeException e) {
                        throw new MessagingException("Invalid token");
                    }
                } else if (cmd == StompCommand.SEND) {
                    throw new MessagingException("Clients only subscribe; writes go through REST.");
                } else if (cmd == StompCommand.SUBSCRIBE) {
                    authorize(acc);
                }
                return message;
            }
        });
    }

    private void authorize(StompHeaderAccessor acc) {
        if (!(acc.getUser() instanceof AbstractAuthenticationToken token) || !(token.getPrincipal() instanceof AuthenticatedUser u))
            throw new MessagingException("Not authenticated");
        String dest = acc.getDestination();
        if (dest == null) throw new MessagingException("Missing destination");
        if (dest.startsWith("/user/queue/")) return;
        Matcher m = SESSION_TOPIC.matcher(dest);
        if (!m.matches()) throw new MessagingException("Forbidden destination");
        long sessionId = Long.parseLong(m.group(1));
        String topic = m.group(2);
        if (u.hasRole(RoleCode.CLUB_MANAGER) || u.hasRole(RoleCode.HEAD_TRAINER) || u.hasRole(RoleCode.VETERINARIAN)) return;
        if (u.hasRole(RoleCode.TRAINER)) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session s JOIN dbo.horse_program_phase ph "
                    + "ON ph.id=s.horse_program_phase_id JOIN dbo.trainer_assignment a ON a.horse_training_program_id=ph.horse_training_program_id "
                    + "WHERE s.id=? AND a.trainer_user_id=? AND a.effective_to IS NULL", Integer.class, sessionId, u.id());
            if (c != null && c > 0) return;
        }
        if (u.hasRole(RoleCode.WORK_RIDER) && !"alerts".equals(topic)) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session WHERE id=? AND work_rider_user_id=?",
                    Integer.class, sessionId, u.id());
            if (c != null && c > 0) return;
        }
        throw new MessagingException("Subscription not permitted");
    }
}
