import java.time.LocalDate;

public class TaskView {

    private final int taskId;
    private final String taskName;
    private final String description;
    private final String subtaskSummary;
    private final String status;
    private final String priority;
    private final LocalDate dueDate;
    private final String projectName;
    private final String projectDescription;
    private final String collaborator;
    private final String collaboratorCategory;

    public TaskView(
        int taskId,
        String taskName,
        String description,
        String subtaskSummary,
        String status,
        String priority,
        LocalDate dueDate,
        String projectName,
        String projectDescription,
        String collaborator,
        String collaboratorCategory
    ) {
        this.taskId = taskId;
        this.taskName = safe(taskName);
        this.description = safe(description);
        this.subtaskSummary = safe(subtaskSummary);
        this.status = safe(status);
        this.priority = safe(priority);
        this.dueDate = dueDate;
        this.projectName = safe(projectName);
        this.projectDescription = safe(projectDescription);
        this.collaborator = safe(collaborator);
        this.collaboratorCategory = safe(collaboratorCategory);
    }

    public int getTaskId() {
        return taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getDescription() {
        return description;
    }

    public String getSubtaskSummary() {
        return subtaskSummary;
    }

    public String getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectDescription() {
        return projectDescription;
    }

    public String getCollaborator() {
        return collaborator;
    }

    public String getCollaboratorCategory() {
        return collaboratorCategory;
    }

    public String[] toCsvColumns() {
        return new String[] {
            taskName,
            description,
            subtaskSummary,
            status,
            priority,
            dueDate == null ? "" : dueDate.toString(),
            projectName,
            projectDescription,
            collaborator,
            collaboratorCategory
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
