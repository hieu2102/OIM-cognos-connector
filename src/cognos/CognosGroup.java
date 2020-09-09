package cognos;

import java.util.Arrays;

import org.identityconnectors.common.logging.Log;

import com.cognos.developer.schemas.bibus._3.Account;
import com.cognos.developer.schemas.bibus._3.AddOptions;
import com.cognos.developer.schemas.bibus._3.BaseClass;
import com.cognos.developer.schemas.bibus._3.BaseClassArrayProp;
import com.cognos.developer.schemas.bibus._3.ConfigurationData;
import com.cognos.developer.schemas.bibus._3.ConfigurationDataEnum;
import com.cognos.developer.schemas.bibus._3.ContentManagerService_PortType;
import com.cognos.developer.schemas.bibus._3.DeleteOptions;
import com.cognos.developer.schemas.bibus._3.Group;
import com.cognos.developer.schemas.bibus._3.Locale;
import com.cognos.developer.schemas.bibus._3.MultilingualToken;
import com.cognos.developer.schemas.bibus._3.MultilingualTokenProp;
import com.cognos.developer.schemas.bibus._3.PropEnum;
import com.cognos.developer.schemas.bibus._3.SearchPathSingleObject;
import com.cognos.developer.schemas.bibus._3.StringProp;
import com.cognos.developer.schemas.bibus._3.UpdateActionEnum;
import com.cognos.developer.schemas.bibus._3.UpdateOptions;

import utils.CognosQueryBuilder;
import utils.DataUtils;

public class CognosGroup {
	private ContentManagerService_PortType cmService = null;
	static Log log = Log.getLog(CognosGroup.class);
	private IBMCognosPermission cognosPermission;

	public CognosGroup(IBMCognosPermission cognosPermission) {
		this.cognosPermission = cognosPermission;
		this.cmService = cognosPermission.cmService;
	}

	public String[] getUserGroupBySearchPath(String SearchPath) {
		log.info("get User Groups by Search Path");
		String[] output = null;
		if (cmService != null) {
			PropEnum props[] = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.userName,
					PropEnum.members, PropEnum.objectClass, };
			try {
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, SearchPath);
				output = new String[3];
				if (bc[0].getObjectClass().getValue().toString().equalsIgnoreCase("group")) {
					Group group = (Group) bc[0];
					output[0] = group.getDefaultName().getValue();
					output[1] = null;
					output[2] = group.getSearchPath().getValue();
				} else {
					Account acc = (Account) bc[0];
					output[0] = acc.getUserName().getValue();
					output[1] = acc.getDefaultName().getValue();
					output[2] = acc.getSearchPath().getValue();
				}
			} catch (Exception e) {
				return null;
			}
		}
		return output;
	}

	public BaseClass[] getGroupMembers(Group group) throws Exception {
		BaseClass[] members = group.getMembers().getValue();
		return null == members ? new BaseClass[0] : members;
	}

	public String addUserGroupToGroup(String UserGroup, String Type, String Group) {
		String searchPathUserGroup = null;
		String searchPathGroup = null;
		if (Type.equalsIgnoreCase("group")) {
			String[][] s = getListOfGroups();
			for (String[] userGroup : s) {
				if (userGroup[0].equalsIgnoreCase(UserGroup.trim())) {
					searchPathUserGroup = userGroup[2];
					break;
				}
			}
		} else {
			String[][] s = cognosPermission.getUserByName(IBMCognosPermission.NameSpaceID, UserGroup);
			if (s != null && s.length > 0) {
				searchPathUserGroup = s[0][2];
			}
		}
		if (searchPathUserGroup == null) {
			String msg = "group/user \"" + UserGroup + "\" does not exist";
			return msg;
		}
		String[][] s = getListOfGroups();
		if (s != null & s.length > 0) {
			for (String[] group : s) {
				if (Group.trim().equalsIgnoreCase(group[0])) {
					searchPathGroup = group[2];
					break;
				}
			}
		}
		if (searchPathGroup == null) {
			String msg = "Group \"" + Group + "\" does not exist";
			return msg;
		}
		if (cmService != null) {
			PropEnum props[] = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
					PropEnum.members, PropEnum.userName, };
			try {
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, searchPathGroup);
				if (bc != null && bc.length > 0) {
					Group group = (Group) bc[0];
					BaseClass[] oldMembers = getGroupMembers(group);
					BaseClass[] allMembers = appendNewMembers(oldMembers, searchPathUserGroup);
					group.setMembers(new BaseClassArrayProp());
					group.getMembers().setValue(allMembers);
					// Update the membership.
					cmService.update(new BaseClass[] { group }, new UpdateOptions());
				} else {
					String msg = "\n\nThere are currently no Groups matching the criteria";
					return msg;
				}
			} catch (Exception e) {
				log.error("{0}", e.getMessage());
				return e.getMessage();
			}
		}
		log.info(String.format("Added Group/User %s to Group %s", UserGroup, Group));
		return "success";
	}

	public String[][] getListOfGroups() {
		log.ok("Enter method ", new Object[0]);
		String str1 = "All Authenticated Users;Everyone";
		PropEnum props[] = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass, };
		String[][] output = null;
		try {
			BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, "CAMID(\":\")/*");
			if (bc == null || bc.length == 0) {
				log.warn("There are currently no Groups matching the criteria", new Object[0]);
				return null;
			}
			output = new String[bc.length][3];
			for (int i = 0; i < bc.length; i++) {
				output[i][0] = bc[i].getDefaultName().getValue();
				output[i][1] = bc[i].getObjectClass().getValue().toString();
				output[i][2] = bc[i].getSearchPath().getValue();
			}
		} catch (Exception e) {
			log.error("{0}", e.getMessage());
		}
		log.ok("Method Exiting", new Object[0]);
		return Arrays.stream(output)
				.filter(x -> x[1].equalsIgnoreCase("group") && !DataUtils.checkStringInString(str1, x[0]))
				.toArray(String[][]::new);
	}

	public BaseClass[] appendNewMembers(BaseClass[] oldMembers, String newSearchPath) throws Exception {
		if (!DataUtils.contains(oldMembers, newSearchPath)) {
			// Get the user
			PropEnum[] props = { PropEnum.defaultName, PropEnum.searchPath };
			BaseClass user = CognosQueryBuilder.buildQuery(cmService, props, newSearchPath)[0];
			BaseClass[] allMembers = Arrays.copyOf(oldMembers, oldMembers.length + 1);
			allMembers[oldMembers.length] = user;
			return allMembers;
		}
		return oldMembers;
	}

	public String[][] getUsersGroupsOfGroups(String SearchPathGroup) {
		String[][] output = null;
		if (cmService != null) {
			PropEnum props[] = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
					PropEnum.members, PropEnum.userName, };
			try {
				BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, SearchPathGroup);
				if (bc != null && bc.length > 0) {
					Group group = (Group) bc[0];
					output = Arrays.stream(group.getMembers().getValue())
							.map(x -> Arrays.copyOf(getUserGroupBySearchPath(x.getSearchPath().getValue()), 3))
							.toArray(String[][]::new);
				} else {
					log.warn("There are currently no Groups matching the criteria", new Object[0]);
				}
			} catch (Exception e) {
				log.error("{0}", new Object[] { e.getMessage() });
				return null;
			}
		}
		return output;
	}

	public void removeUserGroupToGroup(IBMCognosPermission ibmCognosPermission, String UserGroup, String Type,
			String Group) {
		String searchPathUserGroup = null;
		String searchPathGroup = null;
		String[][] groupList = getListOfGroups();
		if (Type.equalsIgnoreCase("group")) {
			if (groupList != null && groupList.length > 0) {
				for (String[] i : groupList) {
					if (UserGroup.trim().equalsIgnoreCase(i[0])) {
						searchPathUserGroup = i[2];
						break;
					}
				}
			}
		} else {
			String[][] s = ibmCognosPermission.getUserByName(IBMCognosPermission.NameSpaceID, UserGroup);
			if (s != null && s.length > 0) {
				searchPathUserGroup = s[0][2];
			}
		}
		if (searchPathUserGroup == null) {
			log.warn("Group/User {0} does not exist", new Object[] { UserGroup });
		}
		if (groupList != null & groupList.length > 0) {
			for (int i = 0; i < groupList.length; i++) {
				if (Group.trim().equalsIgnoreCase(groupList[i][0])) {
					searchPathGroup = groupList[i][2];
					break;
				}
			}
		}
		if (searchPathGroup == null) {
			log.error("Group {0} does not exist", new Object[] { UserGroup });
		}
		PropEnum props[] = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
				PropEnum.members, PropEnum.userName, };
		try {
			BaseClass bc[] = CognosQueryBuilder.buildQuery(cmService, props, searchPathGroup);
			if (bc != null && bc.length > 0) {
				Group group = (Group) bc[0];
				BaseClass[] oldMembers = getGroupMembers(group);
				final String finalSearchPathUserGroup = String.valueOf(searchPathUserGroup);
				boolean findMember = Arrays.stream(oldMembers)
						.filter(x -> x.getSearchPath().getValue().equals(finalSearchPathUserGroup)).findAny()
						.isPresent();
				if (!findMember) {
					log.error("Group/User {0} does not exist in Group {1}", new Object[] { UserGroup, Group });
				}
				BaseClass[] newMembers = Arrays.stream(oldMembers)
						.filter(x -> !x.getSearchPath().getValue().equals(finalSearchPathUserGroup))
						.toArray(BaseClass[]::new);
				group.setMembers(new BaseClassArrayProp());
				group.getMembers().setValue(newMembers);
				// Update the membership.
				ibmCognosPermission.cmService.update(new BaseClass[] { group }, new UpdateOptions());
			} else {
				log.warn("There are currently no Groups matching the criteria", new Object[0]);
			}
		} catch (Exception e) {
			log.error("{0}", new Object[] { e.getMessage() });
		}
		log.ok("Removed Group/User {0} to Group {1}", new Object[] { UserGroup, Group });
	}

	public Boolean createGroup(IBMCognosPermission ibmCognosPermission, String strGroup) {
		String path = "CAMID(\":\")";
		try {
			// Create a new group.
			BaseClass group = new Group();
			// Note that the defaultName of the new object will be set to
			// the first (in this case, only) name token.
			MultilingualToken[] names = new MultilingualToken[1];
			names[0] = new MultilingualToken();
			// declare an enum to pass to getConfiguration.
			ConfigurationDataEnum[] cdProps = new ConfigurationDataEnum[] { ConfigurationDataEnum.serverLocale,
					ConfigurationDataEnum.serverTimeZone, ConfigurationDataEnum.defaultFont };
			ibmCognosPermission.getSetHeaders();
			ConfigurationData cd = ibmCognosPermission.sysService.getConfiguration(cdProps);
			Locale[] locales = cd.getServerLocale();
			names[0].setLocale(locales[0].getLocale());
			names[0].setValue(strGroup);
			group.setName(new MultilingualTokenProp());
			group.getName().setValue(names);
			AddOptions ao = new AddOptions();
			ao.setUpdateAction(UpdateActionEnum.update);
			ao.setReturnProperties(new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.policies });
			// Add the new group.
			SearchPathSingleObject spSingle = new SearchPathSingleObject();
			spSingle.set_value(path);
			ibmCognosPermission.cmService.add(spSingle, new BaseClass[] { group }, ao);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public Boolean deleteGroup(IBMCognosPermission ibmCognosPermission, String groupSearchPath) {
		if (ibmCognosPermission.cmService != null) {
			try {
				Group obj = new Group();
				obj.setSearchPath(new StringProp());
				obj.getSearchPath().setValue(groupSearchPath);
				DeleteOptions del = new DeleteOptions();
				del.setForce(true);
				ibmCognosPermission.cmService.delete(new BaseClass[] { obj }, del);
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}
}
