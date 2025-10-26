package in.mystrn.sqlutil.utils;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;

/**
 * A TableCellRenderer that wraps text within a JTextArea and adjusts row
 * height.
 */
public class WrappingTableCellRenderer extends JTextArea implements TableCellRenderer {
	private static final long serialVersionUID = 8617037110140323308L;

	public WrappingTableCellRenderer() {
		setLineWrap(true);
		setWrapStyleWord(true);
		setOpaque(true);
		setBorder(UIManager.getBorder("Table.cellBorder"));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		setText((value == null) ? "" : value.toString());
		setFont(table.getFont());
		if (isSelected) {
			setForeground(table.getSelectionForeground());
			setBackground(table.getSelectionBackground());
		} else {
			setForeground(table.getForeground());
			setBackground(table.getBackground());
		}
		int columnWidth = table.getColumnModel().getColumn(column).getWidth();
		setSize(new Dimension(columnWidth, Short.MAX_VALUE));
		int preferredHeight = getPreferredSize().height;
		// Adjust row height using invokeLater
		if (table.getRowHeight(row) != preferredHeight) {
			SwingUtilities.invokeLater(() -> {
				if (table.getRowCount() > row && table.getRowHeight(row) != preferredHeight) {
					table.setRowHeight(row, preferredHeight);
				}
			});
		}
		return this;
	}
}