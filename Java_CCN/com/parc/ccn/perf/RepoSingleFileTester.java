package com.parc.ccn.perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.MalformedContentNameStringException;
import com.parc.ccn.data.WirePacket;
import com.parc.ccn.library.CCNFlowControl;
import com.parc.ccn.library.CCNLibrary;
import com.parc.ccn.network.daemons.repo.RepositoryException;

public class RepoSingleFileTester extends RepoPerformanceTester {
	
	private static RepoSingleFileTester _rpt = new RepoSingleFileTester();
	private static TestFlowControl _tfc = null;

	private class TestFlowControl extends CCNFlowControl {
		
		private FileOutputStream _fos = null;
		
		public TestFlowControl(String repoName, ContentName name, CCNLibrary library)
				throws MalformedContentNameStringException, RepositoryException, IOException {
			super(name, library);
			_tfc = this;
			if (repoName != null) {
				File file = new File(repoName + "/" + "repoFile");
				try {
					File repoDir = new File(repoName);
					repoDir.mkdirs();
					file.createNewFile();
					_fos = new FileOutputStream(file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
			}
		}
		
		public ContentObject put(ContentObject co) throws IOException {
			if (_fos != null) {
				try {
					WirePacket packet = new WirePacket(co);
					_fos.write(packet.encode());
				} catch (XMLStreamException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return co;
		}
	}
	
	public RepoSingleFileTester() {}
	
	public RepoSingleFileTester(String repoName, ContentName name, CCNLibrary library)
			throws XMLStreamException, IOException, MalformedContentNameStringException, RepositoryException {
		super(name, _rpt.new TestFlowControl(repoName, name, library));
	}
	
	public RepoPerformanceTester getTester(String repoName, ContentName name, CCNLibrary library) throws MalformedContentNameStringException, XMLStreamException, IOException, RepositoryException {
		return new RepoSingleFileTester(repoName, name, library);
	}
	
	public void close() throws IOException {
		super.close();
		if (_tfc._fos != null)
			_tfc._fos.close();
	}
	
	public static void main(String[] args) {
		_rpt.doTest(args);
	}

}