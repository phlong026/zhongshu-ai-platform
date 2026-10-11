-- Ordered cleanup candidates. Transactional indexes on the empty baseline.
CREATE INDEX idx_infra_job_log_cleanup ON infra_job_log (create_time, id);
CREATE INDEX idx_infra_api_access_log_cleanup ON infra_api_access_log (create_time, id);
CREATE INDEX idx_infra_api_error_log_cleanup ON infra_api_error_log (create_time, id);
CREATE INDEX idx_system_oauth2_access_token_cleanup ON system_oauth2_access_token (expires_time, id);
CREATE INDEX idx_system_oauth2_refresh_token_cleanup ON system_oauth2_refresh_token (expires_time, id);
