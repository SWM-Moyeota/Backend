plugins {
    // java.toolchain 에 적은 JDK 가 없으면 내려받는다 - 팀원 PC 마다 JDK 25 를 손으로 깔지 않아도 된다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "moyeota"
