public interface TaskRepository {

    RepositorySnapshot load() throws PersistenceException;

    void save(RepositorySnapshot snapshot) throws PersistenceException;
}
