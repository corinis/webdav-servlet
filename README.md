WebDAV-Servlet
==============

# What is it? 

A Servlet that brings basic WebDAV access to any store. Only 1 interface 
(IWebdavStorage) has to be implemented, an example (LocalFileSystemStorage)
which uses the local filesystem, is provided.

Unlike large systems (like slide), this servlet only supports the most basic
data access options. versioning or user management are not supported

# REQUIREMENTS 

- JDK 1.6 or above
- A servlet container
- Apache Maven

# INSTALLATION & CONFIGURATION

This is for Apache Tomcat:

- place the webdav-servlet.jar in the /WEB-INF/lib/ of your webapp
- open web.xml of the webapp. it needs to contain the following:
```xml
  	<servlet>
		<servlet-name>webdav</servlet-name>
		<servlet-class>
			net.sf.webdav.WebdavServlet
		</servlet-class>
		<init-param>
			<description>
				name of the class that implements
				net.sf.webdav.IWebdavStore
			</description>
			<param-name>ResourceHandlerImplementation</param-name>
			<param-value>
				net.sf.webdav.LocalFileSystemStore
			</param-value>
		</init-param>
		<init-param>
			<description>
				folder where webdavcontent on the local filesystem is stored (for LocalFileSystemstore)
			</description>
			<param-name>rootpath</param-name>
			<param-value>/tmp/webdav</param-value>
		</init-param>
		<init-param>
			<description>
				triggers debug output of the
				ResourceHandlerImplementation (0 = off , 1 = on) off by default
			</description>
			<param-name>storeDebug</param-name>
			<param-value>0</param-value>
		</init-param>
	</servlet>
	<servlet-mapping>
		<servlet-name>webdav</servlet-name>
		<url-pattern>/*</url-pattern>
	</servlet-mapping>
 ```
 
- if you have implemented your own store, insert the class name
   to the parameter  "ResourceHandlerImplementation"
   and copy your .jar to /WEB-INF/lib/
- with /* as servlet mapping, every request to the webapp is handled by
   the servlet. change this if you want
- with the "storeDebug" parameter you can trigger the reference store implementation
   to spam at every method call. this parameter is optional and can be omitted
- authentication is done by the servlet-container. If you need it, you have to
   add the appropriate sections to the web.xml
- you can have multiple stores on the same server with different url-patterns 

# Sample Stores

## FILESTORE

The sample filestore allows classic webdav clients to read/write files on your
servers file system.

### Configuration

You need to set the LocalFileStore class and the parameter "rootpath" to where 
you want to store your files in your web.xml:

```xml
  	<servlet>
		<servlet-name>webdav-filestore</servlet-name>
		<init-param>
			<param-name>ResourceHandlerImplementation</param-name>
			<param-value>net.sf.webdav.LocalFileSystemStore</param-value>
		</init-param>
		<init-param>
			<param-name>rootpath</param-name>
			<param-value>/tmp/webdav</param-value>
		</init-param>
	</servlet>
	<servlet-mapping>
		<servlet-name>webdav-filestore</servlet-name>
		<url-pattern>/dav/*</url-pattern>
	</servlet-mapping>
```

### Access 

The webdav-filestore is reached at:
```
  "http://<ip/name + port of the server>/<name of the webapp>/<servlet-maping>"
  for the above setting: http://localhost:8080/dav/
```                          

weta-dfs-webdav has been tested on tomcat 5.0.28 and 5.5.12 so far, we accessed it 
from windows(2000 and XP) and MAC

## CARDDAV

Carddav is an extension to the default webdav protocol for storing contact information
as "vcard". The sample implementation allows for that by storing the information 
received onto the file system. 

Note: This is not a full sync solution, but rather a simple proof of concept. It will
allow you to our of the box sync the contacts of i.e. 2 computers running thunderbird.

Because of the simple core code, it will probably not work correctly if:

- if 2 computers change the same file
- multi user support
- clients use of different fields (i.e. one client supports images, the other one does not)

### Configuration

You need to set the CardDavFileStore class and the parameter "rootpath" to where 
you want to store your vcards in your web.xml:

```xml
  	<servlet>
		<servlet-name>webdav-carddav</servlet-name>
		<init-param>
			<param-name>ResourceHandlerImplementation</param-name>
			<param-value>net.sf.webdav.CardDavFileStore</param-value>
		</init-param>
		<init-param>
			<param-name>rootpath</param-name>
			<param-value>/tmp/carddav</param-value>
		</init-param>
	</servlet>
	<servlet-mapping>
		<servlet-name>webdav-carddav</servlet-name>
		<url-pattern>/carddav/*</url-pattern>
	</servlet-mapping>
		
```
  
### Access / Client Support

```
  "http://<ip/name + port of the server>/<name of the webapp>/<servlet-maping>"
  for the aboce configuration: http://localhost:8080/carddav/
```                          

This has been tested with:

* [Thunderbird](https://www.mozilla.org/thunderbird/) and the [Sogo Connector 31.0.1](http://www.sogo.nu/downloads/frontends.html)

# CREDITS

* Remy Maucherat for the original webdav-servlet 
* the dependent files that come with tomcat,
* Oliver Zeigermann for the slide-WCK. Our IWebdavStorage class is modeled after his BasicWebdavStore.
 
 
[original project homepage](http://sourceforge.net/projects/webdav-servlet/)
