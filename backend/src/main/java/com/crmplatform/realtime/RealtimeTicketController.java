package com.crmplatform.realtime;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * One-time socket tickets (ADR-0015): 256 random bits, valid 60 s, consumed once by the realtime
 * service with GETDEL. The realtime service never sees access tokens or signing keys.
 */
@RestController
@Tag(name = "Realtime")
public class RealtimeTicketController {

    static final String KEY_PREFIX = "rt:ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RealtimeProperties properties;

    public RealtimeTicketController(StringRedisTemplate redis, ObjectMapper json, RealtimeProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    @PostMapping("/api/v1/realtime/ticket")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> ticket() {
        AuthenticatedUser user = CurrentUser.require();
        if (!user.isMember()) {
            throw ApiException.notFound("Workspace");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String claims = json.writeValueAsString(Map.of("userId", user.userId().toString(),
                "workspaceId", user.workspaceId().toString()));
        redis.opsForValue().set(KEY_PREFIX + ticket, claims, properties.ticketTtl());
        return Map.of("ticket", ticket, "expiresInSeconds", properties.ticketTtl().toSeconds());
    }
}
