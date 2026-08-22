# Docker + Kubernetes Hands-On Interview Lab — Java Edition

This lab is designed for interview preparation using a technology stack you already know: **Java 21 + Spring Boot + Redis**.

The goal is not merely to memorize Docker and Kubernetes terminology. You will build, run, break, inspect, scale and recover a small application so the concepts become practical.

## Architecture

```text
Browser / curl
      |
      v
Spring Boot API  --->  Redis
      |                  |
      |                  +-- persistent visit counter
      +-- hostname shows which container/Pod answered
```

The API exposes:

- `GET /` — increments a Redis counter and returns environment, hostname and visit count.
- `GET /health` — simple liveness endpoint.
- `GET /ready` — returns readiness based on Redis connectivity.

This deliberately simple application lets you practice:

- Dockerfile and image layers
- containers and port publishing
- environment variables
- Docker networking and DNS
- Docker Compose
- volumes and persistence
- multi-stage Java builds
- Kubernetes Pods, Deployments and ReplicaSets
- Services and service discovery
- ConfigMaps and Secrets
- liveness/readiness probes
- resource requests and limits
- scaling and self-healing
- rolling updates and rollback
- troubleshooting `ImagePullBackOff`, `CrashLoopBackOff`, bad configuration and broken dependencies

---

# 1. Prerequisites

Recommended: Docker Desktop with Kubernetes enabled.

Verify:

```bash
docker --version
docker compose version
kubectl version --client
```

For Kubernetes:

```bash
kubectl config current-context
kubectl get nodes
```

You should see a local node in `Ready` state.

Java and Maven are useful for reading/running the app locally, but **not required to build the Docker image**, because Maven and the JDK run inside the Docker build stage.

If installed locally, verify:

```bash
java -version
mvn -version
```

---

# 2. Clone the learning branch

```bash
git clone https://github.com/gurmeetgold/saas-subscription.git
cd saas-subscription
git checkout docker-k8s-learning-lab
cd docker-k8s-lab
```

Important files:

```text
pom.xml
Dockerfile
docker-compose.yml
src/main/java/com/gurmeet/dockerk8slab/
  DockerK8sLabApplication.java
  LabController.java
src/main/resources/application.properties
k8s/all.yaml
```

---

# 3. Understand the Java app first

Open `LabController.java`.

The important idea is that application code does **not** hard-code environment-specific addresses. Redis configuration comes from environment variables:

```text
REDIS_HOST
REDIS_PORT
APP_ENV
APP_MESSAGE
```

Spring maps Redis configuration in `application.properties`:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

Interview point:

> Build the application once, then inject environment-specific configuration at runtime. The same image should move between development, test and production.

---

# 4. Optional: run Java directly

If you have Maven installed, start Redis first:

```bash
docker run -d --name lab-redis -p 6379:6379 redis:7-alpine
```

Then:

```bash
mvn spring-boot:run
```

Test:

```bash
curl http://localhost:5000/
curl http://localhost:5000/health
curl http://localhost:5000/ready
```

Clean up:

```bash
docker rm -f lab-redis
```

This proves the application is simply a Java process before Docker enters the picture.

---

# 5. Build your first Docker image

```bash
docker build -t docker-k8s-lab:local .
```

The Dockerfile uses a **multi-stage build**.

Stage 1:

```text
maven:3.9.9-eclipse-temurin-21
```

It downloads dependencies and runs Maven package.

Stage 2:

```text
eclipse-temurin:21-jre
```

Only the packaged JAR is copied into the runtime image.

Why this matters:

> Build tools such as Maven and the full JDK are useful during compilation but unnecessary at runtime. Multi-stage builds reduce image size and attack surface.

Inspect the image:

```bash
docker images docker-k8s-lab
docker history docker-k8s-lab:local
```

Interview-ready definitions:

- **Dockerfile**: instructions used to build an image.
- **Image**: immutable application package made of filesystem layers plus metadata.
- **Container**: a running instance of an image with an isolated process, filesystem view and networking.

---

# 6. Run the Java container without Redis — intentionally break it

```bash
docker run --rm -p 5000:5000 docker-k8s-lab:local
```

In another terminal:

```bash
curl http://localhost:5000/health
curl http://localhost:5000/ready
curl http://localhost:5000/
```

Expected concept:

- `/health` can succeed because the Java process is alive.
- `/ready` should fail because Redis is unavailable.
- `/` needs Redis and should return an error.

This demonstrates a crucial distinction:

> A process can be **alive** without being **ready to serve its intended workload**.

Stop with `Ctrl+C`.

---

# 7. Why `localhost` is a common container mistake

Start Redis as another container:

```bash
docker run -d --name redis redis:7-alpine
```

If the Java container uses `REDIS_HOST=localhost`, it still cannot reach that Redis container.

Why?

Inside the Java container:

```text
localhost = Java container itself
```

It does **not** mean another container and it does not mean your laptop.

This is one of the most common interview questions around container networking.

Remove Redis:

```bash
docker rm -f redis
```

---

# 8. Run the complete application with Docker Compose

```bash
docker compose up --build
```

In another terminal:

```bash
curl http://localhost:5000/
curl http://localhost:5000/
curl http://localhost:5000/
```

The visit count should increase.

Inspect:

```bash
docker compose ps
docker compose logs app
docker compose logs redis
```

Why does the app connect using `REDIS_HOST=redis`?

Docker Compose creates a network and supplies DNS-based service discovery. `redis` is the Compose **service name**.

Interview line:

> Containers should normally discover each other using stable DNS/service names rather than hard-coded container IP addresses.

---

# 9. Get inside containers

Enter the Java application container:

```bash
docker compose exec app sh
```

Try:

```bash
pwd
ls
java -version
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

Important:

> `docker exec` starts an additional process inside an existing container. It does not create another container.

---

# 10. Learn volumes and persistence

The Compose file uses the named volume `redis-data`.

```bash
docker volume ls
```

Stop/remove containers but keep the volume:

```bash
docker compose down
```

Start again:

```bash
docker compose up -d
curl http://localhost:5000/
```

The counter should continue.

Now remove the volume too:

```bash
docker compose down -v
docker compose up -d
curl http://localhost:5000/
```

The counter starts from the beginning.

Know these distinctions:

- container writable layer = disposable with the container
- named volume = Docker-managed persistent data
- bind mount = host path mounted into a container

---

# 11. Understand Docker build caching

The Dockerfile copies `pom.xml` before Java source code:

```dockerfile
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
```

Why?

Dependencies change less frequently than application source code. Docker can reuse the Maven dependency layer when only your Java code changes.

Run the build twice:

```bash
docker build -t docker-k8s-lab:local .
docker build -t docker-k8s-lab:local .
```

Watch for cached build steps.

Interview point:

> Dockerfile ordering affects cache efficiency and therefore CI build speed.

---

# 12. Image tags

```bash
docker tag docker-k8s-lab:local docker-k8s-lab:v1
docker images docker-k8s-lab
```

A tag is a friendly reference to an image. Multiple tags can reference the same underlying image.

For production, immutable version tags or image digests are safer than depending only on `latest`.

---

# 13. Container versus virtual machine

Interview-ready explanation:

> A VM virtualizes hardware and normally runs a complete guest operating system with its own kernel. Linux containers isolate application processes at the operating-system level and normally share the host kernel. Namespaces provide isolation and cgroups govern resources, so containers are generally faster to start and lighter than full VMs.

Important nuance:

> A container is not simply a lightweight VM; it is primarily an isolated process environment.

Docker Desktop on Windows/macOS uses virtualization underneath because Linux containers need a Linux kernel.

---

# 14. Move from Docker to Kubernetes

Docker gives you excellent tooling for packaging/building/running containerized applications.

Kubernetes solves orchestration problems such as:

- desired replica count
- scheduling
- self-healing
- stable service discovery
- traffic distribution
- rolling updates
- configuration injection
- health-aware routing
- resource allocation

Precise interview wording:

> Kubernetes orchestrates containerized workloads. Kubernetes nodes use a CRI-compatible runtime, commonly containerd. Kubernetes does not require Docker Engine, although OCI-compatible images built with Docker can be run by Kubernetes runtimes.

Avoid saying simply: "Kubernetes manages Docker containers."

---

# 15. Deploy to Kubernetes

Make sure the image exists:

```bash
docker images docker-k8s-lab:local
```

Apply:

```bash
kubectl apply -f k8s/all.yaml
```

Inspect:

```bash
kubectl get all -n docker-k8s-lab
kubectl get pods -n docker-k8s-lab -o wide
```

The manifest creates:

- Namespace
- ConfigMap
- Secret
- Redis Deployment
- Redis Service
- Java web Deployment with 3 replicas
- Web Service

---

# 16. Pod, Deployment, ReplicaSet and Service

**Pod**: smallest schedulable Kubernetes unit. Usually contains one primary application container.

**ReplicaSet**: maintains the requested number of matching Pods.

**Deployment**: higher-level controller that manages ReplicaSets and supports declarative updates/rollback.

**Service**: stable network identity that routes traffic to matching Pods.

Commands:

```bash
kubectl get pods -n docker-k8s-lab
kubectl get rs -n docker-k8s-lab
kubectl get deployments -n docker-k8s-lab
kubectl get services -n docker-k8s-lab
```

Interview shorthand:

> Deployment manages application lifecycle; Service gives ephemeral Pods stable reachability.

---

# 17. Access the app

The web Service is `ClusterIP`, so expose it locally using port forwarding:

```bash
kubectl port-forward service/web 8080:80 -n docker-k8s-lab
```

In another terminal:

```bash
curl http://localhost:8080/
```

Run repeatedly:

```bash
for i in {1..10}; do curl -s http://localhost:8080/; echo; done
```

Observe `hostname`. Different Pod hostnames demonstrate that the Service can route connections across replicas.

---

# 18. Kubernetes service discovery

The Java app uses:

```text
REDIS_HOST=redis
```

The Kubernetes Service named `redis` provides the stable address.

Within the namespace, DNS resolves `redis`.

Fully qualified DNS resembles:

```text
redis.docker-k8s-lab.svc.cluster.local
```

This means Pods can be replaced without application code tracking their changing IP addresses.

---

# 19. ConfigMap versus Secret

Inspect:

```bash
kubectl get configmap app-config -n docker-k8s-lab -o yaml
kubectl get secret app-secret -n docker-k8s-lab -o yaml
```

- ConfigMap = non-sensitive configuration
- Secret = sensitive configuration mechanism

Important security nuance:

> Kubernetes Secrets are base64 encoded by default; base64 is not encryption. Production security typically also relies on RBAC, encryption at rest and/or external secret managers.

---

# 20. Liveness versus readiness

Manifest:

- `/health` is the liveness probe.
- `/ready` is the readiness probe.

Think of it this way:

**Liveness:** "Should Kubernetes restart me?"

**Readiness:** "Should Kubernetes send traffic to me?"

The Java process can remain alive while Redis is unavailable. In that case it should not be considered ready for traffic.

Excellent interview line:

> Liveness protects process recovery. Readiness protects traffic quality.

---

# 21. Self-healing experiment

```bash
kubectl get pods -n docker-k8s-lab
kubectl delete pod <one-web-pod-name> -n docker-k8s-lab
kubectl get pods -n docker-k8s-lab -w
```

A replacement Pod appears.

Why?

The Deployment desired state says `replicas: 3`. Kubernetes controllers continuously reconcile actual state toward desired state.

Key phrase:

> Declarative desired state plus reconciliation loop.

---

# 22. Scale the Java application

```bash
kubectl scale deployment web --replicas=5 -n docker-k8s-lab
kubectl get pods -n docker-k8s-lab
```

Then:

```bash
kubectl scale deployment web --replicas=2 -n docker-k8s-lab
kubectl scale deployment web --replicas=3 -n docker-k8s-lab
```

Manual scaling explicitly changes desired replica count.

Horizontal Pod Autoscaler can change replica count automatically from metrics when the metrics infrastructure is available.

---

# 23. Rolling update and rollback

Edit the default `APP_MESSAGE` in `docker-compose.yml`, or edit Java code, then build another image:

```bash
docker build -t docker-k8s-lab:v2 .
```

Update:

```bash
kubectl set image deployment/web web=docker-k8s-lab:v2 -n docker-k8s-lab
kubectl rollout status deployment/web -n docker-k8s-lab
kubectl rollout history deployment/web -n docker-k8s-lab
```

Rollback:

```bash
kubectl rollout undo deployment/web -n docker-k8s-lab
```

Concept:

> A Deployment normally performs a rolling update by gradually creating Pods from a new ReplicaSet while removing Pods from the previous ReplicaSet.

---

# 24. Break the image deliberately

```bash
kubectl set image deployment/web web=docker-k8s-lab:this-tag-does-not-exist -n docker-k8s-lab
```

Troubleshoot:

```bash
kubectl get pods -n docker-k8s-lab
kubectl describe pods -n docker-k8s-lab
kubectl get events -n docker-k8s-lab --sort-by=.lastTimestamp
```

You may see `ErrImagePull` or `ImagePullBackOff`.

Recover:

```bash
kubectl rollout undo deployment/web -n docker-k8s-lab
```

Strong troubleshooting order:

1. `kubectl get`
2. `kubectl describe`
3. `kubectl logs`
4. `kubectl get events`
5. inspect image/configuration/dependencies/networking

---

# 25. Break Redis deliberately

```bash
kubectl scale deployment redis --replicas=0 -n docker-k8s-lab
```

Now inspect:

```bash
curl http://localhost:8080/ready
kubectl get pods -n docker-k8s-lab
kubectl describe pods -n docker-k8s-lab
kubectl logs deployment/web -n docker-k8s-lab
```

Restore:

```bash
kubectl scale deployment redis --replicas=1 -n docker-k8s-lab
```

This gives you a realistic answer to:

> "The Pod is running, but the application isn't serving traffic. How do you troubleshoot?"

Do not stop at Pod status. Check readiness, logs, dependent Services, endpoints, DNS and configuration.

---

# 26. Useful commands to know by hand

Docker:

```bash
docker build -t NAME:TAG .
docker images
docker run -p HOST:CONTAINER IMAGE
docker ps
docker logs CONTAINER
docker exec -it CONTAINER sh
docker inspect CONTAINER
docker network ls
docker volume ls
docker compose up -d --build
docker compose ps
docker compose logs -f
docker compose down
docker compose down -v
```

Kubernetes:

```bash
kubectl get pods
kubectl get deployments
kubectl get services
kubectl describe pod POD
kubectl logs POD
kubectl logs deployment/web
kubectl exec -it POD -- sh
kubectl get events --sort-by=.lastTimestamp
kubectl scale deployment web --replicas=5
kubectl set image deployment/web web=IMAGE:TAG
kubectl rollout status deployment/web
kubectl rollout history deployment/web
kubectl rollout undo deployment/web
kubectl port-forward service/web 8080:80
```

---

# 27. Interview questions you should be able to answer after the lab

1. What is the difference between an image and a container?
2. What does a Dockerfile do?
3. Why use a multi-stage Docker build for Java?
4. What is the difference between `EXPOSE` and `-p`?
5. Why doesn't `localhost` reach another container?
6. How does Docker Compose service discovery work?
7. Why use a volume?
8. What happens to data in a container writable layer when the container is deleted?
9. Why does Dockerfile instruction order matter?
10. What is an image tag? What is an image digest?
11. Container versus VM?
12. What are namespaces and cgroups conceptually?
13. Does Kubernetes require Docker Engine?
14. Pod versus container?
15. Deployment versus Pod?
16. What does a ReplicaSet do?
17. Why do we need a Kubernetes Service?
18. ClusterIP versus NodePort versus LoadBalancer?
19. How does Kubernetes service discovery work?
20. ConfigMap versus Secret?
21. Liveness versus readiness?
22. What do resource requests and limits mean?
23. What happens when a Pod dies under a Deployment?
24. How does Kubernetes scaling work?
25. How does a rolling update work?
26. How do you roll back a failed release?
27. What causes `ImagePullBackOff`?
28. What causes `CrashLoopBackOff`?
29. A Pod is Running but unavailable to users. What do you inspect?
30. Docker Compose versus Kubernetes?

---

# 28. Five interview traps

## Trap 1: "Kubernetes manages Docker containers"

Better:

> Kubernetes orchestrates containerized workloads through a CRI-compatible runtime. OCI images created with Docker are compatible with runtimes such as containerd.

## Trap 2: "A container has its own operating system"

Better:

> Linux containers normally share the host kernel while receiving isolated process/network/filesystem views.

## Trap 3: "A Kubernetes Service is a load balancer"

More precise:

> A Service provides stable service discovery and virtual networking to a changing set of selected Pods. `LoadBalancer` is one Service type used to request external exposure from supporting infrastructure.

## Trap 4: "If a Pod says Running, the application is healthy"

Wrong. `Running` describes lifecycle state. Readiness determines whether it should receive Service traffic.

## Trap 5: "Secrets are encrypted because they are base64"

Wrong. Base64 is encoding, not encryption.

---

# 29. Cleanup

Docker Compose:

```bash
docker compose down -v
```

Kubernetes:

```bash
kubectl delete namespace docker-k8s-lab
```

Optional images:

```bash
docker image rm docker-k8s-lab:local docker-k8s-lab:v1 docker-k8s-lab:v2
```

---

# 30. The story you should eventually be able to tell Docker

> I built a small Spring Boot service backed by Redis and containerized it with a multi-stage Dockerfile. I ran the application locally with Docker Compose, used service-name DNS for container networking and a named volume for persistence. I then deployed the same application to Kubernetes using Deployments, Services, ConfigMaps, Secrets, liveness/readiness probes and resource controls. I scaled the workload, deleted Pods to observe reconciliation, performed a rolling update and rollback, and deliberately broke image and dependency scenarios so I could practice troubleshooting with get, describe, logs and events.

The important part is that you should only use this answer after doing the exercises yourself. The objective is genuine hands-on confidence, not memorization.
