package net.sf.webdav;

import java.util.ArrayList;
import java.util.List;

public class DavExtensionConfig {
	
	private String davHeader = "1";
	private List<String> supportedReportSets = new ArrayList<String>();
	
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

}
