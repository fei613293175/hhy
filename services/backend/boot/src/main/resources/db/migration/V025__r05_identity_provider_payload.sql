-- Provider-returned H5 URLs are encrypted before persistence and can exceed the legacy placeholder.
ALTER TABLE hhy.identity_provider_requests
  ALTER COLUMN response_cipher TYPE text;
