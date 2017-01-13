/*
 * The MIT License
 * Copyright (c) 2015 CSC - IT Center for Science, http://www.csc.fi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package fi.csc.idp.stepup.impl;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;

import net.shibboleth.idp.attribute.context.AttributeContext;
import net.shibboleth.idp.profile.context.RelyingPartyContext;

import org.opensaml.profile.context.ProfileRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.id.Audience;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;

import fi.csc.idp.stepup.api.OidcProcessingEventIds;
import fi.csc.idp.stepup.api.OidcStepUpContext;

import com.nimbusds.oauth2.sdk.ErrorObject;

/**
 * Forms a oidc authentication response. Implementation is not generic, assumes
 * that is responding to request specific to Haka MFA request.
 * 
 * This actions does not copy any attributes to responce etc. This is solely for
 * use case of performing specific stepup authentication and delivering the
 * response in ACR
 * 
 * 
 */

public class RespondOidcMFARequest implements org.springframework.webflow.execution.Action {

    /** Class logger. */
    @Nonnull
    private final Logger log = LoggerFactory.getLogger(RespondOidcMFARequest.class);

    /** private key used for JWT signing. */
    private PrivateKey prvKey;
    /** JWT signing algorithm. */
    private JWSAlgorithm jwsAlgorithm = JWSAlgorithm.RS256;
    /** id for the signing key. */
    private String keyID = "id";
    /** name for flowscope variable. */
    private String redirect = "redirect";
    /** token expiration in seconds. */
    private int exp = 300;
    /** list of idtoken claims copied from request back to response. */
    private List<String> tokenResponseClaims;

    /**
     * Set the list of claims copied from request to response.
     * 
     * @param claims
     *            to be copied
     */
    public void setTokenResponseClaims(List<String> claims) {
        this.tokenResponseClaims = claims;
    }

    /**
     * Set token expiration time in seconds.
     * 
     * @param expiration
     *            token expiration time.
     */
    public void setExp(int expiration) {
        this.exp = expiration;
    }

    /**
     * Redirect flowscope variable name.
     * 
     * @param name
     *            variable name.
     */
    public void setRedirectName(String name) {
        this.redirect = name;
    }

    /**
     * Set the private key used for signing. Mandatory.
     * 
     * @param key
     *            signing key
     */
    public void setPrivKey(PrivateKey key) {
        this.prvKey = key;
    }

    /**
     * Set the signing algorithm. Default is JWSAlgorithm.RS256.
     * 
     * @param algorithm
     *            for signing
     */
    public void setJwsAlgorithm(JWSAlgorithm algorithm) {
        this.jwsAlgorithm = algorithm;
    }

    /**
     * Set the id for the signing key. Default is "id".
     * 
     * @param id
     *            signing key identifier
     */
    public void setKeyID(String id) {
        this.keyID = id;
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    public Event execute(@Nonnull final RequestContext springRequestContext) throws Exception {
        log.trace("Entering");
        OidcStepUpContext oidcCtx = (OidcStepUpContext) springRequestContext.getConversationScope().get(
                OidcStepUpContext.getContextKey());
        if (oidcCtx == null) {
            log.error("oidc context missing, misconfiguration in flow");
            log.trace("Leaving");
            return new Event(this, OidcProcessingEventIds.EXCEPTION);
        }
        if (prvKey == null) {
            log.error("oidc context missing, misconfiguration in flow");
            log.trace("Leaving");
            return new Event(this, OidcProcessingEventIds.EXCEPTION);
        }
        if (oidcCtx.getErrorCode() != null) {
            AuthenticationErrorResponse resp = new AuthenticationErrorResponse(
                    oidcCtx.getRequest().getRedirectionURI(), new ErrorObject(oidcCtx.getErrorCode(),
                            oidcCtx.getErrorDescription()), oidcCtx.getRequest().getState(), oidcCtx.getRequest()
                            .getResponseMode());
            springRequestContext.getFlowScope().put(redirect, resp.toURI().toString());
        }
        AuthenticationRequest req = oidcCtx.getRequest();

        final ProfileRequestContext prc = (ProfileRequestContext) springRequestContext.getConversationScope().get(
                ProfileRequestContext.BINDING_KEY);
        if (prc == null) {
            log.error("prc context missing, misconfiguration in flow");
            log.trace("Leaving");
            return new Event(this, OidcProcessingEventIds.EXCEPTION);
        }
        final AttributeContext attributeCtx = prc.getSubcontext(RelyingPartyContext.class).getSubcontext(
                AttributeContext.class);
        if (attributeCtx == null) {
            log.error("attribute context missing, misconfiguration in flow");
            log.trace("Leaving");
            return new Event(this, OidcProcessingEventIds.EXCEPTION);
        }
        // Generate new authorization code
        AuthorizationCode code = new AuthorizationCode();
        List<Audience> aud = new ArrayList<Audience>();
        // Set the requesting client as audience
        aud.add(new Audience(req.getClientID().getValue()));
        // Set us as the Issuer
        Issuer iss = new Issuer(oidcCtx.getIssuer());
        // set exp
        Calendar calExp = Calendar.getInstance();
        calExp.add(Calendar.SECOND, (int) exp);
        // Create Token
        IDTokenClaimsSet idToken2 = new IDTokenClaimsSet(iss, new Subject(oidcCtx.getIdToken().getSubject()), aud,
                calExp.getTime(), new Date());
        // We need to copy specified claims back to response
        if (tokenResponseClaims != null) {
            for (String claim : tokenResponseClaims) {
                idToken2.setClaim(claim, oidcCtx.getIdToken().getClaim(claim));
            }
        }
        // This responce assumes we are using implicit flow
        if (oidcCtx.getRequest().getNonce() != null) {
            idToken2.setClaim("nonce", oidcCtx.getRequest().getNonce());
        }
        // We pick any ACR value from request
        // Action assumes there is only one and that has been performed
        idToken2.setACR(req.getACRValues().get(0));
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(jwsAlgorithm).keyID(keyID).build(),
                idToken2.toJWTClaimsSet());
        jwt.sign(new RSASSASigner(prvKey));
        State state = req.getState();
        AuthenticationSuccessResponse resp = new AuthenticationSuccessResponse(req.getRedirectionURI(), code, jwt,
                null, state, null, req.getResponseMode());
        log.debug("constructed response:" + resp.toURI());
        springRequestContext.getFlowScope().put(redirect, resp.toURI().toString());
        log.trace("Leaving");
        return new Event(this, OidcProcessingEventIds.EVENTID_CONTINUE_OIDC);

    }

}