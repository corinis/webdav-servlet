/*
 * Copyright 1999,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.webdav.methods;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import net.sf.webdav.ILockingListener;
import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.WebdavStatus;
import net.sf.webdav.exceptions.LockFailedException;
import net.sf.webdav.exceptions.WebdavException;
import net.sf.webdav.fromcatalina.XMLWriter;
import net.sf.webdav.locking.IResourceLocks;
import net.sf.webdav.locking.LockedObject;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DoLock extends AbstractMethod {

    private static org.slf4j.Logger LOG = org.slf4j.LoggerFactory
            .getLogger(DoLock.class);

    private final IWebdavStore _store;
    private final ILockingListener _lockingListener;
    private final IResourceLocks _resourceLocks;
    private final boolean _readOnly;

    /**
     * Wrap mutable fields in order to provide thread-safety
     */
    private static final class State {
        private boolean _macLockRequest = false;
        private boolean _exclusive = false;
        private String _type = null;
        private String _lockOwner = null;
        private String _path = null;
        private String _parentPath = null;
        private String _userAgent = null;
    }

    public DoLock(IWebdavStore store, ILockingListener lockingListener,
            IResourceLocks resourceLocks, boolean readOnly) {
        _store = store;
        _lockingListener = lockingListener;
        _resourceLocks = resourceLocks;
        _readOnly = readOnly;
    }

    @Override
	public void execute(ITransaction transaction, HttpServletRequest req,
            HttpServletResponse resp) throws IOException, LockFailedException {
        LOG.trace("-- " + this.getClass().getName());

        State state = new State();

        if (_readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        } else {
            state._path = getRelativePath(req);
            state._parentPath = getParentPath(getCleanPath(state._path));

            if (!checkLocks(transaction, req, resp, _resourceLocks, state._path)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // resource is locked
            }

            if (!checkLocks(transaction, req, resp, _resourceLocks, state._parentPath)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // parent is locked
            }

            // Mac OS Finder (whether 10.4.x or 10.5) can't store files
            // because executing a LOCK without lock information causes a
            // SC_BAD_REQUEST
            state._userAgent = req.getHeader("User-Agent");
            if (state._userAgent != null && state._userAgent.indexOf("Darwin") != -1) {
                state._macLockRequest = true;

                String timeString = Long.toString(System.currentTimeMillis());
                state._lockOwner = state._userAgent.concat(timeString);
            }

            String tempLockOwner = "doLock" + System.currentTimeMillis()
                    + req.toString();
            if (_resourceLocks.lock(transaction, state._path, tempLockOwner, false,
                    0, TEMP_TIMEOUT, TEMPORARY)) {
                try {
                    if (req.getHeader("If") != null) {
                        doRefreshLock(transaction, req, resp, state);
                    } else {
                        doLock(transaction, req, resp, state);
                    }
                } catch (LockFailedException e) {
                    resp.sendError(WebdavStatus.SC_LOCKED);
                    LOG.error("Lockfailed exception", e);
                } finally {
                    _resourceLocks.unlockTemporaryLockedObjects(transaction,
                            state._path, tempLockOwner);
                }
            }
        }
    }

    private void doLock(ITransaction transaction, HttpServletRequest req,
                        HttpServletResponse resp, State state) throws IOException, LockFailedException {

        StoredObject so = _store.getStoredObject(transaction, state._path);

        if (so != null) {
            doLocking(transaction, req, resp, state);
        } else {
            // resource doesn't exist, null-resource lock
            doNullResourceLock(transaction, req, resp, state);
        }

        so = null;
        state._exclusive = false;
        state._type = null;
        state._lockOwner = null;

    }

    private void doLocking(ITransaction transaction, HttpServletRequest req,
                           HttpServletResponse resp, State state) throws IOException {

        // Tests if LockObject on requested path exists, and if so, tests
        // exclusivity
        LockedObject lo = _resourceLocks.getLockedObjectByPath(transaction,
                state._path);
        if (lo != null) {
            if (lo.isExclusive()) {
                sendLockFailError(transaction, req, resp, state);
                return;
            }
        }
        try {
            // Thats the locking itself
            executeLock(transaction, req, resp, state);

        } catch (ServletException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            LOG.trace(e.toString());
        } catch (LockFailedException e) {
            sendLockFailError(transaction, req, resp, state);
        } finally {
            lo = null;
        }

    }

    private void doNullResourceLock(ITransaction transaction,
                                    HttpServletRequest req, HttpServletResponse resp, State state)
            throws IOException {

        StoredObject parentSo, nullSo = null;

        try {
            parentSo = _store.getStoredObject(transaction, state._parentPath);

            if(state._parentPath != null){
                if(parentSo == null){
                    _store.createFolder(transaction, state._parentPath);
                }else if(parentSo.isResource()){
                    resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                                    return;
                }
            }


            nullSo = _store.getStoredObject(transaction, state._path);
            if (nullSo == null) {
                // resource doesn't exist
                _store.createResource(transaction, state._path);

                // Transmit expects 204 response-code, not 201
                if (state._userAgent != null && state._userAgent.contains("Transmit")) {
                    LOG
                            .trace("DoLock.execute() : do workaround for user agent '"
                                    + state._userAgent + "'");
                    resp.setStatus(WebdavStatus.SC_NO_CONTENT);
                } else {
                    resp.setStatus(WebdavStatus.SC_CREATED);
                }

            } else {
                // resource already exists, could not execute null-resource lock
                sendLockFailError(transaction, req, resp, state);
                return;
            }
            nullSo = _store.getStoredObject(transaction, state._path);
            // define the newly created resource as null-resource
            nullSo.setNullResource(true);

            // Thats the locking itself
            executeLock(transaction, req, resp, state);

        } catch (LockFailedException e) {
            sendLockFailError(transaction, req, resp, state);
        } catch (WebdavException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            LOG.error("Webdav exception", e);
        } catch (ServletException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            LOG.error("Servlet exception", e);
        } finally {
            parentSo = null;
            nullSo = null;
        }
    }

    private void doRefreshLock(ITransaction transaction,
                               HttpServletRequest req, HttpServletResponse resp, State state)
            throws IOException, LockFailedException {

        String[] lockTokens = getLockIdFromIfHeader(req);
        String lockToken = null;
        if (lockTokens != null)
            lockToken = lockTokens[0];

        if (lockToken != null) {
            // Getting LockObject of specified lockToken in If header
            LockedObject refreshLo = _resourceLocks.getLockedObjectByID(
                    transaction, lockToken);
            if (refreshLo != null) {
                int timeout = getTimeout(transaction, req);

                refreshLo.refreshTimeout(timeout);
                // sending success response
                generateXMLReport(transaction, resp, refreshLo, state);

                refreshLo = null;
            } else {
                // no LockObject to given lockToken
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
            }

        } else {
            resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
        }
    }

    // ------------------------------------------------- helper methods

    /**
     * Executes the LOCK
     */
    private void executeLock(ITransaction transaction, HttpServletRequest req,
                             HttpServletResponse resp, State state) throws LockFailedException, IOException,
            ServletException {

        // Mac OS lock request workaround
        if (state._macLockRequest) {
            LOG.trace("DoLock.execute() : do workaround for user agent '"
                    + state._userAgent + "'");

            doMacLockRequestWorkaround(transaction, req, resp, state);
        } else {
            // Getting LockInformation from request
            if (getLockInformation(transaction, req, resp, state)) {
                int depth = getDepth(req);
                int lockDuration = getTimeout(transaction, req);

                boolean lockSuccess = false;
                if (state._exclusive) {
                    lockSuccess = _resourceLocks.exclusiveLock(transaction,
                            state._path, state._lockOwner, depth, lockDuration);
                } else {
                    lockSuccess = _resourceLocks.sharedLock(transaction, state._path,
                            state._lockOwner, depth, lockDuration);
                }

                if (lockSuccess) {
                    // Locks successfully placed - return information about
                    LockedObject lo = _resourceLocks.getLockedObjectByPath(
                            transaction, state._path);
                    if (lo != null) {
                        generateXMLReport(transaction, resp, lo, state);
                    } else {
                        resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                } else {
                    sendLockFailError(transaction, req, resp, state);

                    throw new LockFailedException();
                }
            } else {
                // information for LOCK could not be read successfully
                resp.setContentType("text/xml; charset=UTF-8");
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            }
        }
    }

    /**
     * Tries to get the LockInformation from LOCK request
     */
    private boolean getLockInformation(ITransaction transaction,
                                       HttpServletRequest req, HttpServletResponse resp, State state)
            throws ServletException, IOException {

        Node lockInfoNode = null;
        DocumentBuilder documentBuilder = null;

        documentBuilder = getDocumentBuilder();
        try {
            Document document = documentBuilder.parse(new InputSource(req
                    .getInputStream()));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();

            lockInfoNode = rootElement;

            if (lockInfoNode != null) {
                NodeList childList = lockInfoNode.getChildNodes();
                Node lockScopeNode = null;
                Node lockTypeNode = null;
                Node lockOwnerNode = null;

                Node currentNode = null;
                String nodeName = null;

                for (int i = 0; i < childList.getLength(); i++) {
                    currentNode = childList.item(i);

                    if (currentNode.getNodeType() == Node.ELEMENT_NODE
                            || currentNode.getNodeType() == Node.TEXT_NODE) {

                        nodeName = currentNode.getNodeName();

                        if (nodeName.endsWith("locktype")) {
                            lockTypeNode = currentNode;
                        }
                        if (nodeName.endsWith("lockscope")) {
                            lockScopeNode = currentNode;
                        }
                        if (nodeName.endsWith("owner")) {
                            lockOwnerNode = currentNode;
                        }
                    } else {
                        return false;
                    }
                }

                if (lockScopeNode != null) {
                    String scope = null;
                    childList = lockScopeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                            scope = currentNode.getNodeName();

                            if (scope.endsWith("exclusive")) {
                                state._exclusive = true;
                            } else if (scope.equals("shared")) {
                                state._exclusive = false;
                            }
                        }
                    }
                    if (scope == null) {
                        return false;
                    }

                } else {
                    return false;
                }

                if (lockTypeNode != null) {
                    childList = lockTypeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                            state._type = currentNode.getNodeName();

                            if (state._type.endsWith("write")) {
                                state._type = "write";
                            } else if (state._type.equals("read")) {
                                state._type = "read";
                            }
                        }
                    }
                    if (state._type == null) {
                        return false;
                    }
                } else {
                    return false;
                }

                if (lockOwnerNode != null) {
                    childList = lockOwnerNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                                                        // word 2010+
                            state._lockOwner = currentNode.getFirstChild()
									.getNodeValue();
                        } else if (currentNode.getNodeType() == Node.TEXT_NODE) {
                            // word 2007
                            state._lockOwner = currentNode.getNodeValue();
                        }
                    }
                }
                if (state._lockOwner == null) {
                    return false;
                }
            } else {
                return false;
            }

        } catch (DOMException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            LOG.error("DOM exception", e);
            return false;
        } catch (SAXException e) {
            resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            LOG.error("SAX exception", e);
            return false;
        }

        return true;
    }

    /**
     * Ties to read the timeout from request
     */
    private int getTimeout(ITransaction transaction, HttpServletRequest req) {

        int lockDuration = DEFAULT_TIMEOUT;
        String lockDurationStr = req.getHeader("Timeout");

        if (lockDurationStr == null) {
            lockDuration = DEFAULT_TIMEOUT;
        } else {
            int commaPos = lockDurationStr.indexOf(',');
            // if multiple timeouts, just use the first one
            if (commaPos != -1) {
                lockDurationStr = lockDurationStr.substring(0, commaPos);
            }
            if (lockDurationStr.startsWith("Second-")) {
                lockDuration = Integer.parseInt(lockDurationStr.substring(7));
            } else {
                if (lockDurationStr.equalsIgnoreCase("infinity")) {
                    lockDuration = MAX_TIMEOUT;
                } else {
                    try {
                        lockDuration = Integer.parseInt(lockDurationStr);
                    } catch (NumberFormatException e) {
                        lockDuration = MAX_TIMEOUT;
                    }
                }
            }
            if (lockDuration <= 0) {
                lockDuration = DEFAULT_TIMEOUT;
            }
            if (lockDuration > MAX_TIMEOUT) {
                lockDuration = MAX_TIMEOUT;
            }
        }
        return lockDuration;
    }

    /**
     * Generates the response XML with all lock information
     */
    private void generateXMLReport(ITransaction transaction,
                                   HttpServletResponse resp, LockedObject lo, State state) throws IOException {

        HashMap<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("DAV:", "D");

        resp.setStatus(WebdavStatus.SC_OK);
        resp.setContentType("text/xml; charset=UTF-8");

        XMLWriter generatedXML = new XMLWriter(resp.getWriter(), namespaces);
        generatedXML.writeXMLHeader();
        generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);
        generatedXML.writeElement("DAV::lockdiscovery", XMLWriter.OPENING);
        generatedXML.writeElement("DAV::activelock", XMLWriter.OPENING);

        generatedXML.writeElement("DAV::locktype", XMLWriter.OPENING);
        generatedXML.writeProperty("DAV::" + state._type);
        generatedXML.writeElement("DAV::locktype", XMLWriter.CLOSING);

        generatedXML.writeElement("DAV::lockscope", XMLWriter.OPENING);
        if (state._exclusive) {
            generatedXML.writeProperty("DAV::exclusive");
        } else {
            generatedXML.writeProperty("DAV::shared");
        }
        generatedXML.writeElement("DAV::lockscope", XMLWriter.CLOSING);

        int depth = lo.getLockDepth();

        generatedXML.writeElement("DAV::depth", XMLWriter.OPENING);
        if (depth == INFINITY) {
            generatedXML.writeText("Infinity");
        } else {
            generatedXML.writeText(String.valueOf(depth));
        }
        generatedXML.writeElement("DAV::depth", XMLWriter.CLOSING);

        generatedXML.writeElement("DAV::owner", XMLWriter.OPENING);
        // valid code, but word 2007 can't handle it
        //generatedXML.writeElement("DAV::href", XMLWriter.OPENING);
        generatedXML.writeText(state._lockOwner);
        //generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);
        generatedXML.writeElement("DAV::owner", XMLWriter.CLOSING);

        long timeout = lo.getTimeoutMillis();
        generatedXML.writeElement("DAV::timeout", XMLWriter.OPENING);
        generatedXML.writeText("Second-" + timeout / 1000);
        generatedXML.writeElement("DAV::timeout", XMLWriter.CLOSING);

        String lockToken = lo.getID();
        generatedXML.writeElement("DAV::locktoken", XMLWriter.OPENING);
        generatedXML.writeElement("DAV::href", XMLWriter.OPENING);
        generatedXML.writeText("opaquelocktoken:" + lockToken);
        generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);
        generatedXML.writeElement("DAV::locktoken", XMLWriter.CLOSING);

        generatedXML.writeElement("DAV::activelock", XMLWriter.CLOSING);
        generatedXML.writeElement("DAV::lockdiscovery", XMLWriter.CLOSING);
        generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);

        resp.addHeader("Lock-Token", "<opaquelocktoken:" + lockToken + ">");

        generatedXML.sendData();

    }

    /**
     * Executes the lock for a Mac OS Finder client
     */
    private void doMacLockRequestWorkaround(ITransaction transaction,
                                            HttpServletRequest req, HttpServletResponse resp, State state)
            throws LockFailedException, IOException {
        LockedObject lo;
        int depth = getDepth(req);
        int lockDuration = getTimeout(transaction, req);
        if (lockDuration < 0 || lockDuration > MAX_TIMEOUT)
            lockDuration = DEFAULT_TIMEOUT;

        boolean lockSuccess = _resourceLocks.exclusiveLock(transaction, state._path,
                state._lockOwner, depth, lockDuration);

        if (lockSuccess) {
            // Locks successfully placed - return information about
            lo = _resourceLocks.getLockedObjectByPath(transaction, state._path);
            if (lo != null) {
                generateXMLReport(transaction, resp, lo, state);
            } else {
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            // Locking was not successful
            sendLockFailError(transaction, req, resp, state);
        }
    }

    /**
     * Sends an error report to the client
     */
    private void sendLockFailError(ITransaction transaction,
                                   HttpServletRequest req, HttpServletResponse resp, State state)
            throws IOException {
        Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
        errorList.put(state._path, WebdavStatus.SC_LOCKED);
        sendReport(req, resp, errorList);
    }

}
