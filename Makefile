setup:
	./infrastructure/scripts/bootstrap_cluster.sh

build:
	./infrastructure/scripts/build_images.sh

deploy:
	kubectl apply -f infrastructure/k8s/client-config.yaml
	kubectl apply -f infrastructure/k8s/client-deployment.yaml
	kubectl apply -f infrastructure/k8s/flink-operator/
	@echo "Waiting for Flink JobManager to be ready..."
	@sleep 5
	@kubectl wait --for=condition=Ready pod -l component=jobmanager -l app=poly-example --timeout=180s || echo "JobManager not ready yet, continuing..."
	kubectl apply -f infrastructure/k8s/pod-monitor.yaml
	kubectl apply -f infrastructure/k8s/grafana/dashboard-configmap.yaml