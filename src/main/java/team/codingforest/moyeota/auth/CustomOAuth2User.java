package team.codingforest.moyeota.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import team.codingforest.moyeota.auth.dto.UserDTO;
import team.codingforest.moyeota.auth.service.UserService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final UserDTO userDTO;

    public CustomOAuth2User(UserDTO userDTO) {
        this.userDTO = userDTO;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                //토큰에는 role이 없다. 로그인한 사용자는 모두 같은 보안 권한을 갖는다.
                return UserService.SECURITY_ROLE;
            }
        });
        return collection;
    }

    @Override
    public String getName() {
        return userDTO.getName();
    }

    public String getUsername() {
        return userDTO.getUsername();
    }

    /*
    public String getPublicId() {
        return userDTO.getPublicId();
    }
    */

}