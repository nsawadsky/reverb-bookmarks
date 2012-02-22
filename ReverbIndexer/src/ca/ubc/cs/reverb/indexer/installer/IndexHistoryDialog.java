package ca.ubc.cs.reverb.indexer.installer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JTextArea;
import java.awt.SystemColor;
import javax.swing.JProgressBar;

import ca.ubc.cs.reverb.indexer.IndexerException;
import ca.ubc.cs.reverb.indexer.Util.RunnableWithResult;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SpringLayout;

import org.apache.log4j.Logger;
import java.awt.Font;

public class IndexHistoryDialog extends JDialog {
    private Logger log = Logger.getLogger(IndexHistoryDialog.class);
    
    private JProgressBar progressBar;
    private JLabel progressBarLabel;
    private JCheckBox indexChromeHistory;
    private JCheckBox indexFirefoxHistory;
    private JButton okButton;
    
    /**
     * Create the dialog.
     */
    public IndexHistoryDialog() {
        setModalityType(ModalityType.APPLICATION_MODAL);
        setIconImage(Toolkit.getDefaultToolkit().getImage(IndexHistoryDialog.class.getResource("/ca/ubc/cs/reverb/indexer/installer/reverb-16.png")));
        setModal(true);
        setTitle("Reverb Indexer Installation");
        setBounds(100, 100, 450, 282);
        getContentPane().setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        SpringLayout sl_contentPanel = new SpringLayout();
        contentPanel.setLayout(sl_contentPanel);

        JLabel txtrTheIndexerService = new JLabel();
        txtrTheIndexerService.setFont(new Font("Tahoma", Font.PLAIN, 11));
        sl_contentPanel.putConstraint(SpringLayout.NORTH, txtrTheIndexerService, 5, SpringLayout.NORTH, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.WEST, txtrTheIndexerService, 5, SpringLayout.WEST, contentPanel);
        sl_contentPanel.putConstraint(SpringLayout.EAST, txtrTheIndexerService, -5, SpringLayout.EAST, contentPanel);
        txtrTheIndexerService.setText("<html>The indexer service has been registered at .... <br><br>Reverb can now index your Chrome and Firefox browsing history.  Indexing your browsing history is strongly recommended.  It takes about 10-15 minutes and will allow you to start receiving useful page suggestions right away.</html>");
        contentPanel.add(txtrTheIndexerService);

        indexChromeHistory = new JCheckBox("Index Chrome history (need to shut down Chrome for a moment)");
        indexChromeHistory.setFont(new Font("Tahoma", Font.PLAIN, 11));
        indexChromeHistory.setSelected(true);
        sl_contentPanel.putConstraint(SpringLayout.NORTH, indexChromeHistory, 10, SpringLayout.SOUTH, txtrTheIndexerService);
        sl_contentPanel.putConstraint(SpringLayout.WEST, indexChromeHistory, 5, SpringLayout.WEST, contentPanel);
        contentPanel.add(indexChromeHistory);

        indexFirefoxHistory = new JCheckBox("Index Firefox history");
        indexFirefoxHistory.setFont(new Font("Tahoma", Font.PLAIN, 11));
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
                okButton = new JButton("Index History");
                okButton.setFont(new Font("Tahoma", Font.PLAIN, 11));
                okButton.setActionCommand("OK");
                buttonPane.add(okButton);
                getRootPane().setDefaultButton(okButton);
                okButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        analyzeHistory();
                    }
                    
                });
            }
            {
                JButton btnSkip = new JButton("Skip");
                btnSkip.setFont(new Font("Tahoma", Font.PLAIN, 11));
                btnSkip.setActionCommand("Skip");
                buttonPane.add(btnSkip);
            }
        }
    }
    
    private void analyzeHistory() {
        try {
            okButton.setEnabled(false);

            progressBar.setMaximum(100);
            progressBar.setValue(5);

            GregorianCalendar calendar = new GregorianCalendar();
            calendar.add(Calendar.MONTH, -3);
            
            final Date startTime = calendar.getTime();
            final Date now = new Date();
            
            List<HistoryVisit> allVisits = new ArrayList<HistoryVisit>();
            
            if (indexChromeHistory.isSelected()) {
                allVisits.addAll(extractHistory(WebBrowserType.GOOGLE_CHROME, "Chrome", startTime, now));
            }
            
            if (indexFirefoxHistory.isSelected()) {
                allVisits.addAll(extractHistory(WebBrowserType.MOZILLA_FIREFOX, "Firefox", startTime, now));
            }
    
            progressBar.setValue(15);
            
            final HistoryIndexer indexer = new HistoryIndexer(allVisits);
            
            indexer.startIndexing();
            
            Timer timer = new Timer(1000, null);
            timer.setRepeats(false);
            timer.addActionListener(new ActionListener() {
                private int prevLocationsClassified = 0;
                private int iterationCount = 0;

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    boolean done = false;
                    try {
                        double fractionComplete = 100.0;
                        int locationsToClassifyCount = indexer.getLocationsToIndexCount();
                        int locationsClassifiedCount = indexer.getLocationsIndexedCount();
                        if ( locationsToClassifyCount > 0 ) {
                            fractionComplete = (double)locationsClassifiedCount / locationsToClassifyCount;
                        }
                        progressBar.setValue(15 + (int)(fractionComplete * 80));
                        iterationCount++;
                        
                        done = (locationsClassifiedCount == locationsToClassifyCount);
                        
                        if (!done) {
                            if (iterationCount % 8 == 0) {
                                if (locationsClassifiedCount == prevLocationsClassified) {
                                    done = true;
                                } else {
                                    prevLocationsClassified = locationsClassifiedCount;
                                }
                            }
                        }
                        if (done) {
                            try {
                                indexer.shutdown();
                            } catch (Exception e) {
                                log.error("Error shutting down indexer: " + e);
                            }
                            
                            progressBar.setValue(progressBar.getMaximum());
                        }
                    } catch (Exception e) {
                        log.error("Error monitoring indexer progress: " + e);
                    }
                    if (!done) {
                        Timer newTimer = new Timer(1000, this);
                        newTimer.setRepeats(false);
                        newTimer.start();
                    }
                }
                
            });
            timer.start();
        } catch (Exception e) {
            log.error("Error extracting/indexing history", e);

            showMessageWithWrap("An error occurred while indexing browsing history.  Please close all open browser windows (you can restart " + 
                    "your browser once indexing is under way).",
                    "Error Indexing Browsing History", JOptionPane.ERROR_MESSAGE);

            progressBar.setValue(0);
            okButton.setEnabled(true);
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
            public Date getEarliestVisitDate() throws IndexerException {
                return new Date();
            }
        };
    }
    
    private List<HistoryVisit> extractHistory(final WebBrowserType browserType, String browserName, final Date startTime, final Date endTime) { 
        RunnableWithResult<List<HistoryVisit>> historyExtractor = new RunnableWithResult<List<HistoryVisit>>(){
            public List<HistoryVisit> call() throws Exception {
                HistoryExtractor extractor = HistoryExtractor.getHistoryExtractor(browserType);
                
                return extractor.extractHistory(startTime, endTime);
            }
        };
        
        Thread extractorThread = new Thread(historyExtractor);
        
        extractorThread.start();
        try {
            extractorThread.join();
        } catch (InterruptedException e) { }
        
        if (historyExtractor.getError() != null) {
            showMessageWithWrap("Error extracting " + browserName + " history: " + historyExtractor.getError(),
                    "Error Extracting " + browserName + " History", JOptionPane.ERROR_MESSAGE);
            return null;
        } else {
            return historyExtractor.getResult();
        }
        
    }
    
    private void showMessageWithWrap(String message, String title, int messageType) {
        JTextArea textArea = new JTextArea(message);
        textArea.setColumns(30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setSize(textArea.getPreferredSize().width, 1);
        textArea.setBackground(SystemColor.control);
        textArea.setEditable(false);
        textArea.setFont(new Font("Tahoma", Font.PLAIN, 11));
        JOptionPane.showMessageDialog(this, textArea, title, messageType);
    }
}
