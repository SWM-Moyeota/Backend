-- 택시 기사·배차·정산·지급·긴급 신고 기능을 코드에서 지워 아래 8개 테이블은 매핑하는 엔티티도, 쓰는 쿼리도 없다
--   taxi, taxi_driver, driver_setting, driver_report (V1) / settlement, settlement_ledger, payout (V22) / driver_payout_account (V29)
-- 2026-11-05 읽기 전용 확인: dev(V30) 8개, prod(V22) driver_payout_account 를 뺀 7개 모두 0행.
-- 걸린 FK 는 서로 사이(driver_setting·taxi -> taxi_driver, settlement_ledger·payout -> settlement)뿐이다
-- 손으로 지우지 않는다 - prod 는 V23·V30 이 이 테이블을 고치므로 미리 지우면 배포가 멈추고, 지금 도는 main jar 도 이 테이블을 매핑한다(validate)
-- CASCADE 를 붙이지 않는다 - 확인하지 못한 의존이 있으면 조용히 함께 지우지 말고 실패해야 한다(한 트랜잭션이라 하나도 안 지워진다)
-- match_room·ride_record·users 의 안 쓰는 컬럼은 #301 코드가 모든 서버와 롤백 대상에 깔린 다음 릴리스에서 지운다
DROP TABLE IF EXISTS
    driver_setting,
    taxi,
    taxi_driver,
    driver_report,
    settlement_ledger,
    payout,
    settlement,
    driver_payout_account;
