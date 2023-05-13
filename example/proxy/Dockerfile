FROM openjdk:17
WORKDIR /usr/src/server

RUN curl --output velocity.jar https://api.papermc.io/v2/projects/velocity/versions/3.2.0-SNAPSHOT/builds/252/downloads/velocity-3.2.0-SNAPSHOT-252.jar 

# COPY velocity.toml velocity.toml
RUN mkdir plugins
COPY velokube-1.0-SNAPSHOT-all.jar plugins/velokube.jar

CMD ["java", "-jar", "velocity.jar"]

EXPOSE 25577
