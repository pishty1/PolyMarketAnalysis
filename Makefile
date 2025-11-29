setup:
	./infrastructure/scripts/bootstrap_cluster.sh

build:
	./infrastructure/scripts/build_images.sh

deploy:
	kubectl apply -f infrastructure/k8s/client-config.yaml
	kubectl apply -f infrastructure/k8s/client-deployment.yaml
	kubectl apply -f infrastructure/k8s/flink-operator/
	kubectl apply -f infrastructure/k8s/pod-monitor.yaml

port-forward:
	# Helpers to access Grafana/Flink UI locally
	kubectl port-forward svc/poly-example-rest 8081:8081
