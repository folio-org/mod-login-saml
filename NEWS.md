## 2.9.2 - 2024-11-18

* [MODLOGSAML-201](https://folio-org.atlassian.net/browse/MODLOGSAML-201) \_tenant interface missing from module descriptor

## 2.9.1 - 2024-11-02

* [MODAT-168](https://folio-org.atlassian.net/browse/MODAT-168) Rename permission auth.signtoken to auth.signtoken.all; bump interfaces
* [MODLOGSAML-200](https://folio-org.atlassian.net/browse/MODLOGSAML-200) Upgrade deps: RMB 35.3.0, log4j 2.24.1, PostgreSQL 16, etc.

## 2.9.0 - 2024-11-02

* [MODLOGSAML-111](https://folio-org.atlassian.net/browse/MODLOGSAML-111) Move config from mod-configuration DB to mod-login-saml DB
* [MODLOGSAML-193](https://folio-org.atlassian.net/browse/MODLOGSAML-193) Don't migrate config on fresh install, only on upgrade
* [MODLOGSAML-195](https://folio-org.atlassian.net/browse/MODLOGSAML-195) SimpleSamlPhpContainer, avoid Dockerfile
* [MODLOGSAML-196](https://folio-org.atlassian.net/browse/MODLOGSAML-196) Add folio-module-descriptor-validator
* [MODLOGSAML-189](https://folio-org.atlassian.net/browse/MODLOGSAML-189) Relay State must be a string with a maximum length of 80
* [MODLOGSAML-192](https://folio-org.atlassian.net/browse/MODLOGSAML-192) Allow callback endpoint to return RTR tokens when configured

## 2.8.4 - 2024-09-19

* [MODLOGSAML-197](https://folio-org.atlassian.net/browse/MODLOGSAML-197) Okapi-URL path missing from cookie path

## 2.8.3 - 2024-09-13

* [MODLOGSAML-198](https://folio-org.atlassian.net/browse/MODLOGSAML-198) limit=1000 for GET /configurations/entries

## 2.8.2 - 2024-09-12

* [MODLOGSAML-192](https://folio-org.atlassian.net/browse/MODLOGSAML-192) Allow `callback` endpoint to return RTR tokens when configured
* [MODLOGSAML-194](https://folio-org.atlassian.net/browse/MODLOGSAML-194) pac4j 5.7.7, cryptacular 1.2.7 fixing vulns

## 2.8.1 - 2024-05-29

* [MODLOGSAML-191](https://folio-org.atlassian.net/browse/MODLOGSAML-191) Vert.x 4.5.7 and RMB 35.2.2 fixing Netty CVE-2024-29025

## 2.8.0 - 2024-03-20

* [MODLOGSAML-170](https://folio-org.atlassian.net/browse/MODLOGSAML-170) Add support of SSO login in consortium mode
* [MODLOGSAML-185](https://folio-org.atlassian.net/browse/MODLOGSAML-185) Add tenant id to redirect url
* [MODLOGSAML-187](https://folio-org.atlassian.net/browse/MODLOGSAML-187) Callback is now callback-with-expiry as the default
* [MODLOGSAML-190](https://folio-org.atlassian.net/browse/MODLOGSAML-190) Upgrade deps for Q: RMB 35.2.0, Vertx 4.5.5, vertx-pac4j 6.0.2, pac4j 5.7.3, …

## 2.7.2 - 2023-12-15

* [MODLOGSAML-182](https://issues.folio.org/browse/MODLOGSAML-182) xmlsec 2.3.4 fixing private key in debug level log CVE-2023-44483

## 2.7.1 - 2023-12-07

* [MODLOGSAML-180](https://issues.folio.org/browse/MODLOGSAML-180) Make callback-with-expiry not the default

## 2.7.0 - 2023-10-12

* [MODLOGSAML-173](https://issues.folio.org/browse/MODLOGSAML-173) Upgrade dependencies for Poppy
* [MODLOGSAML-172](https://issues.folio.org/browse/MODLOGSAML-172) Support new mod-authtoken /token/sign endpoint
* [Explain pac4j authentication and authorization mechanisms](https://github.com/folio-org/mod-login-saml/pull/146)
* [MODLOGSAML-169](https://issues.folio.org/browse/MODLOGSAML-169) Update to Java 17
* [FOLIO-3678](https://issues.folio.org/browse/FOLIO-3678) [MODLOGSAML-160](https://issues.folio.org/browse/MODLOGSAML-160) Enable API-related GitHub Workflows, replace those Jenkins stages

## 2.6.2 - 2023-06-01

 * [MODLOGSAML-166](https://issues.folio.org/browse/MODLOGSAML-166) xmlsec 2.3.3, woodstox-core 6.5.0 fixing DoS (CVE-2022-40152)
 * [MODLOGSAML-165](https://issues.folio.org/browse/MODLOGSAML-165) json-smart 2.4.10 fixing DoS (CVE-2023-1370)

## 2.6.1 - 2023-04-01

 * [MODLOGSAML-159](https://issues.folio.org/browse/MODLOGSAML-159) OpenSSL 3.0.8 fixing 8 vulns
 * [MODLOGSAML-161](https://issues.folio.org/browse/MODLOGSAML-161) Use TLSv1.2, not SSL, in SSLContext.getInstance

## 2.6.0 - 2023-02-16

 * [MODLOGSAML-157](https://issues.folio.org/browse/MODLOGSAML-157) Upgrade dependences: pac4j 5.7.0, RMB 35.0.6, Vert.x 4.3.8

## 2.5.0 - 2022-10-25

 * [MODLOGSAML-154](https://issues.folio.org/browse/MODLOGSAML-154) Upgrade to RMB 35.0.2, Vert.x 4.3.4, pac4j 5.4.6
 * [MODLOGSAML-152](https://issues.folio.org/browse/MODLOGSAML-152) Support users interface 14.0 15.0 16.0
 * [MODLOGSAML-151](https://issues.folio.org/browse/MODLOGSAML-151) JDK 17: java.base does not "opens java.util.concurrent.atomic"

## 2.4.9 - 2022-07-12

 * [MODLOGSAML-140](https://issues.folio.org/browse/MODLOGSAML-140) Wrap samlAttributeName String in List

## 2.4.8 - 2022-07-04

 * [MODLOGSAML-148](https://issues.folio.org/browse/MODLOGSAML-148) Bump maxFormAttributeSize from 8192 to 65536

## 2.4.7 - 2022-06-30

 * [MODLOGSAML-146](https://issues.folio.org/browse/MODLOGSAML-146) HTTP/2 causes "Invalid relay state url: null"
 * [MODLOGSAML-144](https://issues.folio.org/browse/MODLOGSAML-144) spring framework 5.3.21 fixing file upload DoS (CVE-2022-22970)

## 2.4.6 - 2022-06-17

 * [MODLOGSAML-142](https://issues.folio.org/browse/MODLOGSAML-142) Upgrade dependencies: RMB 34, Vert.x 4.3.1, pac4j 5.4.3

## 2.4.5 - 2022-04-20

 * [MODLOGSAML-138](https://issues.folio.org/browse/MODLOGSAML-138) Reduce error logging
 * [MODLOGSAML-134](https://issues.folio.org/browse/MODLOGSAML-134) Content-Type validation for Duo's text/xhtml

## 2.4.4 - 2022-04-14

 * [MODLOGSAML-135](https://issues.folio.org/browse/MODLOGSAML-135) Spring4Shell: Update Spring (CVE-2022-22965)
 * [MODLOGSAML-136](https://issues.folio.org/browse/MODLOGSAML-136) jackson-databind 2.13.2.2 (CVE-2020-36518)
 * [MODLOGSAML-137](https://issues.folio.org/browse/MODLOGSAML-137) secureValidation vulnerability (CVE-2021-40690)
 * [MODLOGSAML-107](https://issues.folio.org/browse/MODLOGSAML-107) retry and check 200/500 status

## 2.4.3 - 2022-03-24

 * [MODLOGSAML-107](https://issues.folio.org/browse/MODLOGSAML-107) Delete configuration cache on internal error

## 2.4.2 - 2022-03-10

 * [MODLOGSAML-107](https://issues.folio.org/browse/MODLOGSAML-107) slf4j, web client timeout, Vert.x 4.2.5

## 2.4.1 - 2022-02-09

 * [MODLOGSAML-123](https://issues.folio.org/browse/MODLOGSAML-123) IdP container test
 * [MODLOGSAML-132](https://issues.folio.org/browse/MODLOGSAML-132) Update to vertx-pac4j 6.0.1 fixing "none" alg tokens (CVE-2021-44878)

## 2.4.0 - 2022-01-10

 * [MODLOGSAML-71](https://issues.folio.org/browse/MODLOGSAML-71) Login via SSO possible even after decryption of SAML assertions fails
 * [MODLOGSAML-91](https://issues.folio.org/browse/MODLOGSAML-91) Update vertx-pac4j to pac4j v5
 * [MODLOGSAML-104](https://issues.folio.org/browse/MODLOGSAML-104) SSO settings (configured with user property "Email") always fail to find user by email
 * [MODLOGSAML-110](https://issues.folio.org/browse/MODLOGSAML-110) /saml/validate NEP 400
 * [MODLOGSAML-122](https://issues.folio.org/browse/MODLOGSAML-122) Improve code coverage, avoid deprecated API
 * [MODLOGSAML-128](https://issues.folio.org/browse/MODLOGSAML-128) Update to RMB 33.2.2
 * [MODLOGSAML-129](https://issues.folio.org/browse/MODLOGSAML-129) Netty 4.1.72, Log4j 2.17.0, Vert.x 4.2.3, RMB 33.2.3
 * [MODLOGSAML-130](https://issues.folio.org/browse/MODLOGSAML-130) Pac4j 5.2.1, RMB 33.2.4, vertx-pac4j 6.0.0 fixing unsecure token (CVE-2021-44878)

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
