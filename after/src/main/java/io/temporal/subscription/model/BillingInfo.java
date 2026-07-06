package io.temporal.subscription.model;

/**
 * Returned by the @QueryMethod — lets anyone inspect a live subscription
 * without touching a database. The workflow IS the source of truth.
 */
public class BillingInfo {
    private String customerId;
    private String email;
    private String companyName;
    private int    billingPeriodNumber;
    private double currentCharge;
    private String status; // "TRIAL", "ACTIVE", "CANCELLED", "COMPLETED"

    public BillingInfo() {}

    public BillingInfo(String customerId, String email, String companyName,
                       int billingPeriodNumber, double currentCharge, String status) {
        this.customerId          = customerId;
        this.email               = email;
        this.companyName         = companyName;
        this.billingPeriodNumber = billingPeriodNumber;
        this.currentCharge       = currentCharge;
        this.status              = status;
    }

    public String getCustomerId()                      { return customerId; }
    public void   setCustomerId(String v)              { customerId = v; }
    public String getEmail()                           { return email; }
    public void   setEmail(String v)                   { email = v; }
    public String getCompanyName()                     { return companyName; }
    public void   setCompanyName(String v)             { companyName = v; }
    public int    getBillingPeriodNumber()             { return billingPeriodNumber; }
    public void   setBillingPeriodNumber(int v)        { billingPeriodNumber = v; }
    public double getCurrentCharge()                   { return currentCharge; }
    public void   setCurrentCharge(double v)           { currentCharge = v; }
    public String getStatus()                          { return status; }
    public void   setStatus(String v)                  { status = v; }

    @Override
    public String toString() {
        return String.format("BillingInfo{company='%s', email='%s', status='%s', period=%d, charge=$%.2f}",
                companyName, email, status, billingPeriodNumber, currentCharge);
    }
}
