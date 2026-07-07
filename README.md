SaaS Subscription Billing — Temporalized

Gurmeet Chhiber | Staff Developer Advocate, Enterprise — Take-Home Assignment


The Problem

At my previous enterprise SaaS company, subscription billing ran as three separate cron scripts sharing one database:


Script 1 — nightly cron that expired trials and moved customers to ACTIVE
Script 2 — monthly cron that charged all ACTIVE customers
Script 3 — web endpoint that set a cancellationFlag column


No coordination. No checkpoints. Painful failures every month:

FailureWhat happenedTrial cron + billing cron ran at midnightRace condition → customer charged twiceBilling cron crashed at customer #500 of 2000No checkpoint → restart → customers 1–499 double-chargedCustomer cancelled during a billing runFlag arrived too late → charge went through, manual refundOne payment gateway timeoutEntire batch failed — every customer after that one uncharged"What's Alice's subscription status?"SELECT * FROM subscriptions WHERE id = ? — complex, stale, drifted


The Fix

One Temporal workflow per customer. Every subscription lifecycle — trial, billing loop, cancellation — lives in one place, with durable state, automatic retries, and clean signal handling.

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


What this demo shows

Temporal featureWhereWhy it mattersWorkflow.sleep()SubscriptionWorkflowImplDurable timer — survives crashes. NOT Thread.sleep().Workflow.await()SubscriptionWorkflowImplWakes up early when a Signal arrives during sleep@SignalMethod cancelSubscriptionWorkflowCustomer cancels at any point — handled immediately@SignalMethod updateSubscriptionWorkflowChange billing amount on a running subscription@QueryMethodSubscriptionWorkflowRead live state — no database neededPer-activity retryActivityOptionsOne payment failure retries that customer onlySingle input objectCustomer.javaBest practice — add fields without breaking running workflowsContinueAsNew (documented)SubscriptionWorkflowImplPrevents event history overflow on long subscriptions


Project layout

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


Setup — new MacBook (start here)

Step 1 — Install Homebrew (Mac package manager)

bash/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

Step 2 — Install Java 17

bashbrew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version   # should print: openjdk 17...

Step 3 — Install Maven

bashbrew install maven
mvn -version    # should print: Apache Maven 3...

Step 4 — Install Temporal CLI

bashbrew install temporal
temporal version  # should print: temporal version...

Step 5 — Clone this repo

bashgit clone https://github.com/YOUR_USERNAME/saas-subscription-temporal.git
cd saas-subscription-temporal


IntelliJ IDEA setup (do this once)

Step 1 — Download IntelliJ IDEA Community (free)

Go to https://www.jetbrains.com/idea/download → scroll to Community Edition → Download.
Open the .dmg, drag IntelliJ to your Applications folder, open it.

Step 2 — Open the project


Click Open
Navigate to Downloads → saas-subscription → after
Select the after folder → click Open
Click Trust Project
Wait ~30 seconds — IntelliJ downloads all dependencies automatically


Step 3 — Fix the keymap (so shortcuts work)

Top menu → IntelliJ IDEA → Settings → Keymap → set the dropdown to macOS → click OK.

Step 4 — Verify it compiles

Top menu → Build → Build Project.
Bottom bar should say "Build: completed successfully" with no red errors.
If you see errors in the Problems tab, click any error to jump straight to that line.


Run the demo

You need one Terminal window (for the Temporal server) and IntelliJ for everything else.

Step 1 — Start the Temporal dev server (Terminal)

Open Terminal and run:

bashtemporal server start-dev

Leave this running the whole time.
Open http://localhost:8233 in your browser now — this is the Web UI where you watch workflows run live.

Step 2 — Create Run Configurations in IntelliJ

IntelliJ uses Run Configurations instead of terminal commands. You set them up once, then run anything with one click.

How to create a Run Configuration — every field explained:


Top menu → Run → Edit Configurations
Click the + button (top left) → choose Application
You will see a form with several fields. Fill them in exactly as follows:


FieldWhat to enterNotesNamee.g. WorkerJust a label — you choose thisModulesaas-subscription-afterSelect from the dropdown — this is the only module in the projectMain classe.g. io.temporal.subscription.worker.SubscriptionWorkerClick the … button to browse, or type it directlyProgram argumentsleave blank (or see table below)Only needed for 3 of the 5 configurationsEverything elseleave as defaultDo not change JVM options or working directory


Click OK


Repeat the steps above for each of these 5 configurations:

NameMain classProgram argumentsWorkerio.temporal.subscription.worker.SubscriptionWorker(leave blank)Start Subscriptionsio.temporal.subscription.starter.SubscriptionStarter(leave blank)Cancel C-003io.temporal.subscription.starter.CancelSubscriptionStarterC-003Update Billing C-002io.temporal.subscription.starter.UpdateBillingStarterC-002 200.0Query C-001io.temporal.subscription.starter.QueryBillingStarterC-001

Step 3 — Run the Worker

In IntelliJ, top right dropdown → select Worker → click the green Run ▶ button.
The Run panel at the bottom opens and shows:

Worker started — polling: 'subscription-task-queue'

Leave this running. Do not stop it.

Step 4 — Start 3 subscription workflows

Top right dropdown → select Start Subscriptions → click Run ▶.
Switch to your browser at http://localhost:8233 — you will see 3 workflows appear in Running state.

Step 5 — Interact with running workflows

Each of these runs in a separate tab in IntelliJ's Run panel — they do not stop the Worker.

Cancel C-003 mid-trial — you have 60 seconds after starting workflows:
Dropdown → Cancel C-003 → Run ▶
Watch the Web UI — C-003's workflow transitions to Completed immediately with a trial cancellation email. C-001 and C-002 keep running — completely unaffected.

Update billing amount for C-002 — run any time during the 90-second billing sleep:
Dropdown → Update Billing C-002 → Run ▶
The next billing cycle for C-002 will charge $200 instead of $149. No database update. No redeployment.

Query live billing state for C-001 — run any time:
Dropdown → Query C-001 → Run ▶
The Run panel shows current status, billing period, and charge amount — read directly from the running workflow. No database query involved.


If you see "workflow already exists" or "already completed" errors

This happens when you re-run the demo and old workflow IDs still exist in Temporal.
Fix: stop the Temporal server in Terminal (Ctrl+C), then restart it:

bashtemporal server start-dev

Restarting the dev server wipes all workflow history and gives you a clean slate.
Then restart the Worker in IntelliJ and run Start Subscriptions again.


If you see sun.misc.Unsafe WARNING lines

WARNING: sun.misc.Unsafe::objectFieldOffset has been called...

This is harmless. It comes from an internal gRPC library that Temporal uses. It does not affect the demo in any way — ignore it completely.


The live demo moment that lands hardest

After starting the 3 workflows, wait 10 seconds (they are sleeping in the 60-second trial period).
Stop the Worker: click the red Stop ■ button in IntelliJ's Run panel.
Switch to the Web UI — the 3 workflows show as Running but frozen.
Restart the Worker: dropdown → Worker → Run ▶.
Watch the workflows resume from where they stopped — the trial timer picks up mid-count, not from zero.

That is durable execution. That is the entire point of Temporal.


Key concepts to explain in the presentation

Why Workflow.sleep() and not Thread.sleep()?

Thread.sleep() blocks a JVM thread and is lost when the server restarts. Workflow.sleep() is persisted to Temporal's event history as a timer. If the server crashes on day 13 of a 14-day trial, when it comes back up the timer resumes from day 13, not day 0.

Why Workflow.await() instead of checking a flag in a loop?

Workflow.await(duration, condition) does two things at once: it sleeps until the duration expires or wakes up early if the condition becomes true (e.g. cancelled == true). This is how the cancel Signal interrupts the trial sleep cleanly — no polling, no busy loop.

Signals vs Queries

Signals (@SignalMethod) send data INTO a running workflow. Fire-and-forget. Temporal queues them and delivers them on next wake-up. Used for cancel and billing update.

Queries (@QueryMethod) read state OUT of a running workflow. Synchronous, instant. Not recorded in event history. Replaces a database SELECT.

What is ContinueAsNew and why does it matter?

Temporal stores every activity call, signal, and timer as an event. There is a soft limit of ~50,000 events per workflow execution. For a subscription that runs for years with frequent signals, you can approach this limit. Workflow.continueAsNew() starts a fresh execution with a clean history, carrying forward whatever state you need. It is the production pattern for indefinitely long workflows.


Temporal SDK version

1.27.0 — latest stable