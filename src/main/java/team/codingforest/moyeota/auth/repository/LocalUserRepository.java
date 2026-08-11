package team.codingforest.moyeota.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.LocalUser;

import java.util.Optional;

public interface LocalUserRepository extends JpaRepository<LocalUser, Long> {

    //로컬 로그인의 출발점. "이 아이디로 가입한 사람이 있는가"를 묻는다.
    Optional<LocalUser> findByLoginId(String loginId);

    //회원가입 때 아이디 중복을 미리 걸러 400/409를 돌려주기 위한 용도.
    //이것만으로는 동시 요청을 막을 수 없어서 DB의 unique 제약이 따로 필요하다.
    boolean existsByLoginId(String loginId);
}
