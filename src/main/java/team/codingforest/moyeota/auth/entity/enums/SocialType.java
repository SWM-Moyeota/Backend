package team.codingforest.moyeota.auth.entity.enums;

/*
@Enumerated(EnumType.STRING)으로 저장하므로 값 이름 그대로 DB에 들어간다(SocialUser.socialType).
따라서 값을 추가하는 것은 안전하지만, 이름을 바꾸거나 지우면
이미 저장된 행을 읽을 때 터진다. Gender와 같은 이유다.
*/
public enum SocialType {
    KAKAO, GOOGLE, NAVER
}
