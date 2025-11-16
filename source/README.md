# Polymarket Real-Time Data Socket (RTDS) Client

This project contains a Python client for subscribing to real-time data streams from the Polymarket RTDS via WebSockets. The application is containerized with Docker and configured for deployment with Kubernetes.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- A Kubernetes cluster (e.g., [Minikube](https://minikube.sigs.k8s.io/docs/start/), [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/), Docker Desktop, or a cloud provider's cluster)

## Quick Start (Local Development)

### Initial Setup

1. **Configure secrets (optional):**
   
   The app works without credentials (subscribes to public data). To use authenticated endpoints, update `k8s/config.yaml` with your credentials in the `Secret` section.

2. **Build the Docker image:**
   
   From the project root (`app/`):
   ```sh
   docker build -t polymarket-client:latest ./source
   ```

3. **Load image into your Kubernetes cluster:**
   
   Choose the command that matches your setup:
   
   ```sh
   # Docker Desktop Kubernetes (no action needed - uses local Docker images)
   
   # Minikube
   minikube image load polymarket-client:latest
   
   # Kind
   kind load docker-image polymarket-client:latest
   ```

4. **Deploy to Kubernetes:**
   
   ```sh
   kubectl apply -f k8s/
   ```

5. **Check status and view logs:**
   
   ```sh
   # View pods
   kubectl get pods -l app=polymarket-client
   
   # Stream logs
   kubectl logs -f deployment/polymarket-client
   ```

---

## Updating and Redeploying Code

After making changes to `source/polym.py` or other application files, follow these steps:

### 1. Rebuild the Docker image

From the project root:
```sh
docker build -t polymarket-client:latest ./source
```

### 2. Load the new image into your cluster

**Docker Desktop Kubernetes:**
```sh
# No extra step needed - it uses local Docker images
```

**Minikube:**
```sh
minikube image load polymarket-client:latest
```

**Kind:**
```sh
kind load docker-image polymarket-client:latest
```

### 3. Restart the deployment

Force Kubernetes to pick up the new image:
```sh
kubectl rollout restart deployment/polymarket-client
```

### 4. Monitor the rollout

```sh
# Watch the rollout status
kubectl rollout status deployment/polymarket-client

# View new pod logs
kubectl logs -f deployment/polymarket-client
```

**Pro tip:** Combine all steps into one command:
```sh
# For Docker Desktop / Minikube (after eval $(minikube docker-env))
docker build -t polymarket-client:latest ./source && \
kubectl rollout restart deployment/polymarket-client && \
kubectl rollout status deployment/polymarket-client

# For Minikube (standard)
docker build -t polymarket-client:latest ./source && \
minikube image load polymarket-client:latest && \
kubectl rollout restart deployment/polymarket-client && \
kubectl rollout status deployment/polymarket-client

# For Kind
docker build -t polymarket-client:latest ./source && \
kind load docker-image polymarket-client:latest && \
kubectl rollout restart deployment/polymarket-client && \
kubectl rollout status deployment/polymarket-client
```

---

## Configuration Details

### Secrets and ConfigMap

The application's configuration is managed by:
- **`k8s/config.yaml`**: Contains a `ConfigMap` (public config) and `Secret` (API credentials)

The `Secret` uses `stringData` for plaintext values (Kubernetes automatically Base64-encodes them). To add credentials:

1. Edit `k8s/config.yaml` and update the `Secret` section:
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: polymarket-client-auth
   stringData:
     POLYMARKET_API_KEY: "your-key-here"
     POLYMARKET_API_SECRET: "your-secret-here"
     POLYMARKET_PASSPHRASE: "your-passphrase-here"
   ```

2. Apply the updated config:
   ```sh
   kubectl apply -f k8s/config.yaml
   kubectl rollout restart deployment/polymarket-client
   ```

---

## Production Deployment

For production environments (GKE, EKS, AKS, etc.):

### 1. Use a container registry

Tag and push your image:
```sh
# Example with Docker Hub
docker build -t your-dockerhub-username/polymarket-client:v1.0.0 ./source
docker push your-dockerhub-username/polymarket-client:v1.0.0

# Example with Google Container Registry
docker build -t gcr.io/your-project/polymarket-client:v1.0.0 ./source
docker push gcr.io/your-project/polymarket-client:v1.0.0
```

### 2. Update deployment image reference

Edit `k8s/deployment.yaml`:
```yaml
spec:
  containers:
    - name: polymarket-client
      image: your-dockerhub-username/polymarket-client:v1.0.0
      imagePullPolicy: IfNotPresent  # or Always for :latest tags
```

### 3. Configure image pull secrets (for private registries)

```sh
kubectl create secret docker-registry regcred \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email
```

Then add to `k8s/deployment.yaml`:
```yaml
spec:
  imagePullSecrets:
    - name: regcred
  containers:
    - name: polymarket-client
      # ...
```

### 4. Best practices

- **Use versioned tags** instead of `:latest` (e.g., `:v1.0.0`, `:2024-11-16-abc123`)
- **Set resource limits** (already configured in `k8s/deployment.yaml`)
- **Use proper secret management** (e.g., Kubernetes Secrets, HashiCorp Vault, cloud provider secret managers)
- **Monitor logs** with a centralized logging solution

---

## Troubleshooting

### ImagePullBackOff / ErrImagePull

**Problem:** Pod shows `ImagePullBackOff` or `ErrImagePull` status.

**Solution:**
```sh
# Check detailed error
kubectl describe pod -l app=polymarket-client

# Ensure image is loaded into cluster (for local development)
minikube image load polymarket-client:latest  # or kind load...

# Verify imagePullPolicy in k8s/deployment.yaml is set to "IfNotPresent"
```

### Pod restarts continuously

**Problem:** Pod enters `CrashLoopBackOff`.

**Solution:**
```sh
# Check logs for errors
kubectl logs deployment/polymarket-client

# Check liveness/readiness probe failures
kubectl describe pod -l app=polymarket-client
```

### Connection issues

**Problem:** WebSocket connection fails.

**Solution:**
- Check logs: `kubectl logs -f deployment/polymarket-client`
- Verify network policies allow outbound connections
- Test WebSocket connectivity from within the pod:
  ```sh
  kubectl exec -it deployment/polymarket-client -- python -c "import websocket; print('websocket module OK')"
  ```

---

## Managing the Deployment

**View deployment status:**
```sh
kubectl get deployments
kubectl get pods -l app=polymarket-client
```

**Scale replicas:**
```sh
kubectl scale deployment/polymarket-client --replicas=3
```

**Delete the deployment:**
```sh
kubectl delete -f k8s/
```

