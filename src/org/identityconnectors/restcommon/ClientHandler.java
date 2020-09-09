package org.identityconnectors.restcommon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.restcommon.auth.spi.AuthenticationPlugin;
import org.identityconnectors.restcommon.parser.spi.ParserPlugin;
import org.identityconnectors.restcommon.utils.MessageLocalizer;
import org.identityconnectors.restcommon.utils.RESTCommonUtils;
import org.identityconnectors.restcommon.utils.RESTCommonConstants.HTTPOperationType;

public class ClientHandler {
	private static final Log log = Log.getLog(ClientHandler.class);

	public static Map<String, String> getAuthenticationHeaders(Map<String, Object> authParams, String authClassName) {
		log.ok("Authentication class name : " + authClassName, new Object[0]);
		AuthenticationPlugin auth = (AuthenticationPlugin) AuthenticationPlugin.class
				.cast(RESTCommonUtils.getInstance(authClassName));
		return auth.getAuthHeaders(authParams);
	}

	public static ParserPlugin getParserInstance(String parserClassName) {
		log.ok("Parser class name : " + parserClassName, new Object[0]);
		return (ParserPlugin) ParserPlugin.class.cast(RESTCommonUtils.getInstance(parserClassName));
	}

	public static Map<String, Object> convertAttrSetToMap(ObjectClass oclass, Set<Attribute> attrs, Schema schema,
			List<String> simpleMultiValAttrNames, Map<String, String> specialAttributes) {
		Map<String, Object> attrMap = new HashMap();
		Iterator i$ = attrs.iterator();

		while (i$.hasNext()) {
			Attribute attr = (Attribute) i$.next();
			String attrName = attr.getName();
			if (!attrName.contentEquals("__CURRENT_ATTRIBUTES__")) {
				attrName = RESTCommonUtils.handleSpecialAttribute(attrName, specialAttributes);
				if (RESTCommonUtils.isComplexMultiValAttr(attr)) {
					List<Map<String, Object>> subMap = RESTCommonUtils.convertEmbObjToMap(attr.getValue());
					attrMap.put(attrName, subMap);
				} else if (RESTCommonUtils.isSimpleMultiValAttr(schema, simpleMultiValAttrNames, oclass,
						attr.getName())) {
					attrMap.put(attrName, attr.getValue());
				} else if (RESTCommonUtils.isComplexSingleValAttr(attrName)) {
					RESTCommonUtils.handleComplexSingleValAttr(attrName, attrMap, AttributeUtil.getSingleValue(attr));
				} else {
					attrMap.put(attrName, AttributeUtil.getSingleValue(attr));
				}
			}
		}

		return attrMap;
	}

	public static String executeRequest(final CloseableHttpClient httpClient, final String url,
			final HTTPOperationType operation, final String json, final HttpEntity entity) {
		ClientHandler.log.info("Making a '" + operation + "' call using the URL '" + url + "' ", new Object[0]);
		Logger.getLogger("org.apache.http.headers").setLevel(Level.INFO);
		CloseableHttpResponse response = null;
		HttpRequestBase httpRequest = null;
		try {
			String jsonResp = null;
			switch (operation) {
			case GET: {
				httpRequest = (HttpRequestBase) new HttpGet(url);
				break;
			}
			case POST: {
				httpRequest = (HttpRequestBase) new HttpPost(url);
				if (entity != null) {
					((HttpPost) httpRequest).setEntity(entity);
					break;
				}
				final StringEntity stringEntity = new StringEntity(json);
				((HttpPost) httpRequest).setEntity((HttpEntity) stringEntity);
				break;
			}
			case DELETE: {
				httpRequest = (HttpRequestBase) new HttpDelete(url);
				break;
			}
			case PATCH: {
				httpRequest = (HttpRequestBase) new HttpPatch(url);
				final StringEntity stringEntity = new StringEntity(json);
				((HttpPatch) httpRequest).setEntity((HttpEntity) stringEntity);
				break;
			}
			case PUT: {
				httpRequest = (HttpRequestBase) new HttpPut(url);
				final StringEntity stringEntity = new StringEntity(json);
				((HttpPut) httpRequest).setEntity((HttpEntity) stringEntity);
				break;
			}
			}
			response = httpClient.execute((HttpUriRequest) httpRequest);
			if (response.getEntity() != null) {
				jsonResp = EntityUtils.toString(response.getEntity());
			}
			RESTCommonUtils.checkStatus(response, jsonResp);
			return jsonResp;
		} catch (IOException e) {
			ClientHandler.log.error("Error occurred while executing a {0} REST call on the target. " + e.getMessage(),
					new Object[] { operation });
			throw new ConnectorException(MessageLocalizer.getMessage("ex.restCallInternalFailure",
					"Error occurred while executing a " + operation + " REST call on the target.",
					new Object[] { operation }), (Throwable) e);
		} finally {
			if (httpRequest != null) {
				httpRequest.releaseConnection();
			}
			if (response != null) {
				try {
					response.close();
				} catch (IOException e2) {
					ClientHandler.log.error("Failed to close the resource CloseableResponse. " + e2.getMessage(),
							new Object[0]);
					throw new ConnectorException(MessageLocalizer.getMessage("ex.resourceCloseFailure",
							"Failed to close the resource CloseableResponse.", new Object[] { "CloseableResponse" }),
							(Throwable) e2);
				}
			}
		}
	}

	public static String handlePlaceHolders(Map<String, String> customPayloadMap, String payload, ParserPlugin parser,
			CloseableHttpClient httpClient, Map<String, String> parserConfigParamsMap,
			Map<String, Map<String, Object>> conditionMap) {
		List<String> attrListInPayload = new ArrayList();
		Map<Integer, Integer> indexPlaceholder = RESTCommonUtils.searchPlaceHolders(payload);
		Iterator i$ = indexPlaceholder.keySet().iterator();

		while (i$.hasNext()) {
			Integer startindex = (Integer) i$.next();
			attrListInPayload
					.add(payload.substring(startindex + "$(".length(), (Integer) indexPlaceholder.get(startindex)));
		}

		i$ = attrListInPayload.iterator();

		while (true) {
			while (i$.hasNext()) {
				String attr = (String) i$.next();
				if (customPayloadMap.containsKey(attr)) {
					payload = payload.replace("$(" + attr + ")$", (CharSequence) customPayloadMap.get(attr));
				} else {
					String[] attrHierarchy = attr.split("\\.");
					if (!customPayloadMap.containsKey(attrHierarchy[0] + ".URL")) {
						payload = payload.replace("$(" + attr + ")$", "");
					} else {
						List<Map<String, Object>> respList = parser.parseResponse(
								executeRequest(httpClient, (String) customPayloadMap.get(attrHierarchy[0] + ".URL"),
										HTTPOperationType.GET, (String) null, (HttpEntity) null),
								parserConfigParamsMap);
						Map attrMap;
						if (respList.size() > 1 && conditionMap != null && conditionMap.containsKey(attr)) {
							attrMap = RESTCommonUtils.doConditionalParsing((Map) conditionMap.get(attr), respList);
						} else {
							attrMap = (Map) respList.get(0);
						}

						payload = payload.replace("$(" + attr + ")$", String.valueOf(
								RESTCommonUtils.getValueForNestedTag(attr.substring(attr.indexOf(".") + 1), attrMap)));
					}
				}
			}

			return payload;
		}
	}

	public static String handlePasswordInLogs(ParserPlugin parser, Map<String, Object> payloadMap,
			String passwordAttribute) {
		if (passwordAttribute == null) {
			return parser.parseRequest(payloadMap);
		} else {
			Map<String, Object> logMap = new HashMap();
			logMap.putAll(payloadMap);
			logMap.putAll(RESTCommonUtils.maskPassword(passwordAttribute, logMap));
			return parser.parseRequest(logMap);
		}
	}

	public static String handlePasswordInLogs(String payload) {
		int startIndx = 0;

		int endIndx;
		for (boolean var2 = false; payload.indexOf("##", startIndx) != -1; startIndx = endIndx + "##".length()) {
			startIndx = payload.indexOf("##", startIndx);
			endIndx = payload.indexOf("##", startIndx + "##".length());
			payload = payload.subSequence(0, startIndx) + "****" + payload.substring(endIndx + "##".length());
		}

		return payload;
	}
}