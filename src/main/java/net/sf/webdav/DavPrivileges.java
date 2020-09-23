package net.sf.webdav;

import net.sf.webdav.fromcatalina.XMLWriter;

public enum DavPrivileges {
	ALL("all"),
	READ("read"),
	WRITE("write"),
	WRITE_PROPERTIES("write-properties"),
	WRITE_CONTENT("write-content"),
	UNLOCK("unlock"),
	BIND("bind"),
	UNBIND("unbind"),
	WRITE_ACL("write-acl"),
	READ_ACL("read-acl"),
	READ_CURRENT_USER_PRIVILEGE_SET("read-current-user-privilege-set");
	
	private String name;
	
	private DavPrivileges(String name) {
		this.name = name;
	}
	
	public void write(XMLWriter out) {
        out.writeElement("DAV::privilege", XMLWriter.OPENING);
        	out.writeElement("DAV::" + name, XMLWriter.NO_CONTENT);
        out.writeElement("DAV::privilege", XMLWriter.CLOSING);
	}
	
}
