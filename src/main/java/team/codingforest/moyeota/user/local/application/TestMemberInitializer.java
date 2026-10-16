package team.codingforest.moyeota.user.local.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import javax.sql.DataSource;
import java.time.Instant;

/** 명시적으로 활성화한 로컬 H2 메모리 DB에만 테스트 회원을 생성한다. */
@Component
@Profile("test-member & !dev & !prod")
@RequiredArgsConstructor
public class TestMemberInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;
    private final PasswordHasher passwordHasher;
    private final IdGenerator idGenerator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
                throw new IllegalStateException("test-member는 로컬 H2 메모리 DB에서만 사용할 수 있습니다.");
            }
        }
        if (localUsers.existsByLoginId("portonetest")) return;
        User user = users.save(User.from(idGenerator.generate(), LoginType.LOCAL, "카드테스터"));
        localUsers.register(user.getId(), "portonetest", passwordHasher.hash("PortoneTest1!"));
        userProfiles.save(UserProfile.of(user.getId(), "테스트회원",
                Instant.parse("2000-01-01T00:00:00Z"), "01000000000", Gender.MALE,
                "portone-test@example.com"));
    }
}
