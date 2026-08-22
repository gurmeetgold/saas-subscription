import os
import socket
from flask import Flask, jsonify
import redis

app = Flask(__name__)

REDIS_HOST = os.getenv("REDIS_HOST", "redis")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
APP_ENV = os.getenv("APP_ENV", "local")
APP_MESSAGE = os.getenv("APP_MESSAGE", "Hello from Docker + Kubernetes")

r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)

@app.get("/")
def home():
    try:
        visits = r.incr("visits")
        redis_status = "connected"
    except Exception as exc:
        visits = None
        redis_status = f"error: {type(exc).__name__}"

    return jsonify({
        "message": APP_MESSAGE,
        "environment": APP_ENV,
        "hostname": socket.gethostname(),
        "visits": visits,
        "redis": redis_status,
    })

@app.get("/health")
def health():
    return jsonify({"status": "ok"}), 200

@app.get("/ready")
def ready():
    try:
        r.ping()
        return jsonify({"status": "ready"}), 200
    except Exception:
        return jsonify({"status": "not-ready"}), 503

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
