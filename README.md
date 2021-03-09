# mod-login-saml

Copyright (C) 2017-2021 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This module provides SAML2 SSO functionality for FOLIO.

### Usage

1. On Stripes UI find Settings->Organization->SSO settings, paste the IdP metadata.xml URL.
  - This configuration is stored per tenant in mod-configuration under module=LOGIN-SAML, configName=saml, code=idp.url
2. Call GET /saml/regenerate to generate keyfile with random passwords and store them in mod-configuration too.
  - Don't forget to send X-Okapi-Tenant header
  - UI button will replace this manual step
  - Response is sp-metadata.xml that needs to be uploaded to IdP's configuration.
3. Make sure there is a user stored with `externalSystemId` matches `UserID` SAML attribute.
  - These default properties can be overridden by `user.property` and `saml.attribute` configuration parameters.
  - SAML binding type can be overridden by `saml.binding` configuration property, allowed values are `POST` and `REDIRECT`
  - There will be UI for these too.
4. Go back to Stripes login page (log out obviously), 'SSO Login' button show up. Clicking on it will forward to IdP's login page.

Endpoints are documented in [RAML file](ramls/saml-login.raml)

### Environment variables

`TRUST_ALL_CERTIFICATES`: if value is `true` then HTTPS certificates not checked. This is a security issue in
production environment, use it for testing only! Default value is `false`.

## Additional information

### Other documentation

Refer to the user documentation [Guide](GUIDE.md).

For upgrading see [NEWS](NEWS.md) or [Releases](https://github.com/folio-org/mod-login-saml/releases).

This module is based on the [https://www.pac4j.org/](PAC4J) library, more authentication methods supported by PAC4J
can be added to this module if needed.

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODLOGSAML](https://issues.folio.org/browse/MODLOGSAML)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### Quick start

Compile with `mvn clean install`

Run the local stand-alone instance:

```
java -jar target/mod-login-saml-fat.jar -Dhttp.port=8081
```

### ModuleDescriptor

See the [ModuleDescriptor](descriptors/ModuleDescriptor-template.json)
for the interfaces that this module requires and provides, the permissions,
and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-login-saml).

The local API docs are available, for example:
```
http://localhost:8081/apidocs/?raml=raml/saml-login.raml
http://localhost:8081/apidocs/?raml=raml/admin.raml
etc.
```

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-login-saml).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-login-saml/).

