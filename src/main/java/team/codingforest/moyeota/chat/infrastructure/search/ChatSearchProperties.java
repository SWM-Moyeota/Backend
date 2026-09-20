package team.codingforest.moyeota.chat.infrastructure.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("chat.search")
public record ChatSearchProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:9200") URI url,
        @DefaultValue("chat-messages-v1") String index,
        @DefaultValue("") String apiKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("50") int batchSize) {
    public ChatSearchProperties {
        if (url == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null)
            throw new IllegalArgumentException("chat.search.url은 인증정보가 없는 HTTP(S) 주소여야 합니다");
        if (index == null || !index.matches("[a-z][a-z0-9_-]{0,100}"))
            throw new IllegalArgumentException("chat.search.index 형식이 올바르지 않습니다");
        if (batchSize < 1 || batchSize > 500 || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero())
            throw new IllegalArgumentException("검색 시간 제한 또는 배치 크기가 올바르지 않습니다");
    }
}
