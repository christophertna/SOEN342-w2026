import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class Collaborator implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Map<String, Integer> CATEGORY_LIMITS = createCategoryLimits();

    private String name;
    private String category;

    public Collaborator(String name, String category) {
        this.name = normalizeName(name);
        this.category = normalizeCategory(category);
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = normalizeCategory(category);
    }

    public int getLimit() {
        Integer limit = getLimitForCategory(category);
        if (limit == null) {
            throw new IllegalStateException("Unknown collaborator category: " + category);
        }
        return limit;
    }

    public static Integer getLimitForCategory(String category) {
        if (category == null) {
            return null;
        }
        return CATEGORY_LIMITS.get(category.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isValidCategory(String category) {
        return getLimitForCategory(category) != null;
    }

    public static String key(String name) {
        return normalizeName(name).toLowerCase(Locale.ROOT);
    }

    public static String normalizeCategory(String category) {
        String normalized = normalizeName(category).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.equals("intermediate")) {
            return "Intermediate";
        }
        if (normalized.equals("junior")) {
            return "Junior";
        }
        if (normalized.equals("senior")) {
            return "Senior";
        }
        throw new IllegalArgumentException("Category must be Junior, Intermediate, or Senior.");
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Integer> createCategoryLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("junior", 10);
        limits.put("intermediate", 5);
        limits.put("senior", 2);
        return limits;
    }
}
