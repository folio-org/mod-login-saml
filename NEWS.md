## 2.3.2 - 2021-12-22

 * [MODLOGSAML-129](https://issues.folio.org/browse/MODLOGSAML-129) Netty 4.1.72 (CVE-2021-43797), Log4j 2.17.0, Vert.x 4.2.3, RMB 33.2.3

## 2.3.1 - 2021-12-17

 * [MODLOGSAML-124](https://issues.folio.org/browse/MODLOGSAML-124) RMB 33.2.1, Vertx 4.2.1, Log4j 2.15.0 fixing remote execution (CVE-2021-44228)
 * [MODLOGSAML-128](https://issues.folio.org/browse/MODLOGSAML-128) Update to RMB 33.2.2 Log4j 2.16.0

## 2.3.0 - 2021-09-29

 * [MODLOGSAML-105](https://issues.folio.org/browse/MODLOGSAML-105) Upgrade to RMB 33.1.1, Vert.x 4.1.4

## 2.2.1 - 2021-06-23

 * [MODLOGSAML-97](https://issues.folio.org/browse/MODLOGSAML-97) Single-Sign-On (SSO) always fails
 * Update RMB to 33.0.2 to fix MODLOGSAML-97, see [RMB-854](https://issues.folio.org/browse/RMB-854) `FORM_ATTRIBUTE_SIZE_MAX` is too small
 * Update Vertx to 4.1.0

## 2.2.0 - 2021-05-27

 * [MODLOGSAML-58](https://issues.folio.org/browse/MODLOGSAML-58) Arbitrary URL Redirection in SAML Response
 * [MODLOGSAML-63](https://issues.folio.org/browse/MODLOGSAML-63) Implement CSRF Prevention
 * Make UrlUtilTest locale independent
 * Update Vertx to 4.1.0.CR1
 * Update RMB to 33.0.0

## 2.1.2 - 2021-06-17

 * Update RMB to include [RMB-854](https://issues.folio.org/browse/RMB-854) `FORM_ATTRIBUTE_SIZE_MAX is` too small
 * Above RMB change will fix edge cases of [MODLOGSAML-97](https://issues.folio.org/browse/MODLOGSAML-97) Single-Sign-On (SSO) always fails
 * Update RMB to 33.0.1
 * Update Vertx to 4.1.0

## 2.1.1 - 2021-05-11

 * [MODLOGSAML-97](https://issues.folio.org/browse/MODLOGSAML-97) Single-Sign-On (SSO) always fails

## 2.1.0 - 2021-03-09

No new functionality but Vert.x 4 + vertx-pac4j update as well as RMB.

 * [MODLOGSAML-88](https://issues.folio.org/browse/MODLOGSAML-88) Upgrade to RMB 33 pre-1 with Maven Plugin
 * [MODLOGSAML-82](https://issues.folio.org/browse/MODLOGSAML-82) Add personal data disclosure form

## 2.0.1 - 2020-08-28

 * [MODLOGSAML-73](https://issues.folio.org/browse/MODLOGSAML-73) Upgrade raml-module-builder (RMB) from 30.0.1 to 30.2.6
 * [MODLOGSAML-50](https://issues.folio.org/browse/MODLOGSAML-50) Upgrade Pac4j from 2.0.0 to 3.8.3. This requires new sp-metadata.xml uploaded to the IdP, for details see [MODLOGSAML-75](https://issues.folio.org/browse/MODLOGSAML-75).

## 2.0.0 - 2020-06-09

This is a maintenance release focused on keeping dependencies up to date.  The major version change is due to the new permission requirements on APIs which were previously unrestricted.

[Full Changelog](https://github.com/folio-org/mod-login-saml/compare/v1.3.0...v2.0.0)

### Stories
 * [MODLOGSAML-64](https://issues.folio.org/browse/MODLOGSAML-64) - Upgrade to RMB v30
 * [MODLOGSAML-60](https://issues.folio.org/browse/MODLOGSAML-60) - Securing APIs by default

## 1.3.0 - 2020-03-13
 * Rely on RMB's vertx. dependencies - in particular the Postgres driver
   which has been using specific versions with session usage fix
 * Update to RMB 29.3.1 (#55)
 * [MODLOGSAML-53](https://issues.folio.org/browse/MODLOGSAML-53) Use JVM features to manage container memory
 * [MODLOGSAML-51](https://issues.folio.org/browse/MODLOGSAML-51) Fix com.fasterxml.jackson.core:jackson-databind vulnerability

## 1.2.2 - 2019-08-01
 * [MODLOGSAML-45](https://issues.folio.org/browse/MODLOGSAML-45) Fix security vulnerabilities reported in
   jackson-databind >= 2.0.0, < 2.9.9.1
 * [MODLOGSAML-40](https://issues.folio.org/browse/MODLOGSAML-40) api fails to validate idpurl if the content type
   contains charset ([MODLOGSAML-46](https://issues.folio.org/browse/MODLOGSAML-46) dup)

## 1.2.1

 * MODLOGSAML-36: Fix security vulnerability reported in jackson-databind

## 1.2.0

 * Add support for authtoken v2.0 interface

## 1.1.0

 * MODLOGSAML-31: support `users` interface 15.0
 * MODLOGSAML-32: Update Status Field to also control access to Folio via SSO

## 1.0.2

 * MODLOGSAML-30: IdP URL Content-Type check

## 1.0.1

 * First release
