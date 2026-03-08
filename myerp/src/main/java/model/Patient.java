package model;

import java.util.ArrayList;
import java.util.List;

public class Patient {
    private int id;
    private String name;
    private String phone;
    private List<Session> sessions = new ArrayList<>();

    public Patient() {
    }

    public Patient(int id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }

    public double getTotalCost() {
        return sessions.stream().mapToDouble(Session::getCost).sum();
    }

    public double getTotalPaid() {
        return sessions.stream().mapToDouble(Session::getPaidAmount).sum();
    }

    public double getBalance() {
        return getTotalCost() - getTotalPaid();
    }

    public int getSessionCount() {
        return sessions.size();
    }
}
