package com.clanqol;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;
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
        List<Filepath> selected = new Filepath.Chooser()
                .setDialogTitle("Import Clan QOL CSV")
                .addExtensionFilter("CSV files", "csv")
                .setIsOpen()
                .showDialog(this);
        if (selected == null || selected.isEmpty())
        {
            return;
        }

        try (BufferedReader reader = selected.get(0).openBufferedReader())
        {
            StringBuilder csv = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                csv.append(line).append('\n');
            }

            String text = csv.toString();
            if (text.startsWith("\uFEFF"))
            {
                text = text.substring(1);
            }

            ClanCsvSync.Result result = ClanCsvSync.apply(configManager, text, false);
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

        List<Filepath> selected = new Filepath.Chooser()
                .setDialogTitle("Export Clan QOL CSV")
                .addExtensionFilter("CSV files", "csv")
                .setDefaultExtension("csv")
                .setFileName("clan-qol.csv")
                .setIsSave()
                .showDialog(this);
        if (selected == null || selected.isEmpty())
        {
            return;
        }

        Filepath file = selected.get(0);
        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getFileName() + " already exists. Overwrite?",
                TITLE, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
        {
            return;
        }

        try
        {
            file.write(csv);
            JOptionPane.showMessageDialog(this, "Exported to " + file.getFileName(), TITLE, JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, "Failed to write file: " + e.getMessage(), TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }
}