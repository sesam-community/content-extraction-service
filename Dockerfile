FROM java:8-jre-alpine

ADD target/content-extraction-service-1.0-SNAPSHOT.jar /srv/

ENTRYPOINT ["java", "-jar", "/srv/content-extraction-service-1.0-SNAPSHOT.jar"]


