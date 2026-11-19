# Multi-instance deployment example

This reference deployment runs three API pods behind a Kubernetes `Service` and scales between 3 and 10 pods at 70% average CPU. PostgreSQL and Redis are external shared services; attempt, answer, and result records live in PostgreSQL. Authentication uses signed bearer tokens, so requests can land on any replica.

Before applying:

1. Build and publish `backend/` and `frontend/` images, then replace the example image names in `api.yaml` and `web.yaml` with immutable image tags.
2. Provision highly available PostgreSQL, Redis, backups, DNS/TLS, and a metrics-server. Size the database connection limit for the maximum pod count; the default Hikari pool is 10 connections per pod.
3. Create `exam-api-config` and `exam-api-secrets` from the actual environment values using the deployment secret manager. `config.example.yaml` contains placeholders and must not be applied unchanged.
4. Apply the manifests with your cluster deployment workflow. `api.yaml` provides readiness/liveness probes, resource requests, and horizontal pod autoscaling.
5. Run the load scenario in `../performance/README.md` against the public frontend URL and keep the resulting evidence with the deployment revision.

For a cluster with the metrics server and published images configured:

```sh
kubectl apply -f ops/kubernetes/config.yaml
kubectl apply -f ops/kubernetes/api.yaml
kubectl apply -f ops/kubernetes/web.yaml
kubectl get deployment,service,hpa
```

The YAML demonstrates a multi-replica topology. It does not prove a 1,000-attempt capacity; that requires the representative load run and a database sized for it. The actual HPA response and availability targets remain deployment evidence.
