package team.codingforest.moyeota.dispatch.application.dto;

public record CallStatusResponse(boolean open) {
    public static CallStatusResponse of(boolean open) {
        return new CallStatusResponse(open);
    }
}
