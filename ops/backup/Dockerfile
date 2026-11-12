FROM postgres:15-alpine
RUN apk add --no-cache age \
    && mkdir -p /backups \
    && chown postgres:postgres /backups
COPY backup-once.sh restore-test.sh backup-scheduler.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/backup-once.sh /usr/local/bin/restore-test.sh /usr/local/bin/backup-scheduler.sh
USER postgres
ENV HOME=/tmp
ENTRYPOINT ["/usr/local/bin/backup-scheduler.sh"]
