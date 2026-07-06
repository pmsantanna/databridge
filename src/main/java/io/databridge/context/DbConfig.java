package io.databridge.context;

/**
 * Configuração de conexão com um banco de dados.
 * Suporta PostgreSQL e SQL Server.
 */
public record DbConfig(
    String driver,
    String url,
    String username,
    String password,
    Dialect dialect
) {
    public static final String DRIVER_POSTGRES  = "org.postgresql.Driver";
    public static final String DRIVER_SQLSERVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    /** Dialetos suportados — controla qual sintaxe de upsert é gerada. */
    public enum Dialect { POSTGRES, SQLSERVER }

    public static DbConfig postgres(String url, String username, String password) {
        return new DbConfig(DRIVER_POSTGRES, url, username, password, Dialect.POSTGRES);
    }

    public static DbConfig sqlServer(String url, String username, String password) {
        return new DbConfig(DRIVER_SQLSERVER, url, username, password, Dialect.SQLSERVER);
    }
}
