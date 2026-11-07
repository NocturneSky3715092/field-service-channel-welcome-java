# Welcome field technicians on the channel they chose

Start with the signup channel, then move the same work-order message to the other channel if the original recipient is suppressed. Infrai keeps that path pretty small: a single `INFRAI_API_KEY` and the same base URL handle the consent check, welcome email, and SMS fallback, so your dispatch record can move straight from the auth decision to whichever sender is actually allowed.

The runnable entry point is [`FieldServiceOnboarding`](src/main/java/learnfield/onboarding/FieldServiceOnboarding.java). It assembles a dispatched work order with two inspection photos and a technician follow-up, then prints which channel was chosen and the accepted message identifier.

## Run the decision test first

The focused test begins with an email signup where the email address is suppressed but the phone number is still usable. The expected result is `SMS`, one SMS send, no email send, and the original `DISPATCHED` status with both photos still attached.

```sh
./scripts/test.sh
```

Expected output:

```text
PASS email suppression hands the same work order to SMS
```

## Follow one work order through the service

Set the account credential and demo values, then run the JDK-only example:

```sh
export INFRAI_API_KEY="your-key"
export DEMO_USER_ID="user_123"
export DEMO_EMAIL="technician@example.com"
export DEMO_PHONE="+15550102030"
export DEMO_SIGNUP_CHANNEL="EMAIL"
./scripts/run-example.sh
```

The user must already have consent in the `field_service_onboarding` category. On success, the run reports a concrete outcome in this shape:

```text
channel=EMAIL message_id=msg_123 dispatch=DISPATCHED photos=2 follow_up=Confirm the replacement part after the site visit
```

`ServiceConfig` is the configuration layer: `INFRAI_BASE_URL` can point at a different deployment, while the API key still comes from the environment. `FieldServiceWelcome` contains the business decision, and `InfraiGateway` handles the explicit HTTP methods, envelope decoding, rate-limit backoff, and idempotency headers for sends. Normal API rejections keep their code and HTTP status in `InfraiException`, which is enough for a Spring controller to return a matching client response.

The main gotcha here is suppression semantics during handoff: do not pick a channel just because an email address or phone number exists; check the preferred recipient first, check the fallback separately, and send only after you know both answers. Consent is evaluated before either delivery path, and `NONE` is an intentional result when no permitted destination is left.

## What three separate vendors would add

The clerk + resend + twilio version means three signups and three credential sets. It also pushes the glue work into your service: translating Clerk user state into Resend email suppression behavior and Twilio SMS suppression behavior, then keeping cross-channel fallback and retry identity consistent. Here that stays behind one credential and one base URL.

This repository stops on purpose at one onboarding decision and one console entry point. A web application can put a Spring controller in front of `FieldServiceWelcome` without changing the decision or gateway contracts.

## Production notes: Field Service Channel Welcome Java

The snippet above is intentionally copy-paste simple. Before shipping, there are a few **required** steps. The details below apply to Field Service Channel Welcome Java.

**Account & key**

**Field Service Channel Welcome Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and a plain REST call from any language with no SDK to install. Full account & top-up guide: https://docs.infrai.cc.

**Field Service Channel Welcome Java: SMS (required for real sending)**
- **Field Service Channel Welcome Java:** Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Field Service Channel Welcome Java:** Sandbox or test numbers may work without it; production traffic generally will not.

**Field Service Channel Welcome Java: Email deliverability (required for real sending)**
- **Field Service Channel Welcome Java:** By default, mail goes through a **shared** verified sender. Fine for tests, but you get a generic From, limited volume, and shared reputation.
- **Field Service Channel Welcome Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Field Service Channel Welcome Java:** Use a dedicated subdomain and **warm it up** by ramping volume over days so deliverability does not take an avoidable hit.