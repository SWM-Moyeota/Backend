package team.codingforest.moyeota.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.dto.RegisterPaymentRequest;
import team.codingforest.moyeota.payment.dto.RegisterPaymentResponse;
import team.codingforest.moyeota.payment.service.PaymentMethodService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService service;

    @PostMapping("/payments/methods")
    public ResponseEntity<RegisterPaymentResponse> register(@RequestHeader("X-User-Id") Long userId, @RequestBody RegisterPaymentRequest req) {

        return ResponseEntity.status(HttpStatus.CREATED).body((service.register(userId, req.billingKey())));
    }

    @GetMapping("/payments/methods")
    public ResponseEntity<PaymentMethodResponse> find(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(service.findActiveCard(userId));
    }
}
