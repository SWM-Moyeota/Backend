package team.codingforest.moyeota.matching.domain;

public record Capacity(int value) {
    private static final int MIN_CAPACITY = 1;
    private static final int MAX_CAPACITY = 3;
    public Capacity {
        if(value < MIN_CAPACITY || value > MAX_CAPACITY) throw new IllegalArgumentException("방의 정원은 1~3명 있어야 함 : " + value);
    }
}
