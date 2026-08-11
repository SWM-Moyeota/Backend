package team.codingforest.moyeota.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.UserProfile;

//PK가 user 테이블의 user_id와 같은 값이므로 findById(userId)로 바로 찾을 수 있다.
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
