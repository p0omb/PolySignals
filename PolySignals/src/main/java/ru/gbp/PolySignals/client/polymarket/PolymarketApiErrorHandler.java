package ru.gbp.PolySignals.client.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PolymarketApiErrorHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PolymarketApiErrorHandler() {
    }

    /**
     * Бросает PolymarketApiException, если статус >= 400.
     */
    public static void throwIfError(int statusCode, String responseBody, String endpoint) {
        if (statusCode < 400) {
            return;
        }
        throw buildException(statusCode, responseBody, endpoint,(HttpHeaders) null);
    }

    /**
     * Вариант с requestId, если он уже известен из заголовков.
     */
    public static void throwIfError(int statusCode, String responseBody, String endpoint, String requestIdFromHeader) {
        if (statusCode < 400) {
            return;
        }
        throw buildException(statusCode, responseBody, endpoint, requestIdFromHeader);
    }

    /**
     * Обертка для сетевых/транспортных ошибок (timeout, connect reset, DNS и т.п.).
     */
    public static PolymarketApiException fromTransportError(Throwable cause, String endpoint) {
        String msg = cause == null ? "Transport error while calling Polymarket API"
                : "Transport error while calling Polymarket API: " + safe(cause.getMessage());
        return new RetryablePolymarketApiException(
                0,
                "TRANSPORT_ERROR",
                msg,
                endpoint,
                null,
                null,
                true,
                ErrorCategory.TRANSPORT,
                cause
        );
    }

    public static PolymarketApiException buildException(
            int statusCode,
            String responseBody,
            String endpoint,
            HttpHeaders headers
    ) {
        return buildException(statusCode, responseBody, endpoint, extractRequestId(headers));
    }

    public static PolymarketApiException buildException(int statusCode, String responseBody, String endpoint, String requestIdFromHeader) {
        ParsedError parsed = parseBody(responseBody);
        ErrorCategory category = categorize(statusCode);
        boolean retryable = isRetryable(statusCode, category);

        String requestId = firstNonBlank(requestIdFromHeader, parsed.requestId);
        String code = firstNonBlank(parsed.code, defaultCodeForStatus(statusCode, category));
        String message = firstNonBlank(parsed.message, reasonPhrase(statusCode), "Polymarket API error");

        if (!parsed.details.isEmpty()) {
            message = message + " | details: " + String.join("; ", parsed.details);
        }

        return retryable
                ? new RetryablePolymarketApiException(
                        statusCode, code, message, endpoint, requestId, responseBody, true, category, null
                )
                : new PolymarketApiException(
                        statusCode, code, message, endpoint, requestId, responseBody, false, category, null
                );
    }

    private static String extractRequestId(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) return null;
        String[] keys = { "X-Request-ID", "x-request-id", "request-id", "X-Correlation-ID", "x-correlation-id" };
        for (String key : keys) {
            String value = headers.getFirst(key);
            if (value != null && !value.isBlank()) {
                return firstNonBlank(value);
            }
        }
        return null;
    }

    private static ParsedError parseBody(String body) {
        ParsedError out = new ParsedError();
        if (isBlank(body)) {
            return out;
        }

        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            out.message = trimmed;
            return out;
        }

        try {
            JsonNode root = MAPPER.readTree(trimmed);

            if (root.isObject()) {
                out.message = firstNonBlank(
                        text(root, "message"),
                        text(root, "error_description"),
                        text(root, "detail"),
                        text(root, "title"),
                        text(root, "error"),
                        text(root, "reason")
                );
                out.code = firstNonBlank(
                        text(root, "code"),
                        text(root, "error_code"),
                        text(root, "type")
                );
                out.requestId = firstNonBlank(
                        text(root, "request_id"),
                        text(root, "requestId"),
                        text(root, "x_request_id")
                );

                JsonNode errorNode = root.get("error");
                if (errorNode != null && errorNode.isObject()) {
                    out.message = firstNonBlank(
                            out.message,
                            text(errorNode, "message"),
                            text(errorNode, "detail"),
                            text(errorNode, "description")
                    );
                    out.code = firstNonBlank(
                            out.code,
                            text(errorNode, "code"),
                            text(errorNode, "type")
                    );
                }

                JsonNode errorsNode = root.get("errors");
                if (errorsNode != null && errorsNode.isArray()) {
                    for (JsonNode n : errorsNode) {
                        if (n.isTextual()) {
                            out.details.add(n.asText());
                            continue;
                        }
                        if (n.isObject()) {
                            String field = firstNonBlank(text(n, "field"), text(n, "path"), text(n, "param"));
                            String msg = firstNonBlank(text(n, "message"), text(n, "detail"), n.toString());
                            out.details.add(isBlank(field) ? msg : field + ": " + msg);
                        } else {
                            out.details.add(n.toString());
                        }
                    }
                }
            } else if (root.isArray()) {
                List<String> details = new ArrayList<>();
                for (JsonNode n : root) {
                    details.add(n.isTextual() ? n.asText() : n.toString());
                }
                out.details.addAll(details);
                if (!details.isEmpty()) {
                    out.message = "Multiple errors returned by API";
                }
            }
        } catch (Exception ignored) {
            out.message = trimmed;
        }

        return out;
    }

    private static ErrorCategory categorize(int statusCode) {
        return switch (statusCode) {
            case 400, 422 -> ErrorCategory.VALIDATION;
            case 401 -> ErrorCategory.AUTH;
            case 403 -> ErrorCategory.PERMISSION;
            case 404 -> ErrorCategory.NOT_FOUND;
            case 409 -> ErrorCategory.CONFLICT;
            case 408, 429 -> ErrorCategory.RATE_LIMIT;
            case 500, 502, 503, 504 -> ErrorCategory.UPSTREAM;
            default -> statusCode >= 400 && statusCode < 500 ? ErrorCategory.CLIENT : ErrorCategory.UNKNOWN;
        };
    }

    private static boolean isRetryable(int statusCode, ErrorCategory category) {
        if (category == ErrorCategory.TRANSPORT || category == ErrorCategory.UPSTREAM || category == ErrorCategory.RATE_LIMIT) {
            return true;
        }
        return statusCode == 425;
    }

    private static String defaultCodeForStatus(int statusCode, ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> "VALIDATION_ERROR";
            case AUTH -> "UNAUTHORIZED";
            case PERMISSION -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case RATE_LIMIT -> "RATE_LIMIT";
            case UPSTREAM -> "UPSTREAM_ERROR";
            case TRANSPORT -> "TRANSPORT_ERROR";
            case CLIENT -> "CLIENT_ERROR";
            default -> "UNKNOWN_ERROR";
        } + "_" + statusCode;
    }

    private static String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 425 -> "Too Early";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP " + statusCode;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return safe(value.asText());
        }
        return safe(value.toString());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (!isBlank(v)) {
                return safe(v.trim());
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safe(String s) {
        if (s == null) return null;
        String cleaned = s.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() > 4000 ? cleaned.substring(0, 4000) + "...(truncated)" : cleaned;
    }

    private static final class ParsedError {
        private String message;
        private String code;
        private String requestId;
        private final List<String> details = new ArrayList<>();
    }

    public enum ErrorCategory {
        VALIDATION,
        AUTH,
        PERMISSION,
        NOT_FOUND,
        CONFLICT,
        RATE_LIMIT,
        UPSTREAM,
        TRANSPORT,
        CLIENT,
        UNKNOWN
    }

    public static class PolymarketApiException extends RuntimeException {
        private final int statusCode;
        private final String code;
        private final String endpoint;
        private final String requestId;
        private final String rawBody;
        private final boolean retryable;
        private final ErrorCategory category;

        public PolymarketApiException(
                int statusCode,
                String code,
                String message,
                String endpoint,
                String requestId,
                String rawBody,
                boolean retryable,
                ErrorCategory category,
                Throwable cause
        ) {
            super(buildFinalMessage(statusCode, code, message, endpoint, requestId, retryable, category), cause);
            this.statusCode = statusCode;
            this.code = code;
            this.endpoint = endpoint;
            this.requestId = requestId;
            this.rawBody = rawBody;
            this.retryable = retryable;
            this.category = Objects.requireNonNullElse(category, ErrorCategory.UNKNOWN);
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getCode() {
            return code;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRawBody() {
            return rawBody;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public ErrorCategory getCategory() {
            return category;
        }

        private static String buildFinalMessage(
                int statusCode,
                String code,
                String message,
                String endpoint,
                String requestId,
                boolean retryable,
                ErrorCategory category
        ) {
            return "Polymarket API error"
                    + " | status=" + statusCode
                    + " | code=" + PolymarketApiErrorHandler.firstNonBlank(code, "N/A")
                    + " | category=" + category
                    + " | retryable=" + retryable
                    + " | endpoint=" + PolymarketApiErrorHandler.firstNonBlank(endpoint, "N/A")
                    + " | requestId=" + PolymarketApiErrorHandler.firstNonBlank(requestId, "N/A")
                    + " | message=" + PolymarketApiErrorHandler.firstNonBlank(message, "N/A");
        }
    }

    public static class RetryablePolymarketApiException extends PolymarketApiException {
        public RetryablePolymarketApiException(
                int statusCode,
                String code,
                String message,
                String endpoint,
                String requestId,
                String rawBody,
                boolean retryable,
                ErrorCategory category,
                Throwable cause
        ) {
            super(statusCode, code, message, endpoint, requestId, rawBody, retryable, category, cause);
        }
    }
}
