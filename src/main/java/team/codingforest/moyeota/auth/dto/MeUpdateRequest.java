package team.codingforest.moyeota.auth.dto;

/*
마이페이지 수정 요청.

PATCH라서 보내지 않은 항목(=null)은 그대로 둔다.
  {"nickname":"모여타짱"}  -> 닉네임만 바뀜
누구인지는 토큰에서 읽으므로 userId 같은 신원 정보는 여기에 받지 않는다. 받으면 위조당한다.

bio(자기소개)는 새 구조(user/user_profile)에 컬럼이 없어서 뺐다.
살리려면 UserProfile에 bio 컬럼을 추가하고 이 record에 필드를 늘리면 된다.
*/
public record MeUpdateRequest(String nickname) {
}
