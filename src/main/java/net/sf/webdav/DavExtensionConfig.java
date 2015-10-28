package net.sf.webdav;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension config class allowing extra header and report sets to be added
 * to propfind calls.  
 * @author Niko Berger
 *
 */
public class DavExtensionConfig {
	
	public static final int ETAG_DEFAULT = 0;
	public static final int ETAG_W = 1;
	
	private String davHeader = "1";
	private List<String> supportedReportSets = new ArrayList<String>();
	private int etagFormat = ETAG_W;
	private boolean supportsReport = false;
	
	public DavExtensionConfig() {
	}
	

	/**
     * Every value here will be added to the dav capabilities, like:
     * DAV: extended-mkcol
     * @return additional dav header
     */
	public void setDavHeader(String... name) {
		davHeader = "";
		for(String s : name) {
			if(!davHeader.isEmpty())
				davHeader +=", ";
			davHeader += s;
		}
	}
	

	public void addSupportedReportSet(String... set) {
		for(String s : set)
			supportedReportSets.add(s);
	}

	public String getDavHeader() {
		return davHeader;
	}


	public List<String> getSupportedReportSets() {
		return supportedReportSets;
	}

	public int getEtagFormat() {
		return etagFormat ;
	}


	public void setEtagFormat(int etagFormat) {
		this.etagFormat = etagFormat;
	}


	public boolean isSupportsReport() {
		return supportsReport;
	}


	/**
	 * @param supportsReport set to true if the "Report" operation is handled
	 */
	public void setSupportsReport(boolean supportsReport) {
		this.supportsReport = supportsReport;
	}

}
