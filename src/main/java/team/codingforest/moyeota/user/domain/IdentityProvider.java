package team.codingforest.moyeota.user.domain;

import java.time.Instant;

public interface IdentityProvider {
    Result lookup(IdentityRequest request);

    // DI가 기본 record.toString()을 통해 로그에 노출되지 않도록 재정의한다.
    record Result(String id, String status, String version, String channelKey, String channelType,
                  Instant verifiedAt, String di) {
        @Override public String toString() { return "IdentityProvider.Result[비공개]"; }
    }
}
