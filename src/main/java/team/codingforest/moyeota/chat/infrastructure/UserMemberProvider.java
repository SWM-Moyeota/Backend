package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMemberProvider implements MemberProvider {

    private final UserAccess userAccess;

    @Override
    public Map<Long, ChatMember> findMembers(List<Long> userIds) {
        Map<Long, MemberSummary> summaries = userAccess.findMemberSummaries(userIds);

        return summaries.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ChatMember(
                                e.getKey(),
                                e.getValue().publicId(),
                                e.getValue().nickname(),
                                e.getValue().imageUrl())));
    }
}