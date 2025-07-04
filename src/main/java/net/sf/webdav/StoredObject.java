/*
 * ====================================================================
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.sf.webdav;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoredObject {

    private boolean isFolder;
    private Date lastModified;
    private Date creationDate;
    private long contentLength;
    private String mimeType;

    private boolean isNullRessource;
    
    private List<String> resourceTypes = new ArrayList<>();
    private List<DavPrivileges> privileges = new ArrayList<>();
    /**
     * store additional properties for caching
     */
    private Map<String, String> properties = new HashMap<>();
	private String etag;

    /**
     * Determines whether the StoredObject is a folder or a resource
     * 
     * @return true if the StoredObject is a collection
     */
    public boolean isFolder() {
        return (isFolder);
    }
    /**
     * override this to generate your own href
     * @return the href of the resource
     */
    public String getHref() {
    	return null;
    }

    /**
     * Determines whether the StoredObject is a folder or a resource
     * 
     * @return true if the StoredObject is a resource
     */
    public boolean isResource() {
        return (!isFolder);
    }

    /**
     * Sets a new StoredObject as a collection or resource
     * 
     * @param f
     *      true - collection ; false - resource
     */
    public void setFolder(boolean f) {
        this.isFolder = f;
    }

    /**
     * Gets the date of the last modification
     * 
     * @return last modification Date
     */
    public Date getLastModified() {
        return (lastModified);
    }

    /**
     * Sets the date of the last modification
     * 
     * @param d
     *      date of the last modification
     */
    public void setLastModified(Date d) {
        this.lastModified = d;
    }

    /**
     * Gets the date of the creation
     * 
     * @return creation Date
     */
    public Date getCreationDate() {
        return (creationDate);
    }

    /**
     * Sets the date of the creation
     * 
     * @param d
     *      date of the creation
     */
    public void setCreationDate(Date c) {
        this.creationDate = c;
    }

    /**
     * Gets the length of the resource content
     * 
     * @return length of the resource content
     */
    public long getResourceLength() {
        return (contentLength);
    }

    /**
     * Sets the length of the resource content
     * 
     * @param l
     *      the length of the resource content
     */
    public void setResourceLength(long l) {
        this.contentLength = l;
    }

    /**
     * Gets the state of the resource
     * 
     * @return true if the resource is in lock-null state
     */
    public boolean isNullResource() {
        return isNullRessource;
    }

    /**
     * Sets a StoredObject as a lock-null resource
     * 
     * @param f
     *      true to set the resource as lock-null resource
     */
    public void setNullResource(boolean f) {
        this.isNullRessource = f;
        this.isFolder = false;
        this.creationDate = null;
        this.lastModified = null;
        // this.content = null;
        this.contentLength = 0;
        this.mimeType= null;
    }

    /**
     * Retrieve the myme type from the store object.
     * Can also return NULL if the store does not handle
     * mime type stuff.
     * In that case the mime type is determined by the servletcontext
     *
     * @return the mimeType
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Set the mime type of this object
     * 
     * @param mimeType the mimeType to set
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

	public List<String> getResourceTypes() {
		return resourceTypes;
	}

	public void setResourceTypes(List<String> resourceTypes) {
		this.resourceTypes = resourceTypes;
	}

	public String getEtag() {
		return etag;
	}

	public void setEtag(String etag) {
		this.etag = etag;
	}

	/**
	 * @return the privileges
	 */
	public List<DavPrivileges> getPrivileges() {
		return privileges;
	}

	/**
	 * @param privileges the privileges to set
	 */
	public void setPrivileges(List<DavPrivileges> privileges) {
		this.privileges = privileges;
	}
	/**
	 * @return the properties
	 */
	public Map<String, String> getProperties() {
		return properties;
	}
	/**
	 * @param properties the properties to set
	 */
	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

}
