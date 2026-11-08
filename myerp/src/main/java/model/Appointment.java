package model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Appointment {

    public static final String STATUS_PREVU = "PREVU";
    public static final String STATUS_CONFIRME = "CONFIRME";
    public static final String STATUS_TERMINE = "TERMINE";
    public static final String STATUS_ANNULE = "ANNULE";
    public static final String STATUS_ABSENT = "ABSENT";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private int id;
    private Integer patientId;
    private String patientName;
    private String patientPhone;
    private String patientGender;
    private String guestName;
    private String guestPhone;
    private Integer workerId;
    private String workerName;
    private String date;        // yyyy-MM-dd
    private String startTime;   // HH:mm
    private int durationMin = 30;
    private String motif;
    private String status = STATUS_PREVU;
    private Integer sessionId;
    private String createdBy;
    private String createdAt;

    public Appointment() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public void setPatientId(Integer patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getPatientGender() {
        return patientGender;
    }

    public void setPatientGender(String patientGender) {
        this.patientGender = patientGender;
    }

    public boolean isFemale() {
        if (patientGender == null) return false;
        String g = patientGender.trim().toLowerCase();
        return g.equals("female") || g.equals("femme") || g.startsWith("f");
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(String guestPhone) {
        this.guestPhone = guestPhone;
    }

    public Integer getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Integer workerId) {
        this.workerId = workerId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isWalkIn() {
        return patientId == null || patientId <= 0;
    }

    public boolean isConvertedToSession() {
        return sessionId != null && sessionId > 0;
    }

    public boolean isActiveForConflictCheck() {
        return !STATUS_ANNULE.equalsIgnoreCase(status) && !STATUS_ABSENT.equalsIgnoreCase(status);
    }

    public String getDisplayName() {
        if (!isWalkIn() && patientName != null && !patientName.trim().isEmpty()) {
            return patientName.trim();
        }
        if (guestName != null && !guestName.trim().isEmpty()) {
            return guestName.trim();
        }
        return "Patient inconnu";
    }

    public String getDisplayPhone() {
        if (!isWalkIn() && patientPhone != null && !patientPhone.trim().isEmpty()) {
            return patientPhone.trim();
        }
        if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return guestPhone.trim();
        }
        return "—";
    }

    public String getEndTime() {
        if (startTime == null || startTime.trim().isEmpty()) {
            return "";
        }
        try {
            LocalTime start = LocalTime.parse(startTime.trim(), TIME_FMT);
            LocalTime end = start.plusMinutes(Math.max(1, durationMin));
            return end.format(TIME_FMT);
        } catch (Exception e) {
            return "";
        }
    }

    public String getFormattedTimeRange() {
        String end = getEndTime();
        if (end.isEmpty()) {
            return startTime != null ? startTime : "";
        }
        return startTime + " - " + end;
    }

    public int getStartMinutesOfDay() {
        if (startTime == null || startTime.trim().isEmpty()) {
            return 0;
        }
        try {
            LocalTime start = LocalTime.parse(startTime.trim(), TIME_FMT);
            return start.getHour() * 60 + start.getMinute();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getEndMinutesOfDay() {
        return getStartMinutesOfDay() + Math.max(1, durationMin);
    }

    public static String formatStatusLabel(String status) {
        if (status == null) return "Prévu";
        return switch (status.toUpperCase()) {
            case STATUS_CONFIRME -> "Confirmé";
            case STATUS_TERMINE -> "Terminé";
            case STATUS_ANNULE -> "Annulé";
            case STATUS_ABSENT -> "Absent";
            default -> "Prévu";
        };
    }
}

