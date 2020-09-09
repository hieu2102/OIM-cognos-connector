package utils;

import java.rmi.RemoteException;

import org.identityconnectors.common.logging.Log;

import com.cognos.developer.schemas.bibus._3.BaseClass;
import com.cognos.developer.schemas.bibus._3.ContentManagerService_PortType;
import com.cognos.developer.schemas.bibus._3.OrderEnum;
import com.cognos.developer.schemas.bibus._3.PropEnum;
import com.cognos.developer.schemas.bibus._3.QueryOptions;
import com.cognos.developer.schemas.bibus._3.SearchPathMultipleObject;
import com.cognos.developer.schemas.bibus._3.Sort;

public class CognosQueryBuilder {
	public static Log log = Log.getLog(CognosQueryBuilder.class);

	public static BaseClass[] buildQuery(ContentManagerService_PortType cmService, PropEnum[] props, String spObject) {
		log.info("Entered method", new Object[0]);
		Sort nodeSortType = new Sort();
		Sort nodeSortName = new Sort();
		nodeSortType.setOrder(OrderEnum.ascending);
		nodeSortType.setPropName(PropEnum.objectClass);
		nodeSortName.setOrder(OrderEnum.ascending);
		nodeSortName.setPropName(PropEnum.defaultName);
		Sort[] nodeSorts = new Sort[] { nodeSortType, nodeSortName };
		BaseClass[] bc = null;
		try {
			bc = cmService.query(new SearchPathMultipleObject(spObject), props, nodeSorts, new QueryOptions());
		} catch (RemoteException e) {
			log.error("{0}", new Object[] { e.getMessage() });
			e.printStackTrace();
		}
		log.info("Method Exiting", new Object[0]);
		return null != bc && bc.length > 0 ? bc : null;
	}

}
