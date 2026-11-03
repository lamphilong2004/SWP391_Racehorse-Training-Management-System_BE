package com.racehorse.training.realtime;

import com.racehorse.auth.AuthRepository;
import com.racehorse.auth.CurrentUser;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class Core030WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final Core030StompInterceptor interceptor;
    private final CorsProperties corsProperties;
    public Core030WebSocketConfiguration(Core030StompInterceptor interceptor,CorsProperties corsProperties){this.interceptor=interceptor;this.corsProperties=corsProperties;}
    @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/topic","/queue");registry.setApplicationDestinationPrefixes("/app");registry.setUserDestinationPrefix("/user");}
    @Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));}
    @Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(interceptor);}
}

@Component
class Core030StompInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC=Pattern.compile("^/topic/sessions/(\\d+)/(metrics|alerts|state)$");
    private final JwtDecoder decoder;private final AuthRepository users;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    Core030StompInterceptor(JwtDecoder decoder,AuthRepository users,org.springframework.jdbc.core.JdbcTemplate jdbc){this.decoder=decoder;this.users=users;this.jdbc=jdbc;}

    @Override public Message<?> preSend(Message<?> message,MessageChannel channel){StompHeaderAccessor accessor=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);if(accessor==null)return message;
        StompCommand command=accessor.getCommand();
        if(command==StompCommand.CONNECT){authenticate(accessor);return message;}
        if(command==StompCommand.SEND)throw new AccessDeniedException("STOMP clients are read-only; writes must use REST.");
        if(command==StompCommand.SUBSCRIBE){authorizeSubscription(accessor);return message;}
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor){List<String> values=accessor.getNativeHeader("Authorization");if(values==null||values.isEmpty()||!values.getFirst().startsWith("Bearer "))throw new AccessDeniedException("A Bearer JWT is required in STOMP CONNECT headers.");
        try{var jwt=decoder.decode(values.getFirst().substring(7).trim());long id=Long.parseLong(jwt.getSubject());CurrentUser user=users.findCurrentUser(id).orElseThrow(()->new AccessDeniedException("Authenticated account was not found."));if(!"ACTIVE".equals(user.accountStatus().name()))throw new AccessDeniedException("Inactive accounts cannot connect.");
            accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(Long.toString(id),jwt.getTokenValue(),user.roles().stream().map(r->new SimpleGrantedAuthority("ROLE_"+r.name())).toList()));
        }catch(AccessDeniedException e){throw e;}catch(Exception e){throw new AccessDeniedException("STOMP CONNECT JWT is invalid or expired.",e);}}

    private void authorizeSubscription(StompHeaderAccessor accessor){Principal principal=accessor.getUser();if(!(principal instanceof org.springframework.security.core.Authentication auth)||!auth.isAuthenticated())throw new AccessDeniedException("STOMP CONNECT authentication is required.");
        long userId;try{userId=Long.parseLong(auth.getName());}catch(NumberFormatException e){throw new AccessDeniedException("Authenticated principal is invalid.");}
        CurrentUser user=users.findCurrentUser(userId).orElseThrow(()->new AccessDeniedException("Authenticated account is unavailable."));if(!"ACTIVE".equals(user.accountStatus().name()))throw new AccessDeniedException("Inactive accounts cannot subscribe.");
        String destination=accessor.getDestination();if("/user/queue/notifications".equals(destination))return;
        Matcher match=TOPIC.matcher(destination==null?"":destination);if(!match.matches())throw new AccessDeniedException("Subscription destination is not approved.");
        long session=Long.parseLong(match.group(1));String topic=match.group(2);boolean broad=user.roles().stream().anyMatch(r->List.of(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN).contains(r));if(broad)return;
        if(user.roles().contains(RoleCode.TRAINER)){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=ph.horse_training_program_id WHERE s.id=? AND ta.trainer_user_id=? AND ta.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",Integer.class,session,userId);if(n!=null&&n>0)return;}
        if(user.roles().contains(RoleCode.WORK_RIDER)&&!"alerts".equals(topic)){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session WHERE id=? AND work_rider_user_id=?",Integer.class,session,userId);if(n!=null&&n>0)return;}
        throw new AccessDeniedException("This session topic is outside the authenticated user's role or assignment scope.");
    }
}
