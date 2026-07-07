# Temporalizing a SaaS Subscription Billing Pipeline

---

## The Scenario

The subscription billing ran as three separate scripts sharing one database:

- A **nightly cron** that expired free trials and moved customers to `ACTIVE`
- A **monthly cron** that charged all active customers
- A **web endpoint** that set a `cancellationFlag` column when a customer cancelled

Three scripts. One database. Zero coordination. The failures were predictable and frequent:

| What went wrong | Why |
|---|---|
| Customer charged on day 1 of free trial | Trial cron and billing cron raced at midnight |
| 499 customers charged twice | Billing cron crashed at customer #500, restarted from the top |
| Cancelled customer charged anyway | Cancellation flag arrived after the loop already read that record |
| 1,999 customers uncharged that month | One payment gateway timeout crashed the entire batch |
| "What is Alice's current plan?" | `SELECT * FROM subscriptions WHERE id = ?` — stale, complex, drifted |

This repo shows the problem and the Temporal solution, side by side, in working Java code.

---

## What This Demonstrates

| Temporal Feature | Where | What It Solves |
|---|---|---|
| `@WorkflowInterface` / `@WorkflowMethod` | `SubscriptionWorkflow.java` | Replaces the monthly billing cron |
| `@ActivityInterface` / `@ActivityMethod` | `SubscriptionActivities.java` | Each external call — email, payment, notification |
| `Workflow.sleep()` | `SubscriptionWorkflowImpl.java` | Durable timer — survives crashes, no cron needed |
| `Workflow.await(duration, condition)` | `SubscriptionWorkflowImpl.java` | Sleeps until timer fires *or* cancel signal arrives |
| `@SignalMethod` — cancel | `SubscriptionWorkflow.java` | Cancellation handled immediately at any point |
| `@SignalMethod` — update billing | `SubscriptionWorkflow.java` | Change billing amount on a running subscription |
| `@QueryMethod` | `SubscriptionWorkflow.java` | Read live state — replaces `SELECT * FROM DB` |
| `RetryOptions` | `SubscriptionWorkflowImpl.java` | Per-customer retry — one failure never blocks others |
| Single input object pattern | `Customer.java` | Add fields without breaking running executions |
| `ContinueAsNew` (documented) | `SubscriptionWorkflowImpl.java` | Prevents event history overflow on long subscriptions |

---

## Project Structure

```
saas-subscription/
│
├── before/                              # The fragile cron approach
│   └── src/main/java/io/temporal/subscription/
│       └── SubscriptionBillingSystem.java   ← three scripts, one class, zero checkpoints
│
├── after/                               # The Temporal solution
│   └── src/main/java/io/temporal/subscription/
│       │
│       ├── model/
│       │   ├── Customer.java            ← workflow input (single object pattern)
│       │   └── BillingInfo.java         ← query response
│       │
│       ├── activities/
│       │   ├── SubscriptionActivities.java      ← activity interface
│       │   └── SubscriptionActivitiesImpl.java  ← email, payment, notification
│       │
│       ├── workflow/
│       │   ├── SubscriptionWorkflow.java        ← interface: @WorkflowMethod, @SignalMethod, @QueryMethod
│       │   └── SubscriptionWorkflowImpl.java    ← orchestration: sleep, await, retry, signals
│       │
│       ├── worker/
│       │   └── SubscriptionWorker.java          ← registers workflow + activities, polls task queue
│       │
│       └── starter/
│           ├── SubscriptionStarter.java         ← starts 3 subscriptions (one per customer)
│           ├── CancelSubscriptionStarter.java   ← sends cancel signal, shows before/after status
│           ├── UpdateBillingStarter.java        ← sends update signal, shows before/after charge
│           └── QueryBillingStarter.java         ← reads live billing state from workflow
│
└── README.md
```

---

## How the Subscription Lifecycle Works

```
Customer signs up
      │
      ▼
[Activity]  sendWelcomeEmail()
      │
      ▼
[Workflow.await(10 min trial, () -> cancelled)]
      │                    │
      │                    └── cancelSubscription() Signal arrives
      │                         → sendTrialCancellationEmail()
      │                         → workflow ends cleanly
      ▼
[Billing loop — repeats up to maxBillingPeriods times]
      │
      ├── [Activity]  chargeCustomer()        ← retries automatically on failure
      ├── [Activity]  sendPaymentEmail()
      │
      └── [Workflow.await(20 min billing period, () -> cancelled)]
                │                    │
                │                    └── cancelSubscription() Signal
                │                         → sendSubscriptionCancellationEmail()
                │                         → workflow ends cleanly
                ▼
           next billing cycle...
      │
      ▼
[Activity]  sendSubscriptionEndedEmail()
```

**Key design decisions:**

`Workflow.await()` is used instead of a plain `Workflow.sleep()`. This means the workflow wakes early if a cancel signal arrives during the trial or billing sleep — no polling, no busy loop. One method handles both the timer and the signal.

Each customer runs as an **independent workflow execution**. A payment failure for C-002 does not affect C-001 or C-003. Their event histories, retry queues, and timers are completely separate.

---

---

## Running the Demo

### Step 1 — Start the Temporal dev server

```bash
temporal server start-dev
```

Open **[http://localhost:8233](http://localhost:8233)** in your browser. Keep this running throughout.

### Step 2 — Compile
```

### Step 3 — Start the Worker

```bash
mvn exec:java -Dexec.mainClass="io.temporal.subscription.worker.SubscriptionWorker"
```

You will see:
```
Worker started — polling: 'subscription-task-queue'
```

### Step 4 — Start 3 subscription workflows

Open a second terminal:

```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.SubscriptionStarter"
```

Three workflows start — one per customer. Switch to the Web UI to watch them.

 trial period = 10 minutes, billing period = 20 minutes.

---

## Interacting with Running Workflows


### Cancel C-003 during trial

```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.CancelSubscriptionStarter" \
  -Dexec.args="C-003"
```

Expected output:
```
BEFORE signal → status : TRIAL
Sending signal: cancelSubscription...
AFTER signal  → status : CANCELLED

PROOF: workflow cancelled immediately on signal.
Cancelled during TRIAL → trial cancellation email sent.
```

C-001 and C-002 continue running, unaffected.

### Update billing amount for C-002

```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.UpdateBillingStarter" \
  -Dexec.args="C-002 200.0"
```

Expected output:
```
BEFORE signal → current charge : $149.00
Sending signal: updateBillingCharge($200.00)...
AFTER signal  → current charge : $200.00

PROOF: charge changed $149.00 → $200.00
No database update. No restart. No redeployment.
```

### Query live billing state for C-001

```bash
cd after
mvn exec:java -Dexec.mainClass="io.temporal.subscription.starter.QueryBillingStarter" \
  -Dexec.args="C-001"
```

Expected output:
```
QUERY — reading live state from workflow
No database. No REST call. Asked the workflow directly.
  Status        : ACTIVE
  Billing period: 1
  Current charge: $99.00
```

---

## The Durable Execution Demo

1. Confirm 3 workflows are `Running` in the Web UI
2. **Kill the Worker** — stop the terminal running `SubscriptionWorker` (`Ctrl+C`)
3. Check the Web UI — workflows still show `Running`. The trial timers are still counting in Temporal's event store. The Worker process is gone. The state is not.
4. **Restart the Worker** — run `SubscriptionWorker` again
5. Workflows resume from exactly where they stopped. The trial timers pick up mid-count.

This is durable execution. The workflow code is stateless. The state lives in Temporal.

---

## Running the BEFORE Version

```bash
cd before
mvn compile -q exec:java
```

The before version simulates the three-script approach. `chargeCustomer()` has a 10% random failure rate — the billing batch will crash after partially completing. On restart, customers already processed would be charged again. There is no checkpoint.

```
Exception in thread "main" java.lang.RuntimeException: Billing batch failed — manual fix required
```

---

## If You Need to Reset

Restarting the Temporal dev server clears all workflow history and gives you a clean slate:

```bash
# Stop the server (Ctrl+C), then:
temporal server start-dev
```

---

## Temporal SDK

`io.temporal:temporal-sdk:1.27.0`

Documentation: [docs.temporal.io/develop/java](https://docs.temporal.io/develop/java)  
Java samples: [github.com/temporalio/samples-java](https://github.com/temporalio/samples-java)

---

*All external calls (email, payment) are simulated with realistic failure rates to demonstrate retry behavior.*
