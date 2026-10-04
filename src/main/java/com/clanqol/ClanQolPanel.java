package com.clanqol;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import com.clanqol.utils.ClanCsvSync;

// import/export clan member data as a csv file
public class ClanQolPanel extends PluginPanel
{
    private static final String TITLE = "Clan QOL";

    private final ConfigManager configManager;

    @Inject
    public ClanQolPanel(ConfigManager configManager)
    {
        super(false);
        this.configManager = configManager;
    }

    private void rebuild()
    {
        removeAll();
        setLayout(new BorderLayout(0, 3));
        setBorder(new EmptyBorder(0, 5, 0, 5));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel importExportGroup = new JPanel(new GridLayout(1, 2, 5, 0));

        JButton importButton = new JButton("Import CSV");
        importButton.addActionListener(ev -> importCsv());
        importExportGroup.add(importButton);

        JButton exportButton = new JButton("Export CSV");
        exportButton.addActionListener(ev -> exportCsv());
        importExportGroup.add(exportButton);

        add(importExportGroup, BorderLayout.NORTH);

        revalidate();
    }

    @Override
    public void onActivate()
    {
        rebuild();
    }

    private void importCsv()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Clan QOL CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        try
        {
            String csv = new String(Files.readAllBytes(chooser.getSelectedFile().toPath()), StandardCharsets.UTF_8);
            if (csv.startsWith("\uFEFF"))
            {
                csv = csv.substring(1);
            }

            ClanCsvSync.Result result = ClanCsvSync.apply(configManager, csv, false);
            ClanCsvSync.syncField(configManager);

            String message = "Imported " + result.applied + " clan members.";
            if (!result.errors.isEmpty())
            {
                message += "\n\nSkipped:\n" + String.join("\n", result.errors);
            }
            JOptionPane.showMessageDialog(this, message, TITLE, JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage(), TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCsv()
    {
        String csv = ClanCsvSync.build(configManager);
        if (csv.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "No clan member data to export.", TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Clan QOL CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        chooser.setSelectedFile(new File("clan-qol.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv"))
        {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }

        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Overwrite?",
                TITLE, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
        {
            return;
        }

        try
        {
            Files.write(file.toPath(), csv.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this, "Exported to " + file.getName(), TITLE, JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, "Failed to write file: " + e.getMessage(), TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }
}