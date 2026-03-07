package model;

import java.time.LocalDate;

public class Bill {
    private int id;
    private String billDate;
    private double totalCost;

    public int getId() {return id;}

    public void setId(int id) {this.id = id;}

    public String getBillDate() {return billDate;}

    public void setBillDate(String billDate) {this.billDate = billDate;}

    public double getTotalCost() {return totalCost;}

    public void setTotalCost(double totalCost) {this.totalCost = totalCost;}

    public Bill() {
    }

    public Bill(int id, String billDate, double totalCost) {
        this.id = id;
        this.billDate = billDate;
        this.totalCost = totalCost;
    }

}
