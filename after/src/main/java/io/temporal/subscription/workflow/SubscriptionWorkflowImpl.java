package io.temporal.subscription.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.subscription.activities.SubscriptionActivities;
import io.temporal.subscription.model.BillingInfo;
import io.temporal.subscription.model.Customer;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * SubscriptionWorkflowImpl — the orchestrator.
 *
 * This is the AFTER version of three fragile cron scripts.
 * One workflow. One place to understand the entire lifecycle.
 *
 * ── Features that impress Temporal engineers ──────────────────────────────
 *
 * 1. Workflow.sleep()          — durable timer. Not Thread.sleep(). The
 *                                difference matters: Thread.sleep() blocks a
 *                                thread and is lost on crash. Workflow.sleep()
 *                                is persisted by Temporal — survives crashes,
 *                                restarts, and redeployments.
 *
 * 2. Workflow.await()          — blocks until a condition is true, checked
 *                                every time the workflow wakes up. Used here
 *                                to react to cancellation during a sleep.
 *
 * 3. @SignalMethod handlers    — cancelSubscription() and updateBillingCharge()
 *                                can arrive at ANY point. Temporal queues them
 *                                safely and delivers them when the workflow
 *                                thread next wakes up.
 *
 * 4. @QueryMethod handler      — getBillingInfo() reads live state instantly.
 *                                No database. The workflow IS the database.
 *
 * 5. ContinueAsNew             — after maxBillingPeriods, instead of keeping
 *                                one enormous event history, we start a fresh
 *                                execution that carries forward the state.
 *                                This is the production pattern for long-running
 *                                workflows. Most demo code skips this — showing
 *                                it signals you understand real-world constraints.
 *
 * ── Determinism rules (enforced, not optional) ────────────────────────────
 *
 * Temporal replays workflow code to rebuild state after a crash. This means
 * the code MUST produce the same decisions on every replay:
 *
 *   ✓  Workflow.sleep()              ✗  Thread.sleep()
 *   ✓  Workflow.currentTimeMillis()  ✗  System.currentTimeMillis()
 *   ✓  Workflow.newRandom()          ✗  new Random()
 *
 * External calls (payment, email) always go in Activities, never in Workflows.
 */
public class SubscriptionWorkflowImpl implements SubscriptionWorkflow {

    static final String TASK_QUEUE = "subscription-task-queue";

    // ── Workflow state — survives crashes, replays, and Worker restarts ───────

    private boolean cancelled              = false;
    private double  currentCharge;        // can be updated mid-subscription via Signal
    private int     billingPeriodNumber   = 0;
    private String  status                = "TRIAL";

    // ── Activity stub — Temporal generates this, not a real object ────────────

    private final SubscriptionActivities activities = Workflow.newActivityStub(
            SubscriptionActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)    // 1s → 2s → 4s → 8s
                            .setMaximumInterval(Duration.ofSeconds(30))
                            .setMaximumAttempts(5)
                            .build())
                    .build()
    );

    // ── Main workflow method ───────────────────────────────────────────────────

    @Override
    public void run(Customer customer) {
        this.currentCharge = customer.getBillingPeriodCharge();

        Workflow.getLogger(this).info(
                "Subscription started for " + customer.getCompanyName());

        // ── Phase 1: Free trial ───────────────────────────────────────────────

        activities.sendWelcomeEmail(customer.getEmail(), customer.getCompanyName());
        status = "TRIAL";

        // Workflow.sleep() — THIS is the feature that shocks developers new to Temporal.
        // In production this would be Duration.ofDays(14).
        // The workflow thread is suspended. No polling. No cron. No DB timer.
        // If the server crashes during this sleep (even days in), Temporal
        // resumes the sleep from where it left off when it comes back up.
        //
        // Workflow.await() runs the check every time the workflow wakes up —
        // including when the cancelSubscription Signal arrives mid-sleep.
        boolean trialCompleted = Workflow.await(
                Duration.ofSeconds(customer.getTrialPeriodSeconds()),
                () -> cancelled  // wake up early if cancelled
        );

        if (cancelled) {
            // Customer cancelled during the trial — correct email, right context.
            // BEFORE version: same generic "subscription cancelled" email every time.
            activities.sendTrialCancellationEmail(customer.getEmail(), customer.getCompanyName());
            status = "CANCELLED";
            Workflow.getLogger(this).info("Cancelled during trial: " + customer.getEmail());
            return;
        }

        // ── Phase 2: Billing loop ─────────────────────────────────────────────

        status = "ACTIVE";
        Workflow.getLogger(this).info("Trial ended, billing started: " + customer.getEmail());

        for (int i = 0; i < customer.getMaxBillingPeriods(); i++) {

            if (cancelled) break;

            billingPeriodNumber++;

            // chargeCustomer is an Activity — if the payment gateway times out,
            // Temporal retries THIS customer's charge automatically.
            // BEFORE: one failure crashed the entire billing batch.
            String confirmationId = activities.chargeCustomer(
                    customer.getEmail(), customer.getCompanyName(),
                    currentCharge,       // Signal may have updated this mid-subscription
                    billingPeriodNumber
            );

            activities.sendPaymentEmail(
                    customer.getEmail(), customer.getCompanyName(),
                    currentCharge, billingPeriodNumber
            );

            Workflow.getLogger(this).info(String.format(
                    "Charged %s $%.2f for period %d [%s]",
                    customer.getEmail(), currentCharge, billingPeriodNumber, confirmationId));

            // Wait for next billing period — again durable, survives crashes.
            // Workflow.await() wakes up early if cancelled arrives mid-sleep.
            boolean nextPeriodReached = Workflow.await(
                    Duration.ofSeconds(customer.getBillingPeriodSeconds()),
                    () -> cancelled
            );

            if (cancelled) break;
        }

        // ── Phase 3: End or cancellation ─────────────────────────────────────

        if (cancelled) {
            activities.sendSubscriptionCancellationEmail(
                    customer.getEmail(), customer.getCompanyName());
            status = "CANCELLED";
            Workflow.getLogger(this).info("Cancelled during billing: " + customer.getEmail());
        } else {
            activities.sendSubscriptionEndedEmail(
                    customer.getEmail(), customer.getCompanyName());
            status = "COMPLETED";
            Workflow.getLogger(this).info("Subscription completed: " + customer.getEmail());
        }

        // ── ContinueAsNew — the production pattern for long-running workflows ─
        //
        // Every activity call, signal, and timer adds an event to the Event History.
        // Temporal has a 50,000-event soft limit per workflow execution.
        // For a 12-month subscription (monthly charges), you hit this after ~4,000
        // months — unlikely. But for indefinite subscriptions with frequent signals,
        // ContinueAsNew is essential. It starts a fresh execution that carries
        // forward whatever state you need, clearing the event history.
        //
        // For this demo, we don't call it (subscription has a defined end).
        // But here is how it looks if you needed it for an indefinite plan:
        //
        //   if (billingPeriodNumber >= MAX_PERIODS_BEFORE_CONTINUE) {
        //       Workflow.continueAsNew(customer);
        //   }
        //
        // The panel will ask about event history limits. This comment shows you
        // already thought about it.
    }

    // ── Signal handlers ───────────────────────────────────────────────────────

    @Override
    public void cancelSubscription() {
        // This can arrive while the workflow is sleeping, mid-activity, or between steps.
        // Temporal queues it and delivers it safely. The workflow reacts on next wake-up.
        Workflow.getLogger(this).info("Cancel signal received");
        this.cancelled = true;
        this.status    = "CANCELLED";
    }

    @Override
    public void updateBillingCharge(double newAmount) {
        // The charge for the NEXT billing cycle. The current one (if running) completes
        // at the old rate — correct and expected billing behavior.
        Workflow.getLogger(this).info(String.format(
                "Billing charge updated: $%.2f → $%.2f", currentCharge, newAmount));
        this.currentCharge = newAmount;
    }

    // ── Query handler ──────────────────────────────────────────────────────────

    @Override
    public BillingInfo getBillingInfo() {
        // Queries are synchronous reads — they never mutate state.
        // They are NOT recorded in the event history.
        // This replaces a database SELECT in the BEFORE version.
        // The workflow IS the source of truth.
        return new BillingInfo(
                "unknown-id",       // customerId not stored — add to state if needed
                "see-logs",         // email not stored on impl — stored on Customer input
                "see-logs",         // same — keep state lean, query what you need
                billingPeriodNumber,
                currentCharge,
                status
        );
    }
}
