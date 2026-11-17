package team.codingforest.moyeota.chat.room.domain;

public enum ChatRoomStatus {
    ACTIVE, // 활성화
    FINISHED, // 같이 타고 합승이 끝남. 대화는 계속 가능
    CANCELED, // 같이 타지 못하고 합승이 끝남. 대화는 계속 가능
    CLOSED, // 비활성화
    ARCHIVED; // 기록

    /**
     * 닫힌 파티의 상태를 채팅방 상태로 옮김
     *
     * @param partyStatus PartyClosedEvent 의 파티 상태 이름
     * @return 파티가 같이 타고 끝났으면 FINISHED, 같이 타지 못하고 끝났으면 CANCELED
     */
    public static ChatRoomStatus endedBy(String partyStatus) {
        return switch (partyStatus) {
            case "FINISHED" -> FINISHED;
            case "CANCELED" -> CANCELED;
            default -> throw new IllegalArgumentException("닫힌 파티 상태가 아님 partyStatus=" + partyStatus);
        };
    }

    /** 메시지를 보낼 수 있는 상태. 합승이 끝나도 CLOSED 가 되기 전까지는 대화할 수 있음 */
    public boolean canSend() {
        return this == ACTIVE || this == FINISHED || this == CANCELED;
    }
}
