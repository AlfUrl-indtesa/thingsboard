/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import java.nio.file.Path;

/**
 * Metadata for a PDF streamed to a staging file.
 */
public record RenderedReportFile(
        Path path,
        long size,
        String checksum,
        String requestId) {
}