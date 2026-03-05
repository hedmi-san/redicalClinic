package model;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Worker {
    private int id;
    private String name;
    private String birthDate;
    private String birthPlace;
    private String phoneNumber;
    private String identityCardNumber;
    private String function;
    private String famillySituation;

    public Worker() {
    }

    public Worker(int id, String name, String birthDate, String birthPlace, String phoneNumber,
            String identityCardNumber, String function, String famillySituation) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.phoneNumber = phoneNumber;
        this.identityCardNumber = identityCardNumber;
        this.function = function;
        this.famillySituation = famillySituation;
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

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getIdentityCardNumber() {
        return identityCardNumber;
    }

    public void setIdentityCardNumber(String identityCardNumber) {
        this.identityCardNumber = identityCardNumber;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getFamillySituation() {
        return famillySituation;
    }

    public void setFamillySituation(String famillySituation) {
        this.famillySituation = famillySituation;
    }

    public Integer getAge() {
        if (birthDate == null || birthDate.isEmpty()) {
            return null;
        }
        try {
            // Assuming date format is either yyyy-MM-dd or dd/MM/yyyy based on common
            // usage.
            // SQLite datetime format is usually yyyy-MM-dd
            LocalDate dob;
            if (birthDate.contains("/")) {
                dob = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else {
                dob = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            return Period.between(dob, LocalDate.now()).getYears();
        } catch (DateTimeParseException e) {
            System.err.println("Could not parse birth date: " + birthDate);
            return null;
        }
    }
}
