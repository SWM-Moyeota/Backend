package team.codingforest.moyeota.user.verification.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;
import team.codingforest.moyeota.user.verification.dto.SmsSendRequest;
import team.codingforest.moyeota.user.verification.dto.SmsVerifyRequest;
import team.codingforest.moyeota.user.verification.dto.SmsVerifyResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/sms")
public class SmsVerificationController {

    private final SmsVerificationService smsVerificationService;

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@Valid @RequestBody SmsSendRequest request) {
        smsVerificationService.send(request.phoneNumber());
    }

    @PostMapping("/verify")
    public SmsVerifyResponse verify(@Valid @RequestBody SmsVerifyRequest request) {
        return new SmsVerifyResponse(smsVerificationService.verify(request.phoneNumber(), request.code()));
    }
}
