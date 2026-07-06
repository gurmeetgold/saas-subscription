package io.temporal.subscription.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.model.Customer;
import io.temporal.subscription.workflow.SubscriptionWorkflow;
import io.temporal.subscription.workflow.SubscriptionWorkflowImpl;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * SubscriptionStarter — starts subscription workflows for demo customers.
 *
 * Runs 3 customers in parallel (each is its own independent workflow execution).
 * This demonstrates that Temporal can run millions of concurrent workflows —
 * each customer's subscription is completely isolated from every other.
 *
 * Trial period = 10 seconds, billing period = 15 seconds for demo purposes.
 * In production these would be Duration.ofDays(14) and Duration.ofDays(30).
 *
 * Prerequisites:
 *   1. temporal server start-dev   (terminal 1)
 *   2. SubscriptionWorker running  (terminal 2)
 *   3. Run this class              (terminal 3)
 */
public class SubscriptionStarter {

    private static final Logger log = Logger.getLogger(SubscriptionStarter.class.getName());

    // Demo customers — each gets their own independent workflow execution
    private static final List<Customer> DEMO_CUSTOMERS = Arrays.asList(
        new Customer("C-001", "alice@acme.com",    "Acme Corp",
                     10, 15, 3, 99.0),   // 10s trial, 15s billing, 3 cycles, $99/mo
        new Customer("C-002", "bob@globex.com",    "Globex Inc",
                     10, 15, 3, 149.0),  // $149/mo
        new Customer("C-003", "carol@initech.com", "Initech",
                     10, 15, 3, 49.0)    // $49/mo — we will cancel this one mid-trial
    );

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        for (Customer customer : DEMO_CUSTOMERS) {
            SubscriptionWorkflow workflow = client.newWorkflowStub(
                    SubscriptionWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(SubscriptionWorkflowImpl.TASK_QUEUE)
                            // Stable, idempotent ID — running this twice for the
                            // same customer does NOT start a second workflow.
                            .setWorkflowId("subscription-" + customer.getCustomerId())
                            .build()
            );

            // Start async — we don't wait for completion here.
            // Each workflow runs independently on the Worker.
            WorkflowClient.start(workflow::run, customer);
            log.info("Started subscription workflow for " + customer.getCompanyName()
                    + " [" + customer.getEmail() + "]");
        }

        log.info("\n====================================================");
        log.info("3 subscription workflows started.");
        log.info("Watch them live: http://localhost:8233");
        log.info("After ~5 seconds, try:");
        log.info("  CancelSubscriptionStarter C-003  (cancel during trial)");
        log.info("  QueryBillingStarter C-001         (inspect live state)");
        log.info("  UpdateBillingStarter C-002 200.0  (change billing amount)");
        log.info("====================================================");
    }
}
