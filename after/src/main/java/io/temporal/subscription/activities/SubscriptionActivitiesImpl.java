package io.temporal.subscription.activities;

import java.util.Random;
import java.util.logging.Logger;

/**
 * SubscriptionActivitiesImpl — the real external calls.
 *
 * Same logic as the BEFORE version's helper methods.
 * The difference: Temporal now owns retry, timeout, and fault tolerance.
 * You write the happy path. Temporal handles the rest.
 */
public class SubscriptionActivitiesImpl implements SubscriptionActivities {

    private static final Logger log =
            Logger.getLogger(SubscriptionActivitiesImpl.class.getName());
    private static final Random random = new Random();

    @Override
    public void sendWelcomeEmail(String email, String companyName) {
        log.info("[Activity] Sending welcome email → " + email);
        simulateLatency(200);
        if (random.nextDouble() < 0.05)
            throw new RuntimeException("Email service down — Temporal will retry");
        log.info("[Activity] ✓ Welcome email sent → " + email);
    }

    @Override
    public String chargeCustomer(String email, String companyName, double amount, int periodNumber) {
        log.info(String.format("[Activity] Charging %s $%.2f for period %d", email, amount, periodNumber));
        simulateLatency(400);
        if (random.nextDouble() < 0.10)
            // Key point for presentation: this retries ONLY this customer.
            // In the BEFORE version, one failure crashed the entire billing batch.
            throw new RuntimeException("Payment gateway timeout — retrying this customer only");
        String confirmationId = "CHG-" + periodNumber + "-" + companyName.substring(0, 3).toUpperCase();
        log.info("[Activity] ✓ Charged: " + confirmationId);
        return confirmationId;
    }

    @Override
    public void sendPaymentEmail(String email, String companyName, double amount, int periodNumber) {
        log.info(String.format("[Activity] Sending receipt to %s ($%.2f)", email, amount));
        simulateLatency(150);
        log.info("[Activity] ✓ Receipt sent → " + email);
    }

    @Override
    public void sendTrialCancellationEmail(String email, String companyName) {
        log.info("[Activity] Sending TRIAL cancellation email → " + email);
        simulateLatency(150);
        log.info("[Activity] ✓ Trial cancellation sent → " + email);
    }

    @Override
    public void sendSubscriptionCancellationEmail(String email, String companyName) {
        log.info("[Activity] Sending SUBSCRIPTION cancellation email → " + email);
        simulateLatency(150);
        log.info("[Activity] ✓ Subscription cancellation sent → " + email);
    }

    @Override
    public void sendSubscriptionEndedEmail(String email, String companyName) {
        log.info("[Activity] Sending subscription-ended email → " + email);
        simulateLatency(150);
        log.info("[Activity] ✓ Subscription-ended email sent → " + email);
    }

    private static void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
