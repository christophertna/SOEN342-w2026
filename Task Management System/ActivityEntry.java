import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityEntry implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DISPLAY_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalDateTime timestamp;
    private final String description;

    public ActivityEntry(LocalDateTime timestamp, String description) {
        this.timestamp = timestamp;
        this.description = description == null ? "" : description.trim();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public String toDisplayString() {
        return timestamp.format(DISPLAY_FORMAT) + " - " + description;
    }
}
