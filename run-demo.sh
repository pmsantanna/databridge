#!/bin/bash
# run-demo.sh — sobe os bancos, builda e roda a migração
# Requer: Docker, Docker Compose, Java 17+, Maven

set -e

echo ""
echo "▶ DataBridge — Demo"
echo "────────────────────────────────────────"

# 1. Build
echo ""
echo "▶ Building JAR..."
mvn clean package -q -DskipTests
echo "✔ Build OK"

# 2. Sobe os bancos
echo ""
echo "▶ Starting databases..."
docker compose up -d --wait
echo "✔ source-db ready on :5433"
echo "✔ target-db  ready on :5434"

# 3. Aguarda healthcheck
sleep 2

# 4. Roda a migração
echo ""
echo "▶ Running migration..."
echo ""
java -jar target/databridge.jar migrate \
  --src-url  "jdbc:postgresql://localhost:5433/source_db" \
  --src-user postgres \
  --src-pass postgres \
  --tgt-url  "jdbc:postgresql://localhost:5434/target_db" \
  --tgt-user postgres \
  --tgt-pass postgres \
  --page-size 10

# 5. Verifica resultado
echo ""
echo "▶ Verifying target..."
docker exec databridge-target psql -U postgres -d target_db -c \
  "SELECT count(*) AS migrated_employees FROM employees;"

echo ""
echo "✔ Demo complete! Run 'docker compose down' to clean up."
echo ""
