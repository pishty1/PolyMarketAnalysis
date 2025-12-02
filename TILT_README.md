# Tilt Development Environment

This document explains how to use the new Tilt-based development environment for the Polymarket project, which replaces the traditional Makefile workflow.

## Quick Start

Instead of the old Makefile commands:
```bash
make setup    # Started infrastructure
make build    # Built images  
make deploy   # Deployed applications
```

Now you simply run:
```bash
tilt up
```

That's it! Tilt handles everything automatically.

## What Tilt Provides

### 🚀 **Single Command Development**
- `tilt up` - Start the complete development environment
- `tilt down` - Stop and clean up all resources
- `tilt watch` - Watch for changes and rebuild automatically
- `tilt logs` - View logs from all resources
- `tilt ui` - Open the web interface for better visibility

### 🔄 **Live Reload**
- **Python Application**: Changes to `apps/ingestion-producer/*.py` automatically rebuild the container
- **Java Application**: Changes to `apps/flink-analytics/jobs/poly/src/` trigger:
  1. Maven JAR rebuild
  2. Docker image rebuild
  3. Kubernetes deployment update

### 🏗️ **Proper Dependencies**
Tilt automatically manages startup order:
1. **Infrastructure**: cert-manager → Flink Operator → Kafka → Postgres → Prometheus Stack
2. **Applications**: Wait for infrastructure, then deploy:
   - Python Ingestion Producer
   - Flink Analytics (Flink Job)

### 🎯 **Better Resource Management**
- Resource limits and requests configured per component
- Health checks and readiness probes configured
- Automatic retry and exponential backoff for failed deployments
- PodMonitor and Grafana dashboard automatically configured

## Architecture Overview

```
┌─────────────────┐
│   Tiltfile      │ ← Single configuration file
└────────┬────────┘
         │
    ┌────┴────┐
    │ Infra   │ ← cert-manager, Flink Operator, Kafka, Postgres, Prometheus
    │ Apps    │ ← Ingestion Producer, Flink Analytics Job
    └─────────┘
         │
    ┌────┴────┐
│ Minikube │ ← Local Kubernetes cluster with Docker integration
└───────────┘
```

## File Structure

The Tiltfile replaces multiple scripts and manual commands:

**Old Workflow (Makefile):**
```
make setup      → bootstrap_cluster.sh
make build      → build_images.sh  
make deploy     → kubectl apply -f infrastructure/k8s/
```

**New Workflow (Tilt):**
```
tilt up         → Everything in one go!
```

## Key Features

### 🐍 **Python Ingestion Producer**
- **Path**: `apps/ingestion-producer/`
- **Live Reload**: Yes (any `.py` file change triggers rebuild)
- **Image**: `ingestion-producer:latest`
- **Resources**: CPU 250m, Memory 256Mi
- **Config**: Environment variables from ConfigMap/Secret

### ☕ **Flink Analytics**
- **Path**: `apps/flink-analytics/jobs/poly/src/`
- **Build Chain**: Maven → JAR → Docker → Kubernetes
- **Image**: `flink-analytics:latest`
- **Resources**: JobManager: 2GB RAM + 1 CPU, TaskManager: 2GB RAM + 1 CPU
- **Dependencies**: Waits for Kafka and Postgres to be ready

### 📊 **Monitoring**
- **Prometheus**: Configured with custom PodMonitor
- **Grafana**: Dashboard automatically loaded
- **Metrics**: Flink exposed on ports 9249-9250

## Development Workflow

### 1. Start Development
```bash
tilt up
```

### 2. Monitor Progress
- Use `tilt ui` for web interface
- Watch logs: `tilt logs`
- Check status: `tilt status`

### 3. Make Changes
```bash
# Edit Python files
vim apps/ingestion-producer/polym.py
# → Tilt automatically rebuilds and redeploys

# Edit Java files
vim apps/flink-analytics/jobs/poly/src/main/java/org/apache/flink/examples/Polymarket.java  
# → Tilt runs Maven → rebuilds JAR → rebuilds Docker → redeploys
```

### 4. Debug and Iterate
- View logs: `tilt logs polymarket-client`
- View Flink UI: `tilt ui` → navigate to Flink deployment
- Check Kafka: `tilt logs kafka`

### 5. Stop Everything
```bash
tilt down
```

## Environment Configuration

Tilt automatically handles environment variables and configurations:

### Python Ingestion Producer
```yaml
env:
  KAFKA_BOOTSTRAP_SERVERS: "kafka-service:9092"
  KAFKA_TOPIC: "polymarket-messages"  
  WALLET_ADDRESS: "" (from ConfigMap)
  POLYMARKET_API_KEY: "" (from Secret)
  POLYMARKET_API_SECRET: "" (from Secret)
  POLYMARKET_PASSPHRASE: "" (from Secret)
```

### Flink Analytics
```yaml
# Connects to Kafka service
# Stores data in Postgres
# Exposes metrics on port 9249
```

## Troubleshooting

### Check Minikube Status
```bash
minikube status
minikube start --cpus 4 --memory 7000
```

### View All Resources
```bash
kubectl get all
kubectl get pods -w
```

### Check Tilt Resources
```bash
tilt status
tilt resources  # Shows all managed resources
```

### Debug Specific Component
```bash
tilt logs polymarket-client
tilt logs flink-analytics
tilt logs kafka
```

### Restart Component
```bash
tilt delete polymarket-client
tilt delete flink-analytics
```

## Performance Tips

1. **Use Tilt UI**: `tilt ui` provides better visibility than CLI
2. **Selective Watching**: Tilt only rebuilds what changed
3. **Parallel Builds**: Infrastructure and apps deploy simultaneously where possible
4. **Resource Limits**: Prevents one component from consuming all cluster resources

## Migration from Makefile

| Makefile Command | Tilt Equivalent | Status |
|------------------|-----------------|--------|
| `make setup` | `tilt up` | ✅ Replaced |
| `make build` | Automatic | ✅ Automatic |
| `make deploy` | Automatic | ✅ Automatic |
| Manual monitoring | `tilt ui` | ✅ Enhanced |
| Manual logging | `tilt logs` | ✅ Enhanced |

## Next Steps

1. **Install Tilt**: https://docs.tilt.dev/install.html
2. **Start developing**: `tilt up`
3. **Open UI**: `tilt ui` 
4. **Read docs**: https://docs.tilt.dev/

The Tiltfile is fully self-contained and includes everything needed for local development. No more remembering complex Makefile commands or deployment orders!