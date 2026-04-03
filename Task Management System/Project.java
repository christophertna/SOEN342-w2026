import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private String description;
    private final Map<String, Collaborator> collaborators;

    public Project(String name, String description) {
        this(name, description, new LinkedHashMap<>());
    }

    public Project(String name, String description, Map<String, Collaborator> collaborators) {
        this.name = normalize(name);
        this.description = normalize(description);
        this.collaborators = new LinkedHashMap<>(collaborators);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = normalize(description);
    }

    public Collaborator addOrUpdateCollaborator(String name, String category) {
        String key = Collaborator.key(name);
        Collaborator collaborator = collaborators.get(key);
        if (collaborator == null) {
            collaborator = new Collaborator(name, category);
            collaborators.put(key, collaborator);
        } else if (!normalize(category).isEmpty()) {
            collaborator.setCategory(category);
        }
        return collaborator;
    }

    public Collaborator findCollaborator(String name) {
        return collaborators.get(Collaborator.key(name));
    }

    public List<Collaborator> getCollaborators() {
        return Collections.unmodifiableList(new ArrayList<>(collaborators.values()));
    }

    public static String key(String projectName) {
        return normalize(projectName).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
