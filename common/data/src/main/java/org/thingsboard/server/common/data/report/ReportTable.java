package org.thingsboard.server.common.data.report;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ReportTable {

    private String key;
    private String title;
    private List<ReportTableColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
}