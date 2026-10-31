package team.codingforest.moyeota.socket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 채팅 WebSocket 연결을 유지하고 Redis 로 받은 이벤트를 자기 세션에만 전달함
 * API 서버를 배포해도 연결이 끊기지 않게 따로 띄움
 */
@SpringBootApplication
public class SocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocketApplication.class, args);
    }
}