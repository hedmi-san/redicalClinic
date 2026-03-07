package model;

public class BillItem {
    private int id;
    private int billId;
    private String itemName;
    private double unitPrice;
    private double quantity;

    public int getId() {return id;}

    public void setId(int id) {this.id = id;}

    public int getBillId() {return billId;}

    public void setBillId(int billId) {this.billId = billId;}

    public String getItemName() {return itemName;}

    public void setItemName(String itemName) {this.itemName = itemName;}

    public double getUnitPrice() {return unitPrice;}

    public void setUnitPrice(double unitPrice) {this.unitPrice = unitPrice;}

    public double getQuantity() {return quantity;}

    public void setQuantity(double quantity) {this.quantity = quantity;}

    public BillItem() {
    }

    public BillItem(int id, int billId, String itemName, double unitPrice, double quantity) {
        this.id = id;
        this.billId = billId;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
