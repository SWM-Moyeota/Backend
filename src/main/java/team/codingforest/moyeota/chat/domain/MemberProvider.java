package team.codingforest.moyeota.chat.domain;

import java.util.List;
import java.util.Map;

public interface MemberProvider {
    Map<Long, ChatMember> findMembers(List<Long> userIds);
}
