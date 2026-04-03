import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private String taskName;
    private String description;
    private final LocalDateTime creationTimestamp;
    private String priority;
    private String status;
    private LocalDate dueDate;
    private RecurrencePattern recurrencePattern;
    private String projectName;
    private final List<String> tags;
    private final List<Subtask> subtasks;
    private final List<ActivityEntry> activityHistory;
    private final Map<LocalDate, String> occurrenceStatuses;

    public Task(
        int id,
        String taskName,
        String description,
        LocalDateTime creationTimestamp,
        String priority,
        String status,
        LocalDate dueDate,
        RecurrencePattern recurrencePattern,
        String projectName,
        List<String> tags,
        List<Subtask> subtasks,
        List<ActivityEntry> activityHistory,
        Map<LocalDate, String> occurrenceStatuses
    ) {
        this.id = id;
        this.taskName = safe(taskName);
        this.description = safe(description);
        this.creationTimestamp = creationTimestamp == null ? LocalDateTime.now() : creationTimestamp;
        this.priority = safe(priority);
        this.status = safe(status);
        this.dueDate = dueDate;
        this.recurrencePattern = recurrencePattern;
        this.projectName = safe(projectName);
        this.tags = new ArrayList<>();
        this.subtasks = new ArrayList<>(subtasks);
        this.activityHistory = new ArrayList<>(activityHistory);
        this.occurrenceStatuses = new LinkedHashMap<>(occurrenceStatuses);

        for (String tag : tags) {
            addTag(tag);
        }
        retainOnlyValidOccurrenceStatuses();
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

    public LocalDateTime getCreationTimestamp() {
        return creationTimestamp;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = safe(priority);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = safe(status);
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        retainOnlyValidOccurrenceStatuses();
    }

    public RecurrencePattern getRecurrencePattern() {
        return recurrencePattern;
    }

    public void setRecurrencePattern(RecurrencePattern recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
        retainOnlyValidOccurrenceStatuses();
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = safe(projectName);
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public boolean addTag(String tag) {
        String normalized = safe(tag);
        if (normalized.isEmpty() || tags.stream().anyMatch(existing -> existing.equalsIgnoreCase(normalized))) {
            return false;
        }
        tags.add(normalized);
        return true;
    }

    public boolean removeTag(String tag) {
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).equalsIgnoreCase(tag)) {
                tags.remove(i);
                return true;
            }
        }
        return false;
    }

    public List<Subtask> getSubtasks() {
        return Collections.unmodifiableList(subtasks);
    }

    public Subtask addSubtask(String title) {
        return addSubtask(title, "", "");
    }

    public Subtask addCollaboratorSubtask(Collaborator collaborator) {
        String title = "Collaborate: " + collaborator.getName();
        return addSubtask(title, collaborator.getName(), collaborator.getCategory());
    }

    public Subtask addSubtask(String title, String collaboratorName, String collaboratorCategory) {
        Subtask subtask = new Subtask(nextSubtaskId(), title, false, collaboratorName, collaboratorCategory);
        subtasks.add(subtask);
        return subtask;
    }

    public void addLoadedSubtask(Subtask subtask) {
        subtasks.add(subtask);
    }

    public Subtask findSubtaskById(int subtaskId) {
        for (Subtask subtask : subtasks) {
            if (subtask.getId() == subtaskId) {
                return subtask;
            }
        }
        return null;
    }

    public boolean removeSubtask(int subtaskId) {
        return subtasks.removeIf(subtask -> subtask.getId() == subtaskId);
    }

    public List<ActivityEntry> getActivityHistory() {
        return Collections.unmodifiableList(activityHistory);
    }

    public void addActivity(String description) {
        activityHistory.add(new ActivityEntry(LocalDateTime.now(), description));
    }

    public void addLoadedActivity(ActivityEntry activityEntry) {
        activityHistory.add(activityEntry);
    }

    public Map<LocalDate, String> getOccurrenceStatuses() {
        return Collections.unmodifiableMap(occurrenceStatuses);
    }

    public boolean isRecurring() {
        return recurrencePattern != null;
    }

    public boolean hasDueDate() {
        return dueDate != null || isRecurring();
    }

    public List<LocalDate> getOccurrenceDates() {
        if (isRecurring()) {
            return recurrencePattern.generateDueDates();
        }
        if (dueDate == null) {
            return Collections.emptyList();
        }
        List<LocalDate> dates = new ArrayList<>();
        dates.add(dueDate);
        return dates;
    }

    public boolean hasOccurrenceOn(LocalDate date) {
        return getOccurrenceDates().contains(date);
    }

    public String getStatusForDate(LocalDate date) {
        if (date == null) {
            return status;
        }
        if (!hasOccurrenceOn(date)) {
            throw new IllegalArgumentException("The task has no occurrence on " + date + ".");
        }
        if (isRecurring()) {
            return occurrenceStatuses.getOrDefault(date, "Open");
        }
        return status;
    }

    public void setStatusForDate(LocalDate date, String newStatus) {
        if (date == null) {
            status = safe(newStatus);
            return;
        }
        if (!hasOccurrenceOn(date)) {
            throw new IllegalArgumentException("The task has no occurrence on " + date + ".");
        }
        if (isRecurring()) {
            occurrenceStatuses.put(date, safe(newStatus));
        } else {
            status = safe(newStatus);
        }
    }

    public boolean isOpen() {
        if (isRecurring()) {
            for (LocalDate date : getOccurrenceDates()) {
                if (isOpenStatus(getStatusForDate(date))) {
                    return true;
                }
            }
            return false;
        }
        return isOpenStatus(status);
    }

    public boolean matchesKeyword(String keyword) {
        String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
        return contains(taskName, loweredKeyword)
            || contains(description, loweredKeyword)
            || contains(getSubtaskSummary(), loweredKeyword)
            || contains(String.join(" ", tags), loweredKeyword);
    }

    public String getSubtaskSummary() {
        return subtasks.stream().map(Subtask::toDisplayString).collect(Collectors.joining(" | "));
    }

    public String getCollaboratorSummary() {
        return subtasks.stream()
            .filter(Subtask::isCollaboratorLinked)
            .map(Subtask::getCollaboratorName)
            .distinct()
            .collect(Collectors.joining("; "));
    }

    public String getCollaboratorCategorySummary() {
        Set<String> categories = new LinkedHashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask.isCollaboratorLinked()) {
                categories.add(subtask.getCollaboratorCategory());
            }
        }
        return String.join("; ", categories);
    }

    public int countOpenCollaboratorAssignments() {
        int count = 0;
        for (Subtask subtask : subtasks) {
            if (subtask.isCollaboratorLinked() && !subtask.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    private void retainOnlyValidOccurrenceStatuses() {
        if (isRecurring()) {
            Set<LocalDate> validDates = new LinkedHashSet<>(recurrencePattern.generateDueDates());
            occurrenceStatuses.entrySet().removeIf(entry -> !validDates.contains(entry.getKey()));
            return;
        }

        occurrenceStatuses.clear();
    }

    private int nextSubtaskId() {
        int maxId = 0;
        for (Subtask subtask : subtasks) {
            maxId = Math.max(maxId, subtask.getId());
        }
        return maxId + 1;
    }

    private static boolean isOpenStatus(String value) {
        return !value.equalsIgnoreCase("Completed") && !value.equalsIgnoreCase("Cancelled");
    }

    private static boolean contains(String value, String loweredKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(loweredKeyword);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
