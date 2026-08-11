package team.codingforest.moyeota.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/*
비밀번호 해시 담당.

왜 SecurityConfig가 아니라 별도 클래스인가:
SecurityConfig는 생성자에서 CustomOAuth2UserService -> UserService를 필요로 한다.
그런데 UserService가 PasswordEncoder를 주입받으므로, PasswordEncoder를 SecurityConfig에 두면
"SecurityConfig 만들려면 UserService가 필요하고, UserService 만들려면 SecurityConfig가 필요한"
순환 참조가 되어 앱이 뜨지 않는다. 아무 의존성도 없는 이 클래스에 두면 그럴 일이 없다.

BCrypt를 쓰는 이유:
- 비밀번호를 평문으로 저장하면 DB가 유출될 때 그대로 남의 계정이 털린다.
- 단순 해시(SHA-256 등)도 부족하다. 너무 빨라서 초당 수억 번 대입이 가능하기 때문이다.
  BCrypt는 일부러 느리게 설계돼 있고, 같은 비밀번호라도 매번 다른 salt가 붙어 해시가 달라진다.
- 그래서 "저장된 해시 == 새로 만든 해시" 비교는 성립하지 않는다.
  반드시 encoder.matches(평문, 저장된해시) 를 써야 한다.
*/
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}
