# socket-app 이미지 - CI 가 ./gradlew :socket-app:bootJar 로 만든 socket.jar 를 넣음
# 빌드 컨텍스트는 socket-app/ (docker build -f socket-app/Dockerfile socket-app)

# 1단계: jar 를 레이어별로 풀어 의존성(잘 안 바뀜)과 우리 코드(자주 바뀜)를 다른 레이어에 둠
FROM eclipse-temurin:25-jre AS extract
WORKDIR /work
COPY build/libs/socket.jar socket.jar
RUN java -Djarmode=tools -jar socket.jar extract --layers --launcher --destination extracted

# 2단계: 실행 이미지
FROM eclipse-temurin:25-jre
WORKDIR /app
# root 로 돌리지 않음
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./
USER app
EXPOSE 8081 9091
# 컨테이너 메모리 한도의 75% 를 힙으로
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# exec 형식이라 java 가 PID 1 이 되어 docker stop 의 SIGTERM 을 바로 받음
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
