#!/bin/bash
set -e

echo "Starting Minikube..."
minikube start --cpus 4 --memory 7000

echo "Installing Cert Manager (Required for Flink Operator)..."
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
kubectl wait --for=condition=Available deployment --all -n cert-manager --timeout=300s

echo "Waiting for cert-manager webhook to be fully ready..."
sleep 30

echo "Installing Flink Kubernetes Operator..."
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.7.0/ || true
helm upgrade --install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator

helm repo add bitnami https://charts.bitnami.com/bitnami || true
helm repo update

echo "Installing Kafka..."
# Using raw manifest for Kafka (Confluent image) to avoid Bitnami image issues
kubectl apply -f infrastructure/k8s/kafka.yaml
kubectl wait --for=condition=Ready pod/kafka-0 --timeout=300s

echo "Installing Postgres..."
helm upgrade --install postgres bitnami/postgresql -f infrastructure/helm/postgres-values.yaml

echo "Installing Prometheus Stack..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
helm repo update
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack -f infrastructure/helm/prometheus-values.yaml

echo "Cluster Bootstrap Complete!"
