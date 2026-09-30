# Monetization — RevenueCat

> **Superseded.** This was the original pricing idea from the multi-platform design. The shipped Android plans, limits and RevenueCat integration are described in [BUSINESS_MODEL.md](BUSINESS_MODEL.md).


## 1. Monetization objective

RevenueCat is part of the actual product, not a decorative hackathon integration.

CueBack has ongoing value because context recovery becomes more valuable as users accumulate contexts, connect more sources, and need richer reconstruction.

## 2. Product catalog

Suggested initial products:

### Monthly

Product ID:

`cueback_pro_monthly`

Suggested price hypothesis:

`$4.99 / month`

### Annual

Product ID:

`cueback_pro_annual`

Suggested price hypothesis:

`$29.99–$39.99 / year`

The exact price should be selected from the RevenueCat dashboard and treated as a business experiment, not as a scientific market fact.

## 3. Entitlement

RevenueCat entitlement:

`cueback_pro`

Premium gate:

```text
RevenueCat Customer Info
        ↓
check entitlement cueback_pro
        ↓
active → unlock Pro
inactive → Free tier
```

## 4. Free tier

Free:

- 3 active contexts,
- manual text capture,
- local storage,
- basic warm start,
- basic history.

## 5. Pro tier

Pro:

- unlimited contexts,
- automatic desktop context detection,
- voice capture,
- adaptive recovery,
- delta manifest,
- context evolution,
- artifact vault,
- cross-device sync,
- configurable source integrations.

## 6. Paywall strategy

Do not paywall before the user understands the value.

Suggested trigger:

```text
Install
 ↓
First automatic context captured
 ↓
First successful Warm Start
 ↓
Show value moment
 ↓
Introduce Pro
```

Paywall copy:

> You just resumed without rebuilding the task from scratch.
>
> **Keep that continuity across every project.**

## 7. Free trial

Use a free trial or judge promo code so evaluators can test premium features, consistent with the current Shipaton submission requirements.

Recommended initial test:

- 7-day trial on annual plan.

## 8. Purchase flow

```text
Paywall
 ↓
RevenueCat offerings
 ↓
Package selection
 ↓
Purchase
 ↓
CustomerInfo update
 ↓
Entitlement active
 ↓
Unlock Pro
```

Handle:

- cancellation,
- restore purchases,
- failed payment,
- network error,
- pending transaction,
- expired entitlement.

## 9. Server considerations

The app should not trust client-only strings like `isPro=true`.

For server-protected features:

- verify authenticated RevenueCat state server-side using RevenueCat's supported mechanisms,
- cache conservatively,
- fail gracefully when the service is unavailable.

## 10. Analytics

Recommended privacy-safe product events:

```text
paywall_viewed
trial_started
purchase_completed
purchase_failed
restore_completed
premium_feature_used
```

Never attach sensitive task content to billing analytics.

## 11. Optional web funnel

A separate landing page can offer:

> **How much time do interruptions cost you?**

Input:

- interruptions/day,
- average recovery minutes.

Output:

```text
Estimated annual recovery time =
interruptions/day × recovery minutes × working days/year
```

This is a rough product estimate, not a scientific measurement.

Optional funnel:

```text
Content / social post
      ↓
CueBack calculator
      ↓
Landing page
      ↓
RevenueCat Funnel + Stripe
      ↓
Deep link to app
```

## 12. HAMM-ready details

To make monetization credible:

- test multiple paywall copy variants,
- show annual vs monthly clearly,
- explain premium value in terms of automation and recovery depth,
- provide a generous trial,
- implement restore purchases correctly,
- expose RevenueCat dashboard results honestly in the submission.
