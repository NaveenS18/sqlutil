package in.mystrn.sqlutil.forms;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import javax.swing.table.DefaultTableModel;

// --- Imports ---
import com.formdev.flatlaf.FlatIntelliJLaf; // Or your chosen FlatLaf theme

import gudusoft.gsqlparser.EDbVendor;
import gudusoft.gsqlparser.TGSqlParser;
import gudusoft.gsqlparser.TSourceToken; // Needed for extracting SQL substring
import gudusoft.gsqlparser.nodes.TParseTreeNode; // Needed for statement iteration
import gudusoft.gsqlparser.stmt.TDeleteSqlStatement; // Needed for EXPLAIN check
import gudusoft.gsqlparser.stmt.TInsertSqlStatement; // Needed for EXPLAIN check
import gudusoft.gsqlparser.stmt.TSelectSqlStatement; // Needed for EXPLAIN check & alias map
import gudusoft.gsqlparser.stmt.TUpdateSqlStatement; // Needed for EXPLAIN check
import in.mystrn.sqlutil.utils.ErrorDialog;
import in.mystrn.sqlutil.utils.ProcessingDialog;
import in.mystrn.sqlutil.utils.ProcessingTask;
import in.mystrn.sqlutil.utils.QueryAnalyzerUtil; // Your Gudu Util

/**
 * SQL Query Analyzer GUI using Gudu SQL Parser for structural analysis
 * and JDBC for EXPLAIN plan, with FlatLaf UI, custom fonts, and Enter key binding.
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
        // Default vendor can be set here if desired, otherwise detected from URL
        // queryAnalyzerUtil.vendor = EDbVendor.dbvmysql;

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
        JSplitPane resultsSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tableScrollPane, resultsTabbedPane);
        resultsSplitPane.setDividerLocation(300);
        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, queryScrollPane, resultsSplitPane);
        mainSplitPane.setDividerLocation(400);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(connectionPanel, BorderLayout.CENTER);
        topPanel.add(toolBar, BorderLayout.SOUTH);
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

                        try {
                            messageUpdater.accept("Determining database vendor...");
                            EDbVendor detectedVendor = determineDbVendor(jdbcUrl);
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

                            // Find the first explainable statement (SELECT/INSERT/UPDATE/DELETE)
                            for (int i = 0; i < parser.sqlstatements.size(); i++) {
                                TParseTreeNode stmtNode = parser.sqlstatements.get(i);
                                if (stmtNode instanceof TSelectSqlStatement || stmtNode instanceof TInsertSqlStatement
                                    || stmtNode instanceof TUpdateSqlStatement || stmtNode instanceof TDeleteSqlStatement)
                                {
                                    TSourceToken startToken = stmtNode.getStartToken();
                                    TSourceToken endToken = stmtNode.getEndToken();
                                    // Ensure tokens are valid before substring
                                    if (startToken != null && endToken != null) {
                                        explainableStatementSql = sqlQuery.substring(
                                                (int) startToken.offset,
                                                (int) (endToken.offset + endToken.astext.length())
                                        ).trim();
                                        if (stmtNode instanceof TSelectSqlStatement) {
                                             explainableSelectStatement = (TSelectSqlStatement) stmtNode;
                                        }
                                        break; // Found the first one
                                    }
                                }
                            }

                            messageUpdater.accept("Performing structural analysis...");
                            // Run analysis on the potentially multi-statement script
                            Map<String, Object> analysisResult = queryAnalyzerUtil.analyzeQueryStructure(sqlQuery);

                            SwingUtilities.invokeLater(() -> updateAnalysisDisplay(analysisResult));
                            if (!Boolean.TRUE.equals(analysisResult.get("isValid"))) return; // Stop if parsing failed

                            if (explainableStatementSql == null) {
                                messageUpdater.accept("No explainable (SELECT/INSERT/UPDATE/DELETE) statement found in script.");
                                SwingUtilities.invokeLater(() -> explainTable.setModel(new DefaultTableModel()));
                                return; // Nothing to EXPLAIN
                            }

                            messageUpdater.accept("Connecting to database...");
                            connection = getConnection();

                            messageUpdater.accept("Executing EXPLAIN command...");
                            String explainQuery = "EXPLAIN " + explainableStatementSql;
                            DefaultTableModel explainTableModel;
                            Map<String, String> aliasToTableMapForExplain = new HashMap<>();

                            try (Statement stmt = connection.createStatement();
                                 ResultSet rs = stmt.executeQuery(explainQuery)) {
                                explainTableModel = buildTableModel(rs);

                                // Build alias map only if it was a SELECT statement
                                if (explainableSelectStatement != null) {
                                    aliasToTableMapForExplain.putAll(queryAnalyzerUtil.buildAliasMap(explainableSelectStatement));
                                }

                            } catch (SQLException explainEx) {
                                if (explainEx.getMessage().toLowerCase().contains("unknown column") || explainEx.getMessage().toLowerCase().contains("unknown variable")) {
                                     throw new Exception("Error executing EXPLAIN: Database doesn't recognize variables like '@workspace_id'. Remove SET commands and replace variables with literal values in your query before analyzing.", explainEx);
                                } else {
                                    throw new Exception("Error executing EXPLAIN: " + explainEx.getMessage(), explainEx);
                                }
                            }

                            final Map<String, String> finalAliasMap = aliasToTableMapForExplain;
                            // Ensure analysisResult is accessible in EDT lambda
                            final Map<String, Map<String, Object>> finalTableInfo = (Map<String, Map<String, Object>>) analysisResult.get("tableInfo");

                            SwingUtilities.invokeLater(() -> {
                                explainTable.setModel(explainTableModel);
                                performMicroAnalysis(explainTableModel, resultsTabbedPane, finalAliasMap, finalTableInfo);
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
     * Creates the panel for JDBC connection inputs. (Unchanged)
     */
    /**
     * Creates the panel for JDBC connection inputs.
     * --- UPDATED to add Enter key focus traversal ---
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: JDBC Driver
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Driver Class:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        jdbcDriverField = new JTextField("com.mysql.cj.jdbc.Driver", 30);
        panel.add(jdbcDriverField, gbc);
        // --- ADD Action Listener for Driver Field ---
        jdbcDriverField.addActionListener(e -> jdbcUrlField.requestFocusInWindow()); // Move focus to URL field on Enter

        // Row 0: Username
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        usernameField = new JTextField("root", 15);
        panel.add(usernameField, gbc);
        // --- ADD Action Listener for Username Field ---
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow()); // Move focus to Password field on Enter

        // Row 1: JDBC URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("JDBC URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        jdbcUrlField = new JTextField("jdbc:mysql://localhost:3306/your_database_name", 30);
        panel.add(jdbcUrlField, gbc);
        // --- ADD Action Listener for URL Field ---
        jdbcUrlField.addActionListener(e -> usernameField.requestFocusInWindow()); // Move focus to Username field on Enter

        // Row 1: Password
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        passwordField = new JPasswordField(15);
        panel.add(passwordField, gbc);
        // --- ADD Action Listener for Password Field ---
        passwordField.addActionListener(e -> queryInputArea.requestFocusInWindow()); // Move focus to Query Area on Enter

        return panel;
    }

    /**
     * Establishes and returns a database connection based on GUI inputs. (Unchanged)
     */
    private Connection getConnection() throws SQLException, ClassNotFoundException {
        String jdbcDriver = jdbcDriverField.getText();
        String dbUrl = jdbcUrlField.getText();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        if (jdbcDriver == null || jdbcDriver.trim().isEmpty()) {
            throw new ClassNotFoundException("JDBC Driver class name is empty.");
        }
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            throw new SQLException("JDBC URL is empty.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new SQLException("Username is empty.");
        }
        Class.forName(jdbcDriver.trim());
        return DriverManager.getConnection(dbUrl.trim(), username.trim(), password);
    }

    /**
     * Removes all dynamic table tabs, keeping the first "Analysis" tab. (Unchanged)
     */
    private void clearTableTabs() {
        int tabCount = resultsTabbedPane.getTabCount();
        for (int i = tabCount - 1; i > 0; i--) {
            resultsTabbedPane.remove(i);
        }
    }

    /**
     * Updates the main analysis text area and table tabs based on Gudu analysis results.
     * Should be called on the EDT.
     */
    private void updateAnalysisDisplay(Map<String, Object> analysisResult) {
        clearTableTabs();
        explainTable.setModel(new DefaultTableModel());
        analysisTextArea.setText("");

        if (!Boolean.TRUE.equals(analysisResult.get("isValid"))) {
            String errorMsg = "--- SQL Parse Error ---\n" + analysisResult.get("error");
            analysisTextArea.setText(errorMsg);
            // Optionally show ErrorDialog, but text area already shows it.
            // ErrorDialog.showError(this, "SQL Parsing Error:\n" + analysisResult.get("error"));
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
             sb.append("\n--- Query Statistics ---\n(No specific stats gathered for this statement type)\n");
        }

        // --- UPDATED to handle List<PerformanceHint> ---
        List<?> hintObjects = (List<?>) analysisResult.get("performanceHints");
        if (hintObjects != null && !hintObjects.isEmpty()) {
            sb.append("\n--- Performance Hints (Structural) ---\n");
            for(Object hintObj : hintObjects) {
                if (hintObj instanceof QueryAnalyzerUtil.PerformanceHint) {
                    // Use the toString() method of PerformanceHint
                    sb.append(hintObj.toString()).append("\n\n"); // Add extra newline
                } else {
                     sb.append("- ").append(hintObj.toString()).append("\n"); // Fallback if not PerformanceHint
                }
            }
        }
        // --- END UPDATE ---

        analysisTextArea.setText(sb.toString());

        Map<String, Map<String, Object>> tableInfo = (Map<String, Map<String, Object>>) analysisResult.get("tableInfo");
        if (tableInfo != null && !tableInfo.isEmpty()) { // Check not empty
            createTableTabsFromAnalysis(tableInfo);
        } else {
             System.out.println("tableInfo is null or empty. No table tabs created.");
        }
    }

    /**
     * Creates table tabs based on the tableInfo map from QueryAnalyzerUtil. Applies custom font.
     */
    private void createTableTabsFromAnalysis(Map<String, Map<String, Object>> tableInfo) {
        Vector<String> columnNames = new Vector<>(List.of("Detail", "Value"));

        for (Map.Entry<String, Map<String, Object>> entry : tableInfo.entrySet()) {
            String tableName = entry.getKey();
            Map<String, Object> details = entry.getValue();
            if (details == null) continue; // Skip if details map is null

            Vector<Vector<Object>> data = new Vector<>();

            // Safely get sets, defaulting to empty if null
            Set<?> allColsSet = (Set<?>) details.getOrDefault("allColumnsUsed", Set.of());
            Set<?> whereColsSet = (Set<?>) details.getOrDefault("whereColumns", Set.of());
            Set<?> funcColsSet = (Set<?>) details.getOrDefault("columnsWithFunctionsInWhere", Set.of());
            String alias = details.getOrDefault("aliasUsed", "N/A").toString();

            // Convert Sets to Strings for display
            String allCols = allColsSet.isEmpty() ? "(None Detected)" : allColsSet.stream().map(Object::toString).collect(Collectors.joining(", "));
            String whereCols = whereColsSet.isEmpty() ? "(None)" : whereColsSet.stream().map(Object::toString).collect(Collectors.joining(", "));
            String funcCols = funcColsSet.isEmpty() ? "(None)" : funcColsSet.stream().map(Object::toString).collect(Collectors.joining(", "));


            data.add(new Vector<>(List.of("Alias Used", alias)));
            data.add(new Vector<>(List.of("All Columns Used", allCols)));
            data.add(new Vector<>(List.of("WHERE Columns", whereCols)));
            data.add(new Vector<>(List.of("Funcs on WHERE Cols", funcCols)));

             // Add structural warning derived from Gudu analysis
             if (!funcColsSet.isEmpty()) {
                 data.add(0,new Vector<>(List.of("--- WARNING ---", "Function on WHERE column(s): " + funcCols)));
                 data.add(1,new Vector<>(List.of("", "May prevent index usage.")));
                 data.add(2,new Vector<>(List.of("", ""))); // Spacer
             }

            DefaultTableModel statsModel = new DefaultTableModel(data, columnNames);
            JTable statsTable = new JTable(statsModel);
            statsTable.setFont(MONOSPACED_FONT); // Apply font
            statsTable.setFillsViewportHeight(true);
            statsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            statsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
            statsTable.getColumnModel().getColumn(1).setPreferredWidth(400);

            JScrollPane tableScrollPane = new JScrollPane(statsTable);
            resultsTabbedPane.addTab(tableName, tableScrollPane);
        }
    }

    /**
     * Builds a TableModel from a ResultSet. (Unchanged)
     */
    public static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
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
                // Handle potential nulls from DB gracefully
                Object value = rs.getObject(columnIndex);
                row.add(value != null ? value : ""); // Add empty string for null
            }
            data.add(row);
        }
        return new DefaultTableModel(data, columnNames);
    }

    /**
     * Performs analysis based on the actual EXPLAIN plan from the database. (Unchanged - includes detailed suggestions)
     */
    private void performMicroAnalysis(DefaultTableModel explainModel,
                                      JTabbedPane resultsTabbedPane,
                                      Map<String, String> aliasToTableMap,
                                      Map<String, Map<String, Object>> guduTableInfo) {

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
                String rowId = getColumnValue(explainModel, i, 0, "Row " + (i+1));
                String currentAlias = getColumnValue(explainModel, i, tableCol, null);
                String fullTableName = (currentAlias != null && aliasToTableMap != null) ? aliasToTableMap.get(currentAlias.toLowerCase()) : null;

                // Safely get column sets from Gudu info
                Set<String> filteringCols = Set.of();
                Set<String> funcColsInWhere = Set.of();
                if (fullTableName != null && guduTableInfo != null) {
                    Map<String, Object> tableDetails = guduTableInfo.get(fullTableName);
                    if (tableDetails != null) {
                        filteringCols = (Set<String>) tableDetails.getOrDefault("whereColumns", Set.of());
                        funcColsInWhere = (Set<String>) tableDetails.getOrDefault("columnsWithFunctionsInWhere", Set.of());
                    }
                }

                // Suggestion 1: Full Table Scan
                String scanType = getColumnValue(explainModel, i, typeCol, "");
                if ("ALL".equalsIgnoreCase(scanType)) {
                    String detail = String.format("[%s %s] SEVERE: Full Table Scan ('type' is 'ALL'). ", rowId, currentAlias!=null?currentAlias:"");
                    String why = "Why: The database read every row. Usually inefficient.";
                    String suggestion = "Suggestion: Create or improve indexes on columns used in JOIN conditions or WHERE clauses for this table.";
                    if (!filteringCols.isEmpty()) {
                        suggestion += " Candidate columns: [" + String.join(", ", filteringCols) + "]";
                    } else if (fullTableName != null) {
                        suggestion += " Check columns used to JOIN '" + currentAlias + "' to other tables.";
                    }
                    suggestions.append(detail).append(why).append("\n  > ").append(suggestion).append("\n");
                    if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Full Table Scan", suggestion); }
                }

                // Suggestion 2: Index not used
                String actualKey = getColumnValue(explainModel, i, keyCol, null);
                String possibleKeys = getColumnValue(explainModel, i, possibleKeysCol, null);
                if (possibleKeys != null && !possibleKeys.isEmpty() && (actualKey == null || actualKey.isEmpty() || "NULL".equalsIgnoreCase(actualKey))) { // Check for "NULL" string too
                     String warning = String.format("Possible keys (%s) found, but none used.", possibleKeys);
                     StringBuilder explanation = new StringBuilder("Potential reasons why index wasn't applied: \n");
                     boolean reasonFound = false;
                     if (!funcColsInWhere.isEmpty()) {
                         Set<String> problematicCols = new HashSet<>(filteringCols);
                         problematicCols.retainAll(funcColsInWhere);
                         if (!problematicCols.isEmpty()) { explanation.append("    - Function applied to indexed column(s): [").append(String.join(", ", problematicCols)).append("]. This prevents index use.\n"); reasonFound = true; }
                     }
                     explanation.append("    - Data type mismatch in JOIN or WHERE conditions involving filtering columns");
                     if (!filteringCols.isEmpty()) { explanation.append(": [").append(String.join(", ", filteringCols)).append("]"); }
                     explanation.append(".\n");
                     explanation.append("    - Optimizer estimated scanning rows directly is faster (e.g., small table or low selectivity).\n");
                     if (!reasonFound && !funcColsInWhere.isEmpty()) { explanation.append("    - Note: Functions were detected in WHERE, but maybe not on the relevant indexed columns: [").append(String.join(", ", funcColsInWhere)).append("].\n"); }

                     suggestions.append(String.format("[%s %s] WARNING: Index potentially available but not used.\n  > %s\n  > %s", rowId, currentAlias!=null?currentAlias:"", warning, explanation.toString()));
                     if (fullTableName != null) {
                         // Pass detailed explanation to tab
                         addWarningToTableTab(resultsTabbedPane, fullTableName, "Index Not Used", explanation.toString().replace("\n    - ","\n").trim());
                     }
                }

                // Suggestion 3: Large row scans
                 long rowsScanned = getLongValue(explainModel, i, rowsCol);
                 if (rowsScanned > 10000) {
                     suggestions.append(String.format("[%s %s] INFO: Estimated %d rows scanned.\n  > SUGGESTION: If slow, ensure WHERE/JOIN clause using indexed columns is selective.\n", rowId, currentAlias!=null?currentAlias:"", rowsScanned));
                 }
                // Suggestion 4 & 5: Using filesort / temporary
                String extraInfo = getColumnValue(explainModel, i, extraCol, "");
                if (extraInfo.contains("Using filesort")) {
                    String suggestion = "Suggestion: Add index covering columns in ORDER BY clause.";
                    suggestions.append(String.format("[%s %s] WARNING: 'Using filesort'.\n  > %s\n", rowId, currentAlias!=null?currentAlias:"", suggestion));
                     if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Filesort Used", suggestion); }
                }
                if (extraInfo.contains("Using temporary")) {
                     String suggestion = "Suggestion: Temp table needed (slow). Common for complex GROUP BY/UNION/subqueries. Simplify if possible.";
                    suggestions.append(String.format("[%s %s] WARNING: 'Using temporary'.\n  > %s\n", rowId, currentAlias!=null?currentAlias:"", suggestion));
                    if (fullTableName != null) { addWarningToTableTab(resultsTabbedPane, fullTableName, "Temporary Table Used", suggestion); }
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
    }

    /** Finds a table's specific tab and adds a warning row. (Corrected) */
    private void addWarningToTableTab(JTabbedPane tabPane, String tableName, String warningType, String message) {
        for (int i = 1; i < tabPane.getTabCount(); i++) { // Start at 1 to skip "Analysis" tab
            String tabTitle = tabPane.getTitleAt(i);
            // Handle potential schema prefix in tableName from parser vs. simple name in tab
            String simpleTableName = tableName.contains(".") ? tableName.substring(tableName.lastIndexOf(".") + 1) : tableName;

            if (simpleTableName.equalsIgnoreCase(tabTitle)) { // Case-insensitive compare of simple name
                try {
                    JScrollPane scrollPane = (JScrollPane) tabPane.getComponentAt(i);
                    JTable table = (JTable) scrollPane.getViewport().getView();
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    boolean exists = false;
                    // Check if a similar warning already exists at the top
                    if (model.getRowCount() > 0 && "--- WARNING ---".equals(model.getValueAt(0, 0)) && warningType.equals(model.getValueAt(0, 1)) ){
                         exists = true;
                    }
                    if (!exists) {
                        model.insertRow(0, new Object[]{"--- WARNING ---", warningType, message});
                        model.insertRow(1, new Object[]{"", "", ""}); // Spacer
                        tabPane.setSelectedIndex(i);
                    }
                } catch (Exception e) {
                    System.err.println("Error adding warning to tab '" + tableName + "' (Title: " + tabTitle + "): " + e.getMessage());
                    e.printStackTrace();
                }
                return; // Found tab
            }
        }
         System.err.println("Warning: Could not find tab matching table '" + tableName + "' to add warning.");
    }

    // --- Helper Utilities ---
    private int findColumn(DefaultTableModel model, String name) {
        if (name == null || model == null) return -1;
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(model.getColumnName(i))) {
                return i;
            }
        }
        return -1;
    }

     private String getColumnValue(DefaultTableModel model, int row, int col, String defaultValue) {
         if (model == null || row < 0 || col < 0 || row >= model.getRowCount() || col >= model.getColumnCount()) {
             return defaultValue;
         }
         Object val = model.getValueAt(row, col);
         return (val == null) ? defaultValue : val.toString();
     }

    private long getLongValue(DefaultTableModel model, int row, int col) {
        String strValue = getColumnValue(model, row, col, "0");
        try {
            if (strValue.contains(".")) {
                return (long) Double.parseDouble(strValue);
            }
            return Long.parseLong(strValue);
        } catch (NumberFormatException e) {
            System.err.println("Could not parse long from value: '" + strValue + "'");
            return 0;
        }
    }

    /** Determines EDbVendor based on JDBC URL prefix. (Unchanged) */
     private EDbVendor determineDbVendor(String jdbcUrl) {
         if (jdbcUrl == null) return EDbVendor.dbvansi;
         String urlLower = jdbcUrl.toLowerCase();
         if (urlLower.startsWith("jdbc:mysql:")) return EDbVendor.dbvmysql;
         if (urlLower.startsWith("jdbc:mariadb:")) return EDbVendor.dbvmysql;
         if (urlLower.startsWith("jdbc:postgresql:")) return EDbVendor.dbvpostgresql;
         if (urlLower.startsWith("jdbc:oracle:")) return EDbVendor.dbvoracle;
         if (urlLower.startsWith("jdbc:sqlserver:")) return EDbVendor.dbvmssql;
         return EDbVendor.dbvansi;
     }

    /**
     * Main method to run the application.
     */
    public static void main(String[] args) {
        try {
            // Apply FlatLaf look and feel
            FlatIntelliJLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to system L&F if FlatLaf fails
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ex) { ex.printStackTrace(); }
        }

        SwingUtilities.invokeLater(() -> new FrmQueryAnalyzer().setVisible(true));
    }
}