package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;

public interface ReportRenderService {

    byte[] renderPdf(JsonNode payload);
}