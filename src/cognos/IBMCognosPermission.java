package cognos;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import org.apache.axis.client.Stub;
import org.apache.axis.message.SOAPHeaderElement;
import org.identityconnectors.common.logging.Log;
import com.cognos.developer.schemas.bibus._3.AccessEnum;
import com.cognos.developer.schemas.bibus._3.Account;
import com.cognos.developer.schemas.bibus._3.BaseClass;
import com.cognos.developer.schemas.bibus._3.BiBusHeader;
import com.cognos.developer.schemas.bibus._3.ContentManagerService_PortType;
import com.cognos.developer.schemas.bibus._3.ContentManagerService_ServiceLocator;
import com.cognos.developer.schemas.bibus._3.Permission;
import com.cognos.developer.schemas.bibus._3.Policy;
import com.cognos.developer.schemas.bibus._3.PolicyArrayProp;
import com.cognos.developer.schemas.bibus._3.PropEnum;
import com.cognos.developer.schemas.bibus._3.SearchPathSingleObject;
import com.cognos.developer.schemas.bibus._3.SystemService_PortType;
import com.cognos.developer.schemas.bibus._3.SystemService_ServiceLocator;
import com.cognos.developer.schemas.bibus._3.UpdateOptions;
import com.cognos.developer.schemas.bibus._3.XmlEncodedXML;
import utils.CognosQueryBuilder;
import utils.DataUtils;

public class IBMCognosPermission {
	public static Log log = Log.getLog(IBMCognosPermission.class);
	ContentManagerService_PortType cmService;
	SystemService_PortType sysService;
	public static String NameSpaceID = "BIDV";
	public static String strXML;
	public static String strXML_CP;
	public static Boolean bCP = false;
	CognosGroup cGroup = null;

	public IBMCognosPermission(String sendPoint) {
		log.ok("Initialize ", new Object[0]);
		try {
			ContentManagerService_ServiceLocator cmServiceLocator = new ContentManagerService_ServiceLocator();
			SystemService_ServiceLocator sysServiceLocator = new SystemService_ServiceLocator();
			cmService = cmServiceLocator.getcontentManagerService(new java.net.URL(sendPoint));
			sysService = sysServiceLocator.getsystemService(new java.net.URL(sendPoint));
			this.cGroup = new CognosGroup(this);
		} catch (Exception e) {
			log.error("{0}", new Object[] { e.getMessage() });
		}
	}

	public void getSetHeaders() {
		String BiBus_NS = "http://developer.cognos.com/schemas/bibus/3/";
		String BiBus_H = "biBusHeader";
		BiBusHeader CMbibus = null;
		SOAPHeaderElement temp = ((Stub) cmService).getResponseHeader(BiBus_NS, BiBus_H);
		try {
			CMbibus = (BiBusHeader) temp.getValueAsType(new QName(BiBus_NS, BiBus_H));
			((Stub) cmService).setHeader(BiBus_NS, BiBus_H, CMbibus);
			((Stub) sysService).setHeader(BiBus_NS, BiBus_H, CMbibus);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void quickLogon(String namespaceID, String userID, String password) throws Exception {
		log.info("Login as user {0}", new Object[] { userID });
		String encodedCredentials = String.format(
				"<credential><namespace>%s</namespace><username>%s</username><password>%s</password></credential>",
				namespaceID, userID, password);
		cmService.logon(new XmlEncodedXML(encodedCredentials), new SearchPathSingleObject[] {});
		getSetHeaders();
		log.info("Method Exiting", new Object[0]);
	}

	public String[][] getUserByName(String namespace, String partUserName) {
		log.info("Get user with username {0}", new Object[] { partUserName });
		String[][] output = null;
		if (cmService != null) {
			PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.userName, };
//			String searchPath = String.format("CAMID(\"%s\")/account[@name='%s']", namespace, partUserName);
			String searchPath = String.format("CAMID(\"%s:u:uid=%s\")", namespace, partUserName);
			try {
				BaseClass[] bc = CognosQueryBuilder.buildQuery(cmService, props, searchPath);
				output = new String[bc.length][3];
				for (int i = 0; i < bc.length; i++) {
					Account acc = (Account) bc[i];
					output[i][0] = acc.getUserName().getValue();
					output[i][1] = acc.getDefaultName().getValue();
					output[i][2] = acc.getSearchPath().getValue();
				}
			} catch (Exception e) {
				log.error("{0}", new Object[] { e.getMessage() });
			}
		}
		log.info("Method Exiting", new Object[0]);
		return output;

	}

	public String[][] getListOfObjects(String Path) {
		Path += "/*";
		String[][] lstObjects = null;
		if (cmService != null) {
			try {
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, null, Path);
				lstObjects = new String[bc.length][3];
				for (int i = 0; i < bc.length; i++) {
					lstObjects[i][0] = bc[i].getDefaultName().getValue();
					lstObjects[i][1] = bc[i].getObjectClass().getValue().toString();
					lstObjects[i][2] = bc[i].getSearchPath().getValue();
				}
			} catch (Exception e) {
				log.error("{0}", new Object[] { e.getMessage() });
			}
		}
		log.info("Method Exiting", new Object[0]);
		return lstObjects;
	}

	public String[] getListOfProgram() {
		String[][] lstGroup = cGroup.getListOfGroups();
		Stream<Object> lst = Arrays.stream(lstGroup).map(x -> x[0] = x[0].contains("_") ? x[0].split("_")[0] : x[0]);
		String[] lstProgram = lst.distinct().toArray(String[]::new);
		log.info("Method Exiting", new Object[0]);
		return lstProgram;
	}

	public String[][] getGroupsOfProgram(String strProgram) {
		String[][] lst = cGroup.getListOfGroups();
		String[][] lstGroup = Arrays.stream(lst).filter(x -> x[0].indexOf(strProgram) == 0).toArray(String[][]::new);
		log.info("Method Exiting", new Object[0]);
		return lstGroup;
	}

	public String getXMLGroupsOfProgram() {
		String xml = "";
		for (String program : getListOfProgram()) {
			xml += "<LIST><PROGRAM>";
			xml += program;
			for (String[] group : getGroupsOfProgram(program)) {
				xml += String.format("<GROUPS>%s</GROUPS>", group[0]);
			}
			xml += "</LIST>";
		}
		log.info("Method Exiting", new Object[0]);

		return xml;
	}

	public String[][] getPoliciesOfObjects(String SearchPath) {
		String[][] output = null;
		if (cmService != null) {
			try {
				PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
						PropEnum.policies };
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, SearchPath);
				Policy[] policies = bc[0].getPolicies().getValue();
				output = new String[policies.length][8];
				Policy pol;
				for (int i = 0; i < policies.length; i++) {
					pol = policies[i];
					String[] s = cGroup.getUserGroupBySearchPath(pol.getSecurityObject().getSearchPath().getValue());
					if (s != null && s.length > 0) {
						output[i][0] = s[0];
						output[i][1] = s[1];
						output[i][2] = s[2];
					} else {
						continue;
					}
					for (Permission permission : pol.getPermissions()) {
						switch (permission.getName()) {
						case "read":
							output[i][3] = String.valueOf(permission.getAccess().equals(AccessEnum.deny));
							break;
						case "write":
							output[i][4] = String.valueOf(permission.getAccess().equals(AccessEnum.deny));
							break;
						case "execute":
							output[i][5] = String.valueOf(permission.getAccess().equals(AccessEnum.deny));
							break;
						case "setPolicy":
							output[i][6] = String.valueOf(permission.getAccess().equals(AccessEnum.deny));
							break;
						case "traverse":
							output[i][7] = String.valueOf(permission.getAccess().equals(AccessEnum.deny));
							break;
						}
					}
				}
			} catch (Exception e) {
				log.error("{0}", new Object[] { e.getMessage() });
				return null;
			}
		}
		log.info("Method Exiting", new Object[0]);
		return output;
	}

	public Boolean setPoliciesOfObjects(String spUserGroup, String[] per, String spObject) {
		if (cmService != null) {
			try {
				PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
						PropEnum.policies };
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, spObject);
				Policy[] existingPolicies = bc[0].getPolicies().getValue();
				int found = -1;
				for (Policy policy : existingPolicies) {
					if (policy.getSecurityObject().getSearchPath().getValue().equals(spUserGroup)) {
						found++;
					}
				}
				if (found == -1) {
					// not found, create new policy
					int policyIndex = existingPolicies.length;
					Policy[] newPol = Arrays.copyOf(existingPolicies, policyIndex + 1);
					BaseClass[] bcSingle = null;
					try {
						bcSingle = CognosQueryBuilder.buildQuery(cmService, props, spObject);
					} catch (Exception e) {
						log.error("{0}", new Object[] { e.getMessage() });
						return false;
					}
					newPol[policyIndex] = new Policy();
					newPol[policyIndex].setSecurityObject(bcSingle[0]);
					int iPer = Math.toIntExact(Arrays.stream(per).filter(x -> x != null).count());
					Permission[] permissions = new Permission[iPer];
					iPer = 0;
					for (int j = 0; j < per.length; j++) {
						if (per[j] != null) {
							switch (j) {
							case 0:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("read");
								permissions[iPer].setAccess(
										per[j].equalsIgnoreCase("true") ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 1:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("write");
								permissions[iPer].setAccess(
										per[j].equalsIgnoreCase("true") ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 2:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("execute");
								permissions[iPer].setAccess(
										per[j].equalsIgnoreCase("true") ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 3:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("setPolicy");
								permissions[iPer].setAccess(
										per[j].equalsIgnoreCase("true") ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 4:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("traverse");
								permissions[iPer].setAccess(
										per[j].equalsIgnoreCase("true") ? AccessEnum.grant : AccessEnum.deny);
								break;
							}
							iPer++;
						}
					}
					newPol[policyIndex].setPermissions(permissions);
					PolicyArrayProp pArray = new PolicyArrayProp();
					pArray.setValue(newPol);
					bc[0].setPolicies(pArray);
					cmService.update(new BaseClass[] { bc[0] }, new UpdateOptions());
				} else {
					// found != -1, update existing policy
					found += 1;
					Policy pol = bc[0].getPolicies().getValue()[found];
					int iPer = Arrays.stream(per).filter(x -> x != null).collect(Collectors.toList()).size();
					Permission[] permissions = new Permission[iPer];
					iPer = 0;
					for (int j = 0; j < per.length; j++) {
						if (per[j] != null) {
							switch (j) {
							case 0:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("read");
								permissions[iPer].setAccess(per[j] == "true" ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 1:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("write");
								permissions[iPer].setAccess(per[j] == "true" ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 2:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("execute");
								permissions[iPer].setAccess(per[j] == "true" ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 3:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("setPolicy");
								permissions[iPer].setAccess(per[j] == "true" ? AccessEnum.grant : AccessEnum.deny);
								break;
							case 4:
								permissions[iPer] = new Permission();
								permissions[iPer].setName("traverse");
								permissions[iPer].setAccess(per[j] == "true" ? AccessEnum.grant : AccessEnum.deny);
								break;
							}
							iPer++;
						}
					}
					pol.setPermissions(permissions);
					cmService.update(new BaseClass[] { bc[0] }, new UpdateOptions());
				} // end found<>-1
			} catch (Exception e) {
				return false;
			}
		}
		System.out.println("Set successfully !");
		log.info("Method Exiting", new Object[0]);

		return true;
	}

	public Boolean removePoliciesOfObjects(String spUserGroup, String spObject) {
		if (cmService != null) {
			try {
				PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
						PropEnum.policies };
				BaseClass[] bc = CognosQueryBuilder.buildQuery(cmService, props, spObject);
				Policy[] lstPolicies = bc[0].getPolicies().getValue();
				Policy[] newPolicies = Arrays.stream(lstPolicies)
						.filter(x -> !(x.getSecurityObject().getSearchPath().getValue().equals(spUserGroup)))
						.toArray(Policy[]::new);
				PolicyArrayProp pArray = new PolicyArrayProp();
				pArray.setValue(newPolicies);
				bc[0].setPolicies(pArray);
				cmService.update(new BaseClass[] { bc[0] }, new UpdateOptions());
			} catch (Exception e) {
				return false;
			}
		}
		log.info("Method Exiting", new Object[0]);
		return true;
	}

	public String getXMLTreeByGroup(String groupSearchPath, Boolean bRight) {
		strXML = "";
		strXML_CP = "";
		String content = "/content";
		if (bRight) {
			createFolderRightTreeByGroup(content, groupSearchPath, false);
		} else {
			createFolderLeftTreeByGroup(content, groupSearchPath);
		}
		log.info("Method Exiting", new Object[0]);
		return strXML;
	}

	public String getXMLPolTreeByGroup(String groupSearchPath) {
		strXML = "";
		strXML_CP = "";
		String content = "/content";
		createFolderRightTreeByGroup(content, groupSearchPath, true);
		log.info("Method Exiting", new Object[0]);
		return strXML;
	}

	public void createFolderRightTreeByGroup(String folderSearchPath, String groupSearchPath, Boolean bPol) {
		String str1 = "history;model;Configuration;Version";
		if (cmService != null) {
			try {
				String[][] array = getListOfObjects(folderSearchPath);
				if (array != null) {
					array = Arrays.stream(array).filter(x -> !DataUtils.checkStringInString(str1, x[1]))
							.toArray(String[][]::new);
					for (int i = 0; i < array.length; i++) {
						// check exist policy
						String[][] policy = getPoliciesOfObjects(array[i][2]);
						Boolean chk = Arrays.stream(policy).filter(x -> x[2].equalsIgnoreCase(groupSearchPath))
								.findAny().isPresent();
						if (!chk)// neu khong co quyen
						{
							continue;
						}
						// end check
						String xml = "";
						xml += "<ul>";
						xml += "<li>";
						if (bPol) {
							xml += "<input type=\"checkbox\" checked value=\"";
						} else {
							xml += "<input type=\"checkbox\" value=\"";
						}
						xml += array[i][2];
						xml += "\" name=\"chkAddFunc\">";
						if (DataUtils.checkStringInString("folder;package", array[i][1])) {
							xml += "<strong>";
							xml += array[i][0];
							xml += "</strong>";
						} else
							xml += array[i][0];

						if (bPol) {
							String[] permissions = new String[] { "", "", "", "read", "write", "execute", "setPolicy",
									"traverse" };
							String xmlTemplate = "<input type=\"checkbox\" %s value=\"%s\" name=\"chkAddSubFunc\">read";
							String[] pol = Arrays.stream(policy).filter(x -> x[2].equalsIgnoreCase(groupSearchPath))
									.findFirst().orElse(null);
							if (pol[2].equalsIgnoreCase(groupSearchPath)) {
								xml += "<ul id=\"sub\"><li>";
								xml += String.format(xmlTemplate, (DataUtils.checkBool(pol[3]) ? "checked" : ""),
										permissions[3]);
								xml += String.format(xmlTemplate, (DataUtils.checkBool(pol[3]) ? "checked" : ""),
										permissions[4]);
								xml += String.format(xmlTemplate, (DataUtils.checkBool(pol[3]) ? "checked" : ""),
										permissions[5]);
								xml += String.format(xmlTemplate, (DataUtils.checkBool(pol[3]) ? "checked" : ""),
										permissions[6]);
								xml += String.format(xmlTemplate, (DataUtils.checkBool(pol[3]) ? "checked" : ""),
										permissions[7]);
								xml += "</li></ul>";
								break;
							}
						}
						strXML += xml;
						if (!array[i][1].toLowerCase().equals("dashboard")) {
							createFolderRightTreeByGroup(array[i][2], groupSearchPath, bPol);
						}
						strXML += "</li></ul>";
					}
				}
			} catch (Exception e) {
			}
		}
		log.info("Method Exiting", new Object[0]);

	}

	public void createFolderLeftTreeByGroup(String folderSearchPath, String groupSearchPath) {
		String str1 = "history;model;Configuration;Version";
		// String str1="@#$%^";
		if (cmService != null) {
			try {
				String[][] lstObjects = getListOfObjects(folderSearchPath);
				if (lstObjects != null) {
					for (String[] object : lstObjects) {
						if (DataUtils.checkStringInString(str1, object[1])) {
							lstObjects = DataUtils.removeItemInString(lstObjects, object[1]);
						}
					}
					for (String[] object : lstObjects) {
						// check exist policy
						String[][] policy = getPoliciesOfObjects(object[2]);
						// end check
						String xml = String.format(
								"<ul><li><input type = \"checkbox\" value = \"%s\" name = \"chkAddFunc\"></li></ul>",
								object[2]);
						Boolean chk_C = false;
						// check co con
						try {
							String[][] ar = !object[1].toLowerCase().equals("dashboard") ? getListOfObjects(object[2])
									: null;
							// check con trong report hoac package
							chk_C = Arrays.stream(ar).filter(x -> !DataUtils.checkStringInString(str1, x[1])).findAny()
									.isPresent();
						} catch (Exception e) {
							chk_C = false;
						}
						// end check
						if (DataUtils.checkStringInString("folder;package", object[1]))// neu co con
						{
							xml += String.format("<strong>%s</strong>", object[0]);
						} else {
							xml += object[0];
						}
						Boolean chk = Arrays.stream(policy).filter(x -> x[2].equalsIgnoreCase(groupSearchPath))
								.findAny().isPresent();
						if (!chk)// neu ko co quyen
						{
							strXML += strXML_CP;
							strXML_CP = "";
							strXML += xml;
							if (!object[1].toLowerCase().equals("dashboard")) {
								createFolderLeftTreeByGroup(object[2], groupSearchPath);
							}
							strXML += "</li></ul>";
						} else// neu co quyen
						{
							if (chk_C)// neu co con
							{
								strXML_CP += xml;
								if (!object[1].toLowerCase().equals("dashboard")) {
									createFolderLeftTreeByGroup(object[2], groupSearchPath);
								}
								strXML_CP += "</li></ul>";
							}
						}
					}
				}
			} catch (Exception e) {
			}
		}
		log.info("Method Exiting", new Object[0]);
	}
}
