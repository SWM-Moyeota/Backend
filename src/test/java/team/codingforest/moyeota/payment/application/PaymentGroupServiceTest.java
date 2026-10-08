package team.codingforest.moyeota.payment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.GatewayPayment;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroupStatus;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;
import team.codingforest.moyeota.payment.dto.PaymentGroupResponse;
import team.codingforest.moyeota.payment.dto.PaymentResponse;
import team.codingforest.moyeota.payment.dto.RideCompletedCommand;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGroupServiceTest {
    private static final Long 방 = 1L;
    private static final Long 기사 = 9L;
    private static final Long 방장 = 1L;
    private static final Long 동승자 = 2L;
    private static final Long 남남 = 99L;
    private static final RideCompletedCommand 운행종료 = new RideCompletedCommand(방, 기사, 10000, "강남역", "판교역", List.of(방장, 동승자));

    private FakePaymentGroups groups;
    private FakePaymentMethods methods;
    private FakePaymentGateway gateway;
    private FakeUserAccess users;
    private PaymentGroupService service;

    @BeforeEach
    void setUp() {
        groups = new FakePaymentGroups();
        methods = new FakePaymentMethods();
        gateway = new FakePaymentGateway();
        users = new FakeUserAccess();
        users.등록(방장);
        users.등록(동승자);
        service = new PaymentGroupService(groups, gateway, new PaymentChargeSteps(groups, methods, new PaymentPolicy(500)), users);
    }

    private String 카드등록(Long userId) {
        String billingKey = "bk-" + userId;
        methods.save(PaymentMethod.register(userId, billingKey, new CardDetail("신한카드", "1234-56**-****-7890", "VISA"), Instant.now()));
        return billingKey;
    }

    private PaymentGroup 그룹() {
        return groups.findByPartyId(방).orElseThrow();
    }

    private static Payment 분담금(PaymentGroup group, Long userId) {
        return group.getPayments().stream().filter(p -> p.getType() == PaymentType.SHARE && p.isOwnedBy(userId)).findFirst().orElseThrow();
    }

    // ───────────────────────── 운행 종료 → 자동 청구 ─────────────────────────

    @Test
    void 운행이_끝나면_구성원_전원에게_분담금이_청구된다() {
        카드등록(방장);
        카드등록(동승자);

        service.openForRide(운행종료);

        PaymentGroup group = 그룹();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
        assertThat(group.getRemainingBalance()).isZero();
        assertThat(group.getPayments()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID));
        // 5,000원씩 + 이용료 500원. 결제사엔 유저 publicId 가 customer 로 간다
        assertThat(gateway.charged).hasSize(2);
        assertThat(gateway.charged).allSatisfy(o -> {
            assertThat(o.amount()).isEqualTo(5500);
            assertThat(o.customerId()).isNotNull();
            assertThat(o.orderName()).contains("강남역").contains("판교역");
        });
        assertThat(gateway.charged).extracting(o -> o.billingKey()).containsExactlyInAnyOrder("bk-1", "bk-2");
    }

    @Test
    void 결제수단이_없는_구성원은_NO_PAYMENT_METHOD_로_실패하고_결제사를_호출하지_않는다() {
        카드등록(방장);

        service.openForRide(운행종료);

        PaymentGroup group = 그룹();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
        assertThat(분담금(group, 동승자).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(분담금(group, 동승자).getFailCode()).isEqualTo(PaymentChargeSteps.FAIL_NO_PAYMENT_METHOD);
        assertThat(gateway.charged).hasSize(1);
    }

    @Test
    void 결제사가_거절하면_거절_코드로_FAILED_가_된다() {
        카드등록(방장);
        gateway.rejections.put(카드등록(동승자), "PG_51");

        service.openForRide(운행종료);

        assertThat(분담금(그룹(), 동승자).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(분담금(그룹(), 동승자).getFailCode()).isEqualTo("PG_51");
    }

    @Test
    void 같은_방의_이벤트가_다시_와도_그룹은_하나고_이중_청구는_없다() {
        // 아웃박스 재전송 - 멱등
        카드등록(방장);
        카드등록(동승자);
        service.openForRide(운행종료);

        service.openForRide(운행종료);

        assertThat(groups.findAllByUserId(방장)).hasSize(1);
        assertThat(gateway.charged).hasSize(2);
    }

    @Test
    void 결제사_장애면_예외가_올라가고_재전송_때_결제사_원본으로_결론을_맞춘다() {
        // 청구를 보냈는데 응답이 유실된 경우 - 다시 보내면 이중 결제. 결제사에 물어봐서 맞춘다
        String key = 카드등록(방장);
        카드등록(동승자);
        gateway.down = true;

        assertThatThrownBy(() -> service.openForRide(운행종료))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.GATEWAY_ERROR);

        PaymentGroup afterOutage = 그룹();
        assertThat(afterOutage.getStatus()).isEqualTo(PaymentGroupStatus.IN_PROGRESS);
        String sentId = afterOutage.getPayments().stream().map(Payment::getPgPaymentId).filter(id -> id != null).findFirst().orElseThrow();

        // 사실은 결제사가 승인했었다
        gateway.down = false;
        gateway.remote.put(sentId, new GatewayPayment(GatewayPayment.GatewayPaymentStatus.PAID, Instant.now(), null, null, null));
        service.openForRide(운행종료);

        PaymentGroup recovered = 그룹();
        assertThat(recovered.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
        assertThat(gateway.charged).as("유실된 건은 다시 보내지 않고 나머지 한 건만 보낸다").hasSize(1);
        assertThat(gateway.charged.get(0).billingKey()).isNotEqualTo(key);
    }

    @Test
    void 결제사에_닿지_않은_청구는_재전송_때_실패로_기록해_사용자가_다시_시도할_수_있게_한다() {
        카드등록(방장);
        카드등록(동승자);
        gateway.down = true;
        assertThatThrownBy(() -> service.openForRide(운행종료)).isInstanceOf(BusinessException.class);

        gateway.down = false;   // remote 에 없음 = 결제사가 받은 적 없음
        service.openForRide(운행종료);

        PaymentGroup group = 그룹();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
        assertThat(group.getPayments()).extracting(Payment::getFailCode).contains(PaymentChargeSteps.FAIL_GATEWAY_UNREACHABLE);
    }

    // ───────────────────────── 재시도 ─────────────────────────

    @Test
    void 결제수단을_등록한_뒤_재시도하면_결제된다() {
        카드등록(방장);
        service.openForRide(운행종료);
        Long failedId = 분담금(그룹(), 동승자).getId();

        카드등록(동승자);
        PaymentResponse result = service.retry(failedId, 동승자);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(그룹().getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
    }

    @Test
    void 남의_결제는_재시도할_수_없다() {
        카드등록(방장);
        service.openForRide(운행종료);
        Long failedId = 분담금(그룹(), 동승자).getId();

        assertThatThrownBy(() -> service.retry(failedId, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.NOT_PAYMENT_OWNER);
    }

    @Test
    void 없는_결제_재시도는_404() {
        assertThatThrownBy(() -> service.retry(12345L, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    // ───────────────────────── 대납 ─────────────────────────

    @Test
    void 대납하면_미수금_전액이_결제되고_실패분은_COVERED_로_종결된다() {
        카드등록(방장);
        service.openForRide(운행종료);
        Long groupId = 그룹().getId();

        PaymentResponse cover = service.cover(groupId, 방장);

        assertThat(cover.type()).isEqualTo(PaymentType.COVER);
        assertThat(cover.amount()).as("이용료 없이 동승자 요금 몫만").isEqualTo(5000);
        assertThat(cover.status()).isEqualTo(PaymentStatus.PAID);

        PaymentGroup group = 그룹();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
        assertThat(group.getRemainingBalance()).isZero();
        assertThat(분담금(group, 동승자).getStatus()).isEqualTo(PaymentStatus.COVERED);
    }

    @Test
    void 대납이_거절되면_그룹은_다시_PARTIALLY_FAILED_로_남는다() {
        String key = 카드등록(방장);
        service.openForRide(운행종료);
        gateway.rejections.put(key, "PG_LIMIT");

        PaymentResponse cover = service.cover(그룹().getId(), 방장);

        assertThat(cover.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(그룹().getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
    }

    @Test
    void 구성원이_아니면_대납할_수_없다() {
        카드등록(방장);
        service.openForRide(운행종료);

        assertThatThrownBy(() -> service.cover(그룹().getId(), 남남))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.NOT_GROUP_MEMBER);
    }

    // ───────────────────────── 웹훅 동기화 ─────────────────────────

    @Test
    void 웹훅_동기화는_결제사_원본을_따른다() {
        카드등록(방장);
        카드등록(동승자);
        service.openForRide(운행종료);
        String pgId = 분담금(그룹(), 방장).getPgPaymentId();
        gateway.cancel(pgId, "고객 요청");

        service.sync(pgId);

        PaymentGroup group = 그룹();
        assertThat(분담금(group, 방장).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(분담금(group, 방장).getCancelReason()).isEqualTo("고객 요청");
        assertThat(group.getRemainingBalance()).isEqualTo(5000);
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
    }

    @Test
    void 이미_반영된_결제의_웹훅은_아무것도_바꾸지_않는다() {
        카드등록(방장);
        카드등록(동승자);
        service.openForRide(운행종료);
        String pgId = 분담금(그룹(), 방장).getPgPaymentId();

        service.sync(pgId);   // 결제사도 PAID, 우리도 PAID

        assertThat(그룹().getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
        assertThat(분담금(그룹(), 방장).getLogs()).hasSize(2);   // READY, PAID 뿐
    }

    @Test
    void 모르는_결제_통지는_무시한다() {
        service.sync("moyeota-unknown");   // 예외 없음
    }

    // ───────────────────────── 조회 ─────────────────────────

    @Test
    void 그룹_상세는_구성원만_볼_수_있다() {
        카드등록(방장);
        카드등록(동승자);
        service.openForRide(운행종료);
        Long groupId = 그룹().getId();

        PaymentGroupResponse response = service.findGroup(groupId, 동승자);
        assertThat(response.payments()).hasSize(2);
        assertThat(response.fare()).isEqualTo(10000);

        assertThatThrownBy(() -> service.findGroup(groupId, 남남))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    void 내_결제_내역에는_내_분담금과_대납만_나온다() {
        카드등록(방장);
        service.openForRide(운행종료);
        service.cover(그룹().getId(), 방장);

        List<PaymentResponse> mine = service.findMyPayments(방장);
        assertThat(mine).extracting(PaymentResponse::type).containsExactlyInAnyOrder(PaymentType.SHARE, PaymentType.COVER);
        assertThat(service.findMyPayments(동승자)).extracting(PaymentResponse::status).containsExactly(PaymentStatus.COVERED);
    }

}
