import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.nio.file.AccessDeniedException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class FileOrganizer {

    private static final Map<String, String> fileTypes = new HashMap<>();
    private static final boolean MOVE_FOLDERS = true; // Set to false if you don't want to move folders
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000; // 1 seconds
    private static final int MOVE_DELAY_MS = 500; // 0.5 seconds
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Object fileLock = new Object();

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
        // Add more file types and their respective folders as needed
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            String folderPath = args[0];
            System.out.println("Monitoring folder: " + folderPath);
            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                System.out.println("Invalid folder path.");
                return;
            }

            executor.submit(() -> startWatching(folder.toPath()));

            organizeFiles(folder);
        }
    }

    private static void startWatching(Path path) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_CREATE);

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        System.out.println("Detected new file or folder: " + fileName);
                        organizeFiles(path.toFile());
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error setting up watch service.");
            e.printStackTrace();
        }
    }

    private static void organizeFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files to organize.");
            return;
        }

        File foldersDir = null;
        if (MOVE_FOLDERS) {
            foldersDir = new File(folder, "Folders");
            if (!foldersDir.exists()) {
                foldersDir.mkdir();
            }
        }

        // Create a set of folder names that should be skipped
        Set<String> skipFolders = new HashSet<>(fileTypes.values());
        skipFolders.add("Folders");

        // Move directories first
        for (File file : files) {
            if (file.isDirectory() && MOVE_FOLDERS && !skipFolders.contains(file.getName())) {
                try {
                    moveFileWithRetry(file.toPath(), new File(foldersDir, file.getName()).toPath());
                    System.out.println("Moved folder: " + file.getName() + " to folder: Folders");
                } catch (IOException e) {
                    System.out.println("Failed to move folder: " + file.getName());
                    e.printStackTrace();
                }
            }
        }

        // Move files second
        for (File file : files) {
            if (file.isFile()) {
                String fileExtension = getFileExtension(file.getName());
                String folderName = fileTypes.get(fileExtension.toLowerCase());

                if (folderName != null) {
                    File targetFolder = new File(folder, folderName);
                    if (!targetFolder.exists()) {
                        targetFolder.mkdir();
                    }

                    try {
                        moveFileWithDelay(file.toPath(), new File(targetFolder, file.getName()).toPath());
                        System.out.println("Moved file: " + file.getName() + " to folder: " + folderName);
                    } catch (IOException e) {
                        System.out.println("Failed to move file: " + file.getName());
                        e.printStackTrace();
                    }
                } else if ("zip".equalsIgnoreCase(fileExtension) && MOVE_FOLDERS) {
                    try {
                        moveFileWithRetry(file.toPath(), new File(foldersDir, file.getName()).toPath());
                        System.out.println("Moved zip file: " + file.getName() + " to folder: Folders");
                    } catch (IOException e) {
                        System.out.println("Failed to move zip file: " + file.getName());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return "";
        }
        return fileName.substring(lastIndexOfDot + 1);
    }

    private static void moveFileWithRetry(Path source, Path target) throws IOException {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException e) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    throw new IOException("Failed to move file after " + MAX_RETRIES + " attempts", e);
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to retry file move", ie);
                }
            }
        }
    }

    private static void moveFileWithDelay(Path source, Path target) throws IOException {
        try {
            Thread.sleep(MOVE_DELAY_MS);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to move file", e);
        }
    }
}
