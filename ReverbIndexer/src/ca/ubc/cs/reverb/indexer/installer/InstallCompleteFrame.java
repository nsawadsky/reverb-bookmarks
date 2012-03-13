package ca.ubc.cs.reverb.indexer.installer;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;

import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.border.EmptyBorder;

import org.apache.log4j.Logger;

public class InstallCompleteFrame extends JFrame {
    private static Logger log = Logger.getLogger(InstallCompleteFrame.class);
    
    /**
     * Create the frame.
     */
    public InstallCompleteFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        setIconImage(Toolkit.getDefaultToolkit().getImage(IndexHistoryFrame.class.getResource("/ca/ubc/cs/reverb/indexer/installer/reverb-16.png")));
        setTitle("Reverb Indexer Installed");
        setBounds(100, 100, 450, 141);
        getContentPane().setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        SpringLayout sl_contentPanel = new SpringLayout();
        contentPanel.setLayout(sl_contentPanel);

        JLabel txtrTheIndexerService = new JLabel();
        txtrTheIndexerService.setFont(new Font("Dialog", Font.PLAIN, 12));
        sl_contentPanel.putConstraint(SpringLayout.NORTH, txtrTheIndexerService, 5, SpringLayout.NORTH, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.WEST, txtrTheIndexerService, 5, SpringLayout.WEST, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.EAST, txtrTheIndexerService, -5, SpringLayout.EAST, contentPanel);
        txtrTheIndexerService.setText("<html>The indexer service was installed successfully.  When you click OK, you will be taken to a page with information on how to install the Reverb browser extensions, as well as the Eclipse plugin.</html>");
        contentPanel.add(txtrTheIndexerService);
        
        {
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
            buttonPane.setBorder(new EmptyBorder(0, 5, 0, 5));
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            {
                JButton okButton = new JButton("OK");
                okButton.setFont(new Font("Dialog", Font.PLAIN, 12));
                buttonPane.add(okButton);
                getRootPane().setDefaultButton(okButton);
                okButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent event) {
                        try {
                            Desktop.getDesktop().browse(new URI("http://www.cs.ubc.ca/labs/spl/projects/reverb/installreverb.html"));
                        } catch (Exception e) { }
                        dispose();
                    }
                    
                });
            }
        }
        
    }
    
}
