package io.temporal.subscription.activities;

import java.util.Random;

/**
 * SubscriptionActivitiesImpl — the real external calls.
 *
 * Same logic as the BEFORE version's helper methods.
 * The difference: Temporal now owns retry, timeout, and fault tolerance.
 * You write the happy path. Temporal handles the rest.
 *
 * Emails are printed to stdout so they are clearly visible in IntelliJ's
 * Worker Run panel during the demo — look for the ✉ lines.
 */
public class SubscriptionActivitiesImpl implements SubscriptionActivities {

    private static final Random random = new Random();

    @Override
    public void sendWelcomeEmail(String email, String companyName) {
        simulateLatency(200);
        if (random.nextDouble() < 0.05)
            throw new RuntimeException("Email service down — Temporal will retry");
        System.out.println();
        System.out.println("  ✉  EMAIL SENT");
        System.out.println("     To      : " + email);
        System.out.println("     Subject : Welcome to your free trial, " + companyName + "!");
        System.out.println("     Body    : Your 60-day trial has started. No charge until trial ends.");
        System.out.println();
    }

    @Override
    public String chargeCustomer(String email, String companyName, double amount, int periodNumber) {
        simulateLatency(400);
        if (random.nextDouble() < 0.10)
            throw new RuntimeException("Payment gateway timeout — retrying this customer only");
        String confirmationId = "CHG-" + periodNumber + "-" + companyName.substring(0, 3).toUpperCase();
        System.out.println();
        System.out.println("  💳  PAYMENT CHARGED");
        System.out.println("     Customer : " + companyName + " <" + email + ">");
        System.out.println("     Amount   : $" + String.format("%.2f", amount));
        System.out.println("     Period   : " + periodNumber);
        System.out.println("     Confirm  : " + confirmationId);
        System.out.println();
        return confirmationId;
    }

    @Override
    public void sendPaymentEmail(String email, String companyName, double amount, int periodNumber) {
        simulateLatency(150);
        System.out.println();
        System.out.println("  ✉  EMAIL SENT");
        System.out.println("     To      : " + email);
        System.out.println("     Subject : Payment receipt — $" + String.format("%.2f", amount));
        System.out.println("     Body    : Thank you for period " + periodNumber
                + ". Your next charge is in 90 seconds (demo) / 30 days (production).");
        System.out.println();
    }

    @Override
    public void sendTrialCancellationEmail(String email, String companyName) {
        simulateLatency(150);
        System.out.println();
        System.out.println("  ✉  EMAIL SENT  [TRIAL CANCELLATION]");
        System.out.println("     To      : " + email);
        System.out.println("     Subject : Your free trial has been cancelled");
        System.out.println("     Body    : No charge was made. We hope to see you again, " + companyName + ".");
        System.out.println();
    }

    @Override
    public void sendSubscriptionCancellationEmail(String email, String companyName) {
        simulateLatency(150);
        System.out.println();
        System.out.println("  ✉  EMAIL SENT  [SUBSCRIPTION CANCELLATION]");
        System.out.println("     To      : " + email);
        System.out.println("     Subject : Your subscription has been cancelled");
        System.out.println("     Body    : Your subscription is cancelled. Access continues until end of current period.");
        System.out.println();
    }

    @Override
    public void sendSubscriptionEndedEmail(String email, String companyName) {
        simulateLatency(150);
        System.out.println();
        System.out.println("  ✉  EMAIL SENT  [SUBSCRIPTION ENDED]");
        System.out.println("     To      : " + email);
        System.out.println("     Subject : Your subscription has ended");
        System.out.println("     Body    : Thank you for being a customer, " + companyName
                + ". We hope you'll renew.");
        System.out.println();
    }

    private static void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
