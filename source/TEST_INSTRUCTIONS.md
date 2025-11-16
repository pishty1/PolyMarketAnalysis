# Testing Kafka Integration with Kubernetes

This guide will help you test that Polymarket events are being pushed to Kafka using Kubernetes.

## Prerequisites

- Kubernetes cluster running (minikube, Docker Desktop, or any other K8s cluster)
- kubectl configured and connected to your cluster
- Docker installed (to build the image)

## Step 1: Build the Polymarket Client Docker Image

```bash
cd /Users/pishty/ws/polymarket/app/source
docker build -t polymarket-client:latest .
```

If using minikube, load the image into minikube:
```bash
minikube image load polymarket-client:latest
```

## Step 2: Deploy Kafka Infrastructure

Deploy Kafka (using KRaft mode - no Zookeeper needed) to your cluster:

```bash
cd /Users/pishty/ws/polymarket/app
kubectl apply -f k8s/kafka.yaml
```

Wait for Kafka to be ready:
```bash
kubectl wait --for=condition=ready pod -l app=kafka --timeout=120s
```

## Step 3: Deploy Configuration and Polymarket Client

```bash
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/deployment.yaml
```

## Step 4: Verify Deployment

Check that all pods are running:

```bash
kubectl get pods
```

You should see:
- `kafka-0` - Running (StatefulSet using KRaft mode)
- `polymarket-client-*` - Running

Check the Polymarket client logs:

```bash
kubectl logs -f deployment/polymarket-client
```

You should see:
- `WebSocket connection opened`
- `Kafka producer initialized with bootstrap servers: kafka-service:9092`
- `Subscribed to comments:comment_created`
- `Subscribed to comments:reaction_created`
- `Received message: {...}`
- `Message sent to Kafka topic 'polymarket-messages' with key '...'`

## Step 5: Test Kafka Consumer

Deploy the test consumer pod:

```bash
kubectl apply -f k8s/kafka-consumer-test.yaml
```

The pod will ensure the `polymarket-messages` topic exists before attaching the console consumer. Check the logs to confirm it created (or found) the topic.

View the messages being consumed:

```bash
kubectl logs -f kafka-consumer-test
```

This will show all messages being pushed to the `polymarket-messages` topic in real-time.

## Cleanup

Remove all resources when done:

```bash
kubectl delete -f k8s/deployment.yaml
kubectl delete -f k8s/config.yaml
kubectl delete -f k8s/kafka.yaml
kubectl delete -f k8s/kafka-consumer-test.yaml
```

## Verifying the Integration

Once everything is running, you should see:

1. **In the Polymarket client logs:**
   - `WebSocket connection opened`
   - `Kafka producer initialized with bootstrap servers: ...`
   - `Subscribed to comments:comment_created`
   - `Subscribed to comments:reaction_created`
   - `Received message: {...}`
   - `Message sent to Kafka topic 'polymarket-messages' with key '...'`

2. **In the test consumer:**
   - Messages appearing in JSON format
   - Each message showing the Polymarket event data

## Troubleshooting

### Kafka connection refused
- Make sure Kafka is running: `kubectl get pods`
- Check that the bootstrap servers address is correct

### No messages appearing
- Verify the Polymarket client is connected: `kubectl logs deployment/polymarket-client`
- Check if there are actual events happening on Polymarket (comments/reactions)
- The WebSocket might have connection issues - check for error logs

### Import errors
- For the client, rebuild the Docker image: `docker build -t polymarket-client:latest source`

## Useful Kubernetes Commands

```bash
# View all pods
kubectl get pods

# View all services
kubectl get services

# View logs for Polymarket client
kubectl logs -f deployment/polymarket-client

# View logs for Kafka
kubectl logs -f kafka-0

# View logs for consumer test
kubectl logs -f kafka-consumer-test

# Restart the Polymarket client
kubectl rollout restart deployment/polymarket-client

# Execute a shell in the Kafka pod
kubectl exec -it kafka-0 -- bash

# List Kafka topics (from inside cluster)
kubectl exec kafka-0 -- kafka-topics --list --bootstrap-server localhost:9092

# Describe the polymarket-messages topic
kubectl exec kafka-0 -- kafka-topics --describe --topic polymarket-messages --bootstrap-server localhost:9092

# Manually consume messages from Kafka
kubectl exec -it kafka-0 -- kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic polymarket-messages \
  --from-beginning

# Check pod status and events
kubectl describe pod <pod-name>

# View all resources
kubectl get all
```
