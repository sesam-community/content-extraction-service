FROM java:8-jre-alpine

COPY entrypoint.sh /
ENTRYPOINT ["/entrypoint.sh"]

ADD target/content-extraction-service-1.0-SNAPSHOT.jar /srv/

