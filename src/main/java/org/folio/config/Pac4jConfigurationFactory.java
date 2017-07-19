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
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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


//    final Path tenantsDir = initTenantsDir();
//    List<Client> saml2Clients = initClients(tenantsDir);

    //saml2Clients.forEach(System.out::println);

    final String baseUrl = jsonConf.getString("baseUrl");

    final Clients clients = new Clients();
//    clients.setClients(saml2Clients);

    final Config config = new Config(clients);
    // config.addAuthorizer(AUTHORIZER_ADMIN, new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
    // config.addAuthorizer(AUTHORIZER_CUSTOM, new CustomAuthorizer());
//    LOG.info("Config created " + config.toString());

    return config;
  }


  public static SAML2Client saml2Client() {

    // TODO: .jks file is optional, it will be generated if missing.

    final SAML2ClientConfiguration cfg = new SAML2ClientConfiguration("samlConfig/samlKeystore.jks",
      "pac4j-demo-passwd",
      "pac4j-demo-passwd",
      "samlConfig/ssocircle.xml"); //"https://idp.ssocircle.com/meta-idp.xml" -> let's encrypt cert "bug"

    cfg.setMaximumAuthenticationLifetime(18000);
    // cfg.setServiceProviderEntityId("http://localhost:8080/callback?client_name=SAML2Client");
    cfg.setServiceProviderMetadataPath(new File("target", "sp-metadata.xml").getAbsolutePath());
    // cfg.setDestinationBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);


    SAML2Client saml2Client = new SAML2Client(cfg);
    saml2Client.setIncludeClientNameInCallbackUrl(false); // do not append ?client_name=SAML2Client in callback
    return saml2Client;
  }

  private Path initTenantsDir() {
    Path tenantsDir = Paths.get("tenants");
    try {
      if (!Files.exists(tenantsDir)) {
        Files.createDirectories(tenantsDir);
      }

      if (Files.isDirectory(tenantsDir) && Files.isWritable(tenantsDir)) {
        return tenantsDir;
      } else {
        throw new IOException("The tenants directory is not a directory or not writable!");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot allocate tenants directory", e);
    }
  }


  // TODO: minden clientet `tenant`-nak nevezni, és kikéréskor ezt használni!
  private List<Client> initClients(Path tenantsDir) {
    try {
      List<Client> clients = Files.list(tenantsDir)
        .filter(Files::isDirectory)
        .map(tenantDir -> {
          Path idpMetadata = tenantDir.resolve("idp-metadata.xml");
          if (Files.exists(idpMetadata)) {

            final SAML2ClientConfiguration cfg = new SAML2ClientConfiguration(tenantDir.resolve("samlKeystore.jks").toString(),
              "pac4j-demo-passwd",
              "pac4j-demo-passwd",
              "https://idp.ssocircle.com/meta-idp.xml");
//              "https://www.testshib.org/metadata/testshib-providers.xml"); // idpMetadata is mandatory for instantiationg a client!
//              idpMetadata.toString()); // idpMetadata is mandatory for instantiationg a client!

            cfg.setMaximumAuthenticationLifetime(18000);
//            cfg.setServiceProviderMetadataPath(tenantDir.resolve("sp-metadata.xml").toString());


            SAML2Client saml2Client = new SAML2Client(cfg);
            saml2Client.setIncludeClientNameInCallbackUrl(false); // do not append ?client_name=SAML2Client in callback

            // TODO: create separate 'local' and 'via okapi' callback generation?
            String tenantId = tenantDir.getName(tenantDir.getNameCount() - 1).toString();
            saml2Client.setCallbackUrl("http://localhost:9130/_/invoke/tenant/" + tenantId + "/saml-callback");

            return saml2Client;

          } else {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .map(saml2Client -> (Client) saml2Client)
        .collect(Collectors.toList());

      return clients;

    } catch (IOException ex) {
      throw new IllegalStateException("Failed to initialize SAML2Clients ", ex);
    }
  }

}
