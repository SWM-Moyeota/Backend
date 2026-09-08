package team.codingforest.moyeota.dispatch.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(@Value("${fcm.service-account-path}") String serviceAccountPath) throws IOException {
        // FirebaseApp 은 JVM 전역 정적 레지스트리 - 테스트 컨텍스트가 둘 이상 뜨거나 DevTools 재시작 시 재초기화하면 "already exists" 로 죽는다
        if(!FirebaseApp.getApps().isEmpty()) return FirebaseApp.getInstance();

        try (InputStream credentials = new FileInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();

            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
