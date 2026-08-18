package team.codingforest.moyeota.driver.domain;


import lombok.Getter;

// 확장성을 위한 세팅
@Getter
public class DriverSetting {
    private boolean callEnabled;

    private DriverSetting(boolean callEnabled) {
        this.callEnabled = callEnabled;
    }

    public static DriverSetting defaults() {
        return new DriverSetting(true);
    }

    public void enabledCall() {
        this.callEnabled = true;
    }

    public void disableCall() {
        this.callEnabled = false;
    }

    public static DriverSetting restore(boolean callEnabled) {
        return new DriverSetting(callEnabled);
    }
}
