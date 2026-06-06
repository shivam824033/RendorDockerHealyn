package com.healyn.common.logging;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_ACCOUNT_ID = "accountId";

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private TraceContext() {}

    public static String currentTraceId() {
        String v = MDC.get(MDC_TRACE_ID);
        return v == null ? "" : v;
    }

    public static String currentRequestId() {
        String v = MDC.get(MDC_REQUEST_ID);
        return v == null ? "" : v;
    }
}
