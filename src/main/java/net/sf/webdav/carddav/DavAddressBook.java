package net.sf.webdav.carddav;

import java.util.ArrayList;
import java.util.List;

import net.sf.webdav.DavPrivileges;
import net.sf.webdav.ObjectTree;

public class DavAddressBook extends ObjectTree {
	
	private static List<DavPrivileges> privileges = new ArrayList<DavPrivileges>();
	static {
		privileges.add(DavPrivileges.WRITE);
		privileges.add(DavPrivileges.WRITE_PROPERTIES);
		privileges.add(DavPrivileges.WRITE_CONTENT);
		privileges.add(DavPrivileges.UNLOCK);
		privileges.add(DavPrivileges.BIND);
		privileges.add(DavPrivileges.UNBIND);
		privileges.add(DavPrivileges.WRITE_ACL);
		privileges.add(DavPrivileges.READ);
		privileges.add(DavPrivileges.READ_ACL);
		privileges.add(DavPrivileges.READ_CURRENT_USER_PRIVILEGE_SET);
	}
	
	public DavAddressBook(String name, DavUser parent) {
		super(name, parent);
		this.setPrivileges(privileges);
		this.getResourceTypes().add("urn:ietf:params:xml:ns:carddav:addressbook");
	}


}
