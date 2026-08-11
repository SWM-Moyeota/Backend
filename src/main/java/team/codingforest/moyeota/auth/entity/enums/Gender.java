package team.codingforest.moyeota.auth.entity.enums;

/*
OTHER는 "안 밝힘/해당 없음"을 담는 값이다.

회원가입에서 성별을 안 보내면 여기로 들어간다(UserService.createLocalUser).
null로 두지 않는 이유는, 꺼내 쓰는 쪽마다 null 검사를 하게 되고
한 곳이라도 빠지면 NPE가 나기 때문이다.

@Enumerated(EnumType.STRING)으로 저장하므로 값 이름 그대로 DB에 들어간다.
따라서 값을 추가하는 것은 안전하지만, 이름을 바꾸거나 지우면
이미 저장된 행을 읽을 때 터진다.
*/
public enum Gender {
    MALE, FEMALE, OTHER;
}
