# Polymarket Real-Time Data Socket (RTDS) Client

This project contains a Python client for subscribing to real-time data streams from the Polymarket RTDS via WebSockets. The application is containerized with Docker and configured for deployment with Kubernetes.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- A Kubernetes cluster (e.g., [Minikube](https://minikube.sigs.k8s.io/docs/start/), [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/), or a cloud provider's cluster)

## Kubernetes Deployment

The application is managed by Kubernetes using the manifest files located in the `k8s/` directory.

### 1. Configuration

The application's configuration is managed by a Kubernetes ConfigMap and Secret.

- **`k8s/config.yaml`**: This file contains a `ConfigMap` for non-sensitive configuration and a `Secret` for API credentials.

Before deploying, you must encode your API credentials in Base64 and add them to `k8s/config.yaml`.

**Encode your credentials:**

```sh
echo -n 'YOUR_API_KEY' | base64
echo -n 'YOUR_API_SECRET' | base64
echo -n 'YOUR_PASSPHRASE' | base64
```

Update the `data` section of the `Secret` in `k8s/config.yaml` with the resulting Base64-encoded strings.

### 2. Build and Push the Docker Image

Build the Docker image and tag it with a repository name. If you are using a local cluster like Minikube, you can build directly into its Docker daemon.

```sh
# For a local Minikube cluster
eval $(minikube docker-env)
docker build -t polymarket-client:latest .

# For a remote repository (e.g., Docker Hub, GCR)
# Replace 'your-repo' with your repository name
docker build -t your-repo/polymarket-client:latest .
docker push your-repo/polymarket-client:latest
```

**Note:** If you pushed to a remote repository, you must update the `image` name in `k8s/deployment.yaml` to match (`your-repo/polymarket-client:latest`).

### 3. Deploy to Kubernetes

Apply the Kubernetes manifests to your cluster:

```sh
# Apply the configuration and deployment
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/deployment.yaml
```

### 4. Manage the Deployment

**Check deployment status:**

```sh
kubectl get deployments
```

**View running pods:**

```sh
kubectl get pods
```

**View logs from the running pod:**

```sh
# Get the pod name first
POD_NAME=$(kubectl get pods -l app=polymarket-client -o jsonpath='{.items[0].metadata.name}')

# Stream logs
kubectl logs -f $POD_NAME
```

### 5. Clean Up

To remove the application from your cluster, delete the resources:

```sh
kubectl delete -f k8s/deployment.yaml
kubectl delete -f k8s/config.yaml
```

