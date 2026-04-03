import java.nio.file.Path;
import java.util.List;

public interface ICalGateway {

    void exportTasks(List<TaskView> tasks, Path destinationPath) throws ICalExportException;
}
