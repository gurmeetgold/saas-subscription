package io.temporal.subscription.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.workflow.SubscriptionWorkflow;

import java.util.logging.Logger;

/**
 * UpdateBillingStarter — sends an updateBillingCharge Signal to a running workflow.
 *
 * The next billing cycle will use the new amount.
 * No DB migration. No redeployment. Just a signal.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="...UpdateBillingStarter" -Dexec.args="C-002 200.0"
 */
public class UpdateBillingStarter {

    private static final Logger log = Logger.getLogger(UpdateBillingStarter.class.getName());

    public static void main(String[] args) {
        String customerId = (args.length > 0) ? args[0] : "C-002";
        double newAmount  = (args.length > 1) ? Double.parseDouble(args[1]) : 200.0;
        String workflowId = "subscription-" + customerId;

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        SubscriptionWorkflow workflow = client.newWorkflowStub(
                SubscriptionWorkflow.class, workflowId);

        log.info(String.format("Sending updateBillingCharge($%.2f) to %s", newAmount, workflowId));
        workflow.updateBillingCharge(newAmount);
        log.info("Signal sent. Next billing cycle will use $" + newAmount);
    }
}
