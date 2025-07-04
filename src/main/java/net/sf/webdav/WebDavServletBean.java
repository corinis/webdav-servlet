package net.sf.webdav;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.webdav.exceptions.AccessDeniedException;
import net.sf.webdav.exceptions.UnauthenticatedException;
import net.sf.webdav.exceptions.WebdavException;
import net.sf.webdav.fromcatalina.MD5Encoder;
import net.sf.webdav.locking.ResourceLocks;
import net.sf.webdav.methods.DoCopy;
import net.sf.webdav.methods.DoDelete;
import net.sf.webdav.methods.DoGet;
import net.sf.webdav.methods.DoHead;
import net.sf.webdav.methods.DoLock;
import net.sf.webdav.methods.DoMkcol;
import net.sf.webdav.methods.DoMove;
import net.sf.webdav.methods.DoNotImplemented;
import net.sf.webdav.methods.DoOptions;
import net.sf.webdav.methods.DoPropfind;
import net.sf.webdav.methods.DoProppatch;
import net.sf.webdav.methods.DoPut;
import net.sf.webdav.methods.DoReport;
import net.sf.webdav.methods.DoUnlock;

public class WebDavServletBean extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(WebDavServletBean.class);

	/**
	 * MD5 message digest provider.
	 */
	protected static MessageDigest MD5_HELPER;

	/**
	 * The MD5 helper object for this class.
	 */
	protected static final MD5Encoder MD5_ENCODER = new MD5Encoder();

	private static final boolean READ_ONLY = false;
	protected ResourceLocks _resLocks;
	protected IWebdavStore _store;
	protected ILockingListener _lockingListener;
	private final Map<String, IMethodExecutor> _methodMap = new HashMap<>();

	public WebDavServletBean() {
		try {
			MD5_HELPER = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException();
		}
	}

	public void init(IWebdavStore store, ILockingListener lockingListener,
			String dftIndexFile, String insteadOf404,
			int nocontentLenghHeaders, boolean lazyFolderCreationOnPut) {

		_store = store;
		_lockingListener = lockingListener;

		_resLocks = createResourceLocks(_lockingListener);


		IMimeTyper mimeTyper = new IMimeTyper() {
			@Override
			public String getMimeType(ITransaction transaction, String path) {
				String retVal= _store.getStoredObject(transaction, path, null).getMimeType();
				if ( retVal == null) {
					retVal = getServletContext().getMimeType( path);
				}
				return retVal;
			}
		};

		register("GET", new DoGet(store, dftIndexFile, insteadOf404, _resLocks,
				mimeTyper, nocontentLenghHeaders));
		register("HEAD", new DoHead(store, dftIndexFile, insteadOf404,
				_resLocks, mimeTyper, nocontentLenghHeaders));
		DoDelete doDelete = (DoDelete) register("DELETE", new DoDelete(store,
				_resLocks, READ_ONLY));
		DoCopy doCopy = (DoCopy) register("COPY", new DoCopy(store, _resLocks,
				doDelete, READ_ONLY));
		register("LOCK", new DoLock(store, _lockingListener, _resLocks, READ_ONLY));
		register("UNLOCK", new DoUnlock(store, _lockingListener, _resLocks, READ_ONLY));
		register("MOVE", new DoMove(_resLocks, store, doDelete, doCopy, READ_ONLY));
		register("MKCOL", new DoMkcol(store, _resLocks, READ_ONLY));
		register("OPTIONS", new DoOptions(store, _resLocks));
		register("PUT", new DoPut(store, _resLocks, READ_ONLY,
				lazyFolderCreationOnPut));
		register("PROPFIND", new DoPropfind(store, _resLocks, mimeTyper));
		register("PROPPATCH", new DoProppatch(store, _resLocks, READ_ONLY));
		register("REPORT", new DoReport(store, _resLocks));
		register("*NO*IMPL*", new DoNotImplemented(READ_ONLY));
	}

	/**
	 * This method can be overridden to inject alternative resource lock implementations
	 * @param lockingListener
	 * @return
	 */
	protected ResourceLocks createResourceLocks(ILockingListener lockingListener) {
		return new ResourceLocks(lockingListener);
	}

	@Override
	public void destroy() {
		if(_store != null)
			_store.destroy();
		super.destroy();
	}

	protected IMethodExecutor register(String methodName, IMethodExecutor method) {
		_methodMap.put(methodName, method);
		return method;
	}

	/**
	 * Handles the special WebDAV methods.
	 */
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String methodName = req.getMethod();
		ITransaction transaction = null;
		boolean needRollback = false;

		if (LOG.isTraceEnabled())
			debugRequest(methodName, req);

		try {
			Principal userPrincipal = _store.createPrincipal(req);
			transaction = _store.begin(userPrincipal);
			needRollback = true;
			_store.checkAuthentication(transaction);
			resp.setStatus(WebdavStatus.SC_OK);

			try {
				IMethodExecutor methodExecutor = _methodMap
						.get(methodName);
				if (methodExecutor == null) {
					methodExecutor = _methodMap
							.get("*NO*IMPL*");
				}

				methodExecutor.execute(transaction, req, resp);

				_store.commit(transaction);
				/** Clear not consumed data
				 *
				 * Clear input stream if available otherwise later access
				 * include current input.  These cases occure if the client
				 * sends a request with body to an not existing resource.
				 */
				if (req.getContentLength() != 0 && req.getInputStream().available() > 0) {
					if (LOG.isTraceEnabled()) { LOG.trace("Clear not consumed data!"); }
					while (req.getInputStream().available() > 0) {
						req.getInputStream().read();
					}
				}
				needRollback = false;
			} catch (IOException e) {
				java.io.StringWriter sw = new java.io.StringWriter();
				java.io.PrintWriter pw = new java.io.PrintWriter(sw);
				e.printStackTrace(pw);
				LOG.error("IOException: " + sw.toString());
				resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
				_store.rollback(transaction);
				throw new ServletException(e);
			}

		} catch (UnauthenticatedException e) {
			resp.setHeader("WWW-Authenticate", "Basic realm=\"" + e.getMessage() + "\"");
			resp.sendError(WebdavStatus.SC_UNAUTHORIZED);
		} catch (AccessDeniedException e){
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
		} catch (WebdavException e) {
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("WebdavException: " + sw.toString());
			throw new ServletException(e);
		} catch (Exception e) {
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("Exception: " + sw.toString());
		} finally {
			if (needRollback)
				_store.rollback(transaction);
		}

	}

	private void debugRequest(String methodName, HttpServletRequest req) {
		LOG.debug("-----------");
		LOG.debug("Request: methodName = " + methodName);
		LOG.debug("time: " + System.currentTimeMillis());
		LOG.debug("path: " + req.getRequestURI());
		LOG.debug("-----------");
		Enumeration<?> e = req.getHeaderNames();
		while (e.hasMoreElements()) {
			String s = (String) e.nextElement();
			LOG.trace("header: " + s + " " + req.getHeader(s));
		}
		e = req.getAttributeNames();
		while (e.hasMoreElements()) {
			String s = (String) e.nextElement();
			LOG.trace("attribute: " + s + " " + req.getAttribute(s));
		}
		e = req.getParameterNames();
		while (e.hasMoreElements()) {
			String s = (String) e.nextElement();
			LOG.trace("parameter: " + s + " " + req.getParameter(s));
		}
	}

}
