FROM openjdk:17
WORKDIR /usr/src/server

RUN curl --output server.jar https://api.purpurmc.org/v2/purpur/1.19.4/1970/download

COPY eula.txt .
CMD ["java", "-jar", "server.jar"]

EXPOSE 25565
