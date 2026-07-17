FROM python:3.13.5-alpine3.22
RUN addgroup -S hhy \
    && adduser -S -G hhy -u 10002 hhy \
    && mkdir -p /data \
    && chown hhy:hhy /data
WORKDIR /app
COPY --chown=hhy:hhy infra/staging/alert-sink/alert_sink.py /app/alert_sink.py
USER 10002:10002
EXPOSE 8080
ENTRYPOINT ["python", "/app/alert_sink.py"]
