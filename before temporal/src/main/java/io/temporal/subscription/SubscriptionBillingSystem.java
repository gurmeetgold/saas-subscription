package io.temporal.subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * BEFORE: SaaS Subscription Billing — the fragile cron approach.
 *
 * How it worked at my previous company:
 *   - Script 1: a nightly cron that expired trials, moved customers to ACTIVE
 *   - Script 2: a monthly cron that charged all ACTIVE customers
 *   - Script 3: a web endpoint that set a DB cancellation flag
 *
 * Three scripts. One shared database. Zero coordination between them.
 *
 * The failures were painful and regular:
 *   - Trial expiry cron and billing cron raced at midnight → double charges
 *   - Billing cron crashed at customer #500 of 2000, no checkpoint →
 *     restarting re-charged customers 1-499 again
 *   - Customer cancelled DURING a billing run → charge went through anyway,
 *     angry support call, manual refund
 *   - State scattered across DB columns: trialStartDate, billingCycleCount,
 *     cancellationFlag, nextBillingDate — complex queries, drift over time
 *   - On-call engineers spent hours every month reconciling failed charges
 */
public class SubscriptionBillingSystem {

    private static final Logger log =
            Logger.getLogger(SubscriptionBillingSystem.class.getName());
    private static final Random random = new Random();

    enum Status { TRIAL, ACTIVE, CANCELLED, EXPIRED }

    static class CustomerRecord {
        String id, email, company;
        double charge;
        int cycleCount, maxCycles;
        Status status = Status.TRIAL;
        boolean cancellationFlag = false;

        CustomerRecord(String id, String email, String company, double charge, int maxCycles) {
            this.id = id; this.email = email; this.company = company;
            this.charge = charge; this.maxCycles = maxCycles;
        }
    }

    // Shared database — mutated by three separate scripts with no coordination
    private static final Map<String, CustomerRecord> DB = new HashMap<>();

    // ── Script 1: nightly trial-expiry cron ──────────────────────────────────

    static void runTrialExpiryCron() {
        log.info("=== Trial expiry cron starting ===");
        for (CustomerRecord c : DB.values()) {
            if (c.status == Status.TRIAL) {
                c.status = Status.ACTIVE;
                sendEmail(c.email, "Your free trial has ended — billing starts now");
                // PROBLEM: if billing cron runs concurrently (e.g. both fire
                // at midnight), this customer is now ACTIVE and gets charged
                // in the same run — double charge, furious customer.
            }
        }
    }

    // ── Script 2: monthly billing cron ───────────────────────────────────────

    static void runMonthlyBillingCron() {
        log.info("=== Monthly billing cron starting ===");
        for (CustomerRecord c : DB.values()) {
            if (c.status != Status.ACTIVE) continue;

            // PROBLEM: no checkpoint. Crash here → restart from the top →
            // customers 1 through N get charged again.
            if (c.cancellationFlag) {
                // Too late. We already decided to process this customer.
                // The flag arrived mid-loop. Charge goes through anyway.
                c.status = Status.CANCELLED;
                continue;
            }

            try {
                chargeCustomer(c.email, c.charge);
                c.cycleCount++;
                sendEmail(c.email, "Receipt: $" + c.charge + " (cycle " + c.cycleCount + ")");
                if (c.cycleCount >= c.maxCycles) {
                    c.status = Status.EXPIRED;
                    sendEmail(c.email, "Your subscription has ended. Thank you.");
                }
            } catch (Exception e) {
                // PROBLEM: one payment failure crashes the entire batch.
                // Every customer after this one is not charged this month.
                throw new RuntimeException("Billing batch failed — manual fix required", e);
            }
        }
    }

    // ── Script 3: cancellation endpoint (web app) ─────────────────────────────

    static void cancelSubscription(String customerId) {
        CustomerRecord c = DB.get(customerId);
        if (c == null) return;
        c.cancellationFlag = true;
        c.status = Status.CANCELLED;
        // PROBLEM: no distinction between "cancelled during trial" vs "cancelled
        // while active" → same email template, wrong message sent half the time.
        sendEmail(c.email, "Your subscription has been cancelled.");
    }

    // ── Simulated external calls ──────────────────────────────────────────────

    static void chargeCustomer(String email, double amount) {
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        if (random.nextDouble() < 0.10)
            throw new RuntimeException("Payment gateway timeout");
        log.info("CHARGE $" + amount + " → " + email);
    }

    static void sendEmail(String email, String msg) {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        log.info("EMAIL → " + email + ": " + msg);
    }

    public static void main(String[] args) {
        DB.put("C-001", new CustomerRecord("C-001", "alice@acme.com",    "Acme Corp", 99.0,  12));
        DB.put("C-002", new CustomerRecord("C-002", "bob@globex.com",    "Globex",   149.0,  12));
        DB.put("C-003", new CustomerRecord("C-003", "carol@initech.com", "Initech",   49.0,   6));

        runTrialExpiryCron();
        cancelSubscription("C-003"); // cancelled after cron already read her record
        runMonthlyBillingCron();     // C-003 may still be charged
    }
}
