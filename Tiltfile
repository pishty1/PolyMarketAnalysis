# Tiltfile

load('ext://helm_resource', 'helm_resource', 'helm_repo')
load('ext://helm_remote', 'helm_remote')

# 1. SETUP & CONFIGURATION
allow_k8s_contexts('minikube')

labels_infra = ["Infrastructure"]
labels_app = ["Application"]

# allow_k8s_contexts('minikube')

# --- Cert Manager ---
# We use v1.12.0 to avoid the "incompatible with Kubernetes v1.20.0" error
helm_remote('cert-manager',
    repo_name='jetstack',
    repo_url='https://charts.jetstack.io',
    version='v1.18.2',
    namespace='cert-manager',
    create_namespace=True,
    set=['installCRDs=true'] # Note: In v1.12, the flag is often installCRDs, but let's stick to the standard set if possible. 
    # v1.12 actually uses installCRDs=true as the standard in some docs, but crds.enabled=true is the modern standard. 
    # Let's stick to crds.enabled=true as it usually works, but if it fails we swap.
)

# --- Flink Operator ---
helm_remote('flink-kubernetes-operator',
    repo_name='flink-operator-repo',
    repo_url='https://downloads.apache.org/flink/flink-kubernetes-operator-1.13.0/',
    version='1.13.0',
    namespace='default'
)

# --- Optional: Labels ---
k8s_resource('cert-manager', labels=['Infrastructure'])
k8s_resource('flink-kubernetes-operator', labels=['Application'])


# --- Kafka ---
k8s_yaml('infrastructure/k8s/kafka.yaml')
k8s_resource('kafka', labels=labels_infra)

# --- Postgres ---
helm_repo('bitnami', 'https://charts.bitnami.com/bitnami')
helm_resource('postgres', 
              'bitnami/postgresql',
              flags=['-f', 'infrastructure/helm/postgres-values.yaml'],
              labels=labels_infra)

# --- Prometheus ---
# helm_repo('prometheus-community', 'https://prometheus-community.github.io/helm-charts')
# helm_resource('prometheus',
#               'prometheus-community/kube-prometheus-stack',
#               flags=['-f', 'infrastructure/helm/prometheus-values.yaml'],
#               labels=labels_infra)

# 3. PYTHON PRODUCER (With Live Update)

# k8s_yaml('infrastructure/k8s/client-config.yaml')
# k8s_yaml('infrastructure/k8s/client-deployment.yaml')

# docker_build(
#     'ingestion-producer',
#     context='apps/ingestion-producer',
#     dockerfile='apps/ingestion-producer/Dockerfile',
#     live_update=[
#         sync('apps/ingestion-producer', '/app'),
#     ],
#     only=['apps/ingestion-producer']
# )

# k8s_resource('polymarket-client', 
#     port_forwards=['8000'],
#     labels=labels_app,
#     resource_deps=['kafka', 'postgres'] 
# )

# # 4. FLINK ANALYTICS (Java + Maven)

# local_resource(
#     'maven-compile',
#     cmd='cd apps/flink-analytics/jobs/poly && mvn clean package -DskipTests',
#     deps=['apps/flink-analytics/jobs/poly/src', 'apps/flink-analytics/jobs/poly/pom.xml'],
#     labels=['Build']
# )

# docker_build(
#     'flink-analytics',
#     context='apps/flink-analytics/jobs/poly',
#     dockerfile='apps/flink-analytics/jobs/poly/Dockerfile',
#     only=['apps/flink-analytics/jobs/poly/Dockerfile', 'apps/flink-analytics/jobs/poly/target'],
# )

# k8s_yaml('infrastructure/k8s/flink-operator/flink-job-deployment.yaml')

# # Tell Tilt how to find the image in the FlinkDeployment CRD.
# # This link causes Tilt to AUTO-CREATE the 'poly-example' resource.
# k8s_kind('FlinkDeployment', image_json_path='{.spec.image}')

# # CONFIGURE the existing 'poly-example' resource.
# # We removed 'new_name' and 'objects'.
# k8s_resource('poly-example',
#     labels=labels_app,
#     resource_deps=['flink-kubernetes-operator', 'kafka', 'postgres', 'maven-compile']
# )

# # 5. MONITORING CONFIGS
# k8s_yaml('infrastructure/k8s/pod-monitor.yaml')
# k8s_yaml('infrastructure/k8s/grafana/dashboard-configmap.yaml')