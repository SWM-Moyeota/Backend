plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "team.codingforest"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.1")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")      // Boot 4.1 은 starter 없이는 auto-config 가 안 붙음

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")   // 아웃박스 패턴 - 이벤트 발행 기록을 JPA 로 저장

    // 인증·식별자
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")               // UUID v7

    // 운영: 문서·지표·스케줄러 분산 잠금
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")   // Boot 4 → springdoc 3.x
    implementation("io.micrometer:micrometer-registry-prometheus")                // 지표 수집
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.6.0")              // 서버가 여러 대여도 한 대만 돈다
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:6.6.0")

    // 외부 서비스
    implementation("com.google.firebase:firebase-admin:9.10.0")                  // FCM

    // DB 드라이버
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
