package io.temporal.subscription.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * SubscriptionActivities — every external call lives here.
 *
 * Activities are the "unsafe" parts: email APIs, payment gateways.
 * They can fail transiently. Temporal retries each one independently.
 * If chargeCustomer fails for Alice, only Alice's charge retries —
 * Bob and Carol's subscriptions are completely unaffected.
 */
@ActivityInterface
public interface SubscriptionActivities {

    @ActivityMethod
    void sendWelcomeEmail(String email, String companyName);

    /** Returns a charge confirmation ID. */
    @ActivityMethod
    String chargeCustomer(String email, String companyName, double amount, int periodNumber);

    @ActivityMethod
    void sendPaymentEmail(String email, String companyName, double amount, int periodNumber);

    @ActivityMethod
    void sendTrialCancellationEmail(String email, String companyName);

    @ActivityMethod
    void sendSubscriptionCancellationEmail(String email, String companyName);

    @ActivityMethod
    void sendSubscriptionEndedEmail(String email, String companyName);
}
