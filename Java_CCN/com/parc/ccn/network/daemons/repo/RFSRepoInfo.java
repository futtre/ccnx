package com.parc.ccn.network.daemons.repo;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.MalformedContentNameStringException;
import com.parc.ccn.data.util.BinaryXMLDictionary;
import com.parc.ccn.data.util.GenericXMLEncodable;
import com.parc.ccn.data.util.XMLDecoder;
import com.parc.ccn.data.util.XMLEncodable;
import com.parc.ccn.data.util.XMLEncoder;

/**
 * 
 * @author rasmusse
 *
 */

public class RFSRepoInfo extends GenericXMLEncodable implements XMLEncodable{
	
	protected String _name = null;
	protected String _globalPrefix = null;
	protected ContentName _policyName;
	protected static String DEFAULT_DICTIONARY_RESNAME = "repotags.cvsdict";
	
	private static BinaryXMLDictionary _dictionary;
	
	private static final String LOCAL_NAME_ELEMENT = "RepositoryName";
	private static final String GLOBAL_PREFIX_ELEMENT = "RepositoryPrefix";
	
	static {
		try {
			_dictionary = new BinaryXMLDictionary(DEFAULT_DICTIONARY_RESNAME);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public RFSRepoInfo(String name, String globalPrefix) throws MalformedContentNameStringException {
		_name = name;
		_globalPrefix = globalPrefix;
		if (!_globalPrefix.startsWith("/"))
			_globalPrefix = "/" + _globalPrefix;
		_policyName = ContentName.fromNative(_globalPrefix + '/' + _name 
				+ '/' + Repository.REPO_DATA + '/' + Repository.REPO_POLICY);
	}
	
	public String getName() {
		return _name;
	}
	
	public String getGlobalPrefix() {
		return _globalPrefix;
	}
	
	public ContentName getPolicyName() {
		return _policyName;
	}

	public void decode(XMLDecoder decoder) throws XMLStreamException {
		_name = new String(decoder.readBinaryElement(LOCAL_NAME_ELEMENT));
		_globalPrefix = new String(decoder.readBinaryElement(GLOBAL_PREFIX_ELEMENT));
	}

	public void encode(XMLEncoder encoder) throws XMLStreamException {
		encoder.pushXMLDictionary(_dictionary);
		encoder.writeElement(LOCAL_NAME_ELEMENT, _name.getBytes());
		encoder.writeElement(GLOBAL_PREFIX_ELEMENT, _globalPrefix.getBytes());
		encoder.popXMLDictionary();
	}

	public boolean validate() {
		return true;
	}
}