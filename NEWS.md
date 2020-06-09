## 2.0.0 - Released

This is a maintenance release focused on keeping dependencies up to date.  The major version change is due to the new permission requirements on APIs which were previously unrestricted.
 
[Full Changelog](https://github.com/folio-org/mod-login-saml/compare/v1.3.0...v2.0.0)
 
### Stories
 * [MODLOGSAML-64](https://issues.folio.org/browse/MODLOGSAML-64) - Upgrade to RMB v30
 * [MODLOGSAML-60](https://issues.folio.org/browse/MODLOGSAML-60) - Securing APIs by default

### Bugs
 * [MODLOGSAML-55](https://issues.folio.org/browse/MODLOGSAML-55) - org.pac4j:pac4j-saml vulnerability found in pom.xml on Nov 6, 2019

## 1.3.0 2020-03-13
 * Rely on RMB's vertx. dependencies - in particular the Postgres driver
   which has been using specific versions with session usage fix
 * Update to RMB 29.3.1 (#55)
 * MODLOGSAML-53 Use JVM features to manage container memory
 * MODLOGSAML-51 Fix com.fasterxml.jackson.core:jackson-databind vulnerability

## 1.2.2 2019-08-01
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
