import java.io.Serializable;

public class Subtask implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private String title;
    private boolean completed;
    private String collaboratorName;
    private String collaboratorCategory;

    public Subtask(int id, String title, boolean completed) {
        this(id, title, completed, "", "");
    }

    public Subtask(int id, String title, boolean completed, String collaboratorName, String collaboratorCategory) {
        this.id = id;
        this.title = title == null ? "" : title.trim();
        this.completed = completed;
        this.collaboratorName = collaboratorName == null ? "" : collaboratorName.trim();
        this.collaboratorCategory = collaboratorCategory == null ? "" : collaboratorCategory.trim();
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getCollaboratorName() {
        return collaboratorName;
    }

    public String getCollaboratorCategory() {
        return collaboratorCategory;
    }

    public boolean isCollaboratorLinked() {
        return !collaboratorName.isEmpty();
    }

    public String toDisplayString() {
        String display = (completed ? "[x] " : "[ ] ") + title;
        if (isCollaboratorLinked()) {
            display += " -> " + collaboratorName + " (" + collaboratorCategory + ")";
        }
        return display;
    }

    public static Subtask fromStorageString(int id, String storedValue) {
        if (storedValue.startsWith("[x] ")) {
            return new Subtask(id, storedValue.substring(4), true);
        }
        if (storedValue.startsWith("[ ] ")) {
            return new Subtask(id, storedValue.substring(4), false);
        }
        return new Subtask(id, storedValue, false);
    }
}
