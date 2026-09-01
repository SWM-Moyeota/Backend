package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.UserProfile;
import team.codingforest.moyeota.user.domain.UserProfiles;

@Repository
@RequiredArgsConstructor
public class UserProfileJpa implements UserProfiles {
    private final UserProfileRepository userProfileRepository;

    @Override
    public void save(UserProfile user) {
        userProfileRepository.save(UserProfileEntity.from(user));
    }



}
