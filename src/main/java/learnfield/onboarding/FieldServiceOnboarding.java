package learnfield.onboarding;

import java.util.List;

public final class FieldServiceOnboarding {
    private FieldServiceOnboarding() {}

    public static void main(String[] args) {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        FieldServiceWelcome service = new FieldServiceWelcome(new InfraiGateway(config));
        FieldServiceWelcome.Signup lesson = new FieldServiceWelcome.Signup(
                required("DEMO_USER_ID"),
                System.getenv().getOrDefault("DEMO_TECHNICIAN_NAME", "Mina"),
                required("DEMO_EMAIL"),
                System.getenv().getOrDefault("DEMO_PHONE", ""),
                parseChannel(System.getenv().getOrDefault("DEMO_SIGNUP_CHANNEL", "EMAIL")),
                new FieldServiceWelcome.WorkOrder(
                        System.getenv().getOrDefault("DEMO_WORK_ORDER_ID", "WO-1042"),
                        "DISPATCHED",
                        List.of("https://images.example.test/wo-1042/arrival.jpg",
                                "https://images.example.test/wo-1042/panel.jpg"),
                        "Confirm the replacement part after the site visit"),
                System.getenv().getOrDefault("DEMO_REQUEST_ID", "welcome-wo-1042"));

        FieldServiceWelcome.Result result = service.onboard(lesson);
        System.out.printf("channel=%s message_id=%s dispatch=%s photos=%d follow_up=%s%n",
                result.channel(), result.messageId(), result.dispatchStatus(), result.photoCount(),
                result.technicianFollowUp());
    }

    private static FieldServiceWelcome.Channel parseChannel(String value) {
        return FieldServiceWelcome.Channel.valueOf(value.toUpperCase());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }
}
