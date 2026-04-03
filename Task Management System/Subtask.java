public class Subtask {

    private String title;
    private boolean completed;

    public Subtask(String title, boolean completed) {
        this.title = title == null ? "" : title.trim();
        this.completed = completed;
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

    public String toDisplayString() {
        return (completed ? "[x] " : "[ ] ") + title;
    }

    public String toStorageString() {
        return toDisplayString();
    }

    public static Subtask fromStorageString(String storedValue) {
        if (storedValue.startsWith("[x] ")) {
            return new Subtask(storedValue.substring(4), true);
        }
        if (storedValue.startsWith("[ ] ")) {
            return new Subtask(storedValue.substring(4), false);
        }
        return new Subtask(storedValue, false);
    }
}
