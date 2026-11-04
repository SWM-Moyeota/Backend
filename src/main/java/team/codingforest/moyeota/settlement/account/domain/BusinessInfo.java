package team.codingforest.moyeota.settlement.account.domain;

/**
 * 셀러 등록에 쓰는 사업자 정보. 결제사로만 넘기고 저장하지 않음
 *
 * @param name                       상호, 없으면 null
 * @param representativeName         대표자명
 * @param businessRegistrationNumber 사업자등록번호, 하이픈 없는 10자리
 * @param email                      이메일
 * @param phone                      전화번호, 하이픈 없는 숫자 8자 이상
 */
public record BusinessInfo(String name, String representativeName, String businessRegistrationNumber, String email, String phone) {

    @Override
    public String toString() {
        return "BusinessInfo[name=" + name + ", representativeName=***, businessRegistrationNumber=***, email=***, phone=***]";
    }
}
