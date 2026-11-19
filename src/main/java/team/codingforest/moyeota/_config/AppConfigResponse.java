package team.codingforest.moyeota._config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 앱이 시작할 때 한 번 받는 서버 설정.
 *
 * <p>버전 두 개는 설정하지 않으면 <b>키가 빠진다</b>({@link JsonInclude.Include#NON_NULL}). 앱은 키가 없으면
 * 기준이 없는 것으로 보고 통과시킨다. 둘 다 설정하지 않으면 빈 객체({@code {}})가 나간다.
 *
 * @param minVersionCode         미달이면 앱이 빠져나갈 수 없는 업데이트 화면에서 멈춘다. 기준 없으면 null
 * @param recommendedVersionCode 미달이면 앱이 막지 않고 한 번 알린다. 기준 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppConfigResponse(Integer minVersionCode, Integer recommendedVersionCode) {

    public static AppConfigResponse from(AppProperties app) {
        return new AppConfigResponse(app.minVersionCode(), app.recommendedVersionCode());
    }
}
