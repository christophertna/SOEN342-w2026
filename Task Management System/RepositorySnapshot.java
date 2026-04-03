import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepositorySnapshot {

    private final List<Task> tasks;

    public RepositorySnapshot(List<Task> tasks) {
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
    }

    public List<Task> getTasks() {
        return tasks;
    }
}
