package team.codingforest.moyeota.auth.dto;

import java.util.UUID;

//마이페이지 응답.
//userId(1,2,3...)는 내부용이라 내보내지 않고 publicId(UUID)만 내보낸다.
//name/email은 소셜 계정에서 받은 값이라 수정할 수 없고, nickname만 사용자가 고칠 수 있다.
public record MypageResponse(UUID publicId, String name, String email, String nickname, String imageUrl) {
}
