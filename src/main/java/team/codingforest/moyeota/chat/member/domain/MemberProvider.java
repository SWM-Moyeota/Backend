package team.codingforest.moyeota.chat.member.domain;

import java.util.List;
import java.util.Map;

public interface MemberProvider {
    Map<Long, ChatMember> findMembers(List<Long> userIds);
}
