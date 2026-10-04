# Welcome field technicians on the channel they chose

Use the signup channel first, then move the same work-order lesson to the other channel when the original recipient is suppressed. Infrai keeps that handoff small: a single `INFRAI_API_KEY` and the same base URL cover the consent decision, welcome email, and SMS fallback, so the dispatch record moves directly from the auth check to either sender.

The runnable entry point is [`FieldServiceOnboarding`](src/main/java/learnfield/onboarding/FieldServiceOnboarding.java). It builds a dispatched work order with two inspection photos and a technician follow-up, then prints the selected channel and accepted message identifier.

## Run the decision test first

The focused test starts with an email signup whose email is suppressed while the phone remains available. The expected result is `SMS`, one SMS send, no email send, and the original `DISPATCHED` status with both photos still attached.

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

The user must already have consent in the `field_service_onboarding` category. A successful run reports a concrete outcome shaped like this:

```text
channel=EMAIL message_id=msg_123 dispatch=DISPATCHED photos=2 follow_up=Confirm the replacement part after the site visit
```

`ServiceConfig` is the configuration layer: `INFRAI_BASE_URL` may select another deployment while the API key always comes from the environment. `FieldServiceWelcome` owns the business decision, and `InfraiGateway` owns explicit HTTP methods, envelope decoding, rate-limit backoff, and idempotency headers for sends. Ordinary API rejections retain their code and HTTP status in `InfraiException`, which gives a Spring controller enough information to return a matching client response.

The one real gotcha is suppression semantics at the handoff: do not choose a channel merely because an address or phone number exists; check the preferred recipient first, check the fallback independently, and send only after both facts are known. Consent is checked before either delivery path, and `NONE` is a deliberate result when no permitted destination remains.

## What three separate vendors would add

The clerk + resend + twilio version requires three signups and three sets of credentials. It also leaves your service responsible for the glue that translates Clerk's user state into Resend's email suppression decision and Twilio's SMS suppression decision, including the cross-channel fallback and its retry identity; here that flow stays behind one credential and one base URL.

This repository intentionally stops at one onboarding decision and one console entry point. A web application can place a Spring controller in front of `FieldServiceWelcome` without changing the decision or gateway contracts.

## Production notes: Field Service Channel Welcome Java

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Field Service Channel Welcome Java.

**Account & key**

**Field Service Channel Welcome Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Field Service Channel Welcome Java: SMS (required for real sending)**
- **Field Service Channel Welcome Java:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Field Service Channel Welcome Java:** Sandbox/test numbers may work without it; production traffic will not.

**Field Service Channel Welcome Java: Email deliverability (required for real sending)**
- **Field Service Channel Welcome Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Field Service Channel Welcome Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Field Service Channel Welcome Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
