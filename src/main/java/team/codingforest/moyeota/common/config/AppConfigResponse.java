package team.codingforest.moyeota.common.config;

public record AppConfigResponse(boolean taxiEnabled) {
    public static AppConfigResponse from(boolean taxiEnabled) {
        return new AppConfigResponse(taxiEnabled);
    }
}
