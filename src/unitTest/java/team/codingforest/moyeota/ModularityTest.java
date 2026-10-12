package team.codingforest.moyeota;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void 모듈_경계를_검증한다() {
        ApplicationModules.of(MoyeotaApplication.class).verify();
    }
}
