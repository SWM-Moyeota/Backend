package team.codingforest.moyeota.common.bank;

public enum BankCode {
	IBK("기업은행"),
	KOOKMIN("국민은행"),
	K_BANK("케이뱅크"),
	KAKAO("카카오뱅크"),
	TOSS("토스뱅크");

	private final String displayName;

	BankCode(String displayName) {
		this.displayName = displayName;
	}
}