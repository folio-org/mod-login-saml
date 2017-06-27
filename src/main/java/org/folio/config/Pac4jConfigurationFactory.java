/*
  Copyright 2015 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.folio.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.sstore.SessionStore;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.direct.AnonymousClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;

import java.io.File;

/**
 * @author Jeremy Prime
 * @since 2.0.0
 */
public class Pac4jConfigurationFactory implements ConfigFactory {

    private static final Logger LOG = LoggerFactory.getLogger(Pac4jConfigurationFactory.class);
    public static final String AUTHORIZER_ADMIN = "admin";
    public static final String AUTHORIZER_CUSTOM = "custom";

    private final JsonObject jsonConf;
    private final Vertx vertx;
    private final SessionStore sessionStore;

    public Pac4jConfigurationFactory(final JsonObject jsonConf, final Vertx vertx, final SessionStore sessionStore) {
        this.jsonConf = jsonConf;
        this.vertx = vertx;
        this.sessionStore = sessionStore;
    }

    @Override
    public Config build(Object... parameters) {
        final String baseUrl = jsonConf.getString("baseUrl");


        final Clients clients = new Clients(baseUrl + "/callback",
                saml2Client()
//            ,new AnonymousClient()
        );

        final Config config = new Config(clients);
        config.addAuthorizer(AUTHORIZER_ADMIN, new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
//        config.addAuthorizer(AUTHORIZER_CUSTOM, new CustomAuthorizer()); // TODO
        LOG.info("Config created " + config.toString());
        return config;
    }

    public static SAML2Client saml2Client() {

        final SAML2ClientConfiguration cfg = new SAML2ClientConfiguration("samlConfig/samlKeystore.jks",
                "pac4j-demo-passwd",
                "pac4j-demo-passwd",
                "samlConfig/metadata-okta.xml");
        cfg.setMaximumAuthenticationLifetime(3600);
        cfg.setServiceProviderEntityId("http://localhost:8080/callback?client_name=SAML2Client");
        cfg.setServiceProviderMetadataPath(new File("target", "sp-metadata.xml").getAbsolutePath());
//        cfg.setDestinationBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        return new SAML2Client(cfg);
    }


}
