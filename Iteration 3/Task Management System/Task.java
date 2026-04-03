import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Task {

    private final int id;
    private String taskName;
    private String description;
    private final List<Subtask> subtasks;
    private String status;
    private String priority;
    private LocalDate dueDate;
    private String projectName;
    private String projectDescription;
    private String collaborator;
    private String collaboratorCategory;

    public Task(
        int id,
        String taskName,
        String description,
        List<Subtask> subtasks,
        String status,
        String priority,
        LocalDate dueDate,
        String projectName,
        String projectDescription,
        String collaborator,
        String collaboratorCategory
    ) {
        this.id = id;
        this.taskName = safe(taskName);
        this.description = safe(description);
        this.subtasks = new ArrayList<>(subtasks);
        this.status = safe(status);
        this.priority = safe(priority);
        this.dueDate = dueDate;
        this.projectName = safe(projectName);
        this.projectDescription = safe(projectDescription);
        this.collaborator = safe(collaborator);
        this.collaboratorCategory = safe(collaboratorCategory);
    }

    public int getId() {
        return id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = safe(taskName);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = safe(description);
    }

    public List<Subtask> getSubtasks() {
        return Collections.unmodifiableList(subtasks);
    }

    public void addSubtask(String title) {
        subtasks.add(new Subtask(title, false));
    }

    public void completeSubtask(int index) {
        subtasks.get(index).setCompleted(true);
    }

    public void removeSubtask(int index) {
        subtasks.remove(index);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = safe(status);
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = safe(priority);
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean hasDueDate() {
        return dueDate != null;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = safe(projectName);
    }

    public String getProjectDescription() {
        return projectDescription;
    }

    public void setProjectDescription(String projectDescription) {
        this.projectDescription = safe(projectDescription);
    }

    public String getCollaborator() {
        return collaborator;
    }

    public void setCollaborator(String collaborator) {
        this.collaborator = safe(collaborator);
    }

    public String getCollaboratorCategory() {
        return collaboratorCategory;
    }

    public void setCollaboratorCategory(String collaboratorCategory) {
        this.collaboratorCategory = safe(collaboratorCategory);
    }

    public boolean isOpen() {
        return !status.equalsIgnoreCase("Completed") && !status.equalsIgnoreCase("Cancelled");
    }

    public boolean matchesKeyword(String keyword) {
        String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
        return contains(taskName, loweredKeyword)
            || contains(description, loweredKeyword)
            || contains(getSubtaskSummary(), loweredKeyword);
    }

    public String[] toCsvColumns() {
        return new String[] {
            taskName,
            description,
            getStoredSubtaskValue(),
            status,
            priority,
            dueDate == null ? "" : dueDate.toString(),
            projectName,
            projectDescription,
            collaborator,
            collaboratorCategory
        };
    }

    public String getSubtaskSummary() {
        List<String> lines = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            lines.add(subtask.toDisplayString());
        }
        return String.join(System.lineSeparator(), lines);
    }

    public String getStoredSubtaskValue() {
        List<String> lines = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            lines.add(subtask.toStorageString());
        }
        return String.join("\\n", lines);
    }

    public static List<Subtask> parseSubtasks(String rawValue) {
        List<Subtask> parsed = new ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return parsed;
        }

        String normalized = rawValue.replace("\\n", "\n");
        for (String line : normalized.split("\\R")) {
            if (!line.trim().isEmpty()) {
                parsed.add(Subtask.fromStorageString(line.trim()));
            }
        }
        return parsed;
    }

    @Override
    public String toString() {
        return String.format(
            "| %-3d | %-20s | %-13s | %-8s | %-10s | %-14s | %-12s |",
            id,
            truncate(taskName, 20),
            truncate(status, 13),
            truncate(priority, 8),
            dueDate == null ? "" : dueDate.toString(),
            truncate(projectName, 14),
            truncate(collaborator, 12)
        );
    }

    private static boolean contains(String value, String loweredKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(loweredKeyword);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
