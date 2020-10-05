## 2.1.0 - Unreleased

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
 * MODLOGSAML-53 Use JVM features to manage container memory
 * MODLOGSAML-51 Fix com.fasterxml.jackson.core:jackson-databind vulnerability

## 1.2.2 - 2019-08-01
 * MODLOGSAML-45 Fix security vulnerabilities reported in
   jackson-databind >= 2.0.0, < 2.9.9.1
 * MODLOGSAML-40 api fails to validate idpurl if the content type
   contains charset (MODLOGSAML-46 dup)

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
