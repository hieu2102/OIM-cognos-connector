package org.identityconnectors.genericrest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.EmbeddedObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;
import org.identityconnectors.genericrest.GenericRESTConnector;
import org.identityconnectors.genericrest.utils.GenericRESTUtil;
import org.identityconnectors.genericrest.utils.GenericRESTConstants.CONNECTOR_OPERATION;
import org.identityconnectors.restcommon.ClientHandler;
import org.identityconnectors.restcommon.parser.spi.ParserPlugin;
import org.identityconnectors.restcommon.utils.RESTCommonUtils;
import cognos.IBMCognosPermission;
import utils.DataUtils;

import org.identityconnectors.restcommon.utils.RESTCommonConstants.HTTPOperationType;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;

@ConnectorClass(displayNameKey = "display_GenericRESTConnector", configurationClass = GenericRESTConfiguration.class, messageCatalogPaths = {
		"org/identityconnectors/genericrest/Messages" })
public class GenericRESTConnector
		implements Connector, CreateOp, UpdateOp, DeleteOp, SearchOp<String>, UpdateAttributeValuesOp {
	private static Log log = Log.getLog(GenericRESTConnector.class);
	private GenericRESTConfiguration configuration;
	private GenericRESTConnection connection;
	private Map<String, List<String>> simpleMultivaluedAttributesMap;
	private Map<String, HTTPOperationType> opTypeMap;
	private Map<String, String> relURIsMap;
	private Map<String, Map<String, String>> namedAttributeMap;
	private ParserPlugin parser;
	private Map<String, String> parserConfigParamsMap;
	private Map<String, String> objectClasstoParserConfigMap;
	private Map<String, String> customPayloadMap;
//	private String requestUrlStr;
	private String namespaceID;
	private IBMCognosPermission cognosAPI;

	public Configuration getConfiguration() {
		return this.configuration;
	}

	public void init(Configuration config) {
		log.ok("Method Entered", new Object[0]);
		this.configuration = (GenericRESTConfiguration) config;
		this.configuration.validate();
		this.connection = new GenericRESTConnection(this.configuration);
		this.initConfigMaps();
		this.initParser();
		this.namespaceID = this.configuration.getUsername().split("\\")[0];
		String username = this.configuration.getUsername().split("\\")[1];
		String password = GenericRESTUtil.decryptPassword(this.configuration.getPassword());
		StringBuilder requestUrl = (new StringBuilder(this.configuration.isSslEnabled() ? "https://" : "http://"))
				.append(this.configuration.getHost());
		if (this.configuration.getPort() > 0) {
			requestUrl.append(":").append(this.configuration.getPort());
		}
		requestUrl.append("/ibmcognos/cgi-bin/cognos.cgi");
		String endpoint = requestUrl.toString();
		this.cognosAPI = new IBMCognosPermission(endpoint);
		try {
			this.cognosAPI.quickLogon(namespaceID, username, password);
		} catch (Exception e) {
			log.error("Cognos Login failed: {0}", new Object[] { e.getMessage() });
		}

		log.ok("Method Exiting", new Object[0]);
	}

	public void dispose() {
		log.ok("Method Entered", new Object[0]);
		log.info("disposing connection", new Object[0]);
		this.connection.disposeConnection();
		log.ok("Method Exiting", new Object[0]);
	}

	public Uid create(ObjectClass oclass, Set<Attribute> attrSet, OperationOptions options) {
		log.ok("Method Entered", new Object[0]);
		String jsonMapString = null;
		Object uidValue = null;
		Set<Attribute> specialAttributeset = new HashSet();
		Set<Attribute> normalAttributeSet = new HashSet();
		if (attrSet.isEmpty()) {
			log.error("Attribute set is empty", new Object[0]);
			log.ok("Method Exiting", new Object[0]);
			throw new ConnectorException(
					this.configuration.getMessage("ex.emptyattrset", "Attribute set is empty", new Object[0]));
		} else {
			GenericRESTUtil.separateSpecialAttributeSet(oclass, attrSet, this.relURIsMap, CONNECTOR_OPERATION.CREATEOP,
					specialAttributeset, normalAttributeSet);
			log.info("special attribute set :{0}", new Object[] { specialAttributeset });
			log.info("updated normal attribute set :{0}", new Object[] { normalAttributeSet });
			jsonMapString = this.getRequestPayload(oclass, normalAttributeSet, (String) null,
					oclass.getObjectClassValue(), CONNECTOR_OPERATION.CREATEOP.toString());
			jsonMapString = this.maskPasswordinLog(jsonMapString, oclass.getObjectClassValue());
			List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.CREATEOP,
					(String) null, this.relURIsMap, this.opTypeMap, this.configuration);
			String jsonResponseString = this.executeRequest(oclass, (Uid) null, urlOpTypeList, jsonMapString,
					(String) null);
			log.info("create response :{0}", new Object[] { jsonResponseString });
			GenericRESTUtil.addJsonResourcesTagToConfigParamsMap(this.parserConfigParamsMap,
					this.objectClasstoParserConfigMap, oclass.getObjectClassValue());
			Map<String, Object> jsonResponseMap = (Map) this.parser
					.parseResponse(jsonResponseString, this.parserConfigParamsMap).get(0);
			Map<String, String> objClassNamedAttrMap = (Map) this.namedAttributeMap.get(oclass.getObjectClassValue());
			uidValue = jsonResponseMap.get(objClassNamedAttrMap.get("__UID__"));
			if (uidValue == null
					&& ((String) objClassNamedAttrMap.get("__UID__")).equals(objClassNamedAttrMap.get(Name.NAME))) {
				uidValue = AttributeUtil.getNameFromAttributes(normalAttributeSet).getNameValue();
			}

			log.info("uid:{0}", new Object[] { uidValue });
			if (uidValue != null && specialAttributeset.size() > 0) {
				try {
					this.addAttributeValues(oclass, new Uid(uidValue.toString()), specialAttributeset, options);
				} catch (Exception var13) {
					log.error("Adding special attributes failed, deleting the {0} :{1}",
							new Object[] { oclass.getObjectClassValue(), uidValue });
					log.error("Exception in createOp, {0}", new Object[] { var13 });
					this.delete(oclass, new Uid(uidValue.toString()), (OperationOptions) null);
					throw new ConnectorException(this.configuration.getMessage(
							"ex.addSpecialAttrFailed", "Adding special attributes failed, deleted the "
									+ oclass.getObjectClassValue() + " :" + uidValue,
							new Object[] { oclass.getObjectClassValue(), uidValue }));
				}
			}

			log.info("returning uid:{0}", new Object[] { uidValue });
			log.info("Method Exiting", new Object[0]);
			Uid uid = uidValue == null ? null : new Uid(uidValue.toString());
			return uid;
		}
	}

	public Uid update(ObjectClass oclass, Uid uid, Set<Attribute> attrSet, OperationOptions options) {
		log.ok("Method Entered", new Object[0]);
		Set<Attribute> currentAttrsSet = AttributeUtil.getCurrentAttributes(attrSet);
		if (currentAttrsSet != null) {
			attrSet = GenericRESTUtil.handleCurrentAttributes(attrSet, currentAttrsSet);
		}

		if (this.configuration.isEnableEmptyString()) {
			attrSet = GenericRESTUtil.handleBlankValue(attrSet);
		}

		Set<Attribute> specialAttributeset = new HashSet();
		Set<Attribute> normalAttributeSet = new HashSet();
		GenericRESTUtil.separateSpecialAttributeSet(oclass, attrSet, this.relURIsMap, CONNECTOR_OPERATION.UPDATEOP,
				specialAttributeset, normalAttributeSet);
		String uidValue = null;
		if (!normalAttributeSet.isEmpty()) {
			log.info("Updating {0} with {1}", new Object[] { uid.getUidValue(), normalAttributeSet });
			uidValue = this.executeUpdate(oclass, uid, normalAttributeSet);
		}

		uidValue = uidValue != null ? uidValue : uid.getUidValue();
		log.info("special attribute set :{0}", new Object[] { specialAttributeset });
		if (specialAttributeset.size() > 0) {
			try {
				this.addAttributeValues(oclass, new Uid(uidValue.toString()), specialAttributeset, options);
			} catch (Exception var10) {
				log.error("Adding special attributes failed for uid : {0}", new Object[] { uid.getUidValue() });
				log.error("Exception in updateOp, {0}", new Object[] { var10 });
				throw new ConnectorException(this.configuration.getMessage("ex.addSpecialAttrFailed",
						"Adding special attributes failed" + oclass.getObjectClassValue() + " :" + uidValue,
						new Object[] { oclass.getObjectClassValue(), uidValue }));
			}
		}

		log.info("returning uid:{0}", new Object[] { uidValue });
		log.ok("Method Exiting", new Object[0]);
		return new Uid(uidValue);
	}

	public Uid addAttributeValues(ObjectClass oclass, Uid uid, Set<Attribute> attrSet, OperationOptions options) {
		log.ok("Method Entered", new Object[0]);
		Set<Attribute> specialAttributeset = new HashSet();
		Set<Attribute> normalAttributeSet = new HashSet();
		if (attrSet.isEmpty()) {
			log.error("Attribute set to update is empty", new Object[0]);
			log.ok("Method Exiting", new Object[0]);
			throw new ConnectorException(
					this.configuration.getMessage("ex.emptyattrset", "Attribute set is empty", new Object[0]));
		} else {
			GenericRESTUtil.separateSpecialAttributeSet(oclass, attrSet, this.relURIsMap,
					CONNECTOR_OPERATION.ADDATTRIBUTE, specialAttributeset, normalAttributeSet);
			log.info("special attribute set :{0}", new Object[] { specialAttributeset });
			log.info("normal attribute set :{0}", new Object[] { normalAttributeSet });
			if (normalAttributeSet.size() > 0) {
				this.executeUpdate(oclass, uid, normalAttributeSet);
			}

			if (specialAttributeset.size() > 0) {
				Iterator i$ = specialAttributeset.iterator();

				while (i$.hasNext()) {
					Attribute attr = (Attribute) i$.next();
					Map<String, String> attributeHandlingMap = new HashMap();
					String specialAttrSearchKey = GenericRESTUtil
							.getSearchKey(new String[] { oclass.getObjectClassValue(), attr.getName() });
					List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass,
							CONNECTOR_OPERATION.ADDATTRIBUTE, attr.getName(), this.relURIsMap, this.opTypeMap,
							this.configuration);
					if (this.configuration.getSpecialAttributeHandling() != null) {
						attributeHandlingMap = GenericRESTUtil
								.getSpecialAttributeMap(this.configuration.getSpecialAttributeHandling());
					}

					try {
						this.addSpecialAttributeValue(oclass, uid.getUidValue(), (Map) attributeHandlingMap,
								specialAttrSearchKey, attr, urlOpTypeList, CONNECTOR_OPERATION.ADDATTRIBUTE.toString());
					} catch (Exception var13) {
						throw new ConnectorException(this.configuration.getMessage("ex.addAttrFailed",
								"add attribute operation failed" + var13, new Object[] { var13 }), var13);
					}
				}
			}

			log.ok("Method Exiting", new Object[0]);
			return uid;
		}
	}

	public Uid removeAttributeValues(ObjectClass oclass, Uid uid, Set<Attribute> attrSet, OperationOptions options) {
		log.ok("Method Entered", new Object[0]);
		Set<Attribute> specialAttributeset = new HashSet();
		Set<Attribute> normalAttributeSet = new HashSet();
		if (attrSet.isEmpty()) {
			log.error(this.configuration.getMessage("ex.emptyattrset", "emptyattrset", new Object[0]), new Object[0]);
			log.ok("Method Exiting", new Object[0]);
			throw new ConnectorException(
					this.configuration.getMessage("ex.emptyattrset", "emptyattrset", new Object[0]));
		} else {
			GenericRESTUtil.separateSpecialAttributeSet(oclass, attrSet, this.relURIsMap,
					CONNECTOR_OPERATION.REMOVEATTRIBUTE, specialAttributeset, normalAttributeSet);
			if (specialAttributeset.size() > 0) {
				Iterator i$ = specialAttributeset.iterator();

				while (i$.hasNext()) {
					Attribute attr = (Attribute) i$.next();
					List urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.REMOVEATTRIBUTE,
							attr.getName(), this.relURIsMap, this.opTypeMap, this.configuration);

					try {
						this.removeAttributeValue(oclass, uid.getUidValue(), attr, urlOpTypeList);
					} catch (Exception var11) {
						throw new ConnectorException(this.configuration.getMessage("ex.removeAttrFailed",
								"remove attribute operation failed" + var11, new Object[] { var11 }), var11);
					}
				}
			}

			log.ok("Method Exiting", new Object[0]);
			return uid;
		}
	}

	public void delete(ObjectClass oclass, Uid uid, OperationOptions options) {
		log.ok("Method Entered", new Object[0]);
		if (uid != null) {
			log.info("Deleting {0} with uid value:{1}",
					new Object[] { oclass.getObjectClassValue(), uid.getUidValue() });
			List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.DELETEOP,
					(String) null, this.relURIsMap, this.opTypeMap, this.configuration);
			String jsonResponseString = this.executeRequest(oclass, uid, urlOpTypeList, (String) null, (String) null);
			log.info("delete response :{0}", new Object[] { jsonResponseString });
			log.info("Method Exiting", new Object[0]);
		} else {
			log.ok("Method Exiting", new Object[0]);
			throw new UnknownUidException();
		}
	}

	public FilterTranslator<String> createFilterTranslator(ObjectClass oclass, OperationOptions arg1) {
		log.info("Method Entered", new Object[0]);
		log.info("Method Exiting", new Object[0]);
		return (FilterTranslator<String>) new AbstractFilterTranslator<String>() {
		};
	}

	public void executeQuery(ObjectClass oclass, String query, ResultsHandler handler, OperationOptions options) {
		log.info("Method Entered", new Object[0]);
		String[] attributesToGet = options.getAttributesToGet();
		if (attributesToGet == null) {
			log.error("Attributes to get from the target is empty.", new Object[0]);
			throw new ConnectorException(this.configuration.getMessage("ex.nullAttributesToGet",
					"Attributes to get from the target is empty.", new Object[0]));
		} else {
			List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.SEARCHOP,
					(String) null, this.relURIsMap, this.opTypeMap, this.configuration);
			Set<String> specialAttributeset = GenericRESTUtil.getSpecialAttributeSet(oclass, attributesToGet,
					this.relURIsMap, CONNECTOR_OPERATION.SEARCHOP);
			String filterSuffix = (String) options.getOptions().get("Filter Suffix");
//			get API response
			String jsonResponseString = this.executeRequest(oclass, (Uid) null, urlOpTypeList, (String) null,
					filterSuffix);
			log.info(jsonResponseString, new Object[0]);
			GenericRESTUtil.addJsonResourcesTagToConfigParamsMap(this.parserConfigParamsMap,
					this.objectClasstoParserConfigMap, oclass.getObjectClassValue());
			List jsonResponseMap = this.parser.parseResponse(jsonResponseString, this.parserConfigParamsMap);

			try {
				Iterator i$ = jsonResponseMap.iterator();

				while (i$.hasNext()) {
					Map<String, Object> entityMap = (Map) i$.next();
					this.makeConnectorObject(handler, options, oclass, entityMap, specialAttributeset);
				}
			} catch (Exception var13) {
				log.error("Exception in executeQuery, {0}", new Object[] { var13 });
				throw new ConnectorException(
						this.configuration.getMessage("ex.executeQuery", "Exception in executeQuery.", new Object[0])
								+ " " + var13.getMessage(),
						var13);
			}

			log.info("Method Exiting", new Object[0]);
		}
	}

	private void initConfigMaps() {
		log.ok("Method Entered", new Object[0]);
		log.info("initializing maps", new Object[0]);
		this.simpleMultivaluedAttributesMap = GenericRESTUtil.getSimpleMultivaluedDetails(this.configuration);
		this.opTypeMap = GenericRESTUtil.getOpTypeMap(this.configuration);
		this.relURIsMap = GenericRESTUtil.getRelURIsMap(this.configuration);
		this.namedAttributeMap = GenericRESTUtil.getNamedAttributeMap(this.configuration);
		this.parserConfigParamsMap = GenericRESTUtil.formParserConfigParamsMap(this.configuration);
		this.objectClasstoParserConfigMap = GenericRESTUtil.formObjectClasstoParserConfigMap(this.configuration);
		this.customPayloadMap = GenericRESTUtil.getCustomPayloadMap(this.configuration);
		log.ok("Method Exiting", new Object[0]);
	}

	private void initParser() {
		log.ok("Method Entered", new Object[0]);

		try {
			this.parser = ClientHandler.getParserInstance(this.configuration.getCustomParserClassName());
		} catch (Exception var2) {
			log.error("Exception in initializing parser, {0}", new Object[] { var2 });
			throw new ConnectorException(
					this.configuration.getMessage("ex.initParser", "Exception in initializing parser", new Object[0])
							+ " " + var2.getMessage(),
					var2);
		}

		log.ok("Method Exiting", new Object[0]);
	}

	private String maskPasswordinLog(String jsonMapString, String oclass) {
		String logPayload = null;
		if (StringUtil.isNotBlank(jsonMapString)) {
			if (this.configuration.getHttpHeaderContentType().equalsIgnoreCase("application/json")) {
				List<Map<String, Object>> payload = this.parser.parseResponse(jsonMapString, new HashMap());
				if (payload.size() > 0) {
					logPayload = ClientHandler.handlePasswordInLogs(this.parser, (Map) payload.get(0),
							(String) ((Map) this.namedAttributeMap.get(oclass))
									.get(OperationalAttributes.PASSWORD_NAME));
				}
			} else {
				logPayload = ClientHandler.handlePasswordInLogs(jsonMapString);
				if (jsonMapString.indexOf("##") != -1) {
					jsonMapString = jsonMapString.replaceAll("##", "");
				}
			}

			log.info("request payload :{0}", new Object[] { logPayload });
		}

		return jsonMapString;
	}

	private String executeUpdate(ObjectClass oclass, Uid uid, Set<Attribute> attrSet) {
		log.ok("Method Entered", new Object[0]);
		String jsonMapString = this.getRequestPayload(oclass, attrSet, uid.getUidValue(), oclass.getObjectClassValue(),
				CONNECTOR_OPERATION.UPDATEOP.toString());
		jsonMapString = this.maskPasswordinLog(jsonMapString, oclass.getObjectClassValue());
		List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.UPDATEOP,
				(String) null, this.relURIsMap, this.opTypeMap, this.configuration);
		String jsonResponseString = this.executeRequest(oclass, uid, urlOpTypeList, jsonMapString, (String) null);
		log.info("update response :{0}", new Object[] { jsonResponseString });
		if (jsonResponseString != null) {
			GenericRESTUtil.addJsonResourcesTagToConfigParamsMap(this.parserConfigParamsMap,
					this.objectClasstoParserConfigMap, oclass.getObjectClassValue());
			Map<String, Object> jsonResponseMap = (Map) this.parser
					.parseResponse(jsonResponseString, this.parserConfigParamsMap).get(0);
			Object returnValue = jsonResponseMap
					.get(((Map) this.namedAttributeMap.get(oclass.getObjectClassValue())).get("__UID__"));
			if (returnValue != null) {
				log.ok("Method Exiting", new Object[0]);
				return String.valueOf(returnValue);
			}
		}

		log.ok("Method Exiting", new Object[0]);
		return null;
	}

	private void addSpecialAttributeValue(ObjectClass oclass, String uid,
			Map<String, String> specialAttributeHandlingMap, String searchKey, Attribute attr,
			List<Object> urlOpTypeList, String operation) throws Exception {
		log.info("addSpecialAttributeValue of :{0}", new Object[] { uid });
		Set<Attribute> attributeSet = new HashSet();
		String attrId = null;
		String jsonMapString = null;
		String specialAttrURL = (String) urlOpTypeList.get(0);

		try {
			boolean checkForUpdateOp = false;
			if (operation.toString().equalsIgnoreCase(CONNECTOR_OPERATION.ADDATTRIBUTE.toString())
					|| operation.toString().equalsIgnoreCase(CONNECTOR_OPERATION.REMOVEATTRIBUTE.toString())) {
				checkForUpdateOp = true;
			}

			String key = GenericRESTUtil.getSearchKey(new String[] { searchKey, operation });
			boolean isSingleHandling = specialAttributeHandlingMap.get(key) != null
					&& ((String) specialAttributeHandlingMap.get(key)).contains("SINGLE");
			if (!isSingleHandling && checkForUpdateOp) {
				key = GenericRESTUtil.getSearchKey(new String[] { searchKey, CONNECTOR_OPERATION.UPDATEOP.toString() });
				isSingleHandling = specialAttributeHandlingMap.get(key) != null
						&& ((String) specialAttributeHandlingMap.get(key)).contains("SINGLE");
			}

			if (isSingleHandling) {
				List<Object> attrList = attr.getValue();
				Iterator listIterator = attrList.iterator();

				while (listIterator.hasNext()) {
					Object attributeValue = listIterator.next();
					AttributeBuilder attrBuilder = new AttributeBuilder();
					String uidAttrName = (String) ((Map) this.namedAttributeMap.get(oclass.getObjectClassValue()))
							.get("__UID__");
					if (!GenericRESTUtil.isUidPlaceHolderPresent(specialAttrURL)
							&& GenericRESTUtil.isPlaceHolderPresent(specialAttrURL)) {
						attrBuilder.setName(uidAttrName);
						attrBuilder.addValue(new Object[] { uid });
						attributeSet.add(attrBuilder.build());
						new AttributeBuilder();
						if (!(attributeValue instanceof EmbeddedObject)) {
							attrId = (String) attributeValue;
						} else {
							String objectClassWithAttr = specialAttrURL.substring(
									specialAttrURL.indexOf("$(") + "$(".length(), specialAttrURL.indexOf(")$"));
							String[] sliptArray = objectClassWithAttr.split("\\.");
							String objectClass = sliptArray[0];
							String attributeName = sliptArray[1];
							Iterator i$ = ((EmbeddedObject) attributeValue).getAttributes().iterator();

							label62: while (true) {
								while (true) {
									if (!i$.hasNext()) {
										break label62;
									}

									Attribute attrFromEmbObject = (Attribute) i$.next();
									if (((EmbeddedObject) attributeValue).getObjectClass().getObjectClassValue()
											.equals(objectClass) && attrFromEmbObject.getName().equals(attributeName)) {
										attrId = (String) attrFromEmbObject.getValue().get(0);
									} else {
										attributeSet.add(attrFromEmbObject);
									}
								}
							}
						}
					} else {
						if (attributeValue instanceof EmbeddedObject) {
							attributeSet.addAll(((EmbeddedObject) attributeValue).getAttributes());
						} else {
							attrBuilder.setName(attr.getName());
							attrBuilder.addValue(new Object[] { attributeValue });
							attributeSet.add(attrBuilder.build());
						}

						if (!GenericRESTUtil.isPlaceHolderPresent(specialAttrURL)) {
							;
						}
					}

					jsonMapString = this.getRequestPayload(oclass, attributeSet, uid, searchKey, operation);
					this.executeSpecialAttribute(oclass.getObjectClassValue(), attr.getName(), uid, attrId,
							jsonMapString, specialAttrURL, (HTTPOperationType) urlOpTypeList.get(1));
					attributeSet.clear();
				}
			} else {
				attributeSet.add(attr);
				jsonMapString = this.getRequestPayload(oclass, attributeSet, uid, searchKey, operation);
				this.executeSpecialAttribute(oclass.getObjectClassValue(), attr.getName(), uid, (String) null,
						jsonMapString, specialAttrURL, (HTTPOperationType) urlOpTypeList.get(1));
			}

		} catch (Exception var26) {
			log.error("Exception while adding special attribute, {0}", new Object[] { var26 });
			throw new ConnectorException(
					this.configuration.getMessage("ex.addAttrFailed", "add attribute operation failed", new Object[0])
							+ " " + var26.getMessage(),
					var26);
		}
	}

	private String executeRequest(ObjectClass oclass, Uid uid, List<Object> urlOpTypeList, String jsonMapString,
			String query) {
		String[][] userList = null;
		if (oclass.equals(oclass.ACCOUNT)) {
			userList = cognosAPI.getUserByName(namespaceID, query);
		}
//		requestUrl.append(urlOpTypeList.get(0));
//		String requestUrlStr = requestUrl.toString();
//		if (query != null && requestUrlStr.contains("$(Filter Suffix)$")) {
//			requestUrlStr = requestUrlStr.replace("/$(Filter Suffix)$", query);
//		} else if (query == null && requestUrlStr.contains("$(Filter Suffix)$")) {
//			requestUrlStr = requestUrlStr.replace("/$(Filter Suffix)$", "");
//		}
//
//		if (GenericRESTUtil.isUidPlaceHolderPresent(requestUrlStr)) {
//			String placeHolderValue = uid != null ? uid.getUidValue() : null;
//			if (placeHolderValue == null) {
//				log.error("Unable to replace the placedholder in configured URL.", new Object[0]);
//				throw new ConnectorException(this.configuration.getMessage("ex.replacePlaceholder",
//						"Unable to replace the placeholder in configured URL.", new Object[0]));
//			}
//
//			requestUrlStr = requestUrlStr.replace("$(__UID__)$", placeHolderValue);
//		}
//
//		log.info("requestUrl:{0}", new Object[] { requestUrlStr });

		try {
			return DataUtils.arrayToJSON(userList, "account", "users").toString();
//			jsonMapString = this.maskPasswordinLog(jsonMapString, oclass.getObjectClassValue());
//			return ClientHandler.executeRequest(this.connection.getConnection(), requestUrlStr,
//					(HTTPOperationType) urlOpTypeList.get(1), jsonMapString, (HttpEntity) null);
		} catch (Exception var9) {
			log.error("Exception in getJsonResponseString, {0}", new Object[] { var9 });
			throw new ConnectorException(this.configuration.getMessage("ex.executeRequest",
					"Exception occurred while executing request.", new Object[0]) + " " + var9.getMessage(), var9);
		}
	}

	private String executeSpecialAttribute(String oclass, String attrName, String uid, String attrId,
			String jsonMapString, String relUrl, HTTPOperationType operation) throws Exception {
		String updatedRelUrl = relUrl;
		if (GenericRESTUtil.isUidPlaceHolderPresent(relUrl)) {
			updatedRelUrl = relUrl.replace("$(__UID__)$", uid);
		}

		if (GenericRESTUtil.isPlaceHolderPresent(updatedRelUrl)) {
			if (attrId == null) {
				throw new ConnectorException(
						this.configuration.getMessage("ex.invalidArgument", "Invalid argument {0} passed to method {1}",
								new Object[] { attrId, "executeSpecialAttribute" }));
			}

			String objectClassWithAttrPlaceHolder = updatedRelUrl.substring(updatedRelUrl.indexOf("$("),
					updatedRelUrl.indexOf(")$") + ")$".length());
			String objectClassWithAttr = updatedRelUrl.substring(updatedRelUrl.indexOf("$(") + "$(".length(),
					updatedRelUrl.indexOf(")$"));
			String[] objectClassAttrArray = objectClassWithAttr.split("\\.");
			if (objectClassAttrArray[0].equalsIgnoreCase("__MEMBERSHIP__")) {
				Map<String, String> jsonStringMap = new HashMap();
				String membershipRelUrl = GenericRESTUtil.getMembershipSearchUrl(oclass, uid, attrName, attrId,
						this.relURIsMap, this.configuration);
				jsonStringMap.put(GenericRESTUtil.getSearchKey(new String[] { "__MEMBERSHIP__", "URL" }),
						membershipRelUrl);
				jsonStringMap.put("__UID__", uid);
				Map<String, Map<String, Object>> conditionMap = GenericRESTUtil.getConditionMapForMembershipSearch(
						oclass, attrName, attrId, objectClassWithAttr, this.configuration);
				GenericRESTUtil.addJsonResourcesTagToConfigParamsMap(this.parserConfigParamsMap,
						this.objectClasstoParserConfigMap,
						GenericRESTUtil.getSearchKey(new String[] { oclass, "__MEMBERSHIP__", attrName }));
				updatedRelUrl = ClientHandler.handlePlaceHolders(jsonStringMap, updatedRelUrl, this.parser,
						this.connection.getConnection(), this.parserConfigParamsMap, conditionMap);
			} else {
				updatedRelUrl = updatedRelUrl.replace(objectClassWithAttrPlaceHolder, attrId);
			}
		}

		StringBuilder requestUrl = (new StringBuilder(this.configuration.isSslEnabled() ? "https://" : "http://"))
				.append(this.configuration.getHost());
		if (this.configuration.getPort() > 0) {
			requestUrl.append(":").append(this.configuration.getPort());
		}

		requestUrl.append(updatedRelUrl);
		log.info("requestUrl:{0}", new Object[] { requestUrl });
		jsonMapString = this.maskPasswordinLog(jsonMapString, oclass);
		return ClientHandler.executeRequest(this.connection.getConnection(), requestUrl.toString(), operation,
				jsonMapString, (HttpEntity) null);
	}

	private String getRequestPayload(ObjectClass oclass, Set<Attribute> attrSet, String uid, String searchKey,
			String operation) {
		log.info("Method Entered", new Object[0]);
		String requestPayload = null;
		List<String> multiVal = new ArrayList();
		if (this.simpleMultivaluedAttributesMap != null
				&& this.simpleMultivaluedAttributesMap.containsKey(oclass.getObjectClassValue())) {
			multiVal = (List) this.simpleMultivaluedAttributesMap.get(oclass.getObjectClassValue());
		}

		Map<String, String> namedAttributes = (Map) this.namedAttributeMap.get(oclass.getObjectClassValue());
		if (namedAttributes == null) {
			log.error("Unable to find UID and Name attributes for the given object class {0}.",
					new Object[] { oclass.getObjectClassValue() });
			throw new ConnectorException(
					this.configuration.getMessage("ex.nullNamedAttributes",
							"Unable to find UID and Name attributes for the given object class "
									+ oclass.getObjectClassValue() + ".",
							new Object[] { oclass.getObjectClassValue() }));
		} else {
			Set<Attribute> updAttrSet = new HashSet(attrSet);
			Attribute passwordAttr;
			boolean checkForUpdateOp;
			AttributeBuilder attrBuilder;
			if (!StringUtil.isBlank(this.configuration.getStatusEnableValue())
					&& !StringUtil.isBlank(this.configuration.getStatusDisableValue())) {
				passwordAttr = AttributeUtil.find("__ENABLE__", updAttrSet);
				if (passwordAttr != null) {
					updAttrSet.remove(passwordAttr);
					checkForUpdateOp = AttributeUtil.getBooleanValue(passwordAttr);
					attrBuilder = new AttributeBuilder();
					attrBuilder.setName("__ENABLE__");
					if (checkForUpdateOp) {
						attrBuilder.addValue(new Object[] { this.configuration.getStatusEnableValue() });
					} else {
						attrBuilder.addValue(new Object[] { this.configuration.getStatusDisableValue() });
					}

					updAttrSet.add(attrBuilder.build());
				}
			}

			if (!StringUtil.isBlank(this.configuration.getPasswordAttribute())) {
				passwordAttr = AttributeUtil.find("__PASSWORD__", updAttrSet);
				if (passwordAttr != null) {
					updAttrSet.remove(passwordAttr);
					String passwordValue = GenericRESTUtil
							.decryptPassword((GuardedString) passwordAttr.getValue().get(0));
					attrBuilder = new AttributeBuilder();
					attrBuilder.setName((String) namedAttributes.get(OperationalAttributes.PASSWORD_NAME));
					attrBuilder.addValue(new Object[] { passwordValue });
					updAttrSet.add(attrBuilder.build());
				}
			}

			Map jsonMap = ClientHandler.convertAttrSetToMap(oclass, updAttrSet, (Schema) null, (List) multiVal,
					namedAttributes);

			try {
				checkForUpdateOp = false;
				if (operation.equalsIgnoreCase(CONNECTOR_OPERATION.ADDATTRIBUTE.toString())
						|| operation.equalsIgnoreCase(CONNECTOR_OPERATION.REMOVEATTRIBUTE.toString())) {
					checkForUpdateOp = true;
				}

				String mapKey = GenericRESTUtil.getSearchKey(new String[] { searchKey, operation });
				String customPayload = (String) this.customPayloadMap.get(mapKey);
				if (StringUtil.isBlank(customPayload) && checkForUpdateOp) {
					mapKey = GenericRESTUtil
							.getSearchKey(new String[] { searchKey, CONNECTOR_OPERATION.UPDATEOP.toString() });
					customPayload = (String) this.customPayloadMap.get(mapKey);
				}

				if (StringUtil.isBlank(customPayload)) {
					requestPayload = this.parser.parseRequest(jsonMap);
				} else {
					if (customPayload.contains("$(") && customPayload.contains(")$")) {
						Map<String, String> jsonStringMap = new HashMap();
						jsonStringMap.put("__UID__", uid);
						Iterator i$ = jsonMap.keySet().iterator();

						while (i$.hasNext()) {
							String key = (String) i$.next();
							jsonStringMap.put(
									RESTCommonUtils.handleSpecialAttributeInCustomPayload(key, namedAttributes),
									jsonMap.get(key).toString());
						}

						customPayload = ClientHandler.handlePlaceHolders(jsonStringMap, customPayload, this.parser,
								(CloseableHttpClient) null, (Map) null, (Map) null);
					}

					requestPayload = customPayload;
				}
			} catch (Exception var17) {
				log.error("Exception occurred while creating request payload, {0}", new Object[] { var17 });
				throw new ConnectorException(this.configuration.getMessage("ex.requestPayload",
						"Exception occurred while creating request payload.", new Object[0]) + " " + var17.getMessage(),
						var17);
			}

			log.info("Method Exiting", new Object[0]);
			return requestPayload;
		}
	}

	private String removeAttributeValue(final ObjectClass oclass, final String uid, final Attribute attr,
			final List<Object> urlOpTypeList) throws Exception {
		GenericRESTConnector.log.ok("Method Entered", new Object[0]);
		GenericRESTConnector.log.info("removeAttributeValue for uid :{0}", new Object[] { uid });
		for (final Object attrVal : attr.getValue()) {
			final String attrValToBeRemoved = GenericRESTUtil.getValueToBeRemoved(attrVal,
					(Map) this.namedAttributeMap);
			String jsonMapString = null;
			if (urlOpTypeList.get(1) != HTTPOperationType.DELETE) {
				final Set<Attribute> attSet = new HashSet<Attribute>();
				if (attrVal instanceof EmbeddedObject) {
					for (final Attribute attrFromEmbObject : ((EmbeddedObject) attrVal).getAttributes()) {
						attSet.add(attrFromEmbObject);
					}
				}
				jsonMapString = this.getRequestPayload(oclass, attSet, uid,
						oclass.getObjectClassValue() + "." + attr.getName(),
						CONNECTOR_OPERATION.REMOVEATTRIBUTE.toString());
			}
			this.executeSpecialAttribute(oclass.getObjectClassValue(), attr.getName(), uid, attrValToBeRemoved,
					jsonMapString, urlOpTypeList.get(0).toString(), (HTTPOperationType) urlOpTypeList.get(1));
		}
		GenericRESTConnector.log.ok("Method Exiting", new Object[0]);
		return uid;
	}

	private boolean makeConnectorObject(final ResultsHandler handler, final OperationOptions options,
			final ObjectClass oclass, final Map<String, Object> jsonResponseMap, final Set<String> specialAttributeset)
			throws Exception {
		GenericRESTConnector.log.info("Method Entered", new Object[0]);
		final ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
		final Map<String, String> namedAttributes = this.namedAttributeMap.get(oclass.getObjectClassValue());
		String organization = null;
		final Map<String, Object> attrmap = (Map<String, Object>) options.getOptions();
		final String uidValue = (String) GenericRESTUtil.getAttributeValue((String) namedAttributes.get(Uid.NAME),
				(Map) jsonResponseMap);
		String[] targetIdentifier = null;
		if (options.getAttributesToGet() != null) {
			final String[] attrsToGet = options.getAttributesToGet();
			GenericRESTConnector.log.info("uid:{0}", new Object[] { uidValue });
			builder.setObjectClass(oclass);
			builder.setUid(new Uid(uidValue));
			builder.setName((String) jsonResponseMap.get(namedAttributes.get(Name.NAME)));
			Map<String, String> targetObjectIdentifierMap = null;
			if (this.configuration.getTargetObjectIdentifier() != null) {
				targetObjectIdentifierMap = (Map<String, String>) GenericRESTUtil
						.getSpecialAttributeMap(this.configuration.getTargetObjectIdentifier());
			}
			for (final String attr : attrsToGet) {
				if (!attr.equals(Name.NAME)) {
					if (!attr.equals(Uid.NAME)) {
						if (attr.equalsIgnoreCase("OIM Organization Name")) {
							organization = attrmap.get("OIM Organization Name").toString();
							GenericRESTConnector.log.info("OIM Organization name is :{0}",
									new Object[] { organization });
						} else if (attr.equals("__ENABLE__")) {
							final String status = String
									.valueOf(jsonResponseMap.get(namedAttributes.get("__ENABLE__")));
							final boolean statusValue = GenericRESTUtil.getStatusValue(status, this.configuration);
							GenericRESTConnector.log.info("Setting status as:{0}", new Object[] { statusValue });
							builder.addAttribute(new Attribute[] { AttributeBuilder.buildEnabled(statusValue) });
						} else if (!specialAttributeset.contains(attr)) {
							if (targetObjectIdentifierMap != null
									&& targetObjectIdentifierMap.get(oclass.getObjectClassValue() + "." + attr) != null
									&& targetObjectIdentifierMap.get(oclass.getObjectClassValue() + "." + attr)
											.contains(";")) {
								targetIdentifier = targetObjectIdentifierMap
										.get(oclass.getObjectClassValue() + "." + attr).split(";");
							}
							final Object attributeVal = this.setConnectorObjectValue(attr, attr, jsonResponseMap,
									targetIdentifier);
							if (attributeVal != null) {
								if (attributeVal instanceof List) {
									builder.addAttribute(new Attribute[] {
											AttributeBuilder.build(attr, (Collection) attributeVal) });
								} else {
									builder.addAttribute(new Attribute[] {
											AttributeBuilder.build(attr, new Object[] { attributeVal }) });
								}
							} else {
								GenericRESTConnector.log.error(
										"Failed to retrieve value of attribute {0} from the response",
										new Object[] { attr });
							}
						}
					}
				}
			}
			if (specialAttributeset != null) {
				GenericRESTConnector.log.info("special attributes present", new Object[0]);
				for (final String attr2 : specialAttributeset) {
					String specialAttributeResponse = null;
					try {
						specialAttributeResponse = this.getSpecialAttributeDetails(oclass, attr2, uidValue);
					} catch (Exception ex) {
						GenericRESTConnector.log.error("Getting special attributes: {0} failed, for :{1},{2}",
								new Object[] { attr2, uidValue, ex.getMessage() });
						continue;
					}
					final String searchKey = oclass.getObjectClassValue() + "." + attr2;
					GenericRESTUtil.addJsonResourcesTagToConfigParamsMap((Map) this.parserConfigParamsMap,
							(Map) this.objectClasstoParserConfigMap, searchKey);
					final List<Map<String, Object>> specialAttributeResponseList = (List<Map<String, Object>>) this.parser
							.parseResponse(specialAttributeResponse, (Map) this.parserConfigParamsMap);
					if (targetObjectIdentifierMap != null
							&& targetObjectIdentifierMap.get(oclass.getObjectClassValue() + "." + attr2) != null
							&& targetObjectIdentifierMap.get(oclass.getObjectClassValue() + "." + attr2)
									.contains(";")) {
						targetIdentifier = targetObjectIdentifierMap.get(oclass.getObjectClassValue() + "." + attr2)
								.split(";");
					}
					final Map<String, String> specialAttributeTargetFormatMap = (Map<String, String>) GenericRESTUtil
							.getSpecialAttributeMap(this.configuration.getSpecialAttributeTargetFormat());
					String targetAttr = specialAttributeTargetFormatMap.get(oclass.getObjectClassValue() + "." + attr2);
					targetAttr = ((targetAttr != null) ? targetAttr : attr2);
					final List<Object> attributeValueList = new ArrayList<Object>();
					for (final Map<String, Object> specialAttributeResponseMap : specialAttributeResponseList) {
						final Object attributeVal2 = this.setConnectorObjectValue(attr2, targetAttr,
								specialAttributeResponseMap, targetIdentifier);
						if (attributeVal2 != null) {
							if (attributeVal2 instanceof List) {
								attributeValueList.addAll((Collection<?>) attributeVal2);
							} else {
								attributeValueList.add(attributeVal2);
							}
						}
					}
					if (!attributeValueList.isEmpty()) {
						builder.addAttribute(
								new Attribute[] { AttributeBuilder.build(attr2, (Collection) attributeValueList) });
					}
				}
			}
		}
		if (organization != null) {
			builder.addAttribute(
					new Attribute[] { AttributeBuilder.build("OIM Organization Name", new Object[] { organization }) });
			GenericRESTConnector.log.info("Organization added with value :{0}", new Object[] { organization });
		}
		GenericRESTConnector.log.info("Method Exiting", new Object[0]);
		return handler.handle(builder.build());
	}

	private Object setConnectorObjectValue(String sourceAttribute, String targetAttribute,
			Map<String, Object> attributeResponseMap, String[] targetIdentifier) {
		Object attrValue = null;
		attrValue = GenericRESTUtil.getAttributeValue(targetAttribute, attributeResponseMap);
		log.info("setting attribute: {0} with value: {1}", new Object[] { targetAttribute, attrValue });
		new HashMap();
		if (attrValue != null) {
			if (!(attrValue instanceof List)) {
				return attrValue;
			} else {
				log.info("list", new Object[0]);
				if (((List) attrValue).size() > 0 && ((List) attrValue).get(0) instanceof Map) {
					List<Object> embeddedObjectList = new ArrayList();
					log.info("Map", new Object[0]);
					Iterator it = ((List) attrValue).iterator();

					label45: while (true) {
						Map attrValueMap;
						EmbeddedObjectBuilder embObjBldr;
						do {
							if (!it.hasNext()) {
								return embeddedObjectList;
							}

							embObjBldr = new EmbeddedObjectBuilder();
							embObjBldr.setObjectClass(new ObjectClass(sourceAttribute));
							attrValueMap = (Map) it.next();
						} while (!GenericRESTUtil.isValidObject(attrValueMap, targetIdentifier));

						Iterator i$ = attrValueMap.keySet().iterator();

						while (true) {
							String key;
							do {
								if (!i$.hasNext()) {
									embeddedObjectList.add(embObjBldr.build());
									continue label45;
								}

								key = (String) i$.next();
							} while (!(attrValueMap.get(key) instanceof String)
									&& !(attrValueMap.get(key) instanceof Boolean)
									&& !(attrValueMap.get(key) instanceof Integer));

							embObjBldr.addAttribute(new Attribute[] {
									AttributeBuilder.build(key, new Object[] { attrValueMap.get(key) }) });
						}
					}
				} else {
					return (List) attrValue;
				}
			}
		} else {
			return null;
		}
	}

	private String getSpecialAttributeDetails(ObjectClass oclass, String attr, String uid) throws Exception {
		String searchKey = oclass.getObjectClassValue() + "." + attr + "." + CONNECTOR_OPERATION.SEARCHOP.toString();
		log.info("searchKey:{0}", new Object[] { searchKey });
		List<Object> urlOpTypeList = GenericRESTUtil.getURIAndOpType(oclass, CONNECTOR_OPERATION.SEARCHOP, attr,
				this.relURIsMap, this.opTypeMap, this.configuration);
		String specialAttributeResponse = this.executeSpecialAttribute(oclass.getObjectClassValue(), attr, uid,
				(String) null, (String) null, String.valueOf(urlOpTypeList.get(0)), HTTPOperationType.GET);
		log.info("specialAttributeResponse:{0}", new Object[] { specialAttributeResponse });
		return specialAttributeResponse;
	}
}