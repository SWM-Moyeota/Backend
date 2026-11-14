package team.codingforest.moyeota.user.profile.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.common.masking.Masking;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class MaskedRealNameService implements MaskedRealNames {
    private final UserProfiles userProfiles;

    @Transactional(readOnly = true)
    @Override
    public Map<Long, String> find(List<Long> userIds) {
        if(userIds.isEmpty()) return Map.of();

        Map<Long, String> result = new HashMap<>();

        for(UserProfile profile : userProfiles.findAllByUserIds(userIds)) {
            result.put(profile.getUserId(), Masking.name(profile.getName()));
        }

        return result;
    }
}
