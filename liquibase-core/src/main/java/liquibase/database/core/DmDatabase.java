package liquibase.database.core;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;

public class DmDatabase extends OracleDatabase {

    @Override
    public String getShortName() {
        return "dm";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return getDatabaseProductName();
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        
        return "DM DBMS".equalsIgnoreCase(conn.getDatabaseProductName());
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url.startsWith("jdbc:dm")) {
            return "dm.jdbc.driver.DmDriver";
        }
        return null;
    }

    @Override
    public Integer getDefaultPort() {
        return 5236;
    }
    @Override
    public boolean canAccessDbaRecycleBin() {
        return true;
    }
}