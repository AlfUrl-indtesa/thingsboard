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
        renderExecutiveSummary(doc, payload);
        renderKpis(doc, payload);
        renderStatisticsTables(doc, payload);
        renderCharts(doc, payload);
        renderObservations(doc, payload);
        renderConclusion(doc, payload);

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

    ensureSpace(doc, 280);
    sectionTitle(doc, 'Gráficas de serie temporal');

    series.forEach(item => {
        const points = item.points || [];

        if (!points.length) {
            return;
        }

        ensureSpace(doc, 300);

        const title = `${item.label || item.key} - ${item.entityName || ''}`;

        doc.fontSize(12)
            .fillColor('#111111')
            .text(safeText(title, 80), {
                width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
                lineBreak: false
            });

        doc.moveDown(0.6);

        const chartBox = {
            x: doc.page.margins.left,
            y: doc.y,
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            height: 150
        };

        drawLineChart(doc, points, chartBox);

        doc.y = chartBox.y + chartBox.height + 14;

        renderSeriesMiniTable(doc, item);
        resetCursor(doc);

        doc.moveDown(1);
    });
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

    ensureSpace(doc, 180);
    resetCursor(doc);
    sectionTitle(doc, 'Observaciones técnicas');

    observations.forEach(obs => {
        resetCursor(doc);
        ensureSpace(doc, 42);
        resetCursor(doc);

        const text = cleanObservation(obs, payload);

        doc.fontSize(9)
            .fillColor('#333333')
            .text(`• ${text}`, doc.page.margins.left, doc.y, {
                width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
                align: 'left',
                lineGap: 2
            });

        doc.moveDown(0.45);
        resetCursor(doc);
    });
}

function renderConclusion(doc, payload) {
    ensureSpace(doc, 170);
    resetCursor(doc);
    sectionTitle(doc, 'Conclusión');

    const kpis = payload?.summary?.kpis || [];
    const observations = payload?.summary?.observations || [];

    const text = `El reporte fue generado correctamente con ${kpis.length} indicador(es) y ${observations.length} observación(es) técnicas. La información presentada permite revisar el comportamiento operativo del periodo seleccionado y detectar variaciones relevantes en las variables monitoreadas.`;

    resetCursor(doc);

    doc.fontSize(9)
        .fillColor('#333333')
        .text(text, doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            align: 'left',
            lineGap: 2
        });

    doc.moveDown(2);
    resetCursor(doc);

    doc.fontSize(8)
        .fillColor('#666666')
        .text(payload?.branding?.footerText || 'Reporte generado por Eficentra', doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            align: 'left'
        });

    resetCursor(doc);
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

const port = process.env.PORT || 3000;

app.listen(port, '127.0.0.1', () => {
    console.log(`Eficentra report render service listening on http://127.0.0.1:${port}`);
});