package team.codingforest.moyeota.matching.party;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 *  방의 "지금 모습"을 대표하는 값. 상태나 멤버 구성이 바뀌면 달라진다.
 *  앱은 방 상세를 받을 때 이 값을 기억해 두고, 방 상태 조회의 값과 다르면 상세를 다시 읽는다. 내용을 해석하지 않고 같은지만 본다.
 *
 *  인원수만 비교하면 한 명이 나가고 다른 한 명이 들어온 경우를 놓친다.
 *  키 없이 해시하면 작은 정수인 내부 사용자 번호를 대입으로 알아낼 수 있어 HMAC 을 쓴다.
 */
@Component
public class PartyFingerprint {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int BYTES = 8;   // 16진수 16글자. 방 하나의 변화를 구분하는 용도라 충분하다

    private final SecretKeySpec key;

    public PartyFingerprint(@Value("${moyeota.party.fingerprint-secret}") String secret) {
        Assert.hasText(secret, "moyeota.party.fingerprint-secret 이 비어 있습니다");
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String of(Long partyId, PartyStatus status, Collection<Long> memberIds) {
        // 정렬 - 조회 순서가 달라도 같은 멤버 구성이면 같은 값이어야 한다
        String members = memberIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        String material = partyId + "|" + status.name() + "|" + members;

        try {
            Mac mac = Mac.getInstance(ALGORITHM);   // Mac 은 스레드 안전하지 않아 호출마다 만든다
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)), 0, BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("방 지문 계산 실패", e);
        }
    }
}
