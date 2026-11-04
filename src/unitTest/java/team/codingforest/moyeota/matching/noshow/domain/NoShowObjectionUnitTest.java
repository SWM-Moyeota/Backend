package team.codingforest.moyeota.matching.noshow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class NoShowObjectionUnitTest {
    private static final Long USER_ID = 2L;
    private static final Long PARTY_ID = 100L;
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-11-04T09:00:00Z");

    // ───────────────────────── file ─────────────────────────

    @DisplayName("file - PENDING 으로 만들고 사유의 앞뒤 공백을 뗀다")
    @Test
    void file() {

        /*
         * test
         */
        NoShowObjection objection = NoShowObjection.file(USER_ID, PARTY_ID, "  도착 버튼이 안 눌렸어요 ", CREATED_AT);

        /*
         * verify
         */
        assertThat(objection.getId()).isNull();
        assertThat(objection.getUserId()).isEqualTo(USER_ID);
        assertThat(objection.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(objection.getReason()).isEqualTo("도착 버튼이 안 눌렸어요");
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.PENDING);
        assertThat(objection.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(objection.getResolvedAt()).isNull();
    }

    @DisplayName("file - 사유가 정확히 500자면 받는다")
    @Test
    void file_maxLength() {

        /*
         * test
         */
        NoShowObjection objection = NoShowObjection.file(USER_ID, PARTY_ID, "가".repeat(500), CREATED_AT);

        /*
         * verify
         */
        assertThat(objection.getReason()).hasSize(500);
    }

    @DisplayName("file - 사유가 없거나 공백뿐이면 IllegalArgumentException")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void file_blankReason(String reason) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> NoShowObjection.file(USER_ID, PARTY_ID, reason, CREATED_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("file - 사유가 501자면 IllegalArgumentException")
    @Test
    void file_tooLong() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> NoShowObjection.file(USER_ID, PARTY_ID, "가".repeat(501), CREATED_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── accept / reject ─────────────────────────

    @DisplayName("accept - PENDING 이면 ACCEPTED 로 바꾸고 처리 시각을 남긴다")
    @Test
    void accept() {

        /*
         * settings
         */
        NoShowObjection objection = pending();

        /*
         * test
         */
        objection.accept(RESOLVED_AT);

        /*
         * verify
         */
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.ACCEPTED);
        assertThat(objection.getResolvedAt()).isEqualTo(RESOLVED_AT);
    }

    @DisplayName("reject - PENDING 이면 REJECTED 로 바꾸고 처리 시각을 남긴다")
    @Test
    void reject() {

        /*
         * settings
         */
        NoShowObjection objection = pending();

        /*
         * test
         */
        objection.reject(RESOLVED_AT);

        /*
         * verify
         */
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.REJECTED);
        assertThat(objection.getResolvedAt()).isEqualTo(RESOLVED_AT);
    }

    @DisplayName("accept·reject - 이미 처리된 신청이면 OBJECTION_ALREADY_RESOLVED 이고 상태·처리 시각은 그대로다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ObjectionStatus.class, names = {"ACCEPTED", "REJECTED"})
    void resolve_alreadyResolved(ObjectionStatus status) {

        /*
         * settings
         */
        NoShowObjection objection = NoShowObjection.load(1L, USER_ID, PARTY_ID, "사유", status, CREATED_AT, RESOLVED_AT);

        /*
         * test
         */
        Throwable accepted = catchThrowable(() -> objection.accept(RESOLVED_AT.plusSeconds(60)));
        Throwable rejected = catchThrowable(() -> objection.reject(RESOLVED_AT.plusSeconds(60)));

        /*
         * verify
         */
        assertThat(accepted).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) accepted).getErrorCode()).isEqualTo(MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        assertThat(rejected).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) rejected).getErrorCode()).isEqualTo(MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        assertThat(objection.getStatus()).isEqualTo(status);
        assertThat(objection.getResolvedAt()).isEqualTo(RESOLVED_AT);
    }

    private static NoShowObjection pending() {
        return NoShowObjection.load(1L, USER_ID, PARTY_ID, "사유", ObjectionStatus.PENDING, CREATED_AT, null);
    }
}
