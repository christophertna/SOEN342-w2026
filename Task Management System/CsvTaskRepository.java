import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvTaskRepository {

    private static final String[] CSV_HEADERS = {
        "TaskName",
        "Description",
        "Subtask",
        "Status",
        "Priority",
        "DueDate",
        "ProjectName",
        "ProjectDescription",
        "Collaborator",
        "CollaboratorCategory"
    };

    private final Path csvPath;

    public CsvTaskRepository(Path csvPath) {
        this.csvPath = csvPath;
    }

    public RepositorySnapshot load() throws PersistenceException {
        if (!Files.exists(csvPath)) {
            return new RepositorySnapshot(new ArrayList<>(), new ArrayList<>());
        }

        try {
            List<String> lines = Files.readAllLines(csvPath);
            if (lines.isEmpty()) {
                return new RepositorySnapshot(new ArrayList<>(), new ArrayList<>());
            }

            List<Task> tasks = new ArrayList<>();
            Map<String, Project> projects = new LinkedHashMap<>();
            int startIndex = isCsvHeader(lines.get(0)) ? 1 : 0;

            for (int i = startIndex; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);
                if (columns.size() != CSV_HEADERS.length) {
                    throw new PersistenceException(
                        "Load failed on line " + (i + 1) + ": expected " + CSV_HEADERS.length + " columns."
                    );
                }

                tasks.add(buildTask(columns, tasks.size() + 1, i + 1, projects));
            }

            return new RepositorySnapshot(tasks, new ArrayList<>(projects.values()));
        } catch (IOException exception) {
            throw new PersistenceException("Load failed: " + exception.getMessage(), exception);
        }
    }

    public void save(List<TaskView> rows) throws PersistenceException {
        try {
            if (csvPath.getParent() != null) {
                Files.createDirectories(csvPath.getParent());
            }

            List<String> outputLines = new ArrayList<>();
            outputLines.add(String.join(",", CSV_HEADERS));
            for (TaskView row : rows) {
                outputLines.add(toCsvLine(row.toCsvColumns()));
            }

            Files.write(csvPath, outputLines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PersistenceException("Save failed: " + exception.getMessage(), exception);
        }
    }

    public static boolean isCsvHeader(String line) {
        List<String> columns = parseCsvLine(line);
        return columns.size() == CSV_HEADERS.length
            && columns.get(0).trim().equalsIgnoreCase(CSV_HEADERS[0]);
    }

    private Task buildTask(List<String> columns, int taskId, int lineNumber, Map<String, Project> projects) {
        String taskName = columns.get(0).trim();
        String description = columns.get(1).trim();
        String subtaskValue = columns.get(2).trim();
        String status = normalizeStatus(columns.get(3).trim());
        String priority = columns.get(4).trim();
        String dueDateValue = columns.get(5).trim();
        String projectName = columns.get(6).trim();
        String projectDescription = columns.get(7).trim();
        String collaborator = columns.get(8).trim();
        String collaboratorCategory = columns.get(9).trim();

        if (taskName.isEmpty() || status.isEmpty() || priority.isEmpty()) {
            throw new PersistenceException(
                "Load failed on line " + lineNumber + ": task name, status, and priority are required."
            );
        }

        if (projectName.isEmpty() && !projectDescription.isEmpty()) {
            throw new PersistenceException(
                "Load failed on line " + lineNumber + ": project description requires a project name."
            );
        }

        if (collaborator.isEmpty() != collaboratorCategory.isEmpty()) {
            throw new PersistenceException(
                "Load failed on line " + lineNumber + ": collaborator and collaborator category must be supplied together."
            );
        }

        LocalDate dueDate = null;
        if (!dueDateValue.isEmpty()) {
            try {
                dueDate = LocalDate.parse(dueDateValue);
            } catch (DateTimeParseException exception) {
                throw new PersistenceException(
                    "Load failed on line " + lineNumber + ": due date must use yyyy-mm-dd."
                );
            }
        }

        Task task = new Task(
            taskId,
            taskName,
            description,
            LocalDateTime.now(),
            priority,
            status,
            dueDate,
            null,
            projectName,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>()
        );
        task.addActivity("Imported from CSV.");

        int subtaskId = 1;
        for (String part : splitSubtasks(subtaskValue)) {
            task.addLoadedSubtask(Subtask.fromStorageString(subtaskId++, part));
        }

        if (!projectName.isEmpty()) {
            Project project = projects.computeIfAbsent(Project.key(projectName), key -> new Project(projectName, projectDescription));
            if (project.getDescription().isEmpty() && !projectDescription.isEmpty()) {
                project.setDescription(projectDescription);
            }

            if (!collaborator.isEmpty()) {
                Collaborator collaboratorRecord = project.addOrUpdateCollaborator(collaborator, collaboratorCategory);
                task.addCollaboratorSubtask(collaboratorRecord);
                task.addActivity("Collaborator linked from CSV import: " + collaboratorRecord.getName() + ".");
            }
        }

        return task;
    }

    private static List<String> splitSubtasks(String rawValue) {
        List<String> values = new ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return values;
        }

        String normalized = rawValue.replace("\\n", "\n");
        for (String line : normalized.split("\\R")) {
            if (!line.trim().isEmpty()) {
                values.add(line.trim());
            }
        }
        return values;
    }

    private static String normalizeStatus(String status) {
        if (status.equalsIgnoreCase("Pending")) {
            return "Open";
        }
        return status;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentCharacter = line.charAt(i);

            if (currentCharacter == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (currentCharacter == ',' && !insideQuotes) {
                columns.add(currentValue.toString());
                currentValue.setLength(0);
            } else {
                currentValue.append(currentCharacter);
            }
        }

        if (insideQuotes) {
            throw new PersistenceException("CSV row contains unmatched quotes.");
        }

        columns.add(currentValue.toString());
        return columns;
    }

    private String toCsvLine(String[] values) {
        List<String> escapedValues = new ArrayList<>();
        for (String value : values) {
            escapedValues.add(escapeCsv(value));
        }
        return String.join(",", escapedValues);
    }

    private String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        boolean needsQuotes = safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n");

        if (!needsQuotes) {
            return safeValue;
        }

        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }
}
