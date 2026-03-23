import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaskSystem {
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

    private static final List<Task> taskDatabase = new ArrayList<>();
    private static final Map<String, String> projects = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> collaboratorsByProject = new LinkedHashMap<>();

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n--- TASK SEARCH SYSTEM ---");
                System.out.println("1. View All Tasks");
                System.out.println("2. Search Tasks by Criteria");
                System.out.println("3. Import Tasks from CSV");
                System.out.println("4. Export All Tasks to CSV");
                System.out.println("5. Exit");
                System.out.print("Select an option: ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        viewAllTasks();
                        break;
                    case "2":
                        performSearch(scanner);
                        break;
                    case "3":
                        importFromCsv(scanner);
                        break;
                    case "4":
                        exportToCsv(scanner);
                        break;
                    case "5":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid option. Try again.");
                }
            }
        }
    }

    private static void viewAllTasks() {
        List<Task> sortedTasks = taskDatabase.stream()
            .sorted(Comparator.comparing(Task::getDueDate).thenComparing(Task::getTaskName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        System.out.println("\n--- ALL TASKS ---");
        displayTasks(sortedTasks);
    }

    private static void performSearch(Scanner scanner) {
        System.out.print("Enter keyword (task, description, or subtask, leave blank for none): ");
        String keyword = scanner.nextLine().trim();

        System.out.print("Filter by status (leave blank for none): ");
        String status = scanner.nextLine().trim();

        System.out.print("Filter by priority (leave blank for none): ");
        String priority = scanner.nextLine().trim();

        System.out.print("Filter by project name (leave blank for none): ");
        String projectName = scanner.nextLine().trim();

        System.out.print("Filter by collaborator (leave blank for none): ");
        String collaborator = scanner.nextLine().trim();

        System.out.print("Due date from (yyyy-mm-dd, leave blank for none): ");
        String dueDateFromInput = scanner.nextLine().trim();

        System.out.print("Due date to (yyyy-mm-dd, leave blank for none): ");
        String dueDateToInput = scanner.nextLine().trim();

        LocalDate dueDateFrom = parseOptionalDate(dueDateFromInput, "start");
        LocalDate dueDateTo = parseOptionalDate(dueDateToInput, "end");

        if (dueDateFromInput.length() > 0 && dueDateFrom == null) {
            return;
        }

        if (dueDateToInput.length() > 0 && dueDateTo == null) {
            return;
        }

        boolean noCriteria =
            keyword.isEmpty() &&
            status.isEmpty() &&
            priority.isEmpty() &&
            projectName.isEmpty() &&
            collaborator.isEmpty() &&
            dueDateFrom == null &&
            dueDateTo == null;

        Stream<Task> taskStream = taskDatabase.stream();

        if (noCriteria) {
            taskStream = taskStream.filter(Task::isOpen);
        }

        List<Task> results = taskStream
            .filter(task -> keyword.isEmpty() || matchesKeyword(task, keyword))
            .filter(task -> status.isEmpty() || equalsIgnoreCase(task.getStatus(), status))
            .filter(task -> priority.isEmpty() || equalsIgnoreCase(task.getPriority(), priority))
            .filter(task -> projectName.isEmpty() || containsIgnoreCase(task.getProjectName(), projectName))
            .filter(task -> collaborator.isEmpty() || containsIgnoreCase(task.getCollaborator(), collaborator))
            .filter(task -> dueDateFrom == null || !task.getDueDate().isBefore(dueDateFrom))
            .filter(task -> dueDateTo == null || !task.getDueDate().isAfter(dueDateTo))
            .sorted(Comparator.comparing(Task::getDueDate).thenComparing(Task::getTaskName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        System.out.println("\n--- SEARCH RESULTS ---");
        displayTasks(results);
    }

    private static void importFromCsv(Scanner scanner) {
        System.out.print("Enter CSV source file path: ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            System.out.println("Import cancelled: a file path is required.");
            return;
        }

        Path sourcePath = Paths.get(filePath);
        if (!Files.exists(sourcePath)) {
            System.out.println("Import failed: file not found.");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(sourcePath);
            if (lines.isEmpty()) {
                System.out.println("Import failed: CSV file is empty.");
                return;
            }

            List<Task> importedTasks = new ArrayList<>();
            Map<String, String> importedProjects = new LinkedHashMap<>();
            Map<String, Map<String, String>> importedCollaborators = new LinkedHashMap<>();
            int startingLine = isHeaderRow(parseCsvLine(lines.get(0))) ? 1 : 0;

            for (int i = startingLine; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);
                int lineNumber = i + 1;

                if (columns.size() != CSV_HEADERS.length) {
                    System.out.println("Import failed on line " + lineNumber + ": expected 10 columns.");
                    return;
                }

                Task task = buildTaskFromColumns(
                    columns,
                    lineNumber,
                    importedProjects,
                    importedCollaborators,
                    importedTasks.size() + 1
                );

                if (task == null) {
                    return;
                }

                importedTasks.add(task);
            }

            taskDatabase.clear();
            taskDatabase.addAll(importedTasks);
            projects.clear();
            projects.putAll(importedProjects);
            collaboratorsByProject.clear();
            collaboratorsByProject.putAll(importedCollaborators);
            System.out.println("Import complete: " + taskDatabase.size() + " task(s) loaded.");
        } catch (IOException exception) {
            System.out.println("Import failed: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            System.out.println("Import failed: " + exception.getMessage());
        }
    }

    private static void exportToCsv(Scanner scanner) {
        System.out.print("Enter destination CSV file path: ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            System.out.println("Export cancelled: a file path is required.");
            return;
        }

        Path destinationPath = Paths.get(filePath);

        try {
            if (destinationPath.getParent() != null) {
                Files.createDirectories(destinationPath.getParent());
            }

            List<String> outputLines = new ArrayList<>();
            outputLines.add(String.join(",", CSV_HEADERS));

            for (Task task : taskDatabase) {
                outputLines.add(toCsvLine(task.toCsvColumns()));
            }

            Files.write(destinationPath, outputLines);
            System.out.println("Export complete: " + taskDatabase.size() + " task(s) written to " + destinationPath + ".");
        } catch (IOException exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private static Task buildTaskFromColumns(
        List<String> columns,
        int lineNumber,
        Map<String, String> importedProjects,
        Map<String, Map<String, String>> importedCollaborators,
        int taskId
    ) {
        String taskName = columns.get(0).trim();
        String description = columns.get(1).trim();
        String subtask = columns.get(2).trim();
        String status = columns.get(3).trim();
        String priority = columns.get(4).trim();
        String dueDateText = columns.get(5).trim();
        String projectName = columns.get(6).trim();
        String projectDescription = columns.get(7).trim();
        String collaborator = columns.get(8).trim();
        String collaboratorCategory = columns.get(9).trim();

        if (taskName.isEmpty() || status.isEmpty() || priority.isEmpty() || dueDateText.isEmpty()) {
            System.out.println("Import failed on line " + lineNumber + ": TaskName, Status, Priority, and DueDate are required.");
            return null;
        }

        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(dueDateText);
        } catch (DateTimeParseException exception) {
            System.out.println("Import failed on line " + lineNumber + ": DueDate must use yyyy-mm-dd.");
            return null;
        }

        if (projectName.isEmpty() && !projectDescription.isEmpty()) {
            System.out.println("Import failed on line " + lineNumber + ": ProjectDescription requires ProjectName.");
            return null;
        }

        if (!projectName.isEmpty()) {
            String existingDescription = importedProjects.get(projectName);
            if (existingDescription != null && !existingDescription.equals(projectDescription)) {
                System.out.println("Import failed on line " + lineNumber + ": project '" + projectName + "' has inconsistent descriptions.");
                return null;
            }
            importedProjects.putIfAbsent(projectName, projectDescription);
        }

        if (collaborator.isEmpty() != collaboratorCategory.isEmpty()) {
            System.out.println("Import failed on line " + lineNumber + ": Collaborator and CollaboratorCategory must be provided together.");
            return null;
        }

        if (!collaborator.isEmpty()) {
            if (projectName.isEmpty()) {
                System.out.println("Import failed on line " + lineNumber + ": Collaborator entries must belong to a project.");
                return null;
            }

            Map<String, String> collaborators = importedCollaborators.computeIfAbsent(projectName, key -> new LinkedHashMap<>());
            String existingCategory = collaborators.get(collaborator);
            if (existingCategory != null && !existingCategory.equalsIgnoreCase(collaboratorCategory)) {
                System.out.println("Import failed on line " + lineNumber + ": collaborator '" + collaborator + "' has inconsistent categories in project '" + projectName + "'.");
                return null;
            }
            collaborators.putIfAbsent(collaborator, collaboratorCategory);
        }

        return new Task(
            taskId,
            taskName,
            description,
            subtask,
            status,
            priority,
            dueDate,
            projectName,
            projectDescription,
            collaborator,
            collaboratorCategory
        );
    }

    private static void displayTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }

        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println("| ID  | Task Name            | Status       | Priority   | Due Date   | Project        |");
        System.out.println("--------------------------------------------------------------------------------------");
        tasks.forEach(System.out::println);
        System.out.println("--------------------------------------------------------------------------------------");
    }

    private static boolean matchesKeyword(Task task, String keyword) {
        return containsIgnoreCase(task.getTaskName(), keyword)
            || containsIgnoreCase(task.getDescription(), keyword)
            || containsIgnoreCase(task.getSubtask(), keyword);
    }

    private static boolean containsIgnoreCase(String value, String searchText) {
        return value != null && value.toLowerCase().contains(searchText.toLowerCase());
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private static LocalDate parseOptionalDate(String value, String label) {
        if (value.isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            System.out.println("Invalid " + label + " date. Use yyyy-mm-dd.");
            return null;
        }
    }

    private static boolean isHeaderRow(List<String> columns) {
        return columns.size() == CSV_HEADERS.length && columns.get(0).trim().equalsIgnoreCase(CSV_HEADERS[0]);
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
            throw new IllegalArgumentException("CSV row contains unmatched quotes.");
        }

        columns.add(currentValue.toString());
        return columns;
    }

    private static String toCsvLine(String[] values) {
        List<String> escapedValues = new ArrayList<>();
        for (String value : values) {
            escapedValues.add(escapeCsv(value));
        }
        return String.join(",", escapedValues);
    }

    private static String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        boolean needsQuotes = safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n");

        if (!needsQuotes) {
            return safeValue;
        }

        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }
}

class Task {
    private final int id;
    private final String taskName;
    private final String description;
    private final String subtask;
    private final String status;
    private final String priority;
    private final LocalDate dueDate;
    private final String projectName;
    private final String projectDescription;
    private final String collaborator;
    private final String collaboratorCategory;

    public Task(
        int id,
        String taskName,
        String description,
        String subtask,
        String status,
        String priority,
        LocalDate dueDate,
        String projectName,
        String projectDescription,
        String collaborator,
        String collaboratorCategory
    ) {
        this.id = id;
        this.taskName = taskName;
        this.description = description;
        this.subtask = subtask;
        this.status = status;
        this.priority = priority;
        this.dueDate = dueDate;
        this.projectName = projectName;
        this.projectDescription = projectDescription;
        this.collaborator = collaborator;
        this.collaboratorCategory = collaboratorCategory;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getDescription() {
        return description;
    }

    public String getSubtask() {
        return subtask;
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

    public String getCollaborator() {
        return collaborator;
    }

    public boolean isOpen() {
        return !status.equalsIgnoreCase("completed") && !status.equalsIgnoreCase("closed");
    }

    public String[] toCsvColumns() {
        return new String[] {
            taskName,
            description,
            subtask,
            status,
            priority,
            dueDate.toString(),
            projectName,
            projectDescription,
            collaborator,
            collaboratorCategory
        };
    }

    @Override
    public String toString() {
        return String.format(
            "| %-3d | %-20s | %-12s | %-10s | %-10s | %-16s |",
            id,
            taskName,
            status,
            priority,
            dueDate,
            blankWhenMissing(projectName)
        );
    }

    private String blankWhenMissing(String value) {
        return value == null ? "" : value;
    }
}
