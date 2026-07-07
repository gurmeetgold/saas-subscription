package io.temporal.subscription.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.model.BillingInfo;
import io.temporal.subscription.workflow.SubscriptionWorkflow;

/**
 * UpdateBillingStarter — sends an updateBillingCharge Signal to a running workflow,
 * then immediately queries the workflow to prove the change took effect.
 *
 * HOW THIS PROVES THE POINT TO THE PANEL:
 *   - Queries BEFORE the signal  → shows $149.00
 *   - Sends the signal           → workflow state updated instantly
 *   - Queries AFTER the signal   → shows $200.00
 *
 * No database was touched. No service restarted. The workflow updated its own
 * internal state in response to the signal, and the query reads it back live.
 *
 * Program arguments: C-002  200.0
 */
public class UpdateBillingStarter {

    public static void main(String[] args) throws InterruptedException {
        String customerId = (args.length > 0) ? args[0] : "C-002";
        double newAmount  = (args.length > 1) ? Double.parseDouble(args[1]) : 200.0;
        String workflowId = "subscription-" + customerId;

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);

        SubscriptionWorkflow workflow = client.newWorkflowStub(
                SubscriptionWorkflow.class, workflowId);

        // ── Step 1: Query BEFORE the signal ──────────────────────────────────
        BillingInfo before = workflow.getBillingInfo();

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  SIGNAL — updateBillingCharge");
        System.out.println("  Workflow ID : " + workflowId);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.printf("  BEFORE signal → current charge : $%.2f%n", before.getCurrentCharge());
        System.out.println("  Status: " + before.getStatus()
                + "  |  Billing period: " + before.getBillingPeriodNumber());

        // ── Step 2: Send the signal ───────────────────────────────────────────
        System.out.println();
        System.out.printf("  Sending signal: updateBillingCharge($%.2f)...%n", newAmount);
        workflow.updateBillingCharge(newAmount);
        System.out.println("  Signal delivered to running workflow.");

        // Brief pause so the workflow thread processes the signal
        Thread.sleep(500);

        // ── Step 3: Query AFTER the signal ────────────────────────────────────
        BillingInfo after = workflow.getBillingInfo();

        System.out.println();
        System.out.printf("  AFTER signal  → current charge : $%.2f%n", after.getCurrentCharge());
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  PROOF: charge changed $%.2f → $%.2f%n",
                before.getCurrentCharge(), after.getCurrentCharge());
        System.out.println("  No database update. No restart. No redeployment.");
        System.out.println("  The workflow updated its own state from the signal.");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("  See the signal event in the Web UI:");
        System.out.println("  http://localhost:8233/namespaces/default/workflows/" + workflowId);
        System.out.println("  Look for 'WorkflowExecutionSignaled' in the Event History.");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
