import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class FileOrganizerGUI extends JFrame {
    private JButton selectFolderButton;
    private JButton toggleButton;
    private JTextArea logArea;
    private JCheckBox autoStartCheckbox;
    private FileOrganizer organizer;
    private boolean isMonitoring = false;
    private File defaultFolder;

    public FileOrganizerGUI() {
        setTitle("Auto File Organizer");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Set default folder to Downloads
        String userHome = System.getProperty("user.home");
        defaultFolder = new File(userHome, "Downloads");
        if (!defaultFolder.exists()) {
            defaultFolder.mkdirs(); // Create Downloads folder if it doesn't exist
        }

        JPanel controlPanel = new JPanel();
        selectFolderButton = new JButton("Select Folder");
        toggleButton = new JButton("Start Monitoring");
        autoStartCheckbox = new JCheckBox("Auto-start with Windows");

        selectFolderButton.addActionListener(e -> selectFolder());
        toggleButton.addActionListener(e -> toggleMonitoring());
        autoStartCheckbox.addActionListener(e -> handleAutoStart());

        controlPanel.add(selectFolderButton);
        controlPanel.add(toggleButton);
        controlPanel.add(autoStartCheckbox);

        logArea = new JTextArea();
        logArea.setEditable(false);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        add(mainPanel);

        // Initialize organizer with default folder
        organizer = new FileOrganizer(defaultFolder, this::log);
        log("Default folder set to: " + defaultFolder.getAbsolutePath());
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            organizer = new FileOrganizer(folder, this::log);
            log("Selected folder: " + folder.getAbsolutePath());
        }
    }

    private void toggleMonitoring() {
        if (organizer == null) {
            log("Please select a folder first!");
            return;
        }

        if (!isMonitoring) {
            organizer.startMonitoring();
            toggleButton.setText("Stop Monitoring");
            log("Started monitoring...");
        } else {
            organizer.stopMonitoring();
            toggleButton.setText("Start Monitoring");
            log("Stopped monitoring...");
        }
        isMonitoring = !isMonitoring;
    }

    private void handleAutoStart() {
        if (autoStartCheckbox.isSelected()) {
            configureAutoStart();
        } else {
            removeAutoStart();
        }
    }

    private void configureAutoStart() {
        try {
            File startupFolder = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup");
            File shortcut = new File(startupFolder, "FileOrganizer.lnk");

            // Create shortcut using VBScript
            String vbs = "Set ws = WScript.CreateObject(\"WScript.Shell\")\n" +
                    "Set shortcut = ws.CreateShortcut(\"" + shortcut + "\")\n" +
                    "shortcut.TargetPath = \"" + System.getProperty("java.home") + "\\bin\\java.exe\"\n" +
                    "shortcut.Arguments = \"-jar " + new File(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().getPath()).getAbsolutePath() + "\"\n" +
                    "shortcut.Save";

            Files.write(Paths.get("create_shortcut.vbs"), vbs.getBytes());
            Runtime.getRuntime().exec("wscript create_shortcut.vbs");
            log("Auto-start configured successfully.");
        } catch (Exception e) {
            log("Error configuring auto-start: " + e.getMessage());
        }
    }

    private void removeAutoStart() {
        try {
            File startupFolder = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup");
            File shortcut = new File(startupFolder, "FileOrganizer.lnk");

            if (shortcut.exists() && shortcut.delete()) {
                log("Auto-start shortcut removed successfully.");
            } else {
                log("No auto-start shortcut found or failed to remove.");
            }
        } catch (Exception e) {
            log("Error removing auto-start: " + e.getMessage());
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileOrganizerGUI().setVisible(true));
    }
}