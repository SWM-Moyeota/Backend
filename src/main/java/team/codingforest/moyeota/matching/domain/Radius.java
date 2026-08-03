package team.codingforest.moyeota.matching.domain;

// TODO - 예외처리 해야함
public record Radius(int meters) {
    private static final int MIN_RAD = 100;
    private static final int MAX_RAD = 500;

    public Radius {
        if(meters < MIN_RAD || meters > MAX_RAD) throw new IllegalArgumentException("허용 반경이 아닙니다.");
    }
}