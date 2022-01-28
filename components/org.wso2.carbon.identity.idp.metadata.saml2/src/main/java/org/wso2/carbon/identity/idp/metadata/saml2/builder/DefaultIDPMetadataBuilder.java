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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.metadata.ArtifactResolutionService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;
import org.w3c.dom.Document;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.idp.metadata.saml2.ConfigElements;
import org.wso2.carbon.identity.idp.metadata.saml2.CryptoProvider;
import org.wso2.carbon.identity.idp.metadata.saml2.IDPMetadataConstant;
import org.wso2.carbon.identity.idp.metadata.saml2.MetadataCryptoProvider;
import org.wso2.carbon.identity.idp.metadata.saml2.util.BuilderUtil;
import org.wso2.carbon.idp.mgt.MetadataException;

import java.io.IOException;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This class builds a metadata String using a saml2SSOFederatedAuthenticatedConfig.
 */
public class DefaultIDPMetadataBuilder extends IDPMetadataBuilder {

    private static final int PRIORITY = 50;
    private static final Log log = LogFactory.getLog(DefaultIDPMetadataBuilder.class);

    @Override
    public int getPriority() {

        return PRIORITY;
    }

    public String build(FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig) throws MetadataException {

        return super.build(samlFederatedAuthenticatorConfig);
    }

    private Property getFederatedAuthenticatorConfigProperty(
            FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig, String name) {

        for (Property property : samlFederatedAuthenticatorConfig.getProperties()) {
            if (name.equals(property.getName())) {
                return property;
            }
        }
        return null;
    }

    public void buildExtensions(IDPSSODescriptor idpSsoDesc) throws MetadataException {

    }

    public EntityDescriptor buildEntityDescriptor(FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig)
            throws MetadataException {

        EntityDescriptor entityDescriptor = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.ENTITY_DESCRIPTOR, "");
        entityDescriptor.setEntityID(getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                IdentityApplicationConstants.Authenticator.SAML2SSO.IDP_ENTITY_ID).getValue());
        entityDescriptor.setNoNamespaceSchemaLocation("");
        return entityDescriptor;
    }

    public IDPSSODescriptor buildIDPSSODescriptor() throws MetadataException {

        return BuilderUtil.createSAMLObject(IDPMetadataConstant.IDP_METADATA_SAML2, ConfigElements.IDPSSO_DESCRIPTOR,
                "");
    }

    public void buildValidityPeriod(IDPSSODescriptor idpSsoDesc) throws MetadataException {

        char unit = 'h'; // Default unit is hours.
        idpSsoDesc.setValidUntil(validityPeriod(1, unit));
    }

    public void buildSupportedProtocol(IDPSSODescriptor idpSsoDesc) throws MetadataException {

        idpSsoDesc.addSupportedProtocol(IDPMetadataConstant.SUPPORTED_PROTOCOL_SAML2);
    }

    public void buildKeyDescriptor(EntityDescriptor entityDescriptor) throws MetadataException {

        CryptoProvider cryptoProvider = new MetadataCryptoProvider();
        cryptoProvider.signMetadata(entityDescriptor);
    }

    public String marshallDescriptor(EntityDescriptor entityDescriptor) throws MetadataException {

        DocumentBuilderFactory factory = IdentityUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MetadataException("Error while creating the document.", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Marshalling the metadata element contents");
        }
        Document document = builder.newDocument();
        Marshaller out = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(entityDescriptor);
        CryptoProvider cryptoProvider;
        Signature signature = null;
        if (getSamlMetadataSigningEnabled()) {
            cryptoProvider = new MetadataCryptoProvider();
            signature = ((MetadataCryptoProvider) cryptoProvider).getSignature(entityDescriptor);
        }

        try {
            out.marshall(entityDescriptor, document);
            if (signature != null) {
                Signer.signObject(signature);
            }
        } catch (MarshallingException e) {
            throw new MetadataException("Error while marshalling the descriptor.", e);
        } catch (SignatureException e) {
            throw new MetadataException("Error while signing the descriptor.", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Marshalling metadata completed.");
        }
        org.apache.xml.security.Init.init();

        Transformer transformer;
        StreamResult streamResult;
        StringWriter stringWriter = new StringWriter();
        try {
            transformer = TransformerFactory.newInstance().newTransformer();

            streamResult = new StreamResult(stringWriter);
            DOMSource source = new DOMSource(document);
            transformer.transform(source, streamResult);
            stringWriter.close();
            return stringWriter.toString();
        } catch (TransformerException | IOException e) {
            log.error("Error Occurred while creating XML transformer", e);
        }

        return stringWriter.toString();
    }

    public void buildNameIdFormat(IDPSSODescriptor idpSsoDesc) throws MetadataException {

        NameIDFormat nameIdFormat = BuilderUtil.createSAMLObject(ConfigElements.FED_METADATA_NS,
                ConfigElements.NAMEID_FORMAT, "");
        nameIdFormat.setFormat(IDPMetadataConstant.NAME_FORMAT_ID_SAML);
        idpSsoDesc.getNameIDFormats().add(nameIdFormat);
    }

    public void buildSingleSignOnService(IDPSSODescriptor idpSsoDesc,
                                         FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig)
            throws MetadataException {

        SingleSignOnService ssoHTTPPost = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SSOSERVICE_DESCRIPTOR, "");
        ssoHTTPPost.setBinding(IDPMetadataConstant.HTTP_BINDING_POST_SAML2);
        ssoHTTPPost.setLocation(
                getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                        IdentityApplicationConstants.Authenticator.SAML2SSO.SSO_URL).getValue());
        idpSsoDesc.getSingleSignOnServices().add(ssoHTTPPost);

        SingleSignOnService ssoHTTPRedirect = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SSOSERVICE_DESCRIPTOR, "");
        ssoHTTPRedirect.setBinding(IDPMetadataConstant.HTTP_BINDING_REDIRECT_SAML2);
        ssoHTTPRedirect.setLocation(
                getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                        IdentityApplicationConstants.Authenticator.SAML2SSO.SSO_URL).getValue());
        idpSsoDesc.getSingleSignOnServices().add(ssoHTTPRedirect);

        SingleSignOnService ssoSOAP = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SSOSERVICE_DESCRIPTOR, "");
        ssoSOAP.setBinding(IDPMetadataConstant.SOAP_BINDING_SAML2);
        ssoSOAP.setLocation(
                getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                        IdentityApplicationConstants.Authenticator.SAML2SSO.ECP_URL).getValue());
    }

    public void buildSingleLogOutService(IDPSSODescriptor idpSsoDesc,
                                         FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig)
            throws MetadataException {

        addSingleLogoutService(idpSsoDesc, samlFederatedAuthenticatorConfig, IDPMetadataConstant.SOAP_BINDING_SAML2);
        addSingleLogoutService(idpSsoDesc, samlFederatedAuthenticatorConfig,
                IDPMetadataConstant.HTTP_BINDING_POST_SAML2);
        addSingleLogoutService(idpSsoDesc, samlFederatedAuthenticatorConfig,
                IDPMetadataConstant.HTTP_BINDING_REDIRECT_SAML2);
    }

    private void addSingleLogoutService(IDPSSODescriptor idpSsoDesc,
                                        FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig, String binding)
            throws MetadataException {

        SingleLogoutService sloServiceDesc = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.SLOSERVICE_DESCRIPTOR, "");
        sloServiceDesc.setBinding(binding);
        sloServiceDesc.setLocation(getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                IdentityApplicationConstants.Authenticator.SAML2SSO.LOGOUT_REQ_URL).getValue());
        sloServiceDesc.setResponseLocation(getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                IdentityApplicationConstants.Authenticator.SAML2SSO.LOGOUT_REQ_URL).getValue());
        idpSsoDesc.getSingleLogoutServices().add(sloServiceDesc);
    }

    public void buildArtifactResolutionService(IDPSSODescriptor idpSsoDesc,
                                               FederatedAuthenticatorConfig samlFederatedAuthenticatorConfig)
            throws MetadataException {

        ArtifactResolutionService aresServiceDesc = BuilderUtil
                .createSAMLObject(ConfigElements.FED_METADATA_NS, ConfigElements.ARTIFACTRESSERVICE_DESCRIPTOR, "");
        aresServiceDesc.setBinding(IDPMetadataConstant.SOAP_BINDING_SAML2);
        aresServiceDesc.setLocation(getFederatedAuthenticatorConfigProperty(samlFederatedAuthenticatorConfig,
                IdentityApplicationConstants.Authenticator.SAML2SSO.ARTIFACT_RESOLVE_URL).getValue());
        aresServiceDesc.setIndex(1);
        idpSsoDesc.getArtifactResolutionServices().add(aresServiceDesc);
    }

    private DateTime validityPeriod(long timePeriod, char unit) {

        switch (unit) {
            case 's':
                timePeriod *= 1000; // seconds
                break;
            case 'm':
                timePeriod *= 1000 * 60; // minutes
                break;
            case 'h':
                timePeriod *= 1000 * 60 * 60; // hours
                break;
            case 'd':
                timePeriod *= 1000 * 60 * 60 * 24; // days
                break;
            default:
                timePeriod *= 1000 * 60 * 60; // Default time is hours
                break;
        }

        DateTime currentTime = new DateTime();
        DateTime validUntil = new DateTime(currentTime.getMillis() + timePeriod);

        if (log.isDebugEnabled()) {
            log.debug("Validity input : duration=" + timePeriod + " unit=" + unit + " output : calculated validUntil="
                    + validUntil);
        }

        return validUntil;
    }
}
