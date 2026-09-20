package team.codingforest.moyeota.user.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.application.IdentitySettings;
import team.codingforest.moyeota.user.domain.IdentityProvider;
import team.codingforest.moyeota.user.domain.IdentityRequest;
import java.time.Instant;
import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

@Component
public class PortOneIdentityClient implements IdentityProvider {
    private final RestClient client;
    private final IdentitySettings settings;

    public PortOneIdentityClient(@Qualifier("portOneIdentityRestClient") RestClient client, IdentitySettings settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public Result lookup(IdentityRequest request) {
        try {
            Response body = client.get()
                    .uri(builder -> builder.path("/identity-verifications/{id}")
                            .queryParam("storeId", request.storeId()).build(request.id()))
                    .header("Authorization", "PortOne " + settings.secret())
                    .retrieve().body(Response.class);
            if (body == null) throw new BusinessException(INVALID_RESULT);
            return new Result(body.id(), body.status(), body.version(),
                    body.channel() == null ? null : body.channel().key(),
                    body.channel() == null ? null : body.channel().type(), body.verifiedAt(),
                    body.verifiedCustomer() == null ? null : body.verifiedCustomer().di());
        } catch (RestClientResponseException e) {
            // 외부 오류 본문/예외 원문에는 개인정보가 포함될 수 있어 전달하거나 기록하지 않는다.
            if (e.getStatusCode().value() == 404) throw new BusinessException(NOT_VERIFIED);
            throw new BusinessException(PROVIDER_UNAVAILABLE);
        } catch (RestClientException e) {
            throw new BusinessException(PROVIDER_UNAVAILABLE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String id, String status, String version, Channel channel,
                           Instant verifiedAt, Customer verifiedCustomer) {
        @Override public String toString() { return "PortOne.Response[비공개]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(String key, String type) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String di) {
        @Override public String toString() { return "PortOne.Customer[비공개]"; }
    }
}
