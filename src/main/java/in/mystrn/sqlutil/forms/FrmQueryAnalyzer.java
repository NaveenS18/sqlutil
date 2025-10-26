package in.mystrn.sqlutil.forms;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent; // For Key Binding
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList; // Used in createTableTabsFromAnalysis
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel; // Still needed for clearing/initial build

// --- Imports ---
import com.formdev.flatlaf.FlatIntelliJLaf; // Or your chosen FlatLaf theme

import gudusoft.gsqlparser.EDbVendor;
import gudusoft.gsqlparser.TGSqlParser;
import gudusoft.gsqlparser.TSourceToken;
import gudusoft.gsqlparser.nodes.TParseTreeNode;
import gudusoft.gsqlparser.stmt.TDeleteSqlStatement;
import gudusoft.gsqlparser.stmt.TInsertSqlStatement;
import gudusoft.gsqlparser.stmt.TSelectSqlStatement;
import gudusoft.gsqlparser.stmt.TUpdateSqlStatement;
import in.mystrn.sqlutil.utils.CustomTableModel;
import in.mystrn.sqlutil.utils.ErrorDialog;
import in.mystrn.sqlutil.utils.ProcessingDialog;
import in.mystrn.sqlutil.utils.ProcessingTask;
import in.mystrn.sqlutil.utils.QueryAnalyzerUtil; // Your Gudu Util
import in.mystrn.sqlutil.utils.WrappingTableCellRenderer;

/**
 * SQL Query Analyzer GUI using Gudu SQL Parser for structural analysis
 * and JDBC for EXPLAIN plan, with FlatLaf UI, custom fonts, Enter key binding,
 * tab highlighting, text wrapping, and detailed analysis.
 *
 * @author hive
 */
public class FrmQueryAnalyzer extends JFrame {

    private static final long serialVersionUID = -1090142025140013449L;
    // --- GUI Components ---
    private JTextField jdbcDriverField;
    private JTextField jdbcUrlField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextArea queryInputArea;
    private JTable explainTable;
    private JTabbedPane resultsTabbedPane;
    private JTextArea analysisTextArea;
    private JButton analyzeButton;
    private JLabel explainTimeLabel; // Label for EXPLAIN time

    // --- Font Definition ---
    // Using a Google Font (Roboto Mono). Assumes font is installed.
    private static final Font MONOSPACED_FONT = new Font("Roboto Mono", Font.PLAIN, 13);

    // --- Gudu Query Analyzer Utility Instance ---
    private final QueryAnalyzerUtil queryAnalyzerUtil;

    public FrmQueryAnalyzer() {
        setTitle("SQL Query Analyzer");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        queryAnalyzerUtil = new QueryAnalyzerUtil();

        initComponents();
    }

    /**
     * Initialize and lay out all the Swing components. Applies custom font and Key Binding.
     */
    private void initComponents() {
        setLayout(new BorderLayout());
        JPanel connectionPanel = createConnectionPanel();

        queryInputArea = new JTextArea();
        queryInputArea.setFont(MONOSPACED_FONT); // Apply font

        // --- Add Key Binding for Enter key ---
        InputMap inputMap = queryInputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = queryInputArea.getActionMap();
        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        String actionName = "analyzeOnEnter";
        inputMap.put(enterKey, actionName);
        Action analyzeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                analyzeButton.doClick(); // Simulate button click
            }
        };
        actionMap.put(actionName, analyzeAction);
        // --- END Key Binding ---

        JScrollPane queryScrollPane = new JScrollPane(queryInputArea);
        queryScrollPane.setBorder(BorderFactory.createTitledBorder("SQL Query"));

        explainTable = new JTable();
        explainTable.setFont(MONOSPACED_FONT); // Apply font
        explainTable.setFillsViewportHeight(true);
        explainTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        // Apply Wrapping Renderer to EXPLAIN table
        WrappingTableCellRenderer wrappingRendererExplain = new WrappingTableCellRenderer();
        explainTable.setDefaultRenderer(Object.class, wrappingRendererExplain);
        explainTable.setRowHeight(20); // Minimum row height

        JScrollPane tableScrollPane = new JScrollPane(explainTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("EXPLAIN Plan (from DB)"));

        resultsTabbedPane = new JTabbedPane();
        analysisTextArea = new JTextArea();
        analysisTextArea.setEditable(false);
        analysisTextArea.setFont(MONOSPACED_FONT); // Apply font
        JScrollPane analysisScrollPane = new JScrollPane(analysisTextArea);
        resultsTabbedPane.addTab("Structural Analysis & Hints", analysisScrollPane);

        analyzeButton = new JButton("Analyze Query");
        analyzeButton.setPreferredSize(new Dimension(160, 40));
        analyzeButton.setBackground(new Color(0, 100, 180)); // May be overridden by FlatLaf
        analyzeButton.putClientProperty("JButton.buttonType", "square");
        analyzeButton.putClientProperty("Button.arc", 10);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(analyzeButton);

        // --- Add Explain Time Label to layout ---
        explainTimeLabel = new JLabel("Explain Time: - ms");
        explainTimeLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 2, 5)); // Padding
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(toolBar, BorderLayout.CENTER);
        southPanel.add(explainTimeLabel, BorderLayout.EAST);
        // --- End Explain Time Label ---


        JSplitPane resultsSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tableScrollPane, resultsTabbedPane);
        resultsSplitPane.setDividerLocation(300);
        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, queryScrollPane, resultsSplitPane);
        mainSplitPane.setDividerLocation(400);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(connectionPanel, BorderLayout.CENTER);
        topPanel.add(southPanel, BorderLayout.SOUTH); // Use container with label
        add(topPanel, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);

        // --- Action Listener with Integrated Logic ---
        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sqlQuery = queryInputArea.getText();
                String jdbcUrl = jdbcUrlField.getText();

                if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
                    ErrorDialog.showError(FrmQueryAnalyzer.this, "Please enter a query to analyze.");
                    return;
                }
                if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
                    ErrorDialog.showError(FrmQueryAnalyzer.this, "Please enter a JDBC URL.");
                    return;
                }

                ProcessingDialog.show(FrmQueryAnalyzer.this, "Analyzing Query...", new ProcessingTask() {
                    @Override
                    public void run(Consumer<String> messageUpdater) throws Exception {
                        Connection connection = null;
                        TSelectSqlStatement explainableSelectStatement = null; // Node for SELECT
                        String explainableStatementSql = null; // SQL text for EXPLAIN
                        long explainDurationMs = -1;

                        try {
                            messageUpdater.accept("Determining database vendor...");
                            EDbVendor detectedVendor = determineDbVendor(jdbcUrl);
                            // Corrected check for unknown vendor
                            if (detectedVendor == EDbVendor.dbvansi) {
                                throw new Exception("Could not determine database vendor from URL: " + jdbcUrl);
                            }
                            queryAnalyzerUtil.vendor = detectedVendor;
                            messageUpdater.accept("Vendor detected: " + detectedVendor.name());

                            messageUpdater.accept("Parsing SQL script...");
                            TGSqlParser parser = new TGSqlParser(detectedVendor);
                            parser.sqltext = sqlQuery;
                            if (parser.parse() != 0) {
                                 queryAnalyzerUtil.isValidSQL(sqlQuery); // Populate error field
                                 throw new Exception("SQL parsing failed:\n" + queryAnalyzerUtil.error);
                            }

                            // Find the first explainable statement
                            for (int i = 0; i < parser.sqlstatements.size(); i++) {
                                TParseTreeNode stmtNode = parser.sqlstatements.get(i);
                                if (stmtNode instanceof TSelectSqlStatement || stmtNode instanceof TInsertSqlStatement
                                    || stmtNode instanceof TUpdateSqlStatement || stmtNode instanceof TDeleteSqlStatement)
                                {
                                    TSourceToken startToken = stmtNode.getStartToken();
                                    TSourceToken endToken = stmtNode.getEndToken();
                                    if (startToken != null && endToken != null) {
                                        explainableStatementSql = sqlQuery.substring(
                                                (int) startToken.offset,
                                                (int) (endToken.offset + endToken.astext.length())
                                        ).trim();
                                        if (stmtNode instanceof TSelectSqlStatement) {
                                             explainableSelectStatement = (TSelectSqlStatement) stmtNode;
                                        }
                                        break;
                                    }
                                }
                            }

                            messageUpdater.accept("Performing structural analysis...");
                            final Map<String, Object> analysisResult = queryAnalyzerUtil.analyzeQueryStructure(sqlQuery);

                            SwingUtilities.invokeLater(() -> updateAnalysisDisplay(analysisResult));
                            if (!Boolean.TRUE.equals(analysisResult.get("isValid"))) return;

                            if (explainableStatementSql == null) {
                                messageUpdater.accept("No explainable (SELECT/INSERT/UPDATE/DELETE) statement found in script.");
                                SwingUtilities.invokeLater(() -> {
                                     explainTable.setModel(new DefaultTableModel());
                                     explainTimeLabel.setText("Explain Time: N/A");
                                });
                                return;
                            }

                            messageUpdater.accept("Connecting to database...");
                            connection = getConnection();

                            messageUpdater.accept("Executing EXPLAIN command...");
                            String explainQuery = "EXPLAIN " + explainableStatementSql;
                            CustomTableModel explainTableModel; // Build DefaultTableModel first
                            Map<String, String> aliasToTableMapForExplain = new HashMap<>();

                            try (Statement stmt = connection.createStatement();
                                 ResultSet rs = stmt.executeQuery(explainQuery)) {
                                long startTime = System.currentTimeMillis(); // Start time closer to execution
                                explainTableModel = buildTableModel(rs); // Use our static method
                                explainDurationMs = System.currentTimeMillis() - startTime; // End time after fetching

                            } catch (SQLException explainEx) {
                                if (explainEx.getMessage().toLowerCase().contains("unknown column") || explainEx.getMessage().toLowerCase().contains("unknown variable")) {
                                     throw new Exception("Error executing EXPLAIN: Database doesn't recognize variables like '@workspace_id'. Remove SET commands and replace variables with literal values in your query before analyzing.", explainEx);
                                } else {
                                    throw new Exception("Error executing EXPLAIN: " + explainEx.getMessage(), explainEx);
                                }
                            }

                            // Build alias map only if it was a SELECT statement
                            if (explainableSelectStatement != null) {
                                // Correctly call buildAliasMap on the util instance
                                aliasToTableMapForExplain.putAll(queryAnalyzerUtil.buildAliasMap(explainableSelectStatement));
                            }

                            final Map<String, String> finalAliasMap = aliasToTableMapForExplain;
                            // Ensure analysisResult and its contents are accessible in EDT lambda
                            final Map<String, Map<String, Object>> finalTableInfo = (Map<String, Map<String, Object>>) analysisResult.get("tableInfo");
                            final long finalExplainDuration = explainDurationMs;
                            // Make the DefaultTableModel final to pass to lambda

                            SwingUtilities.invokeLater(() -> {
                                // Create CustomTableModel from DefaultTableModel data for display
                                explainTable.setModel(explainTableModel); // Set the custom model
                                explainTimeLabel.setText("Explain Time: " + finalExplainDuration + " ms");
                                performMicroAnalysis(explainTableModel, resultsTabbedPane, finalAliasMap, finalTableInfo, 10000); // Pass threshold
                            });

                            messageUpdater.accept("Analysis complete.");

                        } finally {
                            if (connection != null && !connection.isClosed()) {
                                try { connection.close(); } catch (SQLException sqlEx) { System.err.println("Error closing connection: " + sqlEx.getMessage()); }
                            }
                        }
                    } // end run()
                }); // end ProcessingDialog.show
            } // end actionPerformed
        }); // end addActionListener
    }

    /**
     * Creates the panel for JDBC connection inputs with Enter key focus traversal. (Unchanged)
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; panel.add(new JLabel("Driver Class:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; jdbcDriverField = new JTextField("com.mysql.cj.jdbc.Driver", 30); panel.add(jdbcDriverField, gbc);
        jdbcDriverField.addActionListener(e -> jdbcUrlField.requestFocusInWindow());

        gbc.gridx = 2; gbc.weightx = 0; panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5; usernameField = new JTextField("root", 15); panel.add(usernameField, gbc);
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; panel.add(new JLabel("JDBC URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; jdbcUrlField = new JTextField("jdbc:mysql://localhost:3306/your_database_name", 30); panel.add(jdbcUrlField, gbc);
        jdbcUrlField.addActionListener(e -> usernameField.requestFocusInWindow());

        gbc.gridx = 2; gbc.weightx = 0; panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5; passwordField = new JPasswordField(15); panel.add(passwordField, gbc);
        passwordField.addActionListener(e -> queryInputArea.requestFocusInWindow());

        return panel;
    }

    /**
     * Establishes and returns a database connection based on GUI inputs. (Unchanged)
     */
    private Connection getConnection() throws SQLException, ClassNotFoundException {
        String jdbcDriver = jdbcDriverField.getText(); String dbUrl = jdbcUrlField.getText();
        String username = usernameField.getText(); String password = new String(passwordField.getPassword());
        if (jdbcDriver == null || jdbcDriver.trim().isEmpty()) { throw new ClassNotFoundException("JDBC Driver class name is empty."); }
        if (dbUrl == null || dbUrl.trim().isEmpty()) { throw new SQLException("JDBC URL is empty."); }
        if (username == null || username.trim().isEmpty()) { throw new SQLException("Username is empty."); }
        Class.forName(jdbcDriver.trim());
        return DriverManager.getConnection(dbUrl.trim(), username.trim(), password);
    }

    /**
     * Resets the foreground color of all tabs. (Unchanged)
     */
    private void resetTabColors(JTabbedPane tabPane) {
        if (tabPane == null) return;
        Color defaultForeground = UIManager.getColor("TabbedPane.foreground");
        for (int i = 0; i < tabPane.getTabCount(); i++) {
             Component contentComponent = tabPane.getComponentAt(i);
             Color originalColor = defaultForeground;
             if (contentComponent instanceof JComponent) {
                 Object storedColor = ((JComponent) contentComponent).getClientProperty("originalTabForeground");
                 if (storedColor instanceof Color) { originalColor = (Color) storedColor; }
                 ((JComponent) contentComponent).putClientProperty("originalTabForeground", null);
             }
             // Ensure index is valid before setting foreground
             if(i < tabPane.getTabCount()) {
                 tabPane.setForegroundAt(i, originalColor);
             }
        }
    }


    /**
     * Removes all dynamic table tabs. (Unchanged)
     */
    private void clearTableTabs() {
        int tabCount = resultsTabbedPane.getTabCount();
        for (int i = tabCount - 1; i > 0; i--) {
             // Check index validity before removing
             if (i < resultsTabbedPane.getTabCount()){
                 resultsTabbedPane.remove(i);
             }
        }
    }

    /**
     * Updates the main analysis text area and table tabs based on Gudu analysis results. (Unchanged)
     */
    private void updateAnalysisDisplay(Map<String, Object> analysisResult) {
        resetTabColors(resultsTabbedPane);
        clearTableTabs();
        explainTable.setModel(new DefaultTableModel());
        explainTimeLabel.setText("Explain Time: - ms");
        analysisTextArea.setText("");

        if (!Boolean.TRUE.equals(analysisResult.get("isValid"))) {
            String errorMsg = "--- SQL Parse Error ---\n" + analysisResult.get("error");
            analysisTextArea.setText(errorMsg);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- Query Structure Analysis ---\n");
        sb.append("Statement Type: ").append(analysisResult.get("statementType")).append("\n");

        Map<String, Object> queryStats = (Map<String, Object>) analysisResult.get("queryStats");
        if (queryStats != null && !queryStats.isEmpty()) {
            sb.append("\n--- Query Statistics ---\n");
            queryStats.forEach((key, value) -> sb.append(key).append(": ").append(value).append("\n"));
        } else {
             sb.append("\n--- Query Statistics ---\n(No specific stats gathered)\n");
        }

        List<?> hintObjects = (List<?>) analysisResult.get("performanceHints");
        if (hintObjects != null && !hintObjects.isEmpty()) {
            sb.append("\n--- Performance Hints (Structural) ---\n");
            for(Object hintObj : hintObjects) {
                if (hintObj instanceof QueryAnalyzerUtil.PerformanceHint) {
                    sb.append(hintObj.toString()).append("\n\n");
                } else {
                     sb.append("- ").append(hintObj.toString()).append("\n");
                }
            }
        }

        analysisTextArea.setText(sb.toString());
        // Ensure text area scrolls to top after update
        analysisTextArea.setCaretPosition(0);


        Map<String, Map<String, Object>> tableInfo = (Map<String, Map<String, Object>>) analysisResult.get("tableInfo");
        if (tableInfo != null && !tableInfo.isEmpty()) {
            createTableTabsFromAnalysis(tableInfo);
        } else {
             System.out.println("tableInfo is null or empty. No table tabs created.");
        }
    }

    /**
     * Creates table tabs with individual columns listed, using CustomTableModel and wrapping renderer. (Unchanged)
     */
    private void createTableTabsFromAnalysis(Map<String, Map<String, Object>> tableInfo) {
        Vector<String> columnNames = new Vector<>(List.of("Category", "Item", "Notes"));

        for (Map.Entry<String, Map<String, Object>> entry : tableInfo.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Object> details = entry.getValue();
            if (details == null) continue;

            Vector<Vector<Object>> data = new Vector<>();
            Set<?> allColsSet = (Set<?>) details.getOrDefault("allColumnsUsed", Set.of());
            Set<?> whereColsSet = (Set<?>) details.getOrDefault("whereColumns", Set.of());
            Set<?> funcColsSet = (Set<?>) details.getOrDefault("columnsWithFunctionsInWhere", Set.of());
            String alias = details.getOrDefault("aliasUsed", "N/A").toString();

            data.add(new Vector<>(List.of("General", "Alias Used", alias)));
            data.add(new Vector<>(List.of("", "", "")));

             if (!funcColsSet.isEmpty()) {
                 data.add(new Vector<>(List.of("--- WARNING ---", "Function on WHERE column(s)", "May prevent index usage")));
                 for(Object col : funcColsSet) { data.add(new Vector<>(List.of("", "- " + col.toString(), "(Function applied)"))); }
                 data.add(new Vector<>(List.of("", "", "")));
             }

            data.add(new Vector<>(List.of("--- ALL COLUMNS USED ---", "(" + allColsSet.size() + " found)", "")));
            if (allColsSet.isEmpty()) { data.add(new Vector<>(List.of("", "(None Detected)", ""))); }
            else { for (Object col : allColsSet) { data.add(new Vector<>(List.of("Column", col.toString(), ""))); } }
            data.add(new Vector<>(List.of("", "", "")));

            data.add(new Vector<>(List.of("--- WHERE/JOIN COLUMNS ---", "(" + whereColsSet.size() + " found)", "")));
             if (whereColsSet.isEmpty()) { data.add(new Vector<>(List.of("", "(None)", ""))); }
             else { for (Object col : whereColsSet) { String notes = funcColsSet.contains(col) ? "(Function applied)" : ""; data.add(new Vector<>(List.of("Filtering", col.toString(), notes))); } }

            // Use CustomTableModel
            CustomTableModel statsModel = new CustomTableModel(data, columnNames);

            JTable statsTable = new JTable(statsModel);
            statsTable.setFont(MONOSPACED_FONT);
            statsTable.setFillsViewportHeight(true);
            statsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
            statsTable.getColumnModel().getColumn(1).setPreferredWidth(250);
            statsTable.getColumnModel().getColumn(2).setPreferredWidth(300);

            // Apply Wrapping Renderer
            WrappingTableCellRenderer wrappingRenderer = new WrappingTableCellRenderer();
            statsTable.getColumnModel().getColumn(1).setCellRenderer(wrappingRenderer); // Item
            statsTable.getColumnModel().getColumn(2).setCellRenderer(wrappingRenderer); // Notes
            statsTable.setRowHeight(20); // Min height

            JScrollPane tableScrollPane = new JScrollPane(statsTable);
            resultsTabbedPane.addTab(tableName, tableScrollPane);
        }
    }

    /**
     * Builds a DefaultTableModel from a ResultSet. (Unchanged)
     */
    public static CustomTableModel buildTableModel(ResultSet rs) throws SQLException {
         ResultSetMetaData metaData = rs.getMetaData();
        Vector<String> columnNames = new Vector<>();
        int columnCount = metaData.getColumnCount();
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnName(column));
        }
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                Object value = rs.getObject(columnIndex);
                row.add(value != null ? value : "");
            }
            data.add(row);
        }
        return new CustomTableModel(data, columnNames);
    }

     /**
     * Helper to extract column names from a DefaultTableModel into a Vector. (Unchanged)
     */
     private Vector<String> getVectorColumnNames(DefaultTableModel model) {
         Vector<String> names = new Vector<>();
         for (int i = 0; i < model.getColumnCount(); i++) {
             names.add(model.getColumnName(i));
         }
         return names;
     }

    /**
     * Performs micro-analysis on EXPLAIN plan using CustomTableModel. (Unchanged)
     */
    private void performMicroAnalysis(CustomTableModel explainModel, // <-- Now uses CustomTableModel
                                      JTabbedPane resultsTabbedPane,
                                      Map<String, String> aliasToTableMap,
                                      Map<String, Map<String, Object>> guduTableInfo,
                                      long rowThreshold) {

        StringBuilder suggestions = new StringBuilder();
        suggestions.append("\n--- EXPLAIN Plan Micro-Analysis (DB Specific) ---\n");

        if (explainModel == null || explainModel.getRowCount() == 0) {
            suggestions.append("No EXPLAIN plan data available to analyze.\n");
            analysisTextArea.append(suggestions.toString());
            return;
        }

        try {
            int tableCol = findColumn(explainModel, "table");
            int typeCol = findColumn(explainModel, "type");
            int keyCol = findColumn(explainModel, "key");
            int possibleKeysCol = findColumn(explainModel, "possible_keys");
            int rowsCol = findColumn(explainModel, "rows");
            int extraCol = findColumn(explainModel, "Extra");

            for (int i = 0; i < explainModel.getRowCount(); i++) {
                String rowId = getColumnValue(explainModel, i, 0, "Row " + (i + 1));
                String currentAlias = getColumnValue(explainModel, i, tableCol, null);
                String fullTableName = (currentAlias != null && aliasToTableMap != null) ? aliasToTableMap.get(currentAlias.toLowerCase()) : null;
                String aliasAndTable = String.format("%s%s", currentAlias != null ? currentAlias : "?", fullTableName != null ? " (" + fullTableName + ")" : "");

                Set<String> filteringCols = Set.of(); Set<String> funcColsInWhere = Set.of();
                Set<String> groupByCols = Set.of(); Set<String> orderByCols = Set.of();
                if (fullTableName != null && guduTableInfo != null) {
                    Map<String, Object> tableDetails = guduTableInfo.get(fullTableName);
                     if (tableDetails != null) {
                         filteringCols = (Set<String>) tableDetails.getOrDefault("whereColumns", Set.of());
                         funcColsInWhere = (Set<String>) tableDetails.getOrDefault("columnsWithFunctionsInWhere", Set.of());
                         groupByCols = (Set<String>) tableDetails.getOrDefault("groupByColumns", Set.of());
                         orderByCols = (Set<String>) tableDetails.getOrDefault("orderByColumns", Set.of());
                     }
                }

                // Suggestion 1: Full Table Scan
                String scanType = getColumnValue(explainModel, i, typeCol, "");
                if ("ALL".equalsIgnoreCase(scanType)) {
                    String detail=String.format("[%s %s] SEVERE: Full Table Scan ('type' is 'ALL'). ", rowId, aliasAndTable); String why="Why: DB read every row."; String suggestion="Suggestion: Index JOIN/WHERE columns.";
                    if (!filteringCols.isEmpty()) { suggestion += " Candidates: [" + String.join(", ", filteringCols) + "]"; }
                    else if (fullTableName != null) { suggestion += " Check JOIN columns for '" + currentAlias + "'."; }
                    suggestions.append(detail).append(why).append("\n  > ").append(suggestion).append("\n");
                    if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Full Table Scan", suggestion, filteringCols, groupByCols, orderByCols); highlightTableTab(resultsTabbedPane, fullTableName, Color.RED); }
                }

                // Suggestion 2: Index Not Used
                String actualKey = getColumnValue(explainModel, i, keyCol, null);
                String possibleKeys = getColumnValue(explainModel, i, possibleKeysCol, null);
                if (possibleKeys != null && !possibleKeys.isEmpty() && (actualKey == null || actualKey.isEmpty() || "NULL".equalsIgnoreCase(actualKey))) {
                     String warning = String.format("Possible keys [%s] found, but none used.", possibleKeys);
                     StringBuilder explanation = new StringBuilder("Potential reasons:\n"); boolean reasonFound = false;
                     if (!funcColsInWhere.isEmpty()) { Set<String> problematicCols = new HashSet<>(filteringCols); problematicCols.retainAll(funcColsInWhere);
                         if (!problematicCols.isEmpty()) { explanation.append("    - Func on indexed col(s): [").append(String.join(", ", problematicCols)).append("]\n"); reasonFound = true; } }
                     explanation.append("    - Data type mismatch in JOIN/WHERE"); if (!filteringCols.isEmpty()) { explanation.append(": [").append(String.join(", ", filteringCols)).append("]"); } explanation.append("\n");
                     explanation.append("    - Optimizer chose scan (small table / low selectivity / outdated stats)\n");
                     if (!reasonFound && !funcColsInWhere.isEmpty()) { explanation.append("    - Note: Funcs in WHERE on: [").append(String.join(", ", funcColsInWhere)).append("]. Overlap with keys?\n"); }

                     suggestions.append(String.format("[%s %s] WARN: Index Not Used\n  > %s\n  > %s", rowId, aliasAndTable, warning, explanation.toString()));
                     if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Index Not Used", explanation.toString().replace("\n    - ", "\n- ").trim(), filteringCols, groupByCols, orderByCols); highlightTableTab(resultsTabbedPane, fullTableName, Color.ORANGE); }
                }

                // Suggestion 3: Large Estimated Row Scan
                 long rowsScanned = getLongValue(explainModel, i, rowsCol);
                 if (rowsScanned > rowThreshold) {
                     suggestions.append(String.format("[%s %s] INFO: High est. rows (%d).\n  > SUGGEST: Check WHERE/JOIN selectivity.\n", rowId, aliasAndTable, rowsScanned));
                     if (fullTableName != null) { highlightTableTab(resultsTabbedPane, fullTableName, Color.ORANGE); addWarningToTableTab(resultsTabbedPane, fullTableName, "High Row Estimate", "Est. rows: " + rowsScanned, filteringCols, groupByCols, orderByCols); }
                 } else if (rowsScanned > 10000) { suggestions.append(String.format("[%s %s] INFO: Est. rows: %d\n", rowId, aliasAndTable, rowsScanned)); }


                // Suggestion 4 & 5: Using filesort / temporary
                String extraInfo = getColumnValue(explainModel, i, extraCol, "");
                if (extraInfo.contains("Using filesort")) {
                    String suggestion = "Suggest: Index ORDER BY cols."; if(!orderByCols.isEmpty()){ suggestion += " Candidates: [" + String.join(", ", orderByCols) + "]"; }
                    suggestions.append(String.format("[%s %s] WARN: 'Using filesort'.\n  > %s\n", rowId, aliasAndTable, suggestion));
                     if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Filesort Used", suggestion, filteringCols, groupByCols, orderByCols); highlightTableTab(resultsTabbedPane, fullTableName, Color.ORANGE); }
                }
                if (extraInfo.contains("Using temporary")) {
                     String suggestion = "Suggest: Temp table needed (slow). Common for complex GROUP BY/DISTINCT/UNION."; if(!groupByCols.isEmpty()){ suggestion += " Consider indexing GROUP BY cols: [" + String.join(", ", groupByCols) + "]"; } else { suggestion += " Simplify query?"; }
                    suggestions.append(String.format("[%s %s] WARN: 'Using temporary'.\n  > %s\n", rowId, aliasAndTable, suggestion));
                    if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Temporary Table Used", suggestion, filteringCols, groupByCols, orderByCols); highlightTableTab(resultsTabbedPane, fullTableName, Color.ORANGE); }
                }
            } // End loop
        } catch (Exception e) {
            suggestions.append("\nError during EXPLAIN plan analysis: " + e.getMessage());
            e.printStackTrace();
        }

        if (suggestions.toString().endsWith("---\n")) {
            suggestions.append("EXPLAIN plan analysis found no common high-priority issues.\n");
        }
        analysisTextArea.append(suggestions.toString());
        // Ensure text area scrolls to top after appending
        SwingUtilities.invokeLater(() -> analysisTextArea.setCaretPosition(0));
    }


    /** Finds a table's tab and adds warnings/suggestions using CustomTableModel. */
    private void addWarningToTableTab(JTabbedPane tabPane, String tableName, String warningType, String message,
                                      Set<String> filteringCols, Set<String> groupByCols, Set<String> orderByCols) {

        for (int i = 1; i < tabPane.getTabCount(); i++) {
            String tabTitle = tabPane.getTitleAt(i);
            String simpleTableName = tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1) : tableName;

            if (simpleTableName.equalsIgnoreCase(tabTitle)) {
                try {
                    JScrollPane scrollPane = (JScrollPane) tabPane.getComponentAt(i);
                    JTable table = (JTable) scrollPane.getViewport().getView();
                    // --- Cast to CustomTableModel ---
                    CustomTableModel model = (CustomTableModel) table.getModel();

                    List<Object[]> suggestionRows = new ArrayList<>();
                    boolean suggestionAdded = false;

                    switch (warningType) {
                        case "Full Table Scan":
                            if (filteringCols != null && !filteringCols.isEmpty()) { suggestionRows.add(new Object[]{"-> Index Suggestion", "Index JOIN/WHERE columns:", "[" + String.join(", ", filteringCols) + "]"}); suggestionAdded = true; }
                            else { suggestionRows.add(new Object[]{"-> Index Suggestion", "Index JOIN columns:", "(Check query for columns used to join)"}); suggestionAdded = true; }
                            break;
                        case "Index Not Used":
                            // Split multi-line explanation for better table display
                            String[] lines = message.split("\n");
                            suggestionRows.add(new Object[]{"-> Explanation", lines.length > 0 ? lines[0] : message, ""}); // First line
                            for(int lineIdx = 1; lineIdx < lines.length; lineIdx++) {
                                suggestionRows.add(new Object[]{"", lines[lineIdx].trim(), ""}); // Subsequent lines indented
                            }
                            suggestionAdded = true;
                            break;
                        case "Filesort Used":
                            if (orderByCols != null && !orderByCols.isEmpty()) { suggestionRows.add(new Object[]{"-> Index Suggestion", "Index ORDER BY columns:", "[" + String.join(", ", orderByCols) + "]"}); suggestionAdded = true; }
                             else { suggestionRows.add(new Object[]{"-> Index Suggestion", "Index ORDER BY columns:", "(Columns not identified)"}); suggestionAdded = true; }
                           break;
                        case "Temporary Table Used":
                            if (groupByCols != null && !groupByCols.isEmpty()) { suggestionRows.add(new Object[]{"-> Index Suggestion", "Index GROUP BY columns:", "[" + String.join(", ", groupByCols) + "]"}); suggestionAdded = true; }
                             else { suggestionRows.add(new Object[]{"-> Index Suggestion", "Consider indexing GROUP BY columns", "(If applicable)"}); suggestionAdded = true; }
                           break;
                         case "High Row Estimate":
                             suggestionRows.add(new Object[]{"-> Info", message, "(Check WHERE/JOIN selectivity)"}); suggestionAdded = true;
                             break;
                    }

                    if (!suggestionAdded && filteringCols != null && !filteringCols.isEmpty()) {
                         suggestionRows.add(new Object[]{"-> Index Suggestion", "Consider indexing WHERE/JOIN columns:", "[" + String.join(", ", filteringCols) + "]"});
                    }

                    boolean warningExists = false;
                    if (model.getRowCount() > 0 && "--- WARNING ---".equals(model.getValueAt(0, 0)) && warningType.equals(model.getValueAt(0, 1)) ){ warningExists = true; }

                    if (!warningExists) {
                        String shortMessage = message.lines().findFirst().orElse(message);
                        // --- Use Object[] with overloaded insertRow ---
                        model.insertRow(0, new Object[]{"--- WARNING ---", warningType, shortMessage});
                        model.insertRow(1, new Object[]{"", "", ""}); // Spacer

                        int insertRowIndex = 2;
                        for(Object[] suggestionRow : suggestionRows) { model.insertRow(insertRowIndex++, suggestionRow); }
                        if(!suggestionRows.isEmpty()){ model.insertRow(insertRowIndex, new Object[]{"", "", ""}); }

                        tabPane.setSelectedIndex(i);
                    } else {
                         System.out.println("Warning type '" + warningType + "' already present in tab '" + tableName + "'. Skipping duplicate warning header.");
                    }
                } catch (ClassCastException cce) {
                     System.err.println("Error: Table model is not a CustomTableModel for tab '" + tableName + "'. Cannot insert warning.");
                     cce.printStackTrace();
                }
                catch (Exception e) {
                    System.err.println("Error adding warning/suggestion to tab '" + tableName + "' (Title: " + tabTitle + "): " + e.getMessage());
                    e.printStackTrace();
                }
                return;
            }
        }
         System.err.println("Warning: Could not find tab matching table '" + tableName + "' to add warning/suggestion.");
    }


    // --- Helper Utilities (Overloaded for CustomTableModel) ---
    // findColumn for DefaultTableModel (used by buildTableModel helper)
    private int findColumn(DefaultTableModel model, String name) {
        if (name == null || model == null) return -1;
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(model.getColumnName(i))) { return i; }
        } return -1;
    }
    // findColumn for CustomTableModel (used by performMicroAnalysis)
    private int findColumn(CustomTableModel model, String name) {
        if (name == null || model == null) return -1;
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(model.getColumnName(i))) { return i; }
        } return -1;
    }

    // getColumnValue for DefaultTableModel (needed if used elsewhere)
     private String getColumnValue(DefaultTableModel model, int row, int col, String defaultValue) {
         if (model == null || row < 0 || col < 0 || row >= model.getRowCount() || col >= model.getColumnCount()) { return defaultValue; }
         Object val = model.getValueAt(row, col);
         return (val == null) ? defaultValue : val.toString();
     }
    // getColumnValue for CustomTableModel
     private String getColumnValue(CustomTableModel model, int row, int col, String defaultValue) {
         if (model == null || row < 0 || col < 0 || row >= model.getRowCount() || col >= model.getColumnCount()) { return defaultValue; }
         Object val = model.getValueAt(row, col);
         return (val == null) ? defaultValue : val.toString();
     }

    // getLongValue for DefaultTableModel (needed if used elsewhere)
    private long getLongValue(DefaultTableModel model, int row, int col) {
        String strValue = getColumnValue(model, row, col, "0");
        try { if (strValue.contains(".")) { return (long) Double.parseDouble(strValue); } return Long.parseLong(strValue); }
        catch (NumberFormatException e) { System.err.println("Could not parse long: '" + strValue + "'"); return 0; }
    }
    // getLongValue for CustomTableModel
    private long getLongValue(CustomTableModel model, int row, int col) {
        String strValue = getColumnValue(model, row, col, "0");
        try { if (strValue.contains(".")) { return (long) Double.parseDouble(strValue); } return Long.parseLong(strValue); }
        catch (NumberFormatException e) { System.err.println("Could not parse long: '" + strValue + "'"); return 0; }
    }

    /** Highlights a tab's title color. (Unchanged) */
    private void highlightTableTab(JTabbedPane tabPane, String tableName, Color highlightColor) {
         if (tableName == null || tabPane == null) return;
        for (int i = 0; i < tabPane.getTabCount(); i++) { String tabTitle = tabPane.getTitleAt(i); String simpleTableName = tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1) : tableName;
            if (simpleTableName.equalsIgnoreCase(tabTitle)) { Component tabComp = tabPane.getTabComponentAt(i); Color origColor = tabPane.getForegroundAt(i);
                if (tabComp != null && (origColor.equals(Color.RED) || origColor.equals(Color.ORANGE))) { /* Skip */ }
                else { JComponent contentComp = (JComponent) tabPane.getComponentAt(i); if (contentComp.getClientProperty("originalTabForeground") == null) { contentComp.putClientProperty("originalTabForeground", origColor); } }
                // Check index valid before setting
                if(i < tabPane.getTabCount()) { tabPane.setForegroundAt(i, highlightColor); }
                return;
            }
        } System.err.println("Warn: No tab found for '" + tableName + "' to highlight.");
    }

    /** Determines EDbVendor based on JDBC URL prefix. (Unchanged) */
     private EDbVendor determineDbVendor(String jdbcUrl) {
          if (jdbcUrl == null) return EDbVendor.dbvansi; String urlLower = jdbcUrl.toLowerCase();
         if (urlLower.startsWith("jdbc:mysql:")) return EDbVendor.dbvmysql; if (urlLower.startsWith("jdbc:mariadb:")) return EDbVendor.dbvmysql; // Treat as MySQL for Gudu
         if (urlLower.startsWith("jdbc:postgresql:")) return EDbVendor.dbvpostgresql; if (urlLower.startsWith("jdbc:oracle:")) return EDbVendor.dbvoracle;
         if (urlLower.startsWith("jdbc:sqlserver:")) return EDbVendor.dbvmssql; return EDbVendor.dbvansi; // Gudu's unknown
     }

    /** Main method. (Unchanged) */
    public static void main(String[] args) {
        try { FlatIntelliJLaf.setup(); } catch (Exception e) { e.printStackTrace(); try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { ex.printStackTrace(); } }
        SwingUtilities.invokeLater(() -> new FrmQueryAnalyzer().setVisible(true));
    }

}