package controller;

/**
 * Interface implemented by page controllers that support on-demand data refresh
 * when active tab is selected or switched.
 */
public interface Refreshable {
    /**
     * Refreshes page data from the database.
     */
    void refresh();
}
