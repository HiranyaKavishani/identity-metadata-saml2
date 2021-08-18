/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.idp.metadata.saml2.builder;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.core.handler.AbstractIdentityHandler;
import org.wso2.carbon.identity.idp.metadata.saml2.ConfigElements;
import org.wso2.carbon.identity.idp.metadata.saml2.IDPMetadataConstant;
import org.wso2.carbon.identity.idp.metadata.saml2.util.BuilderUtil;
import org.wso2.carbon.idp.mgt.MetadataException;

/**
 * This class defines methods that are used to convert a metadata String using saml2SSOFederatedAuthenticatedConfig.
 */
public abstract class IDPMetadataBuilder extends AbstractIdentityHandler {

    private static final Log log = LogFactory.getLog(IDPMetadataBuilder.class);

    static final long ONE_MINUTE_IN_MILLIS = 60000;

    private boolean samlMetadataSigningEnabled;

    private boolean samlAuthRequestSigningEnabled;

    public String build(FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig) throws MetadataException {

        if (log.isDebugEnabled()) {
            log.debug("Starting to build the SAML Federated Authenticator Config.");
        }
        EntityDescriptor entityDescriptor = buildEntityDescriptor(samlFederatedAuthenticatorConfig);
        IDPSSODescriptor idpSsoDesc = buildIDPSSODescriptor();
        setValidityPeriod(idpSsoDesc, samlFederatedAuthenticatorConfig);
        buildSupportedProtocol(idpSsoDesc);
        buildSingleSignOnService(idpSsoDesc, samlFederatedAuthenticatorConfig);
        String samlSsoURL = getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                IdentityApplicationConstants.Authenticator.SAML2SSO.SSO_URL).getValue();
        samlAuthRequestSigningEnabled = Boolean.parseBoolean(
                getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                        IdentityApplicationConstants.Authenticator.SAML2SSO.SAML_METADATA_AUTHN_REQUESTS_SIGNING_ENABLED
                ).getValue()
        );
        for (Property property : samlFederatedAuthenticatorConfig.getProperties()) {
            if (StringUtils.equals(samlSsoURL, property.getValue())) {
                continue; // Skip since default SSO URL has been already added.
            }
            if (StringUtils.startsWith(property.getName(), IdentityApplicationConstants.Authenticator.SAML2SSO.
                    DESTINATION_URL_PREFIX)) {

                SingleSignOnService ssoHTTPPost = BuilderUtil
                        .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SSOSERVICE_DESCRIPTOR, "");
                ssoHTTPPost.setBinding(IDPMetadataConstant.HTTP_BINDING_POST_SAML2);
                ssoHTTPPost.setLocation(property.getValue());
                idpSsoDesc.getSingleSignOnServices().add(ssoHTTPPost);

                SingleSignOnService ssoHTTPRedirect = BuilderUtil
                        .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SSOSERVICE_DESCRIPTOR, "");
                ssoHTTPRedirect.setBinding(IDPMetadataConstant.HTTP_BINDING_REDIRECT_SAML2);
                ssoHTTPRedirect.setLocation(property.getValue());
                idpSsoDesc.getSingleSignOnServices().add(ssoHTTPRedirect);
            }
        }
        buildSingleLogOutService(idpSsoDesc, samlFederatedAuthenticatorConfig);
        buildArtifactResolutionService(idpSsoDesc, samlFederatedAuthenticatorConfig);
        entityDescriptor.getRoleDescriptors().add(idpSsoDesc);
        buildKeyDescriptor(entityDescriptor);
        buildExtensions(idpSsoDesc);
        idpSsoDesc.setWantAuthnRequestsSigned(samlAuthRequestSigningEnabled);
        setSamlMetadataSigningEnabled(samlFederatedAuthenticatorConfig);
        return marshallDescriptor(entityDescriptor);
    }

    private FederatedAuthenticatorConfig getSAMLFederatedAuthenticatorConfig(IdentityProvider identityProvider) {

        for (FederatedAuthenticatorConfig config : identityProvider.getFederatedAuthenticatorConfigs()) {
            if (IdentityApplicationConstants.Authenticator.SAML2SSO.NAME.equals(config.getName())) {
                return config;
            }
        }
        return null;
    }

    private Property getFederatedAuthenticatorConfigProperty(
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig, String name) {

        Property[] properties = samlFederatedAuthenticatorConfig.getProperties();
        if (properties != null) {
            for (Property property : properties) {
                if (name != null && property != null && name.equals(property.getName())) {
                    return property;
                }
            }
        }
        return null;
    }


    protected abstract EntityDescriptor buildEntityDescriptor(
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig
    ) throws MetadataException;

    protected abstract IDPSSODescriptor buildIDPSSODescriptor() throws MetadataException;

    protected abstract void buildValidityPeriod(IDPSSODescriptor idpSsoDesc) throws MetadataException;

    protected abstract void buildSupportedProtocol(IDPSSODescriptor idpSsoDesc) throws MetadataException;

    protected abstract void buildKeyDescriptor(EntityDescriptor entityDescriptor) throws MetadataException;

    protected abstract void buildNameIdFormat(IDPSSODescriptor idpSsoDesc) throws MetadataException;

    protected abstract void buildSingleSignOnService(
            IDPSSODescriptor idpSsoDesc,
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig
    ) throws MetadataException;

    protected abstract void buildSingleLogOutService(
            IDPSSODescriptor idpSsoDesc,
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig
    ) throws MetadataException;

    protected abstract void buildArtifactResolutionService(
            IDPSSODescriptor idpSsoDesc,
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig
    ) throws MetadataException;

    protected abstract void buildExtensions(IDPSSODescriptor idpSsoDesc) throws MetadataException;

    protected abstract String marshallDescriptor(EntityDescriptor entityDescriptor) throws MetadataException;

    /**
     * Set the validity period in IDPSSODescriptor loading the value from Federated Authenticator Configuration.
     *
     * @param idpSsoDesc                       IDPSSODescriptor.
     * @param samlFederatedAuthenticatorConfig Federated Authenticator Configuration.
     * @throws MetadataException if the validity period is not set properly.
     */
    protected void setValidityPeriod(IDPSSODescriptor idpSsoDesc, FederatedAuthenticatorConfig
            samlFederatedAuthenticatorConfig) throws MetadataException {

        try {
            DateTime currentTime = new DateTime();
            String validityPeriodStr = getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                    IdentityApplicationConstants.Authenticator.SAML2SSO.SAML_METADATA_VALIDITY_PERIOD).getValue();
            if (validityPeriodStr == null) {
                throw new MetadataException("Setting validity period failed. Null value found.");
            }
            int validityPeriod = Integer.parseInt(validityPeriodStr);
            DateTime validUntil = new DateTime(currentTime.getMillis() + validityPeriod * ONE_MINUTE_IN_MILLIS);
            idpSsoDesc.setValidUntil(validUntil);
        } catch (NumberFormatException e) {
            throw new MetadataException("Setting validity period failed.", e);
        }
    }

    /**
     * Enable/disable metadata signing based on SAML Federated Authenticator Configuration.
     *
     * @param samlFederatedAuthenticatorConfig SAML Federated Authenticator Configuration.
     */
    protected void setSamlMetadataSigningEnabled(FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig) {

        samlMetadataSigningEnabled = Boolean.parseBoolean(getFederatedAuthenticatorConfigProperty(
                samlFederatedAuthenticatorConfig, IdentityApplicationConstants.Authenticator.SAML2SSO.
                        SAML_METADATA_SIGNING_ENABLED).getValue());
    }

    /**
     * Get SAML metadata signing enabled flag.
     *
     * @return SAML metadata signing enabled.
     */
    protected boolean getSamlMetadataSigningEnabled() {

        return samlMetadataSigningEnabled;
    }

    /**
     * Get the value of isWantAuthRequestSigned.
     *
     * @return Value of wantAuthnRequestSigned flag.
     */
    public boolean isWantAuthRequestSigned() {

        return samlAuthRequestSigningEnabled;
    }

    /**
     * Set the value of wantAuthnRequestSigned flag.
     *
     * @param samlAuthRequestSignedEnabled New value of the wantAuthnRequestSigned flag.
     */
    public void setWantAuthRequestSigned(boolean samlAuthRequestSignedEnabled) {

        this.samlAuthRequestSigningEnabled = samlAuthRequestSignedEnabled;
    }
}
