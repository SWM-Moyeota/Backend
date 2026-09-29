package team.codingforest.moyeota._config;

public record AppConfigResponse(boolean taxiEnabled) {
    public static AppConfigResponse from(boolean taxiEnabled) {
        return new AppConfigResponse(taxiEnabled);
    }
}
