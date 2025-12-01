# Polymarket Real-Time Analytics Pipeline

This project implements a real-time data pipeline for Polymarket, consisting of a Python ingestion client, a Kafka message broker, and a Flink analytics engine running on Kubernetes.

## Architecture

1.  **Ingestion Producer**: A Python application that subscribes to Polymarket's WebSocket API and pushes market events to Kafka.
2.  **Kafka**: Acts as the message broker buffering events.
3.  **Flink Analytics**: A Java-based Flink job that consumes events from Kafka, performs real-time aggregation/analytics.
4.  **Infrastructure**: The entire stack runs on Kubernetes, managed via Helm charts and K8s manifests. Includes Prometheus/Grafana for monitoring and Postgres for storage.

## Project Structure

- `apps/`
    - `ingestion-producer/`: Python client for fetching Polymarket data.
    - `flink-analytics/`: Flink Java application for processing data streams.
- `infrastructure/`
    - `helm/`: Values files for Helm charts (Kafka, Postgres, Prometheus).
    - `k8s/`: Kubernetes manifests for deployments, services, and the Flink Operator.
    - `scripts/`: Helper scripts for bootstrapping and building.
- `Makefile`: Entry point for common development tasks.

## Prerequisites

- [Docker](https://www.docker.com/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Helm](https://helm.sh/)
- [Java JDK 17](https://adoptium.net/) (for building Flink jobs)
- [Maven](https://maven.apache.org/) (for building Flink jobs)

## Quick Start

The project includes a `Makefile` to streamline the setup, build, and deployment process.

### 1. Setup Cluster
Initialize Minikube and install infrastructure dependencies (Cert Manager, Flink Operator, Kafka, Postgres, Prometheus).

```bash
make setup
```

### 2. Build Images
Build the Docker images for the Ingestion Producer and Flink Analytics job. This script automatically points to Minikube's Docker daemon.

```bash
make build
```

### 3. Deploy Applications
Deploy the custom applications (Producer and Flink Job) to the cluster.

```bash
make deploy
```

## Monitoring & Access

### Port Forwarding
To access the Flink UI

```bash
kubectl port-forward svc/poly-example-rest 8081:8081
```
To access the Grafana dashboard

```bash
kubectl port-forward svc/prometheus-grafana 3000:80
```

### Logs
Check the logs of the ingestion producer:
```bash
kubectl logs -f -l app=polymarket-client
```

Check the logs of the Flink TaskManager:
```bash
kubectl logs -f -l component=taskmanager
```

## Development

### Modifying the Flink Job
1.  Edit code in `apps/flink-analytics/jobs/poly/src/`.
2.  Run `make build` to rebuild the JAR and Docker image.
3.  Run `make deploy` to update the deployment.

### Modifying the Ingestion Producer
1.  Edit code in `apps/ingestion-producer/`.
2.  Run `make build` to rebuild the Docker image.
3.  Restart the deployment:
    ```bash
    kubectl rollout restart deployment/polymarket-client
    ```
