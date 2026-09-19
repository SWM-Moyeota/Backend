package team.codingforest.moyeota.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  주입받은 플래그를 그대로 실어 보내는지만 본다.
 *  프로퍼티 바인딩·permitAll 은 TaxiEnabledContextTest / TaxiDisabledContextTest 가 실제 컨텍스트로 확인한다.
 */
class AppConfigControllerTest {
    @Test
    void 택시_기능이_켜져_있으면_true() {
        AppConfigResponse body = new AppConfigController(true).config().getBody();

        assertThat(body).isNotNull();
        assertThat(body.taxiEnabled()).isTrue();
    }

    @Test
    void 택시_기능이_꺼져_있으면_false() {
        AppConfigResponse body = new AppConfigController(false).config().getBody();

        assertThat(body).isNotNull();
        assertThat(body.taxiEnabled()).isFalse();
    }
}
