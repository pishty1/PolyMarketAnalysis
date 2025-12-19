# Polymarket Real-Time Analytics Pipeline

This project implements a real-time data pipeline for Polymarket, consisting of a Python ingestion client, a Kafka message broker, and a Flink analytics engine running on Kubernetes.

## Architecture

1.  **Ingestion Producer**: A Python application that subscribes to Polymarket's WebSocket API and pushes market events to Kafka.
2.  **Kafka**: Acts as the message broker buffering events.
3.  **Flink Analytics**:
    -   **Market Analytics**: A Java-based Flink job that consumes events from Kafka, performs real-time aggregation/analytics.
    -   **Sentiment Analysis**: A Flink job that performs sentiment analysis on market data.
    -   **Session Cluster**: An interactive Flink session cluster for ad-hoc job submission.
4.  **Infrastructure**: The entire stack runs on Kubernetes, managed via Helm charts and K8s manifests. Includes Prometheus/Grafana for monitoring and Postgres for storage.

## Project Structure

- `apps/`
    - `ingestion-producer/`: Python client for fetching Polymarket data.
    - `flink-analytics/`: Flink Java application for processing data streams.
- `infrastructure/`
    - `helm/`: Values files for Helm charts (Kafka, Postgres, Prometheus).
    - `k8s/`: Kubernetes manifests for deployments, services, and the Flink Operator (including Market Analytics, Sentiment Analysis, and Session Cluster).
- `Tiltfile`: Configuration for local development with Tilt.

## Prerequisites

- [Docker](https://www.docker.com/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Helm](https://helm.sh/)
- [Java JDK 17](https://adoptium.net/) (for building Flink jobs)
- [Maven](https://maven.apache.org/) (for building Flink jobs)
- [Tilt](https://docs.tilt.dev/install.html) (for local development)

## Quick Start

The project uses [Tilt](https://tilt.dev/) for local development, which handles building, deploying, and live-reloading automatically.

### 1. Start the Cluster
Create a Minikube cluster with sufficient resources:

```bash
minikube start --cpus 4 --memory 7000
```

### 2. Configure Docker Environment
Point your terminal's Docker client to Minikube's Docker daemon:

```bash
eval $(minikube docker-env)
```

### 3. Start the Development Environment
Run Tilt to build images, deploy infrastructure, and start all applications:

```bash
tilt up
```

Tilt will automatically:
- Install infrastructure (Flink Operator, Kafka, Postgres, Prometheus/Grafana)
- Build Docker images for the Ingestion Producer and Flink Analytics jobs
- Deploy all applications (Ingestion, Market Analytics, Sentiment Analysis, Session Cluster) with proper dependency ordering
- Set up port forwarding for services


To stop and clean up all resources:
```bash
tilt down
```

## Monitoring & Access

### Tilt UI
[http://localhost:10350](http://localhost:10350)

### To access the Grafana UI run this command in a separate terminal: (login: admin/admin)
```
kubectl port-forward svc/prometheus-grafana 3000:80 
```
[http://localhost:3000](http://localhost:3000)

### To access the Flink UIs run these commands in separate terminals:

**Market Analytics:**
```bash
kubectl port-forward svc/poly-example-rest 8081
```

**Sentiment Analysis:**
```bash
kubectl port-forward svc/poly-sentiment-rest 8082:8081
```

**Session Cluster:**
```bash
kubectl port-forward svc/flink-session-rest 8083:8081
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

Tilt provides **live reload** - changes to source files automatically trigger rebuilds and redeployments.

### Modifying the Flink Job
1.  Edit code in `apps/flink-analytics/jobs/poly/src/`.
2.  Tilt automatically runs Maven, rebuilds the Docker image, and redeploys.

### Modifying the Ingestion Producer
1.  Edit code in `apps/ingestion-producer/`.
2.  Tilt automatically rebuilds and redeploys the container.


SQL Client 

./bin/sql-client.sh embedded -Drest.address=localhost -Drest.port=8081