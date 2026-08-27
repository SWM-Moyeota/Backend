package team.codingforest.moyeota.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;
import team.codingforest.moyeota.payment.service.PaymentGroupService;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentGroupController {
    private final PaymentGroupService paymentGroupService;

    @PostMapping("/paymentgroup")
    public void createPaymentGroup(@RequestBody PaymentGroupReq req){
        paymentGroupService.createGroup(req);
    }

}
