package io.databridge.context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega e disponibiliza os datasources de origem e destino.
 * Também mantém estado da execução (checkpoint, contadores, timestamps).
 */
public class MigrationContext {

    private final JdbcTemplate source;
    private final JdbcTemplate target;
    private final DbConfig.Dialect targetDialect;
    private final LocalDateTime startedAt;
    private final Map<String, Object> metadata = new HashMap<>();

    private int totalFetched  = 0;
    private int totalInserted = 0;
    private int totalSkipped  = 0;

    public MigrationContext(JdbcTemplate source, JdbcTemplate target, DbConfig.Dialect targetDialect) {
        this.source        = source;
        this.target        = target;
        this.targetDialect = targetDialect;
        this.startedAt     = LocalDateTime.now();
    }

    // --- Factory ---

    public static MigrationContext fromConfig(DbConfig sourceConfig, DbConfig targetConfig) {
        return new MigrationContext(
            buildTemplate(sourceConfig),
            buildTemplate(targetConfig),
            targetConfig.dialect()
        );
    }

    private static JdbcTemplate buildTemplate(DbConfig config) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(config.driver());
        ds.setUrl(config.url());
        ds.setUsername(config.username());
        ds.setPassword(config.password());
        return new JdbcTemplate(ds);
    }

    // --- Upsert dialect-aware ---

    /**
     * INSERT ignorando conflitos na chave primária.
     * <ul>
     *   <li>PostgreSQL: {@code INSERT ... ON CONFLICT (pk) DO NOTHING}</li>
     *   <li>SQL Server: {@code MERGE ... WHEN NOT MATCHED THEN INSERT}</li>
     * </ul>
     */
    public void upsertIgnore(String table, String pk, String[] columns, Object[] values) {
        String sql = switch (targetDialect) {
            case POSTGRES  -> buildPostgresUpsert(table, pk, columns);
            case SQLSERVER -> buildSqlServerMerge(table, pk, columns);
        };
        target.update(sql, values);
    }

    private String buildPostgresUpsert(String table, String pk, String[] columns) {
        String cols         = String.join(", ", columns);
        String placeholders = String.join(", ", Arrays.stream(columns).map(c -> "?").toArray(String[]::new));
        return "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO NOTHING"
            .formatted(table, cols, placeholders, pk);
    }

    private String buildSqlServerMerge(String table, String pk, String[] columns) {
        String insertCols = String.join(", ", columns);
        String sourceCols = String.join(", ", Arrays.stream(columns).map(c -> "src." + c).toArray(String[]::new));
        String placeholders = String.join(", ", Arrays.stream(columns).map(c -> "?").toArray(String[]::new));
        return """
            MERGE INTO %s AS tgt
            USING (VALUES (%s)) AS src(%s)
            ON tgt.%s = src.%s
            WHEN NOT MATCHED THEN INSERT (%s) VALUES (%s);
            """.formatted(table, placeholders, insertCols, pk, pk, insertCols, sourceCols);
    }

    // --- Contadores ---

    public void incrementFetched(int n)  { totalFetched  += n; }
    public void incrementInserted(int n) { totalInserted += n; }
    public void incrementSkipped(int n)  { totalSkipped  += n; }

    // --- Metadata (checkpoint, flags por rotina) ---

    public void set(String key, Object value) { metadata.put(key, value); }
    public Object get(String key)             { return metadata.get(key); }
    public boolean has(String key)            { return metadata.containsKey(key); }

    // --- Getters ---

    public JdbcTemplate source()            { return source; }
    public JdbcTemplate target()            { return target; }
    public DbConfig.Dialect targetDialect() { return targetDialect; }
    public LocalDateTime startedAt()        { return startedAt; }
    public int totalFetched()               { return totalFetched; }
    public int totalInserted()              { return totalInserted; }
    public int totalSkipped()               { return totalSkipped; }
}
