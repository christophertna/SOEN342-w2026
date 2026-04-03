import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class TaskSystem {

    private static final Map<String, Integer> COLLABORATOR_LIMITS = createCollaboratorLimits();
    private static final Comparator<Task> TASK_ORDER =
        Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getTaskName, String.CASE_INSENSITIVE_ORDER);

    private static final ICalGateway ICAL_GATEWAY = new ICalGatewayImpl();
    private static final List<Task> TASK_DATABASE = new ArrayList<>();

    private static TaskRepository repository;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            repository = new CsvTaskRepository(promptForDataPath(scanner));
            loadData();

            while (true) {
                printMainMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> viewAllTasks();
                    case "2" -> createTask(scanner);
                    case "3" -> editTask(scanner);
                    case "4" -> deleteTask(scanner);
                    case "5" -> manageSubtasks(scanner);
                    case "6" -> performSearch(scanner);
                    case "7" -> viewOverloadedCollaborators();
                    case "8" -> showExportMenu(scanner);
                    case "9" -> importFromCsv(scanner);
                    case "10" -> exportToCsv(scanner);
                    case "11" -> {
                        persistState();
                        System.out.println("Goodbye.");
                        return;
                    }
                    default -> System.out.println("Invalid option.");
                }
            }
        }
    }

    private static void printMainMenu() {
        System.out.println("\n--- TASK MANAGEMENT SYSTEM ---");
        System.out.println("1. View All Tasks");
        System.out.println("2. Create Task");
        System.out.println("3. Edit Task");
        System.out.println("4. Delete Task");
        System.out.println("5. Manage Subtasks");
        System.out.println("6. Search Tasks");
        System.out.println("7. View Overloaded Collaborators");
        System.out.println("8. Export to iCalendar (.ics)");
        System.out.println("9. Import from CSV");
        System.out.println("10. Export to CSV");
        System.out.println("11. Exit");
        System.out.print("Select an option: ");
    }

    private static void loadData() {
        try {
            RepositorySnapshot snapshot = repository.load();
            TASK_DATABASE.clear();
            TASK_DATABASE.addAll(snapshot.getTasks());
            System.out.println("Loaded " + TASK_DATABASE.size() + " task(s).");
        } catch (PersistenceException exception) {
            System.out.println("Startup error: " + exception.getMessage());
            System.out.println("The system will continue with an empty dataset.");
        }
    }

    private static void persistState() {
        try {
            repository.save(TASK_DATABASE);
        } catch (PersistenceException exception) {
            System.out.println("Save failed: " + exception.getMessage());
        }
    }

    private static Path promptForDataPath(Scanner scanner) {
        while (true) {
            System.out.print("Enter data file path (e.g. tasks.csv): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.println("A file path is required.");
                continue;
            }

            try {
                Path path = Paths.get(stripWrappingQuotes(input)).normalize();
                System.out.println("Using data file: " + path);
                return path;
            } catch (Exception exception) {
                System.out.println("Invalid path: " + exception.getMessage());
            }
        }
    }

    private static void viewAllTasks() {
        System.out.println("\n--- ALL TASKS ---");
        displayTasks(sortedTasks(TASK_DATABASE));
    }

    private static void createTask(Scanner scanner) {
        System.out.println("\n--- CREATE TASK ---");

        String title = promptRequired(scanner, "Task title: ");
        String description = promptOptional(scanner, "Description (optional): ");
        String priority = promptPriority(scanner, null);
        LocalDate dueDate = promptDate(scanner, "Due date (yyyy-mm-dd, optional): ", null, true);
        String projectName = promptOptional(scanner, "Project name (optional): ");
        String projectDescription = projectName.isEmpty()
            ? ""
            : promptOptional(scanner, "Project description (optional): ");
        String collaborator = promptOptional(scanner, "Collaborator (optional): ");
        String collaboratorCategory = collaborator.isEmpty()
            ? ""
            : promptCollaboratorCategory(scanner, null);

        Task task = new Task(
            nextTaskId(),
            title,
            description,
            new ArrayList<>(),
            "Open",
            priority,
            dueDate,
            projectName,
            projectDescription,
            collaborator,
            collaboratorCategory
        );

        if (!canAssignCollaborator(task, null)) {
            return;
        }

        TASK_DATABASE.add(task);
        persistState();
        System.out.println("Task created with ID " + task.getId() + ".");
    }

    private static void editTask(Scanner scanner) {
        System.out.println("\n--- EDIT TASK ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        Task beforeEdit = copyTask(task);

        task.setTaskName(promptWithDefault(scanner, "Task title", task.getTaskName()));
        task.setDescription(promptWithDefault(scanner, "Description", task.getDescription()));
        task.setPriority(promptPriority(scanner, task.getPriority()));
        task.setStatus(promptStatus(scanner, task.getStatus()));
        task.setDueDate(promptDate(scanner, "Due date", task.getDueDate(), true));

        String projectName = promptWithDefault(scanner, "Project name", task.getProjectName());
        task.setProjectName(projectName);
        task.setProjectDescription(
            projectName.isEmpty() ? "" : promptWithDefault(scanner, "Project description", task.getProjectDescription())
        );

        String collaborator = promptWithDefault(scanner, "Collaborator", task.getCollaborator());
        task.setCollaborator(collaborator);
        task.setCollaboratorCategory(
            collaborator.isEmpty() ? "" : promptCollaboratorCategory(scanner, task.getCollaboratorCategory())
        );

        if (!canAssignCollaborator(task, beforeEdit)) {
            restoreTask(task, beforeEdit);
            return;
        }

        persistState();
        System.out.println("Task updated.");
    }

    private static void deleteTask(Scanner scanner) {
        System.out.println("\n--- DELETE TASK ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        System.out.print("Delete task \"" + task.getTaskName() + "\"? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Delete cancelled.");
            return;
        }

        TASK_DATABASE.remove(task);
        persistState();
        System.out.println("Task deleted.");
    }

    private static void manageSubtasks(Scanner scanner) {
        System.out.println("\n--- MANAGE SUBTASKS ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        while (true) {
            printSubtasks(task);
            System.out.println("1. Add Subtask");
            System.out.println("2. Mark Subtask Complete");
            System.out.println("3. Remove Subtask");
            System.out.println("4. Back");
            System.out.print("Select an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> {
                    task.addSubtask(promptRequired(scanner, "Subtask title: "));
                    persistState();
                    System.out.println("Subtask added.");
                }
                case "2" -> completeSubtask(scanner, task);
                case "3" -> removeSubtask(scanner, task);
                case "4" -> {
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void completeSubtask(Scanner scanner, Task task) {
        if (task.getSubtasks().isEmpty()) {
            System.out.println("This task has no subtasks.");
            return;
        }

        Integer index = promptSubtaskIndex(scanner, task, "Enter subtask number to mark complete: ");
        if (index == null) {
            return;
        }

        task.completeSubtask(index);
        persistState();
        System.out.println("Subtask updated.");
    }

    private static void removeSubtask(Scanner scanner, Task task) {
        if (task.getSubtasks().isEmpty()) {
            System.out.println("This task has no subtasks.");
            return;
        }

        Integer index = promptSubtaskIndex(scanner, task, "Enter subtask number to remove: ");
        if (index == null) {
            return;
        }

        task.removeSubtask(index);
        persistState();
        System.out.println("Subtask removed.");
    }

    private static void performSearch(Scanner scanner) {
        System.out.println("\n--- SEARCH TASKS ---");
        List<Task> results = searchTasks(scanner);
        System.out.println("\n--- SEARCH RESULTS ---");
        displayTasks(results);
    }

    private static List<Task> searchTasks(Scanner scanner) {
        SearchCriteria criteria = collectSearchCriteria(scanner);
        boolean noCriteria = criteria.isEmpty();

        return TASK_DATABASE.stream()
            .filter(task -> !noCriteria || task.isOpen())
            .filter(task -> criteria.keyword.isEmpty() || task.matchesKeyword(criteria.keyword))
            .filter(task -> criteria.status.isEmpty() || equalsIgnoreCase(task.getStatus(), criteria.status))
            .filter(task -> criteria.priority.isEmpty() || equalsIgnoreCase(task.getPriority(), criteria.priority))
            .filter(task -> criteria.projectName.isEmpty() || containsIgnoreCase(task.getProjectName(), criteria.projectName))
            .filter(task -> criteria.collaborator.isEmpty() || containsIgnoreCase(task.getCollaborator(), criteria.collaborator))
            .filter(task -> criteria.dueDateFrom == null || (task.getDueDate() != null && !task.getDueDate().isBefore(criteria.dueDateFrom)))
            .filter(task -> criteria.dueDateTo == null || (task.getDueDate() != null && !task.getDueDate().isAfter(criteria.dueDateTo)))
            .sorted(TASK_ORDER)
            .collect(Collectors.toList());
    }

    private static void importFromCsv(Scanner scanner) {
        System.out.println("\n--- IMPORT FROM CSV ---");
        Path sourcePath = promptForPath(scanner, "Source CSV file path: ");

        try {
            RepositorySnapshot snapshot = new CsvTaskRepository(sourcePath).load();
            TASK_DATABASE.clear();
            TASK_DATABASE.addAll(snapshot.getTasks());
            persistState();
            System.out.println("Imported " + TASK_DATABASE.size() + " task(s) from " + sourcePath + ".");
        } catch (PersistenceException exception) {
            System.out.println("Import failed: " + exception.getMessage());
        }
    }

    private static void exportToCsv(Scanner scanner) {
        System.out.println("\n--- EXPORT TO CSV ---");
        System.out.println("1. Export All Tasks");
        System.out.println("2. Export Filtered Tasks");
        System.out.print("Select an option: ");

        List<Task> tasksToExport;
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> tasksToExport = sortedTasks(TASK_DATABASE);
            case "2" -> tasksToExport = searchTasks(scanner);
            default -> {
                System.out.println("Invalid option.");
                return;
            }
        }

        Path destinationPath = promptForPath(scanner, "Destination CSV file path: ");

        try {
            new CsvTaskRepository(destinationPath).save(tasksToExport);
            System.out.println("Exported " + tasksToExport.size() + " task(s) to " + destinationPath + ".");
        } catch (PersistenceException exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private static SearchCriteria collectSearchCriteria(Scanner scanner) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.keyword = promptOptional(scanner, "Keyword (task, description, or subtask, optional): ");
        criteria.status = promptOptional(scanner, "Status (Open, In Progress, Completed, Cancelled, optional): ");
        criteria.priority = promptOptional(scanner, "Priority (Low, Medium, High, optional): ");
        criteria.projectName = promptOptional(scanner, "Project name (optional): ");
        criteria.collaborator = promptOptional(scanner, "Collaborator (optional): ");
        criteria.dueDateFrom = promptDate(scanner, "Due date from (yyyy-mm-dd, optional): ", null, true);
        criteria.dueDateTo = promptDate(scanner, "Due date to (yyyy-mm-dd, optional): ", null, true);
        return criteria;
    }

    private static void viewOverloadedCollaborators() {
        System.out.println("\n--- OVERLOADED COLLABORATORS ---");

        Map<String, CollaboratorLoad> loads = new LinkedHashMap<>();
        for (Task task : TASK_DATABASE) {
            if (!task.isOpen() || isBlank(task.getCollaborator())) {
                continue;
            }

            String name = task.getCollaborator().trim();
            CollaboratorLoad load = loads.computeIfAbsent(
                name.toLowerCase(Locale.ROOT),
                key -> new CollaboratorLoad(name, task.getCollaboratorCategory())
            );
            load.increment();
        }

        List<CollaboratorLoad> overloaded = loads.values().stream()
            .filter(load -> load.hasKnownLimit() && load.getOpenTaskCount() > load.getLimit())
            .sorted(Comparator.comparing(CollaboratorLoad::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (overloaded.isEmpty()) {
            System.out.println("No overloaded collaborators found.");
        } else {
            for (CollaboratorLoad load : overloaded) {
                System.out.println(
                    load.getName() + " (" + load.getCategory() + ") - open tasks: "
                        + load.getOpenTaskCount() + ", limit: " + load.getLimit()
                );
            }
        }

        List<CollaboratorLoad> unknownCategories = loads.values().stream()
            .filter(load -> !load.hasKnownLimit())
            .sorted(Comparator.comparing(CollaboratorLoad::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (!unknownCategories.isEmpty()) {
            System.out.println("\nNo configured limit for:");
            for (CollaboratorLoad load : unknownCategories) {
                System.out.println(load.getName() + " (" + load.getCategory() + ")");
            }
        }
    }

    private static void showExportMenu(Scanner scanner) {
        System.out.println("\n--- ICALENDAR EXPORT ---");
        System.out.println("1. Export a Single Task");
        System.out.println("2. Export All Tasks in a Project");
        System.out.println("3. Export Filtered Tasks");
        System.out.print("Select an option: ");

        List<Task> selectedTasks;
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> {
                Task task = promptForTask(scanner);
                if (task == null) {
                    return;
                }
                selectedTasks = new ArrayList<>();
                selectedTasks.add(task);
            }
            case "2" -> {
                String projectName = promptRequired(scanner, "Project name: ");
                selectedTasks = TASK_DATABASE.stream()
                    .filter(currentTask -> equalsIgnoreCase(currentTask.getProjectName(), projectName))
                    .sorted(TASK_ORDER)
                    .collect(Collectors.toList());
            }
            case "3" -> selectedTasks = searchTasks(scanner);
            default -> {
                System.out.println("Invalid option.");
                return;
            }
        }

        List<Task> eligibleTasks = selectedTasks.stream()
            .filter(Task::hasDueDate)
            .collect(Collectors.toList());

        if (eligibleTasks.isEmpty()) {
            System.out.println("No eligible tasks found. Tasks without due dates are ignored.");
            return;
        }

        String destination = promptRequired(scanner, "Destination .ics file path: ");
        try {
            ICAL_GATEWAY.exportTasks(eligibleTasks, Paths.get(stripWrappingQuotes(destination)).normalize());
            System.out.println("Exported " + eligibleTasks.size() + " task(s) to " + destination + ".");
        } catch (ICalExportException exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private static Task promptForTask(Scanner scanner) {
        if (TASK_DATABASE.isEmpty()) {
            System.out.println("There are no tasks to select.");
            return null;
        }

        displayTasks(sortedTasks(TASK_DATABASE));
        System.out.print("Enter task ID: ");
        String input = scanner.nextLine().trim();

        try {
            int id = Integer.parseInt(input);
            Task task = findTaskById(id);
            if (task == null) {
                System.out.println("Task not found.");
            }
            return task;
        } catch (NumberFormatException exception) {
            System.out.println("Invalid task ID.");
            return null;
        }
    }

    private static Path promptForPath(Scanner scanner, String prompt) {
        while (true) {
            String rawValue = promptOptional(scanner, prompt);
            if (!rawValue.isEmpty()) {
                try {
                    return Paths.get(stripWrappingQuotes(rawValue)).normalize();
                } catch (Exception exception) {
                    System.out.println("Invalid path: " + exception.getMessage());
                    continue;
                }
            }

            System.out.println("A file path is required.");
        }
    }

    private static Integer promptSubtaskIndex(Scanner scanner, Task task, String prompt) {
        System.out.print(prompt);
        try {
            int value = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (value < 0 || value >= task.getSubtasks().size()) {
                System.out.println("Invalid subtask number.");
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            System.out.println("Invalid subtask number.");
            return null;
        }
    }

    private static void printSubtasks(Task task) {
        System.out.println("\nTask: " + task.getTaskName());
        if (task.getSubtasks().isEmpty()) {
            System.out.println("No subtasks yet.");
            return;
        }

        for (int i = 0; i < task.getSubtasks().size(); i++) {
            Subtask subtask = task.getSubtasks().get(i);
            String marker = subtask.isCompleted() ? "[x]" : "[ ]";
            System.out.println((i + 1) + ". " + marker + " " + subtask.getTitle());
        }
    }

    private static void displayTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }

        System.out.println("---------------------------------------------------------------------------------------------------");
        System.out.println("| ID  | Task Name            | Status        | Priority | Due Date   | Project        | Collaborator |");
        System.out.println("---------------------------------------------------------------------------------------------------");
        for (Task task : tasks) {
            System.out.println(task);
        }
        System.out.println("---------------------------------------------------------------------------------------------------");
    }

    private static List<Task> sortedTasks(List<Task> tasks) {
        return tasks.stream().sorted(TASK_ORDER).collect(Collectors.toList());
    }

    private static Task findTaskById(int id) {
        for (Task task : TASK_DATABASE) {
            if (task.getId() == id) {
                return task;
            }
        }
        return null;
    }

    private static int nextTaskId() {
        int maxId = 0;
        for (Task task : TASK_DATABASE) {
            maxId = Math.max(maxId, task.getId());
        }
        return maxId + 1;
    }

    private static String promptRequired(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field is required.");
        }
    }

    private static String promptOptional(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String promptWithDefault(Scanner scanner, String label, String currentValue) {
        System.out.print(label + " [" + blankWhenMissing(currentValue) + "]: ");
        String value = scanner.nextLine().trim();
        return value.isEmpty() ? blankWhenMissing(currentValue) : value;
    }

    private static String promptPriority(Scanner scanner, String currentValue) {
        while (true) {
            String label = currentValue == null
                ? "Priority (Low, Medium, High): "
                : "Priority [" + currentValue + "] (Low, Medium, High): ";
            String input = promptOptional(scanner, label);

            if (input.isEmpty() && currentValue != null) {
                return currentValue;
            }

            if (equalsIgnoreCase(input, "Low") || equalsIgnoreCase(input, "Medium") || equalsIgnoreCase(input, "High")) {
                return toDisplayCase(input);
            }

            System.out.println("Priority must be Low, Medium, or High.");
        }
    }

    private static String promptStatus(Scanner scanner, String currentValue) {
        while (true) {
            String label = "Status [" + currentValue + "] (Open, In Progress, Completed, Cancelled): ";
            String input = promptOptional(scanner, label);

            if (input.isEmpty()) {
                return currentValue;
            }

            if (equalsIgnoreCase(input, "Open")
                || equalsIgnoreCase(input, "In Progress")
                || equalsIgnoreCase(input, "Completed")
                || equalsIgnoreCase(input, "Cancelled")) {
                return toDisplayCase(input);
            }

            System.out.println("Status must be Open, In Progress, Completed, or Cancelled.");
        }
    }

    private static String promptCollaboratorCategory(Scanner scanner, String currentValue) {
        while (true) {
            String prompt = currentValue == null || currentValue.isEmpty()
                ? "Collaborator category (Junior, Intermediate, Senior): "
                : "Collaborator category [" + currentValue + "] (Junior, Intermediate, Senior): ";
            String input = promptOptional(scanner, prompt);

            if (input.isEmpty() && currentValue != null) {
                return currentValue;
            }

            if (equalsIgnoreCase(input, "Junior")
                || equalsIgnoreCase(input, "Intermediate")
                || equalsIgnoreCase(input, "Senior")) {
                return toDisplayCase(input);
            }

            System.out.println("Category must be Junior, Intermediate, or Senior.");
        }
    }

    private static LocalDate promptDate(Scanner scanner, String label, LocalDate currentValue, boolean optional) {
        while (true) {
            String prompt = currentValue == null ? label : label + " [" + currentValue + "]";
            prompt += ": ";

            String input = promptOptional(scanner, prompt);
            if (input.isEmpty()) {
                return currentValue;
            }

            if (optional && equalsIgnoreCase(input, "none")) {
                return null;
            }

            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException exception) {
                System.out.println("Use yyyy-mm-dd, or leave blank.");
            }
        }
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean containsIgnoreCase(String value, String searchText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean canAssignCollaborator(Task updatedTask, Task originalTask) {
        if (isBlank(updatedTask.getCollaborator()) || !updatedTask.isOpen()) {
            return true;
        }

        Integer limit = COLLABORATOR_LIMITS.get(updatedTask.getCollaboratorCategory().toLowerCase(Locale.ROOT));
        if (limit == null) {
            System.out.println("Cannot assign collaborator without a valid category limit.");
            return false;
        }

        boolean alreadyCountedForSameCollaborator =
            originalTask != null
                && originalTask.isOpen()
                && !isBlank(originalTask.getCollaborator())
                && equalsIgnoreCase(originalTask.getCollaborator(), updatedTask.getCollaborator());

        if (alreadyCountedForSameCollaborator) {
            return true;
        }

        int openTaskCount = countOpenTasksForCollaborator(updatedTask.getCollaborator(), updatedTask.getId());
        if (openTaskCount >= limit) {
            System.out.println(
                "Assignment rejected: " + updatedTask.getCollaborator() + " already has "
                    + openTaskCount + " open task(s), which meets the limit for "
                    + updatedTask.getCollaboratorCategory() + "."
            );
            return false;
        }

        return true;
    }

    private static int countOpenTasksForCollaborator(String collaborator, int excludedTaskId) {
        int count = 0;
        for (Task task : TASK_DATABASE) {
            if (task.getId() == excludedTaskId) {
                continue;
            }

            if (task.isOpen() && equalsIgnoreCase(task.getCollaborator(), collaborator)) {
                count++;
            }
        }
        return count;
    }

    private static String blankWhenMissing(String value) {
        return value == null ? "" : value;
    }

    private static String toDisplayCase(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("in progress")) {
            return "In Progress";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private static Map<String, Integer> createCollaboratorLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("junior", 10);
        limits.put("intermediate", 5);
        limits.put("senior", 2);
        return limits;
    }

    private static Task copyTask(Task task) {
        return new Task(
            task.getId(),
            task.getTaskName(),
            task.getDescription(),
            new ArrayList<>(task.getSubtasks()),
            task.getStatus(),
            task.getPriority(),
            task.getDueDate(),
            task.getProjectName(),
            task.getProjectDescription(),
            task.getCollaborator(),
            task.getCollaboratorCategory()
        );
    }

    private static void restoreTask(Task task, Task snapshot) {
        task.setTaskName(snapshot.getTaskName());
        task.setDescription(snapshot.getDescription());
        task.setStatus(snapshot.getStatus());
        task.setPriority(snapshot.getPriority());
        task.setDueDate(snapshot.getDueDate());
        task.setProjectName(snapshot.getProjectName());
        task.setProjectDescription(snapshot.getProjectDescription());
        task.setCollaborator(snapshot.getCollaborator());
        task.setCollaboratorCategory(snapshot.getCollaboratorCategory());
    }

    private static final class SearchCriteria {
        private String keyword = "";
        private String status = "";
        private String priority = "";
        private String projectName = "";
        private String collaborator = "";
        private LocalDate dueDateFrom;
        private LocalDate dueDateTo;

        private boolean isEmpty() {
            return keyword.isEmpty()
                && status.isEmpty()
                && priority.isEmpty()
                && projectName.isEmpty()
                && collaborator.isEmpty()
                && dueDateFrom == null
                && dueDateTo == null;
        }
    }

    private static final class CollaboratorLoad {
        private final String name;
        private final String category;
        private final Integer limit;
        private int openTaskCount;

        private CollaboratorLoad(String name, String category) {
            this.name = name;
            this.category = blankWhenMissing(category);
            this.limit = COLLABORATOR_LIMITS.get(this.category.toLowerCase(Locale.ROOT));
        }

        private void increment() {
            openTaskCount++;
        }

        private String getName() {
            return name;
        }

        private String getCategory() {
            return category.isEmpty() ? "Unspecified" : category;
        }

        private Integer getLimit() {
            return limit;
        }

        private int getOpenTaskCount() {
            return openTaskCount;
        }

        private boolean hasKnownLimit() {
            return limit != null;
        }
    }
}
