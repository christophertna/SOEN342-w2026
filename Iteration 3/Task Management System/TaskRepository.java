import java.util.List;

public interface TaskRepository {

    RepositorySnapshot load() throws PersistenceException;

    void save(List<Task> tasks) throws PersistenceException;
}
