## Adding new client/service for testing
1. Add required client and service through siga-admin UI
2. Download service secret from siga-admin UI
3. Decrypt downloaded .cdoc and make note of signingSecret (needed for authentication)
4. Copy required data from database tables "siga_client" and "siga_service" into a new YAML file. See "dev-data-11.yaml" for an example.
5. Include the new YAML file in "changelog/db.changelog-master-dev.yaml".
