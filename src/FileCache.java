import java.util.HashMap;
import java.util.Map;

public class FileCache {
    private Map<String, String> cache = new HashMap<>();
    private Map<String, Boolean> modified = new HashMap<>();
    private Map<String, String> mode = new HashMap<>();

    public void cacheFile(String filePath, String content) {
        System.out.println("Caching file: " + filePath + " with content: " + content);
        cache.put(filePath, content);
        modified.put(filePath, false);
    }


    public boolean isCached(String filePath) {
        return cache.containsKey(filePath);
    }

    public void setFileMode(String filePath, String mode) {
        this.mode.put(filePath, mode);
    }

    public String read(String filePath) {
       return cache.get(filePath);
    }

    public void write(String filePath, String content) {
        cache.put(filePath, content);
        modified.put(filePath, true);
    }

    public boolean isModified(String filePath) {
        return modified.getOrDefault(filePath, false);
    }

    public void remove(String filePath) {
        cache.remove(filePath);
        modified.remove(filePath);
        mode.remove(filePath);
    }
}