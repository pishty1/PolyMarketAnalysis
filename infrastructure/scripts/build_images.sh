#!/bin/bash
set -e

echo "Setting Minikube Docker Environment..."
eval $(minikube docker-env)

echo "Building Ingestion Producer..."
docker build -t ingestion-producer:latest apps/ingestion-producer

echo "Building Flink Analytics..."
# Build the Jar first
cd apps/flink-analytics/jobs/poly
mvn clean package -DskipTests

# Build the Docker image
docker build -t flink-analytics:latest .
cd -

echo "Images Built Successfully!"
