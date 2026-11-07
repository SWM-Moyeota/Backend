package team.codingforest.moyeota._config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 앱 버전 게이트 설정. 평소에는 둘 다 비워 두고(기준 없음), 서버 계약이 깨지는 변경을 내보내는 날에만 값을 넣는다.
 *
 * <p>값은 안드로이드 {@code versionCode} 와 같은 정수다 - 앱은 {@code YYMMDD*100+N} 형식을 쓴다.
 * 빈 환경변수는 null 로 묶이고(Spring 의 문자열→숫자 변환이 빈 문자열을 null 로 준다), null 이면
 * 응답에서 키가 빠져 앱은 아무 기준도 없는 것으로 본다.
 *
 * <p><b>반드시 환경변수로 받는다.</b> 실수로 큰 값을 넣으면 전원이 업데이트 화면에 잠기는데,
 * 막힌 앱은 설정을 다시 받아도 같은 판정이라 앱 쪽에서 되돌릴 방법이 없다. 코드 배포를 기다리지 않고
 * 값을 비워 되돌릴 수 있어야 한다.
 *
 * @param minVersionCode         이 값보다 낮은 versionCode 의 앱은 빠져나갈 수 없는 업데이트 화면에서 멈춘다. 같은 값은 통과한다
 * @param recommendedVersionCode 이 값보다 낮으면 막지 않고 한 번 알리기만 한다
 */
@ConfigurationProperties(prefix = "moyeota.app")
public record AppProperties(Integer minVersionCode, Integer recommendedVersionCode) {
}
