# Polymarket Analytics Pipeline - Migration Plan

## Phase 1: Structural Refactoring (Monorepo)
- [ ] **Directory Structure**:
    - [ ] Create `apps/ingestion-producer`
    - [ ] Create `apps/flink-analytics`
    - [ ] Create `infrastructure/helm`
    - [ ] Create `infrastructure/k8s`
    - [ ] Create `infrastructure/scripts`
- [ ] **Migration**:
    - [ ] Move `client/` contents to `apps/ingestion-producer/`
    - [ ] Move `flink/` contents to `apps/flink-analytics/`
    - [ ] Move `k8s/` contents to `infrastructure/k8s/`
- [ ] **Root Files**:
    - [ ] Create root `Makefile`
    - [ ] Update root `README.md`

## Phase 2: Infrastructure Modernization
- [ ] **Kafka**:
    - [ ] Create `infrastructure/helm/kafka-values.yaml` (Bitnami chart overrides).
    - [ ] Remove `infrastructure/k8s/kafka.yaml` (manual manifest).
- [ ] **PostgreSQL**:
    - [ ] Create `infrastructure/k8s/postgres-init/init.sql` for `market_stats` table.
    - [ ] Create `infrastructure/helm/postgres-values.yaml` or `infrastructure/k8s/postgres.yaml`.
- [ ] **Flink Operator**:
    - [ ] Ensure `infrastructure/k8s/flink-operator/flink-configuration.yaml` exists.
    - [ ] Ensure `infrastructure/k8s/flink-operator/flink-job-deployment.yaml` exists.

## Phase 3: Application Logic (Flink & Postgres Sink)
- [ ] **Dependencies (`apps/flink-analytics/pom.xml`)**:
    - [ ] Add `org.postgresql:postgresql` driver.
    - [ ] Add `org.apache.flink:flink-connector-jdbc` dependency.
    - [ ] Ensure `maven-shade-plugin` is configured correctly for a Fat Jar.
- [ ] **Code (`Polymarket.java`)**:
    - [ ] Import `JdbcSink`, `JdbcExecutionOptions`, `JdbcConnectionOptions`.
    - [ ] Define SQL Insert statement: `INSERT INTO market_stats ...`.
    - [ ] Replace `.print()` with `.addSink(JdbcSink.sink(...))`.
    - [ ] Configure JDBC connection string (`jdbc:postgresql://postgres-service:5432/polymarket`).

## Phase 4: Developer Experience (Automation)
- [ ] **Scripts**:
    - [ ] Create `infrastructure/scripts/bootstrap_cluster.sh` (Minikube start, Helm installs).
    - [ ] Create `infrastructure/scripts/build_images.sh` (Docker build for Producer & Flink).
    - [ ] Create `infrastructure/scripts/teardown.sh`.
- [ ] **Makefile Targets**:
    - [ ] `setup`: Run bootstrap script.
    - [ ] `build`: Run build images script.
    - [ ] `deploy`: Apply K8s manifests (Flink Operator, Producer, Postgres).
    - [ ] `port-forward`: Helper for Flink UI (8081) and Grafana.

## Phase 5: Observability (Grafana & Prometheus)
- [ ] **Prometheus**:
    - [ ] Create `infrastructure/helm/prometheus-values.yaml`.
    - [ ] Configure PodMonitor for Flink metrics.
- [ ] **Grafana**:
    - [ ] Create `infrastructure/k8s/grafana/datasources.yaml` (Postgres & Prometheus).
    - [ ] Create `infrastructure/k8s/grafana/dashboards/` (Top Markets, Records/sec).
