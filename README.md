# Polymarket Client with Kafka and Kubernetes

This project contains a Python client that subscribes to Polymarket's real-time data streams and pushes events to a Kafka topic. The entire stack is designed to be deployed and tested on Kubernetes.

## Project Structure

- `source/`: Contains the Python application source code and its Dockerfile.
- `k8s/`: Contains all Kubernetes manifests for deployment.
  - `kafka.yaml`: Deploys a single-node Kafka broker using KRaft mode.
  - `config.yaml`: Defines the `ConfigMap` and `Secret` for the application.
  - `deployment.yaml`: Deploys the Polymarket client application.
  - `kafka-consumer-test.yaml`: Deploys a test pod to consume messages from Kafka.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- A running Kubernetes cluster (e.g., [Minikube](https://minikube.sigs.k8s.io/docs/start/), [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/), Docker Desktop).

---

## End-to-End Testing Guide

This guide walks through building the client, deploying the full stack (Kafka and the client), and verifying that messages are being processed correctly.

### Step 1: Build the Docker Image

From the project root (`app/`):
```sh
docker build -t polymarket-client:latest ./source
```

### Step 2: Load Image Into Your Kubernetes Cluster

This step is required for local clusters like Minikube or Kind to make the local Docker image available.

```sh
# For Docker Desktop Kubernetes (no action needed)

# For Minikube
minikube image load polymarket-client:latest

# For Kind
kind load docker-image polymarket-client:latest
```

### Step 3: Deploy Kafka and Application

Deploy Kafka, the application configuration, and the client deployment itself.

```sh
# Navigate to the project root
cd /Users/pishty/ws/polymarket/app

# 1. Deploy Kafka and wait for it to be ready
kubectl apply -f k8s/kafka.yaml
echo "Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka --timeout=120s

# 2. Deploy the ConfigMap/Secret and the Polymarket client
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/deployment.yaml
```

### Step 4: Verify the Deployment

Check that the pods are running and inspect the client logs to ensure it's connected.

```sh
# Check that all pods are running
kubectl get pods

# You should see kafka-0 and polymarket-client-* pods in 'Running' state

# Stream the Polymarket client logs
kubectl logs -f deployment/polymarket-client
```
You should see log output indicating a successful WebSocket connection and Kafka initialization.

### Step 5: Test the Kafka Consumer

Deploy a test consumer pod that will listen to the `polymarket-messages` topic and print its contents.

```sh
# Deploy the test consumer
kubectl apply -f k8s/kafka-consumer-test.yaml

# View the messages being consumed from the topic
kubectl logs -f kafka-consumer-test
```
This will show all messages being pushed to the `polymarket-messages` topic in real-time.

---

## Development Workflow: Redeploying Code

After making changes to `source/polym.py`, follow these steps to redeploy:

1.  **Rebuild the Docker image:**
    ```sh
    docker build -t polymarket-client:latest ./source
    ```

2.  **Load the new image** (if using Minikube/Kind, see Step 2 above).

3.  **Restart the deployment** to force Kubernetes to pull the new image:
    ```sh
    kubectl rollout restart deployment/polymarket-client
    ```

4.  **Monitor the rollout:**
    ```sh
    kubectl rollout status deployment/polymarket-client
    ```

---

## Cleanup

To remove all the Kubernetes resources created by this guide, run:
```sh
kubectl delete -f k8s/deployment.yaml
kubectl delete -f k8s/config.yaml
kubectl delete -f k8s/kafka.yaml
kubectl delete -f k8s/kafka-consumer-test.yaml
```

---

## Useful Kubernetes Commands

- **View all pods:** `kubectl get pods`
- **View all services:** `kubectl get services`
- **View logs for Polymarket client:** `kubectl logs -f deployment/polymarket-client`
- **View logs for Kafka:** `kubectl logs -f kafka-0`
- **View logs for consumer test:** `kubectl logs -f kafka-consumer-test`
- **Restart the Polymarket client:** `kubectl rollout restart deployment/polymarket-client`
- **Execute a shell in the Kafka pod:** `kubectl exec -it kafka-0 -- bash`
- **List Kafka topics (from inside the cluster):**
  ```sh
  kubectl exec kafka-0 -- kafka-topics --list --bootstrap-server localhost:9092
  ```
- **Describe a topic:**
  ```sh
  kubectl exec kafka-0 -- kafka-topics --describe --topic polymarket-messages --bootstrap-server localhost:9092
  ```
- **Manually consume messages from a topic:**
  ```sh
  kubectl exec -it kafka-0 -- kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic polymarket-messages \
    --from-beginning
  ```
- **Check pod status and events:** `kubectl describe pod <pod-name>`
- **View all resources:** `kubectl get all`
