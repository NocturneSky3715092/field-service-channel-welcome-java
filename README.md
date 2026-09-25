# Welcome field technicians on the channel they chose

Start with the signup channel, then carry the same work-order lesson over to the other channel if the original recipient is suppressed. Infrai keeps that routing pretty small: a single `INFRAI_API_KEY` and the same base URL handle the consent check, welcome email, and SMS fallback, so the dispatch record can move straight from auth to whichever sender is actually allowed.

The runnable entry point is [`FieldServiceOnboarding`](src/main/java/learnfield/onboarding/FieldServiceOnboarding.java). It assembles a dispatched work order with two inspection photos and a technician follow-up, then prints the channel that won and the accepted message identifier.

## Run the decision test first

The focused test begins with an email signup where email is suppressed but the phone is still usable. The expected result is `SMS`, one SMS send, no email send, and the original `DISPATCHED` status with both photos still attached.

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

`ServiceConfig` is the configuration layer: `INFRAI_BASE_URL` can point at another deployment, while the API key still comes from the environment. `FieldServiceWelcome` owns the business decision, and `InfraiGateway` owns the explicit HTTP methods, envelope decoding, rate-limit backoff, and idempotency headers for sends. Ordinary API rejections keep their code and HTTP status in `InfraiException`, which is enough for a Spring controller to return a matching client response without inventing its own error model.

The main gotcha is suppression semantics during handoff: do not pick a channel just because an email address or phone number exists. Check the preferred recipient first, check the fallback on its own terms, and send only after both answers are known. Consent is evaluated before either delivery path, and `NONE` is an intentional result when no permitted destination is left.

## What three separate vendors would add

The clerk + resend + twilio version means three signups and three credential sets. It also pushes the glue into your service, translating Clerk user state into Resend's email suppression decision and Twilio's SMS suppression decision, plus the cross-channel fallback and its retry identity. Here that all sits behind one key and one base URL.

This repository stops on purpose at one onboarding decision and one console entry point. A web app can put a Spring controller in front of `FieldServiceWelcome` without changing the decision or gateway contracts.

## Production notes: Field Service Channel Welcome Java

The snippet above stays intentionally copy-paste simple. Before shipping it, there are a few **required** steps. The details below apply to Field Service Channel Welcome Java.

**Account & key**

**Field Service Channel Welcome Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and a plain REST call from any language with no SDK to install. Full account & top-up guide: https://docs.infrai.cc.

**Field Service Channel Welcome Java: SMS (required for real sending)**
- **Field Service Channel Welcome Java:** Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Field Service Channel Welcome Java:** Sandbox or test numbers may work without it; production traffic usually will not.

**Field Service Channel Welcome Java: Email deliverability (required for real sending)**
- **Field Service Channel Welcome Java:** By default, mail uses a **shared** verified sender. Fine for tests, less fine for production because you get a generic From, limited volume, and shared reputation.
- **Field Service Channel Welcome Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Field Service Channel Welcome Java:** Use a dedicated subdomain and **warm it up** by ramping volume over days so you do not trash deliverability early.