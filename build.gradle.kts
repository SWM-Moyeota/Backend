import java.net.Socket

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "team.codingforest"
version = providers.gradleProperty("appVersion").getOrElse("0.0.1-SNAPSHOT")

// 구성 단계에서 git 커밋을 읽어 build-info 에 싣는다 (providers.exec 는 configuration cache 호환)
val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

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
    implementation("org.springframework.modulith:spring-modulith-events-core")

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

springBoot {
    buildInfo {
        properties {
            additional.put("commit", gitCommit)
        }
    }
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.jar {
    enabled = false
}

// 테스트를 두 suite 로 나눈다 (JVM Test Suite 플러그인 - java 플러그인이 자동 적용).
//   test            : src/unitTest        단위·슬라이스 테스트. 외부 서비스 없이 어디서나 돈다
//   integrationTest : src/integrationTest @SpringBootTest 처럼 Redis·Postgres 가 필요한 것.
//                     로컬에 서비스가 없으면 실행 단계에서 건너뛴다(onlyIf). CI 는 서비스 컨테이너로 돈다
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            sources {
                java { setSrcDirs(listOf("src/unitTest/java")) }
                resources { setSrcDirs(listOf("src/unitTest/resources")) }
            }
        }

        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            sources {
                // project() 의존은 plain jar 를 거치는데 jar 태스크를 꺼 두었다. main 출력물을 직접 올린다
                compileClasspath += sourceSets.main.get().output
                runtimeClasspath += sourceSets.main.get().output
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(test)
                    onlyIf("localhost:6379(Redis)·5432(Postgres) 가 열려 있을 때만") {
                        listOf(6379, 5432).all { port ->
                            runCatching { Socket("localhost", port).close() }.isSuccess
                        }
                    }
                }
            }
        }
    }
}

// 통합 suite 는 단위 suite 와 같은 테스트 라이브러리(Boot test starter, Modulith test, Lombok)를 쓴다
listOf("Implementation", "CompileOnly", "RuntimeOnly", "AnnotationProcessor").forEach { kind ->
    configurations["integrationTest$kind"].extendsFrom(configurations["test$kind"])
}

tasks.check {
    dependsOn(testing.suites.named("integrationTest"))
}
