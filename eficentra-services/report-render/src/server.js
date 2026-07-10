const express = require('express');
const PDFDocument = require('pdfkit');

const app = express();
app.use(express.json({ limit: '50mb' }));

app.get('/health', (req, res) => {
    res.json({
        status: 'UP',
        service: 'eficentra-report-render'
    });
});

app.post('/render-report', (req, res) => {
    try {
        const payload = req.body || {};

        const doc = new PDFDocument({
            size: 'A4',
            margin: 42,
            info: {
                Title: payload?.meta?.templateName || 'Reporte Eficentra',
                Author: 'Eficentra'
            }
        });

        const chunks = [];

        doc.on('data', chunk => chunks.push(chunk));
        doc.on('end', () => {
            const pdf = Buffer.concat(chunks);
            res.setHeader('Content-Type', 'application/pdf');
            res.setHeader('Content-Disposition', 'inline; filename="report.pdf"');
            res.setHeader('Content-Length', pdf.length);
            res.status(200).send(pdf);
        });

        renderCover(doc, payload);
        normalizePdfState(doc);

        renderExecutiveSummary(doc, payload);
        normalizePdfState(doc);

        renderKpis(doc, payload);
        normalizePdfState(doc);

        renderStatisticsTables(doc, payload);
        normalizePdfState(doc);

        renderCharts(doc, payload);
        normalizePdfState(doc);

        renderObservations(doc, payload);
        normalizePdfState(doc);

        renderConclusion(doc, payload);
        normalizePdfState(doc);
        doc.end();
    } catch (err) {
        console.error('Failed to render report:', err);
        res.status(500).json({
            message: 'Failed to render report',
            error: err.message
        });
    }
});

function renderCover(doc, payload) {
    const title = payload?.meta?.templateName || 'Reporte Eficentra';
    const reportType = payload?.meta?.reportType || 'Reporte técnico';
    const companyName = payload?.branding?.companyName || 'Eficentra';
    const generatedAt = payload?.meta?.generatedAt || new Date().toISOString();

    doc.rect(0, 0, doc.page.width, 130).fill('#0B2239');
    doc.fillColor('#FFFFFF')
        .fontSize(26)
        .text('Eficentra', 42, 42);

    doc.fontSize(12)
        .fillColor('#B9D8F2')
        .text('Sistema de monitoreo y análisis operativo', 42, 78);

    doc.fillColor('#111111');
    doc.moveDown(6);

    doc.fontSize(24)
        .text(title, 42, 170, { width: 500 });

    doc.moveDown();
    doc.fontSize(13)
        .fillColor('#444444')
        .text(reportType);

    doc.moveDown(2);

    doc.fontSize(11)
        .fillColor('#222222')
        .text(`Empresa: ${companyName}`)
        .text(`Generado: ${formatIsoDate(generatedAt)}`);

    const period = payload.period || {};
    if (period.startTs && period.endTs) {
        doc.text(`Periodo: ${formatTimestamp(period.startTs)} - ${formatTimestamp(period.endTs)}`);
    }

    doc.moveDown(6);
    doc.fontSize(10)
        .fillColor('#666666')
        .text(payload?.branding?.footerText || 'Reporte generado por Eficentra');

    doc.addPage();
}

function renderExecutiveSummary(doc, payload) {
    sectionTitle(doc, 'Resumen ejecutivo');

    const entities = payload?.context?.entities || [];
    const kpis = payload?.summary?.kpis || [];
    const observations = payload?.summary?.observations || [];

    doc.fontSize(10)
        .fillColor('#333333')
        .text(`Este reporte analiza ${entities.length} entidad(es), ${kpis.length} indicador(es) y ${observations.length} observación(es) técnicas generadas automáticamente.`);

    doc.moveDown();

    if (entities.length) {
        doc.fontSize(11).fillColor('#111111').text('Entidades analizadas:', { underline: true });
        doc.moveDown(0.4);

        entities.forEach(entity => {
            doc.fontSize(10).fillColor('#333333')
                .text(`• ${entity.name || entity.entityId} (${entity.entityType})`);
        });
    }

    doc.moveDown();
}

function renderDataQuality(doc, payload) {
    const summary = buildDataQualitySummary(payload);

    if (!summary) {
        return;
    }

    ensureSpace(doc, 170);
    resetCursor(doc);
    sectionTitle(doc, 'Calidad de datos');

    const rows = [
        ['Entidades analizadas', summary.entityCount],
        ['Variables analizadas', summary.variableCount],
        ['Muestras procesadas', summary.totalSamples],
        ['Variables sin datos', summary.emptySeriesCount],
        ['Cobertura estimada', summary.coverageLabel]
    ];

    renderKeyValueTable(doc, rows);

    doc.moveDown(0.6);
    resetCursor(doc);

    doc.fontSize(8)
        .fillColor('#555555')
        .text(summary.message, doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            align: 'left',
            lineGap: 2
        });

    doc.moveDown(1);
    resetCursor(doc);
}

function buildDataQualitySummary(payload) {
    const entities = payload?.context?.entities || payload?.data?.entities || [];
    const series = payload?.data?.timeSeries || [];

    if (!series.length) {
        return {
            entityCount: entities.length,
            variableCount: 0,
            totalSamples: 0,
            emptySeriesCount: 0,
            coverageLabel: 'Sin datos',
            message: 'No se encontraron series de telemetría para el periodo seleccionado.'
        };
    }

    let totalSamples = 0;
    let emptySeriesCount = 0;
    const variableKeys = new Set();

    series.forEach(item => {
        const points = item?.points || [];
        totalSamples += points.length;

        if (!points.length) {
            emptySeriesCount++;
        }

        if (item?.key) {
            variableKeys.add(item.key);
        }
    });

    const averageSamples = series.length ? totalSamples / series.length : 0;

    let coverageLabel = 'Baja';

    if (averageSamples >= 100) {
        coverageLabel = 'Alta';
    } else if (averageSamples >= 20) {
        coverageLabel = 'Media';
    }

    let message = 'La cobertura de datos permite realizar una revisión general del comportamiento operativo del periodo seleccionado.';

    if (coverageLabel === 'Alta') {
        message = 'La cobertura de datos es alta. El periodo analizado cuenta con suficiente densidad de muestras para evaluar tendencias y variaciones operativas.';
    } else if (coverageLabel === 'Media') {
        message = 'La cobertura de datos es media. El reporte permite observar el comportamiento general, aunque podrían existir intervalos con menor densidad de muestras.';
    } else {
        message = 'La cobertura de datos es baja. Se recomienda revisar la frecuencia de envío de telemetría o el rango de tiempo seleccionado.';
    }

    if (emptySeriesCount > 0) {
        message += ` Se detectaron ${emptySeriesCount} variable(s) sin datos en el periodo.`;
    }

    return {
        entityCount: entities.length,
        variableCount: variableKeys.size || series.length,
        totalSamples,
        emptySeriesCount,
        coverageLabel,
        message
    };
}

function renderKeyValueTable(doc, rows) {
    const startX = doc.page.margins.left;
    const tableWidth = doc.page.width - doc.page.margins.left - doc.page.margins.right;
    const labelWidth = tableWidth * 0.55;
    const valueWidth = tableWidth * 0.45;
    const rowHeight = 22;

    ensureSpace(doc, rows.length * rowHeight + 30);
    resetCursor(doc);

    let y = doc.y;

    rows.forEach((row, index) => {
        const fill = index % 2 === 0 ? '#FFFFFF' : '#F5F8FB';

        doc.rect(startX, y, tableWidth, rowHeight).fill(fill);

        doc.fillColor('#333333')
            .fontSize(8)
            .text(String(row[0]), startX + 6, y + 7, {
                width: labelWidth - 12,
                lineBreak: false
            });

        doc.fillColor('#111111')
            .fontSize(8)
            .text(String(row[1]), startX + labelWidth + 6, y + 7, {
                width: valueWidth - 12,
                align: 'right',
                lineBreak: false
            });

        y += rowHeight;
    });

    doc.y = y + 12;
    resetCursor(doc);
}

function renderKpis(doc, payload) {
    const kpis = payload?.summary?.kpis || [];

    if (!kpis.length) {
        return;
    }

    sectionTitle(doc, 'Indicadores principales');

    const cardWidth = 160;
    const cardHeight = 70;
    const gap = 12;
    let x = doc.page.margins.left;
    let y = doc.y;

    kpis.forEach((kpi, index) => {
        if (x + cardWidth > doc.page.width - doc.page.margins.right) {
            x = doc.page.margins.left;
            y += cardHeight + gap;
        }

        if (y + cardHeight > doc.page.height - doc.page.margins.bottom) {
            doc.addPage();
            x = doc.page.margins.left;
            y = doc.y;
        }

        doc.roundedRect(x, y, cardWidth, cardHeight, 8)
            .fillAndStroke('#F5F8FB', '#DCE6EF');

        doc.fillColor('#5B6B7A')
            .fontSize(8)
            .text(kpi.label || kpi.key || 'KPI', x + 10, y + 10, {
                width: cardWidth - 20,
                height: 20
            });

        doc.fillColor('#0B2239')
            .fontSize(18)
            .text(kpi.formattedValue || formatNumber(kpi.value), x + 10, y + 32, {
                width: cardWidth - 20
            });

        if (kpi.unit) {
            doc.fillColor('#667')
                .fontSize(8)
                .text(kpi.unit, x + 10, y + 55);
        }

        x += cardWidth + gap;
    });

    doc.y = y + cardHeight + 20;
}

function renderStatisticsTables(doc, payload) {
    const tables = payload?.data?.tables || [];

    if (!tables.length) {
        return;
    }

    tables.forEach(table => {
        sectionTitle(doc, table.title || 'Tabla de datos');

        const columns = table.columns || [];
        const rows = table.rows || [];

        if (!columns.length || !rows.length) {
            doc.fontSize(10).fillColor('#666').text('No hay datos disponibles para esta tabla.');
            doc.moveDown();
            return;
        }

        renderTable(doc, columns, rows);
        doc.moveDown();
    });
}

function renderTable(doc, columns, rows) {
    const startX = doc.page.margins.left;
    const tableWidth = doc.page.width - doc.page.margins.left - doc.page.margins.right;
    const rowHeight = 24;

    const columnWeights = columns.map(col => {
        if (col.key === 'entity') return 1.6;
        if (col.key === 'key') return 1.1;
        if (col.key === 'samples') return 0.8;
        if (col.key === 'firstTs' || col.key === 'lastTs') return 1.35;
        return 0.9;
    });

    const totalWeight = columnWeights.reduce((sum, weight) => sum + weight, 0);
    const colWidths = columnWeights.map(weight => (tableWidth * weight) / totalWeight);

    ensureSpace(doc, rowHeight * 3);

    let y = doc.y;

    doc.rect(startX, y, tableWidth, rowHeight).fill('#0B2239');

    let x = startX;
    columns.forEach((col, i) => {
        doc.fillColor('#FFFFFF')
            .fontSize(7)
            .text(col.label || col.key, x + 4, y + 7, {
                width: colWidths[i] - 8,
                align: col.align || 'left'
            });

        x += colWidths[i];
    });

    y += rowHeight;

    rows.forEach((row, rowIndex) => {
        if (y + rowHeight > doc.page.height - doc.page.margins.bottom) {
            doc.addPage();
            y = doc.y;

            doc.rect(startX, y, tableWidth, rowHeight).fill('#0B2239');

            let headerX = startX;
            columns.forEach((col, i) => {
                doc.fillColor('#FFFFFF')
                    .fontSize(7)
                    .text(col.label || col.key, headerX + 4, y + 7, {
                        width: colWidths[i] - 8,
                        align: col.align || 'left'
                    });

                headerX += colWidths[i];
            });

            y += rowHeight;
        }

        doc.rect(startX, y, tableWidth, rowHeight)
            .fill(rowIndex % 2 === 0 ? '#FFFFFF' : '#F5F8FB');

        let cellX = startX;

        columns.forEach((col, i) => {
            let value = row[col.key] !== undefined && row[col.key] !== null ? row[col.key] : '-';

            if (col.key === 'firstTs' || col.key === 'lastTs') {
                value = formatTableDate(value);
            }

            if (col.key === 'entity') {
                value = safeText(value, 20);
            } else if (col.key === 'firstTs' || col.key === 'lastTs') {
                value = safeText(value, 18);
            } else {
                value = safeText(value, 26);
            }

            doc.fillColor('#222222')
                .fontSize(7)
                .text(String(value), cellX + 4, y + 7, {
                    width: colWidths[i] - 8,
                    align: col.align || 'left',
                    lineBreak: false
                });

            cellX += colWidths[i];
        });

        y += rowHeight;
    });

    doc.y = y + 12;
}

function renderCharts(doc, payload) {
    const series = payload?.data?.timeSeries || [];

    if (!series.length) {
        return;
    }

    normalizePdfState(doc);
    ensureSpace(doc, 280);
    sectionTitle(doc, 'Gráficas de serie temporal');
    normalizePdfState(doc);

    const chartLayout = getChartLayout(payload);

    if (chartLayout === 'COMBINED') {
        renderCombinedChartsWithGranularity(doc, payload, series);
        return;
    }

    series.forEach(item => {
        renderSeriesWithGranularity(doc, payload, item);
    });

    normalizePdfState(doc);
}

function renderSeriesMiniTable(doc, series) {
    const points = series.points || [];
    const stats = calculateStats(points);

    if (!stats) {
        return;
    }

    const columns = [
        { key: 'samples', label: 'Muestras', align: 'right' },
        { key: 'min', label: 'Mínimo', align: 'right' },
        { key: 'max', label: 'Máximo', align: 'right' },
        { key: 'avg', label: 'Promedio', align: 'right' },
        { key: 'firstTs', label: 'Primera muestra', align: 'right' },
        { key: 'lastTs', label: 'Última muestra', align: 'right' }
    ];

    const rows = [
        {
            samples: stats.count,
            min: formatNumber(stats.min),
            max: formatNumber(stats.max),
            avg: formatNumber(stats.avg),
            firstTs: formatTableDate(stats.firstTs),
            lastTs: formatTableDate(stats.lastTs)
        }
    ];

    renderCompactTable(doc, columns, rows);
}

function renderCombinedChartsWithGranularity(doc, payload, series) {
    const granularity = getCombinedChartGranularity(payload, series);
    const timezone = getPayloadTimezone(payload);

    console.log('[report-render] combined granularity=', granularity, 'series=', series.length);

    if (granularity === 'FULL') {
        renderCombinedChartsPage(doc, series, null);
        return;
    }

    const groups = splitSeriesByGranularity(series, granularity, timezone);

    console.log('[report-render] combined periods=', groups.length);

    if (!groups.length) {
        renderCombinedChartsPage(doc, series, null);
        return;
    }

    groups.forEach(group => {
        renderCombinedChartsPage(doc, group.series, group.label);
    });

    normalizePdfState(doc);
}

function getCombinedChartGranularity(payload, series) {
    const granularities = (series || [])
        .map(item => getSeriesGranularity(payload, item))
        .filter(granularity => granularity && granularity !== 'FULL');

    if (!granularities.length) {
        return 'FULL';
    }

    // Si hay mezcla, usamos la división más detallada.
    // DAY domina sobre WEEK, WEEK domina sobre MONTH.
    if (granularities.includes('DAY')) {
        return 'DAY';
    }

    if (granularities.includes('WEEK')) {
        return 'WEEK';
    }

    if (granularities.includes('MONTH')) {
        return 'MONTH';
    }

    return 'FULL';
}

function splitSeriesByGranularity(series, granularity, timezone) {
    const groups = new Map();

    (series || []).forEach(item => {
        const pointGroups = splitPointsByGranularity(item.points || [], granularity, timezone);

        pointGroups.forEach(pointGroup => {
            if (!groups.has(pointGroup.key)) {
                groups.set(pointGroup.key, {
                    key: pointGroup.key,
                    label: pointGroup.label,
                    series: []
                });
            }

            groups.get(pointGroup.key).series.push({
                ...item,
                points: pointGroup.points
            });
        });
    });

    return Array.from(groups.values())
        .filter(group => group.series.some(item => (item.points || []).length))
        .sort((a, b) => String(a.key).localeCompare(String(b.key)));
}

function calculateStats(points) {
    const valid = (points || [])
        .filter(p => p && Number.isFinite(Number(p.value)))
        .map(p => ({
            ts: Number(p.ts),
            value: Number(p.value)
        }));

    if (!valid.length) {
        return null;
    }

    let min = valid[0].value;
    let max = valid[0].value;
    let sum = 0;
    let firstTs = valid[0].ts;
    let lastTs = valid[0].ts;

    valid.forEach(point => {
        min = Math.min(min, point.value);
        max = Math.max(max, point.value);
        sum += point.value;
        firstTs = Math.min(firstTs, point.ts);
        lastTs = Math.max(lastTs, point.ts);
    });

    return {
        count: valid.length,
        min,
        max,
        avg: sum / valid.length,
        firstTs,
        lastTs
    };
}

function getChartLayout(payload) {
    const layout = findChartLayout(payload, 0);

    if (!layout) {
        return 'SEPARATE';
    }

    const normalized = String(layout).toUpperCase();

    if (normalized === 'COMBINED') {
        return 'COMBINED';
    }

    return 'SEPARATE';
}

function findChartLayout(node, depth) {
    if (!node || depth > 8) {
        return null;
    }

    if (Array.isArray(node)) {
        for (const item of node) {
            const found = findChartLayout(item, depth + 1);
            if (found) {
                return found;
            }
        }

        return null;
    }

    if (typeof node !== 'object') {
        return null;
    }

    if (node.chartLayout) {
        return node.chartLayout;
    }

    if (node.config && node.config.chartLayout) {
        return node.config.chartLayout;
    }

    for (const value of Object.values(node)) {
        const found = findChartLayout(value, depth + 1);
        if (found) {
            return found;
        }
    }

    return null;
}

function renderCombinedChartsPage(doc, series, periodLabel) {
    const validSeries = (series || []).filter(item =>
        (item.points || []).some(point =>
            point && Number.isFinite(Number(point.value)) && Number.isFinite(Number(point.ts))
        )
    );

    if (!validSeries.length) {
        return;
    }

    ensureSpace(doc, 390);

    doc.fontSize(12)
        .fillColor('#111111')
        .text(periodLabel ? `Gráfica combinada | ${periodLabel}` : 'Gráfica combinada', doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            lineBreak: false
        });

    doc.moveDown(0.6);

    doc.fontSize(8)
        .fillColor('#666666')
        .text('Las series se muestran normalizadas para comparar comportamiento relativo entre variables con distintas unidades.', doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            lineGap: 2
        });

    doc.moveDown(0.8);

    const chartBox = {
        x: doc.page.margins.left,
        y: doc.y,
        width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
        height: 190
    };

    drawNormalizedCombinedLineChart(doc, validSeries, chartBox);

    doc.y = chartBox.y + chartBox.height + 20;
    resetCursor(doc);

    renderCombinedLegend(doc, validSeries);
    normalizePdfState(doc);

    doc.moveDown(0.8);
    normalizePdfState(doc);

    renderCombinedSeriesStatsTable(doc, validSeries);
    normalizePdfState(doc);

    doc.moveDown(1);
    normalizePdfState(doc);

    resetCursor(doc);
    doc.moveDown(1);
}

function drawNormalizedCombinedLineChart(doc, series, box) {
    const colors = getChartColors();

    const normalizedSeries = series.map(item => {
        const points = (item.points || [])
            .filter(point => point && Number.isFinite(Number(point.value)) && Number.isFinite(Number(point.ts)))
            .map(point => ({
                ts: Number(point.ts),
                value: Number(point.value)
            }))
            .sort((a, b) => a.ts - b.ts);

        return {
            item,
            points
        };
    }).filter(entry => entry.points.length >= 2);

    if (!normalizedSeries.length) {
        return;
    }

    const allTimes = normalizedSeries.flatMap(entry =>
        entry.points.map(point => point.ts)
    );

    const minTs = Math.min(...allTimes);
    const maxTs = Math.max(...allTimes);
    const timeRange = maxTs - minTs || 1;

    doc.rect(box.x, box.y, box.width, box.height).stroke('#DCE6EF');

    doc.fontSize(8).fillColor('#666666');
    doc.text('Escala relativa 0% - 100%', box.x, box.y - 12, {
        lineBreak: false
    });

    doc.fontSize(7).fillColor('#666666');
    doc.text(formatShortDate(minTs), box.x, box.y + box.height + 4, {
        width: 140,
        lineBreak: false
    });

    doc.text(formatShortDate(maxTs), box.x + box.width - 140, box.y + box.height + 4, {
        width: 140,
        align: 'right',
        lineBreak: false
    });

    normalizedSeries.forEach((entry, index) => {
        const values = entry.points.map(point => point.value);
        const min = Math.min(...values);
        const max = Math.max(...values);
        const valueRange = max - min || 1;

        const chartPoints = entry.points.map(point => {
            const x = box.x + ((point.ts - minTs) / timeRange) * box.width;
            const normalizedValue = ((point.value - min) / valueRange) * 100;
            const y = box.y + box.height - (normalizedValue / 100) * box.height;
            return { x, y };
        });

        if (chartPoints.length < 2) {
            return;
        }

        doc.moveTo(chartPoints[0].x, chartPoints[0].y);

        for (let i = 1; i < chartPoints.length; i++) {
            doc.lineTo(chartPoints[i].x, chartPoints[i].y);
        }

        doc.strokeColor(colors[index % colors.length])
            .lineWidth(1.4)
            .stroke();
    });

    doc.strokeColor('#000000')
        .lineWidth(1);
}

function renderCombinedLegend(doc, series) {
    const colors = getChartColors();

    const startX = doc.page.margins.left;
    let x = startX;
    let y = doc.y;
    const maxX = doc.page.width - doc.page.margins.right;

    series.forEach((item, index) => {
        const label = safeText(`${item.label || item.key} - ${item.entityName || ''}`, 42);
        const itemWidth = 170;

        if (x + itemWidth > maxX) {
            x = startX;
            y += 18;
        }

        doc.rect(x, y + 3, 8, 8).fill(colors[index % colors.length]);

        doc.fillColor('#333333')
            .fontSize(7)
            .text(label, x + 12, y, {
                width: itemWidth - 14,
                lineBreak: false
            });

        x += itemWidth;
    });

    doc.y = y + 22;
    resetCursor(doc);
}

function renderCombinedSeriesStatsTable(doc, series) {
    const columns = [
        { key: 'series', label: 'Serie', align: 'left' },
        { key: 'unit', label: 'Unidad', align: 'left' },
        { key: 'samples', label: 'Muestras', align: 'right' },
        { key: 'min', label: 'Mínimo', align: 'right' },
        { key: 'max', label: 'Máximo', align: 'right' },
        { key: 'avg', label: 'Promedio', align: 'right' }
    ];

    const rows = [];

    series.forEach((item, index) => {
        const stats = calculateStats(item.points || []);

        if (!stats) {
            return;
        }

        rows.push({
            series: `${index + 1}. ${safeText(item.label || item.key, 28)}`,
            unit: item.unit || '-',
            samples: stats.count,
            min: formatNumber(stats.min),
            max: formatNumber(stats.max),
            avg: formatNumber(stats.avg)
        });
    });

    if (!rows.length) {
        return;
    }

    renderCompactTable(doc, columns, rows);
}

function getChartColors() {
    return [
        '#1B8DD0',
        '#00BCD4',
        '#3656B0',
        '#4CAF50',
        '#FF9800',
        '#9C27B0',
        '#F44336',
        '#607D8B'
    ];
}

function renderCompactTable(doc, columns, rows) {
    const startX = doc.page.margins.left;
    const tableWidth = doc.page.width - doc.page.margins.left - doc.page.margins.right;
    const rowHeight = 18;
    const colWidth = tableWidth / columns.length;

    ensureSpace(doc, rowHeight * 3);

    let y = doc.y;

    doc.rect(startX, y, tableWidth, rowHeight).fill('#EAF1F8');

    columns.forEach((col, i) => {
        doc.fillColor('#0B2239')
            .fontSize(7)
            .text(col.label, startX + i * colWidth + 3, y + 6, {
                width: colWidth - 6,
                align: col.align || 'left',
                lineBreak: false
            });
    });

    y += rowHeight;

    rows.forEach(row => {
        doc.rect(startX, y, tableWidth, rowHeight).fill('#FFFFFF');

        columns.forEach((col, i) => {
            const value = row[col.key] !== undefined && row[col.key] !== null ? String(row[col.key]) : '-';

            doc.fillColor('#222222')
                .fontSize(7)
                .text(safeText(value, 22), startX + i * colWidth + 3, y + 6, {
                    width: colWidth - 6,
                    align: col.align || 'left',
                    lineBreak: false
                });
        });

        y += rowHeight;
    });

    doc.y = y + 10;
    resetCursor(doc);
}

function drawLineChart(doc, points, box) {
    const validPoints = (points || [])
        .filter(p => p && Number.isFinite(Number(p.value)) && Number.isFinite(Number(p.ts)))
        .map(p => ({
            ts: Number(p.ts),
            value: Number(p.value)
        }))
        .sort((a, b) => a.ts - b.ts);

    if (!validPoints.length) {
        return;
    }

    const values = validPoints.map(p => p.value);
    const times = validPoints.map(p => p.ts);

    const min = Math.min(...values);
    const max = Math.max(...values);
    const minTs = Math.min(...times);
    const maxTs = Math.max(...times);

    const valueRange = max - min || 1;
    const timeRange = maxTs - minTs || 1;

    doc.rect(box.x, box.y, box.width, box.height).stroke('#DCE6EF');

    doc.fontSize(8).fillColor('#666666');
    doc.text(`Máx: ${formatNumber(max)}`, box.x, box.y - 12, { lineBreak: false });
    doc.text(`Mín: ${formatNumber(min)}`, box.x + 85, box.y - 12, { lineBreak: false });

    doc.fontSize(7).fillColor('#666666');
    doc.text(formatShortDate(minTs), box.x, box.y + box.height + 4, {
        width: 140,
        lineBreak: false
    });

    doc.text(formatShortDate(maxTs), box.x + box.width - 140, box.y + box.height + 4, {
        width: 140,
        align: 'right',
        lineBreak: false
    });

    const chartPoints = validPoints.map(point => {
        const x = box.x + ((point.ts - minTs) / timeRange) * box.width;
        const y = box.y + box.height - ((point.value - min) / valueRange) * box.height;
        return { x, y };
    });

    if (chartPoints.length < 2) {
        return;
    }

    doc.moveTo(chartPoints[0].x, chartPoints[0].y);

    for (let i = 1; i < chartPoints.length; i++) {
        doc.lineTo(chartPoints[i].x, chartPoints[i].y);
    }

    doc.strokeColor('#1B8DD0')
        .lineWidth(1.5)
        .stroke();

    doc.strokeColor('#000000')
        .lineWidth(1);
}

function cleanObservation(text, payload) {
    let result = String(text || '');

    const entities = payload?.context?.entities || [];

    entities.forEach(entity => {
        const entityId = entity?.entityId;
        const entityName = entity?.name || entity?.label || entityId;

        if (entityId && entityName) {
            result = result.replaceAll(String(entityId), String(entityName));
        }
    });

    return result;
}

function renderObservations(doc, payload) {
    const observations = payload?.summary?.observations || payload?.data?.observations || [];

    if (!observations.length) {
        return;
    }

    normalizePdfState(doc);
    ensureSpace(doc, 160);
    normalizePdfState(doc);

    sectionTitle(doc, 'Observaciones');
    normalizePdfState(doc);

    observations.forEach((observation, index) => {
        const text = cleanObservation(
            typeof observation === 'string' ? observation : observation?.text,
            payload
        );

        if (!text) {
            return;
        }

        ensureSpace(doc, 70);
        normalizePdfState(doc);

        const left = doc.page.margins.left;
        const width = doc.page.width - doc.page.margins.left - doc.page.margins.right;

        const bulletY = doc.y;

        doc.fontSize(9)
            .fillColor('#1B8DD0')
            .text(`${index + 1}.`, left, bulletY, {
                width: 20,
                lineBreak: false
            });

        doc.fontSize(9)
            .fillColor('#333333')
            .text(safeText(text, 700), left + 24, bulletY, {
                width: width - 24,
                align: 'left',
                lineGap: 3
            });

        normalizePdfState(doc);
        doc.moveDown(0.45);
    });

    normalizePdfState(doc);
}

function buildAutomaticObservations(payload) {
    const observations = [];
    const series = payload?.data?.timeSeries || [];

    series.forEach(item => {
        const points = (item.points || [])
            .filter(point => point && Number.isFinite(Number(point.value)))
            .map(point => ({
                ts: Number(point.ts),
                value: Number(point.value)
            }));

        const label = item.label || item.key || 'Variable';
        const unit = item.unit ? ` ${item.unit}` : '';
        const entityName = item.entityName || 'la entidad seleccionada';

        if (!points.length) {
            observations.push(`La variable ${label} no presentó datos en el periodo seleccionado para ${entityName}.`);
            return;
        }

        const values = points.map(point => point.value);
        const min = Math.min(...values);
        const max = Math.max(...values);
        const avg = values.reduce((sum, value) => sum + value, 0) / values.length;

        observations.push(
            `La variable ${label} registró ${points.length} muestra(s) para ${entityName}. ` +
            `Promedio: ${formatNumber(avg)}${unit}, mínimo: ${formatNumber(min)}${unit}, máximo: ${formatNumber(max)}${unit}.`
        );

        if (points.length < 20) {
            observations.push(
                `La variable ${label} tiene baja cantidad de muestras. Conviene revisar la frecuencia de envío o ampliar el periodo analizado.`
            );
        }

        if (avg !== 0 && Math.abs(max - avg) / Math.abs(avg) > 0.25) {
            observations.push(
                `La variable ${label} presentó picos relevantes respecto a su promedio.`
            );
        }

        if (avg !== 0 && Math.abs(avg - min) / Math.abs(avg) > 0.25) {
            observations.push(
                `La variable ${label} presentó caídas relevantes respecto a su promedio.`
            );
        }
    });

    return observations;
}

function renderConclusion(doc, payload) {
    const conclusion =
        payload?.summary?.conclusion ||
        payload?.data?.conclusion ||
        'El reporte concentra las variables seleccionadas para facilitar la revisión operativa, identificar desviaciones y respaldar decisiones de mantenimiento, eficiencia y control.';

    const text = cleanObservation(conclusion, payload);

    if (!text) {
        return;
    }

    normalizePdfState(doc);
    ensureSpace(doc, 170);
    normalizePdfState(doc);

    sectionTitle(doc, 'Conclusión');
    normalizePdfState(doc);

    const left = doc.page.margins.left;
    const width = doc.page.width - doc.page.margins.left - doc.page.margins.right;

    doc.fontSize(10)
        .fillColor('#333333')
        .text(safeText(text, 1200), left, doc.y, {
            width,
            align: 'left',
            lineGap: 4
        });

    normalizePdfState(doc);
}

function sectionTitle(doc, title) {
    resetCursor(doc);
    ensureSpace(doc, 90);
    resetCursor(doc);

    doc.moveDown(0.5);
    resetCursor(doc);

    doc.fontSize(16)
        .fillColor('#0B2239')
        .text(title, doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            align: 'left',
            lineBreak: false
        });

    const lineY = doc.y + 6;

    doc.moveTo(doc.page.margins.left, lineY)
        .lineTo(doc.page.width - doc.page.margins.right, lineY)
        .strokeColor('#DCE6EF')
        .stroke();

    doc.y = lineY + 12;
    resetCursor(doc);
    doc.strokeColor('#000000');
}

function ensureSpace(doc, neededHeight) {
    if (doc.y + neededHeight > doc.page.height - doc.page.margins.bottom) {
        doc.addPage();
        resetCursor(doc);
    }
}

function formatIsoDate(value) {
    try {
        return new Date(value).toLocaleString('es-MX');
    } catch (e) {
        return value;
    }
}

function resetCursor(doc) {
    doc.x = doc.page.margins.left;
}

function formatShortDate(value) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return '-';
    }

    return new Date(number).toLocaleString('es-MX', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatTimestamp(value) {
    try {
        return new Date(Number(value)).toLocaleString('es-MX');
    } catch (e) {
        return String(value);
    }
}

function formatNumber(value) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return '-';
    }

    return number.toFixed(2);
}

function normalizePdfState(doc) {
    if (!doc || !doc.page) {
        return;
    }

    doc.fillColor('#111111');
    doc.strokeColor('#000000');
    doc.lineWidth(1);

    if (!Number.isFinite(doc.y)) {
        doc.y = doc.page.margins.top;
    }

    if (doc.y < doc.page.margins.top) {
        doc.y = doc.page.margins.top;
    }

    const maxY = doc.page.height - doc.page.margins.bottom;

    if (doc.y > maxY) {
        doc.addPage();
        doc.y = doc.page.margins.top;
    }

    resetCursor(doc);
}

function safeText(value, maxLength = 48) {
    if (value === null || value === undefined) {
        return '-';
    }

    const text = String(value);

    if (text.length <= maxLength) {
        return text;
    }

    return text.substring(0, maxLength - 1) + '…';
}

function formatTableDate(value) {
    if (!value || value === '-') {
        return '-';
    }

    const number = Number(value);

    if (!Number.isFinite(number)) {
        return String(value);
    }

    return new Date(number).toLocaleString('es-MX', {
        year: '2-digit',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function renderSeriesWithGranularity(doc, payload, item) {
    const points = item?.points || [];

    if (!points.length) {
        return;
    }

    const granularity = getSeriesGranularity(payload, item);

    if (granularity === 'FULL') {
        renderSingleSeriesChart(doc, item, null);
        return;
    }

    const groups = splitPointsByGranularity(points, granularity, getPayloadTimezone(payload));

    if (!groups.length) {
        renderSingleSeriesChart(doc, item, null);
        return;
    }

    groups.forEach(group => {
        if (!group.points.length) {
            return;
        }

        const groupedItem = {
            ...item,
            points: group.points
        };

        renderSingleSeriesChart(doc, groupedItem, group.label);
    });
}

function renderSingleSeriesChart(doc, item, periodLabel) {
    const points = item?.points || [];

    if (!points.length) {
        return;
    }

    ensureSpace(doc, 300);
    normalizePdfState(doc);

    let title = `${item.label || item.key} - ${item.entityName || ''}`;

    if (periodLabel) {
        title = `${title} | ${periodLabel}`;
    }

    doc.fontSize(12)
        .fillColor('#111111')
        .text(safeText(title, 100), doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            lineBreak: false
        });

    doc.moveDown(0.6);
    normalizePdfState(doc);

    const chartBox = {
        x: doc.page.margins.left,
        y: doc.y,
        width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
        height: 150
    };

    drawLineChart(doc, points, chartBox);

    doc.y = chartBox.y + chartBox.height + 14;
    normalizePdfState(doc);

    renderSeriesMiniTable(doc, item);
    normalizePdfState(doc);

    doc.moveDown(1);
    normalizePdfState(doc);
}

function getSeriesGranularity(payload, item) {
    const variable = findVariableConfigForSeries(payload, item);

    const rawGranularity =
        item?.granularity ||
        item?.chartGranularity ||
        variable?.granularity ||
        'FULL';

    const granularity = String(rawGranularity).toUpperCase();

    if (granularity === 'DAY' ||
        granularity === 'WEEK' ||
        granularity === 'MONTH') {
        return granularity;
    }

    return 'FULL';
}

function findVariableConfigForSeries(payload, item) {
    const variables = extractVariableConfigs(payload);

    if (!variables.length || !item) {
        return null;
    }

    const itemKey = String(item.key || '').trim();
    const itemEntityName = String(item.entityName || '').trim();
    const itemLabel = String(item.label || '').trim();

    return variables.find(variable => {
        if (!variable || variable.enabled === false) {
            return false;
        }

        const variableKey = String(variable.key || '').trim();
        const variableEntityName = String(variable.entityName || '').trim();
        const variableLabel = String(variable.label || '').trim();

        const keyMatches = variableKey && itemKey && variableKey === itemKey;

        const entityMatches =
            !variableEntityName ||
            !itemEntityName ||
            variableEntityName === itemEntityName;

        const labelMatches =
            !variableLabel ||
            !itemLabel ||
            variableLabel === itemLabel;

        return keyMatches && entityMatches && labelMatches;
    }) || variables.find(variable => {
        const variableKey = String(variable?.key || '').trim();
        return variableKey && itemKey && variableKey === itemKey;
    }) || null;
}

function extractVariableConfigs(payload) {
    const variables = [];
    collectVariableConfigs(payload, variables, 0);

    const seen = new Set();
    const unique = [];

    variables.forEach(variable => {
        const entityId = variable?.entityId?.id || '';
        const entityType = variable?.entityId?.entityType || '';
        const key = variable?.key || '';
        const uniqueKey = `${entityType}:${entityId}:${key}`;

        if (!seen.has(uniqueKey)) {
            seen.add(uniqueKey);
            unique.push(variable);
        }
    });

    return unique;
}

function collectVariableConfigs(node, variables, depth) {
    if (!node || depth > 8) {
        return;
    }

    if (Array.isArray(node)) {
        node.forEach(item => collectVariableConfigs(item, variables, depth + 1));
        return;
    }

    if (typeof node !== 'object') {
        return;
    }

    if (Array.isArray(node.variables)) {
        node.variables.forEach(variable => {
            if (variable?.key) {
                variables.push(variable);
            }
        });
    }

    if (node.config && Array.isArray(node.config.variables)) {
        node.config.variables.forEach(variable => {
            if (variable?.key) {
                variables.push(variable);
            }
        });
    }

    Object.values(node).forEach(value => collectVariableConfigs(value, variables, depth + 1));
}

function splitPointsByGranularity(points, granularity, timezone) {
    const groups = new Map();

    (points || [])
        .filter(point => point && Number.isFinite(Number(point.ts)) && Number.isFinite(Number(point.value)))
        .sort((a, b) => Number(a.ts) - Number(b.ts))
        .forEach(point => {
            const ts = Number(point.ts);
            const key = getPeriodKey(ts, granularity, timezone);
            const label = getPeriodLabel(ts, granularity, timezone);

            if (!groups.has(key)) {
                groups.set(key, {
                    key,
                    label,
                    points: []
                });
            }

            groups.get(key).points.push(point);
        });

    return Array.from(groups.values());
}

function getPeriodKey(ts, granularity, timezone) {
    const parts = getDateParts(ts, timezone);

    if (granularity === 'DAY') {
        return `${parts.year}-${parts.month}-${parts.day}`;
    }

    if (granularity === 'WEEK') {
        const week = getIsoWeek(parts.year, parts.month, parts.day);
        return `${week.year}-W${String(week.week).padStart(2, '0')}`;
    }

    if (granularity === 'MONTH') {
        return `${parts.year}-${parts.month}`;
    }

    return 'FULL';
}

function getPeriodLabel(ts, granularity, timezone) {
    const parts = getDateParts(ts, timezone);

    if (granularity === 'DAY') {
        return `${parts.day}/${parts.month}/${parts.year}`;
    }

    if (granularity === 'WEEK') {
        const week = getIsoWeek(parts.year, parts.month, parts.day);
        return `Semana ${week.week}, ${week.year}`;
    }

    if (granularity === 'MONTH') {
        return `${parts.month}/${parts.year}`;
    }

    return '';
}

function getDateParts(ts, timezone) {
    try {
        const formatter = new Intl.DateTimeFormat('en-CA', {
            timeZone: timezone || 'UTC',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit'
        });

        const parts = formatter.formatToParts(new Date(ts));
        const map = {};

        parts.forEach(part => {
            map[part.type] = part.value;
        });

        return {
            year: Number(map.year),
            month: String(map.month).padStart(2, '0'),
            day: String(map.day).padStart(2, '0')
        };
    } catch (e) {
        const date = new Date(ts);

        return {
            year: date.getFullYear(),
            month: String(date.getMonth() + 1).padStart(2, '0'),
            day: String(date.getDate()).padStart(2, '0')
        };
    }
}

function getIsoWeek(year, month, day) {
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    const dayNumber = date.getUTCDay() || 7;

    date.setUTCDate(date.getUTCDate() + 4 - dayNumber);

    const weekYear = date.getUTCFullYear();
    const yearStart = new Date(Date.UTC(weekYear, 0, 1));
    const week = Math.ceil((((date - yearStart) / 86400000) + 1) / 7);

    return {
        year: weekYear,
        week
    };
}

function getPayloadTimezone(payload) {
    return findTimezone(payload, 0) || 'UTC';
}

function findTimezone(node, depth) {
    if (!node || depth > 6) {
        return null;
    }

    if (Array.isArray(node)) {
        for (const item of node) {
            const found = findTimezone(item, depth + 1);
            if (found) {
                return found;
            }
        }

        return null;
    }

    if (typeof node !== 'object') {
        return null;
    }

    if (node.timezone) {
        return node.timezone;
    }

    if (node.timeZone) {
        return node.timeZone;
    }

    for (const value of Object.values(node)) {
        const found = findTimezone(value, depth + 1);
        if (found) {
            return found;
        }
    }

    return null;
}

const port = process.env.PORT || 3000;

app.listen(port, '127.0.0.1', () => {
    console.log(`Eficentra report render service listening on http://127.0.0.1:${port}`);
});