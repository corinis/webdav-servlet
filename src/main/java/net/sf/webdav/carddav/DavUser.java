package net.sf.webdav.carddav;

import java.util.ArrayList;
import java.util.List;

import net.sf.webdav.DavPrivileges;
import net.sf.webdav.ObjectTree;

public class DavUser extends ObjectTree {
	
	private static List<DavPrivileges> privileges = new ArrayList<>();
	static {
		privileges.add(DavPrivileges.ALL);
		privileges.add(DavPrivileges.READ);
		privileges.add(DavPrivileges.WRITE);
		privileges.add(DavPrivileges.WRITE_PROPERTIES);
		privileges.add(DavPrivileges.WRITE_CONTENT);
		privileges.add(DavPrivileges.UNLOCK);
		privileges.add(DavPrivileges.BIND);
		privileges.add(DavPrivileges.UNBIND);
		privileges.add(DavPrivileges.WRITE_ACL);
		privileges.add(DavPrivileges.READ_ACL);
		privileges.add(DavPrivileges.READ_CURRENT_USER_PRIVILEGE_SET);
	}
	
	public DavUser(String name, String baseUrl) {
		super(name, baseUrl);
		this.setPrivileges(privileges);
	}

	public DavUser(String name, ObjectTree parent) {
		super(name, parent);
		this.setPrivileges(privileges);
	}

}
