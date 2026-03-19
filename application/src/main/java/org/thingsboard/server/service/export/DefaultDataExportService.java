package main.java.org.thingsboard.server.service.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceInfo;
import org.thingsboard.server.common.data.DeviceInfoFilter;
import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.telemetry.TbTelemetryService;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDataExportService implements DataExportService {

    private static final int DEVICE_PAGE_SIZE = 1000;
    private static final int HISTORY_BATCH_SIZE = 1000;

    private final DeviceService deviceService;
    private final AttributesService attributesService;
    private final TimeseriesService tsService;
    private final TbTelemetryService tbTelemetryService;

    @Override
    public DataExportPreviewResponse preview(SecurityUser user, DataExportPreviewRequest request) throws Exception {
        DataExportPreviewResponse response = new DataExportPreviewResponse();
        response.setDevices(loadAccessibleDeviceInfos(user));
        response.setSuggestedEmail(user.getEmail());
        response.setEmailRequired(user.getEmail() == null || user.getEmail().isBlank());
        response.setDefaultStartTs(null);
        response.setDefaultEndTs(System.currentTimeMillis());
        response.setKeys(Collections.emptyList());
        response.setAttributeKeys(Collections.emptyList());

        if (request == null || request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
            return response;
        }

        List<Device> selectedDevices = loadAccessibleDevicesByIds(user, request.getDeviceIds());

        List<String> telemetryKeys = discoverTelemetryKeys(user, selectedDevices);
        List<String> attributeKeys = request.isIncludeAttributes()
                ? discoverAttributeKeys(user, selectedDevices)
                : Collections.emptyList();

        response.setKeys(telemetryKeys);
        response.setAttributeKeys(attributeKeys);

        if (!telemetryKeys.isEmpty()) {
            Long oldestTs = findOldestTelemetryTs(user, selectedDevices, telemetryKeys);
            response.setDefaultStartTs(oldestTs);
        }

        return response;
    }

    @Override
    public void writeCsv(SecurityUser user, DataExportRequest request, OutputStream outputStream) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("Export request cannot be null");
        }
        if (request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
            throw new IllegalArgumentException("At least one device must be selected");
        }

        List<Device> selectedDevices = loadAccessibleDevicesByIds(user, request.getDeviceIds());

        List<String> telemetryKeys = resolveTelemetryKeys(user, selectedDevices, request.getKeys());
        List<String> attributeKeys = resolveAttributeKeys(user, selectedDevices, request.getAttributeKeys(), request.isIncludeAttributes());

        long endTs = request.getEndTs() != null ? request.getEndTs() : System.currentTimeMillis();
        Long startTs = resolveEffectiveStartTs(user, request, selectedDevices, telemetryKeys);

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writer.println("deviceId,deviceName,ts,key,value,scope");

            for (Device device : selectedDevices) {
                if (startTs != null && !telemetryKeys.isEmpty()) {
                    for (String key : telemetryKeys) {
                        exportTelemetryHistoryForKey(writer, user, device, key, startTs, endTs);
                    }
                }

                if (request.isIncludeAttributes()) {
                    exportCurrentAttributes(writer, user, device, attributeKeys);
                }
            }

            writer.flush();
        }
    }

    @Override
    public void saveSchedule(SecurityUser user, DataExportScheduleRequest request) {
        // Etapa posterior: persistencia real del schedule
    }

    @Override
    public DataExportSchedule getSchedule(SecurityUser user) {
        // Etapa posterior: lectura real del schedule persistido
        return null;
    }

    @Override
    public void runSchedules() {
        // Etapa posterior: ejecución real de schedules
    }

    private List<DataExportPreviewResponse.ExportableDeviceInfo> loadAccessibleDeviceInfos(SecurityUser user) {
        List<DataExportPreviewResponse.ExportableDeviceInfo> result = new ArrayList<>();

        int page = 0;
        PageData<DeviceInfo> pageData;

        do {
            PageLink pageLink = new PageLink(DEVICE_PAGE_SIZE, page);

            DeviceInfoFilter.DeviceInfoFilterBuilder filter = DeviceInfoFilter.builder();
            filter.tenantId(user.getTenantId());

            if (hasCustomerScope(user)) {
                filter.customerId(user.getCustomerId());
            }

            pageData = deviceService.findDeviceInfosByFilter(filter.build(), pageLink);

            if (pageData != null && pageData.getData() != null) {
                for (DeviceInfo info : pageData.getData()) {
                    if (info == null || info.getId() == null) {
                        continue;
                    }
                    DataExportPreviewResponse.ExportableDeviceInfo dto =
                            new DataExportPreviewResponse.ExportableDeviceInfo();
                    dto.setId(info.getId().getId().toString());
                    dto.setName(info.getName());
                    dto.setType(info.getType());
                    dto.setLabel(info.getLabel());
                    result.add(dto);
                }
            }

            page++;
        } while (pageData != null && pageData.hasNext());

        result.sort(Comparator.comparing(d -> d.getName() == null ? "" : d.getName().toLowerCase()));
        return result;
    }

    private List<Device> loadAccessibleDevicesByIds(SecurityUser user, List<String> deviceIds) throws Exception {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviceId> ids = new ArrayList<>(deviceIds.size());
        for (String deviceId : deviceIds) {
            ids.add(new DeviceId(UUID.fromString(deviceId)));
        }

        List<Device> devices;
        if (hasCustomerScope(user)) {
            devices = deviceService.findDevicesByTenantIdCustomerIdAndIdsAsync(
                    user.getTenantId(),
                    user.getCustomerId(),
                    ids
            ).get();
        } else {
            devices = deviceService.findDevicesByTenantIdAndIdsAsync(
                    user.getTenantId(),
                    ids
            ).get();
        }

        if (devices == null) {
            devices = Collections.emptyList();
        }

        Map<String, Device> byId = new LinkedHashMap<>();
        for (Device device : devices) {
            if (device != null && device.getId() != null) {
                byId.put(device.getId().getId().toString(), device);
            }
        }

        if (byId.size() != deviceIds.size()) {
            throw new IllegalArgumentException("One or more deviceIds are not accessible for the current user");
        }

        List<Device> ordered = new ArrayList<>(deviceIds.size());
        for (String deviceId : deviceIds) {
            Device device = byId.get(deviceId);
            if (device == null) {
                throw new IllegalArgumentException("Device not accessible: " + deviceId);
            }
            ordered.add(device);
        }

        return ordered;
    }

    private List<String> discoverTelemetryKeys(SecurityUser user, List<Device> devices) throws Exception {
        Set<String> telemetryKeys = new TreeSet<>();

        for (Device device : devices) {
            List<TsKvEntry> latestEntries = tsService.findAllLatest(user.getTenantId(), device.getId()).get();
            if (latestEntries == null) {
                continue;
            }
            for (TsKvEntry entry : latestEntries) {
                if (entry != null && entry.getKey() != null && !entry.getKey().isBlank()) {
                    telemetryKeys.add(entry.getKey());
                }
            }
        }

        return new ArrayList<>(telemetryKeys);
    }

    private List<String> discoverAttributeKeys(SecurityUser user, List<Device> devices) throws Exception {
        Set<String> attributeKeys = new TreeSet<>();

        for (Device device : devices) {
            for (AttributeScope scope : AttributeScope.values()) {
                List<AttributeKvEntry> attrs = attributesService.findAll(user.getTenantId(), device.getId(), scope).get();
                if (attrs == null) {
                    continue;
                }
                for (AttributeKvEntry attr : attrs) {
                    if (attr != null && attr.getKey() != null && !attr.getKey().isBlank()) {
                        attributeKeys.add(attr.getKey());
                    }
                }
            }
        }

        return new ArrayList<>(attributeKeys);
    }

    private List<String> resolveTelemetryKeys(SecurityUser user, List<Device> devices, List<String> requestedKeys) throws Exception {
        Set<String> normalized = normalizeKeys(requestedKeys);
        if (!normalized.isEmpty()) {
            return new ArrayList<>(normalized);
        }
        return discoverTelemetryKeys(user, devices);
    }

    private List<String> resolveAttributeKeys(SecurityUser user,
                                              List<Device> devices,
                                              List<String> requestedKeys,
                                              boolean includeAttributes) throws Exception {
        if (!includeAttributes) {
            return Collections.emptyList();
        }
        Set<String> normalized = normalizeKeys(requestedKeys);
        if (!normalized.isEmpty()) {
            return new ArrayList<>(normalized);
        }
        return discoverAttributeKeys(user, devices);
    }

    private Long resolveEffectiveStartTs(SecurityUser user,
                                         DataExportRequest request,
                                         List<Device> devices,
                                         List<String> telemetryKeys) throws Exception {
        if (telemetryKeys.isEmpty()) {
            return null;
        }

        if (!request.isAutoDetectOldestTs()) {
            if (request.getStartTs() == null) {
                throw new IllegalArgumentException("startTs is required when autoDetectOldestTs is false");
            }
            return request.getStartTs();
        }

        return findOldestTelemetryTs(user, devices, telemetryKeys);
    }

    private Long findOldestTelemetryTs(SecurityUser user, List<Device> devices, List<String> telemetryKeys) throws Exception {
        Long oldest = null;
        long now = System.currentTimeMillis();

        for (Device device : devices) {
            for (String key : telemetryKeys) {
                Long keyOldest = findOldestTelemetryTsForKey(user, device, key, now);
                if (keyOldest != null && (oldest == null || keyOldest < oldest)) {
                    oldest = keyOldest;
                }
            }
        }

        return oldest;
    }

    private Long findOldestTelemetryTsForKey(SecurityUser user, Device device, String key, long endTs) throws Exception {
        List<TsKvEntry> data = tbTelemetryService.getTimeseries(
                device.getId(),
                Collections.singletonList(key),
                0L,
                endTs,
                null,
                0L,
                null,
                1,
                Aggregation.NONE,
                "ASC",
                false,
                user
        ).get();

        if (data == null || data.isEmpty()) {
            return null;
        }

        return data.get(0).getTs();
    }

    private void exportTelemetryHistoryForKey(PrintWriter writer,
                                              SecurityUser user,
                                              Device device,
                                              String key,
                                              long startTs,
                                              long endTs) throws Exception {
        long cursorStart = startTs;

        while (cursorStart <= endTs) {
            List<TsKvEntry> batch = tbTelemetryService.getTimeseries(
                    device.getId(),
                    Collections.singletonList(key),
                    cursorStart,
                    endTs,
                    null,
                    0L,
                    null,
                    HISTORY_BATCH_SIZE,
                    Aggregation.NONE,
                    "ASC",
                    false,
                    user
            ).get();

            if (batch == null || batch.isEmpty()) {
                break;
            }

            long lastTs = -1L;

            for (TsKvEntry entry : batch) {
                if (entry == null) {
                    continue;
                }
                writer.println(
                        csv(device.getId().getId().toString()) + "," +
                        csv(device.getName()) + "," +
                        csv(String.valueOf(entry.getTs())) + "," +
                        csv(entry.getKey()) + "," +
                        csv(entry.getValueAsString()) + "," +
                        csv("TIMESERIES")
                );
                lastTs = entry.getTs();
            }

            if (lastTs < 0) {
                break;
            }

            if (batch.size() < HISTORY_BATCH_SIZE) {
                break;
            }

            cursorStart = lastTs + 1;
        }
    }

    private void exportCurrentAttributes(PrintWriter writer,
                                         SecurityUser user,
                                         Device device,
                                         List<String> attributeKeys) throws Exception {
        Set<String> requestedKeys = new TreeSet<>(attributeKeys);

        for (AttributeScope scope : AttributeScope.values()) {
            List<AttributeKvEntry> attrs = attributesService.findAll(user.getTenantId(), device.getId(), scope).get();
            if (attrs == null) {
                continue;
            }

            for (AttributeKvEntry attr : attrs) {
                if (attr == null) {
                    continue;
                }
                if (!requestedKeys.isEmpty() && !requestedKeys.contains(attr.getKey())) {
                    continue;
                }

                writer.println(
                        csv(device.getId().getId().toString()) + "," +
                        csv(device.getName()) + "," +
                        csv(String.valueOf(attr.getLastUpdateTs())) + "," +
                        csv(attr.getKey()) + "," +
                        csv(attr.getValueAsString()) + "," +
                        csv(scope.name())
                );
            }
        }
    }

    private boolean hasCustomerScope(SecurityUser user) {
        CustomerId customerId = user.getCustomerId();
        return customerId != null && !customerId.isNullUid();
    }

    private Set<String> normalizeKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(k -> !k.isBlank())
                .collect(Collectors.toSet());
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}