package io.temporal.subscription.model;

/**
 * Input passed to the workflow when it starts.
 *
 * BEST PRACTICE: always use a single class (not multiple params).
 * This lets you add fields later without breaking running workflow executions.
 * Temporal serializes this automatically via Jackson.
 */
public class Customer {
    private String customerId;
    private String email;
    private String companyName;
    private int    trialPeriodSeconds;    // use seconds for demo, days in production
    private int    billingPeriodSeconds;  // use seconds for demo, months in production
    private int    maxBillingPeriods;
    private double billingPeriodCharge;

    public Customer() {} // required by Jackson

    public Customer(String customerId, String email, String companyName,
                    int trialPeriodSeconds, int billingPeriodSeconds,
                    int maxBillingPeriods, double billingPeriodCharge) {
        this.customerId           = customerId;
        this.email                = email;
        this.companyName          = companyName;
        this.trialPeriodSeconds   = trialPeriodSeconds;
        this.billingPeriodSeconds = billingPeriodSeconds;
        this.maxBillingPeriods    = maxBillingPeriods;
        this.billingPeriodCharge  = billingPeriodCharge;
    }

    public String getCustomerId()                   { return customerId; }
    public void   setCustomerId(String v)           { customerId = v; }
    public String getEmail()                        { return email; }
    public void   setEmail(String v)                { email = v; }
    public String getCompanyName()                  { return companyName; }
    public void   setCompanyName(String v)          { companyName = v; }
    public int    getTrialPeriodSeconds()            { return trialPeriodSeconds; }
    public void   setTrialPeriodSeconds(int v)      { trialPeriodSeconds = v; }
    public int    getBillingPeriodSeconds()          { return billingPeriodSeconds; }
    public void   setBillingPeriodSeconds(int v)    { billingPeriodSeconds = v; }
    public int    getMaxBillingPeriods()             { return maxBillingPeriods; }
    public void   setMaxBillingPeriods(int v)        { maxBillingPeriods = v; }
    public double getBillingPeriodCharge()           { return billingPeriodCharge; }
    public void   setBillingPeriodCharge(double v)  { billingPeriodCharge = v; }
}
