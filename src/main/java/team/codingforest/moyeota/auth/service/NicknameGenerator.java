package team.codingforest.moyeota.auth.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/*
닉네임 자동 생성기. "형용사 + 동물 + #네자리숫자"를 무작위로 만들어준다. (예: "부끄러워하는 펭귄#4821")

이름이 두 종류인 이유:
- user_profile.name  : 실명. 본인 확인용이라 남에게 보여주는 값이 아니다.
- user.nickname      : 다른 사용자에게 보이는 이름.

그래서 가입할 때 실명이나 구글 계정 이름을 닉네임으로 그대로 쓰면 안 된다.
소셜이든 로컬이든 여기서 만든 값을 넣고, 사용자가 원하면 마이페이지에서 바꾼다.

뒤에 붙는 숫자는 아무 의미가 없는 값이어야 한다.
userId(1,2,3...)를 쓰면 "#0007"만 보고 일곱 번째 가입자임을 알 수 있어서 가입자 수가 새어나간다.
publicId 앞자리를 떼어 쓰면 사용자를 따라다니는 고정값이 되어, 닉네임을 바꿔도 같은 숫자가 남는다.
이 숫자는 사용자를 가리키는 값이 아니라 같은 단어 조합끼리 구분해주는 꼬리표일 뿐이므로
매번 새로 뽑는 무작위 값이 맞다.

[한계] 이걸로 닉네임이 유일해지지는 않는다. 30 x 30 x 10000 = 9,000,000가지라
확률이 크게 낮아질 뿐이고(사용자 3,500명쯤에서 같은 닉네임 쌍이 나올 확률이 50%),
nickname에 unique 제약도 없고 생성할 때 중복 확인도 하지 않는다.
사용자를 찾는 기준은 publicId라서 같은 닉네임이 있어도 기능이 깨지지는 않는다.
정말 유일해야 한다면 unique 제약을 걸고 중복이면 다시 뽑는 방식이 필요하다.
*/
public final class NicknameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "부끄러워하는", "센치한", "부드러운", "열정적인", "사랑스러운", "강렬한",
            "용감한", "다정한", "씩씩한", "엉뚱한", "느긋한", "재빠른",
            "꼼꼼한", "신비로운", "상냥한", "명랑한", "조용한", "우아한",
            "장난스러운", "성실한", "자유로운", "따뜻한", "눈부신", "새침한",
            "든든한", "발랄한", "진지한", "유쾌한", "포근한", "대담한");

    private static final List<String> ANIMALS = List.of(
            "펭귄", "다람쥐", "토끼", "참새", "고양이", "강아지", "여우",
            "너구리", "수달", "판다", "고래", "돌고래", "부엉이", "올빼미",
            "사슴", "늑대", "곰", "호랑이", "사자", "코알라", "햄스터",
            "고슴도치", "두더지", "물개", "해달", "앵무새", "까치", "백조",
            "거북이", "알파카");

    //#0000 ~ #9999. 상한을 여기 하나로 모아두면 자릿수를 바꿀 때 한 곳만 고치면 된다.
    private static final int SUFFIX_BOUND = 10000;

    //인스턴스를 만들 일이 없는 클래스다. 실수로 new 하지 못하게 막아둔다.
    private NicknameGenerator() {
    }

    public static String generate() {

        //new Random()을 필드에 하나 두고 공유하지 않고 ThreadLocalRandom을 쓴다.
        //Random 하나를 여러 요청이 나눠 쓰면 다음 값을 꺼낼 때마다 서로 기다리게 되기 때문이다.
        //ThreadLocalRandom은 요청을 처리하는 스레드마다 따로 있어서 그런 일이 없다.
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String animal = ANIMALS.get(random.nextInt(ANIMALS.size()));

        //%04d는 앞을 0으로 채워 항상 네 자리로 만든다. 7 -> "0007"
        //자릿수가 들쭉날쭉하면("#7"과 "#4821") 화면에서 줄이 흔들리고, 잘린 값처럼 보인다.
        String suffix = String.format("%04d", random.nextInt(SUFFIX_BOUND));

        return adjective + " " + animal + "#" + suffix;
    }
}
