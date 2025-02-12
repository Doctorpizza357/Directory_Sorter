import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FileOrganizer {

    private static final Map<String, String> fileTypes = new HashMap<>();
    private static final boolean MOVE_FOLDERS = true;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MOVE_DELAY_MS = 500;

    private final Path targetFolder;
    private final Path uncategorizedFolder;
    private ExecutorService executor;
    private final Consumer<String> logger;

    static {
        fileTypes.put("jpg", "Images");
        fileTypes.put("jpeg", "Images");
        fileTypes.put("png", "Images");
        fileTypes.put("gif", "Images");
        fileTypes.put("bmp", "Images");
        fileTypes.put("mp3", "Music");
        fileTypes.put("wav", "Music");
        fileTypes.put("flac", "Music");
        fileTypes.put("txt", "Documents");
        fileTypes.put("pdf", "Documents");
        fileTypes.put("doc", "Documents");
        fileTypes.put("docx", "Documents");
        fileTypes.put("xls", "Spreadsheets");
        fileTypes.put("xlsx", "Spreadsheets");
        fileTypes.put("ppt", "Presentations");
        fileTypes.put("pptx", "Presentations");
        fileTypes.put("exe", "Executables");
        fileTypes.put("mp4", "Videos");
        fileTypes.put("zip", "Archives");
    }

    public FileOrganizer(File folder, Consumer<String> logger) {
        this.logger = logger;
        this.targetFolder = folder.toPath();
        this.uncategorizedFolder = targetFolder.resolve("Uncategorized");
        createUncategorizedFolder();
    }

    private void createUncategorizedFolder() {
        try {
            Files.createDirectories(uncategorizedFolder);
        } catch (IOException e) {
            logger.accept("Couldn't create uncategorized folder: " + e.getMessage());
        }
    }

    public void startMonitoring() {
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                organizeFiles(targetFolder.toFile()); // Organize existing files first
                startWatching(targetFolder);
            } catch (IOException e) {
                logger.accept("Monitoring error: " + e.getMessage());
            }
        });
    }

    public void stopMonitoring() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void startWatching(Path path) throws IOException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        logger.accept("Detected new file/folder");
                        organizeFiles(path.toFile());
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        }
    }

    private void organizeFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            logger.accept("No files to organize");
            return;
        }

        File foldersDir = null;
        if (MOVE_FOLDERS) {
            foldersDir = new File(folder, "Folders");
            if (!foldersDir.exists() && !foldersDir.mkdir()) {
                logger.accept("Failed to create Folders directory");
            }
        }

        Set<String> skipFolders = new HashSet<>(fileTypes.values());
        skipFolders.add("Folders");
        skipFolders.add("Uncategorized");

        // Process directories
        for (File file : files) {
            if (file.isDirectory() && MOVE_FOLDERS && !skipFolders.contains(file.getName())) {
                try {
                    moveFileWithRetry(file.toPath(), new File(foldersDir, file.getName()).toPath());
                    logger.accept("Moved folder: " + file.getName() + " to Folders");
                } catch (IOException e) {
                    logger.accept("Failed to move folder: " + file.getName() + " - " + e.getMessage());
                }
            }
        }

        // Process files
        for (File file : files) {
            if (file.isFile()) {
                String ext = getFileExtension(file.getName()).toLowerCase();
                String category = fileTypes.getOrDefault(ext, "Uncategorized");

                File targetDir = new File(folder, category);
                if (!targetDir.exists() && !targetDir.mkdir()) {
                    logger.accept("Failed to create directory: " + category);
                }

                try {
                    moveFileWithDelay(file.toPath(), new File(targetDir, file.getName()).toPath());
                    logger.accept("Moved file: " + file.getName() + " to " + category);
                } catch (IOException e) {
                    logger.accept("Failed to move file: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }

    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }

    private void moveFileWithRetry(Path source, Path target) throws IOException {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException e) {
                if (++attempts >= MAX_RETRIES) throw e;
                try { Thread.sleep(RETRY_DELAY_MS); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private void moveFileWithDelay(Path source, Path target) throws IOException {
        try {
            Thread.sleep(MOVE_DELAY_MS);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}