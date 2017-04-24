package fi.csc.idp.stepup.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.shibboleth.idp.profile.ActionTestingSupport;
import net.shibboleth.idp.profile.RequestContextBuilder;

import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;

import fi.csc.idp.stepup.api.OidcProcessingEventIds;
import fi.csc.idp.stepup.api.OidcStepUpContext;

public class TestVerifyClientRedirectUriOfOidcAuthenticationRequest {

    private VerifyClientRedirectUriOfOidcAuthenticationRequest action;

    protected RequestContext src;

    @BeforeMethod
    public void setUp() throws Exception {
        src = new RequestContextBuilder().buildRequestContext();
        action = new VerifyClientRedirectUriOfOidcAuthenticationRequest();

    }

    /**
     * Test that action copes with no issuer set.
     */
    //@Test
    public void testNoIssuer() {
        final Event event = action.execute(src);
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EXCEPTION);
    }

    /**
     * Test that action copes with no context set.
     */
    //@Test
    public void testNoCtx() {
        Map<String, List<String>> uris = new HashMap<String, List<String>>();
        List<String> value = new ArrayList<String>();
        value.add("foobaar");
        uris.put("key", value);
        action.setRedirectUris(uris);
        final Event event = action.execute(src);
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EXCEPTION);
    }

    /**
     * Test that action copes with no request set.
     */
    //@Test
    public void testNoRequest() {
        Map<String, List<String>> uris = new HashMap<String, List<String>>();
        List<String> value = new ArrayList<String>();
        value.add("foobaar");
        uris.put("key", value);
        OidcStepUpContext oidcCtx = new OidcStepUpContext();
        src.getConversationScope().put(OidcStepUpContext.getContextKey(), oidcCtx);
        action.setRedirectUris(uris);
        final Event event = action.execute(src);
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EXCEPTION);
    }

    /**
     * Test that action copes with no client id registered.
     */
    //@Test
    public void testClientIdRegistered() throws URISyntaxException {
        Map<String, List<String>> uris = new HashMap<String, List<String>>();
        List<String> value = new ArrayList<String>();
        value.add("http://bar");
        uris.put("foo", value);
        OidcStepUpContext oidcCtx = new OidcStepUpContext();
        src.getConversationScope().put(OidcStepUpContext.getContextKey(), oidcCtx);
        action.setRedirectUris(uris);
        ClientID clientID = new ClientID("fooO");
        URI redirectURI = new URI("http://barO");
        Scope scope = new Scope("openid");
        ResponseType rt = new ResponseType(ResponseType.Value.CODE);
        AuthenticationRequest req = new AuthenticationRequest.Builder(rt, scope, clientID, redirectURI).build();
        oidcCtx.setRequest(req);
        final Event event = action.execute(src);
        Assert.assertEquals(oidcCtx.getErrorCode(), "unauthorized_client");
        Assert.assertEquals(oidcCtx.getErrorDescription(), "client has not registered any redirect uri");
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EVENTID_ERROR_OIDC);
    }

    /**
     * Test that action copes with client not having registered correct uri.
     */
    //@Test
    public void testUriRegistered() throws URISyntaxException {
        Map<String, List<String>> uris = new HashMap<String, List<String>>();
        List<String> value = new ArrayList<String>();
        value.add("http://bar");
        uris.put("foo", value);
        OidcStepUpContext oidcCtx = new OidcStepUpContext();
        src.getConversationScope().put(OidcStepUpContext.getContextKey(), oidcCtx);
        action.setRedirectUris(uris);
        ClientID clientID = new ClientID("foo");
        URI redirectURI = new URI("http://barO");
        Scope scope = new Scope("openid");
        ResponseType rt = new ResponseType(ResponseType.Value.CODE);
        AuthenticationRequest req = new AuthenticationRequest.Builder(rt, scope, clientID, redirectURI).build();
        oidcCtx.setRequest(req);
        final Event event = action.execute(src);
        Assert.assertEquals(oidcCtx.getErrorCode(), "unauthorized_client");
        Assert.assertEquals(oidcCtx.getErrorDescription(), "client has not registered redirect uri http://barO");
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EVENTID_ERROR_OIDC);
    }

    /**
     * Test that action is able to verify registered uri
     */
    //@Test
    public void testSuccess() throws URISyntaxException {
        Map<String, List<String>> uris = new HashMap<String, List<String>>();
        List<String> value = new ArrayList<String>();
        value.add("http://bar");
        uris.put("foo", value);
        OidcStepUpContext oidcCtx = new OidcStepUpContext();
        src.getConversationScope().put(OidcStepUpContext.getContextKey(), oidcCtx);
        action.setRedirectUris(uris);
        ClientID clientID = new ClientID("foo");
        URI redirectURI = new URI("http://bar");
        Scope scope = new Scope("openid");
        ResponseType rt = new ResponseType(ResponseType.Value.CODE);
        AuthenticationRequest req = new AuthenticationRequest.Builder(rt, scope, clientID, redirectURI).build();
        oidcCtx.setRequest(req);
        final Event event = action.execute(src);
        ActionTestingSupport.assertEvent(event, OidcProcessingEventIds.EVENTID_CONTINUE_OIDC);
    }
}
