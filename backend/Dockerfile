FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew build -x test -x spotlessCheck --no-daemon

# CDS training run: Spring context 를 한 번 refresh 시켜 app.jsa 생성
RUN cp build/libs/*.jar app.jar \
 && java -Dspring.profiles.active=training \
         -Dspring.context.exit=onRefresh \
         -XX:ArchiveClassesAtExit=app.jsa \
         -jar app.jar \
 && ls -la app.jsa

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
COPY --from=build /app/app.jsa app.jsa
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
