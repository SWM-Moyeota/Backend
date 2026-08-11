package team.codingforest.moyeota.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.SocialUser;
import team.codingforest.moyeota.auth.entity.enums.SocialType;

import java.util.Optional;

public interface SocialUserRepository extends JpaRepository<SocialUser, Long> {

    //소셜 로그인의 출발점. "구글의 sub가 xxx인 계정이 이미 있는가"를 묻는다.
    //socialId만으로 찾으면 안 된다. 구글의 sub와 카카오의 id가 우연히 같을 수 있기 때문이다.
    Optional<SocialUser> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
}
