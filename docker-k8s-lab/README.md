# Docker + Kubernetes Hands-On Interview Lab

This lab is designed to make the core Docker and Kubernetes concepts *muscle memory* before a technical interview.

You will build and run a tiny SaaS-style web API. The app returns its hostname and increments a visit counter stored in Redis. That simple design lets you practice images, containers, networking, environment variables, volumes, health checks, Compose, Kubernetes Pods, Deployments, Services, ConfigMaps, Secrets, probes, scaling, rollouts, logs, exec, service discovery and failure recovery.

## Architecture

```text
Browser / curl
    |
    v
Web API (Flask/Gunicorn)  --->  Redis
    |                           |
    |                           +-- visit counter
    +-- hostname shows which replica answered
```

In Docker Compose, `app` and `redis` run as separate containers on the Compose network.

In Kubernetes, a `web` Deployment runs 3 Pods. A ClusterIP Service gives them one stable virtual endpoint. Redis runs in another Deployment and is reached through the Kubernetes Service named `redis`.

---

# 1. Prerequisites

## Recommended easiest setup: Docker Desktop

Install Docker Desktop and make sure these commands work:

```bash
docker --version
docker compose version
kubectl version --client
```

For the Kubernetes section, enable Kubernetes in Docker Desktop:

1. Open Docker Desktop.
2. Open Settings.
3. Open Kubernetes.
4. Enable Kubernetes.
5. Wait until Kubernetes reports Running.

Then verify:

```bash
kubectl config current-context
kubectl get nodes
```

You should see a local node in `Ready` state.

> Alternative: `kind` or `minikube` also work. Docker Desktop is easiest for this interview lab because the Docker image you build locally can be used directly by its local Kubernetes cluster.

---

# 2. Clone the branch

```bash
git clone https://github.com/gurmeetgold/saas-subscription.git
cd saas-subscription
git checkout docker-k8s-learning-lab
cd docker-k8s-lab
```

Check the files:

```bash
ls
```

You should see:

```text
app.py
requirements.txt
Dockerfile
docker-compose.yml
k8s/
README.md
```

---

# 3. Understand the application before containerizing it

The Flask application has three endpoints:

- `/` - increments a Redis counter and returns application metadata.
- `/health` - basic liveness endpoint.
- `/ready` - readiness endpoint that verifies Redis connectivity.

Important environment variables:

- `REDIS_HOST`
- `REDIS_PORT`
- `APP_ENV`
- `APP_MESSAGE`

The app deliberately gets configuration from environment variables because containers should be portable. The image stays the same while environment-specific configuration changes outside the image.

Interview concept:

> **Image = immutable application package. Configuration = injected at runtime.**

---

# 4. Build your first Docker image

From `docker-k8s-lab`:

```bash
docker build -t docker-k8s-lab:local .
```

What Docker does:

1. Reads the `Dockerfile`.
2. Starts from `python:3.12-slim`.
3. Creates `/app` as the working directory.
4. Copies `requirements.txt`.
5. Installs Python dependencies into an image layer.
6. Copies `app.py`.
7. Records port 5000 as documentation with `EXPOSE`.
8. Adds a container health check.
9. Defines Gunicorn as the default process.

Inspect it:

```bash
docker images docker-k8s-lab
```

Useful interview explanation:

> A Docker image is a read-only template composed of filesystem layers plus metadata. A container is a running instance of that image with a writable container layer, process isolation, networking and resource controls.

---

# 5. Run one container without Redis

Try this deliberately incomplete setup:

```bash
docker run --rm -p 5000:5000 docker-k8s-lab:local
```

In another terminal:

```bash
curl http://localhost:5000/
```

The app should respond, but Redis will report an error because no Redis service exists.

This is useful. It proves:

- The web container itself is healthy.
- The application has an external dependency.
- Containers should normally communicate through container networking rather than `localhost`.

Why not `localhost` for Redis?

Inside a container, `localhost` means **that same container**, not your laptop and not another container.

Stop with `Ctrl+C`.

---

# 6. Run a multi-container application with Docker Compose

Now use Compose:

```bash
docker compose up --build
```

Open another terminal:

```bash
curl http://localhost:5000/
```

Call it several times:

```bash
curl http://localhost:5000/
curl http://localhost:5000/
curl http://localhost:5000/
```

The `visits` value should increase.

## What Compose created

```bash
docker compose ps
```

You should see two services:

- `app`
- `redis`

Docker Compose creates a private network. The app connects to Redis using hostname `redis` because Compose provides DNS-based service discovery using the service name.

Inspect networks:

```bash
docker network ls
docker compose exec app getent hosts redis
```

Interview concept:

> Containers communicate by network and DNS name. Avoid hard-coded IP addresses because container IPs are ephemeral.

---

# 7. Learn container logs and exec

View logs:

```bash
docker compose logs app
docker compose logs redis
docker compose logs -f app
```

Enter the running app container:

```bash
docker compose exec app sh
```

Inside the container:

```bash
pwd
ls
python --version
env | sort
exit
```

Enter Redis:

```bash
docker compose exec redis redis-cli
```

Then:

```text
PING
GET visits
EXIT
```

Interview concept:

> `docker exec` does not start another container. It launches another process inside an already-running container.

---

# 8. Learn volumes and persistence

The Compose file uses a named volume called `redis-data`.

List volumes:

```bash
docker volume ls
```

Stop containers but keep the volume:

```bash
docker compose down
```

Start again:

```bash
docker compose up -d
curl http://localhost:5000/
```

The visit count should continue because the Redis data survived container deletion.

Now remove the volume too:

```bash
docker compose down -v
```

Start again:

```bash
docker compose up -d
curl http://localhost:5000/
```

The counter starts over.

Interview distinction:

- Container filesystem: disposable.
- Named volume: lifecycle independent from the container.
- Bind mount: maps a specific host path into a container.

---

# 9. Learn image layers and build cache

Run:

```bash
docker history docker-k8s-lab:local
```

Notice `requirements.txt` is copied before `app.py`.

Why?

Docker can reuse the expensive dependency-install layer when only application source code changes.

Try changing only `APP_MESSAGE` in the Compose file and rebuild. Dependency installation should remain cached.

Interview concept:

> Order Dockerfile steps from least frequently changed to most frequently changed when practical so the build cache stays effective.

---

# 10. Learn tags

Tag the same image:

```bash
docker tag docker-k8s-lab:local docker-k8s-lab:v1
```

Inspect:

```bash
docker images docker-k8s-lab
```

Both tags can reference the same image ID.

Interview concept:

> A tag is a human-friendly pointer to an image manifest. It is not the image itself.

For production, prefer immutable version tags or digests rather than relying only on `latest`.

---

# 11. Container vs virtual machine - interview-ready explanation

A VM virtualizes hardware and typically runs a complete guest operating system with its own kernel.

A container virtualizes at the operating-system level. Linux containers normally share the host kernel while isolating processes using kernel mechanisms such as namespaces and controlling resources using cgroups.

That is why containers generally start faster and consume fewer resources than full VMs.

Important nuance:

> Containers are isolated processes, not tiny virtual machines.

Docker Desktop itself uses virtualization on macOS and Windows because Linux containers require a Linux kernel.

---

# 12. Move from Docker to Kubernetes

Docker solves packaging and local container execution very well.

Kubernetes solves orchestration problems such as:

- desired replica count
- automatic restart/rescheduling
- service discovery
- load distribution across Pods
- rolling updates
- configuration injection
- health-based traffic decisions
- scheduling across nodes
- declarative desired state

Kubernetes does **not** "manage Docker images". More precisely:

> Kubernetes schedules and manages containerized workloads. Nodes run containers through a CRI-compatible container runtime such as containerd. Docker images use OCI-compatible image formats that Kubernetes runtimes can pull and run.

This distinction is worth knowing in a Docker interview.

---

# 13. Deploy the application to Kubernetes

First make sure your local image exists:

```bash
docker images docker-k8s-lab:local
```

Apply the manifests:

```bash
kubectl apply -f k8s/all.yaml
```

Watch objects appear:

```bash
kubectl get all -n docker-k8s-lab
```

Watch Pods:

```bash
kubectl get pods -n docker-k8s-lab -w
```

Press `Ctrl+C` when everything is Running/Ready.

---

# 14. Understand Pods, Deployments and Services

## Pod

A Pod is Kubernetes' smallest schedulable unit. One Pod can contain one or more tightly coupled containers sharing networking and certain storage.

```bash
kubectl get pods -n docker-k8s-lab -o wide
```

## Deployment

A Deployment describes desired state for stateless application Pods and manages ReplicaSets and rolling updates.

```bash
kubectl get deployments -n docker-k8s-lab
kubectl describe deployment web -n docker-k8s-lab
```

## Service

Pod IP addresses change. A Kubernetes Service gives a stable virtual IP and DNS name and forwards traffic to matching Pods selected by labels.

```bash
kubectl get services -n docker-k8s-lab
kubectl describe service web -n docker-k8s-lab
```

Interview shorthand:

> Deployment manages Pods. Service gives Pods a stable network identity.

---

# 15. Access a ClusterIP Service using port-forward

The `web` Service is intentionally `ClusterIP`, meaning it is reachable inside the cluster but not directly exposed outside.

Forward a local port:

```bash
kubectl port-forward service/web 8080:80 -n docker-k8s-lab
```

From another terminal:

```bash
curl http://localhost:8080/
```

Run it many times:

```bash
for i in {1..10}; do curl -s http://localhost:8080/; echo; done
```

Look at the `hostname` field. You should see different Pod hostnames over repeated requests because the Service distributes connections among the three ready web Pods.

---

# 16. Kubernetes service discovery

The application connects to Redis using:

```text
REDIS_HOST=redis
```

Inside the namespace, Kubernetes DNS resolves `redis` to the Redis Service.

Try:

```bash
kubectl exec -it deployment/web -n docker-k8s-lab -- sh
```

Inside:

```bash
getent hosts redis
exit
```

Fully qualified service DNS is conceptually:

```text
redis.docker-k8s-lab.svc.cluster.local
```

---

# 17. ConfigMaps and Secrets

Inspect configuration:

```bash
kubectl get configmap app-config -n docker-k8s-lab -o yaml
kubectl get secret app-secret -n docker-k8s-lab -o yaml
```

A ConfigMap is for non-sensitive configuration.

A Secret is for sensitive data, but important interview nuance:

> Kubernetes Secrets are base64-encoded by default, not automatically encrypted merely because they are called Secrets. Production security may require encryption at rest, strong RBAC and external secret-management solutions.

Check environment variables in one web Pod:

```bash
kubectl exec deployment/web -n docker-k8s-lab -- env | grep -E 'APP_|REDIS_'
```

---

# 18. Liveness vs readiness probes

The app exposes:

- `/health` for liveness
- `/ready` for readiness

Concept:

- **Liveness** asks: should Kubernetes restart this container?
- **Readiness** asks: should this Pod receive traffic right now?

A container can be alive but temporarily not ready.

Example: if Redis is unavailable, `/ready` returns HTTP 503. The web process is still alive, but it cannot fully serve the intended workload.

Interview line:

> Liveness protects process health; readiness protects traffic quality.

---

# 19. Self-healing experiment

List Pods:

```bash
kubectl get pods -n docker-k8s-lab
```

Delete one web Pod:

```bash
kubectl delete pod <WEB_POD_NAME> -n docker-k8s-lab
```

Immediately watch:

```bash
kubectl get pods -n docker-k8s-lab -w
```

A replacement appears automatically.

Why?

You did not tell Kubernetes "keep this Pod alive". You told the Deployment:

```text
replicas: 3
```

The Deployment controller continuously reconciles actual state toward desired state.

This is a foundational Kubernetes idea:

> **Declarative desired state + reconciliation loop.**

---

# 20. Scale the application

Scale from 3 Pods to 5:

```bash
kubectl scale deployment web --replicas=5 -n docker-k8s-lab
kubectl get pods -n docker-k8s-lab
```

Scale down to 2:

```bash
kubectl scale deployment web --replicas=2 -n docker-k8s-lab
```

Return to 3:

```bash
kubectl scale deployment web --replicas=3 -n docker-k8s-lab
```

Important:

Manual scaling changes replica count. Horizontal Pod Autoscaler can adjust replicas automatically based on observed metrics, but it requires the metrics pipeline to be available.

---

# 21. Rolling update

Create a second image:

Edit `app.py` or change the default message, then:

```bash
docker build -t docker-k8s-lab:v2 .
```

Update Kubernetes:

```bash
kubectl set image deployment/web web=docker-k8s-lab:v2 -n docker-k8s-lab
kubectl rollout status deployment/web -n docker-k8s-lab
```

Watch ReplicaSets:

```bash
kubectl get rs -n docker-k8s-lab
```

Inspect history:

```bash
kubectl rollout history deployment/web -n docker-k8s-lab
```

Rollback:

```bash
kubectl rollout undo deployment/web -n docker-k8s-lab
```

Interview concept:

> A Deployment performs rolling updates by gradually replacing old ReplicaSet Pods with new ones while respecting availability constraints.

---

# 22. Simulate a broken deployment

Set a nonexistent image:

```bash
kubectl set image deployment/web web=docker-k8s-lab:this-tag-does-not-exist -n docker-k8s-lab
```

Inspect:

```bash
kubectl get pods -n docker-k8s-lab
kubectl describe pods -n docker-k8s-lab
kubectl get events -n docker-k8s-lab --sort-by=.lastTimestamp
```

You will likely see `ImagePullBackOff`/`ErrImagePull` behavior depending on your environment.

Recover:

```bash
kubectl rollout undo deployment/web -n docker-k8s-lab
```

This exercise teaches a strong troubleshooting flow:

1. `kubectl get`
2. `kubectl describe`
3. `kubectl logs`
4. `kubectl events`
5. inspect configuration, image and dependencies

---

# 23. Break the Redis dependency intentionally

Scale Redis to zero:

```bash
kubectl scale deployment redis --replicas=0 -n docker-k8s-lab
```

Check web readiness:

```bash
kubectl get pods -n docker-k8s-lab
```

The web Pods may become NotReady because `/ready` cannot reach Redis.

Restore Redis:

```bash
kubectl scale deployment redis --replicas=1 -n docker-k8s-lab
```

Observe readiness recover.

This is a very useful interview scenario because it distinguishes:

- application process health
- dependency health
- readiness to receive traffic

---

# 24. Logs in Kubernetes

Show logs for a Deployment:

```bash
kubectl logs deployment/web -n docker-k8s-lab
```

Follow logs:

```bash
kubectl logs -f deployment/web -n docker-k8s-lab
```

For a specific Pod:

```bash
kubectl logs <POD_NAME> -n docker-k8s-lab
```

If a Pod has multiple containers:

```bash
kubectl logs <POD_NAME> -c <CONTAINER_NAME> -n docker-k8s-lab
```

Interview point:

> In production, Pods are ephemeral, so centralized logging is generally required instead of relying on local container log files.

---

# 25. `kubectl exec` and troubleshooting

```bash
kubectl exec -it deployment/web -n docker-k8s-lab -- sh
```

Inside:

```bash
hostname
env | sort
getent hosts redis
python -c "import socket; print(socket.gethostbyname('redis'))"
exit
```

Use `exec` carefully in production. It is useful for diagnosis, but good systems should be observable without routinely SSH-ing or shelling into workloads.

---

# 26. Labels and selectors

Run:

```bash
kubectl get pods -n docker-k8s-lab --show-labels
kubectl get pods -n docker-k8s-lab -l app=web
```

The Service's selector is:

```yaml
selector:
  app: web
```

That is how Kubernetes determines which Pods are endpoints for the Service.

Interview concept:

> Kubernetes resources are loosely coupled using labels and selectors.

---

# 27. Requests and limits

The manifest contains CPU and memory requests/limits.

Concept:

- `requests` influence scheduling; they describe resources the scheduler should reserve for placement decisions.
- `limits` cap resource consumption where enforceable.

Example values:

```yaml
resources:
  requests:
    cpu: "50m"
    memory: "64Mi"
  limits:
    cpu: "300m"
    memory: "128Mi"
```

`50m` CPU means 50 millicores, or 0.05 CPU.

---

# 28. Compose vs Kubernetes

Docker Compose is excellent for local multi-container development and simple single-host environments.

Kubernetes is designed for orchestrating workloads across a cluster with controllers, declarative state, service discovery, rolling updates, scheduling and self-healing.

Good interview answer:

> Compose describes how my local application services run together. Kubernetes describes desired application state in a cluster and continuously reconciles that state.

---

# 29. Docker networking questions you should be able to answer

## What happens with `-p 5000:5000`?

Host port 5000 is published to container port 5000.

## Does `EXPOSE 5000` publish the port?

No. `EXPOSE` is image metadata/documentation. Publishing happens using `-p`, Compose ports, or an orchestration networking mechanism.

## Why can `app` call `redis:6379` in Compose?

Compose creates a network and DNS entries for service names.

## Why not use a container IP?

Container IPs are dynamic implementation details. Use service names/DNS.

---

# 30. Docker image questions you should be able to answer

## Image vs container

Image = immutable package/template.
Container = running instance of the image.

## Dockerfile vs image

Dockerfile = instructions/source recipe.
Image = build output.

## Registry

A registry stores and distributes container images. Docker Hub is a registry service.

Typical flow:

```text
Dockerfile -> docker build -> image -> docker push -> registry -> docker pull -> container runtime
```

## What is a layer?

Filesystem changes are stored as content-addressed immutable layers. Layers can be reused between images and cached during builds.

---

# 31. Kubernetes questions you should be able to answer

## Container vs Pod

Container = isolated running application process.
Pod = Kubernetes scheduling unit containing one or more containers.

## Pod vs Deployment

Pod = workload instance.
Deployment = controller that manages replicated stateless Pods and rolling updates.

## Deployment vs StatefulSet

Deployment is typically used for stateless interchangeable replicas.
StatefulSet is intended for workloads needing stable identity, ordered behavior and/or stable persistent storage relationships.

## Service types

- `ClusterIP`: internal cluster endpoint.
- `NodePort`: exposes a port on each node.
- `LoadBalancer`: asks the environment/cloud provider to provision an external load balancer.
- `ExternalName`: DNS alias to an external name.

## Ingress

Ingress is an HTTP/HTTPS routing API. An Ingress Controller actually implements the routing.

Modern Kubernetes also has Gateway API, designed as a more expressive successor for many traffic-management use cases.

## ConfigMap vs Secret

ConfigMap = non-sensitive configuration.
Secret = sensitive configuration object; still requires correct security controls.

## Namespace

Logical scope used to organize and isolate Kubernetes resources. It is not automatically a complete security boundary.

---

# 32. Interview troubleshooting scenarios

Practice answering these out loud.

## Scenario A: Pod is Pending

Check:

```bash
kubectl describe pod <pod> -n docker-k8s-lab
kubectl get events -n docker-k8s-lab --sort-by=.lastTimestamp
```

Possible causes:

- insufficient CPU/memory
- node selector/affinity mismatch
- unbound PersistentVolumeClaim
- taints not tolerated

## Scenario B: `CrashLoopBackOff`

Check:

```bash
kubectl logs <pod> -n docker-k8s-lab
kubectl logs <pod> --previous -n docker-k8s-lab
kubectl describe pod <pod> -n docker-k8s-lab
```

Likely causes include application crash, bad configuration, missing dependency, incorrect command or failing liveness probe.

## Scenario C: `ImagePullBackOff`

Check image name/tag, registry access, imagePullSecrets, network and registry availability.

## Scenario D: Pod Running but application unavailable

Check:

- readiness state
- Service selector
- Service targetPort
- application listening interface (`0.0.0.0` vs `127.0.0.1`)
- NetworkPolicy if present
- DNS/dependencies

## Scenario E: Service has no endpoints

```bash
kubectl get endpoints web -n docker-k8s-lab
kubectl get pods --show-labels -n docker-k8s-lab
kubectl describe service web -n docker-k8s-lab
```

Most common conceptual issue: Service selector does not match Pod labels or Pods are not ready.

---

# 33. Security concepts worth knowing for a Docker interview

You do not need to become a security engineer in four days, but know these principles:

1. Use minimal trusted base images.
2. Pin versions/digests where appropriate.
3. Scan images for vulnerabilities.
4. Do not bake secrets into Dockerfiles or images.
5. Prefer non-root containers where possible.
6. Drop unnecessary Linux capabilities.
7. Use read-only filesystems where appropriate.
8. Set CPU/memory controls.
9. Sign/verify image provenance where the organization requires it.
10. Keep dependencies and base images patched.
11. Separate build-time and runtime artifacts using multi-stage builds when useful.
12. Apply least-privilege RBAC and network controls in Kubernetes.

Docker-specific product areas worth recognizing for your interview include Docker Desktop, Docker Hub, Docker Build/BuildKit, Docker Compose, Docker Scout and Docker Hardened Images.

---

# 34. Optional exercise: improve the Dockerfile

The current Dockerfile is intentionally understandable rather than maximally hardened.

Try improving it by:

- creating a non-root user
- pinning the base image by digest
- adding labels
- using a multi-stage build if you add build-time dependencies
- scanning the built image

Then be ready to explain the tradeoff between simplicity, reproducibility, build speed, size and security.

---

# 35. Clean up

Docker Compose:

```bash
docker compose down -v
```

Kubernetes:

```bash
kubectl delete namespace docker-k8s-lab
```

Optional remove images:

```bash
docker image rm docker-k8s-lab:local docker-k8s-lab:v1 docker-k8s-lab:v2
```

---

# 36. 25 rapid-fire interview questions

Practice these until you can answer each in 20-40 seconds.

1. What problem do containers solve?
2. Image vs container?
3. Container vs VM?
4. Dockerfile vs Docker image?
5. What is a Docker image layer?
6. Why use `.dockerignore`?
7. What is Docker build cache?
8. `COPY` vs volume?
9. `EXPOSE` vs `-p`?
10. Why should configuration be externalized?
11. How does container DNS/networking work in Compose?
12. What is a registry? What is Docker Hub?
13. What is Docker Compose useful for?
14. Why Kubernetes if Docker already runs containers?
15. What is a Pod?
16. What does a Deployment do?
17. What is a ReplicaSet?
18. What does a Service solve?
19. ClusterIP vs NodePort vs LoadBalancer?
20. Liveness vs readiness?
21. ConfigMap vs Secret?
22. What happens when a Pod dies under a Deployment?
23. How does a rolling update work?
24. How would you debug a failing Pod?
25. What does Kubernetes mean by desired state and reconciliation?

---

# 37. Five questions that can expose shallow understanding

## 1. Does Kubernetes require Docker Engine?

No. Modern Kubernetes uses the Container Runtime Interface. containerd and CRI-O are common runtimes. Docker-built OCI-compatible images can still run perfectly well.

## 2. Does a container have its own operating-system kernel?

Normally, Linux containers share the host Linux kernel. They get isolated views of system resources using kernel primitives.

## 3. Does `EXPOSE 5000` open port 5000 on the host?

No.

## 4. Is a Kubernetes Service a running proxy Pod?

Not conceptually. A Service is an API abstraction providing stable networking to selected endpoints. The implementation depends on cluster networking components such as kube-proxy or eBPF-based dataplanes.

## 5. If a Pod is Running, does that mean it should receive traffic?

No. It must also satisfy readiness criteria to be considered ready for Service traffic.

---

# 38. Your 90-second interview explanation

Use this structure rather than memorizing every word:

> Docker lets us package an application and its user-space dependencies into a portable image. When we run that image, we get a container: an isolated process environment that normally shares the host kernel, which makes containers much lighter than full virtual machines. For a multi-service application, Docker Compose makes it easy to run the services together locally and gives them service-name-based networking. Once we need orchestration across many workloads or machines, Kubernetes manages the desired state. We define Deployments, Services, configuration and health probes declaratively. Kubernetes then schedules Pods, keeps the requested replica count running, routes traffic only to ready endpoints and supports rolling updates and self-healing. In this lab I built one image, ran it with Redis using Compose, then deployed the exact application concept to Kubernetes and practiced scaling, failure recovery, probes, service discovery and rollout/rollback.

Do not deliver this like a textbook. Anchor the explanation to things you personally did in the lab.

---

# 39. Recommended practice order

## Pass 1 - Docker basics

Do sections 4 through 10.

Goal: image/container/network/volume/log/exec/build concepts feel natural.

## Pass 2 - Kubernetes basics

Do sections 13 through 18.

Goal: confidently explain Pod, Deployment, Service, ConfigMap, Secret and probes.

## Pass 3 - failure and operations

Do sections 19 through 25.

Goal: scaling, self-healing, rolling update, rollback and troubleshooting.

## Pass 4 - interview mode

Close the README.

Without notes, explain the architecture and answer the rapid-fire questions.

Then reopen the README and correct gaps.

---

# 40. Definition cheat sheet

**Dockerfile** - declarative instructions used to build an image.

**Image** - immutable packaged filesystem and metadata used to create containers.

**Container** - isolated running instance of an image.

**Registry** - remote image storage/distribution service.

**Volume** - storage whose lifecycle can be independent of a container.

**Compose** - tool/specification for defining and running multi-container applications.

**Cluster** - Kubernetes control plane plus worker-node resources.

**Node** - machine/VM participating in a Kubernetes cluster.

**Pod** - smallest Kubernetes scheduling unit.

**Deployment** - controller for replicated stateless Pods and rolling updates.

**ReplicaSet** - controller that maintains a target number of matching Pods; normally managed by a Deployment.

**Service** - stable networking abstraction over selected Pods/endpoints.

**ConfigMap** - Kubernetes non-secret configuration object.

**Secret** - Kubernetes object for sensitive configuration data.

**Namespace** - logical grouping/scope for Kubernetes objects.

**Liveness probe** - determines whether a container should be restarted.

**Readiness probe** - determines whether a Pod should receive traffic.

**Ingress** - API for routing external HTTP(S) requests to Services, implemented by an Ingress controller.

---

## Final rule for the interview

Do not say, "Kubernetes manages Docker containers."

A more technically accurate version is:

> Docker provides developer tooling for building and running containerized applications, while Kubernetes orchestrates containerized workloads at cluster scale using a CRI-compatible runtime.

That one sentence will keep you out of several common technical traps.
