package team.codingforest.moyeota.matching.domain;

public record Capacity(int value) {
    public Capacity {
        if(value <= 0 || value >= 4) throw new IllegalArgumentException("방의 정원은 1~3명 있어야 함 : " + value);
    }
}
