package learnfield.onboarding;

import java.util.List;

public final class FieldServiceWelcome {
    public enum Channel { EMAIL, SMS, NONE }

    public record WorkOrder(String id, String dispatchStatus, List<String> photoUrls,
                            String technicianFollowUp) {}

    public record Signup(String userId, String technicianName, String email, String phone,
                         Channel signupChannel, WorkOrder workOrder, String requestId) {}

    public record Result(Channel channel, String messageId, String dispatchStatus,
                         int photoCount, String technicianFollowUp) {}

    public interface Gateway {
        boolean hasConsent(String userId);
        boolean emailSuppressed(String email);
        boolean smsSuppressed(String phone);
        String sendEmail(Signup signup);
        String sendSms(Signup signup);
    }

    private final Gateway gateway;

    public FieldServiceWelcome(Gateway gateway) {
        this.gateway = gateway;
    }

    public Result onboard(Signup signup) {
        requireSignup(signup);
        if (!gateway.hasConsent(signup.userId())) {
            return result(signup, Channel.NONE, "");
        }

        boolean emailReady = !signup.email().isBlank() && !gateway.emailSuppressed(signup.email());
        boolean smsReady = !signup.phone().isBlank() && !gateway.smsSuppressed(signup.phone());
        Channel selected = choose(signup.signupChannel(), emailReady, smsReady);
        String messageId = switch (selected) {
            case EMAIL -> gateway.sendEmail(signup);
            case SMS -> gateway.sendSms(signup);
            case NONE -> "";
        };
        return result(signup, selected, messageId);
    }

    public static Channel choose(Channel signedUpWith, boolean emailReady, boolean smsReady) {
        if (signedUpWith == Channel.EMAIL && emailReady) return Channel.EMAIL;
        if (signedUpWith == Channel.SMS && smsReady) return Channel.SMS;
        if (signedUpWith == Channel.EMAIL && smsReady) return Channel.SMS;
        if (signedUpWith == Channel.SMS && emailReady) return Channel.EMAIL;
        return Channel.NONE;
    }

    private static Result result(Signup signup, Channel channel, String messageId) {
        WorkOrder order = signup.workOrder();
        return new Result(channel, messageId, order.dispatchStatus(), order.photoUrls().size(),
                order.technicianFollowUp());
    }

    private static void requireSignup(Signup signup) {
        if (signup.userId().isBlank() || signup.requestId().isBlank()) {
            throw new IllegalArgumentException("userId and requestId are required");
        }
        if (signup.signupChannel() == Channel.NONE) {
            throw new IllegalArgumentException("signupChannel must be EMAIL or SMS");
        }
    }
}
