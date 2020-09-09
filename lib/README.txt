Oracle Identity Manager (OIM) Connectors
Connector Server 12.2.1.3.0 - May 24, 2018

The Connector Server is for use with Identity Connectors only.
Connector server version 12.2.1.3.0 is backward compatible with earlier versions of the Connector server and therefore can be used for all existing ICF Connectors.


***********************************************************************************************************************************************************************************************************
NOTE: This connector server is compatible with older OIM versions as well.

***********************************************************************************************************************************************************************************************************



***********************************************************************************************************************************************************************************************************

NOTE:  After upgrade customer can choose TLS1.2 protocol for secure communication between OIM and connector server
____________________________________________________________________________________________________________________

After upgrade of connector server, customer can choose the protocol for secure communication between OIM and connector server.
Supported protocols are TLS1, TLS1.1 and TLS1.2.

***********************************************************************************************************************************************************************************************************




***********************************************************************************************************************************************************************************************************

NOTE:  For installation of this connector server pack, please refer the product install guide.


***********************************************************************************************************************************************************************************************************



INSTRUCTIONS TO UPGRADE THE EXISTING CONNECTOR SERVER
******************************************************

Java Connector Server Upgrade:
- Stop the connector server service.
- Take a backup of installed Connector server directory.
- Unzip connector_server_java-1.5.0.zip to a directory.
- Copy "connector_server_java-1.5.0/bin/*"and "connector_server_java-1.5.0/lib/*" files from the given updated Java connector server pack to the installed location of java connector server.
- Open "connector_server_java-1.5.0/conf/ConnectorServer.properties" file from given updated Java connector server pack and open conf/ConnectorServer.properties" file from installed location of java connector server.
  Add below property in "conf/ConnectorServer.properties" file at installed location from "connector_server_java-1.5.0/conf/ConnectorServer.properties" file in given updated Java connector server pack  i.e.
  ##
## Protocol in use for SSL communication e.g. TLSv1, TLSv1.1, TLSv1.2
##
  
connectorserver.protocol=TLSv1

By this property we are providing an option to select the protocol for SSL comunication. OOTB it is TLS1.0.
  But customer can choose the between TLS1.0, TLS1.1 and TLS1.2. 
- We are not preserving any customizations during upgrade of connector server and it is a manual upgrade. 
If customer is having any other customization in any of the file, then re-do the same from the back-up location.
- Start the connector server after doing all the required settings.
