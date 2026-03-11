FROM eclipse-temurin:24-jdk

WORKDIR /app

COPY TCPServer.jar app.jar
COPY .env .env

RUN mkdir extracted && \
    cd extracted && \
    jar xf ../app.jar && \
    rm -f META-INF/*.SF META-INF/*.RSA META-INF/*.DSA && \
    jar cfm ../app.jar META-INF/MANIFEST.MF . && \
    cd .. && rm -rf extracted

EXPOSE 15555
EXPOSE 15556
EXPOSE 15557

CMD ["java", "-jar", "app.jar"]
