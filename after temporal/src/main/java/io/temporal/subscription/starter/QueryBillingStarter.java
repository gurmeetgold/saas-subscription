package io.temporal.subscription.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.model.BillingInfo;
import io.temporal.subscription.workflow.SubscriptionWorkflow;

import java.util.logging.Logger;

/**
 * QueryBillingStarter — queries a running workflow for its current billing state.
 *
 * This is a synchronous read — no database involved.
 * The workflow IS the source of truth.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="...QueryBillingStarter" -Dexec.args="C-001"
 */
public class QueryBillingStarter {

    private static final Logger log = Logger.getLogger(QueryBillingStarter.class.getName());

    public static void main(String[] args) {
        String customerId = (args.length > 0) ? args[0] : "C-001";
        String workflowId = "subscription-" + customerId;

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        SubscriptionWorkflow workflow = client.newWorkflowStub(
                SubscriptionWorkflow.class, workflowId);

        BillingInfo info = workflow.getBillingInfo();

        System.out.println("\n========================================");
        System.out.println("Live Billing State — no database query");
        System.out.println("========================================");
        System.out.println("  Status         : " + info.getStatus());
        System.out.println("  Billing Period : " + info.getBillingPeriodNumber());
        System.out.println("  Current Charge : $" + info.getCurrentCharge());
        System.out.println("========================================");
        System.out.println("Full history: http://localhost:8233/namespaces/default/workflows/" + workflowId);
    }
}
