package io.temporal.subscription.workflow;

import io.temporal.subscription.model.BillingInfo;
import io.temporal.subscription.model.Customer;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * SubscriptionWorkflow — the interface that defines our workflow contract.
 *
 * This single interface exposes three kinds of interaction:
 *
 *   @WorkflowMethod  — starts and runs the workflow
 *   @SignalMethod    — sends an event INTO a running workflow (fire-and-forget)
 *   @QueryMethod     — reads state OUT of a running workflow (read-only, instant)
 *
 * This replaces three separate scripts + a database in the BEFORE version.
 * The workflow IS the state. No external DB needed for subscription status.
 */
@WorkflowInterface
public interface SubscriptionWorkflow {

    /**
     * Runs the full subscription lifecycle for one customer:
     * trial → billing loop → end (or cancellation at any point).
     */
    @WorkflowMethod
    void run(Customer customer);

    /**
     * Signal: customer wants to cancel.
     *
     * Can arrive at ANY point — during trial sleep, during billing sleep,
     * even mid-activity. Temporal delivers it safely.
     *
     * In the BEFORE version, a cancellation during a billing run was silently
     * ignored until the next cycle. Here it is handled immediately.
     */
    @SignalMethod
    void cancelSubscription();

    /**
     * Signal: update the monthly billing amount for this customer.
     *
     * The BEFORE version had no way to change a running subscription's charge
     * without a DB update + code redeploy. Here it's one signal.
     */
    @SignalMethod
    void updateBillingCharge(double newAmount);

    /**
     * Query: inspect live billing state without touching a database.
     *
     * BEFORE: SELECT * FROM subscriptions WHERE customer_id = ?
     * AFTER:  ask the workflow directly — always consistent, always current.
     * Queries are not recorded in event history and never mutate state.
     */
    @QueryMethod
    BillingInfo getBillingInfo();
}
