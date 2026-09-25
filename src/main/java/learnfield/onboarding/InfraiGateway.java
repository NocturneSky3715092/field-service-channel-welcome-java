package learnfield.onboarding;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiGateway implements FieldServiceWelcome.Gateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern CONSENT_RESULT = Pattern.compile("\\\"result\\\"\\s*:\\s*(true|false)");
    private static final Pattern SUPPRESSED = Pattern.compile("\\\"suppressed\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiGateway(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    @Override public boolean hasConsent(String userId) {
        String path = "/v1/auth/consent/check/" + segment(userId) + "/field_service_onboarding";
        return booleanField(call("GET", path, null, null), CONSENT_RESULT, "result");
    }

    @Override public boolean emailSuppressed(String email) {
        return booleanField(call("GET", "/v1/email/suppression/check/" + segment(email), null, null),
                SUPPRESSED, "suppressed");
    }

    @Override public boolean smsSuppressed(String phone) {
        return booleanField(call("POST", "/v1/sms/suppression/check",
                "{\"phone\":" + quote(phone) + "}", null), SUPPRESSED, "suppressed");
    }

    @Override public String sendEmail(FieldServiceWelcome.Signup signup) {
        String subject = "Dispatch " + signup.workOrder().dispatchStatus() + " for " + signup.workOrder().id();
        String text = message(signup);
        String body = "{\"to\":" + quote(signup.email()) + ",\"subject\":" + quote(subject)
                + ",\"body\":" + quote(text) + "}";
        return stringField(call("POST", "/v1/email/send", body, signup.requestId() + "-email"), MESSAGE_ID);
    }

    @Override public String sendSms(FieldServiceWelcome.Signup signup) {
        String body = "{\"to\":" + quote(signup.phone()) + ",\"body\":" + quote(message(signup)) + "}";
        return stringField(call("POST", "/v1/sms/send", body, signup.requestId() + "-sms"), MESSAGE_ID);
    }

    private String call(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(config.timeout()).header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", e);
            }

            Envelope envelope = decode(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt + 1 < config.maxAttempts()) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) throw new InfraiException(envelope.code(), envelope.message(), response.statusCode());
            if (response.statusCode() >= 500) throw new IllegalStateException("Unexpected upstream response");
            return response.body();
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static Envelope decode(String json, int status) {
        Matcher matcher = OK.matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Response was not an envelope, HTTP " + status);
        return new Envelope(Boolean.parseBoolean(matcher.group(1)), find(ERROR_CODE, json, "REQUEST_REJECTED"),
                find(ERROR_MESSAGE, json, "Request rejected"));
    }

    private static boolean booleanField(String json, Pattern pattern, String name) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Response omitted " + name);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String stringField(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Response omitted message_id");
        return matcher.group(1);
    }

    private static String message(FieldServiceWelcome.Signup signup) {
        FieldServiceWelcome.WorkOrder order = signup.workOrder();
        return "Welcome " + signup.technicianName() + ". Work order " + order.id() + " is "
                + order.dispatchStatus() + "; " + order.photoUrls().size() + " photos attached. Follow-up: "
                + order.technicianFollowUp();
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String header = response.headers().firstValue("Retry-After").orElse("");
        try { return Duration.ofSeconds(Math.max(1, Long.parseLong(header))); }
        catch (NumberFormatException ignored) { return Duration.ofMillis(250L * (1L << attempt)); }
    }

    private static void pause(Duration duration) {
        try { Thread.sleep(duration.toMillis()); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private record Envelope(boolean ok, String code, String message) {}

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;
        public InfraiException(String code, String message, int status) {
            super(message); this.code = code; this.status = status;
        }
        public String code() { return code; }
        public int status() { return status; }
    }
}
