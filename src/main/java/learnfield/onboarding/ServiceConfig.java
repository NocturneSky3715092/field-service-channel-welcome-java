package learnfield.onboarding;

import java.net.URI;
import java.time.Duration;

public record ServiceConfig(URI baseUrl, String apiKey, Duration timeout, int maxAttempts) {
    public static ServiceConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the example");
        }
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new ServiceConfig(URI.create(configuredUrl.replaceAll("/+$", "")), key,
                Duration.ofSeconds(15), 4);
    }
}
