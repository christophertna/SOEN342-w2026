import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class TaskSystem {

    private static final ICalGateway ICAL_GATEWAY = new ICalGatewayImpl();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<String, Project> PROJECTS = new LinkedHashMap<>();
    private static final Comparator<TaskRecord> TASK_RECORD_ORDER =
        Comparator.comparing(TaskRecord::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(record -> record.task().getTaskName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(record -> record.task().getId());

    private static TaskRepository repository;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            repository = new FileTaskRepository(promptForDataPath(scanner));
            loadData();

            while (true) {
                printMainMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> viewAllTasks();
                    case "2" -> createTask(scanner);
                    case "3" -> updateTaskDetails(scanner);
                    case "4" -> updateTaskStatus(scanner, "Completed", "completed");
                    case "5" -> updateTaskStatus(scanner, "Cancelled", "cancelled");
                    case "6" -> updateTaskStatus(scanner, "Open", "reopened");
                    case "7" -> deleteTask(scanner);
                    case "8" -> manageSubtasks(scanner);
                    case "9" -> manageTags(scanner);
                    case "10" -> manageProjects(scanner);
                    case "11" -> searchAndDisplay(scanner);
                    case "12" -> viewActivityHistory(scanner);
                    case "13" -> viewOverloadedCollaborators();
                    case "14" -> exportToICalendar(scanner);
                    case "15" -> importFromCsv(scanner);
                    case "16" -> exportToCsv(scanner);
                    case "17" -> {
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
        System.out.println("3. Update Task Details");
        System.out.println("4. Complete Task / Occurrence");
        System.out.println("5. Cancel Task / Occurrence");
        System.out.println("6. Reopen Task / Occurrence");
        System.out.println("7. Delete Task");
        System.out.println("8. Manage Subtasks");
        System.out.println("9. Manage Tags");
        System.out.println("10. Manage Projects");
        System.out.println("11. Search / Filter Tasks");
        System.out.println("12. View Activity History");
        System.out.println("13. View Overloaded Collaborators");
        System.out.println("14. Export to iCalendar (.ics)");
        System.out.println("15. Import from CSV");
        System.out.println("16. Export to CSV");
        System.out.println("17. Exit");
        System.out.print("Select an option: ");
    }

    private static void loadData() {
        try {
            RepositorySnapshot snapshot = repository.load();
            TASKS.clear();
            PROJECTS.clear();
            TASKS.addAll(snapshot.getTasks());
            for (Project project : snapshot.getProjects()) {
                PROJECTS.put(Project.key(project.getName()), project);
            }
            System.out.println("Loaded " + TASKS.size() + " task(s) and " + PROJECTS.size() + " project(s).");
        } catch (PersistenceException exception) {
            System.out.println("Startup error: " + exception.getMessage());
            System.out.println("The system will continue with an empty dataset.");
        }
    }

    private static void persistState() {
        try {
            repository.save(new RepositorySnapshot(TASKS, new ArrayList<>(PROJECTS.values())));
        } catch (PersistenceException exception) {
            System.out.println("Save failed: " + exception.getMessage());
        }
    }

    private static Path promptForDataPath(Scanner scanner) {
        while (true) {
            System.out.print("Enter persistence file path (e.g. data.ptms): ");
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
        displayTaskRecords(buildTaskRecords(TASKS));
    }

    private static void createTask(Scanner scanner) {
        System.out.println("\n--- CREATE TASK ---");

        String title = promptRequired(scanner, "Task title: ");
        String description = promptOptional(scanner, "Description (optional): ");
        String priority = promptPriority(scanner, null);
        RecurrencePattern recurrencePattern = promptRecurrencePattern(scanner);
        LocalDate dueDate = recurrencePattern == null
            ? promptDate(scanner, "Due date (yyyy-mm-dd, optional)", null, true)
            : null;

        Task task = new Task(
            nextTaskId(),
            title,
            description,
            LocalDateTime.now(),
            priority,
            "Open",
            dueDate,
            recurrencePattern,
            "",
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>()
        );

        String projectName = promptOptional(scanner, "Project name (optional): ");
        if (!projectName.isEmpty()) {
            String projectDescription = promptOptional(scanner, "Project description (optional): ");
            Project project = upsertProject(projectName, projectDescription);
            task.setProjectName(project.getName());
            linkCollaboratorsDuringCreation(scanner, task, project);
        }

        if (!validateTaskConstraints(task, -1)) {
            return;
        }

        task.addActivity("Task created.");
        TASKS.add(task);
        persistState();
        System.out.println("Task created with ID " + task.getId() + ".");
    }

    private static void updateTaskDetails(Scanner scanner) {
        System.out.println("\n--- UPDATE TASK DETAILS ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        String oldTitle = task.getTaskName();
        String oldDescription = task.getDescription();
        String oldPriority = task.getPriority();
        String oldStatus = task.getStatus();
        LocalDate oldDueDate = task.getDueDate();
        RecurrencePattern oldPattern = task.getRecurrencePattern();
        String oldProjectName = task.getProjectName();

        task.setTaskName(promptWithDefault(scanner, "Task title", task.getTaskName()));
        task.setDescription(promptWithDefault(scanner, "Description", task.getDescription()));
        task.setPriority(promptPriority(scanner, task.getPriority()));

        if (!task.isRecurring()) {
            task.setStatus(promptStatus(scanner, task.getStatus(), true));
        }

        String scheduleMode = promptOptional(
            scanner,
            "Scheduling mode [keep/none/one-time/recurring] (leave blank to keep): "
        );
        if (scheduleMode.equalsIgnoreCase("none")) {
            task.setRecurrencePattern(null);
            task.setDueDate(null);
        } else if (scheduleMode.equalsIgnoreCase("one-time")) {
            task.setRecurrencePattern(null);
            task.setDueDate(promptDate(scanner, "Due date (yyyy-mm-dd, optional)", task.getDueDate(), true));
        } else if (scheduleMode.equalsIgnoreCase("recurring")) {
            task.setRecurrencePattern(promptRecurringPatternRequired(scanner));
            task.setDueDate(null);
        }

        if (!task.getProjectName().isEmpty() && hasCollaboratorSubtasks(task)) {
            System.out.println("Current project: " + task.getProjectName());
        }

        String projectInput = promptOptional(
            scanner,
            "Project name [" + blankWhenMissing(task.getProjectName()) + "] (type NONE to remove): "
        );
        if (projectInput.equalsIgnoreCase("NONE")) {
            if (hasCollaboratorSubtasks(task)) {
                System.out.println("Remove collaborator-linked subtasks before removing the project.");
                restoreEditableFields(task, oldTitle, oldDescription, oldPriority, oldStatus, oldDueDate, oldPattern, oldProjectName);
                return;
            }
            task.setProjectName("");
        } else if (!projectInput.isBlank()) {
            Project project = upsertProject(projectInput, promptOptional(scanner, "Project description (optional): "));
            task.setProjectName(project.getName());
        }

        if (!validateTaskConstraints(task, task.getId())) {
            restoreEditableFields(task, oldTitle, oldDescription, oldPriority, oldStatus, oldDueDate, oldPattern, oldProjectName);
            return;
        }

        task.addActivity("Task details updated.");
        persistState();
        System.out.println("Task updated.");
    }

    private static void restoreEditableFields(
        Task task,
        String title,
        String description,
        String priority,
        String status,
        LocalDate dueDate,
        RecurrencePattern recurrencePattern,
        String projectName
    ) {
        task.setTaskName(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStatus(status);
        task.setDueDate(dueDate);
        task.setRecurrencePattern(recurrencePattern);
        task.setProjectName(projectName);
    }

    private static void updateTaskStatus(Scanner scanner, String newStatus, String actionLabel) {
        System.out.println("\n--- " + actionLabel.toUpperCase(Locale.ROOT) + " TASK ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        LocalDate targetDate = promptForOccurrenceDate(scanner, task);
        String currentStatus = task.getStatusForDate(targetDate);

        if (!newStatus.equalsIgnoreCase("Open") && !isOpenStatus(currentStatus)) {
            System.out.println("The selected task or occurrence is already closed.");
            return;
        }
        if (newStatus.equalsIgnoreCase("Open") && isOpenStatus(currentStatus)) {
            System.out.println("The selected task or occurrence is already open.");
            return;
        }
        if (newStatus.equalsIgnoreCase("Open")
            && targetDate == null
            && task.getDueDate() == null
            && exceedsOpenNoDueDateLimit(task, task.getId())) {
            System.out.println("Reopen rejected: the system cannot exceed 50 open tasks without a due date.");
            return;
        }

        task.setStatusForDate(targetDate, newStatus);
        task.addActivity(buildOccurrenceMessage(targetDate, actionLabel));
        persistState();
        System.out.println("Task " + actionLabel + ".");
    }

    private static String buildOccurrenceMessage(LocalDate dueDate, String actionLabel) {
        if (dueDate == null) {
            return "Task " + actionLabel + ".";
        }
        return "Occurrence " + dueDate + " " + actionLabel + ".";
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

        TASKS.remove(task);
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
            System.out.println("1. Add General Subtask");
            System.out.println("2. Link Collaborator to Task");
            System.out.println("3. Complete Subtask");
            System.out.println("4. Reopen Subtask");
            System.out.println("5. Remove Subtask");
            System.out.println("6. Back");
            System.out.print("Select an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> {
                    if (task.getSubtasks().size() >= 20) {
                        System.out.println("A task cannot have more than 20 subtasks.");
                        continue;
                    }
                    task.addSubtask(promptRequired(scanner, "Subtask title: "));
                    task.addActivity("General subtask added.");
                    persistState();
                }
                case "2" -> linkCollaboratorToTask(scanner, task);
                case "3" -> setSubtaskStatus(scanner, task, true);
                case "4" -> setSubtaskStatus(scanner, task, false);
                case "5" -> removeSubtask(scanner, task);
                case "6" -> {
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void linkCollaboratorToTask(Scanner scanner, Task task) {
        if (task.getProjectName().isBlank()) {
            System.out.println("A task must belong to a project before collaborators can be linked.");
            return;
        }
        if (task.getSubtasks().size() >= 20) {
            System.out.println("A task cannot have more than 20 subtasks.");
            return;
        }

        Project project = PROJECTS.get(Project.key(task.getProjectName()));
        if (project == null) {
            System.out.println("The task references a missing project.");
            return;
        }

        String collaboratorName = promptRequired(scanner, "Collaborator name: ");
        Collaborator collaborator = project.findCollaborator(collaboratorName);
        if (collaborator == null) {
            collaborator = project.addOrUpdateCollaborator(collaboratorName, promptCollaboratorCategory(scanner, null));
        }

        if (hasCollaboratorAssignment(task, collaborator.getName())) {
            System.out.println("That collaborator is already linked to the task.");
            return;
        }

        if (!canAssignCollaborator(collaborator)) {
            System.out.println(
                "Assignment rejected: " + collaborator.getName() + " already meets the " + collaborator.getCategory() + " limit."
            );
            return;
        }

        task.addCollaboratorSubtask(collaborator);
        task.addActivity("Collaborator linked: " + collaborator.getName() + ".");
        persistState();
        System.out.println("Collaborator linked through an automatically created subtask.");
    }

    private static void setSubtaskStatus(Scanner scanner, Task task, boolean completed) {
        Subtask subtask = promptForSubtask(scanner, task);
        if (subtask == null) {
            return;
        }

        if (completed == subtask.isCompleted()) {
            System.out.println("No change needed.");
            return;
        }

        subtask.setCompleted(completed);
        task.addActivity("Subtask " + subtask.getId() + (completed ? " completed." : " reopened."));
        persistState();
        System.out.println("Subtask updated.");
    }

    private static void removeSubtask(Scanner scanner, Task task) {
        Subtask subtask = promptForSubtask(scanner, task);
        if (subtask == null) {
            return;
        }

        task.removeSubtask(subtask.getId());
        task.addActivity("Subtask " + subtask.getId() + " removed.");
        persistState();
        System.out.println("Subtask removed.");
    }

    private static void manageTags(Scanner scanner) {
        System.out.println("\n--- MANAGE TAGS ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        while (true) {
            System.out.println("Current tags: " + (task.getTags().isEmpty() ? "(none)" : String.join(", ", task.getTags())));
            System.out.println("1. Add Tag");
            System.out.println("2. Remove Tag");
            System.out.println("3. Back");
            System.out.print("Select an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> {
                    String tag = promptRequired(scanner, "Tag name: ");
                    if (!task.addTag(tag)) {
                        System.out.println("Tag already exists or is invalid.");
                        continue;
                    }
                    task.addActivity("Tag added: " + tag + ".");
                    persistState();
                }
                case "2" -> {
                    String tag = promptRequired(scanner, "Tag name to remove: ");
                    if (!task.removeTag(tag)) {
                        System.out.println("Tag not found.");
                        continue;
                    }
                    task.addActivity("Tag removed: " + tag + ".");
                    persistState();
                }
                case "3" -> {
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void manageProjects(Scanner scanner) {
        while (true) {
            System.out.println("\n--- MANAGE PROJECTS ---");
            System.out.println("1. View Projects");
            System.out.println("2. Create / Update Project");
            System.out.println("3. Assign Task to Project");
            System.out.println("4. Remove Task from Project");
            System.out.println("5. View Project Collaborators");
            System.out.println("6. Back");
            System.out.print("Select an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewProjects();
                case "2" -> createOrUpdateProject(scanner);
                case "3" -> assignTaskToProject(scanner);
                case "4" -> removeTaskFromProject(scanner);
                case "5" -> viewProjectCollaborators(scanner);
                case "6" -> {
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void viewProjects() {
        if (PROJECTS.isEmpty()) {
            System.out.println("No projects defined.");
            return;
        }

        for (Project project : PROJECTS.values()) {
            System.out.println(
                "- " + project.getName() + " | " + blankWhenMissing(project.getDescription())
                    + " | collaborators: " + project.getCollaborators().size()
            );
        }
    }

    private static void createOrUpdateProject(Scanner scanner) {
        String name = promptRequired(scanner, "Project name: ");
        String description = promptOptional(scanner, "Project description (optional): ");
        Project project = upsertProject(name, description);
        persistState();
        System.out.println("Project saved: " + project.getName() + ".");
    }

    private static void assignTaskToProject(Scanner scanner) {
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        String projectName = promptRequired(scanner, "Project name: ");
        String projectDescription = promptOptional(scanner, "Project description (optional): ");
        Project project = upsertProject(projectName, projectDescription);
        task.setProjectName(project.getName());
        task.addActivity("Assigned to project: " + project.getName() + ".");
        persistState();
        System.out.println("Task assigned to project.");
    }

    private static void removeTaskFromProject(Scanner scanner) {
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }
        if (task.getProjectName().isBlank()) {
            System.out.println("The selected task is not assigned to a project.");
            return;
        }
        if (hasCollaboratorSubtasks(task)) {
            System.out.println("Remove collaborator-linked subtasks before removing the project.");
            return;
        }

        task.addActivity("Removed from project: " + task.getProjectName() + ".");
        task.setProjectName("");
        persistState();
        System.out.println("Task removed from project.");
    }

    private static void viewProjectCollaborators(Scanner scanner) {
        if (PROJECTS.isEmpty()) {
            System.out.println("No projects defined.");
            return;
        }

        String projectName = promptRequired(scanner, "Project name: ");
        Project project = PROJECTS.get(Project.key(projectName));
        if (project == null) {
            System.out.println("Project not found.");
            return;
        }

        if (project.getCollaborators().isEmpty()) {
            System.out.println("No collaborators for this project.");
            return;
        }

        for (Collaborator collaborator : project.getCollaborators()) {
            System.out.println("- " + collaborator.getName() + " (" + collaborator.getCategory() + ")");
        }
    }

    private static void searchAndDisplay(Scanner scanner) {
        System.out.println("\n--- SEARCH / FILTER TASKS ---");
        displayTaskRecords(searchTaskRecords(scanner));
    }

    private static List<TaskRecord> searchTaskRecords(Scanner scanner) {
        SearchCriteria criteria = collectSearchCriteria(scanner);
        boolean noCriteria = criteria.isEmpty();

        return buildTaskRecords(TASKS).stream()
            .filter(record -> !noCriteria || isOpenStatus(record.status()))
            .filter(record -> criteria.keyword.isBlank() || record.task().matchesKeyword(criteria.keyword))
            .filter(record -> criteria.status.isBlank() || equalsIgnoreCase(record.status(), criteria.status))
            .filter(record -> criteria.priority.isBlank() || equalsIgnoreCase(record.task().getPriority(), criteria.priority))
            .filter(record -> criteria.projectName.isBlank() || containsIgnoreCase(record.task().getProjectName(), criteria.projectName))
            .filter(record -> criteria.collaborator.isBlank() || containsIgnoreCase(record.task().getCollaboratorSummary(), criteria.collaborator))
            .filter(record -> criteria.collaboratorCategory.isBlank()
                || containsIgnoreCase(record.task().getCollaboratorCategorySummary(), criteria.collaboratorCategory))
            .filter(record -> criteria.tag.isBlank()
                || record.task().getTags().stream().anyMatch(tag -> tag.equalsIgnoreCase(criteria.tag)))
            .filter(record -> criteria.dayOfWeek == null
                || (record.dueDate() != null && record.dueDate().getDayOfWeek() == criteria.dayOfWeek))
            .filter(record -> matchesPeriod(record.dueDate(), criteria))
            .sorted(TASK_RECORD_ORDER)
            .collect(Collectors.toList());
    }

    private static SearchCriteria collectSearchCriteria(Scanner scanner) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.keyword = promptOptional(scanner, "Keyword (task/description/subtask/tag, optional): ");
        criteria.status = promptOptional(scanner, "Status (Open, In Progress, Completed, Cancelled, optional): ");
        criteria.priority = promptOptional(scanner, "Priority (Low, Medium, High, optional): ");
        criteria.projectName = promptOptional(scanner, "Project name (optional): ");
        criteria.collaborator = promptOptional(scanner, "Collaborator (optional): ");
        criteria.collaboratorCategory = promptOptional(scanner, "Collaborator category (optional): ");
        criteria.tag = promptOptional(scanner, "Tag (optional): ");
        criteria.dayOfWeek = promptDayOfWeek(scanner);

        String period = promptOptional(scanner, "Period [none/today/this-week/overdue/custom]: ");
        criteria.period = period.toLowerCase(Locale.ROOT);
        if (criteria.period.equals("custom")) {
            criteria.dueDateFrom = promptDate(scanner, "Due date from (yyyy-mm-dd, optional)", null, true);
            criteria.dueDateTo = promptDate(scanner, "Due date to (yyyy-mm-dd, optional)", null, true);
        }
        return criteria;
    }

    private static boolean matchesPeriod(LocalDate dueDate, SearchCriteria criteria) {
        if (criteria.period.isBlank() || criteria.period.equals("none")) {
            return true;
        }
        if (dueDate == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        return switch (criteria.period) {
            case "today" -> dueDate.equals(today);
            case "this-week" -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                yield !dueDate.isBefore(start) && !dueDate.isAfter(end);
            }
            case "overdue" -> dueDate.isBefore(today);
            case "custom" -> (criteria.dueDateFrom == null || !dueDate.isBefore(criteria.dueDateFrom))
                && (criteria.dueDateTo == null || !dueDate.isAfter(criteria.dueDateTo));
            default -> true;
        };
    }

    private static void viewActivityHistory(Scanner scanner) {
        System.out.println("\n--- VIEW ACTIVITY HISTORY ---");
        Task task = promptForTask(scanner);
        if (task == null) {
            return;
        }

        if (task.getActivityHistory().isEmpty()) {
            System.out.println("No activity history for this task.");
            return;
        }

        for (ActivityEntry activityEntry : task.getActivityHistory()) {
            System.out.println(activityEntry.toDisplayString());
        }
    }

    private static void viewOverloadedCollaborators() {
        System.out.println("\n--- OVERLOADED COLLABORATORS ---");
        Map<String, CollaboratorLoad> loads = calculateCollaboratorLoads();
        List<CollaboratorLoad> overloaded = loads.values().stream()
            .filter(load -> load.openTaskCount() > load.limit())
            .sorted(Comparator.comparing(CollaboratorLoad::name, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        if (overloaded.isEmpty()) {
            System.out.println("No overloaded collaborators found.");
            return;
        }

        for (CollaboratorLoad load : overloaded) {
            System.out.println(
                load.name() + " (" + load.category() + ") - open assignments: "
                    + load.openTaskCount() + ", limit: " + load.limit()
            );
        }
    }

    private static void exportToICalendar(Scanner scanner) {
        System.out.println("\n--- EXPORT TO ICALENDAR ---");
        System.out.println("1. Export a Single Task");
        System.out.println("2. Export All Tasks in a Project");
        System.out.println("3. Export a Filtered Task List");
        System.out.print("Select an option: ");

        List<TaskView> selectedViews;
        switch (scanner.nextLine().trim()) {
            case "1" -> {
                Task task = promptForTask(scanner);
                if (task == null) {
                    return;
                }
                selectedViews = buildTaskViews(buildTaskRecords(List.of(task)));
            }
            case "2" -> {
                String projectName = promptRequired(scanner, "Project name: ");
                List<Task> projectTasks = TASKS.stream()
                    .filter(task -> equalsIgnoreCase(task.getProjectName(), projectName))
                    .collect(Collectors.toList());
                selectedViews = buildTaskViews(buildTaskRecords(projectTasks));
            }
            case "3" -> selectedViews = buildTaskViews(searchTaskRecords(scanner));
            default -> {
                System.out.println("Invalid option.");
                return;
            }
        }

        List<TaskView> eligibleTasks = selectedViews.stream()
            .filter(view -> view.getDueDate() != null)
            .collect(Collectors.toList());

        if (eligibleTasks.isEmpty()) {
            System.out.println("No eligible tasks found. Tasks without due dates are ignored.");
            return;
        }

        Path destinationPath = promptForPath(scanner, "Destination .ics file path: ");
        try {
            ICAL_GATEWAY.exportTasks(eligibleTasks, destinationPath);
            System.out.println("Exported " + eligibleTasks.size() + " calendar entries.");
        } catch (ICalExportException exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private static void importFromCsv(Scanner scanner) {
        System.out.println("\n--- IMPORT FROM CSV ---");
        Path sourcePath = promptForPath(scanner, "Source CSV file path: ");

        try {
            RepositorySnapshot snapshot = new CsvTaskRepository(sourcePath).load();
            if (!validateSnapshot(snapshot)) {
                return;
            }

            TASKS.clear();
            PROJECTS.clear();
            TASKS.addAll(snapshot.getTasks());
            for (Project project : snapshot.getProjects()) {
                PROJECTS.put(Project.key(project.getName()), project);
            }
            persistState();
            System.out.println("Imported " + TASKS.size() + " task(s).");
        } catch (PersistenceException exception) {
            System.out.println("Import failed: " + exception.getMessage());
        }
    }

    private static boolean validateSnapshot(RepositorySnapshot snapshot) {
        List<Task> importedTasks = snapshot.getTasks();
        long openNoDueDateCount = importedTasks.stream()
            .filter(task -> !task.hasDueDate() && isOpenStatus(task.getStatus()))
            .count();
        if (openNoDueDateCount > 50) {
            System.out.println("Import rejected: open tasks without a due date cannot exceed 50.");
            return false;
        }

        Map<String, CollaboratorLoad> importedLoads = new LinkedHashMap<>();
        Set<String> titleDueDates = new LinkedHashSet<>();
        for (Task task : importedTasks) {
            if (task.getSubtasks().size() > 20) {
                System.out.println("Import rejected: a task cannot have more than 20 subtasks.");
                return false;
            }

            for (LocalDate dueDate : task.getOccurrenceDates()) {
                String key = task.getTaskName().trim().toLowerCase(Locale.ROOT) + "|" + dueDate;
                if (!titleDueDates.add(key)) {
                    System.out.println("Import rejected: duplicate task name and due date detected.");
                    return false;
                }
            }

            for (Subtask subtask : task.getSubtasks()) {
                if (subtask.isCollaboratorLinked() && !subtask.isCompleted()) {
                    String key = Collaborator.key(subtask.getCollaboratorName());
                    CollaboratorLoad load = importedLoads.computeIfAbsent(
                        key,
                        ignored -> new CollaboratorLoad(
                            subtask.getCollaboratorName(),
                            subtask.getCollaboratorCategory(),
                            Collaborator.getLimitForCategory(subtask.getCollaboratorCategory())
                        )
                    );
                    load.increment();
                    if (load.limit() != null && load.openTaskCount() > load.limit()) {
                        System.out.println("Import rejected: collaborator overload would be introduced.");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static void exportToCsv(Scanner scanner) {
        System.out.println("\n--- EXPORT TO CSV ---");
        System.out.println("1. Export Entire Database");
        System.out.println("2. Export Filtered Task List");
        System.out.print("Select an option: ");

        List<TaskView> rows;
        switch (scanner.nextLine().trim()) {
            case "1" -> rows = buildTaskViews(buildTaskRecords(TASKS));
            case "2" -> rows = buildTaskViews(searchTaskRecords(scanner));
            default -> {
                System.out.println("Invalid option.");
                return;
            }
        }

        Path destinationPath = promptForPath(scanner, "Destination CSV file path: ");
        try {
            new CsvTaskRepository(destinationPath).save(rows);
            System.out.println("Exported " + rows.size() + " row(s) to CSV.");
        } catch (PersistenceException exception) {
            System.out.println("Export failed: " + exception.getMessage());
        }
    }

    private static List<TaskRecord> buildTaskRecords(List<Task> tasks) {
        List<TaskRecord> records = new ArrayList<>();
        for (Task task : tasks) {
            if (task.isRecurring()) {
                for (LocalDate occurrenceDate : task.getOccurrenceDates()) {
                    records.add(new TaskRecord(task, occurrenceDate, task.getStatusForDate(occurrenceDate)));
                }
            } else if (task.getDueDate() != null) {
                records.add(new TaskRecord(task, task.getDueDate(), task.getStatus()));
            } else {
                records.add(new TaskRecord(task, null, task.getStatus()));
            }
        }
        records.sort(TASK_RECORD_ORDER);
        return records;
    }

    private static List<TaskView> buildTaskViews(List<TaskRecord> records) {
        List<TaskView> views = new ArrayList<>();
        for (TaskRecord record : records) {
            Project project = PROJECTS.get(Project.key(record.task().getProjectName()));
            views.add(
                new TaskView(
                    record.task().getId(),
                    record.task().getTaskName(),
                    record.task().getDescription(),
                    record.task().getSubtaskSummary(),
                    record.status(),
                    record.task().getPriority(),
                    record.dueDate(),
                    record.task().getProjectName(),
                    project == null ? "" : project.getDescription(),
                    record.task().getCollaboratorSummary(),
                    record.task().getCollaboratorCategorySummary()
                )
            );
        }
        return views;
    }

    private static void displayTaskRecords(List<TaskRecord> records) {
        if (records.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }

        System.out.println("----------------------------------------------------------------------------------------------------------------");
        System.out.println("| ID  | Task Name            | Status        | Priority | Due Date   | Project        | Recurring | Collaborator |");
        System.out.println("----------------------------------------------------------------------------------------------------------------");
        for (TaskRecord record : records) {
            Task task = record.task();
            System.out.printf(
                "| %-3d | %-20s | %-13s | %-8s | %-10s | %-14s | %-9s | %-12s |%n",
                task.getId(),
                truncate(task.getTaskName(), 20),
                truncate(record.status(), 13),
                truncate(task.getPriority(), 8),
                record.dueDate() == null ? "" : record.dueDate(),
                truncate(task.getProjectName(), 14),
                task.isRecurring() ? "Yes" : "No",
                truncate(task.getCollaboratorSummary(), 12)
            );
        }
        System.out.println("----------------------------------------------------------------------------------------------------------------");
    }

    private static Project upsertProject(String projectName, String projectDescription) {
        String key = Project.key(projectName);
        Project project = PROJECTS.get(key);
        if (project == null) {
            project = new Project(projectName, projectDescription);
            PROJECTS.put(key, project);
        } else if (!projectDescription.isBlank()) {
            project.setDescription(projectDescription);
        }
        return project;
    }

    private static void linkCollaboratorsDuringCreation(Scanner scanner, Task task, Project project) {
        while (promptOptional(scanner, "Link a collaborator now? (y/n): ").equalsIgnoreCase("y")) {
            if (task.getSubtasks().size() >= 20) {
                System.out.println("Subtask limit reached.");
                return;
            }

            String name = promptRequired(scanner, "Collaborator name: ");
            Collaborator collaborator = project.findCollaborator(name);
            if (collaborator == null) {
                collaborator = project.addOrUpdateCollaborator(name, promptCollaboratorCategory(scanner, null));
            }
            if (!canAssignCollaborator(collaborator)) {
                System.out.println("Assignment rejected because the collaborator limit would be exceeded.");
                continue;
            }
            task.addCollaboratorSubtask(collaborator);
        }
    }

    private static boolean validateTaskConstraints(Task task, int excludedTaskId) {
        if (task.getTaskName().isBlank()) {
            System.out.println("Task title is required.");
            return false;
        }
        if (!isValidPriority(task.getPriority())) {
            System.out.println("Priority must be Low, Medium, or High.");
            return false;
        }
        if (!isValidStatus(task.getStatus())) {
            System.out.println("Status must be Open, In Progress, Completed, or Cancelled.");
            return false;
        }
        if (task.getSubtasks().size() > 20) {
            System.out.println("A task cannot have more than 20 subtasks.");
            return false;
        }
        if (hasTitleDueDateConflict(task, excludedTaskId)) {
            System.out.println("The combination of task name and due date must be unique.");
            return false;
        }
        if (exceedsOpenNoDueDateLimit(task, excludedTaskId)) {
            System.out.println("The number of open tasks without a due date cannot exceed 50.");
            return false;
        }
        return true;
    }

    private static boolean exceedsOpenNoDueDateLimit(Task candidate, int excludedTaskId) {
        if (candidate.hasDueDate() || !isOpenStatus(candidate.getStatus())) {
            return false;
        }

        long count = TASKS.stream()
            .filter(task -> task.getId() != excludedTaskId)
            .filter(task -> !task.hasDueDate() && isOpenStatus(task.getStatus()))
            .count();
        return count >= 50;
    }

    private static boolean hasTitleDueDateConflict(Task candidate, int excludedTaskId) {
        for (LocalDate candidateDate : candidate.getOccurrenceDates()) {
            for (Task existing : TASKS) {
                if (existing.getId() == excludedTaskId) {
                    continue;
                }
                for (LocalDate existingDate : existing.getOccurrenceDates()) {
                    if (candidateDate.equals(existingDate)
                        && existing.getTaskName().equalsIgnoreCase(candidate.getTaskName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean canAssignCollaborator(Collaborator collaborator) {
        Integer limit = Collaborator.getLimitForCategory(collaborator.getCategory());
        if (limit == null || limit <= 0) {
            return false;
        }
        return countOpenAssignmentsForCollaborator(collaborator.getName()) < limit;
    }

    private static int countOpenAssignmentsForCollaborator(String collaboratorName) {
        int count = 0;
        for (Task task : TASKS) {
            for (Subtask subtask : task.getSubtasks()) {
                if (subtask.isCollaboratorLinked()
                    && !subtask.isCompleted()
                    && subtask.getCollaboratorName().equalsIgnoreCase(collaboratorName)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Map<String, CollaboratorLoad> calculateCollaboratorLoads() {
        Map<String, CollaboratorLoad> loads = new LinkedHashMap<>();
        for (Task task : TASKS) {
            for (Subtask subtask : task.getSubtasks()) {
                if (!subtask.isCollaboratorLinked() || subtask.isCompleted()) {
                    continue;
                }

                String key = Collaborator.key(subtask.getCollaboratorName());
                CollaboratorLoad load = loads.computeIfAbsent(
                    key,
                    ignored -> new CollaboratorLoad(
                        subtask.getCollaboratorName(),
                        subtask.getCollaboratorCategory(),
                        Collaborator.getLimitForCategory(subtask.getCollaboratorCategory())
                    )
                );
                load.increment();
            }
        }
        return loads;
    }

    private static boolean hasCollaboratorSubtasks(Task task) {
        return task.getSubtasks().stream().anyMatch(Subtask::isCollaboratorLinked);
    }

    private static boolean hasCollaboratorAssignment(Task task, String collaboratorName) {
        return task.getSubtasks().stream()
            .anyMatch(subtask -> subtask.isCollaboratorLinked()
                && subtask.getCollaboratorName().equalsIgnoreCase(collaboratorName));
    }

    private static Task promptForTask(Scanner scanner) {
        if (TASKS.isEmpty()) {
            System.out.println("There are no tasks to select.");
            return null;
        }

        displayTaskRecords(buildTaskRecords(TASKS));
        System.out.print("Enter task ID: ");
        String input = scanner.nextLine().trim();

        try {
            int taskId = Integer.parseInt(input);
            for (Task task : TASKS) {
                if (task.getId() == taskId) {
                    return task;
                }
            }
            System.out.println("Task not found.");
            return null;
        } catch (NumberFormatException exception) {
            System.out.println("Invalid task ID.");
            return null;
        }
    }

    private static LocalDate promptForOccurrenceDate(Scanner scanner, Task task) {
        if (task.isRecurring()) {
            System.out.println("Occurrences for task " + task.getTaskName() + ":");
            for (LocalDate date : task.getOccurrenceDates()) {
                System.out.println("- " + date + " [" + task.getStatusForDate(date) + "]");
            }
            while (true) {
                LocalDate selectedDate = promptDate(scanner, "Occurrence due date (yyyy-mm-dd)", null, false);
                if (selectedDate == null) {
                    System.out.println("An occurrence due date is required for recurring tasks.");
                    continue;
                }
                if (!task.hasOccurrenceOn(selectedDate)) {
                    System.out.println("That date is not one of the task occurrences.");
                    continue;
                }
                return selectedDate;
            }
        }
        return null;
    }

    private static Subtask promptForSubtask(Scanner scanner, Task task) {
        if (task.getSubtasks().isEmpty()) {
            System.out.println("This task has no subtasks.");
            return null;
        }

        for (Subtask subtask : task.getSubtasks()) {
            System.out.println(subtask.getId() + ". " + subtask.toDisplayString());
        }

        System.out.print("Enter subtask ID: ");
        try {
            int subtaskId = Integer.parseInt(scanner.nextLine().trim());
            Subtask subtask = task.findSubtaskById(subtaskId);
            if (subtask == null) {
                System.out.println("Subtask not found.");
            }
            return subtask;
        } catch (NumberFormatException exception) {
            System.out.println("Invalid subtask ID.");
            return null;
        }
    }

    private static void printSubtasks(Task task) {
        System.out.println("\nTask: " + task.getTaskName());
        if (task.getSubtasks().isEmpty()) {
            System.out.println("No subtasks yet.");
            return;
        }

        for (Subtask subtask : task.getSubtasks()) {
            System.out.println(subtask.getId() + ". " + subtask.toDisplayString());
        }
    }

    private static Path promptForPath(Scanner scanner, String prompt) {
        while (true) {
            String input = promptOptional(scanner, prompt);
            if (input.isBlank()) {
                System.out.println("A file path is required.");
                continue;
            }
            try {
                return Paths.get(stripWrappingQuotes(input)).normalize();
            } catch (Exception exception) {
                System.out.println("Invalid path: " + exception.getMessage());
            }
        }
    }

    private static RecurrencePattern promptRecurrencePattern(Scanner scanner) {
        String recurring = promptOptional(scanner, "Recurring task? (y/n): ");
        if (!recurring.equalsIgnoreCase("y")) {
            return null;
        }
        return promptRecurringPatternRequired(scanner);
    }

    private static RecurrencePattern promptRecurringPatternRequired(Scanner scanner) {
        while (true) {
            try {
                String typeInput = promptRequired(scanner, "Recurrence type (Daily, Weekly, Monthly, Custom): ");
                RecurrenceType type = RecurrenceType.valueOf(typeInput.trim().toUpperCase(Locale.ROOT));
                int interval = promptPositiveInteger(scanner, "Recurrence interval (positive integer): ");
                LocalDate startDate = promptDate(scanner, "Recurrence start date (yyyy-mm-dd)", null, false);
                LocalDate endDate = promptDate(scanner, "Recurrence end date (yyyy-mm-dd)", null, false);

                Set<DayOfWeek> weekdays = new LinkedHashSet<>();
                Integer dayOfMonth = null;
                if (type == RecurrenceType.WEEKLY) {
                    weekdays = promptWeekdays(scanner);
                } else if (type == RecurrenceType.MONTHLY) {
                    dayOfMonth = promptPositiveInteger(scanner, "Day of month (1-31): ");
                }

                return new RecurrencePattern(type, interval, startDate, endDate, weekdays, dayOfMonth);
            } catch (IllegalArgumentException exception) {
                System.out.println("Invalid recurrence pattern: " + exception.getMessage());
            }
        }
    }

    private static Set<DayOfWeek> promptWeekdays(Scanner scanner) {
        while (true) {
            String input = promptRequired(scanner, "Weekdays (e.g. MONDAY,WEDNESDAY): ");
            try {
                Set<DayOfWeek> weekdays = new LinkedHashSet<>();
                for (String token : input.split(",")) {
                    weekdays.add(DayOfWeek.valueOf(token.trim().toUpperCase(Locale.ROOT)));
                }
                if (!weekdays.isEmpty()) {
                    return weekdays;
                }
            } catch (IllegalArgumentException exception) {
                // keep looping
            }
            System.out.println("Enter valid weekday names separated by commas.");
        }
    }

    private static int promptPositiveInteger(Scanner scanner, String prompt) {
        while (true) {
            String input = promptRequired(scanner, prompt);
            try {
                int value = Integer.parseInt(input);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException exception) {
                // keep looping
            }
            System.out.println("Enter a positive integer.");
        }
    }

    private static DayOfWeek promptDayOfWeek(Scanner scanner) {
        String input = promptOptional(scanner, "Day of week (optional, e.g. MONDAY): ");
        if (input.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            System.out.println("Invalid day of week. The filter will be ignored.");
            return null;
        }
    }

    private static LocalDate promptDate(Scanner scanner, String prompt, LocalDate currentValue, boolean optional) {
        while (true) {
            String suffix = currentValue == null ? "" : " [" + currentValue + "]";
            String input = promptOptional(scanner, prompt + suffix + ": ");
            if (input.isBlank()) {
                if (optional) {
                    return currentValue;
                }
                System.out.println("A date is required.");
                continue;
            }
            if (optional && input.equalsIgnoreCase("none")) {
                return null;
            }
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException exception) {
                System.out.println("Use yyyy-mm-dd.");
            }
        }
    }

    private static String promptPriority(Scanner scanner, String currentValue) {
        while (true) {
            String label = currentValue == null
                ? "Priority (Low, Medium, High): "
                : "Priority [" + currentValue + "] (Low, Medium, High): ";
            String input = promptOptional(scanner, label);
            if (input.isBlank() && currentValue != null) {
                return currentValue;
            }
            if (isValidPriority(input)) {
                return toDisplayCase(input);
            }
            System.out.println("Priority must be Low, Medium, or High.");
        }
    }

    private static String promptStatus(Scanner scanner, String currentValue, boolean allowBlank) {
        while (true) {
            String label = currentValue == null
                ? "Status (Open, In Progress, Completed, Cancelled): "
                : "Status [" + currentValue + "] (Open, In Progress, Completed, Cancelled): ";
            String input = promptOptional(scanner, label);
            if (input.isBlank() && allowBlank && currentValue != null) {
                return currentValue;
            }
            if (isValidStatus(input)) {
                return toDisplayCase(input);
            }
            System.out.println("Status must be Open, In Progress, Completed, or Cancelled.");
        }
    }

    private static String promptCollaboratorCategory(Scanner scanner, String currentValue) {
        while (true) {
            String label = currentValue == null
                ? "Collaborator category (Junior, Intermediate, Senior): "
                : "Collaborator category [" + currentValue + "] (Junior, Intermediate, Senior): ";
            String input = promptOptional(scanner, label);
            if (input.isBlank() && currentValue != null) {
                return currentValue;
            }
            try {
                return Collaborator.normalizeCategory(input);
            } catch (IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    private static String promptRequired(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isBlank()) {
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
        return value.isBlank() ? blankWhenMissing(currentValue) : value;
    }

    private static int nextTaskId() {
        int max = 0;
        for (Task task : TASKS) {
            max = Math.max(max, task.getId());
        }
        return max + 1;
    }

    private static boolean isValidPriority(String value) {
        return equalsIgnoreCase(value, "Low") || equalsIgnoreCase(value, "Medium") || equalsIgnoreCase(value, "High");
    }

    private static boolean isValidStatus(String value) {
        return equalsIgnoreCase(value, "Open")
            || equalsIgnoreCase(value, "In Progress")
            || equalsIgnoreCase(value, "Completed")
            || equalsIgnoreCase(value, "Cancelled");
    }

    private static boolean isOpenStatus(String value) {
        return !equalsIgnoreCase(value, "Completed") && !equalsIgnoreCase(value, "Cancelled");
    }

    private static boolean containsIgnoreCase(String value, String searchText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private static String toDisplayCase(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("in progress")) {
            return "In Progress";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private static String blankWhenMissing(String value) {
        return value == null ? "" : value;
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

    private record TaskRecord(Task task, LocalDate dueDate, String status) { }

    private static final class SearchCriteria {
        private String keyword = "";
        private String status = "";
        private String priority = "";
        private String projectName = "";
        private String collaborator = "";
        private String collaboratorCategory = "";
        private String tag = "";
        private DayOfWeek dayOfWeek;
        private String period = "";
        private LocalDate dueDateFrom;
        private LocalDate dueDateTo;

        private boolean isEmpty() {
            return keyword.isBlank()
                && status.isBlank()
                && priority.isBlank()
                && projectName.isBlank()
                && collaborator.isBlank()
                && collaboratorCategory.isBlank()
                && tag.isBlank()
                && dayOfWeek == null
                && period.isBlank();
        }
    }

    private static final class CollaboratorLoad {
        private final String name;
        private final String category;
        private final Integer limit;
        private int openTaskCount;

        private CollaboratorLoad(String name, String category, Integer limit) {
            this.name = name;
            this.category = category;
            this.limit = limit;
        }

        private void increment() {
            openTaskCount++;
        }

        private String name() {
            return name;
        }

        private String category() {
            return category;
        }

        private Integer limit() {
            return limit;
        }

        private int openTaskCount() {
            return openTaskCount;
        }
    }
}
