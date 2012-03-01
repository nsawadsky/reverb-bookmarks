package ca.ubc.cs.reverb.indexer.installer;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JTextArea;
import java.awt.SystemColor;
import javax.swing.JProgressBar;

import ca.ubc.cs.reverb.indexer.IndexerConfig;
import ca.ubc.cs.reverb.indexer.IndexerException;

import java.awt.Toolkit;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;

import javax.swing.SpringLayout;

import org.apache.log4j.Logger;
import java.awt.Font;

public class IndexHistoryFrame extends JFrame {
    private static Logger log = Logger.getLogger(IndexHistoryFrame.class);
    
    private JProgressBar progressBar;
    private JLabel progressBarLabel;
    private JCheckBox indexChromeHistory;
    private JCheckBox indexFirefoxHistory;
    private JButton indexHistoryButton;
    private IndexerConfig config;
    private volatile boolean frameClosed = false;
    private boolean closeBrowserWindowsRequested = false;
    private boolean indexingCompleted = false;
    
    /**
     * Create the frame.
     */
    public IndexHistoryFrame(IndexerConfig config, String installLocation) {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.config = config;
        
        setIconImage(Toolkit.getDefaultToolkit().getImage(IndexHistoryFrame.class.getResource("/ca/ubc/cs/reverb/indexer/installer/reverb-16.png")));
        setTitle("Reverb Indexer Installation");
        setBounds(100, 100, 473, 306);
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
        txtrTheIndexerService.setText("<html>The indexer service has been registered at " + installLocation + ".<br><br>Reverb can now index your Chrome and Firefox browsing history.  Indexing your browsing history is highly recommended.  It takes 5 to 10 minutes and will allow you to start receiving useful page suggestions right away.</html>");
        contentPanel.add(txtrTheIndexerService);

        indexChromeHistory = new JCheckBox("Index Chrome history (you will need to shut down Chrome for a moment)");
        indexChromeHistory.setFont(new Font("Dialog", Font.PLAIN, 12));
        indexChromeHistory.setSelected(true);
        sl_contentPanel.putConstraint(SpringLayout.NORTH, indexChromeHistory, 10, SpringLayout.SOUTH, txtrTheIndexerService);
        sl_contentPanel.putConstraint(SpringLayout.WEST, indexChromeHistory, 5, SpringLayout.WEST, contentPanel);
        contentPanel.add(indexChromeHistory);

        indexFirefoxHistory = new JCheckBox("Index Firefox history");
        indexFirefoxHistory.setFont(new Font("Dialog", Font.PLAIN, 12));
        sl_contentPanel.putConstraint(SpringLayout.NORTH, indexFirefoxHistory, -2, SpringLayout.SOUTH, indexChromeHistory);
        sl_contentPanel.putConstraint(SpringLayout.WEST, indexFirefoxHistory, 0, SpringLayout.WEST, txtrTheIndexerService);
        indexFirefoxHistory.setSelected(true);
        contentPanel.add(indexFirefoxHistory);
        
        progressBar = new JProgressBar();
        progressBar.setFont(new Font("Tahoma", Font.PLAIN, 11));
        sl_contentPanel.putConstraint(SpringLayout.WEST, progressBar, 5, SpringLayout.WEST, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.SOUTH, progressBar, 0, SpringLayout.SOUTH, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.EAST, progressBar, -5, SpringLayout.EAST, contentPanel);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(0, 20));
        contentPanel.add(progressBar);
        
        progressBarLabel = new JLabel("");
        progressBarLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        progressBarLabel.setHorizontalAlignment(SwingConstants.CENTER);
        sl_contentPanel.putConstraint(SpringLayout.SOUTH, progressBarLabel, -3, SpringLayout.NORTH, progressBar);
        sl_contentPanel.putConstraint(SpringLayout.EAST, progressBarLabel, -5, SpringLayout.EAST, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.WEST, progressBarLabel, 5, SpringLayout.WEST, contentPanel);
        contentPanel.add(progressBarLabel);
        
        {
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
            buttonPane.setBorder(new EmptyBorder(0, 5, 0, 5));
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            {
                indexHistoryButton = new JButton("Index History");
                indexHistoryButton.setFont(new Font("Dialog", Font.PLAIN, 12));
                buttonPane.add(indexHistoryButton);
                getRootPane().setDefaultButton(indexHistoryButton);
                indexHistoryButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        extractHistory();
                    }
                    
                });
                
                JButton btnMoreInformation = new JButton("More Info");
                btnMoreInformation.setFont(new Font("Dialog", Font.PLAIN, 12));
                buttonPane.add(btnMoreInformation);
                btnMoreInformation.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final String url = "http://code.google.com/p/reverb-bookmarks/wiki/IndexingBrowsingHistory";
                        try {
                            Desktop.getDesktop().browse(new URI(url));
                        } catch (Exception except) {
                            log.error("Error opening page '" + url + "'", except);
                        }
                    }
                    
                });

                JButton btnSkip = new JButton("Skip");
                btnSkip.setFont(new Font("Dialog", Font.PLAIN, 12));
                buttonPane.add(btnSkip);
                btnSkip.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmCloseFrame();
                    }
                    
                });
            }
        }
        
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Handle the X button (for some reason, the X button does not generate a windowClosed call, just 
                // a windowClosing call).
                confirmCloseFrame();
            }
        });
    }
    
    private void confirmCloseFrame() {
        if (!indexingCompleted) {
            int result = showConfirmWithWrap("Are you sure you want to proceed without indexing browsing history?",
                    "Browsing History Not Indexed", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        frameClosed = true;
        dispose();
        
        displayInstallCompleteFrame();
    }
    
    private void displayInstallCompleteFrame() {
        InstallCompleteFrame installCompleteFrame = new InstallCompleteFrame();
        installCompleteFrame.setLocation(this.getLocation());
        installCompleteFrame.setVisible(true);
    }
    
    private void extractHistory() {
        indexHistoryButton.setEnabled(false);

        progressBar.setMaximum(100);
        progressBar.setValue(5);
        progressBarLabel.setText("Extracting browsing history");

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MONTH, -3);
        
        final Date startTime = calendar.getTime();
        final Date now = new Date();
        
        final boolean indexChromeHistorySelected = indexChromeHistory.isSelected();
        final boolean indexFirefoxHistorySelected = indexFirefoxHistory.isSelected();
        
        Runnable extractTask = new Runnable() {
            @Override
            public void run() {
                try {
                    List<HistoryVisit> allVisits = new ArrayList<HistoryVisit>();
                    
                    if (indexChromeHistorySelected) {
                        HistoryExtractor extractor = HistoryExtractor.getHistoryExtractor(WebBrowserType.GOOGLE_CHROME);
                        if (extractor.historyDbExists()) {
                            allVisits.addAll(extractor.extractHistory(startTime, now));
                        }
                    }
                    
                    if (indexFirefoxHistorySelected) {
                        HistoryExtractor extractor = HistoryExtractor.getHistoryExtractor(WebBrowserType.MOZILLA_FIREFOX);
                        if (extractor.historyDbExists()) {
                            allVisits.addAll(extractor.extractHistory(startTime, now));
                        }
                    }
                    
                    indexHistory(allVisits);
                } catch (final IndexerException e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            handleIndexingError(e);
                        }
                    });
                }
            }
        };
        
        new Thread(extractTask).start();
    }
            
    /**
     * This method is invoked from a background (non-UI) thread.
     */
    private void indexHistory(List<HistoryVisit> allVisits) throws IndexerException {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!frameClosed) {
                    progressBar.setValue(15);
                    String progressBarText = "Indexing browsing history";
                    if (closeBrowserWindowsRequested) {
                        progressBarText += " (now safe to restart browser)";
                    }
                    progressBarLabel.setText(progressBarText);
                }
            }
        });

        HistoryIndexer indexer = new HistoryIndexer(config, allVisits);
        
        indexer.startIndexing();
        
        boolean indexingCompleted = false;
        int prevLocationsClassified = 0;
        int iterationCount = 0;
        
        while (!frameClosed && !indexingCompleted) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { }
            
            double fractionComplete = 100.0;
            int locationsToClassifyCount = indexer.getLocationsToIndexCount();
            int locationsClassifiedCount = indexer.getLocationsIndexedCount();
            if ( locationsToClassifyCount > 0 ) {
                fractionComplete = (double)locationsClassifiedCount / locationsToClassifyCount;
            }
            final int progressBarValue = 15 + (int)(fractionComplete * 80);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (!frameClosed) {
                        progressBar.setValue(progressBarValue);
                    }
                }
            });
            iterationCount++;
            
            indexingCompleted = (locationsClassifiedCount == locationsToClassifyCount);
            
            if (!indexingCompleted) {
                if (iterationCount % 8 == 0) {
                    if (locationsClassifiedCount == prevLocationsClassified) {
                        indexingCompleted = true;
                    } else {
                        prevLocationsClassified = locationsClassifiedCount;
                    }
                }
            }
        }
        
        indexer.shutdown();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!frameClosed) {
                    dispose();
                    displayInstallCompleteFrame();
                }
            }
        });
    }
    
    private void handleIndexingError(IndexerException e) {
        log.error("Error extracting/indexing history", e);

        if (!frameClosed) {
            showMessageWithWrap("Please close all open browser windows.  " +
                    "You can restart your browser once indexing is in progress.",
                    "Close Browser Windows", JOptionPane.ERROR_MESSAGE);
    
            progressBar.setValue(0);
            progressBarLabel.setText("");
            indexHistoryButton.setEnabled(true);
            closeBrowserWindowsRequested = true;
        }
    }
    
    private HistoryExtractor getMockHistoryExtractor() throws IndexerException {
        return new HistoryExtractor() {
            @Override
            public List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws IndexerException {
                List<HistoryVisit> result = new ArrayList<HistoryVisit>();
                for (int i = 1; i <= 5; i++) {
                    result.add(new HistoryVisit(WebBrowserType.MOZILLA_FIREFOX, i, new Date(), FirefoxVisitType.LINK, 1, i,
                            "www.site" + i + ".com", "Site Number " + i, i - 1, "www.site" + (i - 1) + ".com"));
                }
                return result;
            }

            @Override
            public boolean historyDbExists() { return true; }
            
            @Override
            public Date getEarliestVisitDate() throws IndexerException {
                return new Date();
            }
        };
    }
    
    private void showMessageWithWrap(String message, String title, int messageType) {
        JTextArea textArea = new JTextArea(message);
        textArea.setColumns(30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setSize(textArea.getPreferredSize().width, 1);
        textArea.setBackground(SystemColor.control);
        textArea.setEditable(false);
        textArea.setFont(new Font("Dialog", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, textArea, title, messageType);
    }
    
    private int showConfirmWithWrap(String message, String title, int optionType) {
        JTextArea textArea = new JTextArea(message);
        textArea.setColumns(30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setSize(textArea.getPreferredSize().width, 1);
        textArea.setBackground(SystemColor.control);
        textArea.setEditable(false);
        textArea.setFont(new Font("Dialog", Font.PLAIN, 12));
        return JOptionPane.showConfirmDialog(this, textArea, title, optionType);
    }
}
