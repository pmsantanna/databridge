package io.databridge.audit;

import java.time.LocalDateTime;

/**
 * Representa uma execução de rotina registrada na tabela migration_runs.
 * Usada tanto para inserir quanto para atualizar o registro ao longo da execução.
 */
public class MigrationRun {

    public enum Status { RUNNING, DONE, FAILED }

    private Long    id;
    private String  routine;
    private Status  status;
    private int     fetched;
    private int     inserted;
    private int     skipped;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String  errorMsg;

    public MigrationRun(String routine) {
        this.routine   = routine;
        this.status    = Status.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    // --- Getters / Setters ---

    public Long            id()         { return id; }
    public String          routine()    { return routine; }
    public Status          status()     { return status; }
    public int             fetched()    { return fetched; }
    public int             inserted()   { return inserted; }
    public int             skipped()    { return skipped; }
    public LocalDateTime   startedAt()  { return startedAt; }
    public LocalDateTime   finishedAt() { return finishedAt; }
    public String          errorMsg()   { return errorMsg; }

    public void setId(Long id)                       { this.id = id; }
    public void setStatus(Status status)             { this.status = status; }
    public void setFetched(int fetched)              { this.fetched = fetched; }
    public void setInserted(int inserted)            { this.inserted = inserted; }
    public void setSkipped(int skipped)              { this.skipped = skipped; }
    public void setFinishedAt(LocalDateTime fin)     { this.finishedAt = fin; }
    public void setErrorMsg(String errorMsg)         { this.errorMsg = errorMsg; }
}
