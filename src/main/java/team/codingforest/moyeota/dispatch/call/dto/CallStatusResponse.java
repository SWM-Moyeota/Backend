package team.codingforest.moyeota.dispatch.call.dto;

public record CallStatusResponse(boolean open) {
    public static CallStatusResponse of(boolean open) {
        return new CallStatusResponse(open);
    }
}
