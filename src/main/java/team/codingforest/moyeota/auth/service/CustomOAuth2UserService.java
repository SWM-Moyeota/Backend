package team.codingforest.moyeota.auth.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.dto.GoogleResponse;
import team.codingforest.moyeota.auth.dto.NaverResponse;
import team.codingforest.moyeota.auth.dto.OAuth2Response;
import team.codingforest.moyeota.auth.dto.UserDTO;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.entity.enums.SocialType;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {

        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println(oAuth2User);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response oAuth2Response = null;
        SocialType socialType = null;

        if (registrationId.equals("naver")) {

            oAuth2Response = new NaverResponse(oAuth2User.getAttributes());
            socialType = SocialType.Naver;
        }
        else if (registrationId.equals("google")) {

            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
            socialType = SocialType.Google;
        }
        else {

            return null;
        }

        //조회/생성/중복 복구는 UserService가 담당한다. 앱 로그인(AuthController)도 같은 메서드를 쓴다.
        //user / social_user / user_profile 세 행이 여기서 함께 만들어진다.
        User user = userService.findOrCreateSocialUser(
                socialType,
                oAuth2Response.getProviderId(),
                oAuth2Response.getEmail(),
                oAuth2Response.getName());

        UserDTO userDTO = new UserDTO();
        //토큰의 subject로 쓰일 값. CustomSuccessHandler가 이걸 그대로 JWT에 넣는다.
        userDTO.setUsername(user.getPublicId().toString());
        userDTO.setName(oAuth2Response.getName());
        userDTO.setRole(UserService.SECURITY_ROLE);

        return new CustomOAuth2User(userDTO);
    }
}
