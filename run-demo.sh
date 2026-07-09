#!/bin/bash
set -e

export POSTGRES_HOST=localhost
export POSTGRES_PORT=5442
export POSTGRES_DB=cetdemo
export POSTGRES_USER=cet
export POSTGRES_PASSWORD=components

echo "Starting PostgreSQL container..."
docker run -d \
  --rm \
  --name cet-demo \
  -e POSTGRES_DB=${POSTGRES_DB} \
  -e POSTGRES_USER=${POSTGRES_USER} \
  -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
  -p ${POSTGRES_PORT}:5432 \
  --health-cmd="pg_isready -U ${POSTGRES_USER}" \
  --health-interval=2s \
  --health-timeout=3s \
  --health-retries=15 \
  postgres:17

# Wait for database to be healthy
echo "Waiting for PostgreSQL to be ready..."
for i in {1..30}; do
  if docker exec cet-demo pg_isready -U ${POSTGRES_USER} > /dev/null 2>&1; then
    echo "✓ PostgreSQL is ready"
    break
  fi
  echo "  Attempt $i/30..."
  sleep 1
done

docker port cet-demo

echo "Starting Spring Boot application..."
./gradlew bootRun -Dspring.profiles.active="local,demo"

echo "Stopping PostgreSQL container..."
docker stop cet-demo
