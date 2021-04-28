/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.controller.ServiceClientHelper;
import org.niis.xroad.securityserver.restapi.converter.AccessRightConverter;
import org.niis.xroad.securityserver.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.securityserver.restapi.converter.ClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ConnectionTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.LocalGroupConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.TokenCertificateConverter;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRight;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRights;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionType;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.OrphanInformation;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientType;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceType;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.securityserver.restapi.openapi.validator.ClientAddValidator;
import org.niis.xroad.securityserver.restapi.openapi.validator.LocalGroupAddValidator;
import org.niis.xroad.securityserver.restapi.openapi.validator.ServiceDescriptionAddValidator;
import org.niis.xroad.securityserver.restapi.service.AccessRightService;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.CertificateNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.securityserver.restapi.service.InvalidServiceUrlException;
import org.niis.xroad.securityserver.restapi.service.InvalidUrlException;
import org.niis.xroad.securityserver.restapi.service.LocalGroupService;
import org.niis.xroad.securityserver.restapi.service.MissingParameterException;
import org.niis.xroad.securityserver.restapi.service.OrphanRemovalService;
import org.niis.xroad.securityserver.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientService;
import org.niis.xroad.securityserver.restapi.service.ServiceDescriptionService;
import org.niis.xroad.securityserver.restapi.service.ServiceNotFoundException;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.securityserver.restapi.util.ResourceUtils;
import org.niis.xroad.securityserver.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlParser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_CERT;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_VALIDATOR_INTERRUPTED;
import static org.niis.xroad.restapi.openapi.ControllerUtil.createCreatedResponse;

/**
 * clients api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {
    private final ClientConverter clientConverter;
    private final ClientService clientService;
    private final LocalGroupConverter localGroupConverter;
    private final LocalGroupService localGroupService;
    private final TokenService tokenService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final ServiceDescriptionConverter serviceDescriptionConverter;
    private final ServiceDescriptionService serviceDescriptionService;
    private final AccessRightService accessRightService;
    private final TokenCertificateConverter tokenCertificateConverter;
    private final OrphanRemovalService orphanRemovalService;
    private final ServiceClientConverter serviceClientConverter;
    private final AccessRightConverter accessRightConverter;
    private final ServiceClientService serviceClientService;
    private final ServiceClientHelper serviceClientHelper;
    private final AuditDataHelper auditDataHelper;
    private final ServiceClientSortingComparator serviceClientSortingComparator;
    private final ClientSortingComparator clientSortingComparator;

    /**
     * Finds clients matching search terms
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @param internalSearch search only in the local clients
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> findClients(String name, String instance, String memberClass,
            String memberCode, String subsystemCode, Boolean showMembers, Boolean internalSearch,
            Boolean localValidSignCert, Boolean excludeLocal) {
        boolean unboxedShowMembers = Boolean.TRUE.equals(showMembers);
        boolean unboxedInternalSearch = Boolean.TRUE.equals(internalSearch);
        List<Client> clients = clientConverter.convert(clientService.findClients(name,
                instance, memberClass, memberCode, subsystemCode, unboxedShowMembers, unboxedInternalSearch,
                localValidSignCert, excludeLocal));
        Collections.sort(clients, clientSortingComparator);
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<Client> getClient(String id) {
        ClientType clientType = getClientType(id);
        Client client = clientConverter.convert(clientType);
        return new ResponseEntity<>(client, HttpStatus.OK);
    }

    /**
     * Read one client from DB
     * @param encodedId id that is encoded with the <INSTANCE>:<MEMBER_CLASS>:....
     * encoding
     * @return
     * @throws ResourceNotFoundException if client does not exist
     * @throws BadRequestException if encodedId was not proper encoded client ID
     */
    private ClientType getClientType(String encodedId) {
        ClientId clientId = clientConverter.convertId(encodedId);
        ClientType clientType = clientService.getLocalClient(clientId);
        if (clientType == null) {
            throw new ResourceNotFoundException("client with id " + encodedId + " not found");
        }
        return clientType;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<List<TokenCertificate>> getClientSignCertificates(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<CertificateInfo> certificateInfos = tokenService.getSignCertificates(clientType);
        List<TokenCertificate> certificates = tokenCertificateConverter.convert(certificateInfos);
        return new ResponseEntity<>(certificates, HttpStatus.OK);
    }

    /**
     * Update a client's connection type
     * @param encodedId
     * @param connectionTypeWrapper wrapper object containing the connection type to set
     * @return
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.SET_CONNECTION_TYPE)
    public ResponseEntity<Client> updateClient(String encodedId, ConnectionTypeWrapper connectionTypeWrapper) {
        if (connectionTypeWrapper == null || connectionTypeWrapper.getConnectionType() == null) {
            throw new BadRequestException();
        }
        ConnectionType connectionType = connectionTypeWrapper.getConnectionType();
        ClientId clientId = clientConverter.convertId(encodedId);
        String connectionTypeString = ConnectionTypeMapping.map(connectionType).get().name();
        ClientType changed = null;
        try {
            changed = clientService.updateConnectionType(clientId, connectionTypeString);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        Client result = clientConverter.convert(changed);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_CLIENT_INTERNAL_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_CLIENT_INTERNAL_CERT)
    public ResponseEntity<CertificateDetails> addClientTlsCertificate(String encodedId,
            Resource body) {
        // there's no filename since we only get a binary application/octet-stream.
        // Have audit log anyway (null behaves as no-op) in case different content type is added later
        String filename = body.getFilename();
        auditDataHelper.put(RestApiAuditProperty.UPLOAD_FILE_NAME, filename);

        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(body);
        ClientId clientId = clientConverter.convertId(encodedId);
        CertificateType certificateType = null;
        try {
            certificateType = clientService.addTlsCertificate(clientId, certificateBytes);
        } catch (CertificateException c) {
            throw new BadRequestException(c, new ErrorDeviation(ERROR_INVALID_CERT));
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (CertificateAlreadyExistsException e) {
            throw new ConflictException(e);
        }
        CertificateDetails certificateDetails = certificateDetailsConverter.convert(certificateType);
        return ControllerUtil
                .createCreatedResponse("/api/clients/{id}/tls-certificates/{hash}", certificateDetails, encodedId,
                certificateDetails.getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT_INTERNAL_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_CLIENT_INTERNAL_CERT)
    public ResponseEntity<Void> deleteClientTlsCertificate(String encodedId, String hash) {
        ClientId clientId = clientConverter.convertId(encodedId);
        try {
            clientService.deleteTlsCertificate(clientId, hash);
        } catch (ClientNotFoundException | CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERT_DETAILS')")
    public ResponseEntity<CertificateDetails> getClientTlsCertificate(String encodedId, String certHash) {
        ClientId clientId = clientConverter.convertId(encodedId);
        Optional<CertificateType> certificateType = null;
        try {
            certificateType = clientService.getTlsCertificate(clientId, certHash);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        if (!certificateType.isPresent()) {
            throw new ResourceNotFoundException("certificate with hash " + certHash
                    + ", client id " + encodedId + " not found");
        }
        return new ResponseEntity<>(certificateDetailsConverter.convert(certificateType.get()), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERTS')")
    public ResponseEntity<List<CertificateDetails>> getClientTlsCertificates(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<CertificateDetails> certificates = clientService.getLocalClientIsCerts(clientType.getIdentifier())
                .stream()
                .map(certificateDetailsConverter::convert)
                .collect(toList());
        return new ResponseEntity<>(certificates, HttpStatus.OK);
    }

    @InitBinder("localGroupAdd")
    @PreAuthorize("permitAll()")
    protected void initLocalGroupAddBinder(WebDataBinder binder) {
        binder.addValidators(new LocalGroupAddValidator());
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_LOCAL_GROUP')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_LOCAL_GROUP)
    public ResponseEntity<LocalGroup> addClientLocalGroup(String id, LocalGroupAdd localGroupAdd) {
        ClientType clientType = getClientType(id);
        LocalGroupType localGroupType = null;
        try {
            localGroupType = localGroupService.addLocalGroup(clientType.getIdentifier(),
                    localGroupConverter.convert(localGroupAdd));
        } catch (LocalGroupService.DuplicateLocalGroupCodeException e) {
            throw new ConflictException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        LocalGroup createdGroup = localGroupConverter.convert(localGroupType);
        return ControllerUtil.createCreatedResponse("/api/local-groups/{id}", createdGroup, localGroupType.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<List<LocalGroup>> getClientLocalGroups(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<LocalGroupType> localGroupTypes = clientService.getLocalClientLocalGroups(clientType.getIdentifier());
        List<LocalGroup> localGroups = localGroupConverter.convert(localGroupTypes);
        localGroups.sort(Comparator.comparing(LocalGroup::getCode, String.CASE_INSENSITIVE_ORDER));
        return new ResponseEntity<>(localGroups, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<List<ServiceDescription>> getClientServiceDescriptions(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<ServiceDescription> serviceDescriptions = serviceDescriptionConverter.convert(
                clientService.getLocalClientServiceDescriptions(clientType.getIdentifier()));

        return new ResponseEntity<>(serviceDescriptions, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADD_WSDL', 'ADD_OPENAPI3')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_SERVICE_DESCRIPTION)
    public ResponseEntity<ServiceDescription> addClientServiceDescription(String id,
            ServiceDescriptionAdd serviceDescription) {
        ClientId clientId = clientConverter.convertId(id);
        String url = serviceDescription.getUrl();
        boolean ignoreWarnings = serviceDescription.getIgnoreWarnings();
        String restServiceCode = serviceDescription.getRestServiceCode();

        // audit logging from controller works better here since logic is split into 3 different methods
        auditDataHelper.put(clientId);
        auditDataHelper.putServiceDescriptionUrl(url, ServiceTypeMapping.map(serviceDescription.getType()).get());

        ServiceDescriptionType addedServiceDescriptionType = null;
        if (serviceDescription.getType() == ServiceType.WSDL) {
            try {
                addedServiceDescriptionType = serviceDescriptionService.addWsdlServiceDescription(
                        clientId, url, ignoreWarnings);
            } catch (WsdlParser.WsdlNotFoundException | UnhandledWarningsException | InvalidUrlException
                    | InvalidWsdlException | InvalidServiceUrlException e) {
                // deviation data (errorcode + warnings) copied
                throw new BadRequestException(e);
            } catch (ClientNotFoundException e) {
                // deviation data (errorcode + warnings) copied
                throw new ResourceNotFoundException(e);
            } catch (ServiceDescriptionService.ServiceAlreadyExistsException
                    | ServiceDescriptionService.WsdlUrlAlreadyExistsException e) {
                // deviation data (errorcode + warnings) copied
                throw new ConflictException(e);
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(new ErrorDeviation(ERROR_WSDL_VALIDATOR_INTERRUPTED));
            }
        } else if (serviceDescription.getType() == ServiceType.OPENAPI3) {
            try {
                addedServiceDescriptionType = serviceDescriptionService.addOpenApi3ServiceDescription(clientId, url,
                        restServiceCode, ignoreWarnings);
            } catch (OpenApiParser.ParsingException | UnhandledWarningsException | MissingParameterException
                    | InvalidUrlException e) {
                throw new BadRequestException(e);
            } catch (ClientNotFoundException e) {
                throw new ResourceNotFoundException(e);
            } catch (ServiceDescriptionService.UrlAlreadyExistsException
                    | ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
                throw new ConflictException(e);
            }
        } else if (serviceDescription.getType() == ServiceType.REST) {
            try {
                addedServiceDescriptionType = serviceDescriptionService.addRestEndpointServiceDescription(clientId,
                        url, restServiceCode);
            } catch (ClientNotFoundException e) {
                throw new ResourceNotFoundException(e);
            } catch (MissingParameterException | InvalidUrlException e) {
                throw new BadRequestException(e);
            } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException
                    | ServiceDescriptionService.UrlAlreadyExistsException e) {
                throw new ConflictException(e);
            }
        }
        ServiceDescription addedServiceDescription = serviceDescriptionConverter.convert(
                addedServiceDescriptionType);

        auditDataHelper.put(RestApiAuditProperty.DISABLED, addedServiceDescription.getDisabled());
        auditDataHelper.putDateTime(RestApiAuditProperty.REFRESHED_DATE, addedServiceDescription.getRefreshedAt());

        return createCreatedResponse("/api/service-descriptions/{id}", addedServiceDescription,
                addedServiceDescription.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<List<ServiceClient>> findServiceClientCandidates(String encodedClientId,
            String memberNameOrGroupDescription,
            ServiceClientType serviceClientType, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        XRoadObjectType xRoadObjectType = ServiceClientTypeMapping.map(serviceClientType).orElse(null);
        List<ServiceClientDto> serviceClientDtos = null;
        try {
            serviceClientDtos = accessRightService.findAccessRightHolderCandidates(clientId,
                    memberNameOrGroupDescription, xRoadObjectType, instance, memberClass, memberGroupCode,
                    subsystemCode);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        List<ServiceClient> serviceClients = serviceClientConverter.convertServiceClientDtos(serviceClientDtos);
        Collections.sort(serviceClients, serviceClientSortingComparator);
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }

    @InitBinder("clientAdd")
    @PreAuthorize("permitAll()")
    protected void initClientAddBinder(WebDataBinder binder) {
        binder.addValidators(new ClientAddValidator());
    }

    @InitBinder("serviceDescriptionAdd")
    @PreAuthorize("permitAll()")
    protected void initServiceDescriptionAddBinder(WebDataBinder binder) {
        binder.addValidators(new ServiceDescriptionAddValidator());
    }

    /**
     * This method is synchronized (like client add in old Ruby implementation)
     * to prevent a problem with two threads both creating "first" additional members.
     */
    @Override
    @PreAuthorize("hasAuthority('ADD_CLIENT')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_CLIENT)
    public synchronized ResponseEntity<Client> addClient(ClientAdd clientAdd) {
        boolean ignoreWarnings = clientAdd.getIgnoreWarnings();
        IsAuthentication isAuthentication = null;

        try {
            isAuthentication = ConnectionTypeMapping.map(clientAdd.getClient().getConnectionType()).get();
        } catch (Exception e) {
            throw new BadRequestException("bad connection type parameter", e);
        }
        ClientType added = null;
        try {
            added = clientService.addLocalClient(clientAdd.getClient().getMemberClass(),
                    clientAdd.getClient().getMemberCode(),
                    clientAdd.getClient().getSubsystemCode(),
                    isAuthentication, ignoreWarnings);
        } catch (ClientService.ClientAlreadyExistsException
                | ClientService.AdditionalMemberAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (UnhandledWarningsException | ClientService.InvalidMemberClassException e) {
            throw new BadRequestException(e);
        }
        Client result = clientConverter.convert(added);

        return createCreatedResponse("/api/clients/{id}", result, result.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_CLIENT)
    public ResponseEntity<Void> deleteClient(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        try {
            clientService.deleteLocalClient(clientId);
        } catch (ActionNotPossibleException | ClientService.CannotDeleteOwnerException e) {
            throw new ConflictException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_ORPHANS)
    public ResponseEntity<Void> deleteOrphans(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        try {
            orphanRemovalService.deleteOrphans(clientId);
        } catch (OrphanRemovalService.OrphansNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    public ResponseEntity<OrphanInformation> getClientOrphans(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        boolean orphansExist = orphanRemovalService.orphansExist(clientId);
        if (orphansExist) {
            OrphanInformation info = new OrphanInformation().orphansExist(true);
            return new ResponseEntity<>(info, HttpStatus.OK);
        } else {
            throw new ResourceNotFoundException();
        }
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_CLIENT_REG_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.REGISTER_CLIENT)
    public ResponseEntity<Void> registerClient(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        try {
            clientService.registerClient(clientId);
        } catch (ClientService.CannotRegisterOwnerException
                | ClientService.InvalidMemberClassException | ClientService.InvalidInstanceIdentifierException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_CLIENT_DEL_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.UNREGISTER_CLIENT)
    public ResponseEntity<Void> unregisterClient(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        try {
            clientService.unregisterClient(clientId);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException
                | ClientService.CannotUnregisterOwnerException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_OWNER_CHANGE_REQ')")
    @AuditEventMethod(event = RestApiAuditEvent.SEND_OWNER_CHANGE_REQ)
    public ResponseEntity<Void> changeOwner(String encodedClientId) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        try {
            clientService.changeOwner(clientId.getMemberClass(), clientId.getMemberCode(),
                    clientId.getSubsystemCode());
        } catch (ClientService.MemberAlreadyOwnerException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<List<ServiceClient>> getClientServiceClients(String id) {
        ClientId clientId = clientConverter.convertId(id);
        List<ServiceClient> serviceClients = null;
        try {
            serviceClients = serviceClientConverter.
                    convertServiceClientDtos(serviceClientService.getServiceClientsByClient(clientId));
            Collections.sort(serviceClients, serviceClientSortingComparator);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<ServiceClient> getServiceClient(String id, String scId) {
        ClientId clientIdentifier = clientConverter.convertId(id);
        ServiceClient serviceClient = null;
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(scId);
            serviceClient = serviceClientConverter.convertServiceClientDto(
                    serviceClientService.getServiceClient(clientIdentifier, serviceClientId));
        } catch (ClientNotFoundException | ServiceClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }

        return new ResponseEntity<>(serviceClient, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ACL_SUBJECT_OPEN_SERVICES')")
    public ResponseEntity<List<AccessRight>> getServiceClientAccessRights(String id, String scId) {
        ClientId clientIdentifier = clientConverter.convertId(id);
        List<AccessRight> accessRights = null;
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(scId);
            accessRights = accessRightConverter.convert(
                    serviceClientService.getServiceClientAccessRights(clientIdentifier, serviceClientId));
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(accessRights, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ACL_SUBJECT_OPEN_SERVICES')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_SERVICE_CLIENT_ACCESS_RIGHTS)
    public ResponseEntity<List<AccessRight>> addServiceClientAccessRights(String encodedClientId,
            String endcodedServiceClientId, AccessRights accessRights) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        Set<String> serviceCodes = getServiceCodes(accessRights);
        List<ServiceClientAccessRightDto> accessRightTypes = null;
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(endcodedServiceClientId);
            accessRightTypes = accessRightService.addServiceClientAccessRights(clientId, serviceCodes, serviceClientId);
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.DuplicateAccessRightException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(accessRightConverter.convert(accessRightTypes), HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ACL_SUBJECT_OPEN_SERVICES')")
    @AuditEventMethod(event = RestApiAuditEvent.REMOVE_SERVICE_CLIENT_ACCESS_RIGHTS)
    public ResponseEntity<Void> deleteServiceClientAccessRights(String encodedClientId,
            String endcodedServiceClientId, AccessRights accessRights) {
        ClientId clientId = clientConverter.convertId(encodedClientId);
        Set<String> serviceCodes = getServiceCodes(accessRights);
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(endcodedServiceClientId);
            accessRightService.deleteServiceClientAccessRights(clientId, serviceCodes, serviceClientId);
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.AccessRightNotFoundException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private Set<String> getServiceCodes(AccessRights accessRights) {
        Set<String> serviceCodes = new HashSet<>();
        for (AccessRight accessRight : accessRights.getItems()) {
            serviceCodes.add(accessRight.getServiceCode());
        }
        return serviceCodes;
    }
}
