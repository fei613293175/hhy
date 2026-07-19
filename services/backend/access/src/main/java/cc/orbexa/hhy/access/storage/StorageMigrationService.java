package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Resumable, provider-neutral R04 migration coordinator. Provider I/O never holds a DB lock. */
public final class StorageMigrationService {
    private final Store store;
    private final PortResolver ports;
    private final TransactionRunner transactions;
    private final Clock clock;

    public StorageMigrationService(
            Store store, PortResolver ports, TransactionRunner transactions, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.ports = Objects.requireNonNull(ports, "ports");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BatchResult migratePage(long jobId, int limit, long actorId) {
        if (jobId <= 0 || actorId <= 0) throw new IllegalArgumentException("job and actor are required");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("batch limit out of range");

        Claim claim = transactions.inTransaction(() -> claim(jobId));
        MigrationPage page;
        try {
            StorageObjectPort sourcePort = ports.resolve(claim.source().provider());
            StorageObjectPort targetPort = ports.resolve(claim.target().provider());
            page = sourcePort.scan(claim.source(), claim.job().cursor(), limit);
            for (var object : page.objects()) targetPort.copy(claim.source(), claim.target(), object);
        } catch (RuntimeException providerFailure) {
            return transactions.inTransaction(() -> pause(claim, actorId));
        }
        return transactions.inTransaction(() -> finish(claim, page, actorId));
    }

    private Claim claim(long jobId) {
        Job current = store.findJobForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("storage migration job does not exist"));
        if (current.status().terminal()) throw new IllegalStateException("storage migration job is terminal");
        BindingRecord source = store.findBinding(current.sourceBindingId())
                .orElseThrow(() -> new IllegalStateException("source storage binding does not exist"));
        BindingRecord target = store.findBinding(current.targetBindingId())
                .orElseThrow(() -> new IllegalStateException("target storage binding does not exist"));
        if (source.id() == target.id()
                || source.binding().scope() != current.scope()
                || target.binding().scope() != current.scope()) {
            throw new IllegalStateException("migration bindings do not match the job scope");
        }
        Job running = current.status() == JobStatus.RUNNING
                ? current : current.withProgress(current.cursor(), current.total(),
                        current.success(), current.failed(), JobStatus.RUNNING);
        if (running != current) store.save(current, running);
        return new Claim(running, source.binding(), target.binding());
    }

    private BatchResult finish(Claim claim, MigrationPage page, long actorId) {
        Job current = requireSameClaim(claim);
        int copied = page.objects().size();
        JobStatus status = page.complete() ? JobStatus.COMPLETED : JobStatus.RUNNING;
        int success = Math.addExact(current.success(), copied);
        int total = Math.max(current.total(), Math.addExact(success, current.failed()));
        Job updated = current.withProgress(page.nextCursor(), total, success, current.failed(), status);
        store.save(current, updated);
        store.audit(new AuditEvent(actorId, updated.id(), "STORAGE_MIGRATION_PAGE_" + status.name(),
                updated.scope(), copied, updated.success(), updated.failed(), clock.instant()));
        return result(updated, copied);
    }

    private BatchResult pause(Claim claim, long actorId) {
        Job current = requireSameClaim(claim);
        int failed = Math.addExact(current.failed(), 1);
        int total = Math.max(current.total(), Math.addExact(current.success(), failed));
        Job paused = current.withProgress(current.cursor(), total, current.success(), failed, JobStatus.PAUSED);
        store.save(current, paused);
        store.audit(new AuditEvent(actorId, paused.id(), "STORAGE_MIGRATION_PAGE_PAUSED",
                paused.scope(), 0, paused.success(), paused.failed(), clock.instant()));
        return result(paused, 0);
    }

    private Job requireSameClaim(Claim claim) {
        Job current = store.findJobForUpdate(claim.job().id())
                .orElseThrow(() -> new IllegalStateException("storage migration job disappeared"));
        if (current.status() != JobStatus.RUNNING
                || !Objects.equals(current.cursor(), claim.job().cursor())) {
            throw new IllegalStateException("storage migration job was changed by another worker");
        }
        return current;
    }

    private static BatchResult result(Job job, int copied) {
        return new BatchResult(job.id(), job.status(), job.cursor(), copied,
                job.total(), job.success(), job.failed());
    }

    public enum JobStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED;
        boolean terminal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
    }

    public record Job(long id, long sourceBindingId, long targetBindingId, Scope scope,
                      String cursor, int total, int success, int failed, JobStatus status) {
        public Job {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(status, "status");
            if (id <= 0 || sourceBindingId <= 0 || targetBindingId <= 0
                    || sourceBindingId == targetBindingId || total < 0 || success < 0 || failed < 0
                    || success + failed > total) {
                throw new IllegalArgumentException("invalid storage migration job");
            }
        }

        Job withProgress(String nextCursor, int nextTotal, int nextSuccess, int nextFailed,
                         JobStatus nextStatus) {
            return new Job(id, sourceBindingId, targetBindingId, scope, nextCursor,
                    nextTotal, nextSuccess, nextFailed, nextStatus);
        }
    }

    public record BindingRecord(long id, Binding binding) {
        public BindingRecord { if (id <= 0) throw new IllegalArgumentException("binding id is required"); }
    }

    public record AuditEvent(long actorId, long jobId, String action, Scope scope,
                             int batchObjects, int success, int failed, Instant createdAt) { }

    public record BatchResult(long jobId, JobStatus status, String nextCursor, int copied,
                              int total, int success, int failed) { }

    private record Claim(Job job, Binding source, Binding target) { }

    public interface Store {
        Optional<Job> findJobForUpdate(long jobId);
        Optional<BindingRecord> findBinding(long bindingId);
        void save(Job previous, Job updated);
        void audit(AuditEvent event);
    }

    public interface PortResolver { StorageObjectPort resolve(Provider provider); }

    public interface TransactionRunner { <T> T inTransaction(Supplier<T> work); }
}
