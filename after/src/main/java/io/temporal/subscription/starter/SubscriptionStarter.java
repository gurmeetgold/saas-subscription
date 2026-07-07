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
 * Timings (demo-friendly):
 *   Trial period   = 60 seconds  (production: 14 days)
 *   Billing period = 90 seconds  (production: 30 days)
 *   Max cycles     = 3
 *
 * This gives you ~60 seconds to cancel C-003 during trial,
 * and ~90 seconds between billing cycles to send signals and queries.
 *
 * Re-running: if workflows already exist from a previous run, this prints
 * a reminder to reset Temporal. See README for the reset command.
 *
 * Prerequisites:
 *   1. temporal server start-dev   (terminal)
 *   2. SubscriptionWorker running  (IntelliJ)
 *   3. Run this class              (IntelliJ)
 */
public class SubscriptionStarter {

    private static final Logger log = Logger.getLogger(SubscriptionStarter.class.getName());

    // Fixed IDs — simple, stable, match the cancel/query/update configs exactly.
    private static final List<Customer> DEMO_CUSTOMERS = Arrays.asList(
        new Customer("C-001", "alice@acme.com",    "Acme Corp",
                     60, 90, 3, 99.0),   // 60s trial, 90s billing, 3 cycles
        new Customer("C-002", "bob@globex.com",    "Globex Inc",
                     60, 90, 3, 149.0),
        new Customer("C-003", "carol@initech.com", "Initech",
                     60, 90, 3, 49.0)    // cancel this one during the 60s trial window
    );

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        for (Customer customer : DEMO_CUSTOMERS) {
            String workflowId = "subscription-" + customer.getCustomerId();
            try {
                SubscriptionWorkflow workflow = client.newWorkflowStub(
                        SubscriptionWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setTaskQueue(SubscriptionWorkflowImpl.TASK_QUEUE)
                                .setWorkflowId(workflowId)
                                .build()
                );
                WorkflowClient.start(workflow::run, customer);
                log.info("✓ Started: " + customer.getCompanyName()
                        + " [" + customer.getEmail() + "]");
            } catch (io.temporal.client.WorkflowExecutionAlreadyStarted e) {
                log.warning("Workflow already exists: " + workflowId
                        + " — reset Temporal first: temporal server start-dev (restart it)");
            }
        }

        log.info("====================================================");
        log.info("Workflows started. Watch live: http://localhost:8233");
        log.info("You have 60 seconds to run Cancel C-003.");
        log.info("If you see 'already exists' errors: restart temporal server start-dev");
        log.info("====================================================");
    }
}
