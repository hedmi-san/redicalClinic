package model;

public class Sold {
    private int id;
    private String itemName;
    private String soldDate;
    private double soldPrice;
    private double quantity;

    public int getId() {return id;}

    public void setId(int id) {this.id = id;}

    public String getItemName() {return itemName;}

    public void setItemName(String itemName) {this.itemName = itemName;}

    public String getSoldDate() {return soldDate;}

    public void setSoldDate(String soldDate) {this.soldDate = soldDate;}

    public double getSoldPrice() {return soldPrice;}

    public void setSoldPrice(double soldPrice) {this.soldPrice = soldPrice;}

    public double getQuantity() {return quantity;}

    public void setQuantity(double quantity) {this.quantity = quantity;}

    public Sold(){

    }

    public Sold(int id, String itemName, String soldDate, double soldPrice, double quantity){
        this.id = id;
        this.itemName = itemName;
        this.soldDate = soldDate;
        this.soldPrice = soldPrice;
        this.quantity = quantity;
    }
}
