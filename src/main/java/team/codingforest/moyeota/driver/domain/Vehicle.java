package team.codingforest.moyeota.driver.domain;

// TODO 예외처리 재작성
public record Vehicle(Integer seats, String plateNumber, String type) {
    public Vehicle {
        if(plateNumber == null || plateNumber.isBlank()) throw new IllegalArgumentException("차량 번호는 필수입니다.");

        if(seats == null || seats < 1) throw new IllegalArgumentException("좌석 수가 올바르지 않습니다.");
    }
}
