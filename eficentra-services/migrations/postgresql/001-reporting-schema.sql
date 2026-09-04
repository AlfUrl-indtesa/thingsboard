--
-- Copyright © 2016-2026 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS report_template (
    id uuid NOT NULL CONSTRAINT report_template_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    name varchar(255) NOT NULL,
    description varchar(1000),
    type varchar(50) NOT NULL,
    status varchar(50) NOT NULL DEFAULT 'DRAFT',
    scope_type varchar(50) NOT NULL,
    entity_filter jsonb NOT NULL,
    sections jsonb NOT NULL,
    branding jsonb,
    default_time_range jsonb,
    generation_options jsonb,
    output_format varchar(50) NOT NULL DEFAULT 'PDF',
    system boolean NOT NULL DEFAULT false,
    created_by uuid,
    updated_time bigint,
    updated_by uuid
);

CREATE INDEX IF NOT EXISTS idx_report_template_tenant_created_time
    ON report_template(tenant_id, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_template_tenant_customer_created_time
    ON report_template(tenant_id, customer_id, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_template_tenant_status_created_time
    ON report_template(tenant_id, status, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_template_tenant_type_created_time
    ON report_template(tenant_id, type, created_time DESC);

CREATE TABLE IF NOT EXISTS report_execution (
    id uuid NOT NULL CONSTRAINT report_execution_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    template_id uuid NOT NULL,
    template_name_snapshot varchar(255) NOT NULL,
    report_type varchar(50) NOT NULL,
    status varchar(50) NOT NULL,
    requested_by uuid,
    requested_time bigint,
    started_time bigint,
    finished_time bigint,
    execution_request jsonb,
    payload_snapshot jsonb,
    file_name varchar(255),
    mime_type varchar(100),
    storage_type varchar(50),
    file_path varchar(1000),
    external_file_id varchar(255),
    file_size bigint,
    checksum varchar(255),
    error_code varchar(100),
    error_message text,
    execution_metadata jsonb
);

-- template_id is intentionally not a foreign key. Execution snapshots must
-- remain available after a non-system template is deleted.

CREATE INDEX IF NOT EXISTS idx_report_execution_tenant_requested_time
    ON report_execution(tenant_id, requested_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_execution_tenant_template_requested_time
    ON report_execution(tenant_id, template_id, requested_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_execution_tenant_status_requested_time
    ON report_execution(tenant_id, status, requested_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_execution_tenant_customer_requested_time
    ON report_execution(tenant_id, customer_id, requested_time DESC);

CREATE INDEX IF NOT EXISTS idx_report_execution_tenant_customer_requester
    ON report_execution(
        tenant_id,
        customer_id,
        requested_by,
        requested_time DESC
    );

CREATE INDEX IF NOT EXISTS idx_report_exec_tenant_template_customer_requester
    ON report_execution(
        tenant_id,
        template_id,
        customer_id,
        requested_by,
        requested_time DESC
    );

CREATE INDEX IF NOT EXISTS idx_report_execution_status_requested_time
    ON report_execution(status, requested_time ASC);

CREATE INDEX IF NOT EXISTS idx_report_execution_status_started_time
    ON report_execution(status, started_time)
    WHERE started_time IS NOT NULL;
