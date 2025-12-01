setup:
	./infrastructure/scripts/bootstrap_cluster.sh

build:
	./infrastructure/scripts/build_images.sh

deploy:
	kubectl apply -f infrastructure/k8s/client-config.yaml
	kubectl apply -f infrastructure/k8s/client-deployment.yaml
	kubectl apply -f infrastructure/k8s/flink-operator/
	kubectl apply -f infrastructure/k8s/pod-monitor.yaml
	kubectl apply -f infrastructure/k8s/grafana/dashboard-configmap.yaml