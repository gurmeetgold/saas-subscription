# SaaS Subscription Billing — Temporalized

**Gurmeet Chhiber** | Staff Developer Advocate, Enterprise — Take-Home Assignment

---

## The Problem

At my previous enterprise SaaS company, subscription billing ran as three separate cron scripts sharing one database:

- **Script 1** — nightly cron that expired trials and moved customers to `ACTIVE`
- **Script 2** — monthly cron that charged all `ACTIVE` customers
- **Script 3** — web endpoint that set a `cancellationFlag` column

No coordination. No checkpoints. Painful failures every month:

| Failure | What happened |
|---|---|
| Trial cron + billing cron ran at midnight | Race condition → customer charged twice |
| Billing cron crashed at customer #500 of 2000 | No checkpoint → restart → customers 1–499 double-charged |
| Customer cancelled *during* a billing run | Flag arrived too late → charge went through, manual refund |
| One payment gateway timeout | Entire batch failed — every customer after that one uncharged |
| "What's Alice's subscription status?" | `SELECT * FROM subscriptions WHERE id = ?` — complex, stale, drifted |

---

## The Fix

One Temporal workflow per customer. Every subscription lifecycle — trial, billing loop, cancellation — lives in one place, with durable state, automatic retries, and clean signal handling.

```
signup
  └─► sendWelcomeEmail
  └─► Workflow.sleep(14 days trial)   ← survives server crashes
        │  [cancelSubscription Signal can arrive here]
        │
  └─► billing loop (up to maxBillingPeriods):
        └─► chargeCustomer            ← retries automatically on failure
        └─► sendPaymentEmail
        └─► Workflow.sleep(30 days)   ← durable, survives crashes
              │  [cancelSubscription Signal can arrive here]
              │  [updateBillingCharge Signal can arrive here]
```

---

## What this demo shows

| Temporal feature | Where | Why it matters |
|---|---|---|
| `Workflow.sleep()` | `SubscriptionWorkflowImpl` | Durable timer — survives crashes. NOT `Thread.sleep()`. |
| `Workflow.await()` | `SubscriptionWorkflowImpl` | Wakes up early when a Signal arrives during sleep |
| `@SignalMethod` cancel | `SubscriptionWorkflow` | Customer cancels at any point — handled immediately |
| `@SignalMethod` update | `SubscriptionWorkflow` | Change billing amount on a running subscription |
| `@QueryMethod` | `SubscriptionWorkflow` | Read live state — no database needed |
| Per-activity retry | `ActivityOptions` | One payment failure retries that customer only |
| Single input object | `Customer.java` | Best practice — add fields without breaking running workflows |
| `ContinueAsNew` (documented) | `SubscriptionWorkflowImpl` | Prevents event history overflow on long subscriptions |

---

## Project layout

```
saas-subscription/
├── before/
│   └── src/main/java/io/temporal/subscription/
│       └── SubscriptionBillingSystem.java   ← 3 fragile cron scripts
│
├── after/
│   └── src/main/java/io/temporal/subscription/
│       ├── model/
│       │   ├── Customer.java               ← workflow input
│       │   └── BillingInfo.java            ← query response
│       ├── activities/
│       │   ├── SubscriptionActivities.java ← activity interface
│       │   └── SubscriptionActivitiesImpl.java
│       ├── workflow/
│       │   ├── SubscriptionWorkflow.java   ← workflow interface (Signal + Query)
│       │   └── SubscriptionWorkflowImpl.java ← orchestration logic
│       ├── worker/
│       │   └── SubscriptionWorker.java     ← registers and polls
│       └── starter/
│           ├── SubscriptionStarter.java    ← starts 3 subscriptions
│           ├── CancelSubscriptionStarter.java
│           ├── UpdateBillingStarter.java
│           └── QueryBillingStarter.java
│
└── README.md
```

---

## Setup — new MacBook (start here)

### Step 1 — Install Homebrew (Mac package manager)
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Step 2 — Install Java 17
```bash
brew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version   # should print: openjdk 17...
```

### Step 3 — Install Maven
```bash
brew install maven
mvn -version    # should print: Apache Maven 3...
```

### Step 4 — Install Temporal CLI
```bash
brew install temporal
temporal version  # should print: temporal version...
```

### Step 5 — Clone this repo
```bash
git clone https://github.com/YOUR_USERNAME/saas-subscription-temporal.git
cd saas-subscription-temporal
```

---

## Run the demo (4 terminals)

### Terminal 1 — Start Temporal dev server
```bash
temporal server start-dev
```
**Open http://localhost:8233 in your browser now.** You will watch workflows execute here live.

### Terminal 2 — Start the Worker
```bash
cd after
mvn compile -q
mvn exec:java -Dexec.mainClass="io.temporal.subscription.worker.SubscriptionWorker"
```
You will see: `Worker started — polling: 'subscription-task-queue'`

### Terminal 3 — Start 3 subscription workflows
```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.SubscriptionStarter"
```
Watch the Web UI — 3 workflows appear, all in `Running` state.

### Terminal 4 — Interact with running workflows

**Cancel a subscription mid-trial:**
```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.CancelSubscriptionStarter" \
  -Dexec.args="C-003"
```

**Update billing amount:**
```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.UpdateBillingStarter" \
  -Dexec.args="C-002 200.0"
```

**Query live billing state (no database):**
```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.QueryBillingStarter" \
  -Dexec.args="C-001"
```

---

## The live demo moment that lands hardest

After starting the workflows, wait ~5 seconds (they are in trial sleep), then kill the Worker with `Ctrl+C`. Restart it. The workflows **resume from where they stopped** — the trial timer picks up mid-count, not from zero. That is durable execution. That is the entire point.

---

## Key concepts to explain in the presentation

### Why `Workflow.sleep()` and not `Thread.sleep()`?

`Thread.sleep()` blocks a JVM thread and is **lost when the server restarts**. `Workflow.sleep()` is persisted to Temporal's event history as a timer. If the server crashes on day 13 of a 14-day trial, when it comes back up the timer resumes from day 13, not day 0.

### Why `Workflow.await()` instead of checking a flag in a loop?

`Workflow.await(duration, condition)` does two things at once: it sleeps until the duration expires **or** wakes up early if the condition becomes true (e.g. `cancelled == true`). This is how the cancel Signal interrupts the trial sleep cleanly — no polling, no busy loop.

### Signals vs Queries

**Signals** (`@SignalMethod`) send data INTO a running workflow. Fire-and-forget. Temporal queues them and delivers them on next wake-up. Used for cancel and billing update.

**Queries** (`@QueryMethod`) read state OUT of a running workflow. Synchronous, instant. Not recorded in event history. Replaces a database SELECT.

### What is ContinueAsNew and why does it matter?

Temporal stores every activity call, signal, and timer as an event. There is a soft limit of ~50,000 events per workflow execution. For a subscription that runs for years with frequent signals, you can approach this limit. `Workflow.continueAsNew()` starts a fresh execution with a clean history, carrying forward whatever state you need. It is the production pattern for indefinitely long workflows.

---

## Temporal SDK version
`1.27.0` — latest stable

---

*Temporal documentation: https://docs.temporal.io/develop/java*
