package model;

public class Session {
    private int id;
    private int patientId;
    private String date; // Using String to match DB TEXT format
    private String treatment;
    private String paied; // Status or simple tag
    private double cost;
    private double paidAmount;
    private String patientName; // Transient field for UI display

    public Session() {
    }

    public Session(int id, int patientId, String date, String treatment, double cost, double paidAmount) {
        this.id = id;
        this.patientId = patientId;
        this.date = date;
        this.treatment = treatment;
        this.cost = cost;
        this.paidAmount = paidAmount;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getPaied() {
        return paied;
    }

    public void setPaied(String paied) {
        this.paied = paied;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(double paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    // Helper for badge logic
    public String getPaymentStatus() {
        if (paidAmount >= cost && cost > 0)
            return "FULL";
        if (paidAmount > 0)
            return "PARTIAL";
        return "NONE";
    }
}
