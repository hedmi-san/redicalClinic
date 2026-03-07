package model;

public class PaymentCheck {
    private int id;
    private int workerId;
    private String paymentDate;
    private double paidAmount;
    private String note;

    public PaymentCheck() {
    }

    public PaymentCheck(int id, int workerId, String paymentDate, double paidAmount, String note) {
        this.id = id;
        this.workerId = workerId;
        this.paymentDate = paymentDate;
        this.paidAmount = paidAmount;
        this.note = note;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(double paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
