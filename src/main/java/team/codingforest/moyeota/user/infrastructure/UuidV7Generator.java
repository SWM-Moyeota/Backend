package team.codingforest.moyeota.user.infrastructure;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.domain.IdGenerator;

import java.util.UUID;

/** UUID v7. 시간순 정렬돼 인덱스 지역성 좋음 (publicId, jti 공용). JUG 라이브러리 사용 */
@Component
public class UuidV7Generator implements IdGenerator {
    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Override
    public UUID generate() {
        return generator.generate();
    }
}
