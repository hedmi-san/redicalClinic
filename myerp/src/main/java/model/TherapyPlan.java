package model;

public class TherapyPlan {
    private int id;
    private int patientId;
    private String date;
    private double cost;
    private String patientName; // Transient field for UI display

    public TherapyPlan() {
    }

    public TherapyPlan(int id, int patientId, String date, double cost) {
        this.id = id;
        this.patientId = patientId;
        this.date = date;
        this.cost = cost;
    }

    public int getId() {return id;}

    public void setId(int id) {this.id = id;}

    public int getPatientId() {return patientId;}

    public void setPatientId(int patientId) {this.patientId = patientId;}

    public String getDate() {return date;}

    public void setDate(String date) {this.date = date;}

    public double getCost() {return cost;}

    public void setCost(double cost) {this.cost = cost;}

    public String getPatientName() {return patientName;}

    public void setPatientName(String patientName) {this.patientName = patientName;}
}
