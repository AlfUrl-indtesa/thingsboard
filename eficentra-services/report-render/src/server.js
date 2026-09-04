/*
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
const cluster = require('cluster');
const express = require('express');
const PDFDocument = require('pdfkit');
const http = require('http');
const https = require('https');
const { URL } = require('url');

const renderWorkerCount = readIntegerEnvironment(
    'RENDER_WORKERS',
    2,
    1,
    8
);

const renderConcurrency = readIntegerEnvironment(
    'RENDER_CONCURRENCY',
    1,
    1,
    4
);

const renderMaxQueue = readIntegerEnvironment(
    'RENDER_MAX_QUEUE',
    20,
    0,
    1000
);

const renderQueueTimeoutMs = readIntegerEnvironment(
    'RENDER_QUEUE_TIMEOUT_MS',
    300000,
    1000,
    3600000
);

const renderMaxPayloadMb = readIntegerEnvironment(
    'RENDER_MAX_PAYLOAD_MB',
    50,
    1,
    200
);

const app = express();
const renderQueue = [];

let activeRenders = 0;
let completedRenders = 0;
let failedRenders = 0;
let rejectedRenders = 0;
let totalQueueWaitMs = 0;
let queuedRenderStarts = 0;

const rendererStartedAt = Date.now();

app.use(
    express.json({
        limit: `${renderMaxPayloadMb}mb`
    })
);

app.get('/health', (req, res) => {
    const memory = process.memoryUsage();

    res.json({
        status: 'UP',
        service: 'eficentra-report-render',
        pid: process.pid,
        workerId: cluster.worker?.id || 0,
        configuredWorkers: renderWorkerCount,
        activeRenders,
        queuedRenders: renderQueue.length,
        concurrencyPerWorker: renderConcurrency,
        maxQueuePerWorker: renderMaxQueue,
        queueTimeoutMs: renderQueueTimeoutMs,
        maxPayloadMb: renderMaxPayloadMb,
        completedRenders,
        failedRenders,
        rejectedRenders,
        averageQueueWaitMs:
            queuedRenderStarts > 0
                ? Math.round(
                    totalQueueWaitMs /
                    queuedRenderStarts
                )
                : 0,
        uptimeSeconds:
            Math.floor(
                (Date.now() - rendererStartedAt) /
                1000
            ),
        memory: {
            rssMb: bytesToMegabytes(memory.rss),
            heapUsedMb: bytesToMegabytes(
                memory.heapUsed
            ),
            externalMb: bytesToMegabytes(
                memory.external
            )
        }
    });
});

app.post(
    '/render-report',
    renderQueueMiddleware,
    async (req, res) => {
        try {
            const payload = req.body || {};

            payload._logoBuffer = await loadLogoBuffer(
                payload?.branding?.logoUrl
            );

            const branding = getBranding(payload);

            const doc = new PDFDocument({
                size: 'A4',
                margin: 42,
                bufferPages: true,
                info: {
                    Title:
                        branding.coverTitle ||
                        payload?.meta?.templateName ||
                        'Reporte Eficentra',

                    Author:
                        branding.companyName ||
                        'Eficentra'
                }
            });

            doc._eficentraTheme = buildReportTheme(payload);

            doc.on('error', error => {
                console.error(
                    '[report-render] PDF stream error:',
                    error
                );

                if (!res.destroyed) {
                    res.destroy(error);
                }
            });

            renderCover(doc, payload);
            normalizePdfState(doc);

            const tocEntries = [];
            const tocPageIndex =
                reserveTableOfContents(doc);

            recordTocEntry(
                doc,
                tocEntries,
                'Resumen ejecutivo'
            );
            renderExecutiveSummary(doc, payload);
            normalizePdfState(doc);

            recordTocEntry(
                doc,
                tocEntries,
                'Calidad de datos'
            );
            renderDataQuality(doc, payload);
            normalizePdfState(doc);

            if ((payload?.summary?.kpis || []).length) {
                recordTocEntry(
                    doc,
                    tocEntries,
                    'Indicadores principales'
                );
            }

            renderKpis(doc, payload);
            normalizePdfState(doc);

            if ((payload?.data?.tables || []).length) {
                recordTocEntry(
                    doc,
                    tocEntries,
                    'Estadística general'
                );
            }

            renderStatisticsTables(doc, payload);
            normalizePdfState(doc);

            if ((payload?.data?.timeSeries || []).length) {
                recordTocEntry(
                    doc,
                    tocEntries,
                    'Gráficas'
                );
            }

            renderCharts(doc, payload, tocEntries);
            normalizePdfState(doc);

            const advancedAnalysis =
                getAdvancedAnalysis(payload);

            if (advancedAnalysis.results.length) {
                recordTocEntry(
                    doc,
                    tocEntries,
                    'Análisis avanzado'
                );

                renderAdvancedAnalysisSummary(
                    doc,
                    payload
                );

                normalizePdfState(doc);
            }

            if (getCombinedObservations(payload).length) {
                recordTocEntry(
                    doc,
                    tocEntries,
                    'Observaciones'
                );
            }

            renderObservations(doc, payload);
            normalizePdfState(doc);

            recordTocEntry(
                doc,
                tocEntries,
                'Conclusión'
            );

            renderConclusion(doc, payload);
            normalizePdfState(doc);

            renderTableOfContents(
                doc,
                payload,
                tocPageIndex,
                tocEntries
            );

            renderPageFooters(doc, payload);

            res.status(200);

            res.setHeader(
                'Content-Type',
                'application/pdf'
            );

            res.setHeader(
                'Content-Disposition',
                'inline; filename="report.pdf"'
            );

            res.setHeader(
                'Cache-Control',
                'no-store'
            );

            doc.pipe(res);
            doc.end();
        } catch (err) {
            console.error(
                '[report-render] Failed to render report:',
                err
            );

            if (!res.headersSent && !res.destroyed) {
                res.status(500).json({
                    message:
                        'Failed to render report',
                    error:
                        err?.message ||
                        'Unknown render error'
                });
            } else if (!res.destroyed) {
                res.destroy(err);
            }
        }
    });

function readIntegerEnvironment(
    name,
    fallback,
    minimum,
    maximum
) {
    const value = Number(
        process.env[name]
    );

    if (!Number.isFinite(value)) {
        return fallback;
    }

    return Math.max(
        minimum,
        Math.min(
            maximum,
            Math.floor(value)
        )
    );
}

function bytesToMegabytes(value) {
    return Math.round(
        Number(value || 0) /
        1024 /
        1024 *
        10
    ) / 10;
}

async function renderQueueMiddleware(
    req,
    res,
    next
) {
    const releaseRenderSlot =
        await acquireRenderSlot(req, res);

    if (!releaseRenderSlot) {
        return;
    }

    let released = false;
    let outcomeRecorded = false;

    const releaseOnce = () => {
        if (released) {
            return;
        }

        released = true;
        releaseRenderSlot();
    };

    const recordOutcome = success => {
        if (outcomeRecorded) {
            return;
        }

        outcomeRecorded = true;

        if (success) {
            completedRenders++;
        } else {
            failedRenders++;
        }
    };

    res.once('finish', () => {
        recordOutcome(
            res.statusCode < 500
        );

        releaseOnce();
    });

    res.once('close', () => {
        if (!res.writableEnded) {
            recordOutcome(false);
        }

        releaseOnce();
    });

    next();
}

function acquireRenderSlot(req, res) {
    if (req.aborted || res.destroyed) {
        return Promise.resolve(null);
    }

    if (activeRenders < renderConcurrency) {
        activeRenders++;

        return Promise.resolve(
            createRenderSlotRelease()
        );
    }

    if (
        renderMaxQueue === 0 ||
        renderQueue.length >= renderMaxQueue
    ) {
        rejectedRenders++;

        res.setHeader(
            'Retry-After',
            '5'
        );

        res.status(429).json({
            message:
                'Report renderer is busy',
            activeRenders,
            queuedRenders:
                renderQueue.length,
            maxQueue:
                renderMaxQueue
        });

        return Promise.resolve(null);
    }

    return new Promise(resolve => {
        const entry = {
            req,
            res,
            resolve,
            enqueuedAt:
                Date.now(),
            settled:
                false,
            timer:
                null,
            cleanup:
                null
        };

        const removeFromQueue = () => {
            const index =
                renderQueue.indexOf(entry);

            if (index >= 0) {
                renderQueue.splice(index, 1);
            }
        };

        const onAborted = () => {
            if (entry.settled) {
                return;
            }

            entry.settled = true;
            entry.cleanup?.();
            removeFromQueue();
            resolve(null);
        };

        const onClose = () => {
            if (!res.writableEnded) {
                onAborted();
            }
        };

        entry.cleanup = () => {
            if (entry.timer) {
                clearTimeout(entry.timer);
                entry.timer = null;
            }

            req.removeListener(
                'aborted',
                onAborted
            );

            res.removeListener(
                'close',
                onClose
            );
        };

        req.once(
            'aborted',
            onAborted
        );

        res.once(
            'close',
            onClose
        );

        entry.timer = setTimeout(() => {
            if (entry.settled) {
                return;
            }

            entry.settled = true;
            entry.cleanup?.();
            removeFromQueue();
            rejectedRenders++;

            if (
                !res.headersSent &&
                !res.destroyed
            ) {
                res.setHeader(
                    'Retry-After',
                    '5'
                );

                res.status(503).json({
                    message:
                        'Timed out waiting for a render slot',
                    queueTimeoutMs:
                        renderQueueTimeoutMs
                });
            }

            resolve(null);
        }, renderQueueTimeoutMs);

        renderQueue.push(entry);
    });
}

function createRenderSlotRelease() {
    let released = false;

    return () => {
        if (released) {
            return;
        }

        released = true;

        activeRenders = Math.max(
            0,
            activeRenders - 1
        );

        setImmediate(
            dispatchQueuedRenders
        );
    };
}

function dispatchQueuedRenders() {
    while (
        activeRenders < renderConcurrency &&
        renderQueue.length > 0
    ) {
        const entry =
            renderQueue.shift();

        if (
            !entry ||
            entry.settled ||
            entry.req.aborted ||
            entry.res.destroyed
        ) {
            entry?.cleanup?.();
            continue;
        }

        entry.settled = true;
        entry.cleanup?.();

        activeRenders++;

        totalQueueWaitMs +=
            Date.now() -
            entry.enqueuedAt;

        queuedRenderStarts++;

        entry.resolve(
            createRenderSlotRelease()
        );
    }
}

app.use((err, req, res, next) => {
    if (err?.type === 'entity.too.large') {
        rejectedRenders++;

        res.status(413).json({
            message:
                'Report payload is too large',
            maxPayloadMb:
                renderMaxPayloadMb
        });

        return;
    }

    if (
        err instanceof SyntaxError &&
        err.status === 400 &&
        Object.prototype.hasOwnProperty.call(
            err,
            'body'
        )
    ) {
        res.status(400).json({
            message:
                'Invalid JSON payload'
        });

        return;
    }

    next(err);
});

function reserveTableOfContents(doc) {
    const pageIndex =
        getCurrentBufferedPageIndex(doc);

    /*
     * La página actual, creada después de la portada,
     * se reserva para el índice.
     */
    doc.addPage();
    normalizePdfState(doc);

    return pageIndex;
}

function getCurrentBufferedPageIndex(doc) {
    const range =
        doc.bufferedPageRange();

    return (
        range.start +
        range.count -
        1
    );
}

function recordTocEntry(
    doc,
    entries,
    title,
    level = 0
) {
    const range =
        doc.bufferedPageRange();

    const physicalPageIndex =
        getCurrentBufferedPageIndex(doc);

    /*
     * La portada es la página física cero.
     * La página física uno corresponde a Página 1.
     */
    const contentPageNumber =
        physicalPageIndex -
        range.start;

    entries.push({
        title,
        pageNumber: contentPageNumber,
        level
    });
}

function renderTableOfContents(
    doc,
    payload,
    tocPageIndex,
    entries
) {
    const lastPageIndex =
        getCurrentBufferedPageIndex(doc);

    doc.switchToPage(tocPageIndex);

    doc.x = doc.page.margins.left;
    doc.y = doc.page.margins.top;

    sectionTitle(doc, 'Índice');

    const left =
        doc.page.margins.left;

    const right =
        doc.page.width -
        doc.page.margins.right;

    const titleWidth = 330;
    const pageWidth = 45;

    /*
     * Se usa un for clásico porque break no es válido
     * dentro del callback de Array.forEach().
     */
    for (
        let index = 0;
        index < entries.length;
        index++
    ) {
        const entry = entries[index];

        /*
         * La versión actual reserva una sola página
         * para el índice. Si se llena, termina de
         * imprimir entradas sin crear páginas nuevas.
         */
        if (
            doc.y >
            doc.page.height -
            doc.page.margins.bottom -
            40
        ) {
            break;
        }

        const y = doc.y;
        const level = Number(entry.level) || 0;
        const indent = Math.min(level, 2) * 18;
        const entryFontSize = level > 0 ? 8.8 : 10;
        const entryColor = level > 0 ? '#4F5D6B' : '#263238';

        doc.fontSize(entryFontSize)
            .fillColor(entryColor)
            .text(
                entry.title,
                left + indent,
                y,
                {
                    width: titleWidth - indent,
                    lineBreak: false
                }
            );

        const lineStart =
            left + titleWidth + 8;

        const lineEnd =
            right - pageWidth - 8;

        doc.moveTo(
            lineStart,
            y + 7
        )
            .lineTo(
                lineEnd,
                y + 7
            )
            .dash(
                1,
                {
                    space: 3
                }
            )
            .strokeColor('#A7B6C3')
            .lineWidth(0.6)
            .stroke()
            .undash();

        doc.fontSize(10)
            .fillColor(
                getTheme(doc).primaryColor
            )
            .text(
                String(entry.pageNumber),
                right - pageWidth,
                y,
                {
                    width: pageWidth,
                    align: 'right',
                    lineBreak: false
                }
            );

        doc.y = y + (level > 0 ? 22 : 28);
    }

    const period =
        payload?.period || {};

    if (
        period.startTs &&
        period.endTs &&
        doc.y <
        doc.page.height -
        doc.page.margins.bottom -
        60
    ) {
        doc.moveDown(2);

        doc.fontSize(8)
            .fillColor('#6B7785')
            .text(
                `Periodo: ${formatTimestamp(period.startTs)} - ${formatTimestamp(period.endTs)}`,
                left,
                doc.y,
                {
                    width: right - left
                }
            );
    }

    doc.switchToPage(lastPageIndex);
}

async function loadLogoBuffer(value) {
    const rawValue = String(value || '').trim();

    if (!rawValue) {
        return null;
    }

    try {
        if (rawValue.startsWith('data:image/')) {
            return parseLogoDataUri(rawValue);
        }

        const baseUrl =
            process.env.REPORT_PUBLIC_BASE_URL ||
            'https://eficentra.indtesa.com';

        const url = new URL(rawValue, baseUrl);

        validateLogoUrl(url);

        return await downloadLogoBuffer(url, 0);
    } catch (error) {
        console.warn(
            '[report-render] No fue posible cargar el logotipo:',
            error.message
        );

        return null;
    }
}

function parseLogoDataUri(value) {
    const match = value.match(
        /^data:image\/(png|jpeg|jpg);base64,(.+)$/i
    );

    if (!match) {
        throw new Error(
            'El logotipo embebido debe ser PNG o JPG.'
        );
    }

    const buffer = Buffer.from(match[2], 'base64');

    validateLogoBuffer(buffer);

    return buffer;
}

function validateLogoUrl(url) {
    if (
        url.protocol !== 'http:' &&
        url.protocol !== 'https:'
    ) {
        throw new Error(
            'El protocolo del logotipo no está permitido.'
        );
    }

    const allowedHosts = new Set(
        (
            process.env.ALLOWED_LOGO_HOSTS ||
            [
                'eficentra.indtesa.com',
                'indtesa.com',
                '127.0.0.1',
                'localhost'
            ].join(',')
        )
            .split(',')
            .map(host => host.trim().toLowerCase())
            .filter(Boolean)
    );

    if (!allowedHosts.has(url.hostname.toLowerCase())) {
        throw new Error(
            `Host no permitido para el logotipo: ${url.hostname}`
        );
    }
}

function downloadLogoBuffer(url, redirectCount) {
    return new Promise((resolve, reject) => {
        if (redirectCount > 3) {
            reject(
                new Error('Demasiadas redirecciones.')
            );
            return;
        }

        const client =
            url.protocol === 'https:'
                ? https
                : http;

        const request = client.get(
            url,
            {
                headers: {
                    'User-Agent':
                        'Eficentra-report-render/1.0',

                    Accept:
                        'image/png,image/jpeg'
                }
            },
            response => {
                const statusCode =
                    response.statusCode || 0;

                if (
                    statusCode >= 300 &&
                    statusCode < 400 &&
                    response.headers.location
                ) {
                    response.resume();

                    const redirectUrl = new URL(
                        response.headers.location,
                        url
                    );

                    try {
                        validateLogoUrl(redirectUrl);
                    } catch (error) {
                        reject(error);
                        return;
                    }

                    downloadLogoBuffer(
                        redirectUrl,
                        redirectCount + 1
                    )
                        .then(resolve)
                        .catch(reject);

                    return;
                }

                if (statusCode !== 200) {
                    response.resume();

                    reject(
                        new Error(
                            `HTTP ${statusCode} al descargar el logotipo.`
                        )
                    );

                    return;
                }

                const chunks = [];
                let totalBytes = 0;
                const maximumBytes =
                    3 * 1024 * 1024;

                response.on('data', chunk => {
                    totalBytes += chunk.length;

                    if (totalBytes > maximumBytes) {
                        request.destroy(
                            new Error(
                                'El logotipo supera 3 MB.'
                            )
                        );

                        return;
                    }

                    chunks.push(chunk);
                });

                response.on('end', () => {
                    try {
                        const buffer =
                            Buffer.concat(chunks);

                        validateLogoBuffer(buffer);

                        resolve(buffer);
                    } catch (error) {
                        reject(error);
                    }
                });

                response.on('error', reject);
            }
        );

        request.setTimeout(7000, () => {
            request.destroy(
                new Error(
                    'Tiempo agotado al descargar el logotipo.'
                )
            );
        });

        request.on('error', reject);
    });
}

function validateLogoBuffer(buffer) {
    if (!buffer || buffer.length < 4) {
        throw new Error(
            'El archivo del logotipo está vacío.'
        );
    }

    const isPng =
        buffer[0] === 0x89 &&
        buffer[1] === 0x50 &&
        buffer[2] === 0x4E &&
        buffer[3] === 0x47;

    const isJpeg =
        buffer[0] === 0xFF &&
        buffer[1] === 0xD8 &&
        buffer[2] === 0xFF;

    if (!isPng && !isJpeg) {
        throw new Error(
            'El logotipo debe ser PNG o JPG.'
        );
    }
}

function renderCoverLogo(doc, payload, options = {}) {
    const logoBuffer = payload?._logoBuffer;

    if (!Buffer.isBuffer(logoBuffer)) {
        return false;
    }

    const boxWidth = options.width || 128;
    const boxHeight = options.height || 72;
    const x = options.x ?? doc.page.margins.left;
    const y = options.y ?? 28;

    try {
        doc.save();

        doc.roundedRect(
            x,
            y,
            boxWidth,
            boxHeight,
            6
        )
            .fillOpacity(0.98)
            .fill('#FFFFFF');

        doc.fillOpacity(1);

        doc.image(
            logoBuffer,
            x + 8,
            y + 8,
            {
                fit: [
                    boxWidth - 16,
                    boxHeight - 16
                ],
                align: 'center',
                valign: 'center'
            }
        );

        doc.restore();
        return true;
    } catch (error) {
        try {
            doc.restore();
        } catch (_) {
            // El estado puede no haberse guardado si PDFKit falló antes.
        }

        doc.fillOpacity(1);

        console.warn(
            '[report-render] Error dibujando logotipo:',
            error.message
        );

        return false;
    }
}

function renderCover(doc, payload) {
    const branding = getBranding(payload);
    const theme = getTheme(doc);

    const primaryColor = theme.primaryColor;
    const secondaryColor = theme.secondaryColor;
    const primaryTextColor = contrastTextColor(primaryColor);

    const title =
        branding.coverTitle ||
        payload?.meta?.templateName ||
        'Reporte Eficentra';

    const subtitle =
        branding.coverSubtitle ||
        payload?.meta?.reportType ||
        'Reporte técnico';



    const generatedAt =
        payload?.meta?.generatedAt ||
        new Date().toISOString();

    const period = payload?.period || {};

    const left = doc.page.margins.left;
    const contentWidth =
        doc.page.width -
        doc.page.margins.left -
        doc.page.margins.right;

    /*
     * Encabezado principal. El fondo se dibuja antes del logotipo;
     * de lo contrario la imagen queda cubierta por el rectángulo.
     */
    doc.rect(
        0,
        0,
        doc.page.width,
        148
    ).fill(primaryColor);

    doc.rect(
        0,
        140,
        doc.page.width,
        8
    ).fill(secondaryColor);

    const logoRendered = renderCoverLogo(
        doc,
        payload,
        {
            x: left,
            y: 28,
            width: 128,
            height: 72
        }
    );

    const headerTextX = logoRendered ? left + 148 : left;
    const headerTextWidth = contentWidth - (logoRendered ? 148 : 0);

    doc.fillColor(primaryTextColor)
        .fontSize(26)
        .text(
            safeText(branding.companyName || 'Eficentra', 55),
            headerTextX,
            38,
            {
                width: headerTextWidth,
                lineBreak: false
            }
        );

    doc.fontSize(11)
        .fillColor(primaryTextColor)
        .text(
            safeText(
                branding.siteName ||
                'Sistema de monitoreo y análisis operativo',
                90
            ),
            headerTextX,
            79,
            {
                width: headerTextWidth,
                lineBreak: false
            }
        );

    /*
     * Título y subtítulo.
     */
    doc.fillColor('#111111')
        .fontSize(24)
        .text(
            safeText(title, 150),
            left,
            184,
            {
                width: contentWidth,
                lineGap: 4
            }
        );

    doc.moveDown(0.5);

    doc.fontSize(13)
        .fillColor('#4F5D6B')
        .text(
            safeText(subtitle, 140),
            left,
            doc.y,
            {
                width: contentWidth,
                lineGap: 3
            }
        );

    /*
     * Línea decorativa.
     */
    const dividerY = doc.y + 28;

    doc.moveTo(left, dividerY)
        .lineTo(
            doc.page.width - doc.page.margins.right,
            dividerY
        )
        .strokeColor(secondaryColor)
        .lineWidth(2)
        .stroke();

    /*
     * Información principal.
     */
    let detailY = dividerY + 30;

    const detailRows = [];

    if (branding.customerName) {
        detailRows.push([
            'Cliente',
            branding.customerName
        ]);
    }

    if (branding.siteName) {
        detailRows.push([
            'Planta o instalación',
            branding.siteName
        ]);
    }

    detailRows.push([
        'Empresa responsable',
        branding.companyName || 'Eficentra'
    ]);

    if (
        branding.showGeneratedDate &&
        generatedAt
    ) {
        detailRows.push([
            'Fecha de generación',
            formatIsoDate(generatedAt)
        ]);
    }

    if (period.startTs && period.endTs) {
        detailRows.push([
            'Periodo analizado',
            `${formatTimestamp(period.startTs)} - ${formatTimestamp(period.endTs)}`
        ]);
    }

    detailRows.forEach(([label, value]) => {
        doc.fontSize(9)
            .fillColor('#697887')
            .text(
                label,
                left,
                detailY,
                {
                    width: 125,
                    lineBreak: false
                }
            );

        doc.fontSize(10)
            .fillColor('#222222')
            .text(
                safeText(value, 115),
                left + 135,
                detailY,
                {
                    width: contentWidth - 135,
                    lineBreak: false
                }
            );

        detailY += 26;
    });

    /*
     * Pie visual de portada.
     */
    const coverFooterY =
        doc.page.height -
        doc.page.margins.bottom -
        70;

    doc.moveTo(left, coverFooterY)
        .lineTo(
            doc.page.width - doc.page.margins.right,
            coverFooterY
        )
        .strokeColor('#DCE6EF')
        .lineWidth(1)
        .stroke();

    if (branding.footerText) {
        doc.fontSize(9)
            .fillColor('#4F5D6B')
            .text(
                safeText(branding.footerText, 130),
                left,
                coverFooterY + 14,
                {
                    width: contentWidth,
                    align: 'left'
                }
            );
    }

    if (branding.confidentialityText) {
        doc.fontSize(7)
            .fillColor('#778491')
            .text(
                safeText(
                    branding.confidentialityText,
                    150
                ),
                left,
                coverFooterY + 36,
                {
                    width: contentWidth,
                    align: 'left'
                }
            );
    }

    /*
     * La portada no lleva numeración.
     */
    doc.addPage();
    normalizePdfState(doc);
}

function renderExecutiveSummary(doc, payload) {
    sectionTitle(doc, 'Resumen ejecutivo');

    const entities = payload?.context?.entities || [];
    const kpis = payload?.summary?.kpis || [];
    const observations =
        getCombinedObservations(payload);

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
    const kpis =
        payload?.summary?.kpis || [];

    if (!kpis.length) {
        return;
    }

    sectionTitle(
        doc,
        'Indicadores principales'
    );

    const cardWidth = 160;
    const cardHeight = 88;
    const gap = 12;

    let x =
        doc.page.margins.left;

    let y =
        doc.y;

    kpis.forEach(kpi => {
        if (
            x + cardWidth >
            doc.page.width -
            doc.page.margins.right
        ) {
            x =
                doc.page.margins.left;

            y +=
                cardHeight +
                gap;
        }

        if (
            y + cardHeight >
            doc.page.height -
            doc.page.margins.bottom
        ) {
            doc.addPage();

            normalizePdfState(doc);

            x =
                doc.page.margins.left;

            y =
                doc.y;
        }

        const variableLabel =
            String(
                kpi?.label ||
                kpi?.key ||
                'Indicador'
            ).trim();

        const aggregationLabel =
            getKpiAggregationLabel(
                kpi?.aggregation
            );

        const entityName =
            String(
                kpi?.entityName || ''
            ).trim();

        const secondaryLabel = [
            aggregationLabel,
            entityName
        ]
            .filter(Boolean)
            .join(' · ');

        const formattedValue =
            String(
                kpi?.formattedValue ??
                formatNumber(kpi?.value)
            ).trim();

        const unit =
            String(
                kpi?.unit || ''
            ).trim();

        const valueText = [
            formattedValue,
            unit
        ]
            .filter(Boolean)
            .join(' ');

        doc.roundedRect(
            x,
            y,
            cardWidth,
            cardHeight,
            8
        )
            .fillAndStroke(
                '#F5F8FB',
                '#DCE6EF'
            );

        /*
         * Etiqueta visible de la variable.
         */
        doc.font('Helvetica-Bold')
            .fillColor('#17212B')
            .fontSize(9)
            .text(
                variableLabel,
                x + 10,
                y + 10,
                {
                    width:
                        cardWidth - 20,

                    height: 24,

                    ellipsis: true
                }
            );

        /*
         * Tipo de operación y entidad.
         */
        doc.font('Helvetica')
            .fillColor('#667482')
            .fontSize(7.5)
            .text(
                secondaryLabel,
                x + 10,
                y + 37,
                {
                    width:
                        cardWidth - 20,

                    height: 12,

                    ellipsis: true,

                    lineBreak: false
                }
            );

        /*
         * Resultado con la unidad.
         */
        doc.font('Helvetica-Bold')
            .fillColor(
                getTheme(doc).primaryColor
            )
            .fontSize(16)
            .text(
                valueText,
                x + 10,
                y + 56,
                {
                    width:
                        cardWidth - 20,

                    height: 22,

                    ellipsis: true,

                    lineBreak: false
                }
            );

        doc.font('Helvetica');

        x +=
            cardWidth +
            gap;
    });

    doc.y =
        y +
        cardHeight +
        20;

    normalizePdfState(doc);
}

function getKpiAggregationLabel(
    aggregation
) {
    const normalized =
        String(
            aggregation || ''
        )
            .trim()
            .toUpperCase();

    const labels = {
        AVG: 'Promedio',
        MIN: 'Mínimo',
        MAX: 'Máximo',
        SUM: 'Suma',
        COUNT: 'Muestras',
        FIRST: 'Primer valor',
        LAST: 'Último valor',
        DELTA: 'Variación'
    };

    return (
        labels[normalized] ||
        'Indicador'
    );
}

function renderStatisticsTables(doc, payload) {
    const tables = payload?.data?.tables || [];

    if (!tables.length) {
        return;
    }

    tables.forEach(table => {
        sectionTitle(doc, table.title || 'Tabla de datos');

        const columns = table.columns || [];
        const rows = prepareStatisticsRows(payload, table.rows || []);

        if (!columns.length || !rows.length) {
            doc.fontSize(10).fillColor('#666').text('No hay datos disponibles para esta tabla.');
            doc.moveDown();
            return;
        }

        renderTable(doc, columns, rows, payload);
        doc.moveDown();
    });
}

function prepareStatisticsRows(payload, rows) {
    return (rows || []).map(row => {
        const key = row?.key ?? row?.variable ?? '';
        const entityName = row?.entity ?? row?.entityName ?? '';
        const variable = findVariableConfigByKey(
            payload,
            key,
            entityName
        );

        if (!variable) {
            return row;
        }

        return {
            ...row,
            key: formatVariableLabel(variable, key, true),
            variable: formatVariableLabel(variable, key, true)
        };
    });
}

function renderTable(doc, columns, rows, payload) {
    const startX = doc.page.margins.left;
    const tableWidth = doc.page.width - doc.page.margins.left - doc.page.margins.right;
    const rowHeight = 24;

    const columnWeights = columns.map(col => {
        if (col.key === 'entity') return 1.35;
        if (col.key === 'key' || col.key === 'variable') return 1.55;
        if (col.key === 'samples') return 0.7;
        if (col.key === 'firstTs' || col.key === 'lastTs') return 1.45;
        return 0.9;
    });

    const totalWeight = columnWeights.reduce((sum, weight) => sum + weight, 0);
    const colWidths = columnWeights.map(weight => (tableWidth * weight) / totalWeight);

    ensureSpace(doc, rowHeight * 3);

    let y = doc.y;

    doc.rect(startX, y, tableWidth, rowHeight).fill(getTheme(doc).primaryColor);

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

            doc.rect(startX, y, tableWidth, rowHeight).fill(getTheme(doc).primaryColor);;

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
                value = formatTableDate(value, getPayloadTimezone(payload));
            }

            if (col.key === 'entity') {
                value = safeText(value, 20);
            } else if (col.key === 'firstTs' || col.key === 'lastTs') {
                value = safeText(value, 22);
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

function renderCharts(doc, payload, tocEntries = []) {
    const series = (payload?.data?.timeSeries || [])
        .map(item => applySeriesPresentation(payload, item));

    if (!series.length) {
        return;
    }

    normalizePdfState(doc);
    ensureSpace(doc, 280);
    sectionTitle(doc, 'Gráficas de serie temporal');
    normalizePdfState(doc);

    const chartLayout = getChartLayout(payload);

    if (chartLayout === 'COMBINED') {
        ensureSpace(doc, 390);
        normalizePdfState(doc);

        const seenVariables = new Set();

        series.forEach(item => {
            const id = `${item.entityId || item.entityName || ''}:${item.key || item.label || ''}`;

            if (seenVariables.has(id)) {
                return;
            }

            seenVariables.add(id);
            recordTocEntry(
                doc,
                tocEntries,
                `${item.label || item.key}${item.entityName ? ` · ${item.entityName}` : ''}`,
                1
            );
        });

        renderCombinedChartsWithGranularity(doc, payload, series);
        return;
    }

    series.forEach(item => {
        let tocRecorded = false;

        renderSeriesWithGranularity(
            doc,
            payload,
            item,
            () => {
                if (tocRecorded) {
                    return;
                }

                tocRecorded = true;
                recordTocEntry(
                    doc,
                    tocEntries,
                    `${item.label || item.key}${item.entityName ? ` · ${item.entityName}` : ''}`,
                    1
                );
            }
        );
    });

    normalizePdfState(doc);
}

function renderSeriesMiniTable(doc, series, payload) {
    const points = series.points || [];
    const stats = calculateStats(points);

    if (!stats) {
        return;
    }

    const unitSuffix = series?.unit ? ` (${series.unit})` : '';

    const columns = [
        { key: 'samples', label: 'Muestras', align: 'right' },
        { key: 'min', label: `Mínimo${unitSuffix}`, align: 'right' },
        { key: 'max', label: `Máximo${unitSuffix}`, align: 'right' },
        { key: 'avg', label: `Promedio${unitSuffix}`, align: 'right' },
        { key: 'firstTs', label: 'Primera muestra', align: 'right' },
        { key: 'lastTs', label: 'Última muestra', align: 'right' }
    ];

    const rows = [
        {
            samples: stats.count,
            min: formatNumber(stats.min),
            max: formatNumber(stats.max),
            avg: formatNumber(stats.avg),
            firstTs: formatTableDate(stats.firstTs, getPayloadTimezone(payload)),
            lastTs: formatTableDate(stats.lastTs, getPayloadTimezone(payload))
        }
    ];

    renderCompactTable(doc, columns, rows);
}

function renderCombinedChartsWithGranularity(
    doc,
    payload,
    series
) {
    const combinedConfig =
        getCombinedChartConfig(payload);

    const granularity =
        getCombinedChartGranularity(
            payload,
            series
        );

    const timezone =
        getPayloadTimezone(payload);

    const chartUnits =
        buildCombinedChartUnits(
            series,
            granularity,
            timezone,
            combinedConfig
        );

    if (!chartUnits.length) {
        return;
    }

    const pageLayout =
        resolveCombinedPageLayout(
            combinedConfig,
            chartUnits
        );

    console.log(
        '[report-render] combined layout:',
        'granularity=',
        granularity,
        'groupMode=',
        combinedConfig.groupMode,
        'units=',
        chartUnits.length,
        'series=',
        series.length,
        'dataInterval=',
        combinedConfig.dataInterval,
        'density=',
        pageLayout.density,
        'chartsPerPage=',
        pageLayout.chartsPerPage,
        'effectiveTableMode=',
        pageLayout.tableMode
    );

    if (pageLayout.density === 'DETAILED') {
        chartUnits.forEach(unit => {
            renderCombinedChartsPage(
                doc,
                payload,
                unit,
                combinedConfig
            );
        });
    } else {
        renderCombinedChartsByDensity(
            doc,
            payload,
            chartUnits,
            combinedConfig,
            pageLayout
        );
    }

    normalizePdfState(doc);
}

function getCombinedChartConfig(payload) {
    const rawConfig =
        findCombinedChartConfig(payload, 0) || {};

    const rawStats =
        rawConfig.stats || {};

    const tableMode =
        normalizeCombinedOption(
            rawConfig.tableMode,
            ['FULL', 'COMPACT', 'NONE'],
            rawConfig.tableEnabled === false
                ? 'NONE'
                : 'FULL'
        );

    return {
        granularity: normalizeCombinedOption(
            rawConfig.granularity,
            ['FULL', 'DAY', 'WEEK', 'MONTH'],
            'FULL'
        ),

        groupMode: normalizeCombinedOption(
            rawConfig.groupMode,
            ['ALL_SERIES', 'BY_ENTITY', 'BY_VARIABLE'],
            'BY_ENTITY'
        ),

        titleMode: normalizeCombinedOption(
            rawConfig.titleMode,
            ['AUTO', 'ENTITY_NAME', 'VARIABLE_NAME', 'CUSTOM'],
            'AUTO'
        ),

        customTitle:
            String(
                rawConfig.customTitle || ''
            ).trim(),

        sortMode: normalizeCombinedOption(
            rawConfig.sortMode,
            ['ENTITY_THEN_PERIOD', 'PERIOD_THEN_ENTITY'],
            'ENTITY_THEN_PERIOD'
        ),

        dataInterval: normalizeCombinedOption(
            rawConfig.dataInterval,
            [
                'AUTO',
                'RAW',
                'FIFTEEN_MINUTES',
                'THIRTY_MINUTES',
                'HOUR',
                'CUSTOM'
            ],
            'AUTO'
        ),

        customIntervalMinutes: clampNumber(
            rawConfig.customIntervalMinutes,
            1,
            10080,
            60
        ),

        bucketAggregation: normalizeCombinedOption(
            rawConfig.bucketAggregation,
            ['AVG', 'MIN', 'MAX', 'SUM', 'FIRST', 'LAST'],
            'AVG'
        ),

        pageDensity: normalizeCombinedOption(
            rawConfig.pageDensity,
            ['AUTO', 'DETAILED', 'COMPACT', 'DENSE'],
            'DETAILED'
        ),

        tableMode,

        legendMode: normalizeCombinedOption(
            rawConfig.legendMode,
            ['AUTO', 'PER_CHART', 'SHARED', 'NONE'],
            'AUTO'
        ),

        seriesNameMode: normalizeCombinedOption(
            rawConfig.seriesNameMode,
            [
                'AUTO',
                'LABEL_ONLY',
                'LABEL_AND_ENTITY',
                'NUMBERED'
            ],
            'AUTO'
        ),

        /*
         * Compatibilidad con la lógica anterior.
         */
        tableEnabled:
            tableMode !== 'NONE',

        stats: {
            min:
                rawStats.min !== false,

            max:
                rawStats.max !== false,

            avg:
                rawStats.avg !== false,

            count:
                rawStats.count !== false,

            sum:
                rawStats.sum === true,

            first:
                rawStats.first === true,

            last:
                rawStats.last === true,

            delta:
                rawStats.delta === true
        }
    };
}

function resolveCombinedPageLayout(
    combinedConfig,
    chartUnits
) {
    const units =
        Array.isArray(chartUnits)
            ? chartUnits
            : [];

    const maximumSeriesCount =
        units.reduce(
            (maximum, unit) =>
                Math.max(
                    maximum,
                    getValidCombinedSeries(unit).length
                ),
            0
        );

    let density =
        combinedConfig?.pageDensity ||
        'DETAILED';

    if (density === 'AUTO') {
        if (units.length <= 4) {
            density = 'DETAILED';
        } else if (
            units.length > 16 &&
            combinedConfig?.tableMode === 'NONE'
        ) {
            density = 'DENSE';
        } else {
            density = 'COMPACT';
        }
    }

    if (density === 'DENSE') {
        return {
            density: 'DENSE',

            chartsPerPage:
                maximumSeriesCount > 8
                    ? 2
                    : 4,

            chartHeight:
                maximumSeriesCount > 8
                    ? 105
                    : 82,

            tableMode:
                'NONE',

            titleFontSize:
                9,

            subtitleFontSize:
                6.8,

            blockGap:
                8,

            showNormalizationNote:
                false
        };
    }

    if (density === 'COMPACT') {
        return {
            density: 'COMPACT',

            chartsPerPage:
                maximumSeriesCount > 8
                    ? 1
                    : 2,

            chartHeight:
                maximumSeriesCount > 8
                    ? 135
                    : 108,

            tableMode:
                combinedConfig?.tableMode === 'NONE'
                    ? 'NONE'
                    : 'COMPACT',

            titleFontSize:
                10,

            subtitleFontSize:
                7,

            blockGap:
                12,

            showNormalizationNote:
                true
        };
    }

    return {
        density:
            'DETAILED',

        chartsPerPage:
            1,

        chartHeight:
            190,

        tableMode:
            combinedConfig?.tableMode ||
            'FULL',

        titleFontSize:
            12,

        subtitleFontSize:
            8,

        blockGap:
            16,

        showNormalizationNote:
            true
    };
}

function getValidCombinedSeries(unit) {
    return (unit?.series || [])
        .filter(item =>
            (item?.points || [])
                .some(point =>
                    point &&
                    Number.isFinite(
                        Number(point.value)
                    ) &&
                    Number.isFinite(
                        Number(point.ts)
                    )
                )
        );
}

function renderCombinedChartsByDensity(
    doc,
    payload,
    chartUnits,
    combinedConfig,
    pageLayout
) {
    let unitIndex = 0;
    let pageIndex = 0;

    while (unitIndex < chartUnits.length) {
        let pageUnits =
            chartUnits.slice(
                unitIndex,
                unitIndex +
                pageLayout.chartsPerPage
            );

        /*
         * Cuatro gráficas densas sólo se mantienen cuando
         * todas comparten la misma leyenda.
         *
         * Cuando las series son diferentes, se reducen a
         * dos gráficas para conservar legibilidad.
         */
        if (
            pageLayout.density === 'DENSE' &&
            pageUnits.length > 2 &&
            !canShareCombinedLegend(
                pageUnits,
                combinedConfig
            )
        ) {
            pageUnits =
                pageUnits.slice(
                    0,
                    2
                );
        }

        if (!pageUnits.length) {
            break;
        }

        /*
         * La primera página puede comenzar debajo del título
         * de sección. Las siguientes empiezan en una página
         * nueva.
         */
        if (pageIndex > 0) {
            doc.addPage();
            normalizePdfState(doc);
        } else {
            ensureSpace(
                doc,
                pageLayout.density === 'DENSE'
                    ? 520
                    : 600
            );
        }

        const sharedLegendSeries =
            shouldRenderSharedCombinedLegend(
                pageUnits,
                combinedConfig,
                pageLayout
            )
                ? getValidCombinedSeries(
                    pageUnits[0]
                )
                : null;

        /*
         * La explicación de normalización sólo se muestra
         * una vez, en lugar de repetirse para cada gráfica.
         */
        if (
            pageLayout.showNormalizationNote &&
            pageIndex === 0 &&
            pageUnits.some(unit =>
                getValidCombinedSeries(unit)
                    .length > 1
            )
        ) {
            renderCombinedNormalizationNote(
                doc
            );
        }

        /*
         * Cuando las gráficas contienen las mismas series,
         * la leyenda se dibuja una sola vez para toda la
         * página.
         */
        if (sharedLegendSeries) {
            renderCombinedLegend(
                doc,
                sharedLegendSeries,
                combinedConfig
            );

            normalizePdfState(doc);
            doc.moveDown(0.2);
        }

        pageUnits.forEach(
            (unit, index) => {
                renderCombinedChartDensityBlock(
                    doc,
                    payload,
                    unit,
                    combinedConfig,
                    pageLayout,
                    Boolean(
                        sharedLegendSeries
                    )
                );

                if (
                    index <
                    pageUnits.length - 1
                ) {
                    doc.y +=
                        pageLayout.blockGap;

                    normalizePdfState(doc);
                }
            }
        );

        unitIndex +=
            pageUnits.length;

        pageIndex++;
    }
}

function renderCombinedNormalizationNote(doc) {
    doc.font('Helvetica')
        .fontSize(7.2)
        .fillColor('#5F6B76')
        .text(
            'Las series se normalizan para comparar su comportamiento relativo aun cuando utilizan unidades diferentes.',
            doc.page.margins.left,
            doc.y,
            {
                width:
                    doc.page.width -
                    doc.page.margins.left -
                    doc.page.margins.right,

                lineGap:
                    1
            }
        );

    doc.moveDown(0.45);

    normalizePdfState(doc);
}

function shouldRenderSharedCombinedLegend(
    pageUnits,
    combinedConfig,
    pageLayout
) {
    const legendMode =
        combinedConfig?.legendMode ||
        'AUTO';

    if (
        legendMode === 'NONE' ||
        legendMode === 'PER_CHART'
    ) {
        return false;
    }

    if (
        legendMode === 'AUTO' &&
        pageLayout.density === 'DETAILED'
    ) {
        return false;
    }

    return canShareCombinedLegend(
        pageUnits,
        combinedConfig
    );
}

function canShareCombinedLegend(
    pageUnits,
    combinedConfig
) {
    const signatures =
        (pageUnits || [])
            .map(unit =>
                getCombinedLegendSignature(
                    getValidCombinedSeries(
                        unit
                    ),
                    combinedConfig
                )
            )
            .filter(Boolean);

    return (
        signatures.length > 0 &&
        signatures.every(
            signature =>
                signature ===
                signatures[0]
        )
    );
}

function getCombinedLegendSignature(
    series,
    combinedConfig
) {
    return buildCombinedSeriesDisplayEntries(
        series,
        combinedConfig
    )
        .map(entry => {
            const colorIndex =
                Number.isInteger(
                    entry.item?.__colorIndex
                )
                    ? entry.item.__colorIndex
                    : entry.index;

            return `${colorIndex}:${entry.displayName}`;
        })
        .join('|');
}

function renderCombinedChartDensityBlock(
    doc,
    payload,
    unit,
    combinedConfig,
    pageLayout,
    sharedLegendRendered
) {
    const validSeries =
        getValidCombinedSeries(
            unit
        );

    if (!validSeries.length) {
        return;
    }

    const title =
        resolveCombinedChartTitle(
            unit,
            combinedConfig
        );

    const periodSubtitle =
        resolveCombinedPeriodSubtitle(
            unit
        );

    const contentWidth =
        doc.page.width -
        doc.page.margins.left -
        doc.page.margins.right;

    const titleHeight =
        pageLayout.density === 'DENSE'
            ? 14
            : 17;

    doc.font('Helvetica-Bold')
        .fontSize(
            pageLayout.titleFontSize
        )
        .fillColor('#111111')
        .text(
            title,
            doc.page.margins.left,
            doc.y,
            {
                width:
                    contentWidth,

                height:
                    titleHeight,

                ellipsis:
                    true,

                lineBreak:
                    false
            }
        );

    doc.y +=
        titleHeight;

    if (periodSubtitle) {
        doc.font('Helvetica')
            .fontSize(
                pageLayout.subtitleFontSize
            )
            .fillColor('#5F6B76')
            .text(
                periodSubtitle,
                doc.page.margins.left,
                doc.y,
                {
                    width:
                        contentWidth,

                    height:
                        10,

                    ellipsis:
                        true,

                    lineBreak:
                        false
                }
            );

        doc.y +=
            11;
    }

    const chartBox = {
        x:
            doc.page.margins.left,

        y:
            doc.y + 7,

        width:
            contentWidth,

        height:
            pageLayout.chartHeight
    };

    /*
     * Sólo se reducen los puntos utilizados para dibujar.
     * Las tablas y estadísticas conservan la serie original.
     */
    const drawingSeries =
        prepareCombinedSeriesForDrawing(
            validSeries,
            combinedConfig
        );

    drawNormalizedCombinedLineChart(
        doc,
        drawingSeries,
        chartBox,
        getPayloadTimezone(payload)
    );

    doc.y =
        chartBox.y +
        chartBox.height +
        13;

    resetCursor(doc);

    const effectiveLegendMode =
        resolveEffectiveLegendMode(
            combinedConfig,
            validSeries
        );

    if (
        !sharedLegendRendered &&
        effectiveLegendMode !== 'NONE'
    ) {
        renderCombinedLegend(
            doc,
            validSeries,
            combinedConfig
        );

        normalizePdfState(doc);
        doc.moveDown(0.1);
    }

    if (
        pageLayout.tableMode !== 'NONE'
    ) {
        renderCombinedSeriesStatsTable(
            doc,
            validSeries,
            {
                ...combinedConfig,

                tableMode:
                    pageLayout.tableMode
            },
            {
                compact:
                    true,

                /*
                 * No se permite que una tabla compacta cree
                 * una página por su cuenta, porque el bloque
                 * ya fue distribuido por el algoritmo de
                 * densidad.
                 */
                allowPageBreak:
                    false
            }
        );

        normalizePdfState(doc);
    }
}

function normalizeCombinedOption(
    value,
    allowedValues,
    fallback
) {
    const normalized =
        String(value || '')
            .trim()
            .toUpperCase();

    return allowedValues.includes(normalized)
        ? normalized
        : fallback;
}

function clampNumber(
    value,
    minimum,
    maximum,
    fallback
) {
    const numericValue =
        Number(value);

    if (!Number.isFinite(numericValue)) {
        return fallback;
    }

    return Math.max(
        minimum,
        Math.min(
            maximum,
            numericValue
        )
    );
}

function findCombinedChartConfig(node, depth) {
    if (!node || depth > 8) {
        return null;
    }

    if (Array.isArray(node)) {
        for (const item of node) {
            const found = findCombinedChartConfig(item, depth + 1);
            if (found) {
                return found;
            }
        }

        return null;
    }

    if (typeof node !== 'object') {
        return null;
    }

    if (node.combinedChart && typeof node.combinedChart === 'object') {
        return node.combinedChart;
    }

    if (
        node.config?.combinedChart &&
        typeof node.config.combinedChart === 'object'
    ) {
        return node.config.combinedChart;
    }

    for (const value of Object.values(node)) {
        const found = findCombinedChartConfig(value, depth + 1);
        if (found) {
            return found;
        }
    }

    return null;
}

function getCombinedChartGranularity(payload, series) {
    const combinedConfig = getCombinedChartConfig(payload);
    const configuredGranularity = String(
        combinedConfig?.granularity || ''
    ).toUpperCase();

    if (
        configuredGranularity === 'FULL' ||
        configuredGranularity === 'DAY' ||
        configuredGranularity === 'WEEK' ||
        configuredGranularity === 'MONTH'
    ) {
        return configuredGranularity;
    }

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

function buildCombinedChartUnits(
    series,
    granularity,
    timezone,
    combinedConfig
) {
    const sourceSeries =
        Array.isArray(series)
            ? series.filter(Boolean)
            : [];

    if (!sourceSeries.length) {
        return [];
    }

    const colorIndexes =
        buildCombinedColorIndexes(
            sourceSeries,
            combinedConfig.groupMode
        );

    const groups =
        groupCombinedSeries(
            sourceSeries,
            combinedConfig.groupMode
        );

    const units = [];

    groups.forEach((group, groupIndex) => {
        const coloredSeries =
            group.series.map(item => ({
                ...item,

                __colorIndex:
                    colorIndexes.get(
                        getCombinedColorIdentity(
                            item,
                            combinedConfig.groupMode
                        )
                    ) || 0
            }));

        if (granularity === 'FULL') {
            units.push({
                groupKey:
                    group.key,

                groupLabel:
                    group.label,

                groupIndex,

                periodKey:
                    'FULL',

                periodLabel:
                    null,

                periodIndex:
                    0,

                granularity,

                series:
                    coloredSeries
            });

            return;
        }

        const periods =
            splitSeriesByGranularity(
                coloredSeries,
                granularity,
                timezone
            );

        periods.forEach(
            (period, periodIndex) => {
                units.push({
                    groupKey:
                        group.key,

                    groupLabel:
                        group.label,

                    groupIndex,

                    periodKey:
                        period.key,

                    periodLabel:
                        period.label,

                    periodIndex,

                    granularity,

                    series:
                        period.series
                });
            }
        );
    });

    return sortCombinedChartUnits(
        units,
        combinedConfig.sortMode
    );
}

function groupCombinedSeries(
    series,
    groupMode
) {
    const groups =
        new Map();

    (series || []).forEach(
        (item, index) => {
            const identity =
                resolveCombinedGroupIdentity(
                    item,
                    groupMode
                );

            if (!groups.has(identity.key)) {
                groups.set(
                    identity.key,
                    {
                        key:
                            identity.key,

                        label:
                            identity.label,

                        order:
                            index,

                        series:
                            []
                    }
                );
            }

            groups.get(identity.key)
                .series
                .push(item);
        }
    );

    return Array.from(
        groups.values()
    );
}

function resolveCombinedGroupIdentity(
    item,
    groupMode
) {
    if (groupMode === 'BY_VARIABLE') {
        return {
            key:
                `variable:${item?.key ||
                item?.label ||
                'unknown'
                }`,

            label:
                item?.label ||
                item?.key ||
                'Variable'
        };
    }

    if (groupMode === 'ALL_SERIES') {
        return {
            key:
                'all-series',

            label:
                'Comparativa de equipos'
        };
    }

    return {
        key:
            `entity:${item?.entityType || 'ENTITY'
            }:${item?.entityId ||
            item?.entityName ||
            'unknown'
            }`,

        label:
            item?.entityName ||
            'Dispositivo'
    };
}

function sortCombinedChartUnits(
    units,
    sortMode
) {
    return [...units].sort(
        (left, right) => {
            if (
                sortMode ===
                'PERIOD_THEN_ENTITY'
            ) {
                const periodComparison =
                    String(left.periodKey)
                        .localeCompare(
                            String(right.periodKey)
                        );

                if (periodComparison !== 0) {
                    return periodComparison;
                }

                return (
                    left.groupIndex -
                    right.groupIndex
                );
            }

            if (
                left.groupIndex !==
                right.groupIndex
            ) {
                return (
                    left.groupIndex -
                    right.groupIndex
                );
            }

            return String(left.periodKey)
                .localeCompare(
                    String(right.periodKey)
                );
        }
    );
}

function buildCombinedColorIndexes(
    series,
    groupMode
) {
    const result =
        new Map();

    (series || []).forEach(item => {
        const identity =
            getCombinedColorIdentity(
                item,
                groupMode
            );

        if (!result.has(identity)) {
            result.set(
                identity,
                result.size
            );
        }
    });

    return result;
}

function getCombinedColorIdentity(
    item,
    groupMode
) {
    /*
     * Al agrupar por dispositivo, la misma variable
     * conserva el mismo color en todos los equipos.
     */
    if (groupMode === 'BY_ENTITY') {
        return `variable:${item?.key ||
            item?.label ||
            'unknown'
            }`;
    }

    /*
     * Al agrupar por variable, cada dispositivo
     * conserva el mismo color.
     */
    if (groupMode === 'BY_VARIABLE') {
        return `entity:${item?.entityId ||
            item?.entityName ||
            'unknown'
            }`;
    }

    return `${item?.entityId ||
        item?.entityName ||
        'entity'
        }:${item?.key ||
        item?.label ||
        'variable'
        }`;
}

function resolveCombinedChartTitle(
    unit,
    combinedConfig
) {
    const series =
        unit?.series || [];

    const titleMode =
        combinedConfig?.titleMode ||
        'AUTO';

    if (
        titleMode === 'CUSTOM' &&
        combinedConfig?.customTitle
    ) {
        return combinedConfig.customTitle;
    }

    const entityNames =
        uniqueTextValues(
            series.map(
                item => item?.entityName
            )
        );

    const variableNames =
        uniqueTextValues(
            series.map(
                item =>
                    item?.label ||
                    item?.key
            )
        );

    if (titleMode === 'ENTITY_NAME') {
        return entityNames.length === 1
            ? entityNames[0]
            : 'Comparativa de equipos';
    }

    if (titleMode === 'VARIABLE_NAME') {
        return variableNames.length === 1
            ? variableNames[0]
            : 'Comparativa de variables';
    }

    if (
        combinedConfig?.groupMode ===
        'BY_ENTITY'
    ) {
        return (
            unit?.groupLabel ||
            entityNames[0] ||
            'Dispositivo'
        );
    }

    if (
        combinedConfig?.groupMode ===
        'BY_VARIABLE'
    ) {
        return (
            unit?.groupLabel ||
            variableNames[0] ||
            'Variable'
        );
    }

    if (entityNames.length === 1) {
        return entityNames[0];
    }

    if (variableNames.length === 1) {
        return variableNames[0];
    }

    return 'Comparativa de equipos';
}

function resolveCombinedPeriodSubtitle(
    unit
) {
    if (
        !unit?.periodLabel ||
        unit?.granularity === 'FULL'
    ) {
        return null;
    }

    const labels = {
        DAY:
            'Periodo diario',

        WEEK:
            'Periodo semanal',

        MONTH:
            'Periodo mensual'
    };

    return [
        labels[unit.granularity] ||
        'Periodo',

        unit.periodLabel
    ]
        .filter(Boolean)
        .join(' · ');
}

function buildCombinedSeriesDisplayEntries(
    series,
    combinedConfig
) {
    const sourceSeries =
        Array.isArray(series)
            ? series
            : [];

    const entityNames =
        uniqueTextValues(
            sourceSeries.map(
                item => item?.entityName
            )
        );

    const variableNames =
        uniqueTextValues(
            sourceSeries.map(
                item =>
                    item?.label ||
                    item?.key
            )
        );

    const allSameEntity =
        entityNames.length <= 1;

    const allSameVariable =
        variableNames.length <= 1;

    return sourceSeries.map(
        (item, index) => {
            const variableLabel =
                item?.label ||
                item?.key ||
                `Serie ${index + 1}`;

            const entityLabel =
                item?.entityName ||
                'Dispositivo';

            let contextualName;

            if (allSameEntity) {
                contextualName =
                    variableLabel;
            } else if (allSameVariable) {
                contextualName =
                    entityLabel;
            } else {
                contextualName =
                    `${variableLabel} · ${entityLabel}`;
            }

            const nameMode =
                combinedConfig
                    ?.seriesNameMode ||
                'AUTO';

            let displayName;

            if (
                nameMode ===
                'LABEL_ONLY'
            ) {
                displayName =
                    variableLabel;
            } else if (
                nameMode ===
                'LABEL_AND_ENTITY'
            ) {
                displayName =
                    `${variableLabel} · ${entityLabel}`;
            } else if (
                nameMode ===
                'NUMBERED'
            ) {
                displayName =
                    `S${index + 1} · ${contextualName}`;
            } else {
                displayName =
                    contextualName;
            }

            return {
                item,

                index,

                shortId:
                    `S${index + 1}`,

                displayName
            };
        }
    );
}

function uniqueTextValues(values) {
    return Array.from(
        new Set(
            (values || [])
                .map(value =>
                    String(
                        value || ''
                    ).trim()
                )
                .filter(Boolean)
        )
    );
}

function prepareCombinedSeriesForDrawing(
    series,
    combinedConfig
) {
    const intervalMs =
        resolveCombinedDrawingInterval(
            series,
            combinedConfig
        );

    const aggregation =
        combinedConfig
            ?.bucketAggregation ||
        'AVG';

    return (series || []).map(item => {
        const points =
            normalizeChartPoints(
                item?.points
            );

        const reducedPoints =
            intervalMs > 0
                ? bucketCombinedChartPoints(
                    points,
                    intervalMs,
                    aggregation
                )
                : points;

        return {
            ...item,

            points:
                downsampleCombinedChartPoints(
                    reducedPoints,
                    1200
                )
        };
    });
}

function resolveCombinedDrawingInterval(
    series,
    combinedConfig
) {
    const dataInterval =
        combinedConfig
            ?.dataInterval ||
        'AUTO';

    if (dataInterval === 'RAW') {
        return 0;
    }

    if (
        dataInterval ===
        'FIFTEEN_MINUTES'
    ) {
        return 15 * 60 * 1000;
    }

    if (
        dataInterval ===
        'THIRTY_MINUTES'
    ) {
        return 30 * 60 * 1000;
    }

    if (dataInterval === 'HOUR') {
        return 60 * 60 * 1000;
    }

    if (dataInterval === 'CUSTOM') {
        return (
            clampNumber(
                combinedConfig
                    ?.customIntervalMinutes,
                1,
                10080,
                60
            ) *
            60 *
            1000
        );
    }

    const normalizedSeries =
        (series || [])
            .map(item =>
                normalizeChartPoints(
                    item?.points
                )
            )
            .filter(points =>
                points.length
            );

    if (!normalizedSeries.length) {
        return 0;
    }

    const largestPointCount =
        Math.max(
            ...normalizedSeries.map(
                points => points.length
            )
        );

    if (largestPointCount <= 700) {
        return 0;
    }

    const allFirstTimestamps =
        normalizedSeries.map(
            points => points[0].ts
        );

    const allLastTimestamps =
        normalizedSeries.map(
            points =>
                points[
                    points.length - 1
                ].ts
        );

    const minimumTimestamp =
        Math.min(
            ...allFirstTimestamps
        );

    const maximumTimestamp =
        Math.max(
            ...allLastTimestamps
        );

    const duration =
        Math.max(
            1,
            maximumTimestamp -
            minimumTimestamp
        );

    const desiredInterval =
        duration / 600;

    const friendlyIntervals = [
        60 * 1000,
        5 * 60 * 1000,
        15 * 60 * 1000,
        30 * 60 * 1000,
        60 * 60 * 1000,
        2 * 60 * 60 * 1000,
        4 * 60 * 60 * 1000,
        6 * 60 * 60 * 1000,
        12 * 60 * 60 * 1000,
        24 * 60 * 60 * 1000
    ];

    return (
        friendlyIntervals.find(
            interval =>
                interval >=
                desiredInterval
        ) ||
        friendlyIntervals[
        friendlyIntervals.length - 1
        ]
    );
}

function normalizeChartPoints(points) {
    return (points || [])
        .filter(point =>
            point &&
            Number.isFinite(
                Number(point.ts)
            ) &&
            Number.isFinite(
                Number(point.value)
            )
        )
        .map(point => ({
            ts:
                Number(point.ts),

            value:
                Number(point.value)
        }))
        .sort(
            (left, right) =>
                left.ts - right.ts
        );
}

function bucketCombinedChartPoints(
    points,
    intervalMs,
    aggregation
) {
    if (
        !Array.isArray(points) ||
        !points.length ||
        !Number.isFinite(intervalMs) ||
        intervalMs <= 0
    ) {
        return points || [];
    }

    const buckets =
        new Map();

    points.forEach(point => {
        const bucketStart =
            Math.floor(
                point.ts /
                intervalMs
            ) *
            intervalMs;

        if (!buckets.has(bucketStart)) {
            buckets.set(
                bucketStart,
                {
                    bucketStart,

                    first:
                        point,

                    last:
                        point,

                    min:
                        point,

                    max:
                        point,

                    sum:
                        0,

                    count:
                        0
                }
            );
        }

        const bucket =
            buckets.get(bucketStart);

        bucket.last =
            point;

        if (
            point.value <
            bucket.min.value
        ) {
            bucket.min =
                point;
        }

        if (
            point.value >
            bucket.max.value
        ) {
            bucket.max =
                point;
        }

        bucket.sum +=
            point.value;

        bucket.count++;
    });

    return Array.from(
        buckets.values()
    )
        .sort(
            (left, right) =>
                left.bucketStart -
                right.bucketStart
        )
        .map(bucket =>
            aggregateCombinedChartBucket(
                bucket,
                intervalMs,
                aggregation
            )
        );
}

function aggregateCombinedChartBucket(
    bucket,
    intervalMs,
    aggregation
) {
    if (aggregation === 'MIN') {
        return {
            ts:
                bucket.min.ts,

            value:
                bucket.min.value
        };
    }

    if (aggregation === 'MAX') {
        return {
            ts:
                bucket.max.ts,

            value:
                bucket.max.value
        };
    }

    if (aggregation === 'FIRST') {
        return {
            ts:
                bucket.first.ts,

            value:
                bucket.first.value
        };
    }

    if (aggregation === 'LAST') {
        return {
            ts:
                bucket.last.ts,

            value:
                bucket.last.value
        };
    }

    const timestamp =
        bucket.bucketStart +
        intervalMs / 2;

    if (aggregation === 'SUM') {
        return {
            ts:
                timestamp,

            value:
                bucket.sum
        };
    }

    return {
        ts:
            timestamp,

        value:
            bucket.count > 0
                ? bucket.sum /
                bucket.count
                : 0
    };
}

function downsampleCombinedChartPoints(
    points,
    maximumPoints
) {
    const source =
        Array.isArray(points)
            ? points
            : [];

    if (
        source.length <=
        maximumPoints
    ) {
        return source;
    }

    const result = [
        source[0]
    ];

    const interior =
        source.slice(
            1,
            source.length - 1
        );

    const bucketCount =
        Math.max(
            1,
            Math.floor(
                (maximumPoints - 2) / 2
            )
        );

    const bucketSize =
        interior.length /
        bucketCount;

    for (
        let bucketIndex = 0;
        bucketIndex < bucketCount;
        bucketIndex++
    ) {
        const startIndex =
            Math.floor(
                bucketIndex *
                bucketSize
            );

        const endIndex =
            Math.min(
                interior.length,
                Math.floor(
                    (bucketIndex + 1) *
                    bucketSize
                )
            );

        const bucket =
            interior.slice(
                startIndex,
                Math.max(
                    startIndex + 1,
                    endIndex
                )
            );

        if (!bucket.length) {
            continue;
        }

        let minimumPoint =
            bucket[0];

        let maximumPoint =
            bucket[0];

        bucket.forEach(point => {
            if (
                point.value <
                minimumPoint.value
            ) {
                minimumPoint =
                    point;
            }

            if (
                point.value >
                maximumPoint.value
            ) {
                maximumPoint =
                    point;
            }
        });

        if (
            minimumPoint.ts <=
            maximumPoint.ts
        ) {
            result.push(
                minimumPoint
            );

            if (
                maximumPoint !==
                minimumPoint
            ) {
                result.push(
                    maximumPoint
                );
            }
        } else {
            result.push(
                maximumPoint
            );

            if (
                maximumPoint !==
                minimumPoint
            ) {
                result.push(
                    minimumPoint
                );
            }
        }
    }

    result.push(
        source[
        source.length - 1
        ]
    );

    return result
        .filter(
            (point, index, values) =>
                index === 0 ||
                point.ts !==
                values[index - 1].ts ||
                point.value !==
                values[index - 1].value
        )
        .sort(
            (left, right) =>
                left.ts - right.ts
        );
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
function renderCombinedChartsPage(
    doc,
    payload,
    unit,
    combinedConfig
) {
    const validSeries =
        (unit?.series || [])
            .filter(item =>
                (item.points || [])
                    .some(point =>
                        point &&
                        Number.isFinite(
                            Number(point.value)
                        ) &&
                        Number.isFinite(
                            Number(point.ts)
                        )
                    )
            );

    if (!validSeries.length) {
        return;
    }

    ensureSpace(
        doc,
        combinedConfig.tableMode === 'NONE'
            ? 285
            : 390
    );

    const title =
        resolveCombinedChartTitle(
            unit,
            combinedConfig
        );

    const periodSubtitle =
        resolveCombinedPeriodSubtitle(
            unit
        );

    doc.font('Helvetica-Bold')
        .fontSize(12)
        .fillColor('#111111')
        .text(
            title,
            doc.page.margins.left,
            doc.y,
            {
                width:
                    doc.page.width -
                    doc.page.margins.left -
                    doc.page.margins.right,

                height:
                    30,

                ellipsis:
                    true
            }
        );

    doc.font('Helvetica');

    if (periodSubtitle) {
        doc.moveDown(0.2);

        doc.fontSize(8)
            .fillColor('#5F6B76')
            .text(
                periodSubtitle,
                doc.page.margins.left,
                doc.y,
                {
                    width:
                        doc.page.width -
                        doc.page.margins.left -
                        doc.page.margins.right
                }
            );
    }

    doc.moveDown(0.55);

    if (validSeries.length > 1) {
        doc.fontSize(8)
            .fillColor('#666666')
            .text(
                'Las series se normalizan para comparar su comportamiento relativo aun cuando utilizan unidades diferentes.',
                doc.page.margins.left,
                doc.y,
                {
                    width:
                        doc.page.width -
                        doc.page.margins.left -
                        doc.page.margins.right,

                    lineGap:
                        2
                }
            );

        doc.moveDown(0.7);
    }

    const chartBox = {
        x:
            doc.page.margins.left,

        y:
            doc.y,

        width:
            doc.page.width -
            doc.page.margins.left -
            doc.page.margins.right,

        height:
            190
    };

    const drawingSeries =
        prepareCombinedSeriesForDrawing(
            validSeries,
            combinedConfig
        );

    drawNormalizedCombinedLineChart(
        doc,
        drawingSeries,
        chartBox,
        getPayloadTimezone(payload)
    );

    doc.y =
        chartBox.y +
        chartBox.height +
        20;

    resetCursor(doc);

    const effectiveLegendMode =
        resolveEffectiveLegendMode(
            combinedConfig,
            validSeries
        );

    if (
        effectiveLegendMode !==
        'NONE'
    ) {
        renderCombinedLegend(
            doc,
            validSeries,
            combinedConfig
        );

        normalizePdfState(doc);

        doc.moveDown(0.6);
    }

    if (
        combinedConfig.tableMode !==
        'NONE'
    ) {
        renderCombinedSeriesStatsTable(
            doc,
            validSeries,
            combinedConfig
        );

        normalizePdfState(doc);
    }

    doc.moveDown(1);
    normalizePdfState(doc);
}

function resolveEffectiveLegendMode(
    combinedConfig,
    series
) {
    const configuredMode =
        combinedConfig?.legendMode ||
        'AUTO';

    if (configuredMode === 'NONE') {
        return 'NONE';
    }

    if (
        configuredMode ===
        'AUTO'
    ) {
        return (series || []).length > 1
            ? 'PER_CHART'
            : 'NONE';
    }

    /*
     * SHARED se tratará como leyenda por gráfica
     * hasta implementar varias gráficas por página.
     */
    return 'PER_CHART';
}

function drawNormalizedCombinedLineChart(
    doc,
    series,
    box,
    timezone = 'UTC'
) {
    const colors = getChartColors(doc);

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
    doc.text(formatShortDate(minTs, timezone), box.x, box.y + box.height + 4, {
        width: 140,
        lineBreak: false
    });

    doc.text(formatShortDate(maxTs, timezone), box.x + box.width - 140, box.y + box.height + 4, {
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

        const colorIndex =
            Number.isInteger(
                entry.item?.__colorIndex
            )
                ? entry.item.__colorIndex
                : index;

        doc.strokeColor(
            colors[
            colorIndex %
            colors.length
            ]
        )
            .lineWidth(1.4)
            .stroke();
    });

    doc.strokeColor('#000000')
        .lineWidth(1);
}

function renderCombinedLegend(
    doc,
    series,
    combinedConfig = {}
) {
    const colors =
        getChartColors(doc);

    const entries =
        buildCombinedSeriesDisplayEntries(
            series,
            combinedConfig
        );

    const startX =
        doc.page.margins.left;

    const maximumX =
        doc.page.width -
        doc.page.margins.right;

    let x =
        startX;

    let y =
        doc.y;

    doc.font('Helvetica')
        .fontSize(7);

    entries.forEach(entry => {
        const label =
            safeText(
                entry.displayName,
                72
            );

        const measuredWidth =
            doc.widthOfString(label);

        const itemWidth =
            Math.max(
                105,
                Math.min(
                    235,
                    measuredWidth + 28
                )
            );

        if (
            x + itemWidth >
            maximumX
        ) {
            x =
                startX;

            y +=
                18;
        }

        const colorIndex =
            Number.isInteger(
                entry.item?.__colorIndex
            )
                ? entry.item.__colorIndex
                : entry.index;

        doc.rect(
            x,
            y + 3,
            8,
            8
        )
            .fill(
                colors[
                colorIndex %
                colors.length
                ]
            );

        doc.fillColor('#333333')
            .fontSize(7)
            .text(
                label,
                x + 12,
                y,
                {
                    width:
                        itemWidth - 14,

                    height:
                        14,

                    ellipsis:
                        true,

                    lineBreak:
                        false
                }
            );

        x +=
            itemWidth;
    });

    doc.y =
        y + 22;

    resetCursor(doc);
}

function renderCombinedSeriesStatsTable(
    doc,
    series,
    combinedConfig = {},
    tableOptions = {}
) {
    const configuredStats =
        combinedConfig?.stats || {};

    const statsConfig = {
        count:
            configuredStats.count !== false,

        min:
            configuredStats.min !== false,

        max:
            configuredStats.max !== false,

        avg:
            configuredStats.avg !== false,

        sum:
            configuredStats.sum === true,

        first:
            configuredStats.first === true,

        last:
            configuredStats.last === true,

        delta:
            configuredStats.delta === true
    };

    const columns = [
        {
            key:
                'series',

            label:
                'Serie',

            align:
                'left'
        },

        {
            key:
                'unit',

            label:
                'Unidad',

            align:
                'left'
        }
    ];

    if (statsConfig.count) {
        columns.push({
            key:
                'samples',

            label:
                'Muestras',

            align:
                'right'
        });
    }

    if (statsConfig.min) {
        columns.push({
            key:
                'min',

            label:
                'Mínimo',

            align:
                'right'
        });
    }

    if (statsConfig.max) {
        columns.push({
            key:
                'max',

            label:
                'Máximo',

            align:
                'right'
        });
    }

    if (statsConfig.avg) {
        columns.push({
            key:
                'avg',

            label:
                'Promedio',

            align:
                'right'
        });
    }

    if (statsConfig.sum) {
        columns.push({
            key:
                'sum',

            label:
                'Suma',

            align:
                'right'
        });
    }

    if (statsConfig.first) {
        columns.push({
            key:
                'first',

            label:
                'Primero',

            align:
                'right'
        });
    }

    if (statsConfig.last) {
        columns.push({
            key:
                'last',

            label:
                'Último',

            align:
                'right'
        });
    }

    if (statsConfig.delta) {
        columns.push({
            key:
                'delta',

            label:
                'Diferencia',

            align:
                'right'
        });
    }

    const displayEntries =
        buildCombinedSeriesDisplayEntries(
            series,
            combinedConfig
        );

    const rows = [];

    displayEntries.forEach(entry => {
        const stats =
            calculateExtendedStats(
                entry.item?.points || []
            );

        if (!stats) {
            return;
        }

        rows.push({
            series:
                entry.displayName,

            unit:
                entry.item?.unit ||
                '-',

            samples:
                stats.count,

            min:
                formatNumber(
                    stats.min
                ),

            max:
                formatNumber(
                    stats.max
                ),

            avg:
                formatNumber(
                    stats.avg
                ),

            sum:
                formatNumber(
                    stats.sum
                ),

            first:
                formatNumber(
                    stats.first
                ),

            last:
                formatNumber(
                    stats.last
                ),

            delta:
                formatNumber(
                    stats.delta
                )
        });
    });

    if (!rows.length) {
        return;
    }

    renderFlexibleCombinedStatsTable(
        doc,
        columns,
        rows,
        tableOptions
    );
}

function renderFlexibleCombinedStatsTable(
    doc,
    columns,
    rows,
    options = {}
) {
    const compact =
        options.compact === true;

    const allowPageBreak =
        options.allowPageBreak !== false;

    const startX =
        Number.isFinite(
            Number(options.startX)
        )
            ? Number(options.startX)
            : doc.page.margins.left;

    const tableWidth =
        Number.isFinite(
            Number(options.width)
        )
            ? Number(options.width)
            : doc.page.width -
            doc.page.margins.left -
            doc.page.margins.right;

    const headerHeight =
        compact
            ? 15
            : 18;

    const columnWeights =
        columns.map(column => {
            if (column.key === 'series') {
                return compact
                    ? 2.75
                    : 2.45;
            }

            if (column.key === 'unit') {
                return 0.72;
            }

            if (column.key === 'samples') {
                return 0.82;
            }

            if (column.key === 'sum') {
                return 1.12;
            }

            return 0.9;
        });

    const totalWeight =
        columnWeights.reduce(
            (sum, weight) =>
                sum + weight,
            0
        );

    const columnWidths =
        columnWeights.map(
            weight =>
                tableWidth *
                weight /
                totalWeight
        );

    const fontSize =
        compact
            ? columns.length >= 9
                ? 5.1
                : 5.8
            : columns.length >= 9
                ? 5.8
                : 6.4;

    const verticalPadding =
        compact
            ? 3
            : 5;

    const drawHeader =
        y => {
            doc.rect(
                startX,
                y,
                tableWidth,
                headerHeight
            )
                .fill('#EAF1F8');

            let x =
                startX;

            columns.forEach(
                (column, index) => {
                    doc.fillColor('#0B2239')
                        .font('Helvetica-Bold')
                        .fontSize(fontSize)
                        .text(
                            column.label,
                            x + 3,
                            y +
                            (
                                compact
                                    ? 4
                                    : 6
                            ),
                            {
                                width:
                                    columnWidths[index] -
                                    6,

                                align:
                                    column.align ||
                                    'left',

                                lineBreak:
                                    false,

                                ellipsis:
                                    true
                            }
                        );

                    x +=
                        columnWidths[index];
                }
            );

            doc.font('Helvetica');

            return y + headerHeight;
        };

    /*
     * El comportamiento anterior se conserva para las
     * tablas detalladas. Las tablas compactas ya fueron
     * posicionadas por el algoritmo de densidad.
     */
    if (allowPageBreak) {
        ensureSpace(
            doc,
            headerHeight + 42
        );
    }

    let y =
        drawHeader(
            doc.y
        );

    rows.forEach(
        (row, rowIndex) => {
            const seriesColumnIndex =
                columns.findIndex(
                    column =>
                        column.key ===
                        'series'
                );

            const seriesWidth =
                seriesColumnIndex >= 0
                    ? columnWidths[
                    seriesColumnIndex
                    ]
                    : 100;

            const seriesText =
                String(
                    row.series || '-'
                );

            const measuredHeight =
                doc.font('Helvetica')
                    .fontSize(fontSize)
                    .heightOfString(
                        seriesText,
                        {
                            width:
                                seriesWidth - 8,

                            lineGap:
                                0
                        }
                    );

            const minimumRowHeight =
                compact
                    ? 14
                    : 18;

            const maximumRowHeight =
                compact
                    ? 22
                    : 32;

            const rowHeight =
                Math.max(
                    minimumRowHeight,
                    Math.min(
                        maximumRowHeight,
                        measuredHeight +
                        (
                            compact
                                ? 5
                                : 8
                        )
                    )
                );

            if (
                allowPageBreak &&
                y + rowHeight >
                doc.page.height -
                doc.page.margins.bottom
            ) {
                doc.addPage();

                y =
                    drawHeader(
                        doc.y
                    );
            }

            doc.rect(
                startX,
                y,
                tableWidth,
                rowHeight
            )
                .fill(
                    rowIndex % 2 === 0
                        ? '#FFFFFF'
                        : '#F7FAFC'
                );

            let x =
                startX;

            columns.forEach(
                (
                    column,
                    columnIndex
                ) => {
                    const rawValue =
                        row[column.key] !==
                            undefined &&
                            row[column.key] !==
                            null
                            ? row[column.key]
                            : '-';

                    const isSeriesColumn =
                        column.key ===
                        'series';

                    doc.fillColor('#222222')
                        .font('Helvetica')
                        .fontSize(fontSize)
                        .text(
                            String(rawValue),
                            x + 3,
                            y + verticalPadding,
                            {
                                width:
                                    columnWidths[
                                    columnIndex
                                    ] - 6,

                                height:
                                    rowHeight -
                                    verticalPadding -
                                    2,

                                align:
                                    column.align ||
                                    'left',

                                lineBreak:
                                    isSeriesColumn,

                                ellipsis:
                                    true
                            }
                        );

                    x +=
                        columnWidths[
                        columnIndex
                        ];
                }
            );

            y +=
                rowHeight;
        }
    );

    doc.y =
        y +
        (
            compact
                ? 6
                : 10
        );

    resetCursor(doc);
}

function calculateExtendedStats(points) {
    const valid = (points || [])
        .filter(point =>
            point &&
            Number.isFinite(Number(point.value)) &&
            Number.isFinite(Number(point.ts))
        )
        .map(point => ({
            ts: Number(point.ts),
            value: Number(point.value)
        }))
        .sort((a, b) => a.ts - b.ts);

    if (!valid.length) {
        return null;
    }

    const values = valid.map(point => point.value);
    const sum = values.reduce((total, value) => total + value, 0);

    return {
        count: valid.length,
        min: Math.min(...values),
        max: Math.max(...values),
        avg: sum / valid.length,
        sum,
        first: valid[0].value,
        last: valid[valid.length - 1].value,
        delta: valid[valid.length - 1].value - valid[0].value
    };
}

function getChartColors(doc) {
    const theme = getTheme(doc);

    const colors = [
        theme.primaryColor,
        theme.secondaryColor,
        '#3656B0',
        '#4CAF50',
        '#FF9800',
        '#9C27B0',
        '#F44336',
        '#607D8B'
    ];

    /*
     * Evita colores repetidos cuando el usuario
     * elige colores iguales o similares.
     */
    return Array.from(
        new Set(colors)
    );
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

function drawLineChart(doc, points, box, unit, timezone) {
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
    const unitSuffix = unit ? ` ${unit}` : '';
    doc.text(`Máx: ${formatNumber(max)}${unitSuffix}`, box.x, box.y - 12, { lineBreak: false });
    doc.text(`Mín: ${formatNumber(min)}${unitSuffix}`, box.x + 125, box.y - 12, { lineBreak: false });

    doc.fontSize(7).fillColor('#666666');
    doc.text(formatShortDate(minTs, timezone), box.x, box.y + box.height + 4, {
        width: 140,
        lineBreak: false
    });

    doc.text(formatShortDate(maxTs, timezone), box.x + box.width - 140, box.y + box.height + 4, {
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

    doc.strokeColor(getTheme(doc).primaryColor)
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

    extractVariableConfigs(payload).forEach(variable => {
        const key = String(variable?.key || '').trim();
        const label = String(variable?.label || '').trim();

        if (key && label && key !== label) {
            result = result.replaceAll(key, label);
        }
    });

    return result;
}

function renderAdvancedAnalysisSummary(
    doc,
    payload
) {
    const analysis =
        getAdvancedAnalysis(payload);

    const results =
        analysis?.results || [];

    if (!results.length) {
        return;
    }

    sectionTitle(
        doc,
        'Análisis avanzado'
    );

    results.forEach(result => {
        const lines =
            buildAnalysisDetailLines(result);

        const cardHeight =
            62 +
            lines.length * 15;

        ensureSpace(
            doc,
            cardHeight + 14
        );

        normalizePdfState(doc);

        const left =
            doc.page.margins.left;

        const width =
            doc.page.width -
            doc.page.margins.left -
            doc.page.margins.right;

        const y = doc.y;

        const statusColor =
            getAnalysisStatusColor(
                result.status
            );

        doc.roundedRect(
            left,
            y,
            width,
            cardHeight,
            7
        )
            .fillAndStroke(
                '#F8FAFC',
                '#D9E3EB'
            );

        doc.fontSize(11)
            .fillColor('#17212B')
            .text(
                `${result.label} · ${result.entityName}`,
                left + 12,
                y + 12,
                {
                    width: width - 150,
                    lineBreak: false
                }
            );

        doc.rect(
            left,
            y,
            4,
            cardHeight
        ).fill(statusColor);

        doc.fontSize(8)
            .fillColor(statusColor)
            .text(
                `Estado: ${getAnalysisStatusLabel(result.status)}`,
                left + width - 150,
                y + 14,
                {
                    width: 136,
                    align: 'right',
                    lineBreak: false
                }
            );

        let lineY = y + 43;

        lines.forEach(line => {
            doc.fontSize(8.2)
                .fillColor('#45515D')
                .text(
                    `• ${line}`,
                    left + 14,
                    lineY,
                    {
                        width: width - 28,
                        lineBreak: false
                    }
                );

            lineY += 15;
        });

        doc.y =
            y +
            cardHeight +
            14;

        normalizePdfState(doc);
    });
}

function buildAnalysisDetailLines(result) {
    if (result.status === 'NO_DATA') {
        return [
            'No se encontraron muestras durante el periodo seleccionado.'
        ];
    }

    const unit =
        result.unit
            ? ` ${result.unit}`
            : '';

    const lines = [
        `Promedio ${formatNumber(result.stats.avg)}${unit}; mínimo ${formatNumber(result.stats.min)}${unit}; máximo ${formatNumber(result.stats.max)}${unit}.`,

        `Cobertura estimada ${formatNumber(result.coverage.pct)} %; mínimo configurado ${formatNumber(result.minimumCoverage)} %.`
    ];

    if (result.expectedRange) {
        lines.push(
            `Cumplimiento del rango esperado: ${formatNumber(result.expectedRange.insidePct)} % de las muestras.`
        );
    }

    if (result.warningRange) {
        lines.push(
            `Muestras fuera del rango de advertencia: ${result.warningRange.outsideCount}.`
        );
    }

    if (result.trend) {
        lines.push(
            `Tendencia: ${getTrendLabel(result.trend.direction)}.`
        );
    }

    if (result.outliers) {
        lines.push(
            `Valores atípicos detectados: ${result.outliers.count}.`
        );
    }

    if (
        result.previousComparison &&
        result.previousComparison.changePct !== null
    ) {
        lines.push(
            `Cambio del promedio frente al periodo anterior: ${formatSignedPercent(result.previousComparison.changePct)}.`
        );
    }

    return lines;
}

function getAnalysisStatusLabel(status) {
    const labels = {
        OK: 'Aceptable',
        ATTENTION: 'Requiere revisión',
        CRITICAL: 'Condición crítica',
        NO_DATA: 'Sin datos'
    };

    return labels[status] || 'Sin clasificar';
}

function getAnalysisStatusColor(status) {
    const colors = {
        OK: '#2E7D32',
        ATTENTION: '#ED8B00',
        CRITICAL: '#C62828',
        NO_DATA: '#607D8B'
    };

    return colors[status] || '#607D8B';
}

function getTrendLabel(direction) {
    const labels = {
        RISING: 'ascendente',
        FALLING: 'descendente',
        STABLE: 'estable'
    };

    return labels[direction] || 'no determinada';
}

function formatSignedPercent(value) {
    const number =
        Number(value);

    if (!Number.isFinite(number)) {
        return '-';
    }

    const prefix =
        number > 0
            ? '+'
            : '';

    return `${prefix}${number.toFixed(2)} %`;
}

function renderObservations(doc, payload) {
    const suppliedObservations =
        payload?.summary?.observations ||
        payload?.data?.observations ||
        [];

    const suppliedTexts =
        suppliedObservations
            .map(observation =>
                typeof observation === 'string'
                    ? observation
                    : observation?.text
            )
            .filter(Boolean);

    const advancedAnalysis =
        getAdvancedAnalysis(payload);

    const observations =
        getCombinedObservations(payload);

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
            .fillColor(getTheme(doc).primaryColor)
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

function getCombinedObservations(payload) {
    const supplied =
        payload?.summary?.observations ||
        payload?.data?.observations ||
        [];

    const suppliedTexts =
        supplied
            .map(observation =>
                typeof observation === 'string'
                    ? observation
                    : observation?.text
            )
            .filter(Boolean);

    const advanced =
        getAdvancedAnalysis(payload);

    return deduplicateTexts([
        ...suppliedTexts,
        ...(advanced?.observations || [])
    ]);
}

function getAdvancedAnalysis(payload) {
    if (payload?._advancedAnalysisResult) {
        return payload._advancedAnalysisResult;
    }

    const results = [];
    const observations = [];
    const series =
        payload?.data?.timeSeries || [];

    series.forEach(item => {
        const variable =
            findVariableConfigForSeries(
                payload,
                item
            );

        const config =
            variable?.analysis;

        if (!config || config.enabled !== true) {
            return;
        }

        const result =
            analyzeConfiguredSeries(
                payload,
                item,
                variable,
                config
            );

        results.push(result);

        const observation =
            formatAdvancedObservation(result);

        if (observation) {
            observations.push(observation);
        }
    });

    const analysisResult = {
        results,
        observations,
        conclusion:
            buildAdvancedConclusion(results)
    };

    payload._advancedAnalysisResult =
        analysisResult;

    return analysisResult;
}

function analyzeConfiguredSeries(
    payload,
    item,
    variable,
    config
) {
    const points =
        normalizeAnalysisPoints(item?.points);

    const label =
        variable?.label ||
        item?.label ||
        item?.key ||
        'Variable';

    const entityName =
        variable?.entityName ||
        item?.entityName ||
        'Entidad';

    const unit =
        variable?.unit ||
        item?.unit ||
        '';

    const minimumCoverage =
        clampNumber(
            config.minimumCoveragePct,
            0,
            100,
            80
        );

    if (!points.length) {
        return {
            label,
            entityName,
            unit,
            status: 'NO_DATA',
            points: 0,
            minimumCoverage
        };
    }

    const stats =
        calculateStats(points);

    const coverage =
        estimateSeriesCoverage(
            points,
            payload?.period
        );

    const expectedRange =
        config.expectedRange?.enabled === true
            ? evaluateRange(
                points,
                config.expectedRange
            )
            : null;

    const warningRange =
        config.warningRange?.enabled === true
            ? evaluateRange(
                points,
                config.warningRange
            )
            : null;

    const trend =
        config.detectTrend === true
            ? calculateSeriesTrend(points)
            : null;

    const outliers =
        config.detectOutliers === true
            ? calculateIqrOutliers(points)
            : null;

    const previousPoints =
        config.comparePreviousPeriod === true
            ? findPreviousSeriesPoints(
                payload,
                item
            )
            : [];

    const previousComparison =
        previousPoints.length
            ? compareSeriesAverages(
                points,
                previousPoints
            )
            : null;

    let status = 'OK';

    if (coverage.pct < minimumCoverage) {
        status = 'ATTENTION';
    }

    if (expectedRange) {
        if (expectedRange.insidePct < 80) {
            status = 'CRITICAL';
        } else if (
            expectedRange.insidePct < 95 &&
            status !== 'CRITICAL'
        ) {
            status = 'ATTENTION';
        }
    }

    if (
        warningRange &&
        warningRange.outsideCount > 0
    ) {
        status = 'CRITICAL';
    }

    if (
        outliers &&
        outliers.count >
        Math.max(
            3,
            Math.ceil(points.length * 0.05)
        ) &&
        status === 'OK'
    ) {
        status = 'ATTENTION';
    }

    return {
        label,
        entityName,
        unit,
        status,
        points: points.length,
        stats,
        coverage,
        minimumCoverage,
        expectedRange,
        warningRange,
        trend,
        outliers,
        previousComparison,
        performanceDirection:
            config.performanceDirection ||
            'TARGET_RANGE'
    };
}

function normalizeAnalysisPoints(points) {
    return (points || [])
        .filter(point =>
            point &&
            Number.isFinite(Number(point.ts)) &&
            Number.isFinite(Number(point.value))
        )
        .map(point => ({
            ts: Number(point.ts),
            value: Number(point.value)
        }))
        .sort((a, b) => a.ts - b.ts);
}

function evaluateRange(points, range) {
    const minimum =
        toFiniteNumber(range?.min);

    const maximum =
        toFiniteNumber(range?.max);

    if (minimum === null && maximum === null) {
        return null;
    }

    let insideCount = 0;

    points.forEach(point => {
        const aboveMinimum =
            minimum === null ||
            point.value >= minimum;

        const belowMaximum =
            maximum === null ||
            point.value <= maximum;

        if (aboveMinimum && belowMaximum) {
            insideCount++;
        }
    });

    const outsideCount =
        points.length - insideCount;

    return {
        minimum,
        maximum,
        insideCount,
        outsideCount,
        insidePct:
            points.length
                ? insideCount /
                points.length *
                100
                : 0
    };
}

function estimateSeriesCoverage(points, period) {
    if (!points.length) {
        return {
            pct: 0,
            expectedSamples: 0,
            actualSamples: 0,
            estimatedIntervalMs: null
        };
    }

    if (points.length === 1) {
        return {
            pct: 100,
            expectedSamples: 1,
            actualSamples: 1,
            estimatedIntervalMs: null
        };
    }

    const intervals = [];

    for (let index = 1; index < points.length; index++) {
        const delta =
            points[index].ts -
            points[index - 1].ts;

        if (delta > 0) {
            intervals.push(delta);
        }
    }

    const estimatedInterval =
        median(intervals);

    if (
        !Number.isFinite(estimatedInterval) ||
        estimatedInterval <= 0
    ) {
        return {
            pct: 100,
            expectedSamples: points.length,
            actualSamples: points.length,
            estimatedIntervalMs: null
        };
    }

    const startTs =
        Number(period?.startTs) ||
        points[0].ts;

    const endTs =
        Number(period?.endTs) ||
        points[points.length - 1].ts;

    const duration =
        Math.max(0, endTs - startTs);

    const expectedSamples =
        Math.max(
            1,
            Math.floor(
                duration / estimatedInterval
            ) + 1
        );

    const pct =
        Math.min(
            100,
            points.length /
            expectedSamples *
            100
        );

    return {
        pct,
        expectedSamples,
        actualSamples: points.length,
        estimatedIntervalMs:
            estimatedInterval
    };
}

function calculateSeriesTrend(points) {
    if (points.length < 2) {
        return null;
    }

    const startTs = points[0].ts;

    const values = points.map(point => ({
        x: (point.ts - startTs) / 1000,
        y: point.value
    }));

    const count = values.length;

    const meanX =
        values.reduce(
            (sum, point) => sum + point.x,
            0
        ) / count;

    const meanY =
        values.reduce(
            (sum, point) => sum + point.y,
            0
        ) / count;

    let numerator = 0;
    let denominator = 0;

    values.forEach(point => {
        numerator +=
            (point.x - meanX) *
            (point.y - meanY);

        denominator +=
            Math.pow(point.x - meanX, 2);
    });

    const slope =
        denominator !== 0
            ? numerator / denominator
            : 0;

    const durationSeconds =
        values[values.length - 1].x;

    const projectedChange =
        slope * durationSeconds;

    const changePct =
        meanY !== 0
            ? projectedChange /
            Math.abs(meanY) *
            100
            : null;

    let direction = 'STABLE';

    if (
        changePct !== null &&
        changePct > 2
    ) {
        direction = 'RISING';
    } else if (
        changePct !== null &&
        changePct < -2
    ) {
        direction = 'FALLING';
    }

    return {
        direction,
        slopePerSecond: slope,
        projectedChange,
        changePct
    };
}

function calculateIqrOutliers(points) {
    if (points.length < 4) {
        return {
            count: 0,
            lowerLimit: null,
            upperLimit: null
        };
    }

    const values = points
        .map(point => point.value)
        .sort((a, b) => a - b);

    const q1 = quantile(values, 0.25);
    const q3 = quantile(values, 0.75);
    const iqr = q3 - q1;

    const lowerLimit =
        q1 - 1.5 * iqr;

    const upperLimit =
        q3 + 1.5 * iqr;

    const count =
        values.filter(value =>
            value < lowerLimit ||
            value > upperLimit
        ).length;

    return {
        count,
        lowerLimit,
        upperLimit
    };
}

function quantile(values, percentile) {
    if (!values.length) {
        return null;
    }

    const position =
        (values.length - 1) * percentile;

    const base =
        Math.floor(position);

    const remainder =
        position - base;

    if (values[base + 1] !== undefined) {
        return (
            values[base] +
            remainder *
            (
                values[base + 1] -
                values[base]
            )
        );
    }

    return values[base];
}

function median(values) {
    if (!values.length) {
        return null;
    }

    const sorted =
        [...values].sort((a, b) => a - b);

    const middle =
        Math.floor(sorted.length / 2);

    if (sorted.length % 2 === 0) {
        return (
            sorted[middle - 1] +
            sorted[middle]
        ) / 2;
    }

    return sorted[middle];
}

function findPreviousSeriesPoints(payload, item) {
    if (Array.isArray(item?.previousPoints)) {
        return normalizeAnalysisPoints(
            item.previousPoints
        );
    }

    const previousSeries =
        payload?.data?.previousTimeSeries || [];

    const match =
        previousSeries.find(previous =>
            String(previous?.key || '') ===
            String(item?.key || '') &&
            (
                !previous?.entityName ||
                !item?.entityName ||
                String(previous.entityName) ===
                String(item.entityName)
            )
        );

    return normalizeAnalysisPoints(
        match?.points
    );
}

function compareSeriesAverages(
    currentPoints,
    previousPoints
) {
    const currentAverage =
        currentPoints.reduce(
            (sum, point) =>
                sum + point.value,
            0
        ) / currentPoints.length;

    const previousAverage =
        previousPoints.reduce(
            (sum, point) =>
                sum + point.value,
            0
        ) / previousPoints.length;

    const difference =
        currentAverage -
        previousAverage;

    const changePct =
        previousAverage !== 0
            ? difference /
            Math.abs(previousAverage) *
            100
            : null;

    return {
        currentAverage,
        previousAverage,
        difference,
        changePct
    };
}

function formatAdvancedObservation(result) {
    if (result.status === 'NO_DATA') {
        return (
            `${result.label} no presentó datos ` +
            `para ${result.entityName} durante el periodo seleccionado.`
        );
    }

    const unit =
        result.unit
            ? ` ${result.unit}`
            : '';

    const parts = [
        `${result.label} en ${result.entityName}: ` +
        `promedio ${formatNumber(result.stats.avg)}${unit}, ` +
        `mínimo ${formatNumber(result.stats.min)}${unit} y ` +
        `máximo ${formatNumber(result.stats.max)}${unit}.`
    ];

    if (result.expectedRange) {
        parts.push(
            `${formatNumber(result.expectedRange.insidePct)} % ` +
            `de las muestras permaneció dentro del rango esperado.`
        );
    }

    if (
        result.warningRange &&
        result.warningRange.outsideCount > 0
    ) {
        parts.push(
            `${result.warningRange.outsideCount} muestra(s) ` +
            `quedaron fuera del rango de advertencia.`
        );
    }

    if (
        result.coverage.pct <
        result.minimumCoverage
    ) {
        parts.push(
            `La cobertura estimada fue de ` +
            `${formatNumber(result.coverage.pct)} %, ` +
            `por debajo del mínimo configurado de ` +
            `${formatNumber(result.minimumCoverage)} %.`
        );
    }

    if (result.trend) {
        const trendLabels = {
            RISING: 'ascendente',
            FALLING: 'descendente',
            STABLE: 'estable'
        };

        parts.push(
            `La tendencia calculada fue ` +
            `${trendLabels[result.trend.direction]}.`
        );
    }

    if (
        result.outliers &&
        result.outliers.count > 0
    ) {
        parts.push(
            `Se detectaron ` +
            `${result.outliers.count} valor(es) atípico(s).`
        );
    }

    if (
        result.previousComparison &&
        result.previousComparison.changePct !== null
    ) {
        parts.push(
            `El promedio cambió ` +
            `${formatNumber(result.previousComparison.changePct)} % ` +
            `respecto al periodo anterior.`
        );
    }

    return parts.join(' ');
}

function buildAdvancedConclusion(results) {
    if (!results || !results.length) {
        return '';
    }

    const validResults =
        results.filter(
            result =>
                result.status !== 'NO_DATA'
        );

    const noDataResults =
        results.filter(
            result =>
                result.status === 'NO_DATA'
        );

    const criticalResults =
        validResults.filter(
            result =>
                result.status === 'CRITICAL'
        );

    const attentionResults =
        validResults.filter(
            result =>
                result.status === 'ATTENTION'
        );

    const acceptableResults =
        validResults.filter(
            result =>
                result.status === 'OK'
        );

    const parts = [
        `Se evaluaron ${results.length} variable(s) mediante los criterios configurados de cobertura, rangos operativos, tendencia, valores atípicos y comparación temporal.`
    ];

    if (acceptableResults.length) {
        parts.push(
            `${acceptableResults.length} variable(s) presentaron un comportamiento aceptable durante el periodo.`
        );
    }

    if (attentionResults.length) {
        parts.push(
            `${attentionResults.length} variable(s) requieren revisión.`
        );
    }

    if (criticalResults.length) {
        parts.push(
            `${criticalResults.length} variable(s) presentaron condiciones críticas respecto a los límites configurados.`
        );
    }

    if (noDataResults.length) {
        parts.push(
            `${noDataResults.length} variable(s) no contaron con telemetría suficiente.`
        );
    }

    const priorityResults = [
        ...criticalResults,
        ...attentionResults
    ].slice(0, 3);

    priorityResults.forEach(result => {
        const findings = [];

        if (
            result.coverage &&
            result.coverage.pct <
            result.minimumCoverage
        ) {
            findings.push(
                `cobertura de ${formatNumber(result.coverage.pct)} %`
            );
        }

        if (
            result.expectedRange &&
            result.expectedRange.insidePct < 95
        ) {
            findings.push(
                `cumplimiento de rango de ${formatNumber(result.expectedRange.insidePct)} %`
            );
        }

        if (
            result.warningRange &&
            result.warningRange.outsideCount > 0
        ) {
            findings.push(
                `${result.warningRange.outsideCount} muestra(s) fuera del rango de advertencia`
            );
        }

        if (
            result.trend &&
            result.trend.direction !== 'STABLE'
        ) {
            findings.push(
                `tendencia ${getTrendLabel(result.trend.direction)}`
            );
        }

        if (
            result.outliers &&
            result.outliers.count > 0
        ) {
            findings.push(
                `${result.outliers.count} valor(es) atípico(s)`
            );
        }

        if (
            result.previousComparison &&
            result.previousComparison.changePct !== null
        ) {
            findings.push(
                `cambio de ${formatSignedPercent(result.previousComparison.changePct)} respecto al periodo anterior`
            );
        }

        if (findings.length) {
            parts.push(
                `${result.label} en ${result.entityName}: ${findings.join(', ')}.`
            );
        }
    });

    if (
        criticalResults.length +
        attentionResults.length >
        priorityResults.length
    ) {
        const remaining =
            criticalResults.length +
            attentionResults.length -
            priorityResults.length;

        parts.push(
            `Además, ${remaining} variable(s) adicionales presentan hallazgos que deben revisarse en la sección de análisis avanzado.`
        );
    }

    parts.push(
        'Las clasificaciones se basan exclusivamente en los datos y umbrales configurados; una desviación no determina por sí sola la causa técnica y debe correlacionarse con la operación y el mantenimiento del sistema.'
    );

    return parts.join(' ');
}

function toFiniteNumber(value) {
    if (
        value === null ||
        value === undefined ||
        value === ''
    ) {
        return null;
    }

    const number = Number(value);

    return Number.isFinite(number)
        ? number
        : null;
}

function clampNumber(
    value,
    minimum,
    maximum,
    fallback
) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return fallback;
    }

    return Math.min(
        maximum,
        Math.max(minimum, number)
    );
}

function deduplicateTexts(values) {
    const result = [];
    const seen = new Set();

    values.forEach(value => {
        const text =
            String(value || '').trim();

        if (!text || seen.has(text)) {
            return;
        }

        seen.add(text);
        result.push(text);
    });

    return result;
}

function renderConclusion(doc, payload) {
    const advancedAnalysis =
        getAdvancedAnalysis(payload);

    const suppliedConclusion =
        payload?.summary?.conclusion ||
        payload?.data?.conclusion ||
        '';

    /*
     * Se conservan tanto la conclusión proporcionada
     * por el backend como la conclusión avanzada.
     */
    const conclusionParts =
        deduplicateTexts([
            suppliedConclusion,
            advancedAnalysis?.conclusion
        ]);

    const conclusion =
        conclusionParts.length
            ? conclusionParts.join(' ')
            : 'El reporte concentra las variables seleccionadas para facilitar la revisión operativa, identificar desviaciones y respaldar decisiones de mantenimiento, eficiencia y control.';

    const text =
        cleanObservation(
            conclusion,
            payload
        );

    if (!text) {
        return;
    }

    normalizePdfState(doc);
    ensureSpace(doc, 170);
    normalizePdfState(doc);

    sectionTitle(doc, 'Conclusión');
    normalizePdfState(doc);

    const left =
        doc.page.margins.left;

    const width =
        doc.page.width -
        doc.page.margins.left -
        doc.page.margins.right;

    doc.fontSize(10)
        .fillColor('#333333')
        .text(
            safeText(text, 2000),
            left,
            doc.y,
            {
                width,
                align: 'left',
                lineGap: 4
            }
        );

    normalizePdfState(doc);
}

function getBranding(payload) {
    const source = payload?.branding || {};

    return {
        companyName:
            source.companyName ||
            'Eficentra',

        customerName:
            source.customerName ||
            '',

        siteName:
            source.siteName ||
            '',

        coverTitle:
            source.coverTitle ||
            '',

        coverSubtitle:
            source.coverSubtitle ||
            '',

        logoUrl:
            source.logoUrl ||
            '',

        primaryColor:
            normalizeHexColor(
                source.primaryColor,
                '#1B8DD0'
            ),

        secondaryColor:
            normalizeHexColor(
                source.secondaryColor,
                '#00BCD4'
            ),

        footerText:
            source.footerText ||
            'Reporte generado por Eficentra',

        confidentialityText:
            source.confidentialityText ||
            '',

        showPageNumbers:
            source.showPageNumbers !== false,

        showGeneratedDate:
            source.showGeneratedDate !== false
    };
}

function buildReportTheme(payload) {
    const branding = getBranding(payload);

    return {
        primaryColor: branding.primaryColor,
        secondaryColor: branding.secondaryColor,
        primaryTextColor:
            contrastTextColor(
                branding.primaryColor
            )
    };
}

function getTheme(doc) {
    return doc?._eficentraTheme || {
        primaryColor: '#1B8DD0',
        secondaryColor: '#00BCD4',
        primaryTextColor: '#FFFFFF'
    };
}

function normalizeHexColor(value, fallback) {
    const color = String(value || '').trim();

    if (/^#[0-9A-Fa-f]{6}$/.test(color)) {
        return color.toUpperCase();
    }

    if (/^#[0-9A-Fa-f]{3}$/.test(color)) {
        return (
            '#' +
            color
                .substring(1)
                .split('')
                .map(character => character + character)
                .join('')
        ).toUpperCase();
    }

    return fallback;
}

function contrastTextColor(hexColor) {
    const normalized =
        normalizeHexColor(hexColor, '#1B8DD0')
            .substring(1);

    const red =
        parseInt(normalized.substring(0, 2), 16);

    const green =
        parseInt(normalized.substring(2, 4), 16);

    const blue =
        parseInt(normalized.substring(4, 6), 16);

    const luminance =
        (
            red * 299 +
            green * 587 +
            blue * 114
        ) / 1000;

    return luminance >= 155
        ? '#111111'
        : '#FFFFFF';
}

function renderPageFooters(doc, payload) {
    const branding = getBranding(payload);
    const theme = getTheme(doc);
    const pageRange = doc.bufferedPageRange();

    if (!pageRange || pageRange.count <= 1) {
        return;
    }

    const firstContentPage = pageRange.start + 1;
    const lastPageExclusive =
        pageRange.start + pageRange.count;

    const totalContentPages =
        pageRange.count - 1;

    const lastPageIndex =
        lastPageExclusive - 1;

    const generatedAt =
        payload?.meta?.generatedAt ||
        new Date().toISOString();

    for (
        let pageIndex = firstContentPage;
        pageIndex < lastPageExclusive;
        pageIndex++
    ) {
        doc.switchToPage(pageIndex);

        const originalX = doc.x;
        const originalY = doc.y;
        const originalBottomMargin =
            doc.page.margins.bottom;

        /*
         * PDFKit crea una página nueva cuando se escribe
         * dentro del margen inferior. Temporalmente se
         * elimina ese margen únicamente para el pie.
         */
        doc.page.margins.bottom = 0;

        const pageNumber =
            pageIndex - firstContentPage + 1;

        const left =
            doc.page.margins.left;

        const right =
            doc.page.width -
            doc.page.margins.right;

        const width =
            right - left;

        const lineY =
            doc.page.height - 34;

        const textY =
            doc.page.height - 25;

        const columnWidth =
            width / 3;

        const leftText =
            branding.confidentialityText || '';

        const centerParts = [];

        if (branding.footerText) {
            centerParts.push(branding.footerText);
        }

        if (branding.showGeneratedDate) {
            centerParts.push(formatIsoDate(generatedAt));
        }

        const centerText =
            centerParts.join(' · ');

        const rightText =
            branding.showPageNumbers
                ? `Página ${pageNumber} de ${totalContentPages}`
                : '';

        doc.save();

        doc.moveTo(left, lineY)
            .lineTo(right, lineY)
            .strokeColor(theme.secondaryColor)
            .lineWidth(0.6)
            .stroke();

        doc.fontSize(6.5)
            .fillColor('#687582')
            .text(
                safeText(leftText, 55),
                left,
                textY,
                {
                    width: columnWidth - 8,
                    height: 10,
                    align: 'left',
                    lineBreak: false
                }
            );

        doc.fontSize(6.5)
            .fillColor('#687582')
            .text(
                safeText(centerText, 68),
                left + columnWidth,
                textY,
                {
                    width: columnWidth,
                    height: 10,
                    align: 'center',
                    lineBreak: false
                }
            );

        doc.fontSize(6.5)
            .fillColor('#687582')
            .text(
                rightText,
                left + columnWidth * 2 + 8,
                textY,
                {
                    width: columnWidth - 8,
                    height: 10,
                    align: 'right',
                    lineBreak: false
                }
            );

        doc.restore();

        doc.page.margins.bottom =
            originalBottomMargin;

        doc.x = originalX;
        doc.y = originalY;
    }

    doc.switchToPage(lastPageIndex);
}

function sectionTitle(doc, title) {
    resetCursor(doc);
    ensureSpace(doc, 90);
    resetCursor(doc);

    doc.moveDown(0.5);
    resetCursor(doc);

    doc.fontSize(16)
        .fillColor(getTheme(doc).primaryColor)
        .text(title, doc.page.margins.left, doc.y, {
            width: doc.page.width - doc.page.margins.left - doc.page.margins.right,
            align: 'left',
            lineBreak: false
        });

    const lineY = doc.y + 6;

    doc.moveTo(doc.page.margins.left, lineY)
        .lineTo(doc.page.width - doc.page.margins.right, lineY)
        .strokeColor(getTheme(doc).secondaryColor)
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

function formatShortDate(value, timezone = 'UTC') {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return '-';
    }

    const parts = getDateTimeParts(number, timezone, false);

    return `${parts.day}/${parts.month}, ${parts.hour}:${parts.minute}`;
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

function formatTableDate(value, timezone = 'UTC') {
    if (!value || value === '-') {
        return '-';
    }

    const number = Number(value);

    if (!Number.isFinite(number)) {
        return String(value);
    }

    const parts = getDateTimeParts(number, timezone, true);

    return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`;
}

function getDateTimeParts(value, timezone = 'UTC', includeYear = true) {
    let safeTimezone = timezone || 'UTC';

    try {
        new Intl.DateTimeFormat('es-MX', {
            timeZone: safeTimezone
        }).format(new Date(value));
    } catch (_) {
        safeTimezone = 'UTC';
    }

    const options = {
        timeZone: safeTimezone,
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23'
    };

    if (includeYear) {
        options.year = '2-digit';
    }

    const values = {};

    new Intl.DateTimeFormat('es-MX', options)
        .formatToParts(new Date(value))
        .forEach(part => {
            if (part.type !== 'literal') {
                values[part.type] = part.value;
            }
        });

    return {
        day: values.day || '--',
        month: values.month || '--',
        year: values.year || '--',
        hour: values.hour || '--',
        minute: values.minute || '--'
    };
}

function renderSeriesWithGranularity(doc, payload, item, beforeFirstRender) {
    const points = item?.points || [];

    if (!points.length) {
        return;
    }

    const granularity = getSeriesGranularity(payload, item);

    if (granularity === 'FULL') {
        renderSingleSeriesChart(doc, payload, item, null, beforeFirstRender);
        return;
    }

    const groups = splitPointsByGranularity(points, granularity, getPayloadTimezone(payload));

    if (!groups.length) {
        renderSingleSeriesChart(doc, payload, item, null, beforeFirstRender);
        return;
    }

    let firstRendered = false;

    groups.forEach(group => {
        if (!group.points.length) {
            return;
        }

        const groupedItem = {
            ...item,
            points: group.points
        };

        renderSingleSeriesChart(
            doc,
            payload,
            groupedItem,
            group.label,
            !firstRendered
                ? () => {
                    firstRendered = true;
                    beforeFirstRender?.();
                }
                : null
        );
    });
}

function renderSingleSeriesChart(doc, payload, item, periodLabel, beforeRender) {
    const points = item?.points || [];

    if (!points.length) {
        return;
    }

    ensureSpace(doc, 300);
    normalizePdfState(doc);
    beforeRender?.();

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

    drawLineChart(
        doc,
        points,
        chartBox,
        item?.unit || '',
        getPayloadTimezone(payload)
    );

    doc.y = chartBox.y + chartBox.height + 14;
    normalizePdfState(doc);

    renderSeriesMiniTable(doc, item, payload);
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

function applySeriesPresentation(payload, item) {
    if (!item) {
        return item;
    }

    const variable = findVariableConfigForSeries(payload, item);

    return {
        ...item,
        label:
            variable?.label ||
            item?.label ||
            item?.key ||
            'Variable',
        unit:
            variable?.unit ??
            item?.unit ??
            '',
        entityName:
            variable?.entityName ||
            item?.entityName ||
            ''
    };
}

function findVariableConfigByKey(payload, key, entityName) {
    const variables = extractVariableConfigs(payload);
    const normalizedKey = String(key || '').trim();
    const normalizedEntity = String(entityName || '').trim();

    return variables.find(variable => {
        const variableKey = String(variable?.key || '').trim();
        const variableEntity = String(variable?.entityName || '').trim();

        return variableKey === normalizedKey &&
            (!normalizedEntity || !variableEntity || variableEntity === normalizedEntity);
    }) || variables.find(variable =>
        String(variable?.key || '').trim() === normalizedKey
    ) || null;
}

function formatVariableLabel(variable, fallbackKey, includeUnit = false) {
    const label =
        variable?.label ||
        fallbackKey ||
        'Variable';

    const unit = String(variable?.unit || '').trim();

    return includeUnit && unit
        ? `${label} (${unit})`
        : label;
}

function humanizeAggregation(value) {
    const normalized = String(value || '').trim().toUpperCase();

    const labels = {
        AVG: 'Promedio',
        MIN: 'Mínimo',
        MAX: 'Máximo',
        SUM: 'Suma',
        COUNT: 'Muestras',
        FIRST: 'Primera muestra',
        LAST: 'Última muestra',
        DELTA: 'Diferencia'
    };

    return labels[normalized] || '';
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

if (
    cluster.isPrimary &&
    renderWorkerCount > 1
) {
    startRendererCluster();
} else {
    startRendererHttpServer();
}

function startRendererCluster() {
    let shuttingDown = false;

    console.log(
        `[report-render] Primary ${process.pid} starting ${renderWorkerCount} workers`
    );

    for (
        let index = 0;
        index < renderWorkerCount;
        index++
    ) {
        cluster.fork();
    }

    cluster.on(
        'exit',
        (worker, code, signal) => {
            console.error(
                `[report-render] Worker ${worker.process.pid} exited code=${code} signal=${signal || '-'}`
            );

            if (!shuttingDown) {
                cluster.fork();
            }
        }
    );

    const shutdown = signal => {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        console.log(
            `[report-render] Primary received ${signal}; stopping workers`
        );

        for (
            const worker of Object.values(
                cluster.workers || {}
            )
        ) {
            worker?.process?.kill(
                'SIGTERM'
            );
        }

        setTimeout(
            () => process.exit(0),
            10000
        ).unref();
    };

    process.once(
        'SIGTERM',
        () => shutdown('SIGTERM')
    );

    process.once(
        'SIGINT',
        () => shutdown('SIGINT')
    );
}

function startRendererHttpServer() {
    const server = app.listen(
        port,
        '127.0.0.1',
        () => {
            console.log(
                `[report-render] Worker ${process.pid} listening on http://127.0.0.1:${port} workers=${renderWorkerCount} concurrency=${renderConcurrency} maxQueue=${renderMaxQueue}`
            );
        }
    );

    server.keepAliveTimeout = 65000;
    server.headersTimeout = 66000;

    let shuttingDown = false;

    const shutdown = signal => {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        console.log(
            `[report-render] Worker ${process.pid} received ${signal}; closing HTTP server`
        );

        server.close(() => {
            process.exit(0);
        });

        setTimeout(
            () => process.exit(1),
            10000
        ).unref();
    };

    process.once(
        'SIGTERM',
        () => shutdown('SIGTERM')
    );

    process.once(
        'SIGINT',
        () => shutdown('SIGINT')
    );
}