package net.sf.webdav;

import java.util.List;
import java.util.Vector;

import net.sf.webdav.fromcatalina.XMLWriter;

/**
 * Represents a container.
 * This can be either a store, an address book or a calendar
 * A container is owned by user and its url is normally defined by a container:
 * 
 *  /dav/adressbooks/[USER]/[Container]/
 *  
 * @author Niko Berger
 */
public interface IContainer {
	
		/**
		 * @return the name of the container (make sure its url-safe!)
		 */
		String getName();

		/**
		 * @return the privileges for this container 
		 */
		List<DavPrivileges> getPrivileges();

		String getDisplayName();
		

		/**
		 * write custom properties and return the ones not written
		 * @param out
		 * @param propertiesVector
		 * @return
		 */
		Vector<String> writeProp(XMLWriter out, Vector<String> propertiesVector);
}
