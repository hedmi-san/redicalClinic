package model;

/**
 * Domain entity representing an immutable ledger payment record.
 */
public class Payment {

    public static final String TYPE_SESSION = "SESSION";
    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_SETTLEMENT = "SETTLEMENT";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_REVERSAL = "REVERSAL";
    public static final String TYPE_THERAPY_PLAN = "THERAPY_PLAN";

    private int id;
    private int patientId;
    private Integer sessionId;
    private String paymentDate;
    private String createdAt;
    private double amount;
    private String paymentType;
    private Integer reversalOf;
    private String reason;
    private String createdBy;

    // Transient UI helper fields
    private String patientName;
    private boolean reversed;

    public Payment() {
    }

    public Payment(int id, int patientId, Integer sessionId, String paymentDate, String createdAt,
                   double amount, String paymentType, Integer reversalOf, String reason, String createdBy) {
        this.id = id;
        this.patientId = patientId;
        this.sessionId = sessionId;
        this.paymentDate = paymentDate;
        this.createdAt = createdAt;
        this.amount = amount;
        this.paymentType = paymentType;
        this.reversalOf = reversalOf;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public Integer getReversalOf() {
        return reversalOf;
    }

    public Integer getReversedPaymentId() {
        return reversalOf;
    }

    public void setReversalOf(Integer reversalOf) {
        this.reversalOf = reversalOf;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public boolean isCompensating() {
        return amount < 0 || TYPE_REVERSAL.equalsIgnoreCase(paymentType) || TYPE_REFUND.equalsIgnoreCase(paymentType);
    }
}
