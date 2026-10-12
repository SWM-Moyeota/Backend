package team.codingforest.moyeota.user.verification.domain;

public interface SmsSender {
    void send(String phoneNumber, String text);
}
