-- DEV/TEST only. Stop all WebSocket traffic before applying this rollback.
SET search_path TO hhy, public;

DROP TRIGGER IF EXISTS trg_r14_ws_gap_watermark_guard ON hhy.websocket_gap_watermarks;
DROP TRIGGER IF EXISTS trg_r14_ws_delivery_guard ON hhy.websocket_deliveries;
DROP TRIGGER IF EXISTS trg_r14_ws_sequence_guard ON hhy.websocket_user_sequences;
DROP FUNCTION IF EXISTS hhy.guard_r14_ws_gap_watermark();
DROP FUNCTION IF EXISTS hhy.guard_r14_ws_delivery();
DROP FUNCTION IF EXISTS hhy.guard_r14_ws_sequence();
DROP TABLE IF EXISTS hhy.websocket_gap_watermarks;
DROP TABLE IF EXISTS hhy.websocket_deliveries;
DROP TABLE IF EXISTS hhy.websocket_user_sequences;
