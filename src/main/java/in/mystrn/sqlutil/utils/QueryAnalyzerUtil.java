package in.mystrn.sqlutil.utils;

import gudusoft.gsqlparser.EDbObjectType;
import gudusoft.gsqlparser.EDbVendor;
import gudusoft.gsqlparser.EExpressionType;
import gudusoft.gsqlparser.EFunctionType;
import gudusoft.gsqlparser.EJoinType;
import gudusoft.gsqlparser.ESetOperatorType;
import gudusoft.gsqlparser.TCustomSqlStatement;
import gudusoft.gsqlparser.TGSqlParser;
import gudusoft.gsqlparser.TSourceToken;
import gudusoft.gsqlparser.TSyntaxError;
import gudusoft.gsqlparser.nodes.*; // Using wildcard for brevity
import gudusoft.gsqlparser.stmt.TDeleteSqlStatement;
import gudusoft.gsqlparser.stmt.TInsertSqlStatement;
import gudusoft.gsqlparser.stmt.TSelectSqlStatement;
import gudusoft.gsqlparser.stmt.TUpdateSqlStatement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enhanced Utility for Handling SQL Statements parsing using Gudu SQL Parser.
 *
 * Provides detailed structural analytics and beginner-friendly performance
 * hints.
 *
 * @author hive
 */
public class QueryAnalyzerUtil {

	public EDbVendor vendor;
	public String error;

	/**
	 * Checks if the provided SQL string is valid according to the specified vendor.
	 * 
	 * @param sql The SQL query string.
	 * @return true if the SQL is valid, false otherwise. Populates 'error' field on
	 *         failure.
	 */
	public boolean isValidSQL(String sql) {
		TGSqlParser parser = new TGSqlParser(vendor);
		parser.sqltext = sql;
		int result = parser.parse();

		if (result == 0) {
			this.error = null; // Clear previous errors
			return true;
		}

		StringBuilder errorBuilder = new StringBuilder();
		for (TSyntaxError syntaxError : parser.getSyntaxErrors()) {
			// Use getMsg() for a more descriptive error
			errorBuilder.append("Line: ").append(syntaxError.lineNo).append(", Col: ").append(syntaxError.columnNo)
					.append(" (Near '").append(syntaxError.tokentext).append("')\n");
		}
		this.error = errorBuilder.toString();
		return false;
	}

	/**
	 * Parses a SQL query and extracts structural analytics and potential
	 * performance hints.
	 *
	 * @param sql The SQL query string.
	 * @return A Map containing analysis results: - "isValid": boolean - "error":
	 *         String (if !isValid) - "statementType": String (e.g., "SELECT",
	 *         "UPDATE") - "queryStats": Map<String, Object> (counts of joins,
	 *         selects, etc.) - "tableInfo": Map<String, Map<String, Object>>
	 *         (details per table) - "performanceHints": List<PerformanceHint>
	 *         (potential issues based on structure)
	 * @throws Exception If parsing fails unexpectedly.
	 */
	public Map<String, Object> analyzeQueryStructure(String sql) throws Exception {
		Map<String, Object> analysisResult = new LinkedHashMap<>();
		TGSqlParser parser = new TGSqlParser(vendor);
		parser.sqltext = sql;

		if (parser.parse() != 0) {
			isValidSQL(sql); // Populate this.error
			analysisResult.put("isValid", false);
			analysisResult.put("error", this.error);
			return analysisResult;
		}

		analysisResult.put("isValid", true);
		analysisResult.put("error", null);

		if (parser.sqlstatements.size() == 0) {
			analysisResult.put("statementType", "EMPTY");
			analysisResult.put("performanceHints", List.of(new PerformanceHint(PerformanceHint.Severity.INFO,
					"Empty Query", "The input string contained no SQL statements.", "Enter a valid SQL query.")));
			return analysisResult;
		}

		// --- Analyze the first statement ---
		TParseTreeNode statement = parser.sqlstatements.get(0);
		String statementTypeStr = "UNKNOWN"; // Default, will be overwritten

		List<PerformanceHint> hints = new ArrayList<>();
		Map<String, Object> queryStats = new HashMap<>();
		Map<String, Map<String, Object>> tableInfo = new LinkedHashMap<>();

		// --- Specific Analysis for SELECT Statements ---
		if (statement instanceof TSelectSqlStatement) {
			TSelectSqlStatement select = (TSelectSqlStatement) statement;
			statementTypeStr = select.sqlstatementtype.name(); // CORRECT access

			Map<String, String> aliasToTableMap = buildAliasMap(select);
			tableInfo = extractTableDetails(select, aliasToTableMap);
			queryStats = gatherQueryStats(select, tableInfo);
			hints = generatePerformanceHints(select, tableInfo, queryStats);

		} else {
			// --- Enhanced handling for other statement types ---
			tableInfo = extractGeneralTableUsage(statement); // Populate basic table info first

			if (statement instanceof TInsertSqlStatement) {
				TInsertSqlStatement insert = (TInsertSqlStatement) statement;
				statementTypeStr = insert.sqlstatementtype.toString(); // CORRECT access
				queryStats = gatherGeneralStats(statement);
				hints = generateGeneralHints(statement, tableInfo, queryStats);

			} else if (statement instanceof TUpdateSqlStatement) {
				TUpdateSqlStatement update = (TUpdateSqlStatement) statement;
				statementTypeStr = update.sqlstatementtype.toString(); // CORRECT access
				queryStats = gatherGeneralStats(statement);
				hints = generateGeneralHints(statement, tableInfo, queryStats);

			} else if (statement instanceof TDeleteSqlStatement) {
				TDeleteSqlStatement delete = (TDeleteSqlStatement) statement;
				statementTypeStr = delete.sqlstatementtype.toString(); // CORRECT access
				queryStats = gatherGeneralStats(statement);
				hints = generateGeneralHints(statement, tableInfo, queryStats);

			} else if (statement instanceof TCustomSqlStatement) {
				TCustomSqlStatement customStmt = (TCustomSqlStatement) statement;
				statementTypeStr = customStmt.sqlstatementtype.toString(); // CORRECT access
				queryStats = gatherGeneralStats(statement);
				hints = generateGeneralHints(statement, tableInfo, queryStats);
				hints.clear(); // Clear any generic hints
				hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "DDL/Custom Statement",
						"Statement type: " + statementTypeStr, "Structural analysis is basic for this type."));

			} else {
				statementTypeStr = statement.getClass().getSimpleName(); // Use class name as fallback
				queryStats = gatherGeneralStats(statement); // Get whatever stats we can
				hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "Unsupported Type",
						"Analysis for " + statementTypeStr + " is basic.", "Focus on database-specific tools."));
			}
		}

		// Add results to the final map
		analysisResult.put("statementType", statementTypeStr);
		analysisResult.put("tableInfo", tableInfo);
		analysisResult.put("queryStats", queryStats);
		analysisResult.put("performanceHints", hints);

		return analysisResult;
	}

	// --- PerformanceHint Class ---
	public static class PerformanceHint {
		public enum Severity {
			INFO, WARN, SEVERE
		}

		public Severity severity;
		public String title;
		public String explanation;
		public String suggestion;

		public PerformanceHint(Severity severity, String title, String explanation, String suggestion) {
			this.severity = severity;
			this.title = title;
			this.explanation = explanation;
			this.suggestion = suggestion;
		}

		@Override
		public String toString() {
			return String.format("[%s] %s\n  Why: %s\n  Suggestion: %s", severity, title, explanation, suggestion);
		}
	}

	// --- Helper Methods ---

	/** Builds alias map. (Unchanged) */
	public Map<String, String> buildAliasMap(TSelectSqlStatement select) {
		Map<String, String> aliasMap = new HashMap<>();
		if (select.tables != null) {
			for (TTable table : select.tables) {
				String tableName = table.getTableName().toString();
				String alias = tableName;
				if (table.getAliasName() != null && !table.getAliasName().toString().isEmpty()) {
					alias = table.getAliasName().toString();
				}
				aliasMap.put(alias.toLowerCase(), tableName);
			}
		}
		return aliasMap;
	}

	/**
	 * Extracts details (WHERE, Functions, ALL, GROUP BY, ORDER BY cols).
	 * (Unchanged)
	 */
	private Map<String, Map<String, Object>> extractTableDetails(TSelectSqlStatement select,
			Map<String, String> aliasToTableMap) {
		Map<String, Map<String, Object>> tableInfo = new LinkedHashMap<>();

		if (select.tables != null) {
			for (TTable table : select.tables) {
				String tableName = table.getTableName().toString();
				String mapKey = table.getAliasName() != null ? table.getAliasName().toString() : tableName;
				String actualTableName = aliasToTableMap.getOrDefault(mapKey.toLowerCase(), mapKey);
				tableInfo.computeIfAbsent(actualTableName, k -> {
					Map<String, Object> data = new HashMap<>();
					data.put("whereColumns", new HashSet<String>());
					data.put("columnsWithFunctionsInWhere", new HashSet<String>());
					data.put("allColumnsUsed", new HashSet<String>());
					data.put("groupByColumns", new HashSet<String>());
					data.put("orderByColumns", new HashSet<String>());
					data.put("sourceToken", table.getStartToken());
					data.put("aliasUsed", mapKey);
					return data;
				});
			}
		}

		if (select.getWhereClause() != null && select.getWhereClause().getCondition() != null) {
			WhereClauseColumnVisitor whereVisitor = new WhereClauseColumnVisitor(aliasToTableMap, tableInfo);
			select.getWhereClause().getCondition().accept(whereVisitor);
		}

		AllUsageColumnVisitor allUsageVisitor = new AllUsageColumnVisitor(aliasToTableMap, tableInfo);
		select.accept(allUsageVisitor);

		return tableInfo;
	}

	/** Gathers detailed stats about SELECT query structure. (Unchanged) */
	private Map<String, Object> gatherQueryStats(TSelectSqlStatement select,
			Map<String, Map<String, Object>> tableInfo) {
		Map<String, Object> stats = new LinkedHashMap<>();
		GeneralStatsVisitor statsVisitor = new GeneralStatsVisitor();
		select.accept(statsVisitor);

		stats.put("selectItemCount", select.getResultColumnList() == null ? 0 : select.getResultColumnList().size());
		stats.put("usesSelectStar", statsVisitor.selectStarCount > 0);
		stats.put("tableCount", select.tables == null ? 0 : select.tables.size());
		stats.put("joinCount", select.joins == null ? 0 : select.joins.size());

		List<String> joinTypes = new ArrayList<>();
		if (select.joins != null) {
			for (TJoin join : select.joins) {
				joinTypes.add(getJoinTypeString(join.getKind()));
			}
		}
		stats.put("joinTypes", joinTypes);

		stats.put("hasWhereClause", select.getWhereClause() != null);
		stats.put("whereConditionComplexity", statsVisitor.whereConditionCount);
		stats.put("hasGroupByClause", select.getGroupByClause() != null);
		stats.put("groupByItemCount",
				select.getGroupByClause() == null ? 0 : select.getGroupByClause().getItems().size());
		stats.put("hasOrderByClause", select.getOrderbyClause() != null);
		stats.put("orderByItemCount",
				select.getOrderbyClause() == null ? 0 : select.getOrderbyClause().getItems().size());
		stats.put("hasLimitClause", select.getLimitClause() != null);
		stats.put("isDistinct", select.getSelectDistinct() != null);
		stats.put("setOperation",
				select.getSetOperatorType() != ESetOperatorType.none ? select.getSetOperatorType().toString() : "None");
		// Subquery count removed as it was unreliable
		stats.put("functionCallCount", statsVisitor.functionCallCount);
		stats.put("windowFunctionCount", statsVisitor.windowFunctionCount);
		stats.put("aggregateFunctionCount", statsVisitor.aggregateFunctionCount);

		long totalWhereCols = tableInfo.values().stream()
				.mapToLong(m -> ((Set<?>) m.getOrDefault("whereColumns", Set.of())).size()).sum();
		long totalFuncOnWhereCols = tableInfo.values().stream()
				.mapToLong(m -> ((Set<?>) m.getOrDefault("columnsWithFunctionsInWhere", Set.of())).size()).sum();
		stats.put("totalWhereColumnsUsed", totalWhereCols);
		stats.put("totalFunctionsOnWhereColumns", totalFuncOnWhereCols);

		return stats;
	}

	/** Converts Gudu join int to String using enum constants. (Unchanged) */
	private String getJoinTypeString(int joinKindInt) {
		return EJoinType.values()[joinKindInt].name();
	}

	/**
	 * Generates detailed, beginner-friendly performance hints as PerformanceHint
	 * objects. (Unchanged)
	 */
	private List<PerformanceHint> generatePerformanceHints(TSelectSqlStatement select,
			Map<String, Map<String, Object>> tableInfo, Map<String, Object> queryStats) {
		List<PerformanceHint> hints = new ArrayList<>();

		if (Boolean.TRUE.equals(queryStats.get("usesSelectStar"))) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.WARN, "Avoid SELECT *",
					"Retrieving all columns (*) forces the database to fetch potentially unnecessary data, increasing network traffic and memory usage. It also prevents certain index optimizations (covering indexes).",
					"Explicitly list only the columns your application requires in the SELECT clause."));
		}

		for (Map.Entry<String, Map<String, Object>> entry : tableInfo.entrySet()) {
			@SuppressWarnings("unchecked")
			Set<String> funcCols = (Set<String>) entry.getValue().get("columnsWithFunctionsInWhere");
			if (funcCols != null && !funcCols.isEmpty()) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.SEVERE, "Function on WHERE Column(s)",
						"Applying a function (like YEAR(), UPPER(), CONCAT()) to a column in the WHERE clause often prevents the database from using an index on that column, forcing a slower table scan. This is because the database must calculate the function's result for every row before comparing.",
						"Rewrite the condition to apply functions to the constant value instead of the column, if possible (e.g., `date_col >= '2024-01-01'` instead of `YEAR(date_col) = 2024`). Consider function-based indexes if rewriting isn't feasible (database-specific). Columns involved: ["
								+ String.join(", ", funcCols) + "] in table '" + entry.getKey() + "'."));
			}
		}

		if (!Boolean.TRUE.equals(queryStats.get("hasWhereClause"))) {
			int tableCount = (Integer) queryStats.getOrDefault("tableCount", 0);
			if (tableCount > 1) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.SEVERE, "Potential Cartesian Product",
						"The query joins multiple tables (" + tableCount
								+ ") but lacks a WHERE clause to filter the results *after* joining. If JOIN conditions are missing or insufficient, this can result in a 'Cartesian Product' - every row from one table combined with every row from another, which is usually extremely large and slow.",
						"Ensure correct and sufficient JOIN conditions (`ON tableA.col = tableB.col`) are specified for all joined tables. Add a WHERE clause if further filtering is needed."));
			} else if (tableCount == 1) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.WARN, "Potential Full Table Scan (No WHERE)",
						"The query selects from a single table without a WHERE clause. This forces the database to read every row (Full Table Scan), which can be slow for large tables.",
						"Add a WHERE clause to filter rows if you don't need the entire table's data. If the table is intentionally small, this might be acceptable."));
			}
		}

		if (select.getOrderbyClause() != null) {
			for (TOrderByItem item : select.getOrderbyClause().getItems()) {
				TExpression sortKeyExpr = item.getSortKey();
				boolean isSimpleColumn = false;
				if (sortKeyExpr != null && sortKeyExpr.getExpressionType() == EExpressionType.simple_object_name_t) {
					TObjectName objName = sortKeyExpr.getObjectOperand();
					if (objName != null && objName.getDbObjectType() == EDbObjectType.column) {
						isSimpleColumn = true;
					}
				}
				if (!isSimpleColumn) {
					hints.add(new PerformanceHint(PerformanceHint.Severity.WARN, "Expression in ORDER BY",
							"The ORDER BY clause uses an expression ('"
									+ (sortKeyExpr != null ? sortKeyExpr.toString() : "NULL")
									+ "') instead of directly referencing a column. The database must calculate this expression for rows *before* sorting, preventing the use of standard indexes for sorting.",
							"If possible, sort directly by indexed columns. Consider adding a function-based index if sorting by the expression is essential (database-specific)."));
				}
			}
		}
		if (select.getGroupByClause() != null) {
			for (TGroupByItem item : select.getGroupByClause().getItems()) {
				TExpression groupByExpr = item.getExpr();
				boolean isSimpleGroupByColumn = false;
				if (groupByExpr != null && groupByExpr.getExpressionType() == EExpressionType.simple_object_name_t) {
					TObjectName objName = groupByExpr.getObjectOperand();
					if (objName != null && objName.getDbObjectType() == EDbObjectType.column) {
						isSimpleGroupByColumn = true;
					}
				}
				if (!isSimpleGroupByColumn) {
					hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "Expression in GROUP BY",
							"The GROUP BY clause uses an expression ('"
									+ (groupByExpr != null ? groupByExpr.toString() : "NULL")
									+ "'). While valid, grouping directly by columns might allow for better optimization or index usage in some databases.",
							"Ensure grouping by the expression is necessary. Grouping by simple columns is sometimes more efficient."));
				}
			}
		}

		LeadingWildcardVisitor lwv = new LeadingWildcardVisitor();
		if (select.getWhereClause() != null && select.getWhereClause().getCondition() != null) {
			select.getWhereClause().getCondition().accept(lwv);
		}
		if (!lwv.columnsWithLeadingWildcard.isEmpty()) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.WARN, "LIKE with Leading Wildcard",
					"The WHERE clause uses `LIKE '%...'` (a leading wildcard) on column(s): ["
							+ String.join(", ", lwv.columnsWithLeadingWildcard)
							+ "]. Standard B-tree indexes cannot be used efficiently for this type of search, often resulting in a full table/index scan.",
					"Avoid leading wildcards if possible. Consider full-text indexing if searching within text is a primary requirement. If trailing wildcards (`LIKE 'abc%'`) are sufficient, they can use standard indexes."));
		}

		OrVisitor ov = new OrVisitor();
		if (select.getWhereClause() != null && select.getWhereClause().getCondition() != null) {
			select.getWhereClause().getCondition().accept(ov);
		}
		if (ov.orOnDifferentColumns) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "OR Condition on Different Columns",
					"The WHERE clause uses OR to combine conditions on different columns. Databases sometimes struggle to use multiple indexes efficiently for OR conditions, potentially leading to scans or less optimal index merges.",
					"Consider rewriting the query using UNION ALL if appropriate, especially if each part of the OR condition could use a separate index effectively. Evaluate the EXPLAIN plan carefully."));
		}

		// Subquery hint removed due to unreliable counting
		// int subqueryCount = (Integer)queryStats.getOrDefault("subqueryCount", 0);
		// if (subqueryCount > 0) { hints.add(new
		// PerformanceHint(PerformanceHint.Severity.INFO, "Subquery Usage
		// ("+subqueryCount+" found)", /*...*/ "Review correlated subqueries; consider
		// JOINs. Check EXPLAIN.")); }

		if (Boolean.TRUE.equals(queryStats.get("isDistinct"))) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "SELECT DISTINCT Usage",
					"The query uses SELECT DISTINCT to remove duplicate rows. This requires the database to perform extra work (often sorting or hashing) on the result set, which can be resource-intensive for large results.",
					"Ensure DISTINCT is truly necessary. Sometimes duplicates can be avoided by refining JOIN conditions or using GROUP BY instead."));
		}

		if (hints.isEmpty()) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "No Obvious Structural Issues",
					"The query structure doesn't show common beginner anti-patterns.",
					"Review the database-specific EXPLAIN plan for detailed execution analysis and index usage."));
		}

		return hints;
	}

	/** Gathers basic stats for non-SELECT statements. (Unchanged) */
	private Map<String, Object> gatherGeneralStats(TParseTreeNode statement) {
		Map<String, Object> queryStats = new HashMap<>();
		if (statement instanceof TInsertSqlStatement) {
			TInsertSqlStatement insert = (TInsertSqlStatement) statement;
			queryStats.put("targetTable",
					insert.getTargetTable() != null ? insert.getTargetTable().getFullName() : "UNKNOWN");
			queryStats.put("columnCount", insert.getColumnList() != null ? insert.getColumnList().size() : 0);
			queryStats.put("insertSource",
					insert.getSubQuery() != null ? "SELECT Subquery"
							: (insert.getValues() != null ? "VALUES Clause (" + insert.getValues().size() + " rows)"
									: "Default"));
		} else if (statement instanceof TUpdateSqlStatement) {
			TUpdateSqlStatement update = (TUpdateSqlStatement) statement;
			queryStats.put("targetTable",
					update.getTargetTable() != null ? update.getTargetTable().getFullName() : "UNKNOWN");
			queryStats.put("setColumnCount",
					update.getResultColumnList() != null ? update.getResultColumnList().size() : 0);
			queryStats.put("hasWhereClause", update.getWhereClause() != null);
		} else if (statement instanceof TDeleteSqlStatement) {
			TDeleteSqlStatement delete = (TDeleteSqlStatement) statement;
			queryStats.put("targetTable",
					delete.getTargetTable() != null ? delete.getTargetTable().getFullName() : "UNKNOWN");
			queryStats.put("hasWhereClause", delete.getWhereClause() != null);
		} else if (statement instanceof TCustomSqlStatement) {
			queryStats.put("statementKind", "DDL/Custom");
		} else {
			queryStats.put("statementKind", "Other");
		}
		return queryStats;
	}

	/**
	 * Generates hints as PerformanceHint objects for non-SELECT statements.
	 * (Unchanged)
	 */
	private List<PerformanceHint> generateGeneralHints(TParseTreeNode statement,
			Map<String, Map<String, Object>> tableInfo, Map<String, Object> queryStats) {
		List<PerformanceHint> hints = new ArrayList<>();
		String statementTypeStr = "";

		if (statement instanceof TInsertSqlStatement) {
			TInsertSqlStatement insert = (TInsertSqlStatement) statement;
			statementTypeStr = insert.sqlstatementtype.toString();
			if (insert.getSubQuery() != null) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "INSERT...SELECT",
						"Data is inserted based on a SELECT subquery.",
						"Analyze the SELECT subquery separately for potential performance issues. Ensure target table indexes are maintained during insert."));
			} else if (insert.getValues() != null && insert.getValues().size() > 50) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "Large VALUES List",
						"INSERT uses many VALUES clauses (" + insert.getValues().size() + ").",
						"For very large numbers of rows, consider database-specific bulk insert utilities or batching for better performance."));
			}
		} else if (statement instanceof TUpdateSqlStatement) {
			TUpdateSqlStatement update = (TUpdateSqlStatement) statement;
			statementTypeStr = update.sqlstatementtype.toString();
			String targetTable = (String) queryStats.getOrDefault("targetTable", "UNKNOWN");
			if (update.getWhereClause() == null) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.SEVERE, "UPDATE Without WHERE",
						"This statement will update *all* rows in the table '" + targetTable + "'.",
						"ALWAYS include a WHERE clause unless you explicitly intend to modify the entire table. Double-check your logic."));
			} else {
				checkWhereFunctions(update.getWhereClause(), targetTable, tableInfo, hints);
			}
		} else if (statement instanceof TDeleteSqlStatement) {
			TDeleteSqlStatement delete = (TDeleteSqlStatement) statement;
			statementTypeStr = delete.sqlstatementtype.toString();
			String targetTable = (String) queryStats.getOrDefault("targetTable", "UNKNOWN");
			if (delete.getWhereClause() == null) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.SEVERE, "DELETE Without WHERE",
						"This statement will delete *all* rows from the table '" + targetTable + "'.",
						"ALWAYS include a WHERE clause unless you explicitly intend to clear the entire table (consider TRUNCATE if applicable and appropriate). Double-check your logic."));
			} else {
				checkWhereFunctions(delete.getWhereClause(), targetTable, tableInfo, hints);
			}
		} else {
			hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "Basic Analysis",
					"Structural analysis for this statement type (" + statementTypeStr + ") is limited.",
					"Focus on database-specific best practices and EXPLAIN plans if available for this statement type."));
		}
		if (hints.isEmpty() && !(statement instanceof TSelectSqlStatement)) {
			hints.add(new PerformanceHint(PerformanceHint.Severity.INFO, "Basic Analysis Complete",
					"No major structural issues detected for this statement type.",
					"Review database-specific guidelines for " + statementTypeStr + "."));
		}
		return hints;
	}

	/** Helper to add function-in-where hint for DML. (Unchanged) */
	private void checkWhereFunctions(TWhereClause whereClause, String tableName,
			Map<String, Map<String, Object>> tableInfo, List<PerformanceHint> hints) {
		Map<String, Object> details = tableInfo.get(tableName);
		if (details == null && !tableInfo.isEmpty() && tableInfo.size() == 1) {
			details = tableInfo.values().iterator().next();
		}
		if (details != null) {
			Map<String, String> aliasMap = Map.of(details.getOrDefault("aliasUsed", tableName).toString().toLowerCase(),
					tableName);
			WhereClauseColumnVisitor whereVisitor = new WhereClauseColumnVisitor(aliasMap, tableInfo);
			if (whereClause != null && whereClause.getCondition() != null) {
				whereClause.getCondition().accept(whereVisitor);
			}
			@SuppressWarnings("unchecked")
			Set<String> funcCols = (Set<String>) details.getOrDefault("columnsWithFunctionsInWhere", Set.of());
			if (!funcCols.isEmpty()) {
				hints.add(new PerformanceHint(PerformanceHint.Severity.WARN, "Function on WHERE Column(s)",
						"Applying a function to column(s) [" + String.join(", ", funcCols)
								+ "] in the WHERE clause often prevents index usage.",
						"Rewrite the condition to apply functions to constant values if possible, or consider function-based indexes."));
			}
		} else {
			System.err.println("Could not find table details for '" + tableName + "' to check WHERE functions.");
		}
	}

	/** Extracts basic table usage for non-SELECTs. (Unchanged) */
	private Map<String, Map<String, Object>> extractGeneralTableUsage(TParseTreeNode statement) {
		Map<String, Map<String, Object>> tableInfo = new LinkedHashMap<>();
		TableVisitor tv = new TableVisitor();
		statement.accept(tv);
		for (TTable table : tv.tables) {
			String fullName = table.getFullName();
			String simpleName = fullName.contains(".") ? fullName.substring(fullName.lastIndexOf(".") + 1) : fullName;
			tableInfo.computeIfAbsent(fullName, k -> {
				Map<String, Object> data = new HashMap<>();
				data.put("sourceToken", table.getStartToken());
				data.put("aliasUsed", table.getAliasName() != null ? table.getAliasName().toString() : simpleName);
				data.put("whereColumns", new HashSet<String>());
				data.put("columnsWithFunctionsInWhere", new HashSet<String>());
				data.put("allColumnsUsed", new HashSet<String>());
				data.put("groupByColumns", new HashSet<String>());
				data.put("orderByColumns", new HashSet<String>());
				return data;
			});
		}
		return tableInfo;
	}

	// --- Visitor Classes ---

	/** Visitor to find all tables. (Unchanged) */
	private static class TableVisitor extends TParseTreeVisitor {
		List<TTable> tables = new ArrayList<>();

		@Override
		public void preVisit(TTable table) {
			tables.add(table);
		}
	}

	/** Visitor for WHERE columns + functions. (Unchanged) */
	private static class WhereClauseColumnVisitor extends TParseTreeVisitor {
		private final Map<String, String> aliasToTableMap;
		private final Map<String, Map<String, Object>> tableInfo;
		private boolean insideFunction = false;

		public WhereClauseColumnVisitor(Map<String, String> a, Map<String, Map<String, Object>> t) {
			aliasToTableMap = a;
			tableInfo = t;
		}

		@Override
		public void preVisit(TFunctionCall f) {
			insideFunction = true;
			super.preVisit(f);
		} // super needed

		@Override
		public void postVisit(TFunctionCall f) {
			insideFunction = false;
		}

		@Override
		public void preVisit(TObjectName o) {
			if (o.getDbObjectType() == EDbObjectType.column) {
				String c = o.getColumnNameOnly();
				String q = o.getTableString();
				String t = resolveTableName(q, c);
				if (t != null) {
					@SuppressWarnings("unchecked")
					Set<String> wc = (Set<String>) tableInfo.get(t).get("whereColumns");
					if (wc != null)
						wc.add(c);
					if (insideFunction) {
						@SuppressWarnings("unchecked")
						Set<String> fc = (Set<String>) tableInfo.get(t).get("columnsWithFunctionsInWhere");
						if (fc != null)
							fc.add(c);
					}
				}
			}
		}

		private String resolveTableName(String q, String c) {
			String t = null;
			if (q != null && !q.isEmpty()) {
				t = aliasToTableMap.get(q.toLowerCase());
				if (t == null) {
					if (tableInfo.containsKey(q)) {
						t = q;
					} else {
						t = "? (" + q + ")";
						initializeTableInfoIfNeeded(t);
					}
				}
			} else {
				if (aliasToTableMap.size() == 1) {
					t = aliasToTableMap.values().iterator().next();
				} else {
					t = "? (Ambiguous)";
					initializeTableInfoIfNeeded(t);
				}
			}
			return t;
		}

		private void initializeTableInfoIfNeeded(String t) {
			tableInfo.computeIfAbsent(t, k -> {
				Map<String, Object> d = new HashMap<>();
				d.put("whereColumns", new HashSet<>());
				d.put("columnsWithFunctionsInWhere", new HashSet<>());
				d.put("allColumnsUsed", new HashSet<>());
				d.put("groupByColumns", new HashSet<>());
				d.put("orderByColumns", new HashSet<>());
				return d;
			});
		}
	}

	/** Visitor to find ALL columns + GROUP BY + ORDER BY columns. (Unchanged) */
	private static class AllUsageColumnVisitor extends TParseTreeVisitor {
		private final Map<String, String> aliasToTableMap;
		private final Map<String, Map<String, Object>> tableInfo;
		private boolean inGroupBy = false;
		private boolean inOrderBy = false;

		public AllUsageColumnVisitor(Map<String, String> a, Map<String, Map<String, Object>> t) {
			aliasToTableMap = a;
			tableInfo = t;
		}

		@Override
		public void preVisit(TGroupBy g) {
			inGroupBy = true;
			super.preVisit(g);
		}

		@Override
		public void postVisit(TGroupBy g) {
			inGroupBy = false;
		}

		@Override
		public void preVisit(TOrderBy o) {
			inOrderBy = true;
			super.preVisit(o);
		}

		@Override
		public void postVisit(TOrderBy o) {
			inOrderBy = false;
		}

		@Override
		public void preVisit(TObjectName o) {
			if (o.getDbObjectType() == EDbObjectType.column) {
				String c = o.getColumnNameOnly();
				String q = o.getTableString();
				String t = resolveTableName(q, c);
				if (t != null) {
					@SuppressWarnings("unchecked")
					Set<String> ac = (Set<String>) tableInfo.get(t).get("allColumnsUsed");
					if (ac != null)
						ac.add(c);
					if (inGroupBy) {
						@SuppressWarnings("unchecked")
						Set<String> gc = (Set<String>) tableInfo.get(t).get("groupByColumns");
						if (gc != null)
							gc.add(c);
					}
					if (inOrderBy) {
						@SuppressWarnings("unchecked")
						Set<String> oc = (Set<String>) tableInfo.get(t).get("orderByColumns");
						if (oc != null)
							oc.add(c);
					}
				}
			}
		}

		@Override
		public void preVisit(TResultColumn r) {
			TExpression e = r.getExpr();
			if (e != null && e.getExpressionType() == EExpressionType.simple_object_name_t) {
				TObjectName o = e.getObjectOperand();
				if (o != null && "*".equals(o.getPartString())) {
					String q = o.getTableString();
					if (q != null && !q.isEmpty()) {
						String t = resolveTableName(q, "*");
						if (t != null) {
							@SuppressWarnings("unchecked")
							Set<String> c = (Set<String>) tableInfo.get(t).get("allColumnsUsed");
							if (c != null)
								c.add("* (" + q + ".*)");
						}
					} else {
						for (Map<String, Object> d : tableInfo.values()) {
							@SuppressWarnings("unchecked")
							Set<String> c = (Set<String>) d.get("allColumnsUsed");
							if (c != null)
								c.add("*");
						}
					}
				}
			}
		}

		private String resolveTableName(String q, String c) {
			String t = null;
			if (q != null && !q.isEmpty()) {
				t = aliasToTableMap.get(q.toLowerCase());
				if (t == null) {
					if (tableInfo.containsKey(q)) {
						t = q;
					} else {
						t = "? (" + q + ")";
						initializeTableInfoIfNeeded(t);
					}
				}
			} else {
				if (aliasToTableMap.size() == 1) {
					t = aliasToTableMap.values().iterator().next();
				} else {
					t = "? (Ambiguous)";
					initializeTableInfoIfNeeded(t);
				}
			}
			return t;
		}

		private void initializeTableInfoIfNeeded(String t) {
			tableInfo.computeIfAbsent(t, k -> {
				Map<String, Object> d = new HashMap<>();
				d.put("whereColumns", new HashSet<>());
				d.put("columnsWithFunctionsInWhere", new HashSet<>());
				d.put("allColumnsUsed", new HashSet<>());
				d.put("groupByColumns", new HashSet<>());
				d.put("orderByColumns", new HashSet<>());
				return d;
			});
		}
	}

	/** Visitor to count various query elements. (Corrected) */
	private static class GeneralStatsVisitor extends TParseTreeVisitor {
		int subqueryCount = 0;
		int functionCallCount = 0;
		int windowFunctionCount = 0;
		int aggregateFunctionCount = 0;
		int selectStarCount = 0;
		int whereConditionCount = 0;
		private boolean isInWhereClause = false;

		// Simplified subquery count: Count any nested SELECT encountered during
		// traversal
		@Override
		public void preVisit(TSelectSqlStatement select) {
			// Basic check: if this select node has a parent which is also a node (not the
			// root),
			// it's likely nested somehow. This isn't perfect but better than nothing.
			// Gudu's parent tracking isn't exposed via a simple getParent().
			// We rely on the fact that visitors traverse depth-first.
			// Let's refine the count logic in gatherQueryStats instead.
		}

		@Override
		public void preVisit(TFunctionCall func) {
			functionCallCount++;
			EFunctionType funcTypeInt = func.getFunctionType();
			if (funcTypeInt == EFunctionType.array_agg_t) { // Use integer constant
				aggregateFunctionCount++;
			}
			// Check for OVER clause for window functions, as ftAnalytic might not exist or
			// be reliable
			if (func.getAnalyticFunction() != null) {
				windowFunctionCount++;
			}
		}

		@Override
		public void preVisit(TResultColumn r) {
			TExpression e = r.getExpr();
			if (e != null && e.getExpressionType() == EExpressionType.simple_object_name_t) {
				TObjectName o = e.getObjectOperand();
				if (o != null && "*".equals(o.getPartString())) {
					selectStarCount++;
				}
			}
		}

		@Override
		public void preVisit(TWhereClause w) {
			isInWhereClause = true;
			super.preVisit(w);
		}

		@Override
		public void postVisit(TWhereClause w) {
			isInWhereClause = false;
		}

		@Override
		public void preVisit(TExpression e) {
			// Count conditions within WHERE
			if (isInWhereClause) {
				EExpressionType t = e.getExpressionType();
				if (t == EExpressionType.logical_and_t || t == EExpressionType.logical_or_t
						|| t == EExpressionType.simple_comparison_t || t == EExpressionType.parenthesis_t
						|| t == EExpressionType.pattern_matching_t || t == EExpressionType.null_t
						|| t == EExpressionType.between_t || t == EExpressionType.in_t) {
					whereConditionCount++;
				}
			}
			// Count subqueries more reliably by checking for TSelectSqlStatement within an
			// expression context
			if (e.getSubQuery() != null) {
				subqueryCount++;
			}
		}
	}

	/** Visitor to detect LIKE '%...'. (Unchanged) */
	private static class LeadingWildcardVisitor extends TParseTreeVisitor {
		Set<String> columnsWithLeadingWildcard = new HashSet<>();

		@Override
		public void preVisit(TExpression e) {
			if (e.getExpressionType() == EExpressionType.pattern_matching_t) {
				TExpression l = e.getLeftOperand();
				TExpression r = e.getRightOperand();
				if (l != null && l.getExpressionType() == EExpressionType.simple_object_name_t
						&& l.getObjectOperand() != null
						&& l.getObjectOperand().getDbObjectType() == EDbObjectType.column && r != null
						&& r.getExpressionType() == EExpressionType.simple_constant_t
						&& r.getConstantOperand().toString() != null) {
					String p = r.getConstantOperand().toString().trim();
					if ((p.startsWith("'") && p.endsWith("'")) || (p.startsWith("\"") && p.endsWith("\""))) {
						if (p.length() >= 2) {
							p = p.substring(1, p.length() - 1);
						}
					}
					if (p.startsWith("%")) {
						columnsWithLeadingWildcard.add(l.getObjectOperand().getColumnNameOnly());
					}
				}
			}
		}
	}

	/**
	 * Visitor to detect OR conditions involving different base columns. (Unchanged)
	 */
	private static class OrVisitor extends TParseTreeVisitor {
		boolean orOnDifferentColumns = false;

		@Override
		public void preVisit(TExpression e) {
			if (e.getExpressionType() == EExpressionType.logical_or_t) {
				Set<String> l = collectBaseColumns(e.getLeftOperand());
				Set<String> r = collectBaseColumns(e.getRightOperand());
				if (!l.isEmpty() && !r.isEmpty()) {
					boolean lu = !r.containsAll(l);
					boolean ru = !l.containsAll(r);
					if (lu || ru) {
						orOnDifferentColumns = true;
					}
				}
			}
		}

		private Set<String> collectBaseColumns(TExpression e) {
			Set<String> c = new HashSet<>();
			if (e == null)
				return c;
			e.accept(new TParseTreeVisitor() {
				@Override
				public void preVisit(TObjectName o) {
					if (o.getDbObjectType() == EDbObjectType.column) {
						c.add(o.getColumnNameOnly().toLowerCase());
					}
				}

				@Override
				public void preVisit(TFunctionCall f) {
				}

				@Override
				public void preVisit(TSelectSqlStatement s) {
				}
			});
			return c;
		}
	}
}