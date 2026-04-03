import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ICalGatewayImpl implements ICalGateway {

    private static final DateTimeFormatter ICAL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ICAL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    @Override
    public void exportTasks(List<TaskView> tasks, Path destinationPath) throws ICalExportException {
        List<TaskView> eligibleTasks = new ArrayList<>();
        for (TaskView task : tasks) {
            if (task.getDueDate() != null) {
                eligibleTasks.add(task);
            }
        }

        if (eligibleTasks.isEmpty()) {
            throw new ICalExportException("There are no tasks with due dates to export.");
        }

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//SOEN342//Task Management System//EN");
        lines.add("CALSCALE:GREGORIAN");

        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(ICAL_TIMESTAMP);
        for (TaskView task : eligibleTasks) {
            lines.add("BEGIN:VEVENT");
            lines.add("UID:task-" + task.getTaskId() + "-" + formatDate(task.getDueDate()) + "@soen342.local");
            lines.add("DTSTAMP:" + timestamp);
            lines.add("DTSTART;VALUE=DATE:" + formatDate(task.getDueDate()));
            lines.add("SUMMARY:" + escapeText(task.getTaskName()));
            lines.add("DESCRIPTION:" + escapeText(buildDescription(task)));
            lines.add("STATUS:" + mapStatus(task.getStatus()));
            lines.add("END:VEVENT");
        }

        lines.add("END:VCALENDAR");

        try {
            if (destinationPath.getParent() != null) {
                Files.createDirectories(destinationPath.getParent());
            }
            Files.write(destinationPath, lines);
        } catch (IOException exception) {
            throw new ICalExportException("Could not write .ics file: " + exception.getMessage(), exception);
        }
    }

    private String buildDescription(TaskView task) {
        StringBuilder builder = new StringBuilder();

        if (!isBlank(task.getDescription())) {
            builder.append("Description: ").append(task.getDescription());
        }

        appendLine(builder, "Status: " + task.getStatus());
        appendLine(builder, "Priority: " + task.getPriority());

        if (!isBlank(task.getProjectName())) {
            appendLine(builder, "Project: " + task.getProjectName());
        }

        if (!isBlank(task.getSubtaskSummary())) {
            appendLine(builder, "Subtasks:");
            for (String subtask : task.getSubtaskSummary().split("\\s*\\|\\s*")) {
                if (!subtask.isBlank()) {
                    appendLine(builder, "- " + subtask.trim());
                }
            }
        }

        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String formatDate(LocalDate date) {
        return date.format(ICAL_DATE);
    }

    private String mapStatus(String status) {
        if (status != null && status.equalsIgnoreCase("Cancelled")) {
            return "CANCELLED";
        }
        return "CONFIRMED";
    }

    private String escapeText(String value) {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
