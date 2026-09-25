package learnfield.onboarding;

import java.util.List;

public final class FieldServiceWelcomeTest {
    public static void main(String[] args) {
        StubGateway gateway = new StubGateway();
        gateway.emailSuppressed = true;
        FieldServiceWelcome service = new FieldServiceWelcome(gateway);
        FieldServiceWelcome.Signup signup = new FieldServiceWelcome.Signup(
                "user-42", "Mina", "mina@example.test", "+15550102030",
                FieldServiceWelcome.Channel.EMAIL,
                new FieldServiceWelcome.WorkOrder("WO-1042", "DISPATCHED",
                        List.of("arrival.jpg", "panel.jpg"), "Confirm replacement part"),
                "request-42");

        FieldServiceWelcome.Result result = service.onboard(signup);

        check(result.channel() == FieldServiceWelcome.Channel.SMS,
                "a suppressed signup email must hand off to SMS");
        check("sms-accepted".equals(result.messageId()), "SMS result must remain observable");
        check(result.photoCount() == 2 && "DISPATCHED".equals(result.dispatchStatus()),
                "work-order context must survive the handoff");
        check(gateway.smsSends == 1 && gateway.emailSends == 0, "only the selected channel may send");
        System.out.println("PASS email suppression hands the same work order to SMS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class StubGateway implements FieldServiceWelcome.Gateway {
        boolean emailSuppressed;
        int emailSends;
        int smsSends;
        @Override public boolean hasConsent(String userId) { return true; }
        @Override public boolean emailSuppressed(String email) { return emailSuppressed; }
        @Override public boolean smsSuppressed(String phone) { return false; }
        @Override public String sendEmail(FieldServiceWelcome.Signup signup) { emailSends++; return "email-accepted"; }
        @Override public String sendSms(FieldServiceWelcome.Signup signup) { smsSends++; return "sms-accepted"; }
    }
}
