import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileTaskRepository implements TaskRepository {

    private static final String HEADER = "PTMS-DATA-V1";

    private final Path dataPath;

    public FileTaskRepository(Path dataPath) {
        this.dataPath = dataPath;
    }

    @Override
    public RepositorySnapshot load() throws PersistenceException {
        if (!Files.exists(dataPath)) {
            return new RepositorySnapshot(new ArrayList<>(), new ArrayList<>());
        }

        try {
            List<String> lines = Files.readAllLines(dataPath);
            if (lines.isEmpty()) {
                return new RepositorySnapshot(new ArrayList<>(), new ArrayList<>());
            }

            if (CsvTaskRepository.isCsvHeader(lines.get(0))) {
                return new CsvTaskRepository(dataPath).load();
            }

            if (!HEADER.equals(lines.get(0).trim())) {
                throw new PersistenceException("Unsupported data file format.");
            }

            Map<Integer, Task> tasksById = new LinkedHashMap<>();
            Map<String, Project> projects = new LinkedHashMap<>();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                switch (parts[0]) {
                    case "PROJECT" -> {
                        Project project = new Project(decode(parts[1]), decode(parts[2]));
                        projects.put(Project.key(project.getName()), project);
                    }
                    case "COLLABORATOR" -> {
                        Project project = projects.computeIfAbsent(
                            Project.key(decode(parts[1])),
                            key -> new Project(decode(parts[1]), "")
                        );
                        project.addOrUpdateCollaborator(decode(parts[2]), decode(parts[3]));
                    }
                    case "TASK" -> {
                        RecurrencePattern recurrencePattern = parseRecurrence(parts);
                        Task task = new Task(
                            Integer.parseInt(parts[1]),
                            decode(parts[2]),
                            decode(parts[3]),
                            LocalDateTime.parse(parts[4]),
                            decode(parts[5]),
                            decode(parts[6]),
                            parseDate(parts[7]),
                            recurrencePattern,
                            decode(parts[8]),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new LinkedHashMap<>()
                        );
                        tasksById.put(task.getId(), task);
                    }
                    case "SUBTASK" -> {
                        Task task = requireTask(tasksById, parts[1]);
                        task.addLoadedSubtask(
                            new Subtask(
                                Integer.parseInt(parts[2]),
                                decode(parts[3]),
                                Boolean.parseBoolean(parts[4]),
                                decode(parts[5]),
                                decode(parts[6])
                            )
                        );
                    }
                    case "TAG" -> requireTask(tasksById, parts[1]).addTag(decode(parts[2]));
                    case "ACTIVITY" -> requireTask(tasksById, parts[1]).addLoadedActivity(
                        new ActivityEntry(LocalDateTime.parse(parts[2]), decode(parts[3]))
                    );
                    case "OCCURRENCE" -> requireTask(tasksById, parts[1]).setStatusForDate(LocalDate.parse(parts[2]), decode(parts[3]));
                    default -> throw new PersistenceException("Unknown record type: " + parts[0]);
                }
            }

            return new RepositorySnapshot(new ArrayList<>(tasksById.values()), new ArrayList<>(projects.values()));
        } catch (IOException exception) {
            throw new PersistenceException("Load failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void save(RepositorySnapshot snapshot) throws PersistenceException {
        try {
            if (dataPath.getParent() != null) {
                Files.createDirectories(dataPath.getParent());
            }

            List<String> lines = new ArrayList<>();
            lines.add(HEADER);

            for (Project project : snapshot.getProjects()) {
                lines.add("PROJECT|" + encode(project.getName()) + "|" + encode(project.getDescription()));
                for (Collaborator collaborator : project.getCollaborators()) {
                    lines.add(
                        "COLLABORATOR|"
                            + encode(project.getName()) + "|"
                            + encode(collaborator.getName()) + "|"
                            + encode(collaborator.getCategory())
                    );
                }
            }

            for (Task task : snapshot.getTasks()) {
                lines.add(taskLine(task));
                for (Subtask subtask : task.getSubtasks()) {
                    lines.add(
                        "SUBTASK|"
                            + task.getId() + "|"
                            + subtask.getId() + "|"
                            + encode(subtask.getTitle()) + "|"
                            + subtask.isCompleted() + "|"
                            + encode(subtask.getCollaboratorName()) + "|"
                            + encode(subtask.getCollaboratorCategory())
                    );
                }
                for (String tag : task.getTags()) {
                    lines.add("TAG|" + task.getId() + "|" + encode(tag));
                }
                for (ActivityEntry activityEntry : task.getActivityHistory()) {
                    lines.add(
                        "ACTIVITY|"
                            + task.getId() + "|"
                            + activityEntry.getTimestamp() + "|"
                            + encode(activityEntry.getDescription())
                    );
                }
                for (Map.Entry<LocalDate, String> entry : task.getOccurrenceStatuses().entrySet()) {
                    lines.add(
                        "OCCURRENCE|"
                            + task.getId() + "|"
                            + entry.getKey() + "|"
                            + encode(entry.getValue())
                    );
                }
            }

            Files.write(dataPath, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PersistenceException("Save failed: " + exception.getMessage(), exception);
        }
    }

    private static String taskLine(Task task) {
        RecurrencePattern recurrencePattern = task.getRecurrencePattern();
        String recurrenceType = recurrencePattern == null ? "" : recurrencePattern.getType().name();
        String interval = recurrencePattern == null ? "" : Integer.toString(recurrencePattern.getInterval());
        String startDate = recurrencePattern == null ? "" : recurrencePattern.getStartDate().toString();
        String endDate = recurrencePattern == null ? "" : recurrencePattern.getEndDate().toString();
        String weekdays = recurrencePattern == null
            ? ""
            : recurrencePattern.getWeekdays().stream().map(DayOfWeek::name).reduce((left, right) -> left + "," + right).orElse("");
        String dayOfMonth = recurrencePattern == null || recurrencePattern.getDayOfMonth() == null
            ? ""
            : recurrencePattern.getDayOfMonth().toString();

        return "TASK|"
            + task.getId() + "|"
            + encode(task.getTaskName()) + "|"
            + encode(task.getDescription()) + "|"
            + task.getCreationTimestamp() + "|"
            + encode(task.getPriority()) + "|"
            + encode(task.getStatus()) + "|"
            + (task.getDueDate() == null ? "" : task.getDueDate()) + "|"
            + encode(task.getProjectName()) + "|"
            + recurrenceType + "|"
            + interval + "|"
            + startDate + "|"
            + endDate + "|"
            + weekdays + "|"
            + dayOfMonth;
    }

    private static RecurrencePattern parseRecurrence(String[] parts) {
        if (parts.length < 15 || parts[9].isEmpty()) {
            return null;
        }

        Set<DayOfWeek> weekdays = new LinkedHashSet<>();
        if (!parts[13].isEmpty()) {
            for (String value : parts[13].split(",")) {
                weekdays.add(DayOfWeek.valueOf(value));
            }
        }

        Integer dayOfMonth = parts[14].isEmpty() ? null : Integer.parseInt(parts[14]);
        return new RecurrencePattern(
            RecurrenceType.valueOf(parts[9]),
            Integer.parseInt(parts[10]),
            LocalDate.parse(parts[11]),
            LocalDate.parse(parts[12]),
            weekdays,
            dayOfMonth
        );
    }

    private static Task requireTask(Map<Integer, Task> tasksById, String rawTaskId) {
        int taskId = Integer.parseInt(rawTaskId);
        Task task = tasksById.get(taskId);
        if (task == null) {
            throw new PersistenceException("Referenced task " + taskId + " was not found while loading data.");
        }
        return task;
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
