package net.sf.webdav;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * This is an extension to a generic stored object which allows creation of simple resource 
 * tree with user, group and other base objects beofre going deeper.
 * 
 * @author Niko Berger
 */
public class ObjectTree extends StoredObject {
	/**
	 * name is used as part of the url and for displayname
	 */
    private String name;

    protected ObjectTree parent;
    
    private List<ObjectTree> children = new ArrayList<>();
    
    private String baseref;
    
    private ObjectTree(String name) {
    	this.parent = null;
    	this.name = name;
    	this.setFolder(true);
    	this.setCreationDate(new Date());
    	this.setLastModified(new Date());
    }
    
    /**
     * create a root object
     * @param name
     */
    public ObjectTree(String name, String baseref) {
    	this(name);
    	this.baseref = baseref + (baseref.endsWith("/")?"":"/");
    }
    
    /**
     * create a root object
     * @param name
     */
    public ObjectTree(String name, ObjectTree parent) {
    	this(name);
    	
    	this.parent = parent;
    	parent.children.add(this);
    }

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the parent
	 */
	public ObjectTree getParent() {
		return parent;
	}

	/**
	 * @return the children
	 */
	public List<ObjectTree> getChildren() {
		return children;
	}

	/**
	 * @return the href
	 */
	public String getHref() {
		if(baseref != null)
			return baseref + name + "/";
		if(parent == null) {
			System.out.println("parent is null on " + name);
		}
		return parent.getHref() + name + "/";
	}

	/**
	 * Try to find a given child
	 * @param path the path (each element is one segment)
	 * @return a given path or null if not found
	 */
	public ObjectTree findByPath(String[] path) {
		if(path == null || path.length == 0)
			return this;
		
		String cur = path[0];
		if(cur.isEmpty()) {
			if(path.length == 1)
				return this;
			else {
				path = Arrays.copyOfRange(path, 1, path.length);
				cur = path[0];
			}
		}
		
		if(cur.equals(name)) {
			if(path.length == 1)
				return this;
			
			// go one deep
			path = Arrays.copyOfRange(path, 1, path.length);
			cur = path[0];
		}
		
		// try to find the child
		for(ObjectTree child: children) {
			if(child.getName() == null || child.getName().equalsIgnoreCase(cur)) {
				// go down one level
				return child.findByPath(Arrays.copyOfRange(path, 1, path.length));
			}
		}
		
		return null;
	}
    
}
