package in.mystrn.sqlutil.models;

import java.util.List;
import java.util.Map;

/**
 *
 * @author hive
 */
public class SqlResult {

    private List<Map<String, Object>> tables;

    public List<Map<String, Object>> getTables() {
        return tables;
    }

    public void setTables(List<Map<String, Object>> tables) {
        this.tables = tables;
    }

    
    
}
