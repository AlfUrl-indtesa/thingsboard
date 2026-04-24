package org.thingsboard.server.service.report;

import lombok.Getter;
import org.thingsboard.server.common.data.report.ReportErrorCode;

@Getter
public class ReportServiceException extends RuntimeException {

    private final ReportErrorCode errorCode;

    public ReportServiceException(ReportErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReportServiceException(ReportErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}