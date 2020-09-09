package utils;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.cognos.developer.schemas.bibus._3.BaseClass;

public class DataUtils {

	public static JSONObject arrayToJSON(String[][] source, String objectClass, String jsonResourceTag) throws JSONException {
		JSONObject output = new JSONObject();
		JSONArray array = new JSONArray();
		switch (objectClass) {
		case "group": // indx 0 = default name, indx 2= searchpath
			for (String[] ar : source) {
				JSONObject element = new JSONObject();
				element.put("defaultName", ar[0]);
				element.put("searchPath", ar[2]);
				array.put(element);
			}
			break;
		case "account":
			for (String[] ar : source) {
				JSONObject element = new JSONObject();
				element.put("searchPath", ar[2]);
				element.put("username", ar[0]);
				element.put("defaultName", ar[1]);
				array.put(element);
			}
		}
		output.put(jsonResourceTag, array);
		return output;
	}

	public static void printResult(String[][] result) {
		for (String[] i : result) {
			printResult(i);
		}
	}

	public static void printResult(String[] result) {
		System.out.println(String.join(" || ", result));
	}

	public static String[][] removeItemInString(String[][] array, String RemoveItem) {
		return Arrays.asList(array).stream().filter(x -> !x[1].equalsIgnoreCase(RemoveItem)).toArray(String[][]::new);
	}

	public static Boolean checkStringInString(String element, String source) {
		return source.toLowerCase().contains(element.toLowerCase());
	}

	public static Boolean checkBool(String s) {
		return null != s && s.equalsIgnoreCase("true") ? true : false;
	}

	public static boolean contains(BaseClass[] oldMembers, String pathOfUser) {
		return Arrays.asList(oldMembers).stream().filter(x -> pathOfUser.equals(x.getSearchPath().getValue())).findAny()
				.isPresent();
	}
}
