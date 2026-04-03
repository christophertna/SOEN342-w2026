import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepositorySnapshot {

    private final List<Task> tasks;
    private final List<Project> projects;

    public RepositorySnapshot(List<Task> tasks, List<Project> projects) {
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.projects = Collections.unmodifiableList(new ArrayList<>(projects));
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<Project> getProjects() {
        return projects;
    }
}
