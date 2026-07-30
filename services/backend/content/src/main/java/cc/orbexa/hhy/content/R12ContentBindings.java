package cc.orbexa.hhy.content;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

final class R12ContentBindings {
    private R12ContentBindings() { }

    static void replaceMedia(JdbcTemplate jdbc, long contentId, List<Long> mediaIds, Instant now) {
        if (new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw new IllegalArgumentException("Content media ids must be unique");
        }
        List<MediaBinding> existing = jdbc.query("""
                SELECT id,media_id,sort_order FROM hhy.content_media
                WHERE content_id=? ORDER BY sort_order,id FOR UPDATE
                """, (rs, row) -> new MediaBinding(rs.getLong(1), rs.getLong(2), rs.getInt(3)), contentId);
        int archiveOrder = archiveStart(existing.stream().map(MediaBinding::sortOrder).toList(), mediaIds.size());
        OffsetDateTime timestamp = time(now);
        Map<Long, MediaBinding> byMedia = new HashMap<>();
        for (MediaBinding binding : existing) {
            byMedia.put(binding.mediaId(), binding);
            jdbc.update("UPDATE hhy.content_media SET removed_at=?,sort_order=?,updated_at=? WHERE id=?",
                    timestamp, archiveOrder++, timestamp, binding.id());
        }
        for (int index = 0; index < mediaIds.size(); index++) {
            long mediaId = mediaIds.get(index);
            MediaBinding binding = byMedia.get(mediaId);
            if (binding == null) {
                int inserted = jdbc.update("""
                        INSERT INTO hhy.content_media(
                          content_id,media_id,media_type,sort_order,removed_at,created_at,updated_at
                        ) SELECT ?,id,mime,?,NULL,?,? FROM hhy.media_objects WHERE id=?
                        """, contentId, index, timestamp, timestamp, mediaId);
                if (inserted != 1) throw new IllegalStateException("Content media object disappeared");
            } else {
                jdbc.update("""
                        UPDATE hhy.content_media binding
                        SET media_type=media.mime,sort_order=?,removed_at=NULL,updated_at=?
                        FROM hhy.media_objects media
                        WHERE binding.id=? AND media.id=binding.media_id
                        """, index, timestamp, binding.id());
            }
        }
    }

    static void replaceContacts(
            JdbcTemplate jdbc, long contentId, List<R08Store.ContactWrite> contacts, Instant now) {
        Set<String> channels = new HashSet<>();
        for (R08Store.ContactWrite contact : contacts) {
            if (!channels.add(contact.channel())) {
                throw new IllegalArgumentException("Content contact channels must be unique");
            }
        }
        List<ContactBinding> existing = jdbc.query("""
                SELECT id,channel,sort_order FROM hhy.content_contacts
                WHERE content_id=? ORDER BY sort_order,id FOR UPDATE
                """, (rs, row) -> new ContactBinding(rs.getLong(1), rs.getString(2), rs.getInt(3)), contentId);
        int archiveOrder = archiveStart(existing.stream().map(ContactBinding::sortOrder).toList(), contacts.size());
        OffsetDateTime timestamp = time(now);
        Map<String, ContactBinding> byChannel = new HashMap<>();
        for (ContactBinding binding : existing) {
            byChannel.put(binding.channel(), binding);
            jdbc.update("UPDATE hhy.content_contacts SET removed_at=?,sort_order=?,updated_at=? WHERE id=?",
                    timestamp, archiveOrder++, timestamp, binding.id());
        }
        for (R08Store.ContactWrite contact : contacts) {
            ContactBinding binding = byChannel.get(contact.channel());
            if (binding == null) {
                jdbc.update("""
                        INSERT INTO hhy.content_contacts(
                          content_id,channel,value_cipher,display_mask,sort_order,removed_at,created_at,updated_at
                        ) VALUES (?,?,?,?,?,NULL,?,?)
                        """, contentId, contact.channel(), contact.valueCipher(), contact.displayMask(),
                        contact.sortOrder(), timestamp, timestamp);
            } else {
                jdbc.update("""
                        UPDATE hhy.content_contacts
                        SET value_cipher=?,display_mask=?,sort_order=?,removed_at=NULL,updated_at=?
                        WHERE id=?
                        """, contact.valueCipher(), contact.displayMask(), contact.sortOrder(), timestamp, binding.id());
            }
        }
    }

    private static int archiveStart(List<Integer> currentOrders, int activeCount) {
        long maximum = activeCount;
        for (Integer order : currentOrders) maximum = Math.max(maximum, order == null ? 0 : order);
        long start = maximum + 1;
        if (start + currentOrders.size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("Content binding order space exhausted");
        }
        return (int) start;
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private record MediaBinding(long id, long mediaId, int sortOrder) { }
    private record ContactBinding(long id, String channel, int sortOrder) { }
}

