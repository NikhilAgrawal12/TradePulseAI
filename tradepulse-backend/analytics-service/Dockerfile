FROM python:3.11-slim

WORKDIR /app
ENV PYTHONPATH=/app

# Install Java (required by PySpark) and procps (provides 'ps' command)
RUN apt-get update && \
    apt-get install -y --no-install-recommends default-jdk-headless procps && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/default-java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY tests ./tests

EXPOSE 4010

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "4010"]

