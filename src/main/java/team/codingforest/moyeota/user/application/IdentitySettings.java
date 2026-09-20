package team.codingforest.moyeota.user.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.domain.exception.IdentityErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class IdentitySettings {
    private final String storeId;
    private final String channelKey;
    private final String secret;
    private final String hashKey;
    private final Duration validity;

    public IdentitySettings(@Value("${portone.identity.store-id:}") String storeId,
                            @Value("${portone.identity.channel-key:}") String channelKey,
                            @Value("${portone.api.secret:}") String secret,
                            @Value("${portone.identity.di-hash-key:}") String hashKey,
                            @Value("${portone.identity.request-validity:10m}") Duration validity) {
        this.storeId = storeId;
        this.channelKey = channelKey;
        this.secret = secret;
        this.hashKey = hashKey;
        this.validity = validity;
    }

    public void requireConfigured() {
        if (storeId.isBlank() || channelKey.isBlank() || secret.isBlank()
                || hashKey.getBytes(StandardCharsets.UTF_8).length < 32
                || validity.isNegative() || validity.isZero()) {
            throw new BusinessException(IdentityErrorCode.NOT_CONFIGURED);
        }
    }

    public String storeId() { return storeId; }
    public String channelKey() { return channelKey; }
    public String secret() { return secret; }
    public String hashKey() { return hashKey; }
    public Duration validity() { return validity; }
}
