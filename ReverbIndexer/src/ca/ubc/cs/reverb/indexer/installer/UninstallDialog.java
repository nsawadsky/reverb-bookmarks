package ca.ubc.cs.reverb.indexer.installer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import javax.swing.SwingConstants;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.SpringLayout;
import java.awt.TextArea;
import javax.swing.JTextArea;
import java.awt.Font;
import java.awt.Color;
import java.awt.SystemColor;

public class UninstallDialog extends JDialog {

    private final JPanel contentPanel = new JPanel();

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        try {
            UninstallDialog dialog = new UninstallDialog();
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create the dialog.
     */
    public UninstallDialog() {
        setModal(true);
        setTitle("Reverb Indexer Uninstalled");
        setBounds(100, 100, 450, 202);
        getContentPane().setLayout(new BorderLayout());
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        SpringLayout sl_contentPanel = new SpringLayout();
        contentPanel.setLayout(sl_contentPanel);
        {
            JTextArea txtrTheIndexerService = new JTextArea();
            txtrTheIndexerService.setWrapStyleWord(true);
            txtrTheIndexerService.setBackground(SystemColor.control);
            sl_contentPanel.putConstraint(SpringLayout.SOUTH, txtrTheIndexerService, 129, SpringLayout.NORTH, contentPanel);
            sl_contentPanel.putConstraint(SpringLayout.EAST, txtrTheIndexerService, 409, SpringLayout.WEST, contentPanel);
            txtrTheIndexerService.setFont(new Font("Arial", Font.PLAIN, 12));
            sl_contentPanel.putConstraint(SpringLayout.WEST, txtrTheIndexerService, 15, SpringLayout.WEST, contentPanel);
            txtrTheIndexerService.setText("The indexer service has been stopped and unregistered.  You can delete this folder to complete the uninstall.\r\n\r\nTo remove the index, as well as all logs and settings, delete the folder C:\\Users\\Nick\\AppData\\Local\\cs.ubc.ca\\Reverb\\data.");
            txtrTheIndexerService.setLineWrap(true);
            sl_contentPanel.putConstraint(SpringLayout.NORTH, txtrTheIndexerService, 10, SpringLayout.NORTH, contentPanel);
            contentPanel.add(txtrTheIndexerService);
        }
        {
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            {
                JButton okButton = new JButton("OK");
                okButton.setActionCommand("OK");
                buttonPane.add(okButton);
                getRootPane().setDefaultButton(okButton);
            }
        }
    }
}
