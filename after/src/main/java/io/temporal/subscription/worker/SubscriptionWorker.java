package io.temporal.subscription.worker;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.subscription.activities.SubscriptionActivitiesImpl;
import io.temporal.subscription.workflow.SubscriptionWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.util.logging.Logger;

/**
 * SubscriptionWorker — the process that polls Temporal for work.
 *
 * Run this FIRST (keep it running), then run any Starter class.
 *
 * Prerequisites:
 *   temporal server start-dev     (in a separate terminal)
 */
public class SubscriptionWorker {

    private static final Logger log = Logger.getLogger(SubscriptionWorker.class.getName());

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient       client  = WorkflowClient.newInstance(service);
        WorkerFactory        factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(SubscriptionWorkflowImpl.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(SubscriptionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new SubscriptionActivitiesImpl());

        log.info("Worker started — polling: '" + SubscriptionWorkflowImpl.TASK_QUEUE + "'");
        log.info("Open http://localhost:8233 to watch workflows run live.");
        log.info("Run SubscriptionStarter to start a workflow.");
        log.info("Run CancelSubscriptionStarter to send a cancel signal.");
        log.info("Run UpdateBillingStarter to update billing amount.");
        log.info("Run QueryBillingStarter to query live billing state.");

        factory.start(); // blocks until process is killed
    }
}
