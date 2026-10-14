package team.codingforest.moyeota.user.api;

/**
 * 탈퇴 전에 진행 중인 합승이 있는지 묻는 창구, 구현은 matching 이 함
 * user 가 matching 을 직접 부르면 matching → user(UserAccess) 와 순환이 생겨서 user 쪽에 인터페이스를 둠
 */
public interface OngoingPartyChecker {
    /**
     * 기사면 기사 행을 잠근 뒤 CANCELED·FINISHED 가 아닌 합승에 멤버로 들어가 있거나 기사로 배정돼 있는지 확인함
     *
     * @param userId 확인할 사용자의 내부 번호
     * @return 진행 중인 합승이 있으면 true
     */
    boolean hasOngoingParty(Long userId);
}
