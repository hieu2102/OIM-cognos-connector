package cognos;

import java.util.Arrays;

import org.json.JSONObject;

import utils.DataUtils;

public class IBMCognosPermissionMain {
	public static void main(String args[]) {

//    	endPoint = "http://10.53.253.14:9300/p2pd/servlet/dispatch";
//		String endPoint = "http://10.53.252.17/ibmcognos/cgi-bin/cognos.cgi";
//		String namespaceID = "BIDV";
//		String userID = "149760";
//		String password = "Abc12345";
		String endPoint;
		String namespaceID;
		String userID;
		String password;
		endPoint = "http://192.168.100.32:9300/p2pd/servlet/dispatch";
		namespaceID = "CognosEx";
		userID = "cognosadm";
		password = "BNHdba@2020";
		IBMCognosPermission perBidv = new IBMCognosPermission(endPoint);
		try {
			perBidv.quickLogon(namespaceID, userID, password);
			CognosGroup cGroup = new CognosGroup(perBidv);
//			String[][] groups = cGroup.getListOfGroups();
//			Arrays.asList(groups).stream().forEach(x -> System.out.println(Arrays.toString(x)));
//			cGroup.createGroup(perBidv, "testGroup");
			DataUtils.printResult(perBidv.getUserByName("CognosEx", "hieund"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
////    	/*//Search User By Name
//		String user = "151041";
//		String[][] users = perBidv.getUserByName(namespaceID, user);
//		if (users != null) {
//			perBidv.printResult(users);
//		}
//
//		perBidv.printResult(perBidv.getUserGroupBySearchPath("//group"));
////    	/*//Search User By SearchPath
//		String SearchPath = "CAMID(\":Group120\")";
//		System.out.println("Search User By SearchPath: \"" + SearchPath + "\"");
//		String[] users2 = perBidv.getUserGroupBySearchPath(SearchPath);
//		if (users2 != null) {
//			perBidv.printResult(users2);
//		}

//    	/*//List all objects  	
		// Path=/* la root
//		System.out.println("List all objects");
//		String Path = "/content/folder[@name='QLTTTD']/folder[@name='TTTD']";
//		String Path = "/*";
//		String[][] objlst = perBidv.getListOfObjects(Path);
//		if (objlst != null) {
//			perBidv.printResult(objlst);
//		}

		/*
		 * //Get list Groups System.out.println("Get list Groups:"); String[][]
		 * groups=perBidv.getListOfGroups(); String[]
		 * programs=perBidv.getListOfProgram(); String[][]
		 * groups2=perBidv.getGroupsOfProgram("FTP"); if(groups2!= null) {
		 * perBidv.printResult(groups2); } System.out.println("\n=============="); for
		 * (int i = 0; i < groups.length; i++) { System.out.println("\n"); for (int j =
		 * 0; j < groups[i].length; j++) System.out.print(groups[i][j] + "---"); }
		 * System.out.println("\n=============="); for (int i = 0; i < programs.length;
		 * i++) System.out.println(programs[i]);
		 */
		// Get Users of Groups
		/*
		 * System.out.println("getUsersGroupsOfGroups"); String[][]
		 * usersofgroup=perBidv.getUsersGroupsOfGroups("CAMID(\":FTP_test\")");
		 * if(usersofgroup!= null) { perBidv.printResult(usersofgroup); }
		 */

		/*
		 * //Add User or Group to Group
		 * System.out.println("Add User or Group to Group:"); Boolean
		 * b=perBidv.addUserGroupToGroup("1286", "user", "FTP_test"); if (b)
		 * System.out.println("succeed"); else System.out.println("failed");
		 */

		// Remove User or Group to Group
		/*
		 * System.out.println("Remove User or Group to Group:"); Boolean
		 * c=perBidv.removeUserGroupToGroup("31333", "user", "FTP_test"); if (c)
		 * System.out.println("succeed"); else System.out.println("failed");
		 */

		/*
		 * //getPoliciesOfObjects(String)
		 * System.out.println("getPoliciesOfObjects(String)");
		 * perBidv.printResult(perBidv.getPoliciesOfObjects(
		 * "/content/folder[@name='TT21']"));
		 */

		/*
		 * //SetPoliciesOfObjects System.out.println("SetPoliciesOfObjects"); String[]
		 * s={"true","true","true",null,null};
		 * perBidv.setPoliciesOfObjects("CAMID(\":TT21_HO\")",
		 * s,"/content/folder[@name='Chung khoan']");
		 */
		/*
		 * //RemovePoliciesOfObjects System.out.println("RemovePoliciesOfObjects");
		 * String[] s={null,null,"true","true",null}; perBidv.removePoliciesOfObjects(
		 * "CAMID(\":TT21_HO\")","/content/folder[@name='Chung khoan']");
		 */

		/*
		 * //Create Group System.out.println("Create Group:"); Boolean
		 * b=perBidv.createGroup("Test_Group2");
		 */

		/*
		 * //Delete Group System.out.println("Delete Group:"); Boolean
		 * b=perBidv.deleteGroup("CAMID(\":Test_Group2\")");
		 */

		// Get folder tree by group
//		System.out.println("Get Tree by Group:");
//		System.out.println(perBidv.getXMLTreeByGroup(("CAMID(\":GDQTK_120\")"), true));
//		System.out.println(perBidv.getXMLPolTreeByGroup("CAMID(\":TT21_Group\")"));
//		System.out.println(perBidv.getXMLGroupsOfProgram());
	}
}
