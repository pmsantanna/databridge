package io.databridge.migration;

import io.databridge.context.MigrationContext;
import io.databridge.util.ProgressPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Base de toda rotina de migração.
 *
 * <p>Subclasses precisam implementar apenas dois métodos:
 * <ul>
 *   <li>{@link #fetchPage} — busca uma página de registros na origem</li>
 *   <li>{@link #persist}   — persiste a lista no destino</li>
 * </ul>
 *
 * <p>O controle de paginação, checkpointing e logging são feitos aqui.
 *
 * @param <T> tipo do registro sendo migrado (ex: Employee, PayrollEntry)
 */
public abstract class AbstractMigrationRoutine<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Chave usada para salvar o checkpoint no MigrationContext
    private String checkpointKey() {
        return getClass().getSimpleName() + ".lastOffset";
    }

    /**
     * Nome legível da rotina, usado nos logs e no relatório final.
     */
    public abstract String routineName();

    /**
     * Busca uma página de registros na origem.
     *
     * @param ctx    contexto com datasource de origem e metadados
     * @param offset posição inicial da página
     * @param limit  tamanho da página
     * @return lista de registros; lista vazia sinaliza fim dos dados
     */
    protected abstract List<T> fetchPage(MigrationContext ctx, int offset, int limit);

    /**
     * Persiste os registros no destino.
     *
     * @param records registros retornados por {@link #fetchPage}
     * @param ctx     contexto com datasource de destino
     */
    protected abstract void persist(List<T> records, MigrationContext ctx);

    /**
     * Hook opcional chamado antes do início da migração.
     * Use para criar tabelas temporárias, limpar destino, etc.
     */
    protected void beforeMigration(MigrationContext ctx) {}

    /**
     * Hook opcional chamado após o término bem-sucedido.
     * Use para criar índices, atualizar sequences, etc.
     */
    protected void afterMigration(MigrationContext ctx) {}

    /**
     * Executa a migração completa com paginação e checkpointing.
     *
     * @param ctx       contexto da migração
     * @param pageSize  quantos registros buscar por página
     */
    public final void execute(MigrationContext ctx, int pageSize) {
        log.info("=== Iniciando: {} ===", routineName());

        // Retoma do checkpoint se houver
        int offset = ctx.has(checkpointKey())
            ? (int) ctx.get(checkpointKey())
            : 0;

        if (offset > 0) {
            log.info("Retomando do checkpoint: offset={}", offset);
        }

        beforeMigration(ctx);

        ProgressPrinter progress = new ProgressPrinter(routineName());
        int page = 0;

        try {
            while (true) {
                List<T> records = fetchPage(ctx, offset, pageSize);

                if (records.isEmpty()) {
                    break;
                }

                persist(records, ctx);

                ctx.incrementFetched(records.size());
                ctx.incrementInserted(records.size());
                ctx.set(checkpointKey(), offset + records.size());

                offset += records.size();
                page++;
                progress.print(page, offset);

                // Página incompleta = última página
                if (records.size() < pageSize) {
                    break;
                }
            }

            afterMigration(ctx);

            log.info("=== Concluído: {} | {} registros migrados ===",
                routineName(), ctx.totalInserted());

        } catch (Exception e) {
            log.error("Erro na rotina '{}' no offset {}: {}", routineName(), offset, e.getMessage());
            log.info("Checkpoint salvo. Rode novamente para retomar.");
            throw new MigrationException("Falha em " + routineName(), e);
        }
    }
}
