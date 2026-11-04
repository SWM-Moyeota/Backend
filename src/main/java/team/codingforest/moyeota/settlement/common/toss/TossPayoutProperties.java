package team.codingforest.moyeota.settlement.common.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스 지급대행 연결 설정
 *
 * @param baseUrl        API 주소
 * @param secretKey      API 개별 연동 키의 시크릿 키, Basic 인증에 씀
 * @param securityKey    지급대행 보안 키(64자 hex), 요청·응답 JWE 암호화에 씀
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 제한 시간
 */
@ConfigurationProperties(prefix = "moyeota.settlement.toss")
public record TossPayoutProperties(String baseUrl, String secretKey, String securityKey,
                                   Duration connectTimeout, Duration readTimeout) {
}