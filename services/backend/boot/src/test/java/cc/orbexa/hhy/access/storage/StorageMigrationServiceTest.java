package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.storage.StorageMigrationService.AuditEvent;
import cc.orbexa.hhy.access.storage.StorageMigrationService.BindingRecord;
import cc.orbexa.hhy.access.storage.StorageMigrationService.Job;
import cc.orbexa.hhy.access.storage.StorageMigrationService.JobStatus;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageMigrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
    private static final String SHA = "b".repeat(64);

    @Test
    void completesCrossProviderPageAndPersistsCursor() {
        FakeStore store = new FakeStore(job(JobStatus.PENDING, null, 0, 0, 0));
        FakePort source = new FakePort(new MigrationPage(List.of(
                new ObjectRef(7, "backup/7/a.bin", SHA),
                new ObjectRef(7, "backup/7/b.bin", SHA)), "cursor-2", false));
        FakePort target = new FakePort(null);
        var service = service(store, source, target);

        var result = service.migratePage(10, 100, 99);

        assertEquals(JobStatus.RUNNING, result.status());
        assertEquals("cursor-2", result.nextCursor());
        assertEquals(2, result.copied());
        assertEquals(2, target.copied.size());
        assertEquals("STORAGE_MIGRATION_PAGE_RUNNING", store.audits.getFirst().action());
    }

    @Test
    void providerFailurePausesWithoutAdvancingCursorAndCanResume() {
        FakeStore store = new FakeStore(job(JobStatus.RUNNING, "cursor-1", 1, 1, 0));
        FakePort source = new FakePort(new MigrationPage(
                List.of(new ObjectRef(7, "backup/7/a.bin", SHA)), "cursor-2", false));
        FakePort target = new FakePort(null);
        target.failCopy = true;
        var service = service(store, source, target);

        var paused = service.migratePage(10, 100, 99);
        assertEquals(JobStatus.PAUSED, paused.status());
        assertEquals("cursor-1", paused.nextCursor());
        assertEquals(1, paused.success());

        target.failCopy = false;
        var resumed = service.migratePage(10, 100, 99);
        assertEquals(JobStatus.RUNNING, resumed.status());
        assertEquals("cursor-2", resumed.nextCursor());
        assertEquals(2, resumed.success());
    }

    @Test
    void completedPageIsTerminalAndClearsCursor() {
        FakeStore store = new FakeStore(job(JobStatus.PENDING, null, 0, 0, 0));
        var service = service(store, new FakePort(new MigrationPage(List.of(), null, true)), new FakePort(null));
        var result = service.migratePage(10, 100, 99);
        assertEquals(JobStatus.COMPLETED, result.status());
        assertNull(result.nextCursor());
        assertThrows(IllegalStateException.class, () -> service.migratePage(10, 100, 99));
    }

    private static StorageMigrationService service(FakeStore store, FakePort source, FakePort target) {
        return new StorageMigrationService(store,
                provider -> provider == Provider.CLOUDFLARE_R2 ? source : target,
                new StorageMigrationService.TransactionRunner() {
                    @Override public <T> T inTransaction(java.util.function.Supplier<T> work) {
                        return work.get();
                    }
                }, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Job job(JobStatus status, String cursor, int total, int success, int failed) {
        return new Job(10, 1, 2, Scope.BACKUP, cursor, total, success, failed, status);
    }

    private static Binding binding(Provider provider) {
        return new Binding(Scope.BACKUP, provider, "hhy-backup",
                URI.create("https://objects.example.test"), null);
    }

    private static final class FakeStore implements StorageMigrationService.Store {
        private Job job;
        private final List<AuditEvent> audits = new ArrayList<>();
        private FakeStore(Job job) { this.job = job; }
        @Override public Optional<Job> findJobForUpdate(long jobId) { return Optional.of(job); }
        @Override public Optional<BindingRecord> findBinding(long id) {
            return Optional.of(new BindingRecord(id, binding(id == 1 ? Provider.CLOUDFLARE_R2 : Provider.ALIYUN_OSS)));
        }
        @Override public void save(Job previous, Job updated) {
            if (!job.equals(previous)) throw new IllegalStateException("concurrent update");
            job = updated;
        }
        @Override public void audit(AuditEvent event) { audits.add(event); }
    }

    private static final class FakePort implements StorageObjectPort {
        private final MigrationPage page;
        private final List<ObjectRef> copied = new ArrayList<>();
        private boolean failCopy;
        private FakePort(MigrationPage page) { this.page = page; }
        @Override public MigrationPage scan(Binding binding, String cursor, int limit) { return page; }
        @Override public void copy(Binding source, Binding target, ObjectRef object) {
            if (failCopy) throw new IllegalStateException("provider timeout");
            copied.add(object);
        }
        @Override public UploadTicket createUpload(Binding binding, UploadIntent intent) { throw unsupported(); }
        @Override public StoredObject completeUpload(Binding binding, CompleteUpload command) { throw unsupported(); }
        @Override public ReadTicket createReadUrl(Binding binding, ObjectRef object, java.time.Duration ttl) { throw unsupported(); }
        @Override public void delete(Binding binding, ObjectRef object) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
