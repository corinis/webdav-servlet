/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 Niko Berger 
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this class/java file, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.sf.webdav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import net.sf.webdav.exceptions.WebdavException;

/**
 * A sample CardDav store.
 * This allows connection of CardDav compatible clients and vcards to a file system path.
 * If using two different pieces of client software some fields will probably not be correctly 
 * synchronized because of non-standard fields in the v-cards. But it is fine as a proof of
 * concept.
 * 
 * @author Niko Berger
 * @license MIT
 *
 */
public class CardDavFileStore implements IWebdavStore {
	
    private static org.slf4j.Logger LOG = org.slf4j.LoggerFactory
            .getLogger(CardDavFileStore.class);

    private static int BUF_SIZE = 65536;
    
    private static String ABOOK_BASE = "addressbook";
    
    private static String ABOOK_URL = "/" + ABOOK_BASE;
    
    private File _root = null;

	private DavExtensionConfig config;
	
	public CardDavFileStore(File root) {
        _root = root;

        // default configuration for card-dav
		config = new DavExtensionConfig();
		config.setDavHeader("1", "3", "extended-mkcol", "addressbook", "access-control");
		config.setEtagFormat(DavExtensionConfig.ETAG_DEFAULT);
		config.setSupportsReport(true);
		config.addSupportedReportSet(
				"urn:ietf:params:xml:ns:carddav:addressbook-muliget", 
				"urn:ietf:params:xml:ns:carddav:addressbook-query", 
				"DAV::expand-property","DAV::principal-property-search","DAV::principal-search-property-set"
				);
	}

	@Override
	public void destroy() {
		// not needed
	}

	@Override
	public ITransaction begin(Principal principal) {
        LOG.trace("CardDavFileStore.begin()");
        if (!_root.exists()) {
            if (!_root.mkdirs()) {
                throw new WebdavException("root path: "
                        + _root.getAbsolutePath()
                        + " does not exist and could not be created");
            }
        }
		return null;
	}

	@Override
	public void checkAuthentication(ITransaction transaction) {
        LOG.trace("CardDavFileStore.checkAuthentication()");
        // do nothing
	}

	@Override
	public void commit(ITransaction transaction) {
        // do nothing
        LOG.trace("LocalFileSystemStore.commit()");
	}

	@Override
	public void rollback(ITransaction transaction) {
        // do nothing
        LOG.trace("LocalFileSystemStore.rollback()");
	}

	@Override
	public void createFolder(ITransaction transaction, String folderUri) {
        LOG.trace("LocalFileSystemStore.createFolder(" + folderUri + ")");
		// not possible
        throw new WebdavException("cannot create folder: " + folderUri);
	}

	@Override
	public void createResource(ITransaction transaction, String resourceUri) {
        LOG.trace("LocalFileSystemStore.createResource(" + resourceUri + ")");
        File file = getFile(resourceUri);
        try {
            if (!file.createNewFile())
                throw new WebdavException("cannot create file: " + resourceUri);
        } catch (IOException e) {
            LOG
                    .error("LocalFileSystemStore.createResource(" + resourceUri
                            + ") failed");
            throw new WebdavException(e);
        }
	}

	@Override
	public InputStream getResourceContent(ITransaction transaction,
			String resourceUri) {
		LOG.trace("LocalFileSystemStore.getResourceContent(" + resourceUri + ")");
		File file = getFile(resourceUri);

        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
        } catch (IOException e) {
            LOG.error("LocalFileSystemStore.getResourceContent(" + resourceUri
                    + ") failed");
            throw new WebdavException(e);
        }
        return in;
	}

	@Override
	public long setResourceContent(ITransaction transaction,
			String resourceUri, InputStream content, String contentType,
			String characterEncoding) {
		LOG.trace("LocalFileSystemStore.setResourceContent(" + resourceUri + ")");
        File file = getFile(resourceUri);
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(
                    file), BUF_SIZE);
            try {
                int read;
                byte[] copyBuffer = new byte[BUF_SIZE];

                while ((read = content.read(copyBuffer, 0, copyBuffer.length)) != -1) {
                    os.write(copyBuffer, 0, read);
                }
            } finally {
                try {
                    content.close();
                } finally {
                    os.close();
                }
            }
        } catch (IOException e) {
            LOG.error("LocalFileSystemStore.setResourceContent(" + resourceUri
                    + ") failed");
            throw new WebdavException(e);
        }
        long length = -1;

        try {
            length = file.length();
        } catch (SecurityException e) {
            LOG.error("LocalFileSystemStore.setResourceContent(" + resourceUri
                    + ") failed" + "\nCan't get file.length");
        }

        return length;	
    }

	@Override
	public String[] getChildrenNames(ITransaction transaction, String folderUri) {
		LOG.trace("LocalFileSystemStore.getChildrenNames(" + folderUri + ")");
		// special handling: we need a subcollection with the cards
		if(folderUri.equals("/")) {
			return new String[]{ABOOK_BASE};
		}
		
        File file = getFile(folderUri);
        String[] childrenNames = null;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            List<String> childList = new ArrayList<String>();
            String name = null;
            for (int i = 0; i < children.length; i++) {
                name = children[i].getName();
                childList.add(name);
                LOG.trace("Child " + i + ": " + name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = (String[]) childList.toArray(childrenNames);
        }
        return childrenNames;
	}

	/**
	 * prefix-handling for the address-book. 
	 * @param folderUri the uri 
	 * @return the normalized uri (without the prefix)
	 */
	private String normalize(String folderUri) {
		if(folderUri.startsWith(ABOOK_URL)) {
			return folderUri.substring(ABOOK_URL.length());
		}
		return folderUri;
	}
	
	private File getFile(String path) {
		return new File(_root, normalize(path));
	}

	@Override
	public long getResourceLength(ITransaction transaction, String path) {
		LOG.trace("LocalFileSystemStore.getResourceLength(" + path + ")");
		if(path.equals("/")) {
			return 1;
		}

        File file = getFile(path);
        return file.length();
	}

	@Override
	public void removeObject(ITransaction transaction, String uri) {
		File file = getFile(uri);
        boolean success = file.delete();
        LOG.trace("LocalFileSystemStore.removeObject(" + uri + ")=" + success);
        if (!success) {
            throw new WebdavException("cannot delete object: " + uri);
        }
	}

	@Override
	public StoredObject getStoredObject(ITransaction transaction, String uri) {
		StoredObject so = null;
		if(uri.equals("/")) {
            so = new StoredObject();
            so.setFolder(true);
            so.setLastModified(new Date(_root.lastModified()));
            so.setCreationDate(new Date(_root.lastModified()));
            so.setResourceLength(getResourceLength(transaction, uri));
            return so;
		}
		
        File file = getFile(uri);
        if (file.exists()) {
            so = new StoredObject();
            so.setFolder(file.isDirectory());
            so.setLastModified(new Date(file.lastModified()));
            so.setCreationDate(new Date(file.lastModified()));
            so.setResourceLength(getResourceLength(transaction, uri));
            // only vcard content 
            if(!file.isDirectory()) {
    			so.setMimeType("text/x-vcard");
            } else {
    			so.getResourceTypes().add("urn:ietf:params:xml:ns:carddav:addressbook");
            }
        }

        return so;
	}

	@Override
	public Principal createPrincipal(HttpServletRequest request) {
		return request.getUserPrincipal();
	}

	@Override
	public boolean supportsMoveOperation() {
		return false;
	}

	@Override
	public void moveResource(ITransaction transaction, String sourceUri,
			String destinationUri) {
		throw new UnsupportedOperationException("Move operation is not supported in the LocalFileSystemStore");
	}

	@Override
	public DavExtensionConfig getConfig() {
		return config;
	}

	@Override
	public void addNamespace(HashMap<String, String> namespaces) {
		namespaces.put("urn:ietf:params:xml:ns:carddav", "card");
	}

	@Override
	public List<String> getReportSubEntries(String reportAction, String path) {
		// report with subentries not supported
		return null;
	}
	
	@Override
	public Map<String, String> getAdditionalProperties(String path,
			Vector<String> properties) {
		
		File file = getFile(path);
		Map<String, String> props = new HashMap<String, String>();
		if(properties != null) {
        	if(properties.remove("urn:ietf:params:xml:ns:carddav:address-data")) {
        		if(!file.isDirectory()) {
        			byte[] bytes;
					try {
						bytes = Files.readAllBytes(file.toPath());
						props.put("urn:ietf:params:xml:ns:carddav:address-data", new String(bytes, Charset.forName("UTF-8")));
					} catch (IOException e) {
						// re-add as not found
						properties.add("urn:ietf:params:xml:ns:carddav:address-data");
					}
        		}
        		
        	}
        }
		return props;
	}


}
