package org.thingsboard.server.common.data.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ReportTemplateId extends UUIDBased {

    @JsonCreator
    public ReportTemplateId(@JsonProperty("id") UUID id) {
        super(id);
    }
}