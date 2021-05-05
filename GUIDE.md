# mod-login-saml documentation

## Terms

### SAML
Security Assertion Markup Language is an open standard for exchanging authentication and authorization data between parties, in particular, between an identity provider and a service provider. This module implements the 2.0 version of SAML standard. This module sometimes referred as SAML Client, and identity provider (or IdP) is referred as a SAML Server.

### IdP
Identity Provider. It is an authentication server that issues SAML that issues authentication assertions. It has a login page to where client applications can forward the users. It is operated by the client organization or a 3rd party. Typical implementation is [Shibboleth](https://www.shibboleth.net/)

### Service Provider
A SAML service provider is a system entity that receives and accepts an authentication assertion issued by a SAML identity provider. In this case FOLIO itself (with mod-login-saml enabled) is a service provider.

### IdP metadata
An XML file that contains metadata about the Identity Provider, like signing and encryption keys, bindings, formats, etc. This is the starting point to configure every Service Provider.

Examples:

* [SSOCircle IdP metadata](https://idp.ssocircle.com)
* [OpenIdP metadata](https://openidp.feide.no/simplesaml/saml2/idp/metadata.php)

### SP metadata

An XML file that describes the Service Point's configuration like successful login callback URL, and the encryption keys.

### SAML binding

Method to communicating with IdP. Different IdPs support different binding types. mod-login-saml supports POST Binding (wehere data sent as POST request body) and REDIRECT Binding (where the browser got redirected to the IdP, and data sent as base64 encoded query parameters).

### SAML attribute

IdPs provides different attributes of the logged in user. The most common are UserID and Email. We can use this parameter to match the user in FOLIO to the user coming from IdP.

### User property

Which FOLIO user attribute we want to match with SAML attribute. For example ExternalSystemId, Email, Barcode, etc.

## Configuration

1. Open SSO configuration page (Settings > Organization > SSO Settings)
2. Fill the form fields:
 * IdP url: the full url of the **idp-metadata.xml** file. If the URL starts with https then only valid (not self-signed) certificates will work.
 * SAML binding type: both redirect and POST methods supported, choose what suits for the IdP.
 * SAML attribute: choose which parameter sent by the IdP will be used for matching.
 * User property: choose which user property - in local user db - will be used for matching.
3. Save the configuration.
4. Download sp-metadata.xml with "Download metadata" button.
5. Configure IdP to work with our installation by uploading sp-metadata.xml. This process is out of the scope of this document.
6. Logout and refresh browser window. If everything went well then SSO Login button will appear below the normal login button. Clicking on it the user will be redirected to IdP's login page. If the user has already been logged in then he or she will be simply redirected back to FOLIO UI.

## Testing

### SSOCircle

In this example we will describe how to use SSOCircle public SAML test service.

1. Register an account on [SSOCircle](https://www.ssocircle.com).
2. Configure and save SSO settings in FOLIO:
 * Identity Provider URL: https://idp.ssocircle.com
 * SAML binding: POST binding
 * SAML attribute: UserID
 * User property: External System ID
3. After save download sp-metadata.xml by clicking on Download metadata button. Open downloaded file in a text editor and copy its content to clipboard.
4. Configure SSOCircle: Manage Metadata > Add new Service Provider
 * FQDN: http://localhost:9130 (okapi endpoint)
 * Attributes: check UserID at least
 * Insert SAML metadata (paste previously copied sp-metadata.xml content)
 * On top of the page you can check your UserID (that will be sent in the assertion) then submit this configuration.
5. On FOLIO side, make sure that there is a user in the system with externalSystemId param set to whatever the UserID is in SSOCircle. For example you can set the externalSystemId of the administrator (diku\_admin) account.
6. Logout, refresh browser, SSO Login button will appear below normal login button.
7. Click on SSO Login button. Log in to SSOCircle. If the user was previously logged in, a recaptcha verification is still needed (which is an SSOCircle speciality) then click on Continue SAML Single Sign On button. This redirects us back to FOLIO.
8. SSO landing page will be displayed. In a couple of seconds, it will redirect us to the main landing page.

Note: the Single Sign *Out* functionality is not part of mod-login-saml module.

### samltest.id

The samltest.id site doesn't require an account to configure a FOLIO installation.

* SAML Binding: Redirect Binding
* SAML attribute: uid
* User property: username
* Identity provider URL: https://samltest.id/saml/idp

Save the settings, then click the Download metadata button to retrieve sp-metadata.xml. Upload this file at https://samltest.id/upload.php

The three hardcoded logins that samltest.id provides are:

```
rick    psych
morty   panic
sheldon bazinga
```

They are mod-users sample data and get automatically installed at FOLIO demo and tests sites if sample data is enabled.

