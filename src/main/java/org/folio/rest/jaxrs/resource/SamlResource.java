
package org.folio.rest.jaxrs.resource;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import io.vertx.core.Context;
import org.folio.rest.annotations.Validate;

@Path("saml")
public interface SamlResource {


    /**
     * Regenerate the metadata XML
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     */
    @GET
    @Path("regenerate")
    @Produces({
        "application/xml",
        "text/plain"
    })
    @Validate
    void getSamlRegenerate(java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Handles the login with sending a form or a redirect as a response
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     */
    @GET
    @Path("login")
    @Produces({
        "text/html",
        "text/plain"
    })
    @Validate
    void getSamlLogin(java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Send callback to OKAPI after SSO authentication
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     */
    @POST
    @Path("callback")
    @Produces({
        "text/html",
        "text/plain"
    })
    @Validate
    void postSamlCallback(java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Decides if SSO login is configured properly, returns true or false
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     */
    @GET
    @Path("check")
    @Produces({
        "text/html",
        "text/plain"
    })
    @Validate
    void getSamlCheck(java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class GetSamlCheckResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetSamlCheckResponse(Response delegate) {
            super(delegate);
        }

        /**
         *  e.g. true
         * 
         * @param entity
         *     true
         */
        public static SamlResource.GetSamlCheckResponse withPlainOK(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlCheckResponse(responseBuilder.build());
        }

        /**
         * Module is not deployed e.g. Module is not deployed
         * 
         * @param entity
         *     Module is not deployed
         */
        public static SamlResource.GetSamlCheckResponse withHtmlNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/html");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlCheckResponse(responseBuilder.build());
        }

        /**
         * Internal server error e.g. Internal server error
         * 
         * @param entity
         *     Internal server error
         */
        public static SamlResource.GetSamlCheckResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlCheckResponse(responseBuilder.build());
        }

    }

    public class GetSamlLoginResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetSamlLoginResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Return with HTML page in case POST_BINDING is used
         * 
         * @param entity
         *     
         */
        public static SamlResource.GetSamlLoginResponse withHtmlOK(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "text/html");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlLoginResponse(responseBuilder.build());
        }

        /**
         * Redirect in case REDIRECT_BINDING is used e.g. http://localhost:9130
         * 
         * @param entity
         *     http://localhost:9130
         */
        public static SamlResource.GetSamlLoginResponse withPlainMovedTemporarily(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(302).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlLoginResponse(responseBuilder.build());
        }

        /**
         * Internal server error e.g. Internal server error
         * 
         * @param entity
         *     Internal server error
         */
        public static SamlResource.GetSamlLoginResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlLoginResponse(responseBuilder.build());
        }

    }

    public class GetSamlRegenerateResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetSamlRegenerateResponse(Response delegate) {
            super(delegate);
        }

        /**
         * 
         * @param entity
         *     
         */
        public static SamlResource.GetSamlRegenerateResponse withXmlOK(StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/xml");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlRegenerateResponse(responseBuilder.build());
        }

        /**
         * Internal server error e.g. Internal server error
         * 
         * @param entity
         *     Internal server error
         */
        public static SamlResource.GetSamlRegenerateResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.GetSamlRegenerateResponse(responseBuilder.build());
        }

    }

    public class PostSamlCallbackResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostSamlCallbackResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Generate JWT token and set cookie
         * 
         * @param entity
         *     
         */
        public static SamlResource.PostSamlCallbackResponse withPlainOK(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new SamlResource.PostSamlCallbackResponse(responseBuilder.build());
        }

        /**
         * Unauthorized e.g. Unauthorized
         * 
         * @param entity
         *     Unauthorized
         */
        public static SamlResource.PostSamlCallbackResponse withHtmlUnauthorized(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(401).header("Content-Type", "text/html");
            responseBuilder.entity(entity);
            return new SamlResource.PostSamlCallbackResponse(responseBuilder.build());
        }

        /**
         * Unauthorized e.g. Unauthorized
         * 
         * @param entity
         *     Unauthorized
         */
        public static SamlResource.PostSamlCallbackResponse withHtmlForbidden(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(403).header("Content-Type", "text/html");
            responseBuilder.entity(entity);
            return new SamlResource.PostSamlCallbackResponse(responseBuilder.build());
        }

        /**
         * Internal server error e.g. Internal server error
         * 
         * @param entity
         *     Internal server error
         */
        public static SamlResource.PostSamlCallbackResponse withHtmlInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/html");
            responseBuilder.entity(entity);
            return new SamlResource.PostSamlCallbackResponse(responseBuilder.build());
        }

    }

}
