public class Task {
    private int id;
    private String title;
    private String status;
    private String priority;

    public Task(int id, String title, String status, String priority) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.priority = priority;
    }

    // Getters for the search logic
    public String getTitle() { return title.toLowerCase(); }
    public String getStatus() { return status.toLowerCase(); }
    public String getPriority() { return priority.toLowerCase(); }

    @Override
    public String toString() {
        return String.format("| %-3d | %-20s | %-12s | %-10s |", id, title, status, priority);
    }
}