# Tiltfile

load('ext://helm_resource', 'helm_resource', 'helm_repo')
load('ext://helm_remote', 'helm_remote')
update_settings(k8s_upsert_timeout_secs=200)

allow_k8s_contexts('minikube')

labels_infra = ["Infrastructure"]
labels_app = ["Application"]
cert_manager_labels = ["cert-manager"] 
labels_repos = ["Repositories"]


# First, define the repo for Flink
helm_repo('flink-operator-repo', 
          'https://downloads.apache.org/flink/flink-kubernetes-operator-1.13.0/',
          labels=labels_repos)

# Use helm_resource, which respects dependencies more strictly
helm_resource('flink-kubernetes-operator',
    'flink-operator-repo/flink-kubernetes-operator',
    flags=['--version=1.13.0', '--set=webhook.create=false'], # Note: 'version' is a direct argument in helm_resource
    namespace='default',
    labels=labels_infra
)


# --- Kafka ---
k8s_yaml('infrastructure/k8s/kafka.yaml')
k8s_resource('kafka', labels=labels_infra)

# --- Postgres ---
helm_repo('bitnami', 'https://charts.bitnami.com/bitnami', labels=labels_repos)
helm_resource('postgres', 
              'bitnami/postgresql',
              flags=['-f', 'infrastructure/helm/postgres-values.yaml'],
              labels=labels_infra)


# --- Prometheus ---
helm_repo('prometheus-community', 'https://prometheus-community.github.io/helm-charts', labels=labels_repos)

helm_resource(
  'prometheus',
  'prometheus-community/kube-prometheus-stack',
  flags=['-f', 'infrastructure/helm/prometheus-values.yaml'],
  labels=labels_infra,
  pod_readiness='wait',
#   port_forwards=['3000:80'],
)

# ==========================================================
# APPLICATIONS
# ==========================================================

k8s_yaml('infrastructure/k8s/client-config.yaml')
k8s_yaml('infrastructure/k8s/client-deployment.yaml')

docker_build(
    'ingestion-producer',
    context='apps/ingestion-producer',
    dockerfile='apps/ingestion-producer/Dockerfile',
    live_update=[
        sync('apps/ingestion-producer', '/app'),
    ]
)

k8s_resource('polymarket-client', 
    port_forwards=['8000'],
    labels=labels_app,
    resource_deps=['kafka', 'postgres'] 
)

# --- Flink Jobs ---

local_resource(
    'maven-compile',
    cmd='cd apps/flink-analytics/jobs/poly && mvn clean package -DskipTests',
    deps=['apps/flink-analytics/jobs/poly/src', 'apps/flink-analytics/jobs/poly/pom.xml'],
    labels=['Build']
)

docker_build(
    'flink-analytics',
    context='apps/flink-analytics/jobs/poly',
    dockerfile='apps/flink-analytics/jobs/poly/Dockerfile',
)

k8s_yaml('infrastructure/k8s/flink-operator/flink-job-deployment.yaml')
k8s_kind('FlinkDeployment', image_json_path='{.spec.image}')
k8s_resource('poly-example',
    labels=labels_app,
    resource_deps=['flink-kubernetes-operator', 'kafka', 'postgres', 'maven-compile']
)

# local_resource(
#     'flink-ui-forward',
#     resource_deps=['poly-example']
# )

k8s_yaml('infrastructure/k8s/pod-monitor.yaml')
k8s_resource(
  new_name='poly-example-monitor',
  objects=['poly-example-monitor:PodMonitor'],
  resource_deps=['prometheus'],
  labels=labels_infra
)

k8s_yaml('infrastructure/k8s/grafana/dashboard-configmap.yaml')