FROM rwasp-docker-push.prod.jp.local/rwasp-oraclejava8:1.0.0
ADD jhipster-sample-application-*.war app.war
RUN chmod +x app.war
CMD ./rwasp-run.sh /bin/sh -c "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.war"
