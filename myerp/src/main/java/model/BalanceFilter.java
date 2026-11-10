package model;

/**
 * Filter criteria for filtering patients based on their financial balance:
 * - ALL: all patients
 * - DEBT: patients who owe money (totalCost > totalPaid, balance > 0.01)
 * - ADVANCE: patients who paid in advance (totalPaid > totalCost, balance < -0.01)
 * - SETTLED: patients whose balance is zero / settled (Math.abs(balance) <= 0.01)
 */
public enum BalanceFilter {
    ALL("Tous les soldes"),
    DEBT("En dette"),
    ADVANCE("En avance"),
    SETTLED("Réglé");

    private final String displayName;

    BalanceFilter(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean matches(double balance) {
        switch (this) {
            case DEBT:
                return balance > 0.01;
            case ADVANCE:
                return balance < -0.01;
            case SETTLED:
                return Math.abs(balance) <= 0.01;
            case ALL:
            default:
                return true;
        }
    }

    public boolean matches(Patient patient) {
        if (patient == null) return false;
        return matches(patient.getBalance());
    }

    public static BalanceFilter fromDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ALL;
        }
        String normalized = name.trim().toLowerCase();
        if (normalized.contains("tous") || normalized.contains("all")) {
            return ALL;
        }
        if (normalized.contains("dette") || normalized.contains("debt")) {
            return DEBT;
        }
        if (normalized.contains("avance") || normalized.contains("advance") || normalized.contains("crédit") || normalized.contains("credit")) {
            return ADVANCE;
        }
        if (normalized.contains("régl") || normalized.contains("regl") || normalized.contains("rigl") || normalized.contains("soldé") || normalized.equals("soldé") || normalized.contains("settled")) {
            return SETTLED;
        }
        return ALL;
    }
}
