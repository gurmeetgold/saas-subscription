package io.temporal.subscription.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.workflow.SubscriptionWorkflow;

import java.util.logging.Logger;

/**
 * CancelSubscriptionStarter — sends a cancel Signal to a running workflow.
 *
 * This is the live demo moment: the workflow is sleeping (trial or billing),
 * you run this, and the workflow wakes up and handles the cancellation
 * immediately — sending the correct email and completing cleanly.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="...CancelSubscriptionStarter" -Dexec.args="C-003"
 */
public class CancelSubscriptionStarter {

    private static final Logger log = Logger.getLogger(CancelSubscriptionStarter.class.getName());

    public static void main(String[] args) {
        String customerId = (args.length > 0) ? args[0] : "C-003";
        String workflowId = "subscription-" + customerId;

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        // Get a typed stub for the running workflow by its ID
        SubscriptionWorkflow workflow = client.newWorkflowStub(
                SubscriptionWorkflow.class, workflowId);

        log.info("Sending cancel signal to workflow: " + workflowId);
        workflow.cancelSubscription(); // Signal — fire and forget
        log.info("Cancel signal sent. Check http://localhost:8233 to see it handled.");
    }
}
