import java.util.*;
import java.util.stream.Collectors;

public class TaskSystem {

    // Array list for now, database implementation later
    private static List<Task> taskDatabase = new ArrayList<>();

    public static void main(String[] args) {
        seedData(); // Populate with some initial tasks
        Scanner scanner = new Scanner(System.in);

        // Main control loop
        while (true) {
            System.out.println("\n--- TASK SEARCH SYSTEM ---");
            System.out.println("1. View All Tasks");
            System.out.println("2. Search Tasks by Criteria");
            System.out.println("3. Exit");
            System.out.print("Select an option: ");
            
            String choice = scanner.nextLine();

            if (choice.equals("1")) {
                displayTasks(taskDatabase);
            } else if (choice.equals("2")) {
                performSearch(scanner);
            } else if (choice.equals("3")) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Invalid option. Try again.");
            }
        }
    }

    // User input
    private static void performSearch(Scanner scanner) {
        System.out.print("Enter Keyword (or leave blank): ");
        String keyword = scanner.nextLine().toLowerCase();

        System.out.print("Filter by Status (Pending/Completed or leave blank): ");
        String status = scanner.nextLine().toLowerCase();

        System.out.print("Filter by Priority (High/Low or leave blank): ");
        String priority = scanner.nextLine().toLowerCase();

        // The Search Logic using Java Streams
        List<Task> results = taskDatabase.stream()
            .filter(t -> keyword.isEmpty() || t.getTitle().contains(keyword))
            .filter(t -> status.isEmpty() || t.getStatus().equals(status))
            .filter(t -> priority.isEmpty() || t.getPriority().equals(priority))
            .collect(Collectors.toList());

        System.out.println("\n--- SEARCH RESULTS ---");
        displayTasks(results);
    }

    private static void displayTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }
        System.out.println("---------------------------------------------------------");
        System.out.println("| ID  | Title                | Status       | Priority   |");
        System.out.println("---------------------------------------------------------");
        tasks.forEach(System.out::println);
        System.out.println("---------------------------------------------------------");
    }

    private static void seedData() {
        taskDatabase.add(new Task(1, "Database Setup", "Completed", "High"));
        taskDatabase.add(new Task(2, "UI Wireframes", "Pending", "Medium"));
        taskDatabase.add(new Task(3, "API Integration", "Pending", "High"));
        taskDatabase.add(new Task(4, "User Testing", "Pending", "Low"));
        taskDatabase.add(new Task(5, "Bug Squashing", "Completed", "High"));
    }
}