import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FileOrganizer {
    private static final Map<String, String> FILE_TYPES = new HashMap<>();
    private static final Set<String> TEMP_EXTENSIONS = new HashSet<>(
            Arrays.asList("crdownload", "part", "tmp", "download", "partial", "temp")
    );
    private static final boolean MOVE_FOLDERS = true;
    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_MS = 3000;

    private final Path targetFolder;
    private final Consumer<String> logger;
    private ExecutorService executor;

    static {
        FILE_TYPES.put("jpg", "Images");
        FILE_TYPES.put("jpeg", "Images");
        FILE_TYPES.put("png", "Images");
        FILE_TYPES.put("gif", "Images");
        FILE_TYPES.put("bmp", "Images");
        FILE_TYPES.put("mp3", "Music");
        FILE_TYPES.put("wav", "Music");
        FILE_TYPES.put("flac", "Music");
        FILE_TYPES.put("txt", "Documents");
        FILE_TYPES.put("pdf", "Documents");
        FILE_TYPES.put("doc", "Documents");
        FILE_TYPES.put("docx", "Documents");
        FILE_TYPES.put("xls", "Spreadsheets");
        FILE_TYPES.put("xlsx", "Spreadsheets");
        FILE_TYPES.put("ppt", "Presentations");
        FILE_TYPES.put("pptx", "Presentations");
        FILE_TYPES.put("exe", "Executables");
        FILE_TYPES.put("mp4", "Videos");
        FILE_TYPES.put("zip", "Archives");
    }

    public FileOrganizer(File folder, Consumer<String> logger) {
        this.logger = logger;
        this.targetFolder = folder.toPath();
        createSystemFolders();
    }

    private void createSystemFolders() {
        createFolder(targetFolder.toFile(), "Folders");
        createFolder(targetFolder.toFile(), "Uncategorized");
    }

    public void startMonitoring() {
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                organizeFiles(targetFolder.toFile());
                startWatching(targetFolder);
            } catch (IOException e) {
                logger.accept("Monitoring error: " + e.getMessage());
            }
        });
    }

    private void startWatching(Path path) throws IOException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path filePath = ((WatchEvent<Path>) event).context();
                    Path fullPath = path.resolve(filePath);

                    // Handle both files and folders
                    if (Files.isDirectory(fullPath)) {
                        // Wait briefly to ensure folder is fully moved
                        Thread.sleep(1000);
                        organizeFiles(path.toFile());
                    } else if (!isTemporaryFile(fullPath) && isFileReady(fullPath)) {
                        organizeFiles(path.toFile());
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTemporaryFile(Path path) {
        String ext = getFileExtension(path.getFileName().toString()).toLowerCase();
        return TEMP_EXTENSIONS.contains(ext);
    }

    private boolean isFileReady(Path path) {
        try {
            if (Files.isDirectory(path) || isTemporaryFile(path)) return false;
            long size1 = Files.size(path);
            Thread.sleep(500);
            return size1 == Files.size(path);
        } catch (Exception e) {
            return false;
        }
    }

    private void organizeFiles(File folder) {
        File[] items = folder.listFiles();
        if (items == null) return;

        // Process folders first
        File foldersDir = new File(folder, "Folders");
        Set<String> protectedFolders = new HashSet<>(FILE_TYPES.values());
        protectedFolders.addAll(Arrays.asList("Folders", "Uncategorized"));

        Arrays.stream(items)
                .filter(File::isDirectory)
                .filter(dir -> !protectedFolders.contains(dir.getName()))
                .forEach(dir -> moveFolder(dir, foldersDir));

        // Process files
        Arrays.stream(items)
                .filter(File::isFile)
                .filter(file -> !isTemporaryFile(file.toPath()))
                .forEach(this::processFile);
    }

    private void processFile(File file) {
        String ext = getFileExtension(file.getName()).toLowerCase();
        String category = FILE_TYPES.getOrDefault(ext, "Uncategorized");
        File targetDir = createFolder(targetFolder.toFile(), category);
        moveFileWithRetry(file.toPath(), targetDir.toPath().resolve(file.getName()));
    }

    private File createFolder(File parent, String name) {
        File folder = new File(parent, name);
        if (!folder.exists() && !folder.mkdir()) {
            logger.accept("Failed to create directory: " + name);
        }
        return folder;
    }

    private void moveFolder(File source, File targetDir) {
        try {
            Path target = targetDir.toPath().resolve(source.getName());
            Files.move(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            logger.accept("Moved folder: " + source.getName());
        } catch (IOException e) {
            logger.accept("Folder move failed: " + source.getName() + " - " + e.getMessage());
        }
    }

    private void moveFileWithRetry(Path source, Path target) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                if (isFileReady(source)) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.accept("Moved file: " + source.getFileName());
                    return;
                }
                attempts++;
                Thread.sleep(RETRY_DELAY_MS);
            } catch (Exception e) {
                attempts++;
                logger.accept("Retrying (" + attempts + "/" + MAX_RETRIES + ") for: " + source.getFileName());
            }
        }
        logger.accept("Failed to move file after " + MAX_RETRIES + " attempts: " + source.getFileName());
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }

    public void stopMonitoring() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}