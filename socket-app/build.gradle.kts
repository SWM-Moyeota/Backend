plugins {
    java
    id("org.springframework.boot")              // 버전은 루트 plugins 에서 받음
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":chat-contract"))

    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")   // API 가 발행한 채팅 이벤트 구독
    implementation("org.springframework.boot:spring-boot-starter-restclient")   // /internal 호출 - 주입되는 Builder 로 호출 지연 지표가 남음
    implementation("io.micrometer:micrometer-registry-prometheus")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "socket.jar"
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
