package team.codingforest.moyeota.common.logging;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 요청 안의 한 단계(외부 API 호출 등)의 소요 시간을 key-value 로그로 남긴다.
 * RequestLoggingFilter 가 MDC 에 넣은 requestId 와 함께 찍히므로, Kibana 에서
 * "전체 응답 시간 중 이 단계가 얼마였는지"를 요청 단위로 비교할 수 있다.
 *
 * <pre>
 * return StageTimer.time("kakao.place-search", () -> kakaoRestClient.get()...body(...));
 * </pre>
 */
@Slf4j
public final class StageTimer {
    private StageTimer() {
    }

    public static <T> T time(String stage, Supplier<T> action) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            T result = action.get();
            success = true;
            return result;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.atInfo()
                    .addKeyValue("stage", stage)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("success", success)
                    .log("stage {} {} ({}ms)", stage, success ? "ok" : "failed", durationMs);
        }
    }
}
