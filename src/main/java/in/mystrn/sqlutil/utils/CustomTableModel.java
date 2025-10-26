package in.mystrn.sqlutil.utils;

import java.util.List;
import java.util.Vector; // Or use ArrayList<ArrayList<Object>>

import javax.swing.table.AbstractTableModel;

/**
 * A basic custom TableModel extending AbstractTableModel. Stores data in a
 * Vector of Vectors (similar to DefaultTableModel's internal structure) but
 * provides more control. By default, cells are not editable.
 */
public class CustomTableModel extends AbstractTableModel {
	private Vector<Vector<Object>> data;
	private Vector<String> columnNames;

	public CustomTableModel(Vector<Vector<Object>> d, Vector<String> c) {
		data = (d != null) ? d : new Vector<>();
		columnNames = (c != null) ? c : new Vector<>();
	}

	@Override
	public int getRowCount() {
		return data.size();
	}

	@Override
	public int getColumnCount() {
		return columnNames.size();
	}

	@Override
	public Object getValueAt(int r, int c) {
		if (r >= 0 && r < data.size()) {
			Vector<Object> row = data.get(r);
			if (c >= 0 && c < row.size()) {
				return row.get(c);
			}
		}
		return null;
	}

	@Override
	public String getColumnName(int c) {
		if (c >= 0 && c < columnNames.size()) {
			return columnNames.get(c);
		}
		return super.getColumnName(c);
	}

	@Override
	public Class<?> getColumnClass(int c) {
		if (getRowCount() > 0 && getValueAt(0, c) != null) {
			return getValueAt(0, c).getClass();
		}
		return Object.class;
	}

	@Override
	public boolean isCellEditable(int r, int c) {
		return false;
	}

	public void addRow(Vector<Object> d) {
		if (d != null && d.size() == getColumnCount()) {
			data.add(d);
			fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
		}
	}

	public void removeRow(int r) {
		if (r >= 0 && r < getRowCount()) {
			data.remove(r);
			fireTableRowsDeleted(r, r);
		}
	}

	public void setDataVector(Vector<Vector<Object>> nD, Vector<String> nC) {
		data = (nD != null) ? nD : new Vector<>();
		columnNames = (nC != null) ? nC : new Vector<>();
		fireTableStructureChanged();
	}

	public void insertRow(int r, Vector<Object> d) {
		if (d == null || d.size() != getColumnCount()) {
			throw new IllegalArgumentException("Data mismatch.");
		}
		if (r < 0 || r > getRowCount()) {
			throw new IllegalArgumentException("Index OOB: " + r);
		}
		data.insertElementAt(d, r);
		fireTableRowsInserted(r, r);
	}

	public void insertRow(int r, Object[] d) {
		if (d == null || d.length != getColumnCount()) {
			throw new IllegalArgumentException("Array mismatch.");
		}
		Vector<Object> v = new Vector<>(List.of(d));
		insertRow(r, v);
	}

	public Vector<Vector<Object>> getDataVector() {
		return data;
	} // Added getter
}